package com.xiaomanjun.sleepdownschedule.feature.home.week

// [fork] 本仓库独有功能：周视图冲突课堆叠卡 + 点击展开浮层。
// 实现集中在本文件；上游共享文件只保留带 [fork] 标记的最小调用行，方便后续合并上游：
//   - WeekCourseLayout.kt：renderedWeekSegments 委托本文件的 renderedWeekSegmentsForked；
//     WeekDayColumn 循环内使用 forkStackLayerIndexes / forkStackLayer，末尾调用 ConflictGroupExpandOverlay
//   - WeekCourseBlock.kt：conflictGroupCount / onExpandConflictGroup 参数与 clickBody 分支

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.IntOffset
import androidx.compose.ui.geometry.IntSize
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import com.kyant.shapes.RoundedRectangle
import com.xiaomanjun.sleepdownschedule.CourseEntity
import com.xiaomanjun.sleepdownschedule.PeriodEntity
import com.xiaomanjun.sleepdownschedule.ScheduleConfigEntity
import com.xiaomanjun.sleepdownschedule.domain.course.WeekConflictGroup
import com.xiaomanjun.sleepdownschedule.domain.course.WeekCourseSegment
import com.xiaomanjun.sleepdownschedule.exactTimeWeekPlacement
import com.xiaomanjun.sleepdownschedule.feature.home.currentWindowSizeDp
import com.xiaomanjun.sleepdownschedule.feature.home.day.occurrenceOverrideKey
import com.xiaomanjun.sleepdownschedule.feature.home.overlay.CourseShortcutRequest
import com.xiaomanjun.sleepdownschedule.feature.home.overlay.LocalCourseShortcuts
import com.xiaomanjun.sleepdownschedule.glass.ui.CourseGlassCard
import com.xiaomanjun.sleepdownschedule.hasCustomTime

/** 冲突组堆叠渲染：组内每门课渲染最早一段；focus 课程排最前（zIndex 最高、完整可见），
 *  其余按「有教室优先 → 跨度长的优先 → 起始节次早的优先」排列，保证顶卡是有教室的长课。
 *  非冲突组保持原逻辑（自定义时间的课只渲染起始最早的一段，避免重复叠卡）。 */
internal fun renderedWeekSegmentsForked(
    conflictGroups: List<WeekConflictGroup>,
    conflictFocusCourseId: Long?,
    conflictFocusCourseKey: String?
): List<WeekRenderedSegment> = buildList {
    conflictGroups.forEachIndexed { groupIndex, group ->
        if (group.courses.size <= 1) {
            val visibleCourse = group.courses.first()
            val visibleSegments = group.segments.filter { it.course.id == visibleCourse.id }
            val segments = if (visibleCourse.hasCustomTime() && visibleCourse.arrangementProjection == null) {
                visibleSegments.minByOrNull { it.startPosition }?.let(::listOf).orEmpty()
            } else {
                visibleSegments
            }
            segments.forEach { segment ->
                add(WeekRenderedSegment(groupIndex, group, segment))
            }
            return@forEachIndexed
        }
        val perCourseSegments = group.segments
            .groupBy { it.course.id }
            .map { (_, list) -> list.minByOrNull { s -> s.startPosition }!! }
            .sortedWith(
                compareByDescending<WeekCourseSegment> { !it.course.location.isNullOrBlank() }
                    .thenByDescending { it.endPosition - it.startPosition + 1 }
                    .thenBy { it.startPosition }
            )
        val ordered = buildList {
            val focus = conflictFocusCourseId?.let { id ->
                perCourseSegments.firstOrNull { it.course.id == id }
            } ?: conflictFocusCourseKey?.let { key ->
                perCourseSegments.firstOrNull { it.course.occurrenceOverrideKey() == key }
            }
            if (focus != null) add(focus)
            perCourseSegments.forEach { segment ->
                if (focus == null || segment.course.id != focus.course.id) add(segment)
            }
        }
        ordered.forEach { segment ->
            add(WeekRenderedSegment(groupIndex, group, segment))
        }
    }
}

/** 每个冲突组在渲染列表中的首个下标（组内顶卡），供堆叠层计算使用。 */
internal fun forkStackLayerIndexes(renderedSegments: List<WeekRenderedSegment>): Map<Int, Int> =
    renderedSegments.withIndex()
        .groupBy { it.value.groupIndex }
        .mapValues { (_, entries) -> entries.first().index }

/** 冲突堆叠层：0 = 顶卡（完整可见），底卡向右下错位露出边缘。 */
internal fun forkStackLayer(listIndex: Int, firstIndexes: Map<Int, Int>, groupIndex: Int): Int =
    (listIndex - firstIndexes.getValue(groupIndex)).coerceAtLeast(0)

