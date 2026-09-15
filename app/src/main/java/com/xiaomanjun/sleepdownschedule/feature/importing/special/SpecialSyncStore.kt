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
    val hideNoRoom: Boolean = false
)

object SpecialSyncStore {

    private const val PREFS = "special_sync_store"

    fun load(context: Context): SpecialSyncConfig {
        val p: SharedPreferences = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return SpecialSyncConfig(
            enabled = p.getBoolean("enabled", false),
            server = p.getString("server", null) ?: SpecialSyncApi.DefaultBaseUrl,
            username = p.getString("username", "") ?: "",
            password = p.getString("password", "") ?: "",
            scheduleId = p.getInt("scheduleId", 0),
            lastSyncAtMillis = p.getLong("lastSyncAt", 0L),
            lastSyncSummary = p.getString("lastSyncSummary", "") ?: "",
            hideNoRoom = p.getBoolean("hideNoRoom", false)
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
            .apply()
    }
}
