package dev.powerfulcalendar.ui.v2

import androidx.compose.runtime.Composable
import java.time.ZoneId

enum class NativeTimelineKind(val label: String) { ALARM("闹钟"), ACTIVITY("活动"), DEADLINE("截止"), MEMO("备忘") }
enum class NativeTimelineSide { LEFT, RIGHT }
enum class NativeTimelineAsset { BRAND_MARK, ALARM_MARKER, DEADLINE_MARKER }

data class NativeTimelineCamera(val anchorMillis: Long, val hours: Double)

/** One already-projected event occurrence and lane. It contains display values only. */
data class NativeTimelineEvent(
    val rowKey: String,
    val eventSeq: Long,
    val revision: Long,
    val kind: NativeTimelineKind,
    val kindLabel: String,
    val title: String,
    val notes: String,
    val summary: String,
    val sequenceLabel: String,
    val anchorMillis: Long,
    val endMillis: Long?,
    val deadlineDate: String?,
    val zoneId: String,
    val dateOnlyDeadline: Boolean,
    val lane: Int,
    val side: NativeTimelineSide
) {
    val stableId: String get() = "$eventSeq:$rowKey"
}

data class NativeTimelineTick(val instantMillis: Long, val text: String)

/** Host adapter keeps the frozen Core coordinate implementation authoritative. */
interface NativeTimelineGeometry {
    val minimumHours: Double
    val maximumHours: Double
    fun spanMillis(hours: Double): Double
    fun clampHours(hours: Double): Double
    fun yOf(instantMillis: Long, anchorMillis: Long, heightPx: Float, hours: Double): Float
    fun snap(instantMillis: Long, hours: Double, zone: ZoneId): Long
    fun transformedCamera(
        camera: NativeTimelineCamera,
        oldY: Float,
        newY: Float,
        zoom: Float,
        heightPx: Float
    ): NativeTimelineCamera
}

data class NativeTimelineAxisColors(
    val alarm: Int,
    val activity: Int,
    val deadline: Int,
    val nowLine: Int,
    val gridLine: Int,
    val glassFill: Int,
    val glassBorder: Int,
    val glassHighlight: Int
)

data class NativeTimelineDialog(
    val onDismissRequest: () -> Unit,
    val title: String,
    val summary: String,
    val notes: String
)

typealias NativeTimelinePageSlot = @Composable (
    @Composable () -> Unit
) -> Unit
typealias NativeTimelineDialogSlot = @Composable (NativeTimelineDialog) -> Unit
typealias NativeTimelineImageSlot = @Composable (NativeTimelineAsset, Float) -> Unit
typealias NativeTimelineHasImage = @Composable (NativeTimelineAsset) -> Boolean

/** Skin-aware visuals supplied by the Host; values do not expose Skin/Android services. */
data class NativeTimelineAppearance(
    val page: NativeTimelinePageSlot,
    val showDialog: NativeTimelineDialogSlot,
    val image: NativeTimelineImageSlot,
    val hasImage: NativeTimelineHasImage,
    val axis: NativeTimelineAxisColors,
    val kindColors: Map<NativeTimelineKind, Int>,
    val pointLineDp: Float,
    val chipCornerRadiusDp: Float = 14f
)

data class NativeTimelineState(
    val events: List<NativeTimelineEvent>,
    val camera: NativeTimelineCamera,
    val nowMillis: Long,
    val zone: ZoneId,
    val pendingCount: Int,
    val aiLocked: Boolean,
    val memoCount: Int,
    val titlesOutside: Boolean,
    val blockedReminders: Int,
    val hasRecords: Boolean,
    val hasDateOnlyDeadlines: Boolean
)

data class NativeTimelineActions(
    val onCameraChange: (NativeTimelineCamera) -> Unit,
    val onOpenEvent: (String) -> Unit,
    val onMoveEvent: (String, Long) -> Unit,
    val onCreate: (NativeTimelineKind, Long) -> Unit,
    val onToday: () -> Unit,
    val onOpenDeck: () -> Unit,
    val onOpenTools: () -> Unit,
    val onOpenMonth: () -> Unit,
    val onOpenMemo: () -> Unit,
    val onTitlesOutsideChange: (Boolean) -> Unit
)

data class NativeTimelineContext(
    val state: NativeTimelineState,
    val geometry: NativeTimelineGeometry,
    val actions: NativeTimelineActions,
    val appearance: NativeTimelineAppearance
)
