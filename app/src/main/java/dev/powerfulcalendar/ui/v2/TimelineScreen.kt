package dev.powerfulcalendar.ui.v2

import androidx.compose.animation.core.animate
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import dev.powerfulcalendar.core.*
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.abs
import kotlin.math.exp
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

private val CLOCK_FORMAT = DateTimeFormatter.ofPattern("HH:mm")
private val DAY_FORMAT = DateTimeFormatter.ofPattern("M月d日 EEE", java.util.Locale.SIMPLIFIED_CHINESE)
private val HEADER_DAY_FORMAT = DateTimeFormatter.ofPattern("yyyy年M月d日 EEE", java.util.Locale.SIMPLIFIED_CHINESE)

/** Every UI projection retains its formal owner; moving a repeating slot is never a series edit. */
@Composable
fun TimelineScreen(
    records: List<TemporalRecord>, camera: TimelineCoordinate.Camera, nowMillis: Long, zone: ZoneId,
    pendingCount: Int, aiLocked: Boolean, onCameraChange: (TimelineCoordinate.Camera) -> Unit,
    onOpenEvent: (TemporalRecord) -> Unit, onMoveEvent: (TemporalRecord, Long) -> Unit,
    onCreate: (TemporalKind, Long) -> Unit, onToday: () -> Unit, onOpenDeck: () -> Unit,
    onOpenTools: () -> Unit, onOpenMonth: () -> Unit = {},
    modifier: Modifier = Modifier, blockedReminders: Int = 0,
    horizontalScale: Float = 1f, onHorizontalScaleChange: (Float) -> Unit = {}
) {
    val scope = rememberCoroutineScope()
    val effects = LocalGlassEffects.current
    val motion = glassMotion()
    val latestCamera by rememberUpdatedState(camera)
    val latestHorizontalScale by rememberUpdatedState(horizontalScale)
    val horizontalScaleChange by rememberUpdatedState(onHorizontalScaleChange)
    var inertia by remember { mutableStateOf<Job?>(null) }
    var hidden by remember { mutableStateOf<List<TemporalRecord>?>(null) }
    var inspected by remember { mutableStateOf<TemporalRecord?>(null) }
    val owners = remember(records) { records.associateBy { it.eventSeq } }
    val day = Instant.ofEpochMilli(camera.anchorMillis).atZone(zone).toLocalDate()
    val projection = remember(records, day, zone) {
        // Cache an overscan window independently from pixel scroll. Repeating slots and one-off
        // overrides/exceptions share the same read-only occurrence projection.
        val from = day.minusDays(8).atStartOfDay(zone).toInstant().toEpochMilli()
        val to = day.plusDays(9).atStartOfDay(zone).toInstant().toEpochMilli()
        records.filter { !it.recurrence.isRepeating }.mapNotNull(::singleDisplayOccurrence) +
            displayOccurrences(records.filter { it.recurrence.isRepeating }, from, to)
    }
    val slotKeys = remember(projection) {
        java.util.IdentityHashMap<TemporalRecord, String>().apply {
            projection.forEach { put(it.record, it.key) }
        }
    }
    fun open(record: TemporalRecord) {
        inertia?.cancel()
        val owner = owners[record.eventSeq] ?: record
        if (aiLocked) inspected = record else onOpenEvent(owner)
    }
    fun changeCamera(updated: TimelineCoordinate.Camera) { inertia?.cancel(); onCameraChange(updated) }

    Column(modifier.fillMaxSize().background(axisBackground()).skinBackdrop(SkinSlot.TIMELINE_BACKGROUND)) {
        // Single actionable toolbar. Page navigation lives ONLY in the bottom tab bar.
        var createMenu by remember { mutableStateOf(false) }
        Row(
            Modifier.fillMaxWidth().padding(start = 14.dp, end = 6.dp, top = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    SkinImage(SkinSlot.BRAND_MARK, Modifier.size(19.dp))
                    Text("Powerful Calendar", style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Text(day.format(HEADER_DAY_FORMAT), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = { inertia?.cancel(); onToday() }, modifier = Modifier.size(44.dp)) {
                Icon(Icons.Default.Today, "今日 · 复位为全天")
            }
            IconButton(onClick = { inertia?.cancel(); onOpenDeck() }, modifier = Modifier.size(44.dp)) {
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
                    TemporalKind.entries.forEach { kind ->
                        DropdownMenuItem(text = { Text(kind.label) }, onClick = {
                            createMenu = false
                            onCreate(kind, TimelineCoordinate.snap(camera.anchorMillis, camera.hours, zone))
                        })
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { changeCamera(camera.copy(hours = TimelineCoordinate.clampHours(camera.hours * 1.5))) }, enabled = camera.hours < 48) {
                Icon(Icons.Default.Remove, "缩小，显示更长时间")
            }
            Text("${if (camera.hours % 1.0 == 0.0) camera.hours.toInt().toString() else String.format(java.util.Locale.US, "%.1f", camera.hours)} 小时 / 屏",
                style = MaterialTheme.typography.labelMedium, modifier = Modifier.semantics { stateDescription = "时间轴缩放 ${camera.hours} 小时" })
            IconButton(onClick = { changeCamera(camera.copy(hours = TimelineCoordinate.clampHours(camera.hours / 1.5))) }, enabled = camera.hours > TimelineCoordinate.MIN_HOURS) {
                Icon(Icons.Default.Add, "放大，显示更短时间")
            }
            Spacer(Modifier.weight(1f))
            Text(if (aiLocked) "AI 处理中" else "↕ 时间 · ↔ 事件轴", style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(end = 8.dp))
        }
        if (blockedReminders > 0) Surface(onClick = onOpenTools, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            shape = GlassShapes.chip, color = MaterialTheme.colorScheme.errorContainer) {
            Row(Modifier.heightIn(min = 48.dp).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.ErrorOutline, null, Modifier.size(20.dp)); Spacer(Modifier.width(8.dp))
                Text("$blockedReminders 项提醒受阻 · 查看权限与状态", style = MaterialTheme.typography.bodyMedium)
            }
        }
        val halfSpan = (TimelineCoordinate.spanMillis(camera.hours) / 2).toLong()
        val visibleDates = projection.filter { entry ->
            val record = entry.record
            if (!record.isDateOnlyDeadline()) false else {
                val start = LocalDate.parse(record.deadlineDate).atStartOfDay(record.zone).toInstant().toEpochMilli()
                val end = LocalDate.parse(record.deadlineDate).plusDays(1).atStartOfDay(record.zone).toInstant().toEpochMilli()
                start < camera.anchorMillis + halfSpan && end > camera.anchorMillis - halfSpan
            }
        }
        if (records.any { it.isDateOnlyDeadline() }) Box(Modifier.fillMaxWidth().height(64.dp * LocalDensity.current.fontScale), contentAlignment = Alignment.Center) {
        if (visibleDates.isEmpty()) Text("可见日期内没有日期级截止", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        LazyRow(Modifier.fillMaxWidth().padding(bottom = 8.dp), contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(visibleDates, key = { it.key }) { entry ->
                Surface(onClick = { open(entry.record) }, shape = GlassShapes.chip, color = MaterialTheme.colorScheme.tertiaryContainer) {
                    Row(Modifier.heightIn(min = 48.dp).padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Flag, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp))
                        Column(Modifier.widthIn(max = 240.dp)) {
                            Text(entry.record.title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelLarge)
                            Text("${entry.record.deadlineDate} · 截止，无具体时刻", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }
        }
        BoxWithConstraints(Modifier.fillMaxWidth().weight(1f).clip(RoundedCornerShape(0.dp))) {
            val density = LocalDensity.current
            val height = with(density) { maxHeight.toPx() }.coerceAtLeast(1f)
            val viewportWidth = maxWidth
            val width = with(density) { viewportWidth.toPx() }
            val heightNow by rememberUpdatedState(height)
            val unlocked by rememberUpdatedState(!aiLocked)
            // Zooming the RIGHT EVENT HALF reveals additional overlapping lanes.
            // The left alarm side remains a fixed, point-only stack.
            // NEVER change the number of lanes during a pinch. It would cause discrete
            // reflow at scale thresholds and create phantom collisions as zoom changes.
            val maxLanes = if (maxWidth >= 840.dp) 3 else 2
            val layout = remember(projection, maxLanes) {
                val projected = projection.map { it.record }.filterNot { it.isDateOnlyDeadline() }
                val fixedLeft = TimelineLayout.layout(projected, 1)
                val expandableRight = TimelineLayout.layout(projected, maxLanes)
                TimelineLayout.LayoutResult(
                    items = fixedLeft.items.filter { it.side == AxisSide.LEFT } +
                        expandableRight.items.filter { it.side == AxisSide.RIGHT },
                    overflow = mapOf(
                        AxisSide.LEFT to fixedLeft.overflow[AxisSide.LEFT].orEmpty(),
                        AxisSide.RIGHT to expandableRight.overflow[AxisSide.RIGHT].orEmpty()
                    )
                )
            }
            val groups = remember(layout) { overflowGroups(layout.overflow, 0L) }
            var dragKey by remember { mutableStateOf<String?>(null) }
            var dragTarget by remember { mutableStateOf<Long?>(null) }
            var dragTravel by remember { mutableFloatStateOf(0f) }
            val cameraChange by rememberUpdatedState(onCameraChange)

            Box(Modifier.fillMaxSize().testTag("timeline-viewport").pointerInput(Unit) {
                timelineGestures({ latestCamera }, { heightNow }, { inertia?.cancel() }, { cameraChange(it) },
                    { target -> horizontalScaleChange(target.coerceIn(0.65f, 4.2f)) },
                    { latestHorizontalScale },
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
                            cam = cam.copy(anchorMillis = cam.anchorMillis - (speed * dt * TimelineCoordinate.spanMillis(cam.hours) / heightNow).toLong())
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
                TimelineCanvas(camera.anchorMillis, camera.hours, height, width, nowMillis, zone)
                // RULER LOCK: X=0 of the right event content is fixed relative to the
                // center axis. Horizontal scale only stretches its right-hand extent.
                val gutter = 20.dp
                val leftLaneWidth = (viewportWidth / 2 - gutter - 12.dp).coerceAtLeast(48.dp)
                val rightSide = (viewportWidth / 2 - gutter - 12.dp).coerceAtLeast(48.dp)
                val rightLaneWidth = (((rightSide - 6.dp * (maxLanes - 1)) / maxLanes) *
                    horizontalScale.coerceIn(0.65f, 4.2f)).coerceAtLeast(24.dp)
                // Floating titles have their own visual positions; time rectangles NEVER grow.
                var nextFloatingTitleY = 0f
                layout.items.forEach { item ->
                    val y = TimelineCoordinate.yOf(item.anchorMillis, camera.anchorMillis, height, camera.hours)
                    val endY = TimelineCoordinate.yOf(item.record.spanEndMillis() ?: item.anchorMillis, camera.anchorMillis, height, camera.hours)
                    if (endY < -80 || y > height + 80) return@forEach
                    val key = slotKeys[item.record] ?: "${item.record.eventSeq}:${item.anchorMillis}"
                    // Activities use real start/end projected onto the same ruler.
                    // Never inflate a short interval to fit typography or the touch target.
                    val activitySpan = if (item.record.kind == TemporalKind.ACTIVITY) {
                        ActivityGeometry.visible(item.anchorMillis,
                            item.record.endMillis ?: item.anchorMillis,
                            camera.anchorMillis, height, camera.hours)
                    } else null
                    if (item.record.kind == TemporalKind.ACTIVITY && activitySpan == null) return@forEach
                    val cardHeight = with(density) {
                        (activitySpan?.heightPx ?: 0f).toDp()
                    }
                    val laneWidth = if (item.side == AxisSide.LEFT) leftLaneWidth else rightLaneWidth
                    val x = if (item.side == AxisSide.LEFT) viewportWidth / 2 - gutter - leftLaneWidth
                        else viewportWidth / 2 + gutter + (rightLaneWidth + 6.dp) * item.lane
                    val point = item.record.kind != TemporalKind.ACTIVITY
                    val pointHeight = 56.dp
                    val topPx = if (item.record.kind == TemporalKind.DEADLINE) {
                        y - with(density) { pointHeight.toPx() }
                    } else if (item.record.kind == TemporalKind.ALARM) y else activitySpan!!.topPx
                    key(key) {
                        Surface(
                            onClick = { if (dragKey == null) open(item.record) },
                            modifier = Modifier.offset(x, with(density) { topPx.toDp() }).width(laneWidth)
                                .height(if (point) pointHeight else cardHeight)
                                .pointerInput(key, item.record.revision, item.anchorMillis, aiLocked) {
                                    if (!unlocked) return@pointerInput
                                    detectDragGesturesAfterLongPress(
                                        onDragStart = { inertia?.cancel(); dragKey = key; dragTarget = item.anchorMillis; dragTravel = 0f },
                                        onDrag = { change, amount ->
                                            change.consume(); dragTravel += amount.y
                                            dragTarget = TimelineCoordinate.snap(item.anchorMillis + (dragTravel * TimelineCoordinate.spanMillis(latestCamera.hours) / heightNow).toLong(), latestCamera.hours, zone)
                                        },
                                        onDragEnd = {
                                            val target = dragTarget
                                            dragKey = null; dragTarget = null
                                            if (target != null && target != item.anchorMillis) onMoveEvent(owners[item.record.eventSeq] ?: item.record, target)
                                        }, onDragCancel = { dragKey = null; dragTarget = null })
                                }
                                .semantics { contentDescription = "${item.record.kind.label}，${item.record.title}，${TemporalText.summarize(item.record)}，${item.record.displaySeq()}" },
                            shape = if (point) RoundedCornerShape(0.dp) else RoundedCornerShape(14.dp),
                            color = if (point) androidx.compose.ui.graphics.Color.Transparent
                                else if (dragKey == key) MaterialTheme.colorScheme.surfaceContainerHighest
                                else LocalAxisPalette.current.glassFill,
                            border = if (point) null else BorderStroke(
                                1.dp, kindColor(item.record.kind).copy(alpha = 0.36f)
                            )
                        ) {
                            if (point) {
                                AxisPointMarker(item.record, item.anchorMillis, zone)
                            } else {
                            // The colored interval is geometrically exact; text appears
                            // only when there is room and must never resize its boundaries.
                            Box(Modifier.fillMaxSize().clip(RoundedCornerShape(0.dp))) {
                                Box(Modifier.align(Alignment.CenterStart).width(3.dp).fillMaxHeight()
                                    .background(kindColor(item.record.kind)))
                                if (cardHeight >= 24.dp) {
                                    Row(Modifier.fillMaxSize().padding(horizontal = 8.dp, vertical = 2.dp),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        if (skinPainter(SkinSlot.ACTIVITY_BADGE) != null && cardHeight >= 55.dp) {
                                            SkinImage(SkinSlot.ACTIVITY_BADGE, Modifier.size(18.dp))
                                        }
                                        Column(Modifier.weight(1f)) {
                                            Text(item.record.title, style = MaterialTheme.typography.labelLarge,
                                                maxLines = if (cardHeight >= 100.dp) 3 else 1,
                                                overflow = TextOverflow.Ellipsis)
                                            if (cardHeight >= 62.dp) {
                                                Text(TemporalText.summarize(item.record),
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = kindColor(item.record.kind),
                                                    maxLines = 2, overflow = TextOverflow.Ellipsis)
                                            }
                                            if (cardHeight >= 105.dp) {
                                                Text(item.record.displaySeq() +
                                                    if (item.record.recurrence.isRepeating) " · 重复" else "",
                                                    style = MaterialTheme.typography.labelSmall)
                                            }
                                        }
                                    }
                                }
                            }
                            }
                        }
                        // A 1-minute event must remain a 1-minute rectangle. Render its
                        // readable title as a separate overlay that has NO effect on time geometry.
                        if (!point && (cardHeight < 28.dp || laneWidth < 116.dp)) {
                            val sourceY = activitySpan!!.topPx
                            val limit = (height - with(density) { 30.dp.toPx() }).coerceAtLeast(0f)
                            val titleY = maxOf(sourceY, nextFloatingTitleY).coerceIn(0f, limit)
                            nextFloatingTitleY = titleY + with(density) { 31.dp.toPx() }
                            val titleX = x.coerceAtMost((viewportWidth - 144.dp).coerceAtLeast(viewportWidth / 2 + gutter))
                            val titleWidth = (viewportWidth - titleX - 6.dp)
                                .coerceAtLeast(64.dp).coerceAtMost(230.dp)
                            Surface(
                                onClick = { open(item.record) },
                                modifier = Modifier.offset(titleX, with(density) { titleY.toDp() })
                                    .width(titleWidth).height(29.dp).zIndex(4f),
                                shape = RoundedCornerShape(7.dp),
                                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
                                border = BorderStroke(1.dp, kindColor(item.record.kind))
                            ) {
                                Text(item.record.title, Modifier.padding(horizontal = 6.dp, vertical = 5.dp),
                                    maxLines = 1, softWrap = false,
                                    overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                }
                groups.forEach { group ->
                    val y = TimelineCoordinate.yOf(group.start, camera.anchorMillis, height, camera.hours)
                    val end = TimelineCoordinate.yOf(group.end, camera.anchorMillis, height, camera.hours)
                    if (end >= 0 && y <= height) Surface(onClick = { hidden = group.records }, shape = GlassShapes.chip,
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier.align(Alignment.TopStart)
                            .offset(
                                x = if (group.side == AxisSide.LEFT)
                                    viewportWidth / 2 - gutter - 48.dp
                                else viewportWidth / 2 + gutter,
                                y = with(density) { y.coerceIn(0f, (height - 48.dp.toPx()).coerceAtLeast(0f)).toDp() }
                            )
                            .size(48.dp).zIndex(3f).semantics { contentDescription = "展开 ${group.records.size} 个被折叠的事件" }) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(if (group.side == AxisSide.LEFT) "×${group.records.size + 1}"
                                else "+${group.records.size}", style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
                TimelineCanvas(camera.anchorMillis, camera.hours, height, width, nowMillis, zone, nowOnly = true)
                dragTarget?.let { target ->
                    val y = TimelineCoordinate.yOf(target, camera.anchorMillis, height, camera.hours)
                    Surface(Modifier.align(Alignment.TopCenter).offset(y = with(density) { y.toDp() }), shape = GlassShapes.chip, color = MaterialTheme.colorScheme.primaryContainer) {
                        Text("${Instant.ofEpochMilli(target).atZone(zone).format(CLOCK_FORMAT)} · 松开后确认草稿", Modifier.padding(10.dp), style = MaterialTheme.typography.labelMedium)
                    }
                }
                if (records.isEmpty()) Text(
                    "还没有事件 · 点击右上角 + 开始",
                    modifier = Modifier.align(Alignment.TopCenter).padding(top = 24.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

    }
    hidden?.let { list ->
        AlertDialog(onDismissRequest = { hidden = null }, title = { Text("重叠事件 · ${list.size}") }, text = {
            Column(Modifier.heightIn(max = 380.dp).verticalScroll(rememberScrollState())) {
                list.forEach { record -> TextButton(onClick = { hidden = null; open(record) }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                    Column(Modifier.fillMaxWidth()) {
                        Text("${record.displaySeq()} · ${record.title}", style = MaterialTheme.typography.bodyLarge)
                        Text(TemporalText.summarize(record), style = MaterialTheme.typography.bodySmall)
                    }
                } }
            }
        }, confirmButton = { TextButton(onClick = { hidden = null }) { Text("关闭") } })
    }
    inspected?.let { record -> AlertDialog(onDismissRequest = { inspected = null }, title = { Text(record.title) },
        text = { Column { Text(TemporalText.summarize(record)); Text(record.notes); Text("AI 正在处理卡组，暂时只可查看。") } },
        confirmButton = { TextButton(onClick = { inspected = null }) { Text("完成") } }) }
}

/**
 * Different temporal primitives must never share the same rectangle.
 * ALARM = line at the top with its label BELOW.
 * DEADLINE = label ABOVE a line exactly at its occurrence instant.
 * ACTIVITY is the only interval rectangle, rendered in the viewport loop.
 */
@Composable
private fun AxisPointMarker(record: TemporalRecord, occurrenceMillis: Long, zone: ZoneId) {
    val color = kindColor(record.kind)
    val label = Instant.ofEpochMilli(occurrenceMillis).atZone(zone).format(CLOCK_FORMAT) +
        " · " + record.title
    Column(Modifier.fillMaxSize()) {
        if (record.kind == TemporalKind.DEADLINE) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.BottomStart) {
                if (skinPainter(SkinSlot.DEADLINE_MARKER) != null) {
                    SkinImage(SkinSlot.DEADLINE_MARKER,
                        Modifier.align(Alignment.TopEnd).size(18.dp))
                }
                Text(
                    label,
                    style = MaterialTheme.typography.labelMedium,
                    color = color,
                    modifier = Modifier.padding(bottom = 5.dp),
                    maxLines = 2, overflow = TextOverflow.Ellipsis
                )
            }
        }
        HorizontalDivider(color = color, thickness = LocalSkinVisual.current.pointLineDp.dp)
        if (record.kind == TemporalKind.ALARM) {
            Row(verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (skinPainter(SkinSlot.ALARM_MARKER) != null) {
                    SkinImage(SkinSlot.ALARM_MARKER, Modifier.size(18.dp))
                }
                Text(
                    label,
                    style = MaterialTheme.typography.labelMedium,
                    color = color,
                    modifier = Modifier.padding(top = 5.dp),
                    maxLines = 2, overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

private suspend fun androidx.compose.ui.input.pointer.PointerInputScope.timelineGestures(
    camera: () -> TimelineCoordinate.Camera, height: () -> Float, onStart: () -> Unit,
    onCamera: (TimelineCoordinate.Camera) -> Unit,
    onHorizontalScale: (Float) -> Unit, currentHorizontalScale: () -> Float,
    onFling: (Float, TimelineCoordinate.Camera) -> Unit
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
        var gestureScale = currentHorizontalScale()
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
                    gestureScale = currentHorizontalScale()
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
                onHorizontalScale((gestureScale * components.x).coerceIn(0.65f, 4.2f))
                cam = transformedCamera(
                    gestureCamera, gestureStartY,
                    gestureStartY + AxisPinch.dampCentroidPan(y - gestureStartY),
                    components.y, height()
                )
            } else {
                cam = transformedCamera(cam, previousY, y, 1f, height())
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
    nowOnly: Boolean = false
) {
    val palette = LocalAxisPalette.current
    val scheme = MaterialTheme.colorScheme
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val tickStyle = TextStyle(color = scheme.onSurfaceVariant, fontSize = 12.sp, fontWeight = FontWeight.Medium)
    val dayStyle = TextStyle(color = scheme.onSurfaceVariant, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    val centerX = widthPx / 2f
    val spanMillis = TimelineCoordinate.spanMillis(hours)
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

    Canvas(Modifier.fillMaxSize()) {
        if (!nowOnly) {
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
                drawText(measured, topLeft = Offset(12.dp.toPx(), y + 4.dp.toPx()))
            }
            day = day.plusDays(1)
            guard++
        }

        // Hour ticks computed in wall-clock minutes so labels match the ruler through DST.
        var tickLocal = Instant.ofEpochMilli(from.toLong()).atZone(zone)
        tickLocal = tickLocal.withSecond(0).withNano(0)
        val remainder = (tickLocal.hour * 60L + tickLocal.minute) % tickMinutes
        tickLocal = tickLocal.minusMinutes(remainder.toLong())
        var ticks = 0
        while (ticks < 400) {
            val tickMillis = tickLocal.toInstant().toEpochMilli()
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
                val ambiguous = zone.rules.getValidOffsets(tickLocal.toLocalDateTime()).size > 1
                val tickLabel = tickLocal.format(CLOCK_FORMAT) + if (ambiguous) "\n${tickLocal.offset}" else ""
                val measured = measurer.measure(tickLabel, tickStyle)
                if (y > measured.size.height / 2 && y < size.height - measured.size.height / 2) drawText(
                    measured,
                    topLeft = Offset(centerX - measured.size.width / 2f, y - measured.size.height / 2f)
                )
            }
            tickLocal = tickLocal.plusMinutes(tickMinutes)
            ticks++
        }

        }
        // NOW at its true position, never pinned to the centre.
        val nowY = TimelineCoordinate.yOf(nowMillis, anchor, heightPx, hours)
        if (nowOnly && nowY in 0f..size.height) {
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
    }
}
