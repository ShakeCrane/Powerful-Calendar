package dev.powerfulcalendar.ui.v2

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.Color
import androidx.test.core.app.ApplicationProvider
import dev.powerfulcalendar.core.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.*

/** Simulated Compose semantics/layout/gesture checks, never a physical-device performance test. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w360dp-h800dp-xhdpi")
class GlassComposeTest {
    @get:Rule val ui = createComposeRule()
    private lateinit var store: PowerStore
    private val zone = ZoneId.of("Asia/Shanghai")
    private val noon = LocalDate.of(2026, 10, 20).atTime(12, 0).atZone(zone).toInstant().toEpochMilli()

    @Before fun openStore() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.deleteDatabase(PowerStore.DATABASE_NAME)
        store = PowerStore(context)
    }
    @After fun closeStore() { store.close() }

    /**
     * DSH-07 commands are suspend and run on the controller's dispatcher. Tests use an unconfined
     * dispatcher so a `runBlocking { open(...) }` executes inline on the test thread and cannot
     * depend on a background thread that Robolectric does not pump.
     */
    private fun newController(persistence: DeckPersistence): DeckController =
        DeckController(persistence, ioDispatcher = kotlinx.coroutines.Dispatchers.Unconfined)

    private fun DeckController.openNow(draftId: Long?) = kotlinx.coroutines.runBlocking { open(draftId) }

    @Test fun dateOnlyDeadlineCanBeOpenedFromNoonAtFourHourZoom() {
        val record = TemporalRecord(42, 1, TemporalKind.DEADLINE, "提交设计稿", deadlineDate = "2026-10-20", zoneId = zone.id)
        var opened: TemporalRecord? = null
        ui.setContent { PowerCalTheme {
            TimelineScreen(listOf(record), TimelineCoordinate.Camera(noon, 4.0), noon, zone, 0, false,
                {}, { opened = it }, { _, _ -> }, { _, _ -> }, {}, {}, {}, {})
        } }
        ui.onNodeWithText("提交设计稿").assertIsDisplayed().performClick()
        ui.runOnIdle { assertEquals(record, opened) }
        ui.onNodeWithContentDescription("今日 · 复位为全天").assertIsDisplayed()
    }

    @Test fun tenOverlappingEventsProvideHorizontallyScrollableWing() {
        val events = (1..10).map { index ->
            TemporalRecord(index.toLong(), 1, TemporalKind.ACTIVITY, "Overlap $index",
                startMillis = noon + 6 * 3_600_000L,
                endMillis = noon + 10 * 3_600_000L,
                zoneId = zone.id)
        }
        ui.setContent { PowerCalTheme {
            TimelineScreen(events, TimelineCoordinate.Camera(noon + 8 * 3_600_000L, 4.4),
                noon, zone, 0, false, {}, {}, { _, _ -> }, { _, _ -> },
                {}, {}, {}, {})
        } }
        val wing = ui.onNodeWithTag("timeline-right-lanes")
        wing.assertExists()
        wing.assert(hasScrollAction())
        val before = wing.fetchSemanticsNode()
            .config[androidx.compose.ui.semantics.SemanticsProperties.HorizontalScrollAxisRange]
            .value()
        wing.performTouchInput { swipeLeft() }
        ui.waitForIdle()
        val after = wing.fetchSemanticsNode()
            .config[androidx.compose.ui.semantics.SemanticsProperties.HorizontalScrollAxisRange]
            .value()
        assertTrue("Horizontal swipe must expose more columns: $before -> $after",
            after > before)
        // Scrolling changes only the wing; the central time viewport stays on-screen.
        ui.onNodeWithTag("timeline-viewport").assertExists()
    }

    @Test fun aiLockKeepsTimelineReadableButDisablesNewEvent() {
        ui.setContent { PowerCalTheme {
            TimelineScreen(emptyList(), TimelineCoordinate.Camera(noon, 24.0), noon, zone, 100, true,
                {}, {}, { _, _ -> }, { _, _ -> }, {}, {}, {}, {})
        } }
        ui.onNodeWithContentDescription("新建事件").assertIsNotEnabled()
        ui.onNodeWithContentDescription("待处理 100").assertIsEnabled()
        ui.onNodeWithText("AI 处理中").assertIsDisplayed()
    }

    @Test fun hundredCardDeckFlushesLastCharacterBeforeNavigationAndKeepsStableIdentity() {
        val cards = (1..100).map { store.newDraft(DraftForm.fresh(TemporalKind.ACTIVITY, LocalDate.of(2026, 10, 20), zone).copy(title = "草稿 $it")) }
        val controller = newController(StoreDeckPersistence(store))
        controller.openNow(cards.first().draftId)
        ui.setContent { PowerCalTheme {
            DraftDeckDialog(controller, null, {}, {}, {}, {}, { emptyList() })
        } }
        ui.onNodeWithText("轻点卡片，展开完整编辑").performClick()
        ui.onNode(hasSetTextAction() and hasText("标题")).performTextReplacement("最后一个字：好")
        ui.onNodeWithContentDescription("下一张草稿").performClick()
        ui.waitUntil(10_000) { controller.state.currentId == cards[1].draftId }
        assertEquals("最后一个字：好", store.draft(cards[0].draftId)?.form?.title)
        ui.onNodeWithText("草稿 2").assertIsDisplayed()
        ui.onNodeWithText("草稿 100").assertDoesNotExist()
        ui.onNodeWithText("2 / 100 · 已暂存").assertIsDisplayed()
    }

    @Test fun explicitNativeSkinKeepsDeckOnItsSingleObserverPath() {
        val cards = (1..2).map { index ->
            store.newDraft(DraftForm.fresh(TemporalKind.ACTIVITY,
                LocalDate.of(2026, 10, 20), zone).copy(title = "Native 分离草稿 $index"))
        }
        val controller = newController(StoreDeckPersistence(store)).also { it.openNow(cards.first().draftId) }
        ActiveVisualSkin.install()
        try {
            ui.setContent { PowerCalTheme {
                DraftDeckDialog(controller, null, {}, {}, {}, {}, { emptyList() })
            } }
            ui.onNodeWithText("Native 分离草稿 1").assertIsDisplayed()
            ui.onNodeWithContentDescription("下一张草稿").performClick()
            ui.waitUntil(10_000) { controller.state.currentId == cards[1].draftId }
            ui.onNodeWithText("Native 分离草稿 2").assertIsDisplayed()
        } finally {
            PresentationSkinRegistry.reset()
        }
    }

    @Test fun failedFlushBlocksTheDialogCloseAndRetainsVisibleText() {
        val card = store.newDraft(DraftForm.fresh(TemporalKind.ACTIVITY, LocalDate.of(2026, 10, 20), zone).copy(title = "原始"))
        val persistence = object : DeckPersistence by StoreDeckPersistence(store) {
            override fun stage(draftId: Long, form: DraftForm, expectedVersion: Long): PowerStore.StageResult = PowerStore.StageResult.Locked
        }
        val controller = newController(persistence)
        controller.openNow(card.draftId)
        controller.edit(controller.state.form!!.copy(title = "保留我的编辑"))
        var closed = false
        ui.setContent { PowerCalTheme { DraftDeckDialog(controller, null, { closed = true }, {}, {}, {}, { emptyList() }) } }
        ui.onNodeWithContentDescription("保存并退出待处理").performClick()
        ui.waitForIdle()
        ui.runOnIdle { assertFalse(closed); assertEquals("保留我的编辑", controller.state.form?.title) }
        ui.onNodeWithText("保留我的编辑").assertIsDisplayed()
        ui.runOnUiThread {
            (org.robolectric.shadows.ShadowDialog.getLatestDialog() as androidx.activity.ComponentDialog).onBackPressedDispatcher.onBackPressed()
        }
        ui.waitForIdle()
        ui.runOnIdle { assertFalse(closed); assertTrue(controller.state.dirty) }
    }

    @Test fun darkModeChangesWhileExpandedWithoutLosingDraftText() {
        val card = store.newDraft(DraftForm.fresh(TemporalKind.ACTIVITY, LocalDate.of(2026, 10, 20), zone).copy(title = "切换前"))
        val controller = newController(StoreDeckPersistence(store)).also { it.openNow(card.draftId) }
        val dark = mutableStateOf(false)
        ui.setContent { PowerCalTheme(darkTheme = dark.value) { DraftDeckDialog(controller, null, {}, {}, {}, {}, { emptyList() }) } }
        ui.onNodeWithText("轻点卡片，展开完整编辑").performClick()
        ui.onNode(hasSetTextAction() and hasText("标题")).performTextReplacement("切换中保留")
        ui.runOnIdle { dark.value = true }
        ui.onNode(hasSetTextAction() and hasText("标题")).assertTextContains("切换中保留").assertIsFocused()
        ui.runOnIdle { assertEquals(card.draftId, controller.state.currentId) }
    }

    @Test fun landscapeLargeFontMonthStillOffersAReadableDayList() {
        ui.setContent { PowerCalTheme {
            CompositionLocalProvider(androidx.compose.ui.platform.LocalDensity provides androidx.compose.ui.unit.Density(1f, 2f)) {
                Box(Modifier.requiredSize(640.dp, 360.dp)) {
                    MonthScreen(emptyList(), LocalDate.of(2026, 10, 20), zone, false, {}, {}, { _, _ -> }, {})
                }
            }
        } }
        ui.onNodeWithText("日历").assertIsDisplayed()
        ui.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("在时间轴查看这一天"))
        ui.onNodeWithText("在时间轴查看这一天").assertIsDisplayed()
    }

    @Test fun cardSwipeIsOneStepAndTextFieldAndVerticalDragsKeepTheirCard() {
        val cards = (1..4).map { store.newDraft(DraftForm.fresh(TemporalKind.ACTIVITY, LocalDate.of(2026, 10, 20), zone).copy(title = "手势草稿 $it")) }
        val controller = newController(StoreDeckPersistence(store)).also { it.openNow(cards.first().draftId) }
        ui.setContent { PowerCalTheme { DraftDeckDialog(controller, null, {}, {}, {}, {}, { emptyList() }) } }
        ui.onNodeWithTag("draft-card").performTouchInput { swipeLeft() }
        ui.waitUntil(10_000) { controller.state.currentId == cards[1].draftId }
        ui.onNodeWithText("轻点卡片，展开完整编辑").performClick()
        ui.onNode(hasSetTextAction() and hasText("标题")).performTouchInput { swipeLeft() }
        ui.runOnIdle { assertEquals(cards[1].draftId, controller.state.currentId) }
        ui.onNodeWithTag("draft-card").performTouchInput { swipeUp() }
        ui.runOnIdle { assertEquals(cards[1].draftId, controller.state.currentId) }
    }

    @Test fun outsideVerticalDragDoesNotDismissAndHorizontalFlingCanBrowseMany() {
        val cards = (1..20).map { store.newDraft(DraftForm.fresh(TemporalKind.ACTIVITY, LocalDate.of(2026, 10, 20), zone).copy(title = "外侧 $it")) }
        val controller = newController(StoreDeckPersistence(store)).also { it.openNow(cards.first().draftId) }
        var dismissed = false
        ui.setContent { PowerCalTheme { DraftDeckDialog(controller, null, { dismissed = true }, {}, {}, {}, { emptyList() }) } }
        ui.onNodeWithTag("deck-backdrop").performTouchInput {
            swipe(androidx.compose.ui.geometry.Offset(2f, height * 0.3f), androidx.compose.ui.geometry.Offset(2f, height * 0.7f), 250)
        }
        ui.runOnIdle { assertFalse(dismissed); assertEquals(cards.first().draftId, controller.state.currentId) }
        ui.onNodeWithTag("deck-backdrop").performTouchInput {
            swipe(androidx.compose.ui.geometry.Offset(width * 0.9f, height - 2f), androidx.compose.ui.geometry.Offset(width * 0.1f, height - 2f), 120)
        }
        ui.waitUntil(10_000) { controller.state.position > 1 }
        ui.runOnIdle { assertFalse(dismissed) }
    }

    @Test fun finalDiscardShowsDurableCountsAndUndoReturnsTheSameIdentity() {
        val card = store.newDraft(DraftForm.fresh(TemporalKind.ACTIVITY, LocalDate.of(2026, 10, 20), zone).copy(title = "最后一张"))
        val controller = newController(StoreDeckPersistence(store)).also { it.openNow(card.draftId) }
        ui.setContent { PowerCalTheme { DraftDeckDialog(controller, null, {}, {}, {}, {}, { emptyList() }) } }
        ui.onNodeWithText("丢弃").performClick()
        ui.waitUntil(10_000) { controller.state.completion != null }
        ui.onNodeWithText("待处理已清空").assertIsDisplayed()
        ui.onNodeWithText("已提交 0 项 · 已丢弃 1 项").assertIsDisplayed()
        ui.onNodeWithText("撤销丢弃").performClick()
        ui.waitUntil(10_000) { controller.state.currentId == card.draftId && controller.state.completion == null }
        ui.onNodeWithText("最后一张").assertIsDisplayed()
    }

    @Test fun timelinePinchThenSingleFingerFlingKeepsZoomBoundedAndCanBeInterrupted() {
        val camera = mutableStateOf(TimelineCoordinate.Camera(noon, 24.0))
        ui.setContent { PowerCalTheme {
            TimelineScreen(emptyList(), camera.value, noon, zone, 0, false, { camera.value = it }, {}, { _, _ -> }, { _, _ -> },
                { camera.value = camera.value.copy(anchorMillis = noon) }, {}, {}, {})
        } }
        ui.onNodeWithTag("timeline-viewport").performTouchInput {
            val y = height / 2f
            down(0, androidx.compose.ui.geometry.Offset(width / 2f, y - 40f))
            down(1, androidx.compose.ui.geometry.Offset(width / 2f, y + 40f))
            moveBy(0, androidx.compose.ui.geometry.Offset(0f, -80f))
            moveBy(1, androidx.compose.ui.geometry.Offset(0f, 80f))
            up(0); up(1)
        }
        ui.runOnIdle { assertTrue(camera.value.hours in 4.0..48.0); assertTrue(camera.value.hours < 24.0) }
        ui.onNodeWithTag("timeline-viewport").performTouchInput { swipeUp() }
        ui.onNodeWithContentDescription("今日 · 复位为全天").performClick()
        ui.mainClock.advanceTimeBy(2000)
        ui.runOnIdle { assertEquals(noon, camera.value.anchorMillis) }
    }

    @Test fun bothThemesMeetTextContrastOnTheirActualGlassFill() {
        val dark = mutableStateOf(false)
        var ratios: List<Float> = emptyList()
        ui.setContent { PowerCalTheme(darkTheme = dark.value) {
            val palette = LocalAxisPalette.current
            val scheme = MaterialTheme.colorScheme
            val background = palette.glassFill.compositeOver(scheme.background).luminance()
            ratios = listOf(androidx.compose.material3.LocalContentColor.current, scheme.onSurface, scheme.onSurfaceVariant, palette.alarm, palette.activity, palette.deadline).map {
                val foreground = it.luminance()
                (maxOf(foreground, background) + 0.05f) / (minOf(foreground, background) + 0.05f)
            }
        } }
        ui.runOnIdle { println("Light glass text contrast: $ratios"); assertTrue("Light: $ratios", ratios.all { it >= 4.5f }); dark.value = true }
        ui.runOnIdle { println("Dark glass text contrast: $ratios"); assertTrue("Dark: $ratios", ratios.all { it >= 4.5f }) }
    }

    @Test fun disabledGlassKeepsOpaqueNativeSurfaceReadableInLightAndDarkThemes() {
        val dark = mutableStateOf(false)
        data class FallbackColors(
            val blur: Boolean,
            val surface: Color,
            val onSurface: Color,
            val fill: Color,
            val highlight: Color
        )
        var observed: FallbackColors? = null

        ui.setContent {
            PowerCalTheme(darkTheme = dark.value, effects = GlassEffects(blur = false)) {
                val scheme = MaterialTheme.colorScheme
                val palette = LocalAxisPalette.current
                observed = FallbackColors(
                    blur = LocalGlassEffects.current.blur,
                    surface = scheme.surface,
                    onSurface = scheme.onSurface,
                    fill = palette.glassFill,
                    highlight = palette.glassHighlight
                )
                GlassPanel(Modifier.testTag("native-glass-fallback")) {
                    androidx.compose.material3.Text("可读的原生表面")
                }
            }
        }

        fun assertReadableFallback() {
            val actual = requireNotNull(observed)
            assertFalse(actual.blur)
            assertEquals(actual.surface, actual.fill)
            assertEquals(Color.Transparent, actual.highlight)
            val foreground = actual.onSurface.luminance()
            val background = actual.surface.luminance()
            val contrast = (maxOf(foreground, background) + 0.05f) /
                (minOf(foreground, background) + 0.05f)
            assertTrue("native fallback contrast=$contrast", contrast >= 4.5f)
            ui.onNodeWithText("可读的原生表面").assertIsDisplayed()
            ui.onNodeWithTag("native-glass-fallback").assertIsDisplayed()
        }

        ui.runOnIdle {
            assertReadableFallback()
            dark.value = true
        }
        ui.waitForIdle()
        ui.runOnIdle { assertReadableFallback() }
    }

    @Test fun reducedMotionSettingIsObservedWithoutRecreatingTheHost() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        android.provider.Settings.Global.putFloat(context.contentResolver, android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
        var effects: GlassEffects? = null
        ui.setContent { effects = rememberSystemGlassEffects() }
        ui.runOnIdle { assertFalse(effects!!.reduceMotion)
            android.provider.Settings.Global.putFloat(context.contentResolver, android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
            // Robolectric's in-memory Settings shadow doesn't emit the provider notification.
            context.contentResolver.notifyChange(android.provider.Settings.Global.getUriFor(android.provider.Settings.Global.ANIMATOR_DURATION_SCALE), null)
        }
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
        ui.waitUntil(5_000) { effects?.reduceMotion == true }
        ui.runOnIdle { assertFalse(effects!!.blur) }
    }

    @Test fun conflictReviewShowsRecurrenceAndCommitsOnlyAfterExplicitFullReplacement() {
        val first = store.newDraft(DraftForm.fresh(TemporalKind.ACTIVITY,
            LocalDate.of(2026, 10, 20), zone).copy(title = "First", trackingEnabled = true))
        store.commit(first.draftId)
        val current = store.get(first.eventSeq)!!
        val stale = store.newDraft(DraftForm.from(current).copy(
            recurrence = Recurrence(RecurrenceFreq.WEEKLY)), first.eventSeq,
            DraftOperation.REPLACE)
        assertTrue(store.setCompleted(first.eventSeq, "single", true,
            expectedRevision = current.revision))
        val controller = newController(StoreDeckPersistence(store)).also {
            it.openNow(stale.draftId)
            kotlinx.coroutines.runBlocking { it.commit() }
        }
        ui.setContent { PowerCalTheme { DraftDeckDialog(controller, null, {}, {}, {}, {}, { emptyList() }) } }
        ui.onNodeWithText("重复频率 · 将更改").performScrollTo().assertIsDisplayed()
        ui.runOnIdle { assertEquals("First", store.get(first.eventSeq)?.title) }
        ui.onNodeWithText("确认整份覆盖").performClick()
        ui.waitUntil(10_000) { controller.state.completion != null }
        assertEquals("First", store.get(first.eventSeq)?.title)
        assertEquals(3L, store.get(first.eventSeq)?.revision)
    }

    @Test
    @Config(sdk = [35], qualifiers = "w320dp-h480dp-mdpi")
    fun smallScreenDeckHasFullSizedNavigationTargetsAndReachablePublish() {
        val card = store.newDraft(DraftForm.fresh(TemporalKind.ACTIVITY, LocalDate.of(2026, 10, 20), zone).copy(title = "小屏编辑"))
        val controller = newController(StoreDeckPersistence(store)).also { it.openNow(card.draftId) }
        ui.setContent { PowerCalTheme { DraftDeckDialog(controller, null, {}, {}, {}, {}, { emptyList() }) } }
        ui.onNodeWithText("轻点卡片，展开完整编辑").performClick()
        ui.onNodeWithText("发布").assertIsDisplayed()
        val bounds = ui.onNodeWithContentDescription("下一张草稿").fetchSemanticsNode().boundsInRoot
        assertTrue("Navigation target: $bounds", bounds.width >= 48f && bounds.height >= 48f)
    }

    @Test fun monthGridHasSevenDistinctColumnsWithFullDateTouchTargets() {
        ui.setContent { PowerCalTheme { MonthScreen(emptyList(), LocalDate.of(2026, 10, 20), zone, false, {}, {}, { _, _ -> }, {}) } }
        val weekdays = listOf("一", "二", "三", "四", "五", "六", "日").map { ui.onNodeWithText(it).fetchSemanticsNode().boundsInRoot }
        assertEquals(7, weekdays.map { it.left }.distinct().size)
        val date = ui.onNode(hasContentDescription("2026-10-20，0 项", substring = true)).fetchSemanticsNode().boundsInRoot
        val minimum = with(ui.density) { 48.dp.toPx() }
        assertTrue("Date target: $date", date.width >= minimum && date.height >= minimum)
    }

    @Test fun selectingNativeMonthDateUpdatesAgendaAndDateActionsWithoutLeavingMonth() {
        val selectedDate = LocalDate.of(2026, 10, 23)
        var created: LocalDate? = null
        var timelineDate: LocalDate? = null
        ui.setContent {
            PowerCalTheme {
                MonthScreen(emptyList(), LocalDate.of(2026, 10, 20), zone, false,
                    {}, { timelineDate = it }, { _, _ -> }, { created = it })
            }
        }
        ui.onNodeWithContentDescription("2026-10-23，0 项").performClick()
        ui.onNodeWithContentDescription("2026-10-23，0 项").assertIsSelected()
        ui.runOnIdle {
            assertNull("selecting a grid date must not jump to the timeline", timelineDate)
        }
        ui.onNodeWithText("为这一天新建活动").performClick()
        ui.runOnIdle { assertEquals(selectedDate, created) }
        ui.onNodeWithText("在时间轴查看这一天").performClick()
        ui.runOnIdle { assertEquals(selectedDate, timelineDate) }
    }
}
