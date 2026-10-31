package dev.powerfulcalendar.ui.v2.nativeui

import dev.powerfulcalendar.ui.v2.*

import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.LocalTextStyle
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.exp
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

private val CLOCK_FORMAT = DateTimeFormatter.ofPattern("HH:mm")

private data class MeasuredTimelineTitle(
    val candidate: TimelineTitleCandidate,
    val layout: TextLayoutResult,
    val drawWidthPx: Int,
    val offset: IntOffset,
    val testTag: String,
    val sourceEvent: NativeTimelineEvent,
    val sourceKey: String,
    val anchorMillis: Long
)

fun Modifier.timeAxisResetSemantics(onReset: () -> Unit): Modifier = semantics {
    contentDescription = "双击当前时间刻度尺返回24小时视图"
    onClick(label = "复位为24小时视图") {
        onReset()
        true
    }
}

fun findUnblockedLabelTopLeft(
    preferred: Offset,
    labelSize: Size,
    canvasSize: Size,
    obstacles: List<Rect>,
    stepPx: Float,
    gapPx: Float
): Offset {
    fun fits(point: Offset): Boolean {
        if (point.x < 0f || point.y < 0f ||
            point.x + labelSize.width > canvasSize.width ||
            point.y + labelSize.height > canvasSize.height
        ) return false
        return obstacles.none { obstacle ->
            point.x < obstacle.right + gapPx && point.x + labelSize.width > obstacle.left - gapPx &&
                point.y < obstacle.bottom + gapPx && point.y + labelSize.height > obstacle.top - gapPx
        }
    }

    if (fits(preferred) || stepPx <= 0f) return preferred
    val directions = listOf(
        Offset(1f, 0f), Offset(-1f, 0f), Offset(0f, -1f), Offset(0f, 1f),
        Offset(1f, -1f), Offset(1f, 1f), Offset(-1f, -1f), Offset(-1f, 1f)
    )
    val maxSteps = ceil(maxOf(canvasSize.width, canvasSize.height) / stepPx).toInt()
    for (step in 1..maxSteps) {
        val distance = step * stepPx
        for (direction in directions) {
            val candidate = preferred + direction * distance
            if (fits(candidate)) return candidate
        }
    }
    return preferred
}
private val DAY_FORMAT = DateTimeFormatter.ofPattern("M月d日 EEE", java.util.Locale.SIMPLIFIED_CHINESE)
private val HEADER_DAY_FORMAT = DateTimeFormatter.ofPattern("yyyy年M月d日 EEE", java.util.Locale.SIMPLIFIED_CHINESE)

