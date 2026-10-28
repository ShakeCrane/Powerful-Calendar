package dev.powerfulcalendar.ui.v2

import android.net.Uri
import android.os.Build
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import java.security.MessageDigest
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class WebSkinInstalledPackageExportInstrumentedTest {
    @Test
    fun installedThirdPartyV3PackageExportsItsOriginalStudioArchiveThroughContentResolver() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val manager = SkinPackageManager(context)
        assumeTrue("package export integration runs only on isolated Android emulators",
            Build.HARDWARE == "ranchu" || Build.HARDWARE == "goldfish")
        assumeTrue("requires the Native safety skin to remain selected",
            manager.currentId() == "powercal-default")
        val packages = File(context.filesDir, "skin-packs")
        assumeTrue("requires an empty isolated third-party package directory",
            packages.listFiles().orEmpty().isEmpty())

        val bytes = InstrumentationRegistry.getInstrumentation().context.assets
            .open("skin-delivery-fixture.pcskin").use { it.readBytes() }
        assertEquals("Studio fixture baseline changed",
            "e7f200d4ddb6cb64cea3eda71f770b9dd920657f615d524319a3858897e5bddf",
            sha256(bytes))
        val input = File(context.cacheDir, "s0-pkg07-input-${UUID.randomUUID()}.pcskin")
        val output = File(context.cacheDir, "s0-pkg07-export-${UUID.randomUUID()}.pcskin")
        val database = context.getDatabasePath("powercal-v2.db")
        val databaseLengthBefore = if (database.isFile) database.length() else -1L
        assertFalse("test must not overwrite a pre-existing export", output.exists())

        try {
            input.writeBytes(bytes)
            val installed = manager.install(Uri.fromFile(input))
            assertEquals("pc-delivery-fixture", installed.id)
            assertEquals("install must not activate the package", "powercal-default",
                manager.currentId())
            assertArrayEquals("installer must retain the original Studio archive", bytes,
                File(packages, "${installed.id}.pcskin").readBytes())

            manager.exportInstalled(installed.id, Uri.fromFile(output))

            assertNotNull("SAF-compatible file URI export must create a package",
                output.takeIf { it.isFile })
            assertArrayEquals("exported ZIP bytes must match the installed Studio package",
                bytes, output.readBytes())
            assertEquals("export must not activate the package", "powercal-default",
                manager.currentId())
            assertArrayEquals("export must not rewrite the installed archive", bytes,
                File(packages, "${installed.id}.pcskin").readBytes())
            assertEquals("skin export must not write calendar storage", databaseLengthBefore,
                if (database.isFile) database.length() else -1L)
            assertEquals("reopened archive must retain its third-party identity", installed.id,
                manager.decode(output.readBytes(), loadImages = false).pack.key)
        } finally {
            runCatching { manager.delete("pc-delivery-fixture") }
            input.delete()
            output.delete()
        }
    }

    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes).joinToString("") { "%02x".format(it) }
}
