package dev.powerfulcalendar.ui.v2

import android.content.Context
import android.content.ContextWrapper
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SingleSkinSelectionMigrationTest {
    private lateinit var storage: Context
    private lateinit var root: File
    private val prefs get() = storage.getSharedPreferences("powercal-skin-choice", Context.MODE_PRIVATE)
    private val folder get() = File(root, "skin-packs")
    private val manager get() = SkinPackageManager(storage)

    @Before fun setup() {
        val base = ApplicationProvider.getApplicationContext<Context>()
        root = File(base.cacheDir, "r03-" + UUID.randomUUID()).apply { mkdirs() }
        storage = object : ContextWrapper(base) { override fun getFilesDir(): File = root }
        prefs.edit().clear().commit()
        PowerCalSkinRegistry.reset()
    }
    private fun select(id: String) { prefs.edit().putString("selected", id).commit() }
    private fun fixture(id: String): File {
        folder.mkdirs()
        val manifest = JSONObject().put("format", "powercal-skin").put("schemaVersion", 1)
            .put("id", id).put("name", "fixture").put("light", JSONObject())
            .put("dark", JSONObject()).put("layout", JSONObject())
            .put("imagesLight", JSONObject()).put("imagesDark", JSONObject()).put("sha256", JSONObject())
        val bytes = ByteArrayOutputStream().also { output ->
            ZipOutputStream(output).use { zip ->
                zip.putNextEntry(ZipEntry("manifest.json"))
                zip.write(manifest.toString().toByteArray())
                zip.closeEntry()
            }
        }.toByteArray()
        return File(folder, "$id.pcskin").apply { writeBytes(bytes) }
    }
    private fun assertComplete(id: String) {
        assertEquals(SkinMigrationResult.COMPLETE, manager.migrateHistoricalForQa())
        assertEquals(id, prefs.getString("selected", null))
        assertEquals("complete", prefs.getString("r03_migration", null))
        assertEquals(id, PowerCalSkinRegistry.active.key)
    }

    @Test fun freshInstallUsesFactoryAndRepeatDoesNotWrite() {
        assertComplete("powercal-clear-default")
        val active = PowerCalSkinRegistry.active
        var writes = 0
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> writes++ }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        assertEquals(SkinMigrationResult.COMPLETE, manager.migrateHistoricalForQa())
        assertSame(active, PowerCalSkinRegistry.active)
        assertEquals(0, writes)
        prefs.unregisterOnSharedPreferenceChangeListener(listener)
    }
    @Test fun explicitNativeRemainsRecovery() {
        select("powercal-default")
        fixture("legacy-a")
        assertComplete("powercal-default")
        assertTrue(folder.listFiles().orEmpty().isEmpty())
        assertTrue(manager.selectNativeForEmergencyRecovery())
        assertEquals(SkinMigrationResult.COMPLETE, manager.migrateHistoricalForQa())
        assertEquals("powercal-default", manager.currentId())
    }
    @Test fun clearRemainsAndFactoryAssetUnchanged() {
        select("powercal-clear-default")
        val before = storage.assets.open("powercal-clear-default.pcskin").use { it.readBytes() }
        fixture("legacy-a")
        assertComplete("powercal-clear-default")
        assertArrayEquals(before, storage.assets.open("powercal-clear-default.pcskin").use { it.readBytes() })
    }
    @Test fun validThirdPartyIsRetiredAndAllOldPackagesRemoved() {
        val first = fixture("legacy-a")
        val second = fixture("legacy-b")
        val nonSkin = File(folder, "notes.txt").apply { writeText("keep") }
        val outside = File(root, "outside.pcskin").apply { writeText("keep") }
        select("legacy-a")
        manager.restoreSelection()
        assertEquals("legacy-a", PowerCalSkinRegistry.active.key)
        assertComplete("powercal-clear-default")
        assertFalse(first.exists())
        assertFalse(second.exists())
        assertEquals("keep", nonSkin.readText())
        assertEquals("keep", outside.readText())
    }
    @Test fun unknownMissingAndCorruptSelectionsRecoverNative() {
        for (id in listOf("missing", "bad", "../outside")) {
            prefs.edit().clear().putString("selected", id).commit()
            PowerCalSkinRegistry.reset()
            if (id == "bad") { folder.mkdirs(); File(folder, "bad.pcskin").writeBytes(byteArrayOf(1)) }
            assertComplete("powercal-default")
            assertTrue(folder.listFiles().orEmpty().none { it.name.endsWith(".pcskin") })
        }
    }
    @Test fun corruptFactoryUsesNativeAndRemovesOldPackages() {
        fixture("legacy-a")
        select("legacy-a")
        val subject = SkinPackageManager(storage, factoryPackageBytes = { byteArrayOf(0) })
        assertEquals(SkinMigrationResult.COMPLETE, subject.migrateHistoricalForQa())
        assertEquals("powercal-default", subject.currentId())
        assertNull(PowerCalSkinRegistry.active.web)
        assertTrue(folder.listFiles().orEmpty().isEmpty())
    }
    @Test fun preferenceFailureLeavesFilesAndSafeRegistryThenRetries() {
        val old = fixture("legacy-a")
        select("legacy-a")
        manager.restoreSelection()
        val broken = SkinPackageManager(storage, migrationCommit = { false })
        assertEquals(SkinMigrationResult.PREFERENCE_FAILED, broken.migrateHistoricalForQa())
        assertTrue(old.exists())
        assertEquals("powercal-default", PowerCalSkinRegistry.active.key)
        assertNotEquals("complete", prefs.getString("r03_migration", null))
        assertComplete("powercal-clear-default")
        assertFalse(old.exists())
    }
    @Test fun partialDeleteFailureAndInterruptionCanResume() {
        fixture("legacy-a"); fixture("legacy-b"); fixture("legacy-c")
        select("legacy-a")
        var count = 0
        val interrupted = SkinPackageManager(storage, deleteLegacyPackage = {
            count++
            if (count == 2) throw IllegalStateException("simulated interruption")
            it.delete()
        })
        assertEquals(SkinMigrationResult.CLEANUP_FAILED, interrupted.migrateHistoricalForQa())
        assertEquals("pending", prefs.getString("r03_migration", null))
        assertEquals(2, folder.listFiles().orEmpty().count { it.name.endsWith(".pcskin") })
        PowerCalSkinRegistry.reset()
        assertComplete("powercal-clear-default")
        assertTrue(folder.listFiles().orEmpty().isEmpty())
    }
    @Test fun deleteFailureIsNotSuccessAndNativeEmergencyIsNotOverriddenOnRetry() {
        fixture("legacy-a"); select("legacy-a")
        val broken = SkinPackageManager(storage, deleteLegacyPackage = { false })
        assertEquals(SkinMigrationResult.CLEANUP_FAILED, broken.migrateHistoricalForQa())
        assertEquals("pending", prefs.getString("r03_migration", null))
        assertTrue(manager.selectNativeForEmergencyRecovery())
        assertComplete("powercal-default")
        assertTrue(folder.listFiles().orEmpty().isEmpty())
    }
    @Test fun preferenceFailureAfterMemoryUpdateRemainsRetryable() {
        fixture("legacy-a"); select("legacy-a")
        val broken = SkinPackageManager(storage, migrationCommit = { editor -> editor.apply(); false })
        assertEquals(SkinMigrationResult.PREFERENCE_FAILED, broken.migrateHistoricalForQa())
        assertEquals("pending", prefs.getString("r03_migration", null))
        assertEquals("powercal-default", PowerCalSkinRegistry.active.key)
        assertTrue(File(folder, "legacy-a.pcskin").exists())
        assertComplete("powercal-clear-default")
    }
    @Test fun factoryBecomingCorruptDuringPendingRecoverySelectsNative() {
        fixture("legacy-a"); fixture("legacy-b"); select("legacy-a")
        val interrupted = SkinPackageManager(storage, deleteLegacyPackage = { false })
        assertEquals(SkinMigrationResult.CLEANUP_FAILED, interrupted.migrateHistoricalForQa())
        val broken = SkinPackageManager(storage, factoryPackageBytes = { byteArrayOf(0) })
        assertEquals(SkinMigrationResult.COMPLETE, broken.migrateHistoricalForQa())
        assertEquals("powercal-default", prefs.getString("selected", null))
        assertEquals("powercal-default", PowerCalSkinRegistry.active.key)
        assertTrue(folder.listFiles().orEmpty().isEmpty())
    }
    @Test fun completionCommitFailureIsRetryable() {
        fixture("legacy-a"); select("legacy-a")
        var calls = 0
        val broken = SkinPackageManager(storage, migrationCommit = { editor ->
            calls++; if (calls == 2) false else editor.commit()
        })
        assertEquals(SkinMigrationResult.PREFERENCE_FAILED, broken.migrateHistoricalForQa())
        assertEquals("pending", prefs.getString("r03_migration", null))
        assertTrue(folder.listFiles().orEmpty().isEmpty())
        assertComplete("powercal-clear-default")
    }
    @Test fun ordinaryProductionRestoreNeverMigratesOrDeletes() {
        val old = fixture("legacy-a"); select("legacy-a")
        manager.restoreSelection()
        assertEquals("legacy-a", PowerCalSkinRegistry.active.key)
        assertTrue(old.exists())
        assertFalse(prefs.contains("r03_migration"))
    }
}
