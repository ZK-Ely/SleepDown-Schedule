package com.xiaomanjun.sleepdownschedule.feature.importing.special

import android.content.Context
import android.util.Log
import com.xiaomanjun.sleepdownschedule.CourseScheduleApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * 特殊同步方式编排器：
 * 1. 确保存在名为「特殊同步」的独立课表并记录其 ID（重复启用/删除后自动重建）；
 * 2. 同步时优先复用已保存 token，失效后用验证码 OCR 自动登录（多次重试），
 *    OCR 不可用时回退到用户手动输入的验证码；
 * 3. 拉取教务课表 → SpecialSyncMapper 转换 → 复用 ScheduleRepository.importDraft
 *    整体替换该课表（课程、节次、周数、学期开始日期）。
 *
 * 写入统一走 CourseScheduleApp.repository 单例，Room Flow 会让所有界面自动刷新。
 */
class SpecialSyncCoordinator(private val context: Context) {

    companion object {
        const val ScheduleName = "特殊同步"
        private const val TAG = "SpecialSync"

        /** 验证码 OCR 自动重试上限；仍失败则由界面弹窗请求手动输入 */
        const val MaxOcrAttempts = 3

        /** 每次识别失败后的短暂等待，避免连续请求验证码接口 */
        const val OcrRetryDelayMillis = 500L
    }

    private val repository get() = (context.applicationContext as CourseScheduleApp).repository

    private val api = SpecialSyncApi()

    /** 当前是否应显示特殊课表的刷新入口：启用中且特殊课表存在 */
    fun isActive(): Boolean {
        val config = SpecialSyncStore.load(context)
        return config.enabled && config.scheduleId > 0
    }

    fun currentScheduleId(): Int = SpecialSyncStore.load(context).scheduleId

    /**
     * 启用开关时调用：创建（或复用）特殊课表并激活，让用户立即切到该课表。
     * 返回课表 ID。
     */
    suspend fun ensureSpecialSchedule(): Int {
        val store = SpecialSyncStore.load(context)
        val profiles = repository.snapshot().schedules
        val existing = store.scheduleId.takeIf { id -> id > 0 && profiles.any { it.id == id } }
        val scheduleId = existing ?: repository.createSchedule(ScheduleName)
        val isCurrent = profiles.firstOrNull { it.id == scheduleId }?.isActive == true
        if (!isCurrent) repository.activateSchedule(scheduleId)
        SpecialSyncStore.save(context, store.copy(scheduleId = scheduleId, enabled = true))
        api.attach(context)
        return scheduleId
    }

    /** 获取一张验证码图片（用于手动输入兜底） */
    suspend fun fetchCaptcha(): ByteArray {
        api.attach(context)
        return api.getCaptcha()
    }

    /**
     * OCR 接口连通性测试：拉一张真实验证码图，按当前识别方式识别并返回结果。
     * @return 成功时返回 "识别结果：XXXX"；失败返回错误描述。
     */
    suspend fun testOcrConnectivity(): String {
        val config = SpecialSyncStore.load(context)
        if (config.ocrMode == SpecialSyncOcrMode.OPENAI) {
            if (config.ocrApiKey.isBlank()) return "请先填写 API Key"
            if (config.ocrModelName.isBlank()) return "请先填写模型名称"
            if (config.ocrApiBaseUrl.isBlank()) return "请先填写请求地址"
        } else {
            if (!OcrEngineManager.isComponentReady(context, config.ocrLocalModel)) {
                return "本地识别组件尚未下载，请先在下方下载引擎与模型"
            }
        }
        api.attach(context)
        val bytes = try {
            api.getCaptcha()
        } catch (error: Throwable) {
            Log.w(TAG, "testOcrConnectivity captcha failed", error)
            return "验证码获取失败：${error.message ?: "网络错误"}"
        }
        return try {
            val outcome = SpecialSyncCaptchaOcr.recognize(context, bytes, config)
            if (outcome.text.isBlank()) {
                "识别结果为空，可重试或改用手动输入验证码"
            } else {
                "连通正常，识别结果：${outcome.text}（4位字母数字 · 加速设备：${outcome.device}）"
            }
        } catch (error: Throwable) {
            Log.w(TAG, "testOcrConnectivity ocr failed", error)
            "识别失败：${error.message ?: "网络错误"}"
        }
    }

    sealed class SyncResult {
        data class Success(val courseCount: Int, val summary: String) : SyncResult()
        data class NeedManualCaptcha(val message: String) : SyncResult()
        data class Failure(val message: String) : SyncResult()
    }

