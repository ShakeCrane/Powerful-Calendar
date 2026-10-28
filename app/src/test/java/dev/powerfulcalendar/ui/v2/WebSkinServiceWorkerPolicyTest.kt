package dev.powerfulcalendar.ui.v2

import android.webkit.WebSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
class WebSkinServiceWorkerPolicyTest {
    @Test
    fun policyDisablesPersistentServiceWorkerCapabilities() {
        val settings = FakeSettingsPort()

        WebSkinServiceWorkerPolicy.configure(settings)
        assertFalse(settings.allowFileAccess)
        assertFalse(settings.allowContentAccess)
        assertTrue(settings.blockNetworkLoads)
        assertEquals(WebSettings.LOAD_NO_CACHE, settings.cacheMode)
    }

    private class FakeSettingsPort : WebSkinServiceWorkerPolicy.SettingsPort {
        override var allowFileAccess = true
        override var allowContentAccess = true
        override var blockNetworkLoads = false
        override var cacheMode = WebSettings.LOAD_DEFAULT
    }
}
