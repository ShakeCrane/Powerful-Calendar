package dev.powerfulcalendar.ui.v2

import android.webkit.CookieManager
import android.webkit.ServiceWorkerController
import android.webkit.WebSettings
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WebSkinServiceWorkerPolicyInstrumentedTest {
    @Test
    fun hostLocksProcessWideWebStorageChannelsBeforeCreatingSkinWebView() {
        WebSkinServiceWorkerPolicy.install()

        val workerSettings = ServiceWorkerController.getInstance().serviceWorkerWebSettings
        assertFalse(workerSettings.allowFileAccess)
        assertFalse(workerSettings.allowContentAccess)
        assertTrue(workerSettings.blockNetworkLoads)
        assertEquals(WebSettings.LOAD_NO_CACHE, workerSettings.cacheMode)
        assertFalse(CookieManager.getInstance().acceptCookie())
    }
}
