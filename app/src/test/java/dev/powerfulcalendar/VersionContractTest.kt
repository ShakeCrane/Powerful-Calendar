package dev.powerfulcalendar

import dev.powerfulcalendar.core.BackupEnvelope
import dev.powerfulcalendar.core.PowerStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The three version axes are deliberately independent. This test pins the released package version
 * and proves it is not accidentally coupled to the SQLite schema or the backup format.
 */
class VersionContractTest {
    @Test
    fun packageIdentityIsStableForInPlaceUpgrades() {
        assertEquals("dev.powerfulcalendar", BuildConfig.APPLICATION_ID)
        assertEquals("0.9.1-rc.1", BuildConfig.VERSION_NAME)
        assertTrue("first stable RC must stay above the historical debug code 2",
            BuildConfig.VERSION_CODE == 901)
    }

    @Test
    fun schemaBackupAndPackageVersionsAreSeparateAxes() {
        assertEquals(3, PowerStore.VERSION)
        assertEquals(1, BackupEnvelope.FORMAT_VERSION)
        assertNotEquals(BuildConfig.VERSION_CODE, PowerStore.VERSION)
        assertNotEquals(BuildConfig.VERSION_CODE, BackupEnvelope.FORMAT_VERSION)
    }
}
