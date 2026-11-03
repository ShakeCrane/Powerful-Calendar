package dev.powerfulcalendar.ui.v2

import android.content.Context
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.powerfulcalendar.MainActivity
import dev.powerfulcalendar.core.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import java.security.MessageDigest
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@RunWith(AndroidJUnit4::class)
class SingleSkinMigrationInstrumentedTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private val prefs get() = context.getSharedPreferences("powercal-skin-choice", Context.MODE_PRIVATE)
    private val folder get() = File(context.filesDir, "skin-packs")
    private fun choose(id: String) { assertTrue(prefs.edit().putString("selected", id).commit()) }
    private fun fixture(id: String): File {
        folder.mkdirs()
        val manifest = JSONObject().put("format", "powercal-skin").put("schemaVersion", 1)
            .put("id", id).put("name", "QA fixture").put("light", JSONObject())
            .put("dark", JSONObject()).put("layout", JSONObject())
            .put("imagesLight", JSONObject()).put("imagesDark", JSONObject()).put("sha256", JSONObject())
        val bytes = ByteArrayOutputStream().also { output ->
            ZipOutputStream(output).use { zip ->
                zip.putNextEntry(ZipEntry("manifest.json")); zip.write(manifest.toString().toByteArray()); zip.closeEntry()
            }
        }.toByteArray()
        return File(folder, "$id.pcskin").apply { writeBytes(bytes) }
    }
    private fun snapshot(store: PowerStore): String {
        val db = store.readableDatabase
        val tables = db.rawQuery("SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%' ORDER BY name", null).use { cursor ->
            buildList { while (cursor.moveToNext()) add(cursor.getString(0)) }
        }
        val content = tables.joinToString("|") { table ->
                db.rawQuery("SELECT * FROM $table ORDER BY rowid", null).use { c ->
                    buildString { while (c.moveToNext()) { for (i in 0 until c.columnCount) append(c.getString(i)).append('|'); append('\n') } }
                }
            }
        return MessageDigest.getInstance("SHA-256").digest(content.toByteArray()).joinToString("") { "%02x".format(it) }
    }
    private fun webView(v: View): WebView? {
        if (v is WebView) return v
        if (v is ViewGroup) for (i in 0 until v.childCount) webView(v.getChildAt(i))?.let { return it }
        return null
    }
    private fun assertWebActivity() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            val ref = AtomicReference<WebView?>()
            val deadline = SystemClock.uptimeMillis() + 30000
            while (ref.get() == null && SystemClock.uptimeMillis() < deadline) {
                scenario.onActivity { ref.set(webView(it.findViewById(android.R.id.content))) }
                if (ref.get() == null) SystemClock.sleep(100)
            }
            val web = requireNotNull(ref.get())
            var ready = false
            while (!ready && SystemClock.uptimeMillis() < deadline) {
                val result = AtomicReference<String>()
                val latch = CountDownLatch(1)
                instrumentation.runOnMainSync { web.evaluateJavascript("document.readyState === 'complete' && document.title === 'PowerCal Clear' && !!document.querySelector('#timelinePage')") { result.set(it); latch.countDown() } }
                assertTrue(latch.await(10, TimeUnit.SECONDS))
                ready = result.get() == "true"
                if (!ready) SystemClock.sleep(100)
            }
            assertTrue("factory WebView did not render", ready)
        }
    }
    @Test fun migrateThirdPartyWithRealBusinessFixture() {
        assertFalse(prefs.contains("selected"))
        val store = PowerStore(context, "r03-qa-fixture.db")
        val zone = ZoneId.of("Asia/Shanghai")
        val date = LocalDate.now(zone).plusDays(5)
        val exceptionKey = "${date.plusDays(2)}T00:00"
        val memo = store.newDraft(DraftForm(kind = TemporalKind.MEMO, title = "R03 recurring fixture",
            startText = date.toString(), zoneId = zone.id, trackingEnabled = true,
            recurrence = Recurrence(freq = RecurrenceFreq.DAILY, count = 3, exceptions = setOf(exceptionKey))))
        assertTrue(store.commit(memo.draftId) is PowerStore.CommitOutcome.Committed)
        val record = requireNotNull(store.get(memo.eventSeq))
        assertEquals(setOf(exceptionKey), record.recurrence.exceptions)
        assertTrue(store.setCompleted(record.eventSeq, "${date}T00:00", true, record.revision))
        val activity = store.newDraft(DraftForm.fresh(TemporalKind.ACTIVITY, date, zone).copy(
            title = "R03 reminder fixture", startText = "${date}T09:00", endText = "${date}T10:00",
            reminderMinutes = 10, reminderMethod = ReminderMethod.MESSAGE, reminderTiming = ReminderTiming.OFFSET,
            enabled = true))
        assertTrue(store.commit(activity.draftId) is PowerStore.CommitOutcome.Committed)
        assertNotNull(store.intentFor(activity.eventSeq))
        store.newDraft(DraftForm(kind = TemporalKind.MEMO, title = "R03 draft fixture", startText = date.plusDays(1).toString(), zoneId = zone.id))
        assertTrue(store.pendingCount() > 0)
        val before = snapshot(store)
        val otherSettings = context.getSharedPreferences("r03-protected-settings", Context.MODE_PRIVATE)
        assertTrue(otherSettings.edit().putString("fixture", "unchanged").commit())
        assertTrue(context.getSharedPreferences("r03-test-evidence", Context.MODE_PRIVATE).edit().putString("store_hash", before).commit())
        val a = fixture("legacy-a"); val b = fixture("legacy-b")
        val nonSkin = File(folder, "keep.txt").apply { writeText("fixture") }
        val outside = File(context.filesDir, "outside.pcskin").apply { writeText("fixture") }
        val factory = context.assets.open("powercal-clear-default.pcskin").use { it.readBytes() }
        choose("legacy-a")
        val manager = SkinPackageManager(context)
        instrumentation.runOnMainSync { manager.restoreSelection() }
        assertEquals("legacy-a", PowerCalSkinRegistry.active.key)
        instrumentation.runOnMainSync { assertEquals(SkinMigrationResult.COMPLETE, manager.migrateHistoricalForQa()) }
        assertEquals("powercal-clear-default", manager.currentId())
        assertEquals("complete", prefs.getString("r03_migration", null))
        assertFalse(a.exists()); assertFalse(b.exists())
        assertEquals("fixture", nonSkin.readText()); assertEquals("fixture", outside.readText())
        assertArrayEquals(factory, context.assets.open("powercal-clear-default.pcskin").use { it.readBytes() })
        assertEquals(before, snapshot(store))
        assertEquals("unchanged", otherSettings.getString("fixture", null))
        assertWebActivity()
        assertEquals(before, snapshot(store))
        store.close()
    }
    @Test fun verifyMigratedColdLaunch() {
        assertEquals("complete", prefs.getString("r03_migration", null))
        assertEquals("powercal-clear-default", prefs.getString("selected", null))
        assertTrue(folder.listFiles().orEmpty().none { it.name.endsWith(".pcskin") })
        val store = PowerStore(context, "r03-qa-fixture.db")
        val expected = context.getSharedPreferences("r03-test-evidence", Context.MODE_PRIVATE).getString("store_hash", null)
        assertNotNull(expected)
        assertEquals(expected, snapshot(store))
        instrumentation.runOnMainSync { assertEquals(SkinMigrationResult.COMPLETE, SkinPackageManager(context).migrateHistoricalForQa()) }
        assertWebActivity()
        assertEquals("powercal-clear-default", PowerCalSkinRegistry.active.key)
        assertEquals(expected, snapshot(store))
        store.close()
    }
    @Test fun corruptFactoryCleansLegacyAndColdNativeRemains() {
        assertFalse(prefs.contains("selected"))
        val old = fixture("legacy-a"); choose("legacy-a")
        val broken = SkinPackageManager(context, factoryPackageBytes = { byteArrayOf(1, 2, 3) })
        instrumentation.runOnMainSync { assertEquals(SkinMigrationResult.COMPLETE, broken.migrateHistoricalForQa()) }
        assertFalse(old.exists())
        assertEquals("powercal-default", prefs.getString("selected", null))
        assertEquals("complete", prefs.getString("r03_migration", null))
        repeat(2) { ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { assertEquals("powercal-default", PowerCalSkinRegistry.active.key); assertNull(PowerCalSkinRegistry.active.web) }
        } }
    }
    @Test fun prepareQaState() {
        assertFalse(prefs.contains("selected"))
        val case = requireNotNull(InstrumentationRegistry.getArguments().getString("r03Case"))
        when (case) {
            "fresh" -> Unit
            "native" -> choose("powercal-default")
            "clear" -> choose("powercal-clear-default")
            "unknown" -> choose("missing-legacy")
            else -> error("unknown QA state")
        }
        val expected = if (case == "fresh" || case == "clear") "powercal-clear-default" else "powercal-default"
        instrumentation.runOnMainSync { assertEquals(SkinMigrationResult.COMPLETE, SkinPackageManager(context).migrateHistoricalForQa()) }
        assertEquals(expected, prefs.getString("selected", null))
        assertEquals(expected, PowerCalSkinRegistry.active.key)
        assertEquals("complete", prefs.getString("r03_migration", null))
    }
    @Test fun verifyQaStateAfterColdStart() {
        val case = requireNotNull(InstrumentationRegistry.getArguments().getString("r03Case"))
        val expected = if (case == "fresh" || case == "clear") "powercal-clear-default" else "powercal-default"
        assertEquals("complete", prefs.getString("r03_migration", null))
        assertEquals(expected, prefs.getString("selected", null))
        assertTrue(folder.listFiles().orEmpty().none { it.name.endsWith(".pcskin") })
        instrumentation.runOnMainSync { assertEquals(SkinMigrationResult.COMPLETE, SkinPackageManager(context).migrateHistoricalForQa()) }
        if (expected == "powercal-clear-default") assertWebActivity()
        else ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity {
                assertEquals("powercal-default", PowerCalSkinRegistry.active.key)
                assertNull(PowerCalSkinRegistry.active.web)
                assertNull(webView(it.findViewById(android.R.id.content)))
            }
        }
        assertEquals(expected, prefs.getString("selected", null))
    }
    @Test fun prepareInterruptedCleanup() {
        assertFalse(prefs.contains("selected"))
        fixture("legacy-a"); fixture("legacy-b"); fixture("legacy-c"); choose("legacy-a")
        var count = 0
        val subject = SkinPackageManager(context, deleteLegacyPackage = { file ->
            count++; if (count == 2) false else file.delete()
        })
        instrumentation.runOnMainSync { assertEquals(SkinMigrationResult.CLEANUP_FAILED, subject.migrateHistoricalForQa()) }
        assertEquals("pending", prefs.getString("r03_migration", null))
        assertEquals("powercal-clear-default", prefs.getString("selected", null))
        assertEquals(2, folder.listFiles().orEmpty().count { it.name.endsWith(".pcskin") })
    }
    @Test fun resumeInterruptedCleanupAfterProcessStop() {
        assertEquals("pending", prefs.getString("r03_migration", null))
        assertEquals(2, folder.listFiles().orEmpty().count { it.name.endsWith(".pcskin") })
        instrumentation.runOnMainSync { assertEquals(SkinMigrationResult.COMPLETE, SkinPackageManager(context).migrateHistoricalForQa()) }
        assertEquals("complete", prefs.getString("r03_migration", null))
        assertEquals("powercal-clear-default", prefs.getString("selected", null))
        assertTrue(folder.listFiles().orEmpty().none { it.name.endsWith(".pcskin") })
        assertWebActivity()
    }
    @Test fun failureInjectionResumesWithoutCrossingBoundary() {
        assertFalse(prefs.contains("selected"))
        val a = fixture("legacy-a"); fixture("legacy-b"); fixture("legacy-c"); choose("legacy-a")
        val noCommit = SkinPackageManager(context, migrationCommit = { false })
        instrumentation.runOnMainSync { assertEquals(SkinMigrationResult.PREFERENCE_FAILED, noCommit.migrateHistoricalForQa()) }
        assertTrue(a.exists()); assertEquals("powercal-default", PowerCalSkinRegistry.active.key)
        var count = 0
        val interrupted = SkinPackageManager(context, deleteLegacyPackage = { file ->
            count++; if (count == 2) throw IllegalStateException("injected interruption") else file.delete()
        })
        instrumentation.runOnMainSync { assertEquals(SkinMigrationResult.CLEANUP_FAILED, interrupted.migrateHistoricalForQa()) }
        assertEquals("pending", prefs.getString("r03_migration", null))
        assertEquals(2, folder.listFiles().orEmpty().count { it.name.endsWith(".pcskin") })
        val failedDelete = SkinPackageManager(context, deleteLegacyPackage = { false })
        instrumentation.runOnMainSync { assertEquals(SkinMigrationResult.CLEANUP_FAILED, failedDelete.migrateHistoricalForQa()) }
        assertEquals("pending", prefs.getString("r03_migration", null))
        instrumentation.runOnMainSync { assertEquals(SkinMigrationResult.COMPLETE, SkinPackageManager(context).migrateHistoricalForQa()) }
        assertTrue(folder.listFiles().orEmpty().none { it.name.endsWith(".pcskin") })
        assertEquals("powercal-clear-default", prefs.getString("selected", null))
        assertTrue(prefs.edit().clear().commit())
        val outside = File(context.cacheDir, "r03-outside-fixture").apply { writeText("safe") }
        folder.mkdirs()
        val link = File(folder, "unsafe.pcskin")
        Files.createSymbolicLink(link.toPath(), outside.toPath())
        choose("powercal-default")
        instrumentation.runOnMainSync { assertEquals(SkinMigrationResult.CLEANUP_FAILED, SkinPackageManager(context).migrateHistoricalForQa()) }
        assertEquals("safe", outside.readText())
        Files.delete(link.toPath())
        instrumentation.runOnMainSync { assertEquals(SkinMigrationResult.COMPLETE, SkinPackageManager(context).migrateHistoricalForQa()) }
        assertEquals("safe", outside.readText())
    }
}
