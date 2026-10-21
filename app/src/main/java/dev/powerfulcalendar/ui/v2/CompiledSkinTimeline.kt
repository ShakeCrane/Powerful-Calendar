package dev.powerfulcalendar.ui.v2

import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import dev.powerfulcalendar.core.TemporalKind
import dev.powerfulcalendar.core.TemporalRecord
import dev.powerfulcalendar.core.TimelineCoordinate
import java.time.ZoneId

/**
 * Source-compiled visual extension, NOT a .pcskin runtime loader.
 *
 * Every callback is owned and validated by the host application. A compiled renderer may
 * replace the entire page's Compose structure, but cannot bypass store/draft authorization by
 * using this interface. A renderer is trusted application source, not untrusted user ZIP code.
 *
 * This first real adapter is TIMELINE only. Other pages still use the native screen until
 * their presentation contracts are lifted out of PowerCalApp.
 */
data class TimelineSkinContext(
    val records: List<TemporalRecord>,
    val camera: TimelineCoordinate.Camera,
    val nowMillis: Long,
    val zone: ZoneId,
    val pendingCount: Int,
    val aiLocked: Boolean,
    val memoCount: Int,
    val titlesOutside: Boolean,
    val blockedReminders: Int,
    val modifier: Modifier,
    val onCameraChange: (TimelineCoordinate.Camera) -> Unit,
    val onOpenEvent: (TemporalRecord) -> Unit,
    val onMoveEvent: (TemporalRecord, Long) -> Unit,
    val onCreate: (TemporalKind, Long) -> Unit,
    val onToday: () -> Unit,
    val onOpenDeck: () -> Unit,
    val onOpenTools: () -> Unit,
    val onOpenMonth: () -> Unit,
    val onOpenMemo: () -> Unit,
    val onTitlesOutsideChange: (Boolean) -> Unit
) {
    /**
     * Pixel-perfect baseline for visual comparison and partial overrides.
     * Existing recurrence, occurrence routing and time geometry stay authoritative.
     */
    @Composable
    fun Native() = TimelineScreen(
        records = records,
        camera = camera,
        nowMillis = nowMillis,
        zone = zone,
        pendingCount = pendingCount,
        aiLocked = aiLocked,
        onCameraChange = onCameraChange,
        onOpenEvent = onOpenEvent,
        onMoveEvent = onMoveEvent,
        onCreate = onCreate,
        onToday = onToday,
        onOpenDeck = onOpenDeck,
        onOpenTools = onOpenTools,
        onOpenMonth = onOpenMonth,
        onOpenMemo = onOpenMemo,
        memoCount = memoCount,
        titlesOutside = titlesOutside,
        onTitlesOutsideChange = onTitlesOutsideChange,
        modifier = modifier,
        blockedReminders = blockedReminders
    )

    /**
     * A new renderer MUST share this mapping and never give an activity a fake duration.
     * HeightPx is the actual content viewport, not the outer card height.
     */
    fun yOf(instantMillis: Long, heightPx: Float): Float =
        TimelineCoordinate.yOf(instantMillis, camera.anchorMillis, heightPx, camera.hours)
}

interface CompiledTimelineSkin {
    val rendererId: String

    /** Arbitrary Compose page layout and motion are allowed at this trusted source level. */
    @Composable
    fun Render(context: TimelineSkinContext)
}

/** Source-compiled renderers can be registered by app startup or a developer preview. */
object CompiledSkinRuntime {
    var timeline by mutableStateOf<CompiledTimelineSkin?>(null)
        private set

    fun registerTimeline(renderer: CompiledTimelineSkin) {
        require(renderer.rendererId.isNotBlank()) { "rendererId must not be blank" }
        timeline = renderer
    }

    fun restoreNativeTimeline() { timeline = null }
}

/**
 * Central adapter intentionally duplicates the old public TimelineScreen parameter names.
 * PowerCalApp only changes the entry point; all existing callbacks/authorizations are reused.
 */
@Composable
fun SkinnableTimelineScreen(
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
    onOpenMonth: () -> Unit = {},
    onOpenMemo: () -> Unit = {},
    memoCount: Int = 0,
    titlesOutside: Boolean = false,
    onTitlesOutsideChange: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier,
    blockedReminders: Int = 0
) {
    val context = TimelineSkinContext(
        records, camera, nowMillis, zone, pendingCount, aiLocked, memoCount,
        titlesOutside, blockedReminders, modifier, onCameraChange, onOpenEvent,
        onMoveEvent, onCreate, onToday, onOpenDeck, onOpenTools, onOpenMonth,
        onOpenMemo, onTitlesOutsideChange
    )
    val renderer = CompiledSkinRuntime.timeline
    if (RuntimeSkinSurfaces.enabled("timeline")) RuntimeSkinSurfaces.timeline(context)
    else if (renderer == null) context.Native() else renderer.Render(context)
}

/** Motion is style policy only, never a clock coordinate change. */
@Composable
fun skinMotion(durationMs: Int = 220): FiniteAnimationSpec<Float> =
    if (LocalGlassEffects.current.reduceMotion) snap()
    else tween(durationMillis = durationMs.coerceIn(0, 600))

/** An actual Compose Preview of the existing production timeline, not HTML approximation. */
@Preview(name = "Skin Studio • native timeline • light", showBackground = true, widthDp = 393, heightDp = 760)
@Composable
internal fun SkinStudioNativeLightPreview() {
    SkinStudioNativePreview(dark = false)
}

@Preview(name = "Skin Studio • native timeline • dark", showBackground = true, widthDp = 393, heightDp = 760)
@Composable
internal fun SkinStudioNativeDarkPreview() {
    SkinStudioNativePreview(dark = true)
}

@Composable
private fun SkinStudioNativePreview(dark: Boolean) {
    val zone = ZoneId.of("Asia/Shanghai")
    val noon = java.time.LocalDate.of(2026, 10, 21).atTime(12, 0).atZone(zone).toInstant().toEpochMilli()
    val alarm = TemporalRecord(1, 1, TemporalKind.ALARM, "起床提醒", startMillis = noon - 2 * 3_600_000)
    val activity = TemporalRecord(2, 1, TemporalKind.ACTIVITY, "组会准备",
        startMillis = noon - 3_600_000, endMillis = noon + 3_600_000)
    val deadline = TemporalRecord(3, 1, TemporalKind.DEADLINE, "提交报告",
        deadlineMillis = noon + 2 * 3_600_000)
    PowerCalTheme(darkTheme = dark) {
        SkinnableTimelineScreen(
            records = listOf(alarm, activity, deadline),
            camera = TimelineCoordinate.Camera(noon, 24.0),
            nowMillis = noon,
            zone = zone,
            pendingCount = 2,
            aiLocked = false,
            onCameraChange = {},
            onOpenEvent = {},
            onMoveEvent = { _, _ -> },
            onCreate = { _, _ -> },
            onToday = {},
            onOpenDeck = {},
            onOpenTools = {}
        )
    }
}
