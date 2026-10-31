package dev.powerfulcalendar.ui.v2

import android.content.Context
import android.os.Build
import android.os.Process
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.test.platform.app.InstrumentationRegistry
import dev.powerfulcalendar.MainActivity
import dev.powerfulcalendar.core.PowerStore
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import androidx.test.ext.junit.runners.AndroidJUnit4

private const val CORRUPT_RECOVERY_NAMESPACE = "pc-corrupt-skin-cold-recovery"
private const val SKIN_CHOICE_PREFERENCES = "powercal-skin-choice"
private const val ONBOARDING_PREFERENCES = "powercal-onboarding"
private const val ONBOARDING_IMPORT_KEY = "system-calendar-import-asked"
private const val NATIVE_SKIN_ID = "powercal-default"
private val CORRUPT_PACKAGE_BYTES = "not-a-valid-pcskin-archive".toByteArray(Charsets.UTF_8)

private fun preferencesFile(context: Context, name: String) =
    File(context.applicationInfo.dataDir, "shared_prefs/$name.xml")

private object CorruptRecoveryProcessIdentity {
    val token: String = UUID.randomUUID().toString()
}

@RunWith(AndroidJUnit4::class)
class WebSkinCorruptPackageColdRecoveryInstrumentedTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    private fun runId(): String = requireNotNull(
        InstrumentationRegistry.getArguments().getString("skinCorruptRecoveryRunId")
    ).also { require(it.matches(Regex("[a-f0-9-]{36}"))) }

    private fun requireIsolatedSkinStore(context: Context) {
        assumeTrue("corrupt package cold recovery is restricted to Android emulators",
            Build.HARDWARE == "ranchu" || Build.HARDWARE == "goldfish")
        val manager = SkinPackageManager(context)
        assumeTrue("requires the original Native skin to be selected",
            manager.currentId() == NATIVE_SKIN_ID)
        assumeTrue("requires an empty installed-skin directory",
            File(context.filesDir, "skin-packs").listFiles().orEmpty().isEmpty())
    }

    private fun markerDirectory(context: Context, runId: String) =
        File(context.cacheDir, "$CORRUPT_RECOVERY_NAMESPACE-$runId")

    private fun packageFile(context: Context, packageId: String) =
        File(context.filesDir, "skin-packs/$packageId.pcskin")

    private fun saveMetadata(file: File, metadata: JSONObject) {
        check(file.parentFile?.mkdirs() == true || file.parentFile?.isDirectory == true)
        FileOutputStream(file).use { output ->
            output.write(metadata.toString().toByteArray(Charsets.UTF_8))
            output.flush()
            output.fd.sync()
        }
    }

    private fun storeSnapshot(store: PowerStore): JSONObject {
        val formalKeys = store.all().map { "${it.eventSeq}:${it.revision}:${it.kind.name}" }.sorted()
        val pendingKeys = store.pendingDrafts()
            .map { "${it.draftId}:${it.eventSeq}:${it.draftVersion}:${it.status.name}" }
        return JSONObject()
            .put("formal", JSONArray(formalKeys))
            .put("pendingDrafts", JSONArray(pendingKeys))
            .put("draftCount", store.draftCount())
            .put("pendingCount", store.pendingCount())
            .put("commitCount", store.commitCount())
            .put("identityCount", store.identityCount())
            .put("nextSequence", store.nextSequencePreview())
            .put("nextQueueOrder", store.nextQueueOrderPreview())
    }

    private fun restorePreferences(context: Context, metadata: JSONObject) {
        val skinPrefs = context.getSharedPreferences(SKIN_CHOICE_PREFERENCES, Context.MODE_PRIVATE)
        val skinEditor = skinPrefs.edit()
        if (metadata.optBoolean("hadSelection")) {
            skinEditor.putString("selected", metadata.getString("previousSelection"))
        } else {
            skinEditor.remove("selected")
        }
        check(skinEditor.commit()) { "failed to restore the original skin preference" }
        if (!metadata.optBoolean("hadSkinPreferencesFile")) {
            check(context.deleteSharedPreferences(SKIN_CHOICE_PREFERENCES)) {
                "failed to remove the test-created skin-choice preferences file"
            }
        }

        val onboarding = context.getSharedPreferences(ONBOARDING_PREFERENCES, Context.MODE_PRIVATE)
        val onboardingEditor = onboarding.edit()
        if (metadata.optBoolean("hadOnboardingFlag")) {
            onboardingEditor.putBoolean(ONBOARDING_IMPORT_KEY,
                metadata.optBoolean("onboardingFlag"))
        } else {
            onboardingEditor.remove(ONBOARDING_IMPORT_KEY)
        }
        check(onboardingEditor.commit()) { "failed to restore the original onboarding preference" }
        if (!metadata.optBoolean("hadOnboardingPreferencesFile")) {
            check(context.deleteSharedPreferences(ONBOARDING_PREFERENCES)) {
                "failed to remove the test-created onboarding preferences file"
            }
        }
        PowerCalSkinRegistry.reset()
    }

    private fun findWebView(view: View): WebView? {
        if (view is WebView) return view
        if (view is ViewGroup) for (index in 0 until view.childCount) {
            findWebView(view.getChildAt(index))?.let { return it }
        }
        return null
    }

    @Test
    fun prepareMalformedSelectedPackageForColdStartRecovery() {
        assumeTrue("run with -e skinCorruptRecoveryPhase prepare",
            InstrumentationRegistry.getArguments().getString("skinCorruptRecoveryPhase") == "prepare")
        val id = runId()
        val context = compose.activity.applicationContext
        requireIsolatedSkinStore(context)

        val markerDir = markerDirectory(context, id)
        val marker = File(markerDir, "session.json")
        val skinPrefs = context.getSharedPreferences(SKIN_CHOICE_PREFERENCES, Context.MODE_PRIVATE)
        val onboarding = context.getSharedPreferences(ONBOARDING_PREFERENCES, Context.MODE_PRIVATE)
        val packageId = "pc-corrupt-${id.take(8)}"
        val corruptFile = packageFile(context, packageId)
        assumeTrue("UUID-scoped marker must not pre-exist", !markerDir.exists())
        assumeTrue("UUID-scoped corrupt package must not pre-exist", !corruptFile.exists())

        val metadata = JSONObject()
            .put("runId", id)
            .put("packageId", packageId)
            .put("hadSkinPreferencesFile", preferencesFile(context, SKIN_CHOICE_PREFERENCES).exists())
            .put("hadOnboardingPreferencesFile", preferencesFile(context, ONBOARDING_PREFERENCES).exists())
            .put("hadSelection", skinPrefs.contains("selected"))
            .put("hadOnboardingFlag", onboarding.contains(ONBOARDING_IMPORT_KEY))
            .put("onboardingFlag", onboarding.getBoolean(ONBOARDING_IMPORT_KEY, false))
            .put("processPid", Process.myPid())
            .put("processToken", CorruptRecoveryProcessIdentity.token)
            .put("storeBefore", storeSnapshot(PowerCalStores.get(context)))
        if (skinPrefs.contains("selected")) {
            metadata.put("previousSelection", skinPrefs.getString("selected", NATIVE_SKIN_ID))
        }

        var prepared = false
        try {
            check(corruptFile.parentFile?.mkdirs() == true || corruptFile.parentFile?.isDirectory == true)
            corruptFile.writeBytes(CORRUPT_PACKAGE_BYTES)
            check(skinPrefs.edit().putString("selected", packageId).commit())
            metadata.put("selectedBeforeRestart", SkinPackageManager(context).currentId())
            check(onboarding.edit().putBoolean(ONBOARDING_IMPORT_KEY, true).commit())
            saveMetadata(marker, metadata)
            assertArrayEquals(CORRUPT_PACKAGE_BYTES, corruptFile.readBytes())
            assertEquals(packageId, SkinPackageManager(context).currentId())
            prepared = true
            println("CORRUPT_SKIN_RECOVERY_PREPARED=pid=${Process.myPid()}; package=$packageId; " +
                "selected=true; bytes=${CORRUPT_PACKAGE_BYTES.size}; nativeDataReadOnly=true")
        } finally {
            if (!prepared) {
                corruptFile.delete()
                restorePreferences(context, metadata)
                marker.delete()
                markerDir.delete()
            }
        }
    }

    @Test
    fun coldStartFallsBackToNativeAndLeavesCalendarStoreUntouched() {
        assumeTrue("run with -e skinCorruptRecoveryPhase restore",
            InstrumentationRegistry.getArguments().getString("skinCorruptRecoveryPhase") == "restore")
        val id = runId()
        val context = compose.activity.applicationContext
        val markerDir = markerDirectory(context, id)
        val marker = File(markerDir, "session.json")
        assumeTrue("prepare phase must leave a recovery marker", marker.isFile)
        val metadata = JSONObject(marker.readText(Charsets.UTF_8))
        assertEquals(id, metadata.getString("runId"))
        val packageId = metadata.getString("packageId")
        val corruptFile = packageFile(context, packageId)
        val manager = SkinPackageManager(context)
        try {
            assertNotEquals("target process must be new after force-stop",
                metadata.getInt("processPid"), Process.myPid())
            assertNotEquals("test process token must change after force-stop",
                metadata.getString("processToken"), CorruptRecoveryProcessIdentity.token)
            assertArrayEquals(CORRUPT_PACKAGE_BYTES, corruptFile.readBytes())
            assertEquals("prepare marker must record the malformed package as selected",
                packageId, metadata.getString("selectedBeforeRestart"))

            val store = PowerCalStores.get(context)
            compose.waitForIdle()
            assertEquals("startup recovery must persist Native selection", NATIVE_SKIN_ID,
                manager.currentId())
            assertEquals(NATIVE_SKIN_ID, PowerCalSkinRegistry.active.key)
            assertEquals(null, PowerCalSkinRegistry.active.web)
            assertTrue("corrupt package file should remain available for diagnosis", corruptFile.isFile)
            assertFalse("malformed archive must not enter the installed package catalogue",
                manager.installed().any { it.id == packageId })

            assertFalse("Native recovery must not retain a WebView",
                findWebView(compose.activity.window.decorView) != null)
            assertTrue("MainActivity must be in the foreground", compose.activity.hasWindowFocus())
            compose.waitUntil(15_000) {
                compose.onAllNodesWithText("时间轴").fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithText("时间轴").assertIsDisplayed()

            val afterStartup = storeSnapshot(store)
            assertEquals("corrupt skin recovery must not change formal rows, drafts, commits or identities",
                metadata.getJSONObject("storeBefore").toString(), afterStartup.toString())
            println("CORRUPT_SKIN_RECOVERY_RESTORED=pid=${Process.myPid()}; selected=${manager.currentId()}; " +
                "native=${PowerCalSkinRegistry.active.key}; webView=false; timelineAccessible=true; storeUnchanged=true")
        } finally {
            restorePreferences(context, metadata)
            check(corruptFile.delete() || !corruptFile.exists()) {
                "UUID-scoped corrupt package was not removed"
            }
            check(marker.delete() || !marker.exists()) { "recovery marker was not removed" }
            check(markerDir.delete() || !markerDir.exists()) { "recovery marker directory remains" }
            compose.runOnUiThread { PowerCalSkinRegistry.reset() }
            compose.waitForIdle()
        }
    }
}
