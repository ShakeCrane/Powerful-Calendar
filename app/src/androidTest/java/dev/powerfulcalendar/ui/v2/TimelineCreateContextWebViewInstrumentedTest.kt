package dev.powerfulcalendar.ui.v2

import android.content.Context
import android.content.ContextWrapper
import android.database.DatabaseErrorHandler
import android.database.sqlite.SQLiteDatabase
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.powerfulcalendar.core.DraftForm
import dev.powerfulcalendar.core.PowerStore
import dev.powerfulcalendar.core.TemporalKind
import dev.powerfulcalendar.core.TimelineCoordinate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

@RunWith(AndroidJUnit4::class)
class TimelineCreateContextWebViewInstrumentedTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private class IsolatedAppContext(
        target: Context,
        private val storage: Context,
        private val namespace: String
    ) : ContextWrapper(target) {
        private val root = File(storage.cacheDir, namespace).apply {
            check(mkdirs() || isDirectory)
        }

        private fun databaseAlias(name: String) = "$namespace-$name"
        private fun isolatedDatabasePath(name: String) = storage.getDatabasePath(databaseAlias(name)).also {
            check(it.parentFile?.mkdirs() == true || it.parentFile?.isDirectory == true)
        }

        override fun getApplicationContext(): Context = this
        override fun getSharedPreferences(name: String, mode: Int) =
            storage.getSharedPreferences("$namespace-$name", mode)
        override fun getFilesDir(): File = root
        override fun getCacheDir(): File = File(root, "cache").apply { mkdirs() }
        override fun getNoBackupFilesDir(): File = File(root, "no_backup").apply { mkdirs() }
        override fun getDir(name: String, mode: Int): File = File(root, "dirs/$name").apply { mkdirs() }
        override fun getDatabasePath(name: String): File = isolatedDatabasePath(name)
        override fun openOrCreateDatabase(
            name: String,
            mode: Int,
            factory: SQLiteDatabase.CursorFactory?
        ): SQLiteDatabase {
            isolatedDatabasePath(name)
            return storage.openOrCreateDatabase(databaseAlias(name), mode, factory)
        }
        override fun openOrCreateDatabase(
            name: String,
            mode: Int,
            factory: SQLiteDatabase.CursorFactory?,
            errorHandler: DatabaseErrorHandler?
        ): SQLiteDatabase {
            isolatedDatabasePath(name)
            return storage.openOrCreateDatabase(databaseAlias(name), mode, factory, errorHandler)
        }
        override fun deleteDatabase(name: String): Boolean = storage.deleteDatabase(databaseAlias(name))
    }

    private fun findWebView(view: View): WebView? {
        if (view is WebView) return view
        if (view is ViewGroup) for (i in 0 until view.childCount) {
            findWebView(view.getChildAt(i))?.let { return it }
        }
        return null
    }

    private fun evaluate(web: WebView, expression: String): String {
        val value = AtomicReference<String>()
        val done = CountDownLatch(1)
        compose.runOnUiThread {
            web.evaluateJavascript(expression) { reply -> value.set(reply); done.countDown() }
        }
        assertTrue("WebView JavaScript response timed out", done.await(10, TimeUnit.SECONDS))
        return value.get()
    }

    private fun awaitJs(web: WebView, condition: String) {
        compose.waitUntil(30_000) { evaluate(web, condition) == "true" }
    }

    @Test
    fun timelineChooserUsesTrustedCameraForAllKindsAndClosingDoesNotWrite() {
        val storage = compose.activity.applicationContext
        val namespace = "pc-timeline-create-${UUID.randomUUID()}"
        val isolated = IsolatedAppContext(compose.activity, storage, namespace)
        val databaseName = "timeline.db"
        val store = PowerStore(isolated, databaseName)
        val skinManager = SkinPackageManager(isolated)
        try {
            skinManager.select("powercal-clear-default")
            isolated.getSharedPreferences("powercal-onboarding", Context.MODE_PRIVATE)
                .edit().putBoolean("system-calendar-import-asked", true).commit()
            compose.runOnUiThread {
                compose.activity.setContent {
                    CompositionLocalProvider(LocalContext provides isolated) {
                        PowerCalTheme { PowerCalApp(store) }
                    }
                }
            }

            var web: WebView? = null
            compose.waitUntil(30_000) {
                compose.runOnUiThread {
                    web = findWebView(compose.activity.findViewById(android.R.id.content))
                }
                web != null
            }
            val view = requireNotNull(web)
            awaitJs(view, "!!document.querySelector('[data-action=\\\"createDraft\\\"]')")

            var cameraValues: Triple<Long, Double, String>? = null
            compose.runOnIdle {
                val timeline = WebSkinBridgeRuntime.state().getJSONObject("timeline")
                cameraValues = Triple(
                    timeline.getLong("anchorMillis"), timeline.getDouble("hours"),
                    timeline.getString("zoneId")
                )
            }
            val (anchorMillis, hours, zoneId) = requireNotNull(cameraValues)
            val zone = ZoneId.of(zoneId)
            val expectedStart = TimelineCoordinate.snap(anchorMillis, hours, zone)
            val countsBefore = listOf(
                store.draftCount(), store.pendingCount(), store.identityCount(),
                store.commitCount(), store.nextSequencePreview(), store.nextQueueOrderPreview(),
                store.all().size.toLong()
            )

            for (kind in TemporalKind.entries) {
                evaluate(view, "document.querySelector('[data-action=\\\"createDraft\\\"]').click(); true")
                awaitJs(view, "!!document.querySelector('#timelineCreateDialog[open]')")
                assertEquals(
                    "true",
                    evaluate(view, "!!document.querySelector('[data-timeline-create-kind=\\\"$kind\\\"]')")
                )
                evaluate(view, "document.querySelector('[data-timeline-create-kind=\\\"$kind\\\"]').click(); true")
                awaitJs(view, "!document.querySelector('#skinSheet').hidden && !!document.querySelector('#sheetFields [data-field=title]')")

                var formJson: org.json.JSONObject? = null
                compose.runOnIdle {
                    formJson = WebSkinBridgeRuntime.state().getJSONObject("editing")
                        .getJSONObject("deck").getJSONObject("form")
                }
                val form = requireNotNull(formJson)
                assertEquals(kind.name, form.getString("kind"))
                assertEquals(zone.id, form.getString("zoneId"))
                when (kind) {
                    TemporalKind.ALARM -> assertEquals(
                        DraftForm.formatInstant(expectedStart, zone), form.getString("startText")
                    )
                    TemporalKind.ACTIVITY -> {
                        assertEquals(DraftForm.formatInstant(expectedStart, zone), form.getString("startText"))
                        assertEquals(DraftForm.formatInstant(expectedStart + 3_600_000L, zone), form.getString("endText"))
                    }
                    TemporalKind.DEADLINE -> assertEquals(
                        DraftForm.formatInstant(expectedStart, zone), form.getString("endText")
                    )
                    TemporalKind.MEMO -> assertEquals(
                        Instant.ofEpochMilli(expectedStart).atZone(zone).toLocalDate().toString(),
                        form.getString("startText")
                    )
                }

                evaluate(view, "document.querySelector('#sheetClose').click(); true")
                awaitJs(view, "document.querySelector('#skinSheet').hidden")
                compose.waitUntil(10_000) {
                    var deckOpen = true
                    compose.runOnUiThread {
                        deckOpen = WebSkinBridgeRuntime.state().getJSONObject("editing")
                            .getJSONObject("deck").optBoolean("open", false)
                    }
                    !deckOpen
                }
                val countsAfter = listOf(
                    store.draftCount(), store.pendingCount(), store.identityCount(),
                    store.commitCount(), store.nextSequencePreview(), store.nextQueueOrderPreview(),
                    store.all().size.toLong()
                )
                assertEquals("closing $kind must remain transient", countsBefore, countsAfter)
            }
        } finally {
            compose.runOnUiThread {
                compose.activity.setContent { }
                PowerCalSkinRegistry.reset()
            }
            compose.waitForIdle()
            store.close()
            isolated.deleteDatabase(databaseName)
            listOf("powercal-onboarding", "powercal-skin-choice", "powercal-ui", "powercal-profile")
                .forEach { storage.deleteSharedPreferences("$namespace-$it") }
            File(storage.cacheDir, namespace).deleteRecursively()
        }
    }

    @Test
    fun monthSelectionAndActivityCreateUseSelectedDateWithoutMovingTimeline() {
        val storage = compose.activity.applicationContext
        val namespace = "pc-month-selection-${UUID.randomUUID()}"
        val isolated = IsolatedAppContext(compose.activity, storage, namespace)
        val databaseName = "month.db"
        val store = PowerStore(isolated, databaseName)
        val skinManager = SkinPackageManager(isolated)
        try {
            skinManager.select("powercal-clear-default")
            isolated.getSharedPreferences("powercal-onboarding", Context.MODE_PRIVATE)
                .edit().putBoolean("system-calendar-import-asked", true).commit()
            compose.runOnUiThread {
                compose.activity.setContent {
                    CompositionLocalProvider(LocalContext provides isolated) {
                        PowerCalTheme { PowerCalApp(store) }
                    }
                }
            }

            var web: WebView? = null
            compose.waitUntil(30_000) {
                compose.runOnUiThread {
                    web = findWebView(compose.activity.findViewById(android.R.id.content))
                }
                web != null
            }
            val view = requireNotNull(web)
            awaitJs(view, """!!document.querySelector(".clear-tabbar [data-action='month']")""")
            val countsBefore = listOf(
                store.draftCount(), store.pendingCount(), store.identityCount(),
                store.commitCount(), store.nextSequencePreview(), store.nextQueueOrderPreview(),
                store.all().size.toLong()
            )
            var initial: org.json.JSONObject? = null
            compose.runOnIdle { initial = WebSkinBridgeRuntime.state() }
            val initialState = requireNotNull(initial)
            val camera = initialState.getJSONObject("timeline")
            val initialAnchor = camera.getLong("anchorMillis")
            val initialHours = camera.getDouble("hours")
            val zone = ZoneId.of(camera.getString("zoneId"))

            evaluate(view, """document.querySelector(".clear-tabbar [data-action='month']").click(); true""")
            awaitJs(view, "!document.querySelector('#monthPage').hidden")
            var selectedMonthDate = ""
            compose.runOnIdle {
                selectedMonthDate = WebSkinBridgeRuntime.state().getJSONObject("month")
                    .getString("selectedDate")
            }
            val currentSelection = LocalDate.parse(selectedMonthDate)
            val selectedDate = if (currentSelection.dayOfMonth != 1)
                currentSelection.withDayOfMonth(1) else currentSelection.plusDays(1)
            evaluate(view, """document.querySelector(".month-cell[data-date='$selectedDate']").click(); true""")
            awaitJs(view,
                """document.querySelector('.month-cell.selected')?.dataset.date==='$selectedDate' && """ +
                    """document.querySelector('#monthAgenda h2')?.textContent==='$selectedDate'""")
            compose.waitUntil(10_000) {
                var state: org.json.JSONObject? = null
                compose.runOnUiThread { state = WebSkinBridgeRuntime.state() }
                val value = requireNotNull(state)
                value.getString("page") == "MONTH" &&
                    value.getJSONObject("month").getString("selectedDate") == selectedDate.toString() &&
                    value.getJSONObject("timeline").getLong("anchorMillis") == initialAnchor &&
                    value.getJSONObject("timeline").getDouble("hours") == initialHours
            }

            evaluate(view, """document.querySelector("#monthPage [data-action='createDraft']").click(); true""")
            awaitJs(view, "!document.querySelector('#skinSheet').hidden && " +
                "!!document.querySelector('#sheetFields [data-field=title]')")
            var form: org.json.JSONObject? = null
            compose.runOnIdle {
                form = WebSkinBridgeRuntime.state().getJSONObject("editing")
                    .getJSONObject("deck").getJSONObject("form")
            }
            val createdForm = requireNotNull(form)
            assertEquals("ACTIVITY", createdForm.getString("kind"))
            assertEquals(selectedDate.toString(), createdForm.getString("startText").take(10))
            compose.runOnIdle {
                val afterCreateCamera = WebSkinBridgeRuntime.state().getJSONObject("timeline")
                assertEquals(initialAnchor, afterCreateCamera.getLong("anchorMillis"))
                assertEquals(initialHours, afterCreateCamera.getDouble("hours"), 0.0)
            }

            evaluate(view, "document.querySelector('#sheetClose').click(); true")
            awaitJs(view, "document.querySelector('#skinSheet').hidden")
            compose.waitUntil(10_000) {
                var isOpen = true
                compose.runOnUiThread {
                    isOpen = WebSkinBridgeRuntime.state().getJSONObject("editing")
                        .getJSONObject("deck").optBoolean("open", false)
                }
                !isOpen
            }
            assertEquals("closing the transient Month Activity editor must not write",
                countsBefore, listOf(
                    store.draftCount(), store.pendingCount(), store.identityCount(),
                    store.commitCount(), store.nextSequencePreview(), store.nextQueueOrderPreview(),
                    store.all().size.toLong()
                ))

            evaluate(view, "document.querySelector('#viewMonthDate').click(); true")
            awaitJs(view, "document.querySelector('#monthPage').hidden && " +
                "!document.querySelector('#timelinePage').hidden")
            compose.waitUntil(10_000) {
                var state: org.json.JSONObject? = null
                compose.runOnUiThread { state = WebSkinBridgeRuntime.state() }
                val timeline = requireNotNull(state).getJSONObject("timeline")
                requireNotNull(state).getString("page") == "TIMELINE" &&
                    timeline.getLong("anchorMillis") == selectedDate.atTime(12, 0).atZone(zone)
                        .toInstant().toEpochMilli()
            }
        } finally {
            compose.runOnUiThread {
                compose.activity.setContent { }
                PowerCalSkinRegistry.reset()
            }
            compose.waitForIdle()
            store.close()
            isolated.deleteDatabase(databaseName)
            listOf("powercal-onboarding", "powercal-skin-choice", "powercal-ui", "powercal-profile")
                .forEach { storage.deleteSharedPreferences("$namespace-$it") }
            File(storage.cacheDir, namespace).deleteRecursively()
        }
    }
}
