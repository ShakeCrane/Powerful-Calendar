package dev.powerfulcalendar.ui.v2

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WebSkinOriginTest {
    @Test fun eachWebSkinSessionGetsAnIndependentHttpsOrigin() {
        val first = WebSkinOrigin.newSession()
        val second = WebSkinOrigin.newSession()

        assertNotEquals(first.host, second.host)
        assertTrue(first.host.matches(Regex("[0-9a-f]{32}\\.skin\\.powercal\\.local")))
        assertEquals("https://${first.host}/", first.baseUrl)
        assertEquals("https://${first.host}/web/index.html",
            first.entrypoint("web/index.html"))
    }

    @Test fun packagedResourcesMustUseTheExactSessionOrigin() {
        val first = WebSkinOrigin.newSession()
        val second = WebSkinOrigin.newSession()

        assertTrue(first.acceptsResource(first.baseUrl + "web/style.css"))
        assertFalse(first.acceptsResource(second.baseUrl + "web/style.css"))
        assertFalse(first.acceptsResource("http://${first.host}/web/style.css"))
        assertFalse(first.acceptsResource("https://${first.host}:443/web/style.css"))
        assertFalse(first.acceptsResource("https://user@${first.host}/web/style.css"))
        assertFalse(first.acceptsResource(first.baseUrl + "web/style.css?x=1"))
        assertFalse(first.acceptsResource("not a URI"))
    }

    @Test fun onlyTheExactPackagedEntrypointCanFinishOrNavigate() {
        val origin = WebSkinOrigin.newSession()
        val entrypoint = origin.entrypoint("web/index.html")

        assertTrue(origin.isEntrypoint(entrypoint, "web/index.html"))
        assertFalse(origin.isEntrypoint(origin.entrypoint("web/other.html"), "web/index.html"))
        assertFalse(origin.isEntrypoint(entrypoint + "?reload=1", "web/index.html"))
        assertFalse(origin.isEntrypoint(entrypoint + "#fragment", "web/index.html"))
    }
}
