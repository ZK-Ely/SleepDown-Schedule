package com.xiaomanjun.sleepdownschedule.feature.importing.special

import android.content.Context
import android.util.Log
import com.xiaomanjun.sleepdownschedule.CourseScheduleApp
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/**
 * 特殊同步的自动刷新调度：
 * - 仅应用前台运行期间生效（依托进程内的 applicationScope 协程）；
 * - 距上次成功同步超过设定间隔、且当前不在任何禁止时段内时，静默执行一次同步；
 * - 自动刷新失败不弹窗打扰（手动输入验证码弹窗仅在用户主动同步时出现），等下个周期重试。
 */
object SpecialSyncAutoRefresher {

    private const val TAG = "SpecialSyncAuto"
    private const val TickIntervalMillis = 60_000L
    private val TimeFormat = DateTimeFormatter.ofPattern("HH:mm")

    fun start(app: CourseScheduleApp) {
        app.applicationScope.launch(start = CoroutineStart.LAZY) {
            // 启动后先等一个 tick 周期，避开应用冷启动的高负载窗口
            delay(TickIntervalMillis)
            while (isActive) {
                runCatching { tick(app) }
                    .onFailure { Log.w(TAG, "auto refresh tick failed", it) }
                delay(TickIntervalMillis)
            }
        }.start()
    }

    /** 供前台恢复（ON_RESUME）等时机立即补一次检查 */
    suspend fun tick(context: Context) {
        val config = SpecialSyncStore.load(context)
        if (!config.enabled || !config.autoRefreshEnabled) return
        if (config.username.isBlank() || config.password.isBlank()) return
        val now = System.currentTimeMillis()
        val elapsed = now - config.lastSyncAtMillis
        // lastSyncAtMillis == 0 表示从未成功同步过：视作早已超期，允许尝试
        if (config.lastSyncAtMillis != 0L &&
            elapsed < config.autoRefreshIntervalMinutes.coerceAtLeast(1) * 60_000L
        ) {
            return
        }
        if (isInForbiddenRange(config.forbiddenRanges, LocalTime.now())) return
        val coordinator = SpecialSyncCoordinator(context)
        when (val result = coordinator.sync()) {
            is SpecialSyncCoordinator.SyncResult.Success ->
                Log.i(TAG, "auto refresh ok: ${result.summary}")
            is SpecialSyncCoordinator.SyncResult.NeedManualCaptcha ->
                Log.i(TAG, "auto refresh needs manual captcha, skip this round")
            is SpecialSyncCoordinator.SyncResult.Failure ->
                Log.w(TAG, "auto refresh failed: ${result.message}")
        }
    }

    /** HH:mm-HH:mm 列表；支持跨午夜（start > end 视为跨零点区间） */
    fun isInForbiddenRange(ranges: List<String>, now: LocalTime): Boolean {
        return ranges.any { raw ->
            val parts = raw.split("-")
            if (parts.size != 2) return@any false
            val start = runCatching { LocalTime.parse(parts[0].trim(), TimeFormat) }.getOrNull() ?: return@any false
            val end = runCatching { LocalTime.parse(parts[1].trim(), TimeFormat) }.getOrNull() ?: return@any false
            if (start == end) {
                return@any false
            }
            if (start < end) {
                !now.isBefore(start) && !now.isAfter(end)
            } else {
                !now.isBefore(start) || !now.isAfter(end)
            }
        }
    }
}