    /**
     * 执行一次完整同步。
     * @param manualCaptchaText 用户手动输入的验证码；非空时优先用它登录一次，
     *        OCR 仅在未提供手动验证码或手动登录仍报验证码错误时介入。
     */
    suspend fun sync(manualCaptchaText: String = ""): SyncResult = withContext(Dispatchers.IO) {
        val store = SpecialSyncStore.load(context)
        if (store.username.isBlank() || store.password.isBlank()) {
            return@withContext SyncResult.Failure("请先填写账号和密码")
        }
        api.attach(context)

        // 1) 会话恢复：token 有效直接进入拉取
        var loggedIn = runCatching { api.tryResume() }.getOrDefault(false)

        // 2) 手动验证码优先（用户看图输入）
        val manualCode = manualCaptchaText.trim()
        if (!loggedIn && manualCode.isNotEmpty()) {
            val outcome = attemptLogin(store.username, store.password, manualCode)
            if (outcome.ok) {
                loggedIn = true
            } else if (!outcome.message.contains("验证码")) {
                // 账号密码等硬错误：直接失败，不要再撞验证码
                return@withContext SyncResult.Failure(outcome.message)
            }
        }

        // 3) OCR 自动识别（自动换图重试，上限 MaxOcrAttempts 次，失败间短暂等待）
        if (!loggedIn) {
            var sawCaptchaError = false
            var lastMessage = "登录失败"
            for (attempt in 1..MaxOcrAttempts) {
                when (val outcome = autoLoginAttempt(store.username, store.password, store)) {
                    is AttemptOutcome.LoggedIn -> { loggedIn = true; break }
                    is AttemptOutcome.CaptchaError -> {
                        sawCaptchaError = true
                        lastMessage = outcome.message
                    }
                    is AttemptOutcome.HardError -> {
                        return@withContext SyncResult.Failure(outcome.message)
                    }
                }
                if (attempt < MaxOcrAttempts) delay(OcrRetryDelayMillis)
            }
            if (!loggedIn) {
                return@withContext if (sawCaptchaError) {
                    SyncResult.NeedManualCaptcha(
                        "验证码自动识别多次未通过。请刷新验证码图片，输入图中文字后重新同步。"
                    )
                } else {
                    SyncResult.Failure(lastMessage)
                }
            }
        }

        // 4) 拉取课表数据
        val items = try {
            api.loadKb()
        } catch (error: Throwable) {
            Log.w(TAG, "loadKb failed", error)
            return@withContext SyncResult.Failure("课表拉取失败：${error.message ?: "网络错误"}")
        }
        if (items.isEmpty()) {
            return@withContext SyncResult.Failure("教务没有返回任何课程记录")
        }

        // 5) 写入特殊课表：确保存在并激活，再整体替换
        val scheduleId = try {
            ensureSpecialSchedule()
        } catch (error: Throwable) {
            Log.w(TAG, "ensureSpecialSchedule failed", error)
            return@withContext SyncResult.Failure("创建特殊课表失败：${error.message ?: "未知错误"}")
        }
        try {
            // 「保留现有节次时间表」开启时沿用课表现有作息，不覆盖；关闭时用教务真实时间推导
            val existingPeriods = if (store.keepPeriods) {
                repository.periodsForSchedule(scheduleId).takeIf { it.isNotEmpty() }
            } else {
                null
            }
            val draft = SpecialSyncMapper.buildDraft(
                items,
                scheduleId,
                existingPeriods = existingPeriods
            )
            repository.importDraft(draft, createNewSchedule = false)
        } catch (error: Throwable) {
            Log.w(TAG, "importDraft failed", error)
            return@withContext SyncResult.Failure("课表写入失败：${error.message ?: "未知错误"}")
        }

        val summary = "共 ${items.size} 条课程记录 · ${items.map { it.kcmc }.distinct().size} 门课程"
        SpecialSyncStore.save(
            context,
            SpecialSyncStore.load(context).copy(
                lastSyncAtMillis = System.currentTimeMillis(),
                lastSyncSummary = summary
            )
        )
        SyncResult.Success(items.size, summary)
    }

    private sealed class AttemptOutcome {
        data object LoggedIn : AttemptOutcome()
        data class CaptchaError(val message: String) : AttemptOutcome()
        data class HardError(val message: String) : AttemptOutcome()
    }

    private suspend fun autoLoginAttempt(username: String, password: String, config: SpecialSyncConfig): AttemptOutcome {
        return try {
            val img = api.getCaptcha()
            val code = SpecialSyncCaptchaOcr.recognize(context, img, config).text
            if (code.isEmpty()) return AttemptOutcome.CaptchaError("验证码识别为空")
            val outcome = attemptLogin(username, password, code)
            when {
                outcome.ok -> AttemptOutcome.LoggedIn
                outcome.message.contains("验证码") -> AttemptOutcome.CaptchaError(outcome.message)
                else -> AttemptOutcome.HardError(outcome.message)
            }
        } catch (error: Throwable) {
            Log.w(TAG, "autoLoginAttempt failed", error)
            AttemptOutcome.HardError("网络错误：${error.message ?: "未知"}")
        }
    }

    private suspend fun attemptLogin(username: String, password: String, code: String): SpecialSyncApi.LoginOutcome {
        return try {
            api.login(username, password, code)
        } catch (error: Throwable) {
            Log.w(TAG, "login failed", error)
            SpecialSyncApi.LoginOutcome(false, "登录请求失败：${error.message ?: "网络错误"}")
        }
    }
}
