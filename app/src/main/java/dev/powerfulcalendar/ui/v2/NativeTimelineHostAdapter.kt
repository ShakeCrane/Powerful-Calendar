package dev.powerfulcalendar.ui.v2

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import dev.powerfulcalendar.core.TemporalKind
import dev.powerfulcalendar.core.TemporalRecord
import dev.powerfulcalendar.core.TimelineCoordinate
import dev.powerfulcalendar.ui.v2.nativeui.NativeEventInspection
import dev.powerfulcalendar.ui.v2.nativeui.NativeTimelineScreen
import java.time.Instant
import java.time.ZoneId

/**
 * Host adapter retained for app routes and regression tests. Core records and identity stop here;
 * the renderer in :native-skin consumes NativeTimelineContext and returns opaque action keys.
 */
@Composable
fun TimelineScreen(
    records: List<TemporalRecord>, camera: TimelineCoordinate.Camera, nowMillis: Long, zone: ZoneId,
    pendingCount: Int, aiLocked: Boolean, onCameraChange: (TimelineCoordinate.Camera) -> Unit,
    onOpenEvent: (TemporalRecord) -> Unit, onMoveEvent: (TemporalRecord, Long) -> Unit,
    onCreate: (TemporalKind, Long) -> Unit, onToday: () -> Unit, onOpenDeck: () -> Unit,
    onOpenTools: () -> Unit, onOpenMonth: () -> Unit = {},
    onOpenMemo: () -> Unit = {}, memoCount: Int = 0,
    titlesOutside: Boolean = false, onTitlesOutsideChange: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier, blockedReminders: Int = 0,
    onOpenOccurrence: (TemporalRecord, String?) -> Unit = { record, _ -> onOpenEvent(record) }
) {
    val localDay = Instant.ofEpochMilli(camera.anchorMillis).atZone(zone).toLocalDate()
    val projection = remember(records, localDay, zone) {
        projectNativeTimeline(records, camera, zone)
    }
    val palette = LocalAxisPalette.current
    val kindColors = NativeTimelineKind.entries.associateWith { kind ->
        kindColor(when (kind) {
            NativeTimelineKind.ALARM -> TemporalKind.ALARM
            NativeTimelineKind.ACTIVITY -> TemporalKind.ACTIVITY
            NativeTimelineKind.DEADLINE -> TemporalKind.DEADLINE
            NativeTimelineKind.MEMO -> TemporalKind.MEMO
        }).toArgb()
    }
    val appearance = NativeTimelineAppearance(
        page = { content ->
            Box(
                Modifier.fillMaxSize()
                    .background(axisBackground())
                    .skinBackdrop(SkinSlot.TIMELINE_BACKGROUND)
            ) { content() }
        },
        showDialog = { dialog: NativeTimelineDialog ->
            NativeEventInspection(
                model = NativeEventInspectionModel(
                    title = dialog.title,
                    summary = dialog.summary,
                    notes = dialog.notes
                ),
                onDismiss = dialog.onDismissRequest,
                showDialog = { inspection ->
                    SkinAlertDialog(
                        onDismissRequest = inspection.onDismissRequest,
                        title = inspection.title,
                        text = inspection.text,
                        confirmButton = inspection.confirmButton
                    )
                },
                readOnlyMessage = "AI 正在处理卡组，暂时只可查看。",
                dismissButtonLabel = "完成"
            )
        },
        image = { asset, sizeDp ->
            SkinImage(timelineSkinSlot(asset), Modifier.size(sizeDp.dp))
        },
        hasImage = { asset -> skinPainter(timelineSkinSlot(asset)) != null },
        axis = NativeTimelineAxisColors(
            alarm = palette.alarm.toArgb(),
            activity = palette.activity.toArgb(),
            deadline = palette.deadline.toArgb(),
            nowLine = palette.nowLine.toArgb(),
            gridLine = palette.gridLine.toArgb(),
            glassFill = palette.glassFill.toArgb(),
            glassBorder = palette.glassBorder.toArgb(),
            glassHighlight = palette.glassHighlight.toArgb()
        ),
        kindColors = kindColors,
        pointLineDp = LocalSkinVisual.current.pointLineDp,
        chipCornerRadiusDp = 14f
    )
    val state = NativeTimelineState(
        events = projection.events,
        camera = NativeTimelineCamera(camera.anchorMillis, camera.hours),
        nowMillis = nowMillis,
        zone = zone,
        pendingCount = pendingCount,
        aiLocked = aiLocked,
        memoCount = memoCount,
        titlesOutside = titlesOutside,
        blockedReminders = blockedReminders,
        hasRecords = projection.hasRecords,
        hasDateOnlyDeadlines = projection.hasDateOnlyDeadlines
    )
    val actions = NativeTimelineActions(
        onCameraChange = { updated ->
            onCameraChange(TimelineCoordinate.Camera(updated.anchorMillis, updated.hours))
        },
        onOpenEvent = { rowKey ->
            projection.openTargets[rowKey]?.let { target ->
                onOpenOccurrence(target.owner, target.occurrenceKey)
            }
        },
        onMoveEvent = { rowKey, targetMillis ->
            projection.openTargets[rowKey]?.let { target -> onMoveEvent(target.owner, targetMillis) }
        },
        onCreate = { kind, atMillis -> onCreate(TemporalKind.valueOf(kind.name), atMillis) },
        onToday = onToday,
        onOpenDeck = onOpenDeck,
        onOpenTools = onOpenTools,
        onOpenMonth = onOpenMonth,
        onOpenMemo = onOpenMemo,
        onTitlesOutsideChange = onTitlesOutsideChange
    )
    NativeTimelineScreen(
        context = NativeTimelineContext(
            state = state,
            geometry = CoreNativeTimelineGeometry,
            actions = actions,
            appearance = appearance
        ),
        modifier = modifier
    )
}

private fun timelineSkinSlot(asset: NativeTimelineAsset): SkinSlot = when (asset) {
    NativeTimelineAsset.BRAND_MARK -> SkinSlot.BRAND_MARK
    NativeTimelineAsset.ALARM_MARKER -> SkinSlot.ALARM_MARKER
    NativeTimelineAsset.DEADLINE_MARKER -> SkinSlot.DEADLINE_MARKER
}
