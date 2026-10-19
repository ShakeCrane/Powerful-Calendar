package dev.powerfulcalendar.ui.v2

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Today
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import dev.powerfulcalendar.core.AxisSide
import dev.powerfulcalendar.core.TemporalKind
import dev.powerfulcalendar.core.TemporalRecord
import dev.powerfulcalendar.core.TemporalText
import dev.powerfulcalendar.core.TimelineCoordinate
import dev.powerfulcalendar.core.TimelineItem
import dev.powerfulcalendar.core.TimelineLayout
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlin.math.exp
import kotlinx.coroutines.launch

private val CLOCK_FORMAT = DateTimeFormatter.ofPattern("HH:mm")
private val DAY_FORMAT = DateTimeFormatter.ofPattern("M月d日 EEE")

/**
 * The central vertical axis: earlier above, later below, alarms left, activities and deadlines
 * right. There is deliberately no day container and no midnight lock: the camera is a continuous
 * (instant, zoom) pair, which is also exactly what survives a 待处理 round trip.
 */
@Composable
fun TimelineScreen(
    records: List<TemporalRecord>,
    camera: TimelineCoordinate.Camera,
    nowMillis: Long,
    zone: ZoneId,
    pendingCount: Int,
    aiLocked: Boolean,
    onCameraChange: (TimelineCoordinate.Camera) -> Unit,
    onOpenEvent: (TemporalRecord) -> Unit,
    onMoveEvent: (TemporalRecord, Long) -> Unit,
    onCreate: (TemporalKind, Long) -> Unit,
    onToday: () -> Unit,
    onOpenDeck: () -> Unit,
    onOpenTools: () -> Unit,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val effects = LocalGlassEffects.current
    val scope = rememberCoroutineScope()
    val latestCamera by rememberUpdatedState(camera)

    BoxWithConstraints(modifier.fillMaxSize()) {
        val viewportWidth = maxWidth
        val heightPx = with(density) { maxHeight.toPx() }.coerceAtLeast(1f)
        val widthPx = with(density) { viewportWidth.toPx() }
        val hours = camera.hours
        val anchor = camera.anchorMillis
        val millisPerPx = TimelineCoordinate.spanMillis(hours) / heightPx
        val maxLanes = if (viewportWidth < 380.dp) 2 else 3

        val layout = remember(records, maxLanes) {
            TimelineLayout.layout(records, maxLanesPerSide = maxLanes)
        }

        fun cam(newAnchor: Long, newHours: Double) = TimelineCoordinate.Camera(
            newAnchor,
            TimelineCoordinate.clampHours(newHours)
        )

        suspend fun fling(velocityY: Float, viewportHeight: Float) {
            if (abs(velocityY) < 120f) return
            var velocity = velocityY
            var lastFrame = withFrameNanos { it }
            while (abs(velocity) > 24f) {
                val frame = withFrameNanos { it }
                val dt = ((frame - lastFrame) / 1_000_000_000.0).toFloat().coerceIn(0f, 0.05f)
                lastFrame = frame
                val current = latestCamera
                val perPx = TimelineCoordinate.spanMillis(current.hours) / viewportHeight
                onCameraChange(
                    TimelineCoordinate.Camera(
                        current.anchorMillis - (velocity * dt * perPx).toLong(),
                        current.hours
                    )
                )
                velocity *= exp(-3.4f * dt)
            }
        }

        val gestureModifier = Modifier.pointerInput(Unit) {
            panZoomGestures(
                latestCamera = { latestCamera },
                heightPx = heightPx,
                onCamera = { c -> onCameraChange(c) },
                onFlingEnd = { velocityY -> scope.launch { fling(velocityY, heightPx) } }
            )
        }

        var draggedSeq by remember { mutableStateOf<Long?>(null) }
        var dragOffsetY by remember { mutableStateOf(0f) }
        var dragTarget by remember { mutableStateOf<Long?>(null) }

        Box(
            Modifier
                .fillMaxSize()
                .background(axisBackground())
                .then(gestureModifier)
        ) {
            TimelineCanvas(
                anchor = anchor,
                hours = hours,
                heightPx = heightPx,
                widthPx = widthPx,
                nowMillis = nowMillis,
                zone = zone
            )

            layout.items.forEach { item ->
                val itemY = TimelineCoordinate.yOf(item.anchorMillis, anchor, heightPx, hours)
                if (itemY < -140f || itemY > heightPx + 140f) return@forEach
                val dragging = draggedSeq == item.record.eventSeq
                val laneShift = 36.dp * item.lane
                val laneOffset: Dp = 10.dp + laneShift
                val available = viewportWidth / 2 - 12.dp - laneShift
                val chipWidth = available.coerceIn(90.dp, 230.dp)
                val x = if (item.side == AxisSide.LEFT) {
                    viewportWidth / 2 - laneOffset - chipWidth
                } else {
                    viewportWidth / 2 + laneOffset
                }
                TimelineChip(
                    item = item,
                    y = itemY,
                    x = x,
                    width = chipWidth,
                    dragging = dragging,
                    modifier = Modifier
                        .zIndex(if (dragging) 12f else 1f)
                        .pointerInput(item.record.eventSeq, item.record.revision, camera.hours) {
                            detectDragGesturesAfterLongPress(
                                onDragStart = {
                                    draggedSeq = item.record.eventSeq
                                    dragOffsetY = 0f
                                    dragTarget = item.anchorMillis
                                },
                                onDrag = { change, amount ->
                                    change.consume()
                                    dragOffsetY += amount.y
                                    val raw = item.anchorMillis + (dragOffsetY * millisPerPx).toLong()
                                    dragTarget = if (item.dateOnly) {
                                        instantToDayStart(raw, zone)
                                    } else {
                                        TimelineCoordinate.snap(raw, hours, zone)
                                    }
                                },
                                onDragEnd = {
                                    val target = dragTarget
                                    draggedSeq = null
                                    dragOffsetY = 0f
                                    dragTarget = null
                                    if (target != null && target != item.anchorMillis) {
                                        onMoveEvent(item.record, target)
                                    }
                                },
                                onDragCancel = {
                                    draggedSeq = null
                                    dragOffsetY = 0f
                                    dragTarget = null
                                }
                            )
                        }
                        .clickable { onOpenEvent(item.record) }
                )
            }

            draggedSeq?.let { seq ->
                dragTarget?.let { target ->
                    DragPreview(
                        millis = target,
                        anchor = anchor,
                        hours = hours,
                        heightPx = heightPx,
                        widthPx = widthPx,
                        zone = zone,
                        seq = seq
                    )
                }
            }

            if (layout.overflow.isNotEmpty()) {
                OverflowBadges(layout.overflow, anchor, hours, heightPx, widthPx)
            }

            if (records.isEmpty()) {
                Surface(
                    modifier = Modifier.align(Alignment.Center).padding(24.dp),
                    shape = GlassShapes.card,
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                    tonalElevation = 2.dp
                ) {
                    Column(
                        Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text("时间轴还是空的", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "点右下角 + 创建闹钟、活动或截止；也可以用自然语言或截图生成待确认草稿。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            TimelineTopBar(
                cameraHours = hours,
                pendingCount = pendingCount,
                aiLocked = aiLocked,
                onToday = onToday,
                onOpenDeck = onOpenDeck,
                onOpenTools = onOpenTools,
                onZoomIn = {
                    val newHours = TimelineCoordinate.clampHours(hours / 1.5)
                    val newAnchor = TimelineCoordinate.anchorPreservingFocus(
                        anchor, hours, newHours, heightPx / 2f, heightPx
                    )
                    onCameraChange(cam(newAnchor, newHours))
                },
                onZoomOut = {
                    val newHours = TimelineCoordinate.clampHours(hours * 1.5)
                    val newAnchor = TimelineCoordinate.anchorPreservingFocus(
                        anchor, hours, newHours, heightPx / 2f, heightPx
                    )
                    onCameraChange(cam(newAnchor, newHours))
                },
                modifier = Modifier.align(Alignment.TopCenter)
            )

            AddButton(
                onCreate = { kind -> onCreate(kind, TimelineCoordinate.snap(anchor, hours, zone)) },
                modifier = Modifier.align(Alignment.BottomEnd).padding(18.dp)
            )

            if (effects.reduceMotion) {
                Text(
                    "已遵循系统减少动画设置",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 10.dp)
                )
            }
        }
    }
}

/** Zoom is anchored to the gesture centroid; single-finger pan comes from the same event stream. */
private suspend fun androidx.compose.ui.input.pointer.PointerInputScope.panZoomGestures(
    latestCamera: () -> TimelineCoordinate.Camera,
    heightPx: Float,
    onCamera: (TimelineCoordinate.Camera) -> Unit,
    onFlingEnd: (Float) -> Unit
) {
    val tracker = VelocityTracker()
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        tracker.resetTracking()
        tracker.addPosition(down.uptimeMillis, down.position)
        var lastCentroidY = down.position.y
        var moved = false
        var zooming = false
        while (true) {
            val event: PointerEvent = awaitPointerEvent()
            val pressed = event.changes.filter { it.pressed }
            if (pressed.isEmpty()) break
            if (event.changes.any { it.isConsumed }) {
                // A card being moved or a text field owns the gesture.
                continue
            }
            val centroidY = pressed.map { it.position.y }.average().toFloat()
            val panY = centroidY - lastCentroidY
            val zoom = event.calculateZoom()
            if (pressed.size >= 2) zooming = true
            var camera = latestCamera()
            var changed = false
            if (zooming && zoom != 1f && zoom > 0f) {
                val newHours = TimelineCoordinate.clampHours(camera.hours / zoom)
                val newAnchor = TimelineCoordinate.anchorPreservingFocus(
                    camera.anchorMillis, camera.hours, newHours, centroidY, heightPx
                )
                camera = TimelineCoordinate.Camera(newAnchor, newHours)
                changed = true
            }
            if (panY != 0f) {
                val millisPerPx = TimelineCoordinate.spanMillis(camera.hours) / heightPx
                camera = camera.copy(anchorMillis = camera.anchorMillis - (panY * millisPerPx).toLong())
                changed = true
                pressed.forEach { if (it.positionChanged()) it.consume() }
            }
            if (zooming) pressed.forEach { it.consume() }
            if (changed) {
                onCamera(camera)
                moved = true
            }
            lastCentroidY = centroidY
            tracker.addPosition(pressed.first().uptimeMillis, pressed.first().position)
        }
        if (moved) onFlingEnd(tracker.calculateVelocity().y)
    }
}

@Composable
private fun TimelineCanvas(
    anchor: Long,
    hours: Double,
    heightPx: Float,
    widthPx: Float,
    nowMillis: Long,
    zone: ZoneId
) {
    val palette = LocalAxisPalette.current
    val scheme = MaterialTheme.colorScheme
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val tickStyle = TextStyle(color = scheme.onSurfaceVariant, fontSize = 11.sp, fontWeight = FontWeight.Medium)
    val dayStyle = TextStyle(color = scheme.onSurfaceVariant, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    val centerX = widthPx / 2f
    val spanMillis = TimelineCoordinate.spanMillis(hours)
    val from = anchor - spanMillis / 2
    val to = anchor + spanMillis / 2
    val tickMinutes = when {
        hours <= 6 -> 30L
        hours <= 12 -> 60L
        hours <= 26 -> 120L
        else -> 240L
    }

    Canvas(Modifier.fillMaxSize()) {
        drawLine(
            brush = Brush.verticalGradient(
                listOf(
                    palette.glassBorder.copy(alpha = 0f),
                    palette.glassBorder.copy(alpha = 0.85f),
                    palette.glassBorder.copy(alpha = 0.85f),
                    palette.glassBorder.copy(alpha = 0f)
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
            val y = TimelineCoordinate.yOf(dayStart, anchor, heightPx, hours)
            if (y > -30f && y < size.height + 30f) {
                drawLine(
                    color = palette.gridLine.copy(alpha = 0.5f),
                    start = Offset(0f, y),
                    end = Offset(size.width, y),
                    strokeWidth = 1.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f))
                )
                val measured = measurer.measure(day.format(DAY_FORMAT), dayStyle)
                drawText(measured, topLeft = Offset(centerX + 12f, y + 4f))
            }
            day = day.plusDays(1)
            guard++
        }

        // Hour ticks computed in wall-clock minutes so labels match the ruler through DST.
        var tickLocal = Instant.ofEpochMilli(from.toLong()).atZone(zone).toLocalDateTime()
        tickLocal = tickLocal.withSecond(0).withNano(0)
        val remainder = tickLocal.minute % tickMinutes
        tickLocal = tickLocal.minusMinutes(remainder.toLong())
        var ticks = 0
        while (ticks < 400) {
            val tickMillis = tickLocal.atZone(zone).toInstant().toEpochMilli()
            if (tickMillis > to) break
            val y = TimelineCoordinate.yOf(tickMillis, anchor, heightPx, hours)
            if (y > -20f && y < size.height + 20f) {
                drawLine(
                    color = palette.gridLine,
                    start = Offset(centerX - 8f, y),
                    end = Offset(centerX - 2f, y),
                    strokeWidth = 1.dp.toPx()
                )
                drawLine(
                    color = palette.gridLine,
                    start = Offset(centerX + 2f, y),
                    end = Offset(centerX + 8f, y),
                    strokeWidth = 1.dp.toPx()
                )
                val measured = measurer.measure(tickLocal.format(CLOCK_FORMAT), tickStyle)
                drawText(
                    measured,
                    topLeft = Offset(centerX - 13f - measured.size.width, y - measured.size.height / 2f)
                )
            }
            tickLocal = tickLocal.plusMinutes(tickMinutes)
            ticks++
        }

        // NOW at its true position, never pinned to the centre.
        val nowY = TimelineCoordinate.yOf(nowMillis, anchor, heightPx, hours)
        if (nowY in 0f..size.height) {
            drawLine(
                brush = Brush.horizontalGradient(
                    listOf(
                        palette.nowLine.copy(alpha = 0f),
                        palette.nowLine.copy(alpha = 0.85f),
                        palette.nowLine.copy(alpha = 0.85f),
                        palette.nowLine.copy(alpha = 0f)
                    )
                ),
                start = Offset(0f, nowY),
                end = Offset(size.width, nowY),
                strokeWidth = 1.6.dp.toPx()
            )
            drawCircle(palette.nowLine, radius = 4.dp.toPx(), center = Offset(centerX, nowY))
            drawCircle(
                palette.nowLine.copy(alpha = 0.2f),
                radius = 9.dp.toPx(),
                center = Offset(centerX, nowY)
            )
            val text = "现在 " + Instant.ofEpochMilli(nowMillis).atZone(zone).format(CLOCK_FORMAT)
            val measured = measurer.measure(
                text,
                TextStyle(color = palette.nowLine, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
            )
            drawRoundRect(
                color = scheme.surface.copy(alpha = 0.94f),
                topLeft = Offset(centerX - measured.size.width / 2f - 9f, nowY - measured.size.height / 2f - 4f),
                size = Size(measured.size.width + 18f, measured.size.height + 8f),
                cornerRadius = CornerRadius(10f, 10f)
            )
            drawText(
                measured,
                topLeft = Offset(centerX - measured.size.width / 2f, nowY - measured.size.height / 2f)
            )
        }
        if (widthPx <= 0f) Unit
    }
}

@Composable
private fun TimelineChip(
    item: TimelineItem,
    y: Float,
    x: Dp,
    width: Dp,
    dragging: Boolean,
    modifier: Modifier = Modifier
) {
    val palette = LocalAxisPalette.current
    val accent = kindColor(item.record.kind)
    val chipHeight = if (item.dateOnly) 32.dp else 38.dp
    val density = LocalDensity.current
    val offsetY: Dp = with(density) { y.toDp() } - chipHeight / 2
    val visibleAlpha by animateFloatAsState(if (dragging) 0.28f else 1f, label = "chipDrag")
    Surface(
        modifier = modifier
            .offset(x = x, y = offsetY)
            .width(width)
            .height(chipHeight)
            .alpha(visibleAlpha),
        shape = GlassShapes.chip,
        color = palette.glassFill,
        tonalElevation = 0.dp,
        shadowElevation = 1.dp,
        border = BorderStroke(0.9.dp, accent.copy(alpha = 0.36f))
    ) {
        Row(
            Modifier.fillMaxSize().padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier
                    .width(3.dp)
                    .height(if (item.dateOnly) 16.dp else 22.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(accent)
            )
            Spacer(Modifier.width(6.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
                Text(
                    item.record.title.ifBlank { "未命名" },
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    if (item.dateOnly) "日期 · " + (item.record.deadlineDate ?: "")
                    else TemporalText.summarize(item.record),
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = accent
                )
            }
            if (item.record.recurrence.isRepeating) {
                Icon(
                    Icons.Default.Schedule,
                    contentDescription = "重复事件",
                    modifier = Modifier.size(13.dp),
                    tint = accent.copy(alpha = 0.85f)
                )
            }
        }
    }
}

@Composable
private fun DragPreview(
    millis: Long,
    anchor: Long,
    hours: Double,
    heightPx: Float,
    widthPx: Float,
    zone: ZoneId,
    seq: Long
) {
    val palette = LocalAxisPalette.current
    val density = LocalDensity.current
    val y = TimelineCoordinate.yOf(millis, anchor, heightPx, hours)
    val label = Instant.ofEpochMilli(millis).atZone(zone).format(CLOCK_FORMAT)
    Canvas(Modifier.fillMaxSize()) {
        drawLine(
            color = palette.nowLine.copy(alpha = 0.7f),
            start = Offset(0f, y),
            end = Offset(size.width, y),
            strokeWidth = 1.2.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 8f))
        )
    }
    Surface(
        modifier = Modifier.offset(
            x = with(density) { (widthPx / 2).toDp() } + 18.dp,
            y = with(density) { y.toDp() } - 17.dp
        ),
        shape = GlassShapes.chip,
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 4.dp
    ) {
        Text(
            "吸附 $label · ${TemporalRecord.formatSeq(seq)}",
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            style = MaterialTheme.typography.labelMedium
        )
    }
}

@Composable
private fun OverflowBadges(
    overflow: Map<AxisSide, List<TemporalRecord>>,
    anchor: Long,
    hours: Double,
    heightPx: Float,
    widthPx: Float
) {
    val density = LocalDensity.current
    overflow.forEach { (side, items) ->
        val firstAnchor = items.mapNotNull { it.anchorMillis() }.minOrNull() ?: anchor
        val y = TimelineCoordinate.yOf(firstAnchor, anchor, heightPx, hours)
        val x = if (side == AxisSide.LEFT) {
            with(density) { (widthPx / 2).toDp() } - 42.dp
        } else {
            with(density) { (widthPx / 2).toDp() } + 28.dp
        }
        Surface(
            modifier = Modifier.offset(x = x, y = with(density) { y.toDp() } - 11.dp),
            shape = GlassShapes.chip,
            color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.95f),
            tonalElevation = 2.dp
        ) {
            Text(
                "+${items.size}",
                modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                style = MaterialTheme.typography.labelSmall
            )
        }
    }
}

@Composable
private fun TimelineTopBar(
    cameraHours: Double,
    pendingCount: Int,
    aiLocked: Boolean,
    onToday: () -> Unit,
    onOpenDeck: () -> Unit,
    onOpenTools: () -> Unit,
    onZoomIn: () -> Unit,
    onZoomOut: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 10.dp),
        shape = GlassShapes.bar,
        color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.9f),
        tonalElevation = 3.dp,
        shadowElevation = 6.dp
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("时间轴", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = 6.dp))
            Spacer(Modifier.weight(1f))
            IconButton(onClick = onZoomOut, modifier = Modifier.size(40.dp)) {
                Icon(Icons.Default.Remove, "缩小时间范围", modifier = Modifier.size(18.dp))
            }
            Text(
                "${formatHours(cameraHours)}/屏",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            IconButton(onClick = onZoomIn, modifier = Modifier.size(40.dp)) {
                Icon(Icons.Default.Add, "放大时间范围", modifier = Modifier.size(18.dp))
            }
            Surface(
                onClick = onToday,
                shape = GlassShapes.chip,
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.padding(start = 2.dp)
            ) {
                Row(
                    Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Today, null, modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("今日", style = MaterialTheme.typography.labelMedium)
                }
            }
            Spacer(Modifier.width(6.dp))
            BadgedBox(
                badge = {
                    if (pendingCount > 0) Badge { Text(pendingCount.coerceAtMost(99).toString()) }
                }
            ) {
                Surface(
                    onClick = onOpenDeck,
                    shape = GlassShapes.chip,
                    color = if (aiLocked) MaterialTheme.colorScheme.tertiaryContainer
                    else MaterialTheme.colorScheme.surfaceContainerHigh
                ) {
                    Row(
                        Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Layers, null, modifier = Modifier.size(15.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("待处理", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
            Spacer(Modifier.width(4.dp))
            IconButton(onClick = onOpenTools, modifier = Modifier.size(40.dp)) {
                Icon(Icons.Default.MoreVert, "导入、AI 与设置", modifier = Modifier.size(20.dp))
            }
        }
    }
}

@Composable
private fun AddButton(onCreate: (TemporalKind) -> Unit, modifier: Modifier = Modifier) {
    var menu by remember { mutableStateOf(false) }
    Box(modifier) {
        Surface(
            onClick = { menu = true },
            shape = RoundedCornerShape(50),
            color = MaterialTheme.colorScheme.primary,
            shadowElevation = 8.dp
        ) {
            Box(Modifier.size(56.dp), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.Add, "新建事件", tint = MaterialTheme.colorScheme.onPrimary)
            }
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            KindItem("闹钟 · 只有开始时刻", Icons.Default.Alarm, TemporalKind.ALARM) {
                menu = false; onCreate(TemporalKind.ALARM)
            }
            KindItem("活动 · 开始到结束", Icons.Default.PlayArrow, TemporalKind.ACTIVITY) {
                menu = false; onCreate(TemporalKind.ACTIVITY)
            }
            KindItem("截止 · 时刻或日期", Icons.Default.Flag, TemporalKind.DEADLINE) {
                menu = false; onCreate(TemporalKind.DEADLINE)
            }
        }
    }
}

@Composable
private fun KindItem(
    label: String,
    icon: ImageVector,
    kind: TemporalKind,
    onClick: () -> Unit
) {
    DropdownMenuItem(
        text = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, null, modifier = Modifier.size(18.dp), tint = kindColor(kind))
                Spacer(Modifier.width(10.dp))
                Text(label)
            }
        },
        onClick = onClick
    )
}

private fun instantToDayStart(millis: Long, zone: ZoneId): Long =
    Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()
        .atStartOfDay(zone).toInstant().toEpochMilli()

private fun formatHours(hours: Double): String =
    if (hours == hours.toInt().toDouble()) "${hours.toInt()}h"
    else String.format(Locale.US, "%.1fh", hours)

/** Kept for callers that need the elapsed span of a visible window in tests/debug overlays. */
internal fun visibleSpanMillis(hours: Double): Long = (hours * 3_600_000.0).toLong()

internal fun Duration.asReadableHours(): String =
    String.format(Locale.US, "%.1f", toMinutes() / 60.0)