internal fun forkStackOffsetDp(stackLayer: Int): Dp = (stackLayer * 3).dp

internal fun forkStackedZIndex(groupIndex: Int, stackLayer: Int): Float =
    groupIndex - stackLayer * 0.05f

internal data class ForkExpandedConflictGroup(
    val groupIndex: Int,
    val anchor: Rect,
    /** 被点击卡自身的起始节次，作为浮层对齐的参考原点 */
    val clickedStartRows: Int
)

internal data class ForkConflictExpandEntry(
    val course: CourseEntity,
    val foldedHeight: Dp,
    /** 相对锚点顶端的纵向偏移（可为负），与折叠前在网格中的位置保持平行 */
    val topOffset: Dp
)

/**
 * 冲突组折叠卡的展开浮层：在原卡旁侧（优先右侧，空间不足时左侧）横向铺开组内全部课程。
 * 每张迷你卡可点击查看详情、长按打开与课表卡一致的快捷操作菜单；
 * 按返回键或点击浮层以外空白处由 Popup 的 onDismissRequest 收起。
 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
internal fun ConflictGroupExpandOverlay(
    expanded: ForkExpandedConflictGroup,
    conflictGroups: List<WeekConflictGroup>,
    renderedSegments: List<WeekRenderedSegment>,
    periods: List<PeriodEntity>,
    cardHeight: Dp,
    config: ScheduleConfigEntity,
    shortcutPivotX: Float,
    editWeek: Int,
    onCourseClick: (CourseEntity, Rect?) -> Unit,
    onEnterEditMode: () -> Unit,
    onDismiss: () -> Unit
) {
    val density = LocalDensity.current
    val group = conflictGroups.getOrNull(expanded.groupIndex) ?: return
    val groupSegments = renderedSegments.filter { it.groupIndex == expanded.groupIndex }
    // 展开卡高度保持各自折叠态；顶端与折叠前的网格位置分别对齐——
    // 参考点 = 被点击卡自身的起始节次（点哪张卡，哪张就精确对齐自身原位）
    val entries: List<ForkConflictExpandEntry> = groupSegments.map { rendered ->
        val seg = rendered.segment
        val exact = exactTimeWeekPlacement(seg.course, periods)
        val foldedHeight = if (exact != null) {
            (cardHeight * exact.heightRows).coerceAtLeast(1.dp)
        } else {
            (cardHeight * seg.span.toFloat() - 4.dp).coerceAtLeast(18.dp)
        }
        ForkConflictExpandEntry(
            course = seg.course,
            foldedHeight = foldedHeight,
            topOffset = cardHeight * (seg.startPosition - expanded.clickedStartRows)
        )
    }.distinctBy { it.course.id }
    if (entries.isEmpty()) return
    val shortcuts = LocalCourseShortcuts.current
    val windowSize = currentWindowSizeDp()
    val gap = 6.dp
    val margin = 8.dp
    val gapPx = with(density) { gap.toPx() }
    val marginPx = with(density) { margin.toPx() }
    val screenWidthPx = with(density) { windowSize.width.toPx() }
    val screenHeightPx = with(density) { windowSize.height.toPx() }
    // 展开后的卡片尺寸 = 折叠卡的 100%（宽度与高度均保持折叠态）
    val fallbackWidthPx = with(density) { 96.dp.toPx() }
    val fallbackHeightPx = with(density) { 120.dp.toPx() }
    val anchorBounds: Rect? = expanded.anchor.takeIf { it != Rect.Zero }
    val itemWidth = with(density) {
        (anchorBounds?.width?.toFloat() ?: fallbackWidthPx).toDp()
    }
    val itemWidthPx = with(density) { itemWidth.toPx() }
    val cardHeightsPx = entries.map { with(density) { it.foldedHeight.toPx() } }
    // 负偏移合法：点击的卡可能晚于组内其他课开始，那些迷你卡要向上对齐各自折叠前位置
    val cardTopOffsetsPx = entries.map { with(density) { it.topOffset.toPx() } }
    val lowestBottomPx = cardHeightsPx.indices
        .maxOf { cardTopOffsetsPx[it] + cardHeightsPx[it] }
        .coerceAtLeast(fallbackHeightPx)
    val contentWidthPx = itemWidthPx * entries.size +
        gapPx * (entries.size - 1).coerceAtLeast(0)
    val expandToLeft = anchorBounds != null &&
        anchorBounds.right + marginPx + contentWidthPx > screenWidthPx - marginPx
    val anchorX = when {
        anchorBounds == null -> ((screenWidthPx - contentWidthPx) / 2f)
        !expandToLeft -> anchorBounds.right + marginPx
        else -> (anchorBounds.left - marginPx - contentWidthPx).coerceAtLeast(marginPx)
    }
    // 锚点 Y 直接取被点击卡的顶端，保证每张迷你卡与折叠前位置逐一对齐；
    // Popup 定位到组内最早的顶端（整体上移），Row 内改用非负偏移，避免负 offset 绘制内容被窗口裁剪；
    // 窗口高度显式按"最深底端 + 阴影余量"计算，上下各留 12dp，向下偏移的卡也不会被裁
    val anchorY = anchorBounds?.top ?: ((screenHeightPx - lowestBottomPx) / 2f)
    val minTopOffsetPx = cardTopOffsetsPx.minOrNull() ?: 0f
    val shadowAllowancePx = with(density) { 12.dp.toPx() }
    val rowBottomPx = cardTopOffsetsPx.indices
        .maxOf { (cardTopOffsetsPx[it] - minTopOffsetPx) + cardHeightsPx[it] }
        .coerceAtLeast(fallbackHeightPx)
    val popupContentHeightPx = rowBottomPx + shadowAllowancePx
    val popupY = anchorY + minTopOffsetPx - shadowAllowancePx
    val positionProvider = remember(anchorX.toInt(), popupY.toInt()) {
        object : PopupPositionProvider {
            override fun calculatePosition(
                anchorBounds: IntRect,
                windowSize: IntSize,
                layoutDirection: LayoutDirection,
                popupContentSize: IntSize
            ): IntOffset = IntOffset(anchorX.toInt(), popupY.toInt())
        }
    }

    // 入场：向展开方向轻微滑入 + 淡入
    var appeared by remember(entries) { mutableStateOf(false) }
    LaunchedEffect(entries) { appeared = true }
    val appearProgress by animateFloatAsState(
        targetValue = if (appeared) 1f else 0f,
        animationSpec = tween(190, easing = CubicBezierEasing(0.16f, 0.82f, 0.18f, 1f)),
        label = "fork-conflict-expand-appear"
    )

    Popup(
        onDismissRequest = onDismiss,
        popupPositionProvider = positionProvider,
        properties = PopupProperties(
            focusable = true,
            dismissOnBackPress = true,
            dismissOnClickOutside = true
        )
    ) {
        Box(
            modifier = Modifier
                .requiredHeight(with(density) { popupContentHeightPx.toDp() })
                .graphicsLayer {
                    alpha = appearProgress
                    translationX = with(density) {
                        val slide = (1f - appearProgress) * 18.dp.toPx()
                        if (expandToLeft) slide else -slide
                    }
                }
        ) {
            Row(
                modifier = Modifier.padding(top = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(gap)
            ) {
                entries.forEachIndexed { index, entry ->
                    val entryHeightPx = cardHeightsPx[index]
                    val entryTopPx = anchorY + cardTopOffsetsPx[index]
                    // 卡片在窗口中的绝对位置：供编辑弹窗 morph 与长按快捷菜单定位
                    val entryLeft = anchorX + index * (itemWidthPx + gapPx)
                    val entryRect = Rect(
                        left = entryLeft,
                        top = entryTopPx,
                        right = entryLeft + itemWidthPx,
                        bottom = entryTopPx + entryHeightPx
                    )
                    // 外层统一接管点击与长按：长按打开与课表卡一致的快捷操作菜单
                    Box(
                        modifier = Modifier
                            // Popup 已上移到组内最早顶端，这里用相对 popupY 的非负偏移，
                            // 保证绘制内容完整落在 Popup 窗口内不被裁剪
                            .offset(y = with(density) { (cardTopOffsetsPx[index] - minTopOffsetPx).toDp() })
                            .width(itemWidth)
                            .height(with(density) { entryHeightPx.toDp() })
                            .combinedClickable(
                                onClick = { onCourseClick(entry.course, entryRect) },
                                onLongClick = {
                                    // 与课表卡长按一致：收起浮层并打开该课程的快捷操作菜单
                                    onDismiss()
                                    shortcuts?.open(
                                        CourseShortcutRequest(
                                            course = entry.course,
                                            week = editWeek,
                                            bounds = entryRect,
                                            cornerPx = with(density) { 12.dp.toPx() },
                                            pivotX = shortcutPivotX,
                                            enterEditMode = onEnterEditMode
                                        )
                                    )
                                }
                            )
                    ) {
                        CourseGlassCard(
                            backdrop = null,
                            config = config,
                            course = entry.course,
                            modifier = Modifier
                                .fillMaxSize()
                                .shadow(
                                    elevation = 10.dp * appearProgress,
                                    shape = RoundedRectangle(12.dp),
                                    ambientColor = ComposeColor.Black,
                                    spotColor = ComposeColor.Black
                                ),
                            shape = RoundedRectangle(12.dp),
                            onClick = null
                        ) {
                            WeekCourseOverlayCardContent(entry.course, config)
                        }
                    }
                }
            }
        }
    }
}
