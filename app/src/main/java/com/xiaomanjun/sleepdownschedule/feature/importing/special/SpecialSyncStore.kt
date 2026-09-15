package com.xiaomanjun.sleepdownschedule.feature.importing.special

import android.content.Context
import android.content.SharedPreferences

/**
 * 特殊同步方式的本地配置存储。
 *
 * 账号密码只保存在本机 SharedPreferences，与备份模块"敏感信息留在本机"的边界一致：
 * BackupFormatV1 不包含这些字段。
 */
data class SpecialSyncConfig(
    val enabled: Boolean = false,
    val server: String = SpecialSyncApi.DefaultBaseUrl,
    val username: String = "",
    val password: String = "",
    val scheduleId: Int = 0,
    val lastSyncAtMillis: Long = 0L,
    val lastSyncSummary: String = "",
    /** 显示时隐藏没有教室地点的课程（仅对特殊同步课表生效） */
    val hideNoRoom: Boolean = false,
    /** 按间隔自动同步（仅前台运行时生效） */
    val autoRefreshEnabled: Boolean = false,
    /** 距上次同步超过该分钟数后自动刷新 */
    val autoRefreshIntervalMinutes: Int = 60,
    /** 禁止自动刷新时间段，元素格式 "HH:mm-HH:mm"（支持跨午夜，如 22:00-06:00） */
    val forbiddenRanges: List<String> = emptyList(),
    /** 验证码识别方式："local"（ML Kit 本地）或 "openai"（OpenAI 兼容视觉接口） */
    val ocrMode: String = SpecialSyncOcrMode.LOCAL,
    /** OpenAI 兼容接口地址（chat/completions 拼接在该 base 之后） */
    val ocrApiBaseUrl: String = SpecialSyncOcrMode.DefaultOpenAiBaseUrl,
    /** 视觉模型名称，如 Qwen/Qwen2-VL-7B-Instruct */
    val ocrModelName: String = SpecialSyncOcrMode.DefaultOcrModel,
    /** OpenAI 兼容接口 API Key（仅保存在本机） */
    val ocrApiKey: String = ""
)

/** 验证码识别方式取值与默认值 */
object SpecialSyncOcrMode {
    const val LOCAL = "local"
    const val OPENAI = "openai"
    const val DefaultOpenAiBaseUrl = "https://api.siliconflow.cn/v1"
    const val DefaultOcrModel = "PaddlePaddle/PaddleOCR-VL-1.5"

    /** 旧版本默认模型：已保存该值的设备视为未手动配置，随默认值迁移 */
    const val LegacyDefaultOcrModel = "Qwen/Qwen2-VL-7B-Instruct"
}

object SpecialSyncStore {

    private const val PREFS = "special_sync_store"
    private const val RANGE_SEPARATOR = "|"

    fun load(context: Context): SpecialSyncConfig {
        val p: SharedPreferences = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val rangesRaw = p.getString("forbiddenRanges", "") ?: ""
        return SpecialSyncConfig(
            enabled = p.getBoolean("enabled", false),
            server = p.getString("server", null) ?: SpecialSyncApi.DefaultBaseUrl,
            username = p.getString("username", "") ?: "",
            password = p.getString("password", "") ?: "",
            scheduleId = p.getInt("scheduleId", 0),
            lastSyncAtMillis = p.getLong("lastSyncAt", 0L),
            lastSyncSummary = p.getString("lastSyncSummary", "") ?: "",
            hideNoRoom = p.getBoolean("hideNoRoom", false),
            autoRefreshEnabled = p.getBoolean("autoRefreshEnabled", false),
            autoRefreshIntervalMinutes = p.getInt("autoRefreshIntervalMinutes", 60),
            forbiddenRanges = rangesRaw.split(RANGE_SEPARATOR)
                .filter { it.isNotBlank() },
            ocrMode = p.getString("ocrMode", null) ?: SpecialSyncOcrMode.LOCAL,
            ocrApiBaseUrl = p.getString("ocrApiBaseUrl", null) ?: SpecialSyncOcrMode.DefaultOpenAiBaseUrl,
            // 已保存旧默认模型（用户未手动改过）的设备，随本次默认值迁移
            ocrModelName = p.getString("ocrModelName", null)
                ?.takeUnless { it.isBlank() || it == SpecialSyncOcrMode.LegacyDefaultOcrModel }
                ?: SpecialSyncOcrMode.DefaultOcrModel,
            ocrApiKey = p.getString("ocrApiKey", "") ?: ""
        )
    }

    fun save(context: Context, config: SpecialSyncConfig) {
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putBoolean("enabled", config.enabled)
            .putString("server", config.server)
            .putString("username", config.username)
            .putString("password", config.password)
            .putInt("scheduleId", config.scheduleId)
            .putLong("lastSyncAt", config.lastSyncAtMillis)
            .putString("lastSyncSummary", config.lastSyncSummary)
            .putBoolean("hideNoRoom", config.hideNoRoom)
            .putBoolean("autoRefreshEnabled", config.autoRefreshEnabled)
            .putInt("autoRefreshIntervalMinutes", config.autoRefreshIntervalMinutes)
            .putString("forbiddenRanges", config.forbiddenRanges.joinToString(RANGE_SEPARATOR))
            .putString("ocrMode", config.ocrMode)
            .putString("ocrApiBaseUrl", config.ocrApiBaseUrl)
            .putString("ocrModelName", config.ocrModelName)
            .putString("ocrApiKey", config.ocrApiKey)
            .apply()
    }
}
