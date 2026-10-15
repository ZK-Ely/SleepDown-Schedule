package com.xiaomanjun.sleepdownschedule.feature.settings.about

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import com.kyant.backdrop.Backdrop
import com.xiaomanjun.sleepdownschedule.AppState
import com.xiaomanjun.sleepdownschedule.R
import com.xiaomanjun.sleepdownschedule.core.identity.currentIconResId
import com.xiaomanjun.sleepdownschedule.core.ui.chrome.DockScrollPadding
import com.xiaomanjun.sleepdownschedule.core.ui.scaffold.detailContentTopPadding
import com.xiaomanjun.sleepdownschedule.core.ui.settings.LocalSettingsSharedGradient
import com.xiaomanjun.sleepdownschedule.core.ui.settings.SettingsDivider
import com.xiaomanjun.sleepdownschedule.core.ui.settings.SettingsInfoRow
import com.xiaomanjun.sleepdownschedule.core.ui.settings.SettingsNavigationRow
import com.xiaomanjun.sleepdownschedule.core.ui.settings.SettingsValueRow
import com.xiaomanjun.sleepdownschedule.core.web.openSleepDownCustomTab
import com.xiaomanjun.sleepdownschedule.core.web.resolveSleepDownCustomTabsPackage
import com.xiaomanjun.sleepdownschedule.feature.settings.title
import com.xiaomanjun.sleepdownschedule.glass.ui.appUsesDarkTheme

