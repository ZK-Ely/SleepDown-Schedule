package com.xiaomanjun.sleepdownschedule.feature.settings

import com.xiaomanjun.sleepdownschedule.R
import com.xiaomanjun.sleepdownschedule.app.ui.DockScrollPadding
import com.xiaomanjun.sleepdownschedule.app.ui.detailContentTopPadding
import com.xiaomanjun.sleepdownschedule.CourseScheduleApp
import com.xiaomanjun.sleepdownschedule.core.ui.settings.LocalGlassMiuixEnabled
import com.xiaomanjun.sleepdownschedule.feature.importing.special.SpecialSyncConfig
import com.xiaomanjun.sleepdownschedule.feature.importing.special.SpecialSyncCoordinator
import com.xiaomanjun.sleepdownschedule.feature.importing.special.SpecialSyncStore
import com.xiaomanjun.sleepdownschedule.model.AppState
import com.kyant.shapes.RoundedRectangle
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.BasicComponent as MiuixBasicComponent
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 特殊同步方式设置页：配置教务账号并在独立「特殊同步」课表与教务系统间同步。
 * 界面完全复用项目公共设置组件（GlassPreferenceSection / SettingsGroup / SettingsRow 族）。
 */
@Composable
fun SpecialSyncSettingsScreen(
    state: AppState,
    backdrop: Backdrop?
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val topPadding = detailContentTopPadding()
    val coordinator = remember(context) { SpecialSyncCoordinator(context) }
    var config by remember { mutableStateOf(SpecialSyncStore.load(context)) }
    var busy by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var statusIsError by remember { mutableStateOf(false) }
    var showCaptchaInput by remember { mutableStateOf(false) }
    var captchaBytes by remember { mutableStateOf<ByteArray?>(null) }
    var captchaText by remember { mutableStateOf("") }

    fun update(transform: (SpecialSyncConfig) -> SpecialSyncConfig) {
        config = transform(config)
        SpecialSyncStore.save(context, config)
    }

    fun refreshCaptchaImage() {
        scope.launch {
            try {
                captchaBytes = coordinator.fetchCaptcha()
                captchaText = ""
                showCaptchaInput = true
            } catch (error: Throwable) {
                statusIsError = true
                statusMessage = "验证码获取失败：${error.message ?: "网络错误"}"
            }
        }
    }

    fun toggle(enabled: Boolean) {
        if (busy) return
        update { it.copy(enabled = enabled) }
        if (enabled) {
            scope.launch {
                busy = true
                statusMessage = null
                statusIsError = false
                try {
                    coordinator.ensureSpecialSchedule()
                    config = SpecialSyncStore.load(context)
                    statusIsError = false
                    statusMessage =
                        "已创建并切换到「${SpecialSyncCoordinator.ScheduleName}」课表。填写账号密码后点击同步即可导入课程。"
                } catch (error: Throwable) {
                    statusIsError = true
                    statusMessage = "创建特殊课表失败：${error.message ?: "未知错误"}"
                }
                busy = false
            }
        }
    }

    fun sync() {
        if (busy) return
        if (config.username.isBlank() || config.password.isBlank()) {
            statusIsError = true
            statusMessage = "请先填写账号和密码"
            return
        }
        scope.launch {
            busy = true
            statusMessage = null
            statusIsError = false
            val result = coordinator.sync(captchaText)
            when (result) {
                is SpecialSyncCoordinator.SyncResult.Success -> {
                    statusIsError = false
                    statusMessage = "同步完成：${result.summary}"
                    showCaptchaInput = false
                    captchaText = ""
                }
                is SpecialSyncCoordinator.SyncResult.NeedManualCaptcha -> {
                    statusIsError = true
                    statusMessage = result.message
                    refreshCaptchaImage()
                }
                is SpecialSyncCoordinator.SyncResult.Failure -> {
                    statusIsError = true
                    statusMessage = result.message
                }
            }
            config = SpecialSyncStore.load(context)
            busy = false
        }
    }

    LazyColumn(
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = topPadding,
            bottom = DockScrollPadding
        ),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item(key = "special-sync-main") {
            GlassPreferenceSection("特殊同步方式") {
                SettingsGroup(backdrop = backdrop, config = state.config, modifier = Modifier.fillMaxWidth()) {
                    SettingsToggleRow(
                        title = "启用特殊同步方式",
                        subtitle = "启用后创建独立的「${SpecialSyncCoordinator.ScheduleName}」课表并切换过去",
                        checked = config.enabled,
                        backdrop = backdrop,
                        enabled = !busy,
                        onCheckedChange = ::toggle
                    )
                    SettingsDivider()
                    SettingsInfoRow(
                        "功能说明",
                        "从教务系统拉取完整课表（含真实节次时间、周次与学期起始日），写入独立课表。账号密码仅保存在本机，不会进入备份或上传到任何服务器。"
                    )
                    if (config.enabled) {
                        SettingsDivider()
                        SettingsToggleRow(
                            title = "隐藏无教室课程",
                            subtitle = "特殊同步课表不显示没有教室地点的课程",
                            checked = config.hideNoRoom,
                            backdrop = backdrop,
                            enabled = !busy,
                            onCheckedChange = { value ->
                                update { it.copy(hideNoRoom = value) }
                            }
                        )
                    }
                    if (config.lastSyncAtMillis > 0) {
                        SettingsDivider()
                        SettingsInfoRow(
                            "上次同步",
                            listOfNotNull(
                                formatSyncTime(config.lastSyncAtMillis),
                                config.lastSyncSummary.takeIf { it.isNotBlank() }
                            ).joinToString(" · ")
                        )
                    }
                }
            }
        }
        if (config.enabled) {
            item(key = "special-sync-account") {
                GlassPreferenceSection("账号信息") {
                    SettingsGroup(backdrop = backdrop, config = state.config, modifier = Modifier.fillMaxWidth()) {
                        SettingsTextFieldRow(
                            title = "服务器地址",
                            value = config.server,
                            onValueChange = { value -> update { it.copy(server = value.trim()) } },
                            enabled = !busy,
                            placeholder = SpecialSyncApiBaseUrlPlaceholder
                        )
                        SettingsDivider()
                        SettingsTextFieldRow(
                            title = "学号 / 账号",
                            value = config.username,
                            onValueChange = { value -> update { it.copy(username = value) } },
                            enabled = !busy,
                            placeholder = "填写教务登录账号"
                        )
                        SettingsDivider()
                        SettingsTextFieldRow(
                            title = "密码",
                            value = config.password,
                            onValueChange = { value -> update { it.copy(password = value) } },
                            keyboardType = KeyboardType.Password,
                            enabled = !busy,
                            placeholder = "填写教务登录密码"
                        )
                    }
                }
            }
            if (showCaptchaInput) {
                item(key = "special-sync-captcha") {
                    GlassPreferenceSection("图形验证码") {
                        SettingsGroup(backdrop = backdrop, config = state.config, modifier = Modifier.fillMaxWidth()) {
                            SpecialSyncCaptchaRow(
                                captchaBytes = captchaBytes,
                                code = captchaText,
                                busy = busy,
                                onCodeChange = { captchaText = it },
                                onRefresh = { if (!busy) refreshCaptchaImage() }
                            )
                        }
                    }
                }
            }
            item(key = "special-sync-action") {
                GlassPreferenceSection("同步") {
                    SettingsGroup(backdrop = backdrop, config = state.config, modifier = Modifier.fillMaxWidth()) {
                        SettingsActionRow(
                            title = "立即同步",
                            subtitle = "登录教务系统并整体替换「${SpecialSyncCoordinator.ScheduleName}」课表",
                            buttonText = "同步",
                            iconRes = R.drawable.ic_refresh,
                            backdrop = backdrop,
                            onClick = ::sync
                        )
                    }
                }
            }
        }
        if (busy || statusMessage != null) {
            item(key = "special-sync-status") {
                GlassPreferenceSection("当前进度") {
                    SettingsGroup(backdrop = backdrop, config = state.config, modifier = Modifier.fillMaxWidth()) {
                        if (busy) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 20.dp, vertical = 14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                                Text("正在与教务系统同步…", style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                        statusMessage?.let { message ->
                            if (busy) SettingsDivider()
                            Text(
                                text = message,
                                modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (statusIsError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

private const val SpecialSyncApiBaseUrlPlaceholder = "https://…"

@Composable
private fun SpecialSyncCaptchaRow(
    captchaBytes: ByteArray?,
    code: String,
    busy: Boolean,
    onCodeChange: (String) -> Unit,
    onRefresh: () -> Unit
) {
    val bitmap = remember(captchaBytes) {
        captchaBytes?.let { BitmapFactory.decodeByteArray(it, 0, it.size) }
    }
    if (LocalGlassMiuixEnabled.current) {
        MiuixBasicComponent(
            title = "图形验证码",
            summary = "点击图片可换一张",
            modifier = Modifier.fillMaxWidth(),
            insideMargin = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
            endActions = {
                SpecialSyncCaptchaContent(bitmap, code, busy, onCodeChange, onRefresh)
            }
        )
        return
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 76.dp)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Text("图形验证码", style = MaterialTheme.typography.titleMedium)
            Text(
                "点击图片可换一张",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.width(12.dp))
        SpecialSyncCaptchaContent(bitmap, code, busy, onCodeChange, onRefresh)
    }
}

@Composable
private fun SpecialSyncCaptchaContent(
    bitmap: android.graphics.Bitmap?,
    code: String,
    busy: Boolean,
    onCodeChange: (String) -> Unit,
    onRefresh: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier
                .size(width = 104.dp, height = 40.dp)
                .clip(RoundedRectangle(10.dp))
                .border(
                    1.dp,
                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
                    RoundedRectangle(10.dp)
                )
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                .clickable(enabled = !busy, onClick = onRefresh),
            contentAlignment = Alignment.Center
        ) {
            val imageBitmap = remember(bitmap) { bitmap?.asImageBitmap() }
            if (imageBitmap != null) {
                Image(
                    bitmap = imageBitmap,
                    contentDescription = "图形验证码",
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
            }
        }
        BasicTextField(
            value = code,
            onValueChange = { value ->
                // 只允许字母数字，最长 6 位（与验证码生成规则一致）
                onCodeChange(value.filter { it.isLetterOrDigit() }.take(6))
            },
            singleLine = true,
            enabled = !busy,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
            textStyle = MaterialTheme.typography.bodyMedium.copy(
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            ),
            modifier = Modifier
                .widthIn(min = 56.dp)
                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.04f))
                .padding(horizontal = 8.dp, vertical = 8.dp)
        )
        Icon(
            painter = painterResource(R.drawable.ic_refresh),
            contentDescription = "刷新验证码",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .size(22.dp)
                .clickable(enabled = !busy, onClick = onRefresh)
        )
    }
}

private fun formatSyncTime(millis: Long): String =
    SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(millis))
