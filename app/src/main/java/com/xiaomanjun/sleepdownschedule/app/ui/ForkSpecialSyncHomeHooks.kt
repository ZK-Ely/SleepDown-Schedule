package com.xiaomanjun.sleepdownschedule.app.ui

// [fork] 本仓库独有功能：教务特殊同步（applicationId .zk 修改版的首页接入）。
// 实现集中在本文件；上游共享文件只保留带 [fork] 标记的最小调用行，方便后续合并上游：
//   - ScheduleAppUi.kt：rememberForkSpecialSyncHomeController / displayState / CaptchaDialogs 三处调用
//   - AppTopBar.kt：ForkSpecialSyncTopBarAction 一行

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.xiaomanjun.sleepdownschedule.AppState
import com.xiaomanjun.sleepdownschedule.R
import com.xiaomanjun.sleepdownschedule.ScheduleConfigEntity
import com.xiaomanjun.sleepdownschedule.core.ui.chrome.HomeIconButton
import com.xiaomanjun.sleepdownschedule.feature.importing.special.SpecialSyncAutoRefresher
import com.xiaomanjun.sleepdownschedule.feature.importing.special.SpecialSyncConfig
import com.xiaomanjun.sleepdownschedule.feature.importing.special.SpecialSyncCoordinator
import com.xiaomanjun.sleepdownschedule.feature.importing.special.SpecialSyncStore
import com.xiaomanjun.sleepdownschedule.feature.settings.SpecialSyncCaptchaDialog
import kotlinx.coroutines.launch

/**
 * 首页特殊同步的运行时状态。持有协调器、偏好快照与验证码弹窗状态；
 * 设置页在独立 Activity 中修改开关后，以 ON_RESUME 为信号重读偏好，
 * 并顺带补一次自动刷新检查（超间隔且不在禁止时段才会真正同步）。
 */
internal class ForkSpecialSyncHomeController internal constructor(
    private val context: Context,
    private val scope: kotlinx.coroutines.CoroutineScope,
    private val coordinator: SpecialSyncCoordinator,
    private val prefsReload: () -> Unit
) {
    var prefs: SpecialSyncConfig by mutableStateOf(SpecialSyncStore.load(context))
        internal set

    var refreshing: Boolean by mutableStateOf(false)
        private set

    // 手动验证码弹窗状态：自动识别三次失败后拉新图弹窗
    var captchaVisible: Boolean by mutableStateOf(false)
        internal set

    var captchaBytes: ByteArray? by mutableStateOf(null)
        private set

    internal fun dismissCaptcha() {
        captchaVisible = false
    }

    /** 特殊同步课表：启用中、存在已记录的课表 ID，且当前正在显示该课表 */
    fun isActiveFor(config: ScheduleConfigEntity): Boolean =
        prefs.enabled && prefs.scheduleId > 0 && prefs.scheduleId == config.id

    /**
     * 隐藏无教室课程：仅对特殊同步课表生效的显示层过滤（数据仍保留在课表中）。
     */
    fun displayState(visual: AppState): AppState =
        if (isActiveFor(visual.config) && prefs.hideNoRoom) {
            visual.copy(courses = visual.courses.filter { !it.location.isNullOrBlank() })
        } else {
            visual
        }

    fun showCaptchaDialog() {
        scope.launch {
            captchaBytes = runCatching { coordinator.fetchCaptcha() }.getOrNull()
            captchaVisible = true
        }
    }

    fun runSync(manualCaptchaText: String = "") {
        if (refreshing) return
        refreshing = true
        scope.launch {
            when (val result = coordinator.sync(manualCaptchaText)) {
                is SpecialSyncCoordinator.SyncResult.NeedManualCaptcha -> {
                    refreshing = false
                    showCaptchaDialog()
                }
                is SpecialSyncCoordinator.SyncResult.Success -> {
                    Toast.makeText(context, "特殊同步完成：${result.summary}", Toast.LENGTH_SHORT).show()
                    refreshing = false
                    prefsReload()
                }
                is SpecialSyncCoordinator.SyncResult.Failure -> {
                    Toast.makeText(context, result.message, Toast.LENGTH_LONG).show()
                    refreshing = false
                    prefsReload()
                }
            }
        }
    }
}

@Composable
internal fun rememberForkSpecialSyncHomeController(): ForkSpecialSyncHomeController {
    val context = LocalContext.current
    val coordinator = remember(context) { SpecialSyncCoordinator(context) }
    val scope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current
    var prefsVersion by remember { mutableIntStateOf(0) }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                prefsVersion++
                scope.launch {
                    runCatching { SpecialSyncAutoRefresher.tick(context) }
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val controller = remember(context) {
        ForkSpecialSyncHomeController(
            context = context,
            scope = scope,
            coordinator = coordinator,
            prefsReload = { prefsVersion++ }
        )
    }
    // prefsVersion 变化触发重读（设置页返回、同步完成后）
    val reloaded = remember(prefsVersion) { SpecialSyncStore.load(context) }
    androidx.compose.runtime.SideEffect {
        controller.prefs = reloaded
    }
    return controller
}

@Composable
internal fun ForkSpecialSyncCaptchaDialogs(
    controller: ForkSpecialSyncHomeController,
    config: ScheduleConfigEntity,
    backdrop: com.kyant.backdrop.Backdrop?
) {
    if (controller.captchaVisible) {
        SpecialSyncCaptchaDialog(
            captchaBytes = controller.captchaBytes,
            busy = controller.refreshing,
            backdrop = backdrop,
            config = config,
            onRefresh = { controller.showCaptchaDialog() },
            onSubmit = { code ->
                controller.dismissCaptcha()
                controller.runSync(manualCaptchaText = code)
            },
            onDismiss = { controller.dismissCaptcha() }
        )
    }
}

/** 特殊同步课表顶栏的刷新入口（日视图与无边距周视图共用顶栏位置）。 */
@Composable
internal fun ForkSpecialSyncTopBarAction(
    specialSyncActive: Boolean,
    specialSyncRefreshing: Boolean,
    onSpecialSyncRefresh: () -> Unit,
    backdrop: com.kyant.backdrop.Backdrop?,
    config: ScheduleConfigEntity,
    contentColor: androidx.compose.ui.graphics.Color
) {
    if (!specialSyncActive) return
    Box(
        modifier = Modifier
            .padding(end = 7.dp)
            .size(42.dp),
        contentAlignment = Alignment.Center
    ) {
        HomeIconButton(
            backdrop = backdrop,
            config = config,
            iconRes = R.drawable.ic_refresh,
            contentDescription = "同步特殊课表",
            selected = false,
            visible = !specialSyncRefreshing,
            onClick = { onSpecialSyncRefresh() }
        )
        if (specialSyncRefreshing) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                strokeWidth = 2.dp,
                color = contentColor
            )
        }
    }
}