@Composable
fun ChangelogSettingsScreen(
	state: AppState,
	backdrop: Backdrop?,
	onDonate: () -> Unit = {},
	onPrivacyPolicy: () -> Unit = {}
) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val topPadding = detailContentTopPadding()
    val darkTheme = appUsesDarkTheme(state.config)
    val listState = rememberLazyListState()
    val heroHeightPx = with(density) { AboutHeroHeight.toPx() }
    @Composable
    fun changelogItem(version: String, body: String) {
        CollapsibleChangelogRow(version, body)
        if (version != "1.0") SettingsDivider()
    }
    val heroScrollOffsetPx = remember(listState, heroHeightPx) {
        derivedStateOf {
            if (listState.firstVisibleItemIndex > 0) {
                heroHeightPx
            } else {
                listState.firstVisibleItemScrollOffset.toFloat().coerceIn(0f, heroHeightPx)
            }
        }
    }
    val heroCollapseProgress = remember(listState, heroHeightPx) {
        derivedStateOf {
            if (listState.firstVisibleItemIndex > 0) {
                1f
            } else {
                (listState.firstVisibleItemScrollOffset / heroHeightPx).coerceIn(0f, 1f)
            }
        }
    }
    val customTabsPackage = remember(context) { context.resolveSleepDownCustomTabsPackage() }
    val customTabWidthPx = with(density) {
        (configuration.screenWidthDp.dp * 0.68f).roundToPx()
    }
    val versionName = remember(context) {
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "1.0"
        }.getOrDefault("1.0")
    }
    val pageGradient = remember(darkTheme) { Brush.linearGradient(aboutPageGradientColors(darkTheme)) }
    val heroTitleGradient = if (darkTheme) {
        Brush.horizontalGradient(
            listOf(
                ComposeColor(0xFF8FC4FF),
                ComposeColor(0xFFB9A6FF),
                ComposeColor(0xFFF0B4E5)
            )
        )
    } else {
        Brush.horizontalGradient(
            listOf(
                ComposeColor(0xFF6758BE),
                ComposeColor(0xFF9147A8),
                ComposeColor(0xFFB43D79)
            )
        )
    }
    fun openProjectPage(url: String) {
        context.openSleepDownCustomTab(
            url = url,
            providerPackage = customTabsPackage,
            initialWidthPx = customTabWidthPx,
            darkTheme = darkTheme
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .then(if (LocalSettingsSharedGradient.current == null) Modifier.background(pageGradient) else Modifier)
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = topPadding,
                bottom = DockScrollPadding
            ),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item(key = "about-hero") {
                AboutHero(
                    versionName = versionName,
                    iconResId = currentIconResId(context, darkTheme),
                    titleBrush = heroTitleGradient,
                    collapseProgress = heroCollapseProgress,
                    scrollOffsetPx = heroScrollOffsetPx,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(AboutHeroHeight)
                )
            }

            item(key = "about-actions") {
                AboutGlassPanel(darkTheme = darkTheme, modifier = Modifier.fillMaxWidth()) {
                    SettingsNavigationRow(
                        "下载新版",
                        "前往 GitHub Releases 查看版本并下载安装包",
                        onClick = { openProjectPage(SleepDownReleasesUrl) }
                    )
                    SettingsDivider()
                    SettingsNavigationRow(
                        "项目官网",
                        "查看完整功能、截图与使用说明",
                        onClick = { openProjectPage(SleepDownWebsiteUrl) }
                    )
                    SettingsDivider()
                    SettingsNavigationRow(
                        "项目仓库",
                        "查看源码、版本与开发记录",
                        onClick = { openProjectPage(SleepDownSourceUrl) }
                    )
                    SettingsDivider()
                    SettingsNavigationRow(
                        "问题反馈",
                        "遇到问题或有建议时告诉我们",
                        onClick = { openProjectPage(SleepDownIssuesUrl) }
                    )
                    SettingsDivider()
					SettingsNavigationRow(
							"隐私政策",
							"了解数据处理、权限用途与个人信息权利",
							onClick = onPrivacyPolicy
						)
                }
            }

            item(key = "about-project-heading") {
                AboutSectionHeading(title = "项目信息")
            }
            item(key = "about-project") {
                AboutGlassPanel(darkTheme = darkTheme, modifier = Modifier.fillMaxWidth()) {
                    SettingsValueRow("项目作者", "小漫君 / xiaomanjun233")
                    SettingsDivider()
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "致谢作者",
                            modifier = Modifier.weight(0.43f),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "开源项目",
                            modifier = Modifier.weight(0.57f),
                            style = MaterialTheme.typography.labelMedium,
                            textAlign = TextAlign.End,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    SettingsDivider()
                    AboutCreditLinkRow(
                        author = "Kyant0",
                        repository = "AndroidLiquidGlass",
                        onClick = { openProjectPage(AndroidLiquidGlassUrl) }
                    )
                    SettingsDivider()
                    AboutCreditLinkRow(
                        author = "compose-miuix-ui",
                        repository = "miuix",
                        onClick = { openProjectPage(MiuixUrl) }
                    )
                    SettingsDivider()
                    AboutCreditLinkRow(
                        author = "xingheyuzhuan",
                        repository = "shiguang_warehouse",
                        onClick = { openProjectPage(ShiguangWarehouseUrl) }
                    )
                }
            }

            item(key = "about-highlights-heading") {
                AboutSectionHeading(
                    title = "不只是一张课表",
                    summary = "一张课表，也值得好好设计。"
                )
            }
            item(key = "about-feature-schedule") {
                AboutFeatureCard(
                    imageRes = R.drawable.about_feature_schedule,
                    eyebrow = "日视图与周视图",
                    title = "今天有重点，一周有全貌"
                )
            }
            item(key = "about-feature-import") {
                AboutFeatureCard(
                    imageRes = R.drawable.about_feature_import,
                    eyebrow = "多种导入方式",
                    title = "课表从哪里来，都先让你确认"
                )
            }
            item(key = "about-feature-assistant") {
                AboutFeatureCard(
                    imageRes = R.drawable.about_feature_assistant,
                    eyebrow = "AI 助理",
                    title = "会回答，也会动手，但最后由你做主"
                )
            }
            item(key = "about-feature-appearance") {
                AboutFeatureCard(
                    imageRes = R.drawable.about_feature_appearance,
                    eyebrow = "个性化与小组件",
                    title = "你的课表，当然该有你的样子"
                )
            }

            item(key = "about-privacy-heading") {
                AboutSectionHeading(
                    title = "数据与隐私",
                    summary = "日常课表留在本机，需要联网的能力由你主动使用。"
                )
            }
            item(key = "about-privacy") {
                AboutGlassPanel(darkTheme = darkTheme, modifier = Modifier.fillMaxWidth()) {
                    SettingsInfoRow(
                        "基础数据保存在本机",
                        "课表、设置、壁纸取景和助手记忆默认保存在应用自己的空间里，日常查看与编辑不需要上传课程。"
                    )
                    SettingsDivider()
                    SettingsInfoRow(
                        "文件由你亲自选择",
                        "应用只读取你在 Android 系统文件选择器中主动选中的文件，不会自行浏览其他文件。"
                    )
                    SettingsDivider()
                    SettingsInfoRow(
                        "联网功能按需开启",
                        "AI、天气和版本检查只在使用时联网；使用第三方服务前，请留意对方的隐私政策、计费和数据处理方式。"
                    )
                }
            }

            item(key = "about-changelog-heading") {
                AboutSectionHeading(
                    title = "更新日志",
                    summary = "每一次打磨，都可以在这里找到。"
                )
            }
            item(key = "about-changelog") {
                // One continuous panel. Canvas clipping avoids a texture as tall as all expanded
                // versions; each details animation still owns only its own small graphics layer.
                AboutGlassPanel(darkTheme, Modifier.fillMaxWidth(), longContent = true) {
            changelogItem(
                    "1.2.7_beta5",
                    "AI 导入接入课程助手，后续可按指定课程、日期或周次细改完整草稿；仅修改本周时保留其他周的安排，所有修改仍需预览确认。\n" +
                    "导入会话支持 Markdown、逐节课程卡片、中途停止和补充要求；历史轮次直接展开，进入时滚到最新内容，后续交付物持续保留。\n" +
                    "导入读取已启用的助手记忆，提示助手先核对原文、谨慎调整并保留无关内容；修复新任务通知打开旧进度页，以及取消确认后按钮失效的问题。\n" +
                    "每日免费 AI 支持按云端下发顺序直接连接多个供应商和模型，不可用时自动切换；修正错误被统一显示为额度已用完的问题。\n" +
                    "教务导入点击后立即显示加载并防止重复点击，脚本每次隔离执行，统一显示执行错误并补齐桥接及网络超时；重叠课程保留原始时间，提示冲突并按开始时间优先展示。\n" +
                    "导入课程默认采用彩色文字与轮廓光，其余数值保持默认；修复课程管理为单门课程添加安排时的闪退。\n" +
                    "新增“显示非本周课程”，以当前浏览周为准，灰色卡片避让本周课程及其他非本周卡片；节假日调课可设置全天占位，无课假日也能显示，实际补课正常呈现。\n" +
                    "课程卡片内容布局支持默认、顶部居左、顶部居中、中部居左、中部居中五项，默认沿用原来的地点在上、课名居中、教师在下的布局，并在多课表自定义中增加非本周课程入口。\n" +
                    "备份与恢复新增独立 WebDAV 分类，支持页面内登录、胶囊输入框和密码显示切换；连接后原地叠化进入管理页，可手动上传、浏览远端备份及下载预览后确认恢复。\n" +
                    "WebDAV 可选自动备份，频率通过菜单选择；发现更新的远端备份后提示，确认才恢复。复用原 .sleepdown 备份范围，连接凭据不进入普通备份。\n" +
                    "通用设置新增质感、性能、超级性能三档材质，保留原手动参数；超级性能关闭密集控件的液态玻璃和光感效果，保留全屏场景模糊，退出后恢复原参数。\n" +
                    "超级性能统一普通控件和 Popup 的半透明底色及描边，Dock 使用灰色选中框，课程编辑器可半透明呈现；隐藏液态玻璃调整入口，材质分级只显示当前档位说明。\n" +
                    "模型选择改用紧凑 Popup，并沿用设置菜单的展开动效；二级菜单同宽，父菜单缩小后退并冻结，按住悬停可展开二级菜单并继续滑动选择。\n" +
                    "调整带停课、补课角标的日视图课程编辑形变，保持原卡材质和正文比例；修复周视图触发顶栏渐变模糊后切到日视图仍保留的问题。\n" +
                    "修复小组件图片背景开关、系统添加预览和今日／今明课程缩略图；深浅色跟随系统，4×2 保留两列并改善多课程显示与标题大小，小米设备提供桌面手动添加指引。\n" +
                    "调整小米超级岛及测试通知的发送逻辑，测试通知不再覆盖课程通知；课程组件过滤已结束的课程并在结束边界刷新，调休保存后重新安排课程提醒。\n" +
                    "并列导航默认开启，深色模式默认跟随系统，Beta 更新执行一次默认开启迁移；统一设置说明文字。\n" +
                    "减少课程玻璃的重复采样与页面切换的重复计算；超过 10 张卡时使用普通滑入并保留圆角裁切，非本周课程也进入甩尾分组。"
                )
            changelogItem(
                    "1.2.7_beta4",
                    "AI 导入持续显示模型输出，生成完整预览后自动收起处理过程，仍可展开查看；不提供思考内容的模型会被要求返回阶段摘要。\n" +
                    "AI 导入支持中途停止、补充要求并继续处理；文件缺少必要信息时可向用户提问。新建或覆盖课表后自动打开目标课表，并引导设置开学日期与当前周。\n" +
                    "导入预览补齐每条课程的上课星期，与上课时间、节次和周次一起展示，便于区分同一门课在不同日期的安排。\n" +
                    "修复云端下发的部分 AI 配置鉴权方式不匹配、导致无法连接的问题，优化 AI 设置中的连接测试入口。\n" +
                    "优化首页加号与三点菜单的二级展开，区分中心弹窗与页面转场；返回时提前叠化为原始按钮，调整页面切换的回弹与速度，减少首页切换和玻璃绘制的重复工作。\n" +
                    "作息编辑改为按钮连续形变、作息行逐行进入、时间轴随后淡入，改善展开时的布局衔接。\n" +
                    "增强课程卡片浅亮彩色文字的可读性，扩大并柔化文字阴影；无壁纸时也可调整卡片透明度，并保留最低不透明度。调整胶囊表头底部轮廓光，使圆角处过渡更柔和。\n" +
                    "修复日视图滚动或切换日期后偶发的玻璃缺失，调整停课、补课卡片与编辑器之间的形变交接，保持原卡片的圆角、文字比例和角标布局。\n" +
                    "统一 Popup 的连续曲率圆角、模糊、柔和描边和浅色阴影，加入跟手高光、形变与滑动反馈；修复设置卡片间隙的背景采样，个性化弹窗内的菜单入口不再持续压灰。\n" +
                    "精简设置首页与通用设置的说明，增加彩色图标，调整分组、开关尺寸与对齐。深浅色选项加入随默认日／周视图变化的课表预览；应用图标深浅色修改后不再立即重启应用。\n" +
                    "优化小组件设置的文字和滑块样式，关闭自定义背景后保留已选图片；通知设置统一使用“自动勿扰”，合并权限入口并精简说明。\n" +
                    "获取到与已有内容完全相同的节假日调休安排时直接提示，避免重复设置；保存后不再提示未保存，统一日期与标题样式。\n" +
                    "赞赏页面优先展示赞赏码，为前三位支持者增加领奖台展示。"
                )
            changelogItem(
                    "1.2.7_beta3",
                    "新增独立作息管理页面，可在课表详细设置中进入。多个课表可共用同一套作息，原有相同作息自动归并，支持新建、编辑、复制和删除。\n" +
                    "切换作息时可选择按原节次或按实际时间对齐，并预览受影响的课程。目标节数不足时保留超出部分，切回原作息后可恢复；完全无法匹配的普通课程仍可在课程列表中找到。\n" +
                    "切换作息不会覆盖课程自带的上课时间，整门和逐节自定义时间都可保留；首页、课程详情、小组件和提醒跟随当前安排。\n" +
                    "课表详细设置中编辑作息仅影响当前课表；在作息管理中修改共用方案前，会提示受影响的课表。导入、分享及备份恢复保留课程时间与作息关系。\n" +
                    "新增上课自动勿扰和静音／震动，可设置提前开启与延后恢复，整门课程期间持续生效，包含课内休息；完善开关动画与权限引导。\n" +
                    "优化作息管理的卡片和操作按钮，调整复制图标与新增按钮；作息对齐选项和教务工具选择弹窗统一样式，内容过长时可在弹窗内滚动。\n" +
                    "调整课程卡片滑动时的玻璃绘制和位置更新，减少重复计算。\n" +
                    "修复切换作息后，AI 删除部分节次可能改写剩余课程原来的上课时间、导致切回后消失的问题；按指定周删除和只修改课程名称等信息也会保留原始安排。"
                )
            changelogItem(
                    "1.2.7_beta2",
                    "重做平板与横屏布局：悬浮侧栏集中呈现今日、课程表、设置、课表操作和导入入口，也可收为顶部文字导航。今日助手、课程管理和设置采用双栏布局，课程管理保留双列课程卡片。\n" +
                    "手机竖屏新增并列导航，可在通用设置中切换。今日、课程表和设置并列显示，蓝色加号集中提供添加与导入，进入设置时自然收回 Dock。\n" +
                    "优化侧栏、顶部导航和 Dock 的玻璃、描边与阴影；关于应用的渐变延伸到侧栏下方。多课表切换保留黑场卡片堆叠，侧栏持续可用，横向课程和堆叠卡片可自然延伸到侧栏下。\n" +
                    "三点与加号菜单采用更紧凑的液滴展开和收回，加入动态模糊与触觉反馈。课程管理、教务导入沿完整菜单做圆角无缝交接；横屏课程编辑等表单居中展示，保留原有内容与材质。\n" +
                    "修复横屏弹窗按钮、滑块与今日助手卡片的触摸冲突，改善侧栏手势、顶部导航滑动、跟手高光、堆叠切换闪烁及明暗背景衔接。\n" +
                    "自动刷新学校列表与教务导入工具页对齐，恢复完整入口读取和搜索，保留需要网页操作的手动刷新方式。\n" +
                    "完善课程编辑与删除范围，按具体课程、星期和周次处理；修改课程信息时保留原周次，缩短学期前提示受影响课程，并补齐未保存修改提醒与作息调整后的课程映射检查。\n" +
                    "统一同一课程卡片的文字颜色，增强复杂壁纸下的文字可读性；个性化设置的文字排布菜单、字段对齐和入场动画更加一致。\n" +
                    "修正 Android 14 及以下课程勿扰的状态恢复，已识别到用户手动调整时保留用户的选择。"
                )
            changelogItem(
                    "1.2.7_beta1",
                    "今日助手按课前、课中和课间展示提醒与倒计时；课程状态切换前的最后三秒，日视图和周视图都会切换到全屏倒计时，结束时的冲击波会带动首页卡片与文字。\n" +
                    "周视图课程卡片新增三种文字排布，可在个性化设置中选择默认、全部居中或从上到下铺满；地点和教师开关可独立调整。课程文字在壁纸上更清晰，没有壁纸时也不再额外加重阴影。\n" +
                    "编辑作息时，调整后续时段的起点不再被前面已设置的时间锁住；节次调整、保存和按钮布局也更加顺手。\n" +
                    "小米超级岛改善课中倒计时刷新与提醒恢复，测试按钮会给出实际发送结果，并可从岛上打开勿扰模式。\n" +
                    "更多教务入口可参与自动刷新；需要网页操作的学校仍可随时打开教务页面手动刷新。导入和刷新时会尽量保留课程自带的上课时间。\n" +
                    "优化 AI 助手的操作确认与错误提示，以及周视图切换、课程卡片和首页玻璃效果的动画细节。"
                )
            changelogItem(
                    "1.2.6",
                    "周视图默认使用无界模式，课程、节次和星期栏自然融入整页背景；仍可在设置中切换普通模式。开启天气后，周数旁可直接看到天气与气温。\n" +
                    "新增渐进式甩尾动画：切周时课程跟随手指移动，再按行列依次追上；同一行保持原有间距，相邻行呈现更明显的先后节奏。滑到一半停住、快速反向或连续翻周时，卡片都会柔和回弹；日周视图及首页、设置之间的切换也延续这一动效。\n" +
                    "新建课表默认开启自动计算当前周。设置开学日期后，课表随日期切换周次，并提示尚未开学或学期已结束，无需每周手动调整。\n" +
                    "新增调休课表：可在线查看法定节假日安排，预览后采用，也可自行设置停课和补课日期。停课课程以灰色保留在课表中，不再发送上课提醒；补课日按原上课日期展示课程，桌面小组件也跟随实际安排。\n" +
                    "重做节次与作息编辑：新建作息时可分步设置各时段的节数、起点、课程时长和课间；课程与课间可逐分钟拖动调整，支持添加、删除节次及设置零分钟课间。调整首节时间时，后续节次随之顺延；离开已修改的作息前会提示保存。\n" +
                    "周视图滚到顶部后继续下拉，即可唤出 AI 助理；课前提醒卡也能直接进入对话。助理沿用已有聊天记录，理解当前日期和课表，可按描述查找并修改课程的周次、时间、地点、教师等信息；选择课表文件后，仍会先预览、再由你确认导入。\n" +
                    "新增教务课表自动刷新，可选择每天、每 7 天或不自动刷新，覆盖百余所学校的多个教务入口。登录后可手动刷新；会话失效时可重新连接，学校需要校园网或校园 VPN 时也会给出提示。刷新和学校适配信息更新都会显示结果。\n" +
                    "导入课程自带的逐节上课时间会随编辑、复制、备份恢复、分享和日历导出保留，当前节次提示也按课程实际时间显示。WakeUp 口令导入会整理重叠的节次时间；AI 导入在所用模型支持时可实时展示处理进度。\n" +
                    "在兼容设备上，可选择 OPPO／一加／realme 的流体云、荣耀 YOYO 建议或小米超级岛课程提醒。ColorOS 的流体云与实时活动并用时，课前提醒交给流体云，课中、课间及次日提醒继续由实时活动承担；小米超级岛可设置展示内容与光效，使用前需完成 Shizuku 或 root 授权。各模式均提供对应的配置引导。\n" +
                    "日视图会淡化当天已经结束的课程和时间胶囊；课程文字在复杂壁纸上更易辨认，彩色文字随背景明暗调整浓淡，黑白文字保持统一颜色并配以柔和阴影。"
                )
            changelogItem(
                    "1.2.5",
                        "长按课程即可打开快捷菜单，编辑单节课、进入快速编辑模式、复制或移除，都更顺手。继续拖动即可移动课程，调整时长的操作也更加清晰。\n" +
                        "全新课程复制体验：选择本周或全部上课周后，直接回到课表选择目标位置，先预览，再点一次确认。顶部玻璃提示胶囊随操作展开，遇到课程冲突时及时提醒；确认后，课程副本从原位置飞向新位置，以回弹与涟漪落地。\n" +
                        "删除也有了新的告别方式：课程从上到下化为光粒子，随风飘散。删除范围只需确认一次，修复快速编辑时重复弹窗的问题。\n" +
                        "新增「彩色文字」，让课程文字跟随卡片主题色，并根据明暗调整亮度、加粗显示，兼顾配色与阅读。切换课程颜色即时呈现，减少等待。\n" +
                        "提供「简约」「看板娘」两套应用图标及深浅色样式，小组件与实时活动中的图标也可跟随所选风格。\n" +
                        "调整课程卡片光效，去除左右两侧的轮廓光；优化无界模式顶栏的模糊过渡，减少对首行课程的遮挡，让课表更清爽。\n" +
                        "持续优化周视图翻页、课程编辑与弹窗开合，保留熟悉的玻璃模糊和背景缩放，减少动画中的卡顿。\n" +
                        "优化课程编辑和个性化面板的展开节奏，减少等待感；移动、调整课程后的落地反馈更加及时。修复部分课程点击、长按无响应，以及屏幕边缘快捷菜单展开位置不合适的问题。\n" +
                        "修复今日助手「明日安排」遗漏课程的问题，普通节次和自定义时间课程都能完整呈现。\n" +
                        "优化上课、课间与课程结束时的通知刷新，分钟提示更新更及时，并改善 ColorOS 下的显示兼容性。修复夜间跨天时实时活动预览立即消失的问题。\n" +
                        "不连续的上课节次按实际连续时段分别提醒；节次表以外的自定义时间课程归入「其他时间」，特殊安排也能清楚查看。\n" +
                        "改善今日助手对部分模型的兼容性与回复显示，修复无法清空课程备注的问题。\n" +
                        "完善教务网页的「手机版 / 电脑版」切换，改善部分门户页面显示不全、登录弹窗被裁切的问题，网页与顶部工具栏的衔接也更自然。\n" +
                        "小组件设置新增「添加到桌面」，选好样式即可添加；修复今日课程 4×2 小组件的添加预览尺寸。\n" +
                        "新增「接收 Beta 版更新」开关：默认接收正式版，也可以主动体验新功能；使用测试版时，同版本正式版发布后也能正常收到更新提示。"
                )
            changelogItem(
                    "1.2.4",
                    "课程卡片新增质感轮廓光：卡片边缘带有一圈柔和的亮光描边，让课程卡从壁纸中浮起、更有层次；同时新增折射强度控制，可以在个性化面板中分别调节\n" +
                        "周视图新增无界模式：星期与日期融入顶栏，课程可滚动到屏幕顶部，并可隐藏上一周/下一周按钮；通用设置中开启，首次更新会弹出切换引导\n" +
                        "重新打造课程编辑弹窗：玻璃外壳先展开到位，随后标题、输入框、选择器按从上到下的顺序，从弹窗中间以轻微缩放与透明度渐变逐行飞入，展开与内容载入节奏更紧凑\n" +
                        "优化三点菜单的拖拽变形反馈，拖动时更加跟手灵动\n" +
                        "课程管理、教务导入等入口在平板上会先收回三点菜单，再以系统过渡打开，返回更顺滑、不再跳帧闪黑；无界模式下大屏课程卡片的圆角与文字随调节幅度整体放大\n" +
                        "修复手动导入、添加单节课、个性化等弹窗内的壁纸采样与真实壁纸错位的问题，玻璃透出的背景与背后画面保持一致\n" +
                        "优化无壁纸状态下的背景与明暗可读性，没有壁纸时界面更清晰自然\n" +
                        "优化调色盘的布局与操作\n" +
                        "优化设置页的布局与操作逻辑，信息层级和返回路径更清晰\n" +
                        "修复教务工具页右上角按钮在浅色模式下错误带灰色着色的问题\n" +
                        "精简实时活动相关文案表述\n" +
                        "完善 AI 请求端点配置兼容"
                )
            changelogItem(
                    "1.2.3",
                    "重新设计教务导入页与网页内页，优化工具排版、字母选择栏、悬浮 Dock、渐变模糊顶栏、玻璃灵动岛和相关弹窗；AI 教务与通用工具的历史页面会从 Dock 处自然展开\n" +
                        "完整接入拾光仓库 2.0 的官方学校索引、适配脚本和交互方式，支持提交课程、开学日期、学期周数与完整节次时间；学校索引和脚本改从 Gitee 获取，缓存超过七天后自动检查更新\n" +
                        "AI 教务导入首次使用时只提醒一次后台网络风险，取消后仍可导入；导入进度接入实时活动，数据格式或字段校验异常时最多自动修正三次，并重做预览顶栏与内容展开动画\n" +
                        "新增上课中、课间与明日课程实时活动，可分别设置课程实时活动、状态栏短文案、操作按钮、明日提醒及提醒时间；明日课程预告仅在第二天有课时显示五分钟\n" +
                        "日视图新增标准与双日两种模式；标准模式会在今日课程全部结束后提前展示明日课程，双日模式沿用原日视图排版连续显示两天课程\n" +
                        "修复教务 Dock 输入时的光标闪烁和焦点异常，优化中心弹窗跟手弥散光与遮挡恢复叠化，恢复浅色顶栏、返回按钮和搜索控件阴影，并恢复多课表详细设置的返回保存询问"
                )
            changelogItem(
                    "1.2.2",
                    "优化教务系统导入页，修复网页重复加载、页面跳转闪烁等问题，并优化教务适配器选择，同一学校存在多个导入工具时可以查看并选择对应适配\n" +
                        "修复平板小组件排版异常的问题\n" +
                        "重构自定义背景小组件的课程卡绘制方式，修复模糊卡片背景与课程文字错位的问题\n" +
                        "修复部分设备上首页左上角日期标题和右上角按钮拖拽放大后被裁切的问题"
                )
            changelogItem(
                    "1.2.1",
                    "建立统一的液态玻璃渲染框架。首页、二级页面、课程卡片、中心弹窗和悬浮菜单现在使用一致的背景采样与材质链路，复杂壁纸下的模糊、折射和前景色表现更加统一，也减少了弹窗背景变黑、退化为纯色或局部失去玻璃效果的情况。\n" +
                        "建立统一的无缝动画框架。多课表快速设置进入详细设置，以及首页前往课程管理、教务导入、手动导入和设置详情等页面时，会从实际点击位置自然展开，并完整衔接背景与页面内容；打开和返回时的跳帧、闪黑、内容重叠与动画偶尔被跳过的问题得到改善。\n" +
                        "正式启用大面积液态玻璃渲染优化。周视图课程较多时，多张课程卡片可以共享更高效的背景处理，同时继续保留各自独立的颜色、文字、阴影、高光和交互效果，在不牺牲现有视觉表现的前提下降低重复渲染开销。\n" +
                        "优化弹窗打开、停留和关闭过程中的课程卡片渲染调度，减少重复模糊、短暂黑底和弹窗关闭后的集中重绘；连续打开页面、切换课表或操作大量课程时更加稳定。\n" +
                        "统一各处中心弹窗的外观设计。\n" +
                        "优化折叠菜单的样式与动画。设置下拉菜单、级联菜单和模型快捷选单统一了圆角、明暗配色、玻璃材质、展开动画与层级关系，二级菜单会贴合真实入口展开，在浅色和深色壁纸上都更清晰自然。感谢 @HaoZai000 提供的设计参考与帮助。\n" +
                        "重新设计教务导入页。学校与工具按类别重新组织，列表信息更清楚；搜索框改为悬浮在页面底部，聚焦、输入、搜索和取消之间使用连续动画，更适合单手操作。\n" +
                        "优化教务导入页的字母索引与滚动反馈。浏览学校时会显示当前位置和滑动进度，停止操作后自然收起，查找学校更直观，也不会长期遮挡页面内容。\n" +
                        "更新至拾光仓库 2.0 索引与适配资源，优先读取新版学校索引，并扩充、更新多所学校的教务适配；遇到未来新增字段时也能更安全地兼容处理。\n" +
                        "AI 手动导入的同一个口令输入框现在可以识别 SleepDown、WakeUp 与星链课表口令。应用会自动判断口令类型，并将解析结果送入同一套预览与确认流程。\n" +
                        "文件导入新支持 PDF、图片、XLSX、CSV、TSV、DOCX、PPTX、ODS、TXT、Markdown、JSON、XML 和 HTML。PDF 会优先提取可读文字；当文档没有有效文字时，再交给支持图片识别的模型处理，兼顾速度与识别效果。\n" +
                        "新增 DeepSeek V4 Flash Vision Exp 多模态模型适配。\n" +
                        "改善导入历史、详情预览和附件页面之间的衔接，历史任务可以进入统一详情页继续查看，长内容、键盘和附件预览的切换也更加连贯。\n" +
                        "重构周视图行高计算，启用全新的动态适应算法。应用会根据当前窗口、安全区、表头高度、节次数量和字体大小计算合适的“自适应”高度，并以此为中心在“紧凑—自适应—宽松”三档之间无级调节；手机和平板、不同节次数与字体比例下都能保持更合理的可读性和空间利用率。\n" +
                        "新增周视图课程卡片圆角选项，可以从更利落的轮廓平滑调整到更柔和的圆角，并与现有玻璃卡片样式共同保存。\n" +
                        "新增“纯色”模式：所有课程使用所选主色，整体观感简洁统一，适合偏好克制配色的用户。\n" +
                        "新增“渐变”模式：课程卡片在同一色相内形成自然的深浅变化，增加层次感的同时避免深色端变成突兀的黑灰色。\n" +
                        "新增“彩色”模式：根据采样自壁纸的多组色彩为不同课程稳定分配颜色，在保持整体协调的同时增强课程之间的辨识度；同一课程的颜色分配会保持一致。\n" +
                        "新增配套调色盘，并重新设计“从壁纸取色”功能。可以在壁纸预览上拖动取色点，直接提取适合课程卡片的颜色。\n" +
                        "加深并扩展周视图当前日期胶囊，显示五天或七天时会根据实际列数动态计算宽度，使当前位置更加醒目且始终与表头边界正确对齐。\n" +
                        "优化系统提示词与工具调用流程，减少不必要的上下文和重复调用。今日助手在读取课表、作息和应用设置时响应更快，也能用更少的 Token 完成同类任务。\n" +
                        "调整任务执行策略。涉及课程或设置修改时，今日助手会先整理计划、影响范围和可能的时间冲突，等待确认后再执行，复杂操作更稳妥。\n" +
                        "更换更加准确的天气数据源，并完善定位、缓存与失败回退逻辑，日程建议和天气回答更加可靠，网络短暂波动时也能尽量提供最近一次可用结果。\n" +
                        "改进联网搜索结果的证据判断，只有真正取得搜索依据时才会将网络信息写入回答，降低无效结果对结论的干扰。\n" +
                        "修复左上角标题栏在部分机型、特殊状态栏高度或显示比例下被裁切的问题。\n" +
                        "修复平板端带背景小组件中，轮廓光课程卡片发生错位的问题，使背景、轮廓和课程内容在不同小组件尺寸下保持一致对齐。"
                )
            changelogItem(
                    "1.2.0",
                    "移除原来的加号菜单和日视图/周视图切换滑块，将相关功能重新整合进功能更完整的三点菜单。首页顶栏更轻、更整洁，课程与日期重新成为视觉中心，常用入口的位置和操作逻辑也更加统一" +
                        "；三点菜单采用全新的液态玻璃外观，从三点按钮打开和收回时更加连贯，拖动时也有更自然的弹性反馈与跟手高光。除了原有功能，现在还可以直接跳转到指定周数，快捷进入多课表管理和全新的课程管理页，让一个入口承担更多日常操作" +
                        "；同名课程会自动归并，并以双列错落卡片展示。长标题、教师、地点和每一条课程安排都可以清楚阅读。进入详情后，可以统一修改课程名称、颜色、教师、地点与备注，也可以单独添加、编辑或删除某一条安排。星期、节次、周次、单双周等信息集中在同一页管理，课程较多时也更容易梳理" +
                        "；课程现在支持自定义开始和结束时间，可以脱离固定节次设置更准确的上课区间。周视图会按照真实时间比例显示课程，并在卡片上下边缘标出起止时间。课程管理、冲突判断、备份导入和今日助手也能够识别这些自定义时间" +
                        "；在周视图中长按课程即可进入编辑，拖动卡片时，指尖和液态玻璃卡片会出现跟手的光场效果，移动过程更灵动。课程可以直接拖到其他星期或节次，右下角角标则用于连续调整课程时长，拖动与缩放互不干扰。松开手指后，课程会立即飞向目标格并播放震荡回弹，不再停在半空等待保存。落点产生的余波会继续带动附近课程轻微位移、缩放和回弹，再逐渐自然消散。遇到冲突或取消移动时，也会沿同样的动画返回原位。切换课表后，长按编辑仍然可以直接使用" +
                        "；针对个性化设置、三点菜单、手动导入、教务导入、课程管理和课程编辑等页面的打开、关闭与切换进行了性能优化，降低周视图和复杂表单同时显示时的负载，减少卡顿、掉帧、闪现和内容跳变，让原有动画在更多场景下保持稳定流畅" +
                        "；灵动岛缩略态的倒计时精简为“X分钟”，不再显示“还剩”。原来的短标签模式改为直接显示课程名称，有限空间里的信息更清楚，也更容易快速识别当前课程" +
                        "；今日助手和 Agent 服务进一步增强稳定性，个别模型服务商暂时不可用时，会自动尝试其他可用服务。Agent 现在也能读取和修改课程的自定义时间，并在执行课程或设置修改前展示计划、影响范围和冲突信息，确认后再完成操作" +
                        "；新增完整的隐私政策说明，清楚介绍课表数据保存在什么位置、哪些功能会联网以及各项权限的用途。用户自行配置的 API Key 等敏感信息只保存在本机，不会写入普通课表备份"
                )
            changelogItem("1.1.5", "新增完整的数据备份与恢复功能，可将课表、作息、应用设置、小组件外观及相关图片保存为一个备份文件，恢复前会先检查文件并展示内容预览，替换数据也会再次确认，帮助你更安心地迁移和保管数据；新增“今明课程”桌面小组件，可同时查看今天剩余课程和明天的课程安排，并支持独立设置背景图片、取景、缩放、模糊和亮度；通用设置新增液态玻璃自定义选项，可以自由调节玻璃组件的清晰与模糊程度；现在液态玻璃开启和关闭时的课程卡片颜色、透明度、模糊及字体大小会分别保存，不再和液态玻璃课程卡片共享保存参数；优化平板设置页面的双栏浏览、返回按钮和顶部标题，修复部分设置返回后没有保存的问题；优化 AI 导入页面，改善导入历史和手动导入的显示与动画；修复个性化滑块拖动时跳动以及 100% 吸附点位置不准确的问题；重新设计关于应用页面，加入应用官网、项目仓库、反馈入口，新增功能亮点介绍页，优化深色模式、更新日志和网页打开体验；调大平板横屏课程卡片文字，查看课程名称、地点和教师信息更加清晰。")
            changelogItem("1.1.4", "修复普通设置保存错误使用个性化字段合并，跟随系统、手动深色模式、首页模式及后台隐藏等设置现在可以稳定持久化；设置详情页返回前等待最新配置写入完成，避免异步保存被页面销毁取消；修复手动切换深色模式时二级设置页触发启动器别名切换、导致 ColorOS 任务被回收并表现为闪退的问题；修复从设置页进入课表详细设置后修改无法持久化的问题，并统一使用课表设置保存确认弹窗。")
            changelogItem("1.1.3", "AI 导入现已使用结构化局部编辑，只提交需要修改的课程、周次或节次，减少重复传输完整课表产生的 Token 消耗，并统一支持全部模型供应商；AI 修改过程中会持续展示处理进度、本轮具体改动摘要和完整历史修改记录，长时间推理不再被过早中断；重新实现导入历史预览与详情页的无缝动画，进入和返回均在同一页面完成，减少闪烁、重复卡片和布局跳动；优化模型快捷选单、手动导入控件及课程编辑选择器在不同玻璃亮度下的文字与控件配色；调整添加单节课页面的垂直排版、选择器宽度和离散周次显示；修复小组件背景编辑时预览区域跳动、交接闪烁以及顶栏字号不一致的问题，并提升多处交互与动画稳定性。")
            changelogItem("1.1.2", "新增由 SleepDown 提供的每日免费 AI 额度，未配置模型服务也可使用今日助手、AI 对话与 AI 教务导入，并在共享额度用尽时提供明确提示；支持 OpenAI Responses 与兼容接口，为 Agent 和 AI 导入加入快捷模型选择、推理强度设置、视觉附件及更多兼容模型；全新设计 AI 教务导入页面，集中呈现导入对话、文件附件、网页识别与视觉截取入口，并新增可保留文件导入上下文、继续历史对话的导入历史页面，以及更连贯的打开、返回和滑动删除交互；优化课程编辑与合并逻辑，相同信息的跨星期、跨周课程可统一编辑，并完善周次、自定义单双周和星期选择；全面改进手机、平板横屏和桌面小组件的动态排版，优化日视图、周视图、浮层、字体缩放、课程组居中及不同组件尺寸下的排版，并为设置壁纸的小组件课程卡片加入质感轮廓光效果；优化无壁纸状态的渐变背景、玻璃采样、折射与明暗可读性，统一弹窗按钮、菜单材质和多处无缝动画；修复兼容接口附件能力识别、个性化设置互相回撤、多课表页面顶栏闪现、输入框长按闪烁、历史记录闪帧、小组件更换背景时可能应用失败并影响系统相册响应及多项稳定性问题。")
            changelogItem("1.1.1", "本次更新进一步缩小安装包，减少安装后的空间占用，并优化壁纸、桌面组件和助手历史等数据的存储，长期使用更省空间；修复部分用户从旧版本升级后闪退的问题；新增武汉科技大学教务导入适配；优化平板等大屏设备的界面布局与操作体验，并提升整体性能和稳定性。支持直接覆盖安装，已有课表和设置不会丢失。")
            changelogItem("1.1.0", "桌面小组件现在也能自由个性化，4×2、2×2 今日课程组件和今日助手组件可分别设置背景图片，支持独立调整取景、缩放、模糊与亮度，保存前即可预览实际效果；今日助手现在支持发送图片，并加入可随时关闭、查看和编辑的长期记忆，课程查找更准确，工具执行状态更清晰，长回复与图片预览也更加流畅稳定；新增更具流动感的液态动画，首页加号菜单、个性化面板、添加课程、手动导入和教务导入之间能够自然衔接，课程编辑弹窗的展开、背景景深与收回动画也更加连贯，并会收回到课程修改后的位置；新增动态模糊过渡，内容展开或收起时会以自然的模糊效果衔接，减少文字和页面的突兀跳变；新增课程冲突处理，修改星期、节次、周次、单周课程或全部周课程时，应用会主动提醒本次新增的冲突，你可以先保留修改并跳转到冲突周，再点击“冲突”将课程移到最近空位，被单独调整的周次也会正确保留，不会再次并回整段周次；新用户将不再默认启用看板娘壁纸，默认日间、夜间壁纸已更新，并会跟随应用深色模式自动切换，应用图标也已换新，浅色与深色模式各有对应样式；调整课程卡片的玻璃采样层级，长按提起或移动卡片时，玻璃材质可以正确透出下方课程，层次更加自然；统一优化课程编辑、弹窗、滑条、加号菜单、个性化面板和教务页之间的动画衔接，减少闪烁和突兀切换；移动课程或调整节次时不再重新打乱整页课程配色，多彩卡片的颜色更加稳定；压缩并整理课程编辑弹窗的间距，常用信息更集中，操作更顺手；优化今日助手的流式回复、图片处理和复杂动画性能，长内容场景下更加流畅；移除首次启动时的遮罩展开动画，壁纸会在首个可见画面直接呈现，随后再自然进入首页；课程保存后，编辑弹窗会收回到修改后的卡片位置，动画方向与最终结果保持一致。")
            changelogItem("1.0.9", "今日助手全面升级，能够根据需要读取当前课表和设置，连续完成查询、修改、确认结果与撤销操作，并支持 MiMo 联网搜索；新增课程卡片和今日助手打开时的背景随动缩放效果，课程卡片展开与收回采用更自然的抛物线运动轨迹，配合弹性缩放和更流畅的页面交接，动画更加灵动；增强课程、节次、作息方案和个性化设置的智能调整能力，修改节次后可更合理地处理原有课程安排；修复今日助手偶尔读取错误课表、工具调用中断、回复内容缺失，以及实时活动倒计时停止刷新、测试提醒无法取消等问题。")
            changelogItem("1.0.8", "桌面小组件新增今日助手，展示当前或下节课、倒计时、地点与教师、上下课时间、今日课程数量、天气和预警，并补齐今日课程与今日助手三款小组件在系统选择页的独立名称和预览；修复升级后部分课表的节次时间与详细设置被错误重建为默认值的问题，完善多作息方案保存和数据库迁移兼容；将周次切换字符替换为矢量图标，并修复添加单节课选择器层级等交互问题。")
            changelogItem("1.0.7", "重构个性化与壁纸调整流程，新增卡片式壁纸裁切页面、横竖屏独立构图及更连贯的无缝过渡，并优化配色布局、玻璃材质、壁纸模糊与全部调节滑条的性能；强化课表详细设置与课程编辑，补充中午时段、总节次配置、时段防重叠和多课程翻页编辑，完善节次选择器、特殊课间以及 ICS 导入导出的完整作息信息，并支持从系统分享或打开方式直接调用 SleepDown 导入 ICS；重新设计今日助手卡片，集中展示课程、天气和预警信息，保留对话入口并增强自然语言课程与设置操作的识别稳定性；改进首页文字可读性和非液态玻璃课程卡片的高斯模糊效果，修复详情页进入闪帧、后台任务卡片隐藏范围及多项动画、数据与交互问题。")
            changelogItem("1.0.6", "重构课表详细设置与节次时间管理，支持上午、下午、晚上分段配置、多套作息方案、自动匹配、特殊课间与手动微调，并完善保存确认、课程节次重映射和不同课表间的数据隔离；增强今日助手的课程与设置操作能力，修复操作按钮缺失、切换课表后当前节次不显示以及生成文案后首页卡顿等问题；优化日视图、周视图、课程卡片与多课表管理的动画性能和交接效果，补全开学前与学期结束后的日期边界处理；更新下载新增后台持续下载与原生实时进度通知。")
            changelogItem("1.0.5", "优化今日助手样式与动画，修复日视图布局错误；重新设计桌面小组件，新增2x1样式；性能优化减少卡顿。日视图与周视图表头新增当前节次标识，上课时段一目了然。课表详细设置页面精简标题、取消二次确认、动画更流畅。节次时间编辑改版：可添加多条大课间，自动匹配一键重算，时间线合并展示。")
            changelogItem("1.0.4", "优化课程卡片、设置页面和今日助手的动画效果，切换更流畅，减少闪烁感；统一课表设置弹窗中的日期选择器、按钮和浮层样式；改进不同字体大小下的排版适配，文字显示更完整。")
            changelogItem("1.0.3", "多课表管理页面全新改版，卡片堆叠效果更灵动流畅；设置页跳转动画更连贯；整体玻璃质感优化，文字更清晰易读；日期选择器和课程编辑弹窗布局改进，操作更顺手。")
            changelogItem("1.0.2", "扩展今日 Agent 能力边界，支持结合当前课表理解更多课程与设置需求，并可引导进入对应功能；优化设置分类与信息层级，常用配置更易查找；优化首页日视图与周视图的跟手切换动画，日期、周次及课程内容衔接更自然；调整日视图课程卡片圆角，使卡片层级与整体界面更加协调；新增 ICS 课表文件导入与导出分享，可通过系统分享器保存或发送课表；通用教务导入会保存曾打开的教务站地址与登录状态，方便下次快速进入；新增每日自动检查更新功能，发现新版本时展示版本号和更新日志；通用设置与通知设置改为修改后直接保存，不再需要二次确认。")
            changelogItem("1.0.1", "新增今日助手，可结合当天课程与时间生成日程提醒，并支持快捷提问；新增课程卡片彩色模式，可从壁纸提取代表色并为同页课程分配不同配色；优化周视图课程卡片排版，课程名称、地点与教师信息层级更清晰。")
            changelogItem("1.0", "优化二级页面排版。")
                }
            }
        }
    }
}