@Composable
fun NativeTimelineScreen(context: NativeTimelineContext, modifier: Modifier = Modifier) {
    val state = context.state
    val geometry = context.geometry
    val actions = context.actions
    val appearance = context.appearance
    val events = state.events
    val camera = state.camera
    val nowMillis = state.nowMillis
    val zone = state.zone
    val pendingCount = state.pendingCount
    val aiLocked = state.aiLocked
    val memoCount = state.memoCount
    val blockedReminders = state.blockedReminders
    val scope = rememberCoroutineScope()
    val latestCamera by rememberUpdatedState(camera)
    var inertia by remember { mutableStateOf<Job?>(null) }
    var inspected by remember { mutableStateOf<NativeTimelineEvent?>(null) }
    val day = Instant.ofEpochMilli(camera.anchorMillis).atZone(zone).toLocalDate()
    fun open(event: NativeTimelineEvent) {
        inertia?.cancel()
        if (aiLocked) inspected = event else actions.onOpenEvent(event.rowKey)
    }
    fun changeCamera(updated: NativeTimelineCamera) { inertia?.cancel(); actions.onCameraChange(updated) }

    appearance.page {
    Column(modifier.fillMaxSize()) {
        // Single actionable toolbar. Page navigation lives ONLY in the bottom tab bar.
        var createMenu by remember { mutableStateOf(false) }
        Row(
            Modifier.fillMaxWidth().padding(start = 14.dp, end = 6.dp, top = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    appearance.image(NativeTimelineAsset.BRAND_MARK, 19f)
                    Text("Powerful Calendar", style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Text(day.format(HEADER_DAY_FORMAT), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = { inertia?.cancel(); actions.onToday() }, modifier = Modifier.size(44.dp)) {
                Icon(Icons.Default.Today, "今日 · 复位为全天")
            }
            IconButton(onClick = { inertia?.cancel(); actions.onOpenMemo() }, modifier = Modifier.size(44.dp)) {
                BadgedBox(badge = {
                    if (memoCount > 0) Badge { Text(if (memoCount > 999) "999+" else memoCount.toString()) }
                }) { Icon(Icons.Default.Checklist, "Memo 未完成 " + memoCount) }
            }
            IconButton(onClick = { inertia?.cancel(); actions.onOpenDeck() }, modifier = Modifier.size(44.dp)) {
                BadgedBox(badge = {
                    if (pendingCount > 0) Badge { Text(pendingCount.toString()) }
                }) {
                    Icon(if (aiLocked) Icons.Default.Lock else Icons.Default.Layers, "待处理 $pendingCount")
                }
            }
            Box {
                IconButton(onClick = { createMenu = true }, enabled = !aiLocked, modifier = Modifier.size(44.dp)) {
                    Icon(Icons.Default.Add, "新建事件")
                }
                DropdownMenu(expanded = createMenu, onDismissRequest = { createMenu = false }) {
                    NativeTimelineKind.entries.forEach { kind ->
                        DropdownMenuItem(text = { Text(kind.label) }, onClick = {
                            createMenu = false
                            actions.onCreate(kind, geometry.snap(camera.anchorMillis, camera.hours, zone))
                        })
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { changeCamera(camera.copy(hours = geometry.clampHours(camera.hours * 1.5))) }, enabled = camera.hours < geometry.maximumHours) {
                Icon(Icons.Default.Remove, "缩小，显示更长时间")
            }
            Text("${if (camera.hours % 1.0 == 0.0) camera.hours.toInt().toString() else String.format(java.util.Locale.US, "%.1f", camera.hours)} 小时 / 屏",
                style = MaterialTheme.typography.labelMedium, modifier = Modifier.semantics { stateDescription = "时间轴缩放 ${camera.hours} 小时" })
            IconButton(onClick = { changeCamera(camera.copy(hours = geometry.clampHours(camera.hours / 1.5))) }, enabled = camera.hours > geometry.minimumHours) {
                Icon(Icons.Default.Add, "放大，显示更短时间")
            }
            Spacer(Modifier.weight(1f))
            Text(if (aiLocked) "AI 处理中" else "↕ 时间", style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(end = 8.dp))
        }
        if (blockedReminders > 0) Surface(onClick = actions.onOpenTools, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            shape = RoundedCornerShape(appearance.chipCornerRadiusDp.dp), color = MaterialTheme.colorScheme.errorContainer) {
            Row(Modifier.heightIn(min = 48.dp).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.ErrorOutline, null, Modifier.size(20.dp)); Spacer(Modifier.width(8.dp))
                Text("$blockedReminders 项提醒受阻 · 查看权限与状态", style = MaterialTheme.typography.bodyMedium)
            }
        }
        val visibleDates = events.filter { isDateDeadlineVisible(it, camera, geometry) }
        if (state.hasDateOnlyDeadlines) Box(Modifier.fillMaxWidth().height(64.dp * LocalDensity.current.fontScale), contentAlignment = Alignment.Center) {
        if (visibleDates.isEmpty()) Text("可见日期内没有日期级截止", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        LazyRow(Modifier.fillMaxWidth().padding(bottom = 8.dp), contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(visibleDates, key = { it.rowKey }) { entry ->
                Surface(onClick = { open(entry) }, shape = RoundedCornerShape(appearance.chipCornerRadiusDp.dp), color = MaterialTheme.colorScheme.tertiaryContainer) {
                    Row(Modifier.heightIn(min = 48.dp).padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Flag, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp))
                        Column(Modifier.widthIn(max = 240.dp)) {
                            Text(entry.title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelLarge)
                            Text("${entry.deadlineDate} · 截止，无具体时刻", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }
        }
        val halfSpan = (geometry.spanMillis(camera.hours) / 2).toLong()
        BoxWithConstraints(Modifier.fillMaxWidth().weight(1f).clip(RoundedCornerShape(0.dp))) {
            val density = LocalDensity.current
            val height = with(density) { maxHeight.toPx() }.coerceAtLeast(1f)
            val viewportWidth = maxWidth
            val viewportHeight = maxHeight
            val width = with(density) { viewportWidth.toPx() }
            val heightNow by rememberUpdatedState(height)
            val unlocked by rememberUpdatedState(!aiLocked)
            val textMeasurer = rememberTextMeasurer()
            val nowBadgeObstacles = mutableListOf<Rect>()
            var dragKey by remember { mutableStateOf<String?>(null) }
            var dragTarget by remember { mutableStateOf<Long?>(null) }
            var dragTravel by remember { mutableFloatStateOf(0f) }
            val cameraChange by rememberUpdatedState(actions.onCameraChange)

            Box(Modifier.fillMaxSize().testTag("timeline-viewport").pointerInput(Unit) {
                timelineGestures({ latestCamera }, { heightNow }, geometry, { inertia?.cancel() }, { cameraChange(it) },
                    { velocity, releasedCamera ->
                    inertia?.cancel()
                    inertia = scope.launch {
                        var cam = releasedCamera
                        var speed = velocity
                        var last = withFrameNanos { it }
                        while (abs(speed) > 24f) {
                            val frame = withFrameNanos { it }
                            val dt = ((frame - last) / 1_000_000_000f).coerceIn(0f, 0.05f)
                            last = frame
                            cam = cam.copy(anchorMillis = cam.anchorMillis - (speed * dt * geometry.spanMillis(cam.hours) / heightNow).toLong())
                            cameraChange(cam)
                            speed *= exp(-4.2f * dt)
                        }
                    }
                })
            }.semantics {
                contentDescription = "中央时间轴，左侧闹钟，右侧活动和截止"
                customActions = listOf(
                    CustomAccessibilityAction("向后浏览半屏") { changeCamera(camera.copy(anchorMillis = camera.anchorMillis + halfSpan)); true },
                    CustomAccessibilityAction("向前浏览半屏") { changeCamera(camera.copy(anchorMillis = camera.anchorMillis - halfSpan)); true }
                )
            }) {
                TimelineCanvas(camera.anchorMillis, camera.hours, height, width, nowMillis, zone, geometry, appearance)
                val gutter = 20.dp
                val leftSide = (viewportWidth / 2 - gutter - 12.dp).coerceAtLeast(24.dp)
                val rightSide = (viewportWidth / 2 - gutter - 12.dp).coerceAtLeast(24.dp)
                // Keep each wing's scroll independent of the vertical time camera.
                // Left starts nearest the centre (scroll at end); right starts at scroll zero.
                for (side in NativeTimelineSide.entries) {
                    key(side) {
                        val sideWidth = if (side == NativeTimelineSide.LEFT) leftSide else rightSide
                        val sideEvents = events.filter { it.side == side }
                        val lanes = (sideEvents
                            .maxOfOrNull { it.lane + 1 } ?: 1)
                        // Each wing keeps stable lane widths and scrolls independently. The
                        // left wing starts at its center-proximal lanes (the content's right edge).
                        val spacing = 4.dp
                        val laneWidth = minOf(84.dp, sideWidth)
                        val contentWidth = maxOf(sideWidth, (laneWidth + spacing) * lanes - spacing)
                        val wingX = if (side == NativeTimelineSide.LEFT)
                            viewportWidth / 2 - gutter - sideWidth
                            else viewportWidth / 2 + gutter
                        val horizontalState = rememberScrollState()
                        var leftWingInitiallyPositioned by rememberSaveable {
                            mutableStateOf(side != NativeTimelineSide.LEFT)
                        }
                        LaunchedEffect(horizontalState, side) {
                            if (side == NativeTimelineSide.LEFT && !leftWingInitiallyPositioned) {
                                val maxValue = snapshotFlow { horizontalState.maxValue }
                                    .first { it in 1 until Int.MAX_VALUE }
                                horizontalState.scrollTo(maxValue)
                                leftWingInitiallyPositioned = true
                            }
                        }
                        Box(
                            Modifier.offset(x = wingX).width(sideWidth).fillMaxHeight()
                                .clipToBounds()
                                .horizontalScroll(horizontalState)
                                .testTag(if (side == NativeTimelineSide.LEFT)
                                    "timeline-left-lanes" else "timeline-right-lanes")
                        ) {
                            Box(Modifier.width(contentWidth).fillMaxHeight()) {
                    val titleCandidates = mutableListOf<MeasuredTimelineTitle>()
                    sideEvents.forEach { item ->
                    val y = geometry.yOf(item.anchorMillis, camera.anchorMillis, height, camera.hours)
                    val endY = geometry.yOf(item.endMillis ?: item.anchorMillis, camera.anchorMillis, height, camera.hours)
                    if (endY < -80 || y > height + 80) return@forEach
                    val key = item.rowKey
                    // Activities use real start/end projected onto the same ruler.
                    // Never inflate a short interval to fit typography or the touch target.
                    val activitySpan = if (item.kind == NativeTimelineKind.ACTIVITY) {
                        ActivityGeometry.visible(item.anchorMillis,
                            item.endMillis ?: item.anchorMillis,
                            camera.anchorMillis, height, camera.hours, geometry)
                    } else null
                    if (item.kind == NativeTimelineKind.ACTIVITY && activitySpan == null) return@forEach
                    val cardHeight = with(density) {
                        (activitySpan?.heightPx ?: 0f).toDp()
                    }
                    // The time axis stays fixed while only the event wing scrolls.
                    val x = if (side == NativeTimelineSide.LEFT)
                        contentWidth - (laneWidth + spacing) * (item.lane + 1) + spacing
                        else (laneWidth + spacing) * item.lane
                    val point = item.kind != NativeTimelineKind.ACTIVITY
                    val pointHeight = 56.dp
                    // A sub-pixel Activity keeps its exact visual height below; retain a
                    // separate 1dp interaction surface so rounding cannot erase its click target.
                    val hitHeight = if (point) pointHeight else maxOf(cardHeight, 1.dp)
                    val topPx = if (item.kind == NativeTimelineKind.DEADLINE) {
                        y - with(density) { pointHeight.toPx() }
                    } else if (item.kind == NativeTimelineKind.ALARM) y else activitySpan!!.topPx
                    val cardLeftPx = with(density) {
                        (wingX.roundToPx() + x.roundToPx() - horizontalState.value).toFloat()
                    }
                    val cardWidthPx = with(density) { laneWidth.roundToPx().toFloat() }
                    val cardHeightPx = with(density) {
                        if (point) pointHeight.toPx() else cardHeight.toPx()
                    }
                    // Point and duration labels share the same outside title pipeline.
                    if (point || activitySpan != null) {
                        val baseStyle = LocalTextStyle.current.copy(
                            fontSize = 13.sp, color = androidx.compose.ui.graphics.Color(appearance.kindColors.getValue(item.kind))
                        )
                        val laneWidthPx = with(density) { laneWidth.roundToPx() }
                        val startPadding = 3.dp
                        val endPadding = 3.dp
                        val startPaddingPx = with(density) { startPadding.roundToPx() }
                        val endPaddingPx = with(density) { endPadding.roundToPx() }
                        val availablePx = laneWidthPx - startPaddingPx - endPaddingPx
                        val measuredTitle = run {
                            if (availablePx <= 0 || startPaddingPx >= laneWidthPx) return@run null
                            val titleText = if (point) {
                                Instant.ofEpochMilli(item.anchorMillis).atZone(zone)
                                .format(CLOCK_FORMAT) + " \u00b7 " + item.title
                            } else item.title
                            val measuredText = measureTimelineTitleText(
                                textMeasurer, titleText, baseStyle, availablePx
                            ) ?: return@run null
                            val textLayout = measuredText.layout
                            val drawWidthPx = measuredText.drawWidthPx
                            val insetPx = with(density) { 2.dp.toPx() }
                            // Left ALARM markers put the line at y and its outside
                            // label immediately BELOW that line. Using the 56dp hit-box
                            // top for all point kinds displaced the left wing upward.
                            // DEADLINE has its line at y and label immediately ABOVE it.
                            // Leave the approved right ACTIVITY placement untouched.
                            val titleTopPx = when (item.kind) {
                                NativeTimelineKind.ALARM -> y + insetPx
                                NativeTimelineKind.DEADLINE -> y - textLayout.size.height - insetPx
                                else -> topPx - textLayout.size.height - insetPx
                            }
                            val titleX = x + startPadding
                            val localLeftPx = with(density) { titleX.roundToPx() }
                            val localTopPx = with(density) { titleTopPx.toDp().roundToPx() }
                            val wingLeftPx = with(density) { wingX.roundToPx() }
                            val sideWidthPx = with(density) { sideWidth.roundToPx() }
                            val screenLeft = wingLeftPx + localLeftPx - horizontalState.value
                            val unclipped = Rect(
                                left = screenLeft.toFloat(),
                                top = localTopPx.toFloat(),
                                right = (screenLeft + drawWidthPx).toFloat(),
                                bottom = (localTopPx + textLayout.size.height).toFloat()
                            )
                            val screenRect = unclipped.intersect(
                                Rect(wingLeftPx.toFloat(), 0f,
                                    (wingLeftPx + sideWidthPx).toFloat(), height)
                            )
                            if (screenRect.width <= 0f || screenRect.height <= 0f) return@run null
                            val stableId = item.stableId
                            MeasuredTimelineTitle(
                                candidate = TimelineTitleCandidate(
                                    stableId = stableId,
                                    eventSeq = item.eventSeq,
                                    regionId = "${item.side.name}:${item.lane}",
                                    screenRect = screenRect
                                ),
                                layout = textLayout,
                                drawWidthPx = drawWidthPx,
                                offset = IntOffset(localLeftPx, localTopPx),
                                testTag = "timeline-title-${item.eventSeq}-" +
                                    stableId.hashCode().toUInt().toString(16),
                                sourceEvent = item,
                                sourceKey = key,
                                anchorMillis = item.anchorMillis
                            )
                        }
                        if (measuredTitle != null) titleCandidates += measuredTitle
                    }
                    nowBadgeObstacles += Rect(
                        left = cardLeftPx,
                        top = topPx,
                        right = cardLeftPx + cardWidthPx,
                        bottom = topPx + cardHeightPx
                    )
                    key(key) {
                        Surface(
                            onClick = { if (dragKey == null) open(item) },
                            modifier = Modifier.offset(x, with(density) { topPx.toDp() }).width(laneWidth)
                                .testTag("timeline-event-${item.eventSeq}-" +
                                    key.hashCode().toUInt().toString(16))
                                .height(hitHeight)
                                .pointerInput(key, item.revision, item.anchorMillis, aiLocked) {
                                    if (!unlocked) return@pointerInput
                                    detectDragGesturesAfterLongPress(
                                        onDragStart = { inertia?.cancel(); dragKey = key; dragTarget = item.anchorMillis; dragTravel = 0f },
                                        onDrag = { change, amount ->
                                            change.consume(); dragTravel += amount.y
                                            dragTarget = geometry.snap(item.anchorMillis + (dragTravel * geometry.spanMillis(latestCamera.hours) / heightNow).toLong(), latestCamera.hours, zone)
                                        },
                                        onDragEnd = {
                                            val target = dragTarget
                                            dragKey = null; dragTarget = null
                                            if (target != null && target != item.anchorMillis) actions.onMoveEvent(item.rowKey, target)
                                        }, onDragCancel = { dragKey = null; dragTarget = null })
                                }
                                .semantics { contentDescription = "${item.kindLabel}，${item.title}，${item.summary}，${item.sequenceLabel}" },
                            shape = RoundedCornerShape(0.dp),
                            color = androidx.compose.ui.graphics.Color.Transparent,
                            border = null
                        ) {
                            if (point) {
                                AxisPointMarker(item, item.anchorMillis, zone, appearance)
                            } else {
                            Box(Modifier.fillMaxSize()) {
                                Box(
                                    Modifier.align(Alignment.TopStart)
                                        .width(laneWidth)
                                        .height(cardHeight)
                        .testTag("timeline-event-geometry-${item.eventSeq}-" +
                                            key.hashCode().toUInt().toString(16))
                                        .background(
                                            if (dragKey == key) MaterialTheme.colorScheme.surfaceContainerHighest
                                            else androidx.compose.ui.graphics.Color(appearance.axis.glassFill)
                                        )
                                        .border(
                                            1.dp,
                                            androidx.compose.ui.graphics.Color(appearance.kindColors.getValue(item.kind)).copy(alpha = 0.36f),
                                            RoundedCornerShape(0.dp)
                                        )
                                ) {
                                    Box(Modifier.align(Alignment.CenterStart).width(2.dp).fillMaxHeight()
                                        .background(androidx.compose.ui.graphics.Color(appearance.kindColors.getValue(item.kind))))
                                    if (cardHeight >= 28.dp) {
                                        Text(Instant.ofEpochMilli(item.anchorMillis).atZone(zone).format(CLOCK_FORMAT),
                                            Modifier.align(Alignment.TopStart).padding(start = 5.dp),
                                            fontSize = 10.sp, maxLines = 1, softWrap = false,
                                            overflow = TextOverflow.Clip)
                                    }
                                    if (cardHeight >= 47.dp) {
                                        Text(Instant.ofEpochMilli(item.endMillis ?: item.anchorMillis)
                                                .atZone(zone).format(CLOCK_FORMAT),
                                            Modifier.align(Alignment.BottomStart).padding(start = 5.dp),
                                            fontSize = 10.sp, maxLines = 1, softWrap = false,
                                            overflow = TextOverflow.Clip)
                                    }
                                }
                            }
                            }
                        }
                    }
                }
                    val titleFold = GreedyTitleFolding.fold(titleCandidates.map { it.candidate })
                    val visibleTitles = titleFold.visibleIds
                    titleCandidates.filter { it.candidate.stableId in visibleTitles }.forEach { title ->
                        nowBadgeObstacles += title.candidate.screenRect
                        Canvas(
                            Modifier.offset { title.offset }
                                .requiredSize(
                                    with(density) { title.drawWidthPx.toDp() },
                                    with(density) { title.layout.size.height.toDp() }
                                )
                                .clipToBounds()
                                .testTag(title.testTag)
                                .clickable(enabled = !aiLocked) {
                                    if (dragKey == null) open(title.sourceEvent)
                                }
                                .pointerInput(title.sourceKey, title.sourceEvent.revision,
                                    title.anchorMillis, aiLocked) {
                                    if (!unlocked) return@pointerInput
                                    detectDragGesturesAfterLongPress(
                                        onDragStart = {
                                            inertia?.cancel()
                                            dragKey = title.sourceKey
                                            dragTarget = title.anchorMillis
                                            dragTravel = 0f
                                        },
                                        onDrag = { change, amount ->
                                            change.consume()
                                            dragTravel += amount.y
                                            dragTarget = geometry.snap(
                                                title.anchorMillis +
                                                    (dragTravel * geometry.spanMillis(latestCamera.hours) /
                                                        heightNow).toLong(),
                                                latestCamera.hours, zone)
                                        },
                                        onDragEnd = {
                                            val target = dragTarget
                                            dragKey = null
                                            dragTarget = null
                                            if (target != null && target != title.anchorMillis) {
                                                actions.onMoveEvent(title.sourceKey, target)
                                            }
                                        },
                                        onDragCancel = { dragKey = null; dragTarget = null }
                                    )
                                }
                        ) {
                            drawText(title.layout, topLeft = Offset.Zero)
                        }
                    }
                            }
                        }
                    }
                }
                TimelineCanvas(camera.anchorMillis, camera.hours, height, width, nowMillis, zone,
                    geometry, appearance, nowOnly = true, eventObstacles = nowBadgeObstacles)
                // Double-tap the *current-time ruler*, not anywhere on an event.
                val resetCameraToDay = {
                    inertia?.cancel()
                    cameraChange(latestCamera.copy(hours = 24.0))
                }
                Box(Modifier.align(Alignment.Center).width(25.dp).fillMaxHeight()
                    .pointerInput(Unit) {
                        detectTapGestures(onDoubleTap = {
                            resetCameraToDay()
                        })
                    }.timeAxisResetSemantics { resetCameraToDay() })
                dragTarget?.let { target ->
                    val y = geometry.yOf(target, camera.anchorMillis, height, camera.hours)
                    Surface(Modifier.align(Alignment.TopCenter).offset(y = with(density) { y.toDp() }), shape = RoundedCornerShape(appearance.chipCornerRadiusDp.dp), color = MaterialTheme.colorScheme.primaryContainer) {
                        Text("${Instant.ofEpochMilli(target).atZone(zone).format(CLOCK_FORMAT)} · 松开后确认草稿", Modifier.padding(10.dp), style = MaterialTheme.typography.labelMedium)
                    }
                }
                if (!state.hasRecords && viewportHeight >= 480.dp && density.fontScale <= 1.3f) Text(
                    "还没有事件 · 点击右上角 + 开始",
                    modifier = Modifier.align(Alignment.TopCenter).padding(top = 24.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

    }
    }
    inspected?.let { event ->
        appearance.showDialog(
            NativeTimelineDialog(
                onDismissRequest = { inspected = null },
                title = event.title,
                summary = event.summary,
                notes = event.notes
            )
        )
    }

}

/**
 * Different temporal primitives must never share the same rectangle.
 * ALARM = line at the top with its label BELOW.
 * DEADLINE = label ABOVE a line exactly at its occurrence instant.
 * ACTIVITY is the only interval rectangle, rendered in the viewport loop.
 */
@Composable
private fun AxisPointMarker(
    event: NativeTimelineEvent,
    occurrenceMillis: Long,
    zone: ZoneId,
    appearance: NativeTimelineAppearance
) {
    val color = androidx.compose.ui.graphics.Color(appearance.kindColors.getValue(event.kind))
    // Full title rendered by measured outside-title fold; marker remains clickable.
    Column(Modifier.fillMaxSize()) {
        if (event.kind == NativeTimelineKind.DEADLINE) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.BottomStart) {
                if (appearance.hasImage(NativeTimelineAsset.DEADLINE_MARKER)) {
                    Box(Modifier.align(Alignment.TopEnd)) {
                        appearance.image(NativeTimelineAsset.DEADLINE_MARKER, 18f)
                    }
                }
            }
        }
        HorizontalDivider(color = color, thickness = appearance.pointLineDp.dp)
        if (event.kind == NativeTimelineKind.ALARM) {
            Row(verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (appearance.hasImage(NativeTimelineAsset.ALARM_MARKER)) {
                    appearance.image(NativeTimelineAsset.ALARM_MARKER, 18f)
                }
            }
        }
    }
}

private suspend fun androidx.compose.ui.input.pointer.PointerInputScope.timelineGestures(
    camera: () -> NativeTimelineCamera,
    height: () -> Float,
    geometry: NativeTimelineGeometry,
    onStart: () -> Unit,
    onCamera: (NativeTimelineCamera) -> Unit,
    onFling: (Float, NativeTimelineCamera) -> Unit
) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        onStart()
        var cam = camera()
        var previousY = down.position.y
        var count = 1
        var travel = 0f
        var active = false
        var zoomed = false
        var ownedByChild = false
        var gestureCamera = cam
        var gestureStartY = previousY
        var initialDx = 0f
        var initialDy = 0f
        val tracker = VelocityTracker()
        tracker.addPosition(down.uptimeMillis, down.position)
        while (true) {
            val event = awaitPointerEvent()
            val pressed = event.changes.filter { it.pressed }
            if (pressed.isEmpty()) {
                event.changes.firstOrNull()?.let { tracker.addPosition(it.uptimeMillis, it.position) }
                break
            }
            // Two-finger zoom is a viewport gesture even if the first finger landed on
            // a clickable title or event. A single-finger child drag still owns its input.
            if (pressed.size >= 2) ownedByChild = false
            else if (event.changes.any { it.isConsumed }) { ownedByChild = true; continue }
            if (ownedByChild) continue
            val y = pressed.map { it.position.y }.average().toFloat()
            if (pressed.size != count) {
                count = pressed.size; previousY = y
                if (count > 1) {
                    zoomed = true
                    val a = pressed[0]; val b = pressed[1]
                    initialDx = a.position.x - b.position.x
                    initialDy = a.position.y - b.position.y
                    gestureCamera = cam
                    gestureStartY = y
                }
                tracker.resetTracking()
                continue
            }
            val pan = y - previousY
            travel += abs(pan)
            if (pressed.size >= 2) zoomed = true
            if (!active && !zoomed && travel < viewConfiguration.touchSlop) {
                previousY = y; tracker.addPosition(pressed.first().uptimeMillis, pressed.first().position); continue
            }
            active = true
            if (pressed.size >= 2) {
                val a = pressed[0]; val b = pressed[1]
                val components = AxisPinch.decompose(
                    initialDx, initialDy,
                    a.position.x - b.position.x, a.position.y - b.position.y,
                    viewConfiguration.touchSlop
                )
                // Absolute gesture baseline: no frame-by-frame multiplying,
                // no sudden snapping to a different lane count.
                cam = geometry.transformedCamera(
                    gestureCamera, gestureStartY,
                    gestureStartY + AxisPinch.dampCentroidPan(y - gestureStartY),
                    components.y, height()
                )
            } else {
                cam = geometry.transformedCamera(cam, previousY, y, 1f, height())
            }
            if (cam != camera()) {
                onCamera(cam)
            }
            pressed.forEach { it.consume() }
            previousY = y
            tracker.addPosition(pressed.first().uptimeMillis, pressed.first().position)
        }
        if (active && !zoomed && !ownedByChild) {
            val velocity = tracker.calculateVelocity().y
            if (abs(velocity) > 120f) onFling(velocity.coerceIn(-8000f, 8000f), cam)
        }
    }
}

@Composable
private fun TimelineCanvas(
    anchor: Long,
    hours: Double,
    heightPx: Float,
    widthPx: Float,
    nowMillis: Long,
    zone: ZoneId,
    geometry: NativeTimelineGeometry,
    appearance: NativeTimelineAppearance,
    nowOnly: Boolean = false,
    eventObstacles: List<Rect> = emptyList()
) {
    val axis = appearance.axis
    val scheme = MaterialTheme.colorScheme
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val tickStyle = TextStyle(color = scheme.onSurfaceVariant, fontSize = 12.sp, fontWeight = FontWeight.Medium)
    val dayStyle = TextStyle(color = scheme.onSurfaceVariant, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    val centerX = widthPx / 2f
    val spanMillis = geometry.spanMillis(hours)
    val from = anchor - spanMillis / 2
    val to = anchor + spanMillis / 2
    val tickMinutes = when {
        hours <= 0.75 -> 1L
        hours <= 2 -> 5L
        hours <= 4 -> 10L
        hours <= 6 -> 30L
        hours <= 12 -> 60L
        hours <= 26 -> 120L
        else -> 240L
    }
    val tickLabels = remember(from, to, tickMinutes, zone) {
        timelineTickLabels(from.toLong(), to.toLong(), tickMinutes, zone)
    }

    Canvas(Modifier.fillMaxSize()) {
        if (!nowOnly) {
        // The central vertical time ruler is a required part of the timeline.
        // Do not confuse it with the separate unwanted horizontal grey decoration.
        drawLine(
            brush = Brush.verticalGradient(
                listOf(
            androidx.compose.ui.graphics.Color(axis.glassBorder).copy(alpha = 0f),
                    androidx.compose.ui.graphics.Color(axis.glassBorder).copy(alpha = 0.85f),
                    androidx.compose.ui.graphics.Color(axis.glassBorder).copy(alpha = 0.85f),
                    androidx.compose.ui.graphics.Color(axis.glassBorder).copy(alpha = 0f)
                )
            ),
            start = Offset(centerX, 0f),
            end = Offset(centerX, size.height),
            strokeWidth = 1.5.dp.toPx()
        )

        // Day separators follow local midnight, so DST days stay honest.
        val startDay = Instant.ofEpochMilli(from.toLong()).atZone(zone).toLocalDate()
        val endDay = Instant.ofEpochMilli(to.toLong()).atZone(zone).toLocalDate()
        var day = startDay
        var guard = 0
        while (!day.isAfter(endDay) && guard < 400) {
            val dayStart = day.atStartOfDay(zone).toInstant().toEpochMilli()
            val y = geometry.yOf(dayStart, anchor, heightPx, hours)
            if (y > -30f && y < size.height + 30f) {
                drawLine(
                    color = androidx.compose.ui.graphics.Color(axis.gridLine).copy(alpha = 0.5f),
                    start = Offset(0f, y),
                    end = Offset(size.width, y),
                    strokeWidth = 1.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f))
                )
                val measured = measurer.measure(day.format(DAY_FORMAT), dayStyle)
                drawText(measured, topLeft = Offset(12.dp.toPx(), y + 4.dp.toPx()))
            }
            day = day.plusDays(1)
            guard++
        }

        // Hour ticks computed in wall-clock minutes so labels match the ruler through DST.
        var lastTickLabelBottom = Float.NEGATIVE_INFINITY
        tickLabels.forEach { tick ->
            val y = geometry.yOf(tick.instantMillis, anchor, heightPx, hours)
            if (y > -20f && y < size.height + 20f) {
                drawLine(
                    color = androidx.compose.ui.graphics.Color(axis.gridLine),
                    start = Offset(centerX - 8f, y),
                    end = Offset(centerX - 2f, y),
                    strokeWidth = 1.dp.toPx()
                )
                drawLine(
                    color = androidx.compose.ui.graphics.Color(axis.gridLine),
                    start = Offset(centerX + 2f, y),
                    end = Offset(centerX + 8f, y),
                    strokeWidth = 1.dp.toPx()
                )
                val measured = measurer.measure(tick.text, tickStyle)
                val labelTop = y - measured.size.height / 2f
                val labelBottom = y + measured.size.height / 2f
                if (y > measured.size.height / 2 &&
                    y < size.height - measured.size.height / 2 &&
                    labelTop >= lastTickLabelBottom + 4.dp.toPx()
                ) {
                    drawText(
                        measured,
                        topLeft = Offset(centerX - measured.size.width / 2f, labelTop)
                    )
                    lastTickLabelBottom = labelBottom
                }
            }
        }

        }
        // NOW at its true position, never pinned to the centre.
        val nowY = geometry.yOf(nowMillis, anchor, heightPx, hours)
        if (nowOnly && nowY in 0f..size.height) {
            drawLine(
                brush = Brush.horizontalGradient(
                    listOf(
                        androidx.compose.ui.graphics.Color(axis.nowLine).copy(alpha = 0f),
                        androidx.compose.ui.graphics.Color(axis.nowLine).copy(alpha = 0.85f),
                        androidx.compose.ui.graphics.Color(axis.nowLine).copy(alpha = 0.85f),
                        androidx.compose.ui.graphics.Color(axis.nowLine).copy(alpha = 0f)
                    )
                ),
                start = Offset(0f, nowY),
                end = Offset(size.width, nowY),
                strokeWidth = 1.6.dp.toPx()
            )
            drawCircle(androidx.compose.ui.graphics.Color(axis.nowLine), radius = 4.dp.toPx(), center = Offset(centerX, nowY))
            drawCircle(
                androidx.compose.ui.graphics.Color(axis.nowLine).copy(alpha = 0.2f),
                radius = 9.dp.toPx(),
                center = Offset(centerX, nowY)
            )
            val text = "现在 " + Instant.ofEpochMilli(nowMillis).atZone(zone).format(CLOCK_FORMAT)
            val measured = measurer.measure(
                text,
                TextStyle(color = androidx.compose.ui.graphics.Color(axis.nowLine), fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
            )
            val badgeSize = Size(measured.size.width + 18f, measured.size.height + 8f)
            val tickObstacles = mutableListOf<Rect>()
            var lastObstacleLabelBottom = Float.NEGATIVE_INFINITY
            tickLabels.forEach { tick ->
                val y = geometry.yOf(tick.instantMillis, anchor, heightPx, hours)
                if (y > -20f && y < size.height + 20f) {
                    val tickMeasured = measurer.measure(tick.text, tickStyle)
                    val labelTop = y - tickMeasured.size.height / 2f
                    val labelBottom = y + tickMeasured.size.height / 2f
                    if (y > tickMeasured.size.height / 2f &&
                        y < size.height - tickMeasured.size.height / 2f &&
                        labelTop >= lastObstacleLabelBottom + 4.dp.toPx()
                    ) {
                        tickObstacles += Rect(
                            left = centerX - tickMeasured.size.width / 2f,
                            top = labelTop,
                            right = centerX + tickMeasured.size.width / 2f,
                            bottom = labelBottom
                        )
                        lastObstacleLabelBottom = labelBottom
                    }
                }
            }
            val badgeTopLeft = findUnblockedLabelTopLeft(
                preferred = Offset(centerX - badgeSize.width / 2f, nowY - badgeSize.height / 2f),
                labelSize = badgeSize,
                canvasSize = size,
                obstacles = tickObstacles + eventObstacles,
                stepPx = 4.dp.toPx(),
                gapPx = 2.dp.toPx()
            )
            drawRoundRect(
                color = scheme.surface.copy(alpha = 0.94f),
                topLeft = badgeTopLeft,
                size = badgeSize,
                cornerRadius = CornerRadius(10f, 10f)
            )
            drawText(
                measured,
                topLeft = badgeTopLeft + Offset(9f, 4f)
            )
        }
    }
}
