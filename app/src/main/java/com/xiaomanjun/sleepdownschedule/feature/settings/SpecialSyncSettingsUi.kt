package com.xiaomanjun.sleepdownschedule.feature.settings

import com.xiaomanjun.sleepdownschedule.R
import com.xiaomanjun.sleepdownschedule.app.ui.DockScrollPadding
import com.xiaomanjun.sleepdownschedule.app.ui.detailContentTopPadding
import com.xiaomanjun.sleepdownschedule.CourseScheduleApp
import com.xiaomanjun.sleepdownschedule.core.ui.designsystem.LiquidAlertAction
import com.xiaomanjun.sleepdownschedule.core.ui.designsystem.LiquidAlertActionStyle
import com.xiaomanjun.sleepdownschedule.core.ui.designsystem.LiquidAlertDialog
import com.xiaomanjun.sleepdownschedule.core.ui.designsystem.QuickSheetLiquidAction
import com.xiaomanjun.sleepdownschedule.core.ui.designsystem.SleepDownDesignTokens
import com.xiaomanjun.sleepdownschedule.core.ui.designsystem.SleepDownPickerDialog
import com.xiaomanjun.sleepdownschedule.core.ui.settings.LocalGlassMiuixEnabled
import com.xiaomanjun.sleepdownschedule.core.ui.settings.LocalSettingsPopupBackdrop
import com.xiaomanjun.sleepdownschedule.core.ui.settings.SleepDownLiquidDropdownPreference
import com.xiaomanjun.sleepdownschedule.domain.schedule.periodTimePickerBounds
import com.xiaomanjun.sleepdownschedule.feature.importing.special.OcrEngineManager
import com.xiaomanjun.sleepdownschedule.feature.importing.special.SpecialSyncConfig
import com.xiaomanjun.sleepdownschedule.feature.importing.special.SpecialSyncCoordinator
import com.xiaomanjun.sleepdownschedule.feature.importing.special.SpecialSyncOcrMode
import com.xiaomanjun.sleepdownschedule.feature.importing.special.SpecialSyncStore
import com.xiaomanjun.sleepdownschedule.model.AppState
import com.xiaomanjun.sleepdownschedule.model.ScheduleConfigEntity
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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
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
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.Backdrop
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
    // 手动验证码弹窗：OCR 三次失败后弹出；bytes 为 null 表示正在拉取新图
    var captchaDialogBytes by remember { mutableStateOf<ByteArray?>(null) }
    var captchaDialogVisible by remember { mutableStateOf(false) }
    // 禁止时段滚轮编辑弹窗（复用节次时间的四列时间选择器）
    var editingRangeIndex by remember { mutableStateOf(-1) }
    var rangeStartMinute by remember { mutableStateOf(11 * 60) }
    var rangeEndMinute by remember { mutableStateOf(14 * 60) }

    fun update(transform: (SpecialSyncConfig) -> SpecialSyncConfig) {
        config = transform(config)
        SpecialSyncStore.save(context, config)
    }

    fun refreshCaptchaAndShowDialog() {
        scope.launch {
            try {
                captchaDialogBytes = coordinator.fetchCaptcha()
            } catch (error: Throwable) {
                statusIsError = true
                statusMessage = "验证码获取失败：${error.message ?: "网络错误"}"
            }
            captchaDialogVisible = true
        }
    }

    fun sync(manualCaptchaText: String = "") {
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
            val result = coordinator.sync(manualCaptchaText)
            when (result) {
                is SpecialSyncCoordinator.SyncResult.Success -> {
                    statusIsError = false
                    statusMessage = "同步完成：${result.summary}"
                    captchaDialogVisible = false
                }
                is SpecialSyncCoordinator.SyncResult.NeedManualCaptcha -> {
                    statusIsError = true
                    statusMessage = result.message
                    refreshCaptchaAndShowDialog()
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

    fun testOcrConnectivity() {
        if (busy) return
        scope.launch {
            busy = true
            statusMessage = null
            statusIsError = false
            val result = coordinator.testOcrConnectivity()
            statusIsError = !result.startsWith("连通正常")
            statusMessage = "OCR 测试：$result"
            busy = false
        }
    }

    /** 下载本地识别引擎与所选模型（幂等，缺失什么下什么） */
    fun downloadEngine() {
        if (busy) return
        scope.launch {
            busy = true
            statusMessage = null
            statusIsError = false
            statusMessage = "正在下载识别引擎与模型，请保持网络畅通…"
            try {
                OcrEngineManager.ensureReady(context, config.ocrLocalModel)
                statusIsError = false
                statusMessage = "识别引擎就绪，本地识别已可使用"
            } catch (error: Throwable) {
                statusIsError = true
                statusMessage = "引擎下载失败：${error.message ?: "网络错误"}"
            }
            busy = false
        }
    }

    /** 卸载已下载的识别组件（引擎 + 模型） */
    fun uninstallEngine() {
        if (busy) return
        scope.launch {
            busy = true
            statusMessage = null
            statusIsError = false
            try {
                OcrEngineManager.uninstall(context)
                statusMessage = "已卸载识别组件"
            } catch (error: Throwable) {
                statusIsError = true
                statusMessage = "卸载失败：${error.message ?: "未知错误"}"
            }
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
                            keyboardType = KeyboardType.Password,
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
            item(key = "special-sync-auto") {
                GlassPreferenceSection("自动刷新") {
                    SettingsGroup(backdrop = backdrop, config = state.config, modifier = Modifier.fillMaxWidth()) {
                        SettingsToggleRow(
                            title = "自动刷新",
                            subtitle = "应用前台运行时，超过设定间隔自动从教务同步",
                            checked = config.autoRefreshEnabled,
                            backdrop = backdrop,
                            enabled = !busy,
                            onCheckedChange = { value ->
                                update { it.copy(autoRefreshEnabled = value) }
                            }
                        )
                        if (config.autoRefreshEnabled) {
                            SettingsDivider()
                            SettingsTextFieldRow(
                                title = "刷新间隔（小时）",
                                value = run {
                                    val hours = config.autoRefreshIntervalMinutes / 60f
                                    if (hours == hours.toInt().toFloat()) hours.toInt().toString()
                                    else "%.1f".format(hours)
                                },
                                onValueChange = { value ->
                                    val hours = value.filter { it.isDigit() || it == '.' }
                                        .toFloatOrNull()?.coerceIn(0.1f, 24f) ?: 1f
                                    update {
                                        it.copy(
                                            autoRefreshIntervalMinutes =
                                            (hours * 60).toInt().coerceAtLeast(5)
                                        )
                                    }
                                },
                                keyboardType = KeyboardType.Number,
                                enabled = !busy,
                                placeholder = "1"
                            )
                            config.forbiddenRanges.forEachIndexed { index, raw ->
                                val parts = raw.split("-")
                                val start = parts.getOrNull(0)?.trim()?.ifBlank { "11:00" } ?: "11:00"
                                val end = parts.getOrNull(1)?.trim()?.ifBlank { "14:00" } ?: "14:00"
                                SettingsDivider()
                                SettingsActionRow(
                                    title = "禁止时段${index + 1}",
                                    subtitle = "$start - $end · 点按编辑时间",
                                    buttonText = "编辑",
                                    iconRes = R.drawable.ic_agent_period,
                                    backdrop = backdrop,
                                    onClick = {
                                        rangeStartMinute = parseHhmmToMinutes(start) ?: (11 * 60)
                                        rangeEndMinute = parseHhmmToMinutes(end) ?: (14 * 60)
                                        editingRangeIndex = index
                                    }
                                )
                            }
                            SettingsDivider()
                            SettingsActionRow(
                                title = "添加禁止时间段",
                                subtitle = "在设定的时段内不会执行自动刷新（跨零点请拆成两条）",
                                buttonText = "添加",
                                iconRes = R.drawable.ic_add_course,
                                backdrop = backdrop,
                                onClick = {
                                    update {
                                        it.copy(
                                            forbiddenRanges = it.forbiddenRanges + listOf("11:00-14:00")
                                        )
                                    }
                                }
                            )
                        }
                    }
                }
            }
            item(key = "special-sync-ocr") {
                GlassPreferenceSection("验证码识别") {
                    SettingsGroup(backdrop = backdrop, config = state.config, modifier = Modifier.fillMaxWidth()) {
                        SleepDownLiquidDropdownPreference(
                            items = listOf("本地识别（离线）", "OpenAI 兼容接口"),
                            selectedIndex = if (config.ocrMode == SpecialSyncOcrMode.OPENAI) 1 else 0,
                            title = "识别方式",
                            summary = "本地识别使用内置模型；接口识别调用兼容 OpenAI 的视觉模型",
                            backdrop = backdrop,
                            config = state.config,
                            modifier = Modifier.fillMaxWidth(),
                            insideMargin = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
                            maxHeight = 240.dp,
                            onExpandedChange = {},
                            onSelectedIndexChange = { index ->
                                update {
                                    it.copy(
                                        ocrMode = if (index == 1) {
                                            SpecialSyncOcrMode.OPENAI
                                        } else {
                                            SpecialSyncOcrMode.LOCAL
                                        }
                                    )
                                }
                            }
                        )
                        if (config.ocrMode != SpecialSyncOcrMode.OPENAI) {
                            SettingsDivider()
                            SleepDownLiquidDropdownPreference(
                                items = listOf(
                                    "新模型（识别更准，约 52MB）",
                                    "旧模型（更小巧，约 13MB）",
                                    "ML Kit 模型（约 11MB）"
                                ),
                                selectedIndex = when (config.ocrLocalModel) {
                                    SpecialSyncOcrMode.LocalModelOld -> 1
                                    SpecialSyncOcrMode.LocalModelMlkit -> 2
                                    else -> 0
                                },
                                title = "本地识别模型",
                                summary = "模型与识别引擎在线下载，仅识别 4 位英文字母与数字",
                                backdrop = backdrop,
                                config = state.config,
                                modifier = Modifier.fillMaxWidth(),
                                insideMargin = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
                                maxHeight = 240.dp,
                                onExpandedChange = {},
                                onSelectedIndexChange = { index ->
                                    update {
                                        it.copy(
                                            ocrLocalModel = when (index) {
                                                1 -> SpecialSyncOcrMode.LocalModelOld
                                                2 -> SpecialSyncOcrMode.LocalModelMlkit
                                                else -> SpecialSyncOcrMode.LocalModelNew
                                            }
                                        )
                                    }
                                }
                            )
                            if (config.ocrLocalModel != SpecialSyncOcrMode.LocalModelMlkit) {
                                SettingsDivider()
                                SettingsToggleRow(
                                    title = "GPU 加速（实验性）",
                                    subtitle = "通过 NNAPI 使用 GPU/NPU 推理；不支持时自动回退 CPU，识别更快但兼容性因设备而异",
                                    checked = config.ocrGpuEnabled,
                                    backdrop = backdrop,
                                    enabled = !busy,
                                    onCheckedChange = { value ->
                                        update { it.copy(ocrGpuEnabled = value) }
                                    }
                                )
                            }
                            SettingsDivider()
                            // 组件状态涉及文件遍历与磁盘读取：异步计算，避免组合期主线程 IO
                            val componentReady by produceState(false, config.ocrLocalModel, busy) {
                                value = withContext(Dispatchers.IO) {
                                    OcrEngineManager.isComponentReady(context, config.ocrLocalModel)
                                }
                            }
                            val installedSize by produceState("未安装", config.ocrLocalModel, busy) {
                                val bytes = withContext(Dispatchers.IO) {
                                    OcrEngineManager.installedSizeBytes(context)
                                }
                                value = if (bytes > 0L) "约 ${bytes / (1024 * 1024)}MB" else "未安装"
                            }
                            val sizeHint = remember(config.ocrLocalModel) {
                                OcrEngineManager.pendingSizeHint(context, config.ocrLocalModel)
                            }
                            SettingsActionRow(
                                title = when {
                                    componentReady -> "识别组件已就绪"
                                    else -> "下载识别引擎"
                                },
                                subtitle = when {
                                    componentReady -> "本地识别已可使用，已占用 ${installedSize}空间；更换模型后点「下载」补齐缺失组件"
                                    else -> "首次使用需在线下载引擎与模型（$sizeHint），仅此一次"
                                },
                                buttonText = if (componentReady) "卸载" else "下载",
                                iconRes = if (componentReady) R.drawable.ic_trash else R.drawable.ic_download,
                                backdrop = backdrop,
                                destructive = componentReady,
                                onClick = {
                                    if (componentReady) uninstallEngine() else downloadEngine()
                                }
                            )
                        }
                        if (config.ocrMode == SpecialSyncOcrMode.OPENAI) {
                            SettingsDivider()
                            SettingsTextFieldRow(
                                title = "请求地址",
                                value = config.ocrApiBaseUrl,
                                onValueChange = { value ->
                                    update { it.copy(ocrApiBaseUrl = value.trim()) }
                                },
                                enabled = !busy,
                                placeholder = SpecialSyncOcrMode.DefaultOpenAiBaseUrl
                            )
                            SettingsDivider()
                            SettingsTextFieldRow(
                                title = "模型名称",
                                value = config.ocrModelName,
                                onValueChange = { value ->
                                    update { it.copy(ocrModelName = value.trim()) }
                                },
                                enabled = !busy,
                                placeholder = SpecialSyncOcrMode.DefaultOcrModel
                            )
                            SettingsDivider()
                            SettingsTextFieldRow(
                                title = "API Key",
                                value = config.ocrApiKey,
                                onValueChange = { value ->
                                    update { it.copy(ocrApiKey = value.trim()) }
                                },
                                keyboardType = KeyboardType.Password,
                                enabled = !busy,
                                placeholder = "sk-…"
                            )
                            SettingsDivider()
                            SettingsTextFieldRow(
                                title = "识别提示词",
                                value = config.ocrPrompt,
                                onValueChange = { value ->
                                    update { it.copy(ocrPrompt = value) }
                                },
                                enabled = !busy,
                                placeholder = "可选，如：4位数字、含干扰线"
                            )
                        }
                        SettingsDivider()
                        SettingsActionRow(
                            title = "测试连通性",
                            subtitle = if (config.ocrMode == SpecialSyncOcrMode.OPENAI) {
                                "拉取一张验证码并调用识别接口验证配置"
                            } else {
                                "拉取一张验证码并用所选本地模型识别验证"
                            },
                            buttonText = "测试",
                            iconRes = R.drawable.ic_check,
                            backdrop = backdrop,
                            onClick = { testOcrConnectivity() }
                        )
                    }
                }
            }
            item(key = "special-sync-action") {
                GlassPreferenceSection("同步") {
                    SettingsGroup(backdrop = backdrop, config = state.config, modifier = Modifier.fillMaxWidth()) {
                        SettingsToggleRow(
                            title = "保留现有节次时间表",
                            subtitle = "同步只更新课程，不改动本课表已配置的节次时间（关闭则采用教务时间）",
                            checked = config.keepPeriods,
                            backdrop = backdrop,
                            enabled = !busy,
                            onCheckedChange = { value ->
                                update { it.copy(keepPeriods = value) }
                            }
                        )
                        SettingsDivider()
                        SettingsActionRow(
                            title = "立即同步",
                            subtitle = "登录教务系统并整体替换「${SpecialSyncCoordinator.ScheduleName}」课表",
                            buttonText = "同步",
                            iconRes = R.drawable.ic_refresh,
                            backdrop = backdrop,
                            onClick = { sync() }
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

    if (captchaDialogVisible) {
        SpecialSyncCaptchaDialog(
            captchaBytes = captchaDialogBytes,
            busy = busy,
            backdrop = backdrop,
            config = state.config,
            onRefresh = { if (!busy) refreshCaptchaAndShowDialog() },
            onSubmit = { code ->
                captchaDialogVisible = false
                sync(manualCaptchaText = code)
            },
            onDismiss = { captchaDialogVisible = false }
        )
    }

    // 禁止时段滚轮编辑弹窗：与节次时间编辑同一套四列时间选择器
    val rangePickerBackdrop = LocalSettingsPopupBackdrop.current ?: backdrop
    SleepDownPickerDialog(
        show = editingRangeIndex >= 0,
        title = "编辑禁止时段",
        onDismissRequest = { editingRangeIndex = -1 },
        backdrop = rangePickerBackdrop,
        config = state.config,
        contentPadding = PaddingValues(SleepDownDesignTokens.QuickSheet.PickerContentPadding)
    ) {
        ConstrainedPeriodTimePickers(
            startMinute = rangeStartMinute,
            endMinute = rangeEndMinute,
            bounds = periodTimePickerBounds(null, null),
            onSelectionChange = { selection ->
                rangeStartMinute = selection.startMinute
                rangeEndMinute = selection.endMinute
            },
            textStyle = top.yukonga.miuix.kmp.theme.MiuixTheme.textStyles.title1.copy(fontSize = 22.sp),
            showSectionLabels = true,
            modifier = Modifier.fillMaxWidth()
        )
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(SleepDownDesignTokens.Dialog.ActionSpacing)
        ) {
            QuickSheetLiquidAction(
                "取消", true, rangePickerBackdrop, state.config,
                modifier = Modifier.weight(1f),
                height = SleepDownDesignTokens.CenteredDialog.ActionHeight
            ) { editingRangeIndex = -1 }
            QuickSheetLiquidAction(
                "删除", true, rangePickerBackdrop, state.config, destructive = true,
                modifier = Modifier.weight(1f),
                height = SleepDownDesignTokens.CenteredDialog.ActionHeight
            ) {
                val index = editingRangeIndex
                if (index >= 0) {
                    update {
                        it.copy(
                            forbiddenRanges = it.forbiddenRanges.toMutableList().apply {
                                if (index < size) removeAt(index)
                            }
                        )
                    }
                }
                editingRangeIndex = -1
            }
            QuickSheetLiquidAction(
                "确定", true, rangePickerBackdrop, state.config,
                modifier = Modifier.weight(1f),
                height = SleepDownDesignTokens.CenteredDialog.ActionHeight
            ) {
                val index = editingRangeIndex
                if (index >= 0) {
                    val startText = "%02d:%02d".format(rangeStartMinute / 60, rangeStartMinute % 60)
                    val endText = "%02d:%02d".format(rangeEndMinute / 60, rangeEndMinute % 60)
                    update { withRangeAt(it, index, startText, endText) }
                }
                editingRangeIndex = -1
            }
        }
    }
}

private fun withRangeAt(
    config: SpecialSyncConfig,
    index: Int,
    start: String,
    end: String
): SpecialSyncConfig {
    val startText = normalizeHm(start) ?: start
    val endText = normalizeHm(end) ?: end
    val newRanges = config.forbiddenRanges.toMutableList().apply {
        if (index < size) this[index] = "$startText-$endText" else add("$startText-$endText")
    }
    return config.copy(forbiddenRanges = newRanges)
}

private fun normalizeHm(value: String): String? {
    val match = Regex("(\\d{1,2}):(\\d{2})").find(value.trim()) ?: return null
    val hour = match.groupValues[1].toInt()
    val minute = match.groupValues[2].toInt()
    if (hour !in 0..23 || minute !in 0..59) return null
    return "%02d:%02d".format(hour, minute)
}

/** "11:00" → 660；非法返回 null */
private fun parseHhmmToMinutes(value: String): Int? {
    val match = Regex("(\\d{1,2}):(\\d{2})").find(value.trim()) ?: return null
    val hour = match.groupValues[1].toInt()
    val minute = match.groupValues[2].toInt()
    if (hour !in 0..23 || minute !in 0..59) return null
    return hour * 60 + minute
}

/**
 * 手动验证码弹窗：OCR 三次失败后弹出。图片可点击换一张，输入后提交触发重试同步。
 */
@Composable
fun SpecialSyncCaptchaDialog(
    captchaBytes: ByteArray?,
    busy: Boolean,
    backdrop: Backdrop?,
    config: ScheduleConfigEntity,
    onRefresh: () -> Unit,
    onSubmit: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var code by remember(captchaBytes) { mutableStateOf("") }
    LiquidAlertDialog(
        title = "输入图形验证码",
        message = "验证码自动识别未通过，请输入图片中的文字后重试。",
        actions = listOf(
            LiquidAlertAction("取消", LiquidAlertActionStyle.Secondary, onClick = onDismiss),
            LiquidAlertAction("提交并重试", LiquidAlertActionStyle.Primary) {
                if (code.isNotBlank()) onSubmit(code)
            }
        ),
        backdrop = backdrop,
        config = config,
        onDismissRequest = onDismiss,
        messageContent = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                SpecialSyncCaptchaImage(
                    bytes = captchaBytes,
                    busy = busy,
                    onRefresh = onRefresh
                )
                BasicTextField(
                    value = code,
                    onValueChange = { value ->
                        code = value.filter { it.isLetterOrDigit() }.take(6)
                    },
                    singleLine = true,
                    enabled = !busy,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                    textStyle = MaterialTheme.typography.titleMedium.copy(
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center
                    ),
                    modifier = Modifier
                        .widthIn(min = 132.dp)
                        .clip(RoundedRectangle(10.dp))
                        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f))
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                )
            }
        }
    )
}

@Composable
private fun SpecialSyncCaptchaImage(
    bytes: ByteArray?,
    busy: Boolean,
    onRefresh: () -> Unit
) {
    val bitmap = remember(bytes) {
        bytes?.let { BitmapFactory.decodeByteArray(it, 0, it.size) }
    }
    Box(
        modifier = Modifier
            .size(width = 116.dp, height = 44.dp)
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
                modifier = Modifier.fillMaxWidth()
            )
        } else {
            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
        }
    }
}

private const val SpecialSyncApiBaseUrlPlaceholder = "https://…"

private fun formatSyncTime(millis: Long): String =
    SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(millis))
