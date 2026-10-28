package dev.powerfulcalendar.ui.v2

import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import android.os.Build
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.json.JSONObject
import org.json.JSONTokener
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/** Exercises v3 package resources through the production SecureWebSkin WebView. */
@RunWith(AndroidJUnit4::class)
class WebSkinResourceParityInstrumentedTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private data class ShownSkin(
        val webView: WebView,
        val failure: AtomicReference<String?>
    )

    private fun fixture(): PowerCalSkinPack {
        val html = """
            <!doctype html>
            <html>
              <head>
                <meta charset="utf-8">
                <link rel="stylesheet" href="/web/style.css">
                <script src="/web/app.js"></script>
                <script>window.__inlineScriptExecuted = true;</script>
              </head>
              <body>
                <img id="valid-png" src="/assets/Probe.PNG">
                <img id="query-png" src="/assets/Probe.PNG?cache=1">
                <img id="missing-png" src="/assets/Missing.PNG">
                <span id="font-probe">PowerCal font probe</span>
              </body>
            </html>
        """.trimIndent()
        val css = """
            @font-face {
              font-family: "ParityProbe";
              src: url("/assets/Probe.WOFF2") format("woff2");
              font-style: normal;
              font-weight: 100 900;
              font-display: block;
            }
            :root { --pc-css-probe: loaded; }
            #font-probe { font-family: "ParityProbe"; font-size: 18px; }
        """.trimIndent()
        val script = """
            window.__externalScriptLoaded = true;
            window.__imageResults = {};
            window.__cspViolations = [];
            window.__fontLoadState = "pending";
            window.__connectProbe = "pending";
            window.addEventListener("securitypolicyviolation", function (event) {
              window.__cspViolations.push({
                directive: event.effectiveDirective,
                blockedURI: event.blockedURI
              });
            });
            function recordImageEvent(event) {
              var image = event.target;
              if (image && image.tagName === "IMG") {
                window.__imageResults[image.id] =
                  event.type === "load" ? "loaded" : "failed";
              }
            }
            document.addEventListener("load", recordImageEvent, true);
            document.addEventListener("error", recordImageEvent, true);
            document.addEventListener("DOMContentLoaded", function () {
              document.fonts.load('18px "ParityProbe"', "PowerCal").then(function (faces) {
                window.__fontLoadState =
                  faces.length > 0 && faces.every(function (face) {
                    return face.status === "loaded";
                  }) ? "loaded" : "empty";
              }).catch(function () {
                window.__fontLoadState = "failed";
              });
            });
            fetch("/assets/connect-probe.json").then(function () {
              window.__connectProbe = "allowed";
            }).catch(function () {
              window.__connectProbe = "blocked";
            });
        """.trimIndent()

        val png = tinyPng()
        val woff2 = bundledWoff2()
        assertTrue("bundled test WOFF2 must be non-empty", woff2.isNotEmpty())
        val resources = linkedMapOf(
            "web/index.html" to html.toByteArray(StandardCharsets.UTF_8),
            "web/style.css" to css.toByteArray(StandardCharsets.UTF_8),
            "web/app.js" to script.toByteArray(StandardCharsets.UTF_8),
            "project.json" to JSONObject().put("projectVersion", 3).put("mode", "advanced")
                .toString().toByteArray(StandardCharsets.UTF_8),
            "assets/Probe.PNG" to png,
            "assets/Probe.WOFF2" to woff2
        )
        val hashes = JSONObject()
        resources.forEach { (path, bytes) -> hashes.put(path, sha256(bytes)) }
        val manifest = JSONObject()
            .put("format", "powercal-skin")
            .put("schemaVersion", 3)
            .put("id", "resource-parity-api35")
            .put("name", "Resource parity API 35")
            .put("entrypoint", "web/index.html")
            .put("project", "project.json")
            .put("fullCanvas", true)
            .put("sha256", hashes)
        val archive = ByteArrayOutputStream().also { bytes ->
            ZipOutputStream(bytes).use { zip ->
                fun add(path: String, content: ByteArray) {
                    zip.putNextEntry(ZipEntry(path))
                    zip.write(content)
                    zip.closeEntry()
                }
                add("manifest.json", manifest.toString().toByteArray(StandardCharsets.UTF_8))
                resources.forEach(::add)
            }
        }.toByteArray()
        val pack = SkinPackageManager(compose.activity.applicationContext)
            .decode(archive, loadImages = false).pack
        assertNotNull("fixture must decode as a v3 Web skin", pack.web)
        return pack
    }

    /**
     * Reuses a genuine WOFF2 already shipped in the app asset, avoiding a synthetic or invalid
     * font payload. The package under test still serves it from the uppercase .WOFF2 path.
     */
    private fun bundledWoff2(): ByteArray {
        val input = compose.activity.applicationContext.assets
            .open("powercal-clear-default.pcskin")
        ZipInputStream(input).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (entry.name == "web/fonts/InterVariable.woff2") {
                    return zip.readBytes()
                }
            }
        }
        error("Bundled Clear package has no InterVariable.woff2 fixture")
    }

    private fun tinyPng(): ByteArray {
        val bitmap = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888)
        bitmap.setPixel(0, 0, AndroidColor.MAGENTA)
        bitmap.setPixel(1, 0, AndroidColor.CYAN)
        bitmap.setPixel(0, 1, AndroidColor.YELLOW)
        bitmap.setPixel(1, 1, AndroidColor.BLACK)
        return ByteArrayOutputStream().use { output ->
            check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output))
            bitmap.recycle()
            output.toByteArray()
        }
    }

    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes).joinToString("") { "%02x".format(it) }

    private fun findWebView(view: View): WebView? {
        if (view is WebView) return view
        if (view is ViewGroup) {
            for (index in 0 until view.childCount) {
                findWebView(view.getChildAt(index))?.let { return it }
            }
        }
        return null
    }

    private fun evaluate(web: WebView, expression: String): String {
        val result = AtomicReference<String>()
        val finished = CountDownLatch(1)
        compose.runOnUiThread {
            web.evaluateJavascript(expression) { value ->
                result.set(value)
                finished.countDown()
            }
        }
        assertTrue("WebView JavaScript timed out: $expression",
            finished.await(10, TimeUnit.SECONDS))
        return requireNotNull(result.get())
    }

    private fun evaluateText(web: WebView, expression: String): String =
        JSONTokener(evaluate(web, expression)).nextValue() as String

    private fun awaitJs(web: WebView, expression: String) {
        compose.waitUntil(30_000) { evaluate(web, expression) == "true" }
    }

    private fun show(pack: PowerCalSkinPack): ShownSkin {
        val failure = AtomicReference<String?>()
        val found = AtomicReference<WebView?>()
        compose.runOnUiThread {
            PowerCalSkinRegistry.activate(pack)
            compose.activity.setContent {
                WebSkinRoot(
                    requireNotNull(pack.web),
                    Modifier.fillMaxSize(),
                    native = {},
                    onSkinFailure = { failure.set("WebSkinRoot entered Native fallback") }
                )
            }
        }
        compose.waitUntil(30_000) {
            compose.runOnUiThread {
                found.set(findWebView(compose.activity.findViewById(android.R.id.content)))
            }
            found.get() != null
        }
        return ShownSkin(requireNotNull(found.get()), failure)
    }

    @Test
    fun v3ResourcesFailuresMimeAndCspAreObservedByRealWebView() {
        assertEquals("This instrumentation fixture targets API 35", 35, Build.VERSION.SDK_INT)
        val previousPack = PowerCalSkinRegistry.active
        val shown = show(fixture())
        val web = shown.webView
        try {
            awaitJs(
                web,
                """
                document.URL === "https://skin.powercal.local/web/index.html" &&
                window.__externalScriptLoaded === true &&
                window.__fontLoadState === "loaded" &&
                window.__imageResults["valid-png"] === "loaded" &&
                window.__imageResults["query-png"] === "failed" &&
                window.__imageResults["missing-png"] === "failed" &&
                window.__connectProbe === "blocked" &&
                window.__cspViolations.some(function (v) {
                  return v.directive.indexOf("script-src") === 0;
                }) &&
                window.__cspViolations.some(function (v) {
                  return v.directive === "connect-src";
                }) &&
                getComputedStyle(document.documentElement)
                  .getPropertyValue("--pc-css-probe").trim() === "loaded"
                """.trimIndent()
            )

            assertEquals("HTML entrypoint must be served at the isolated v3 origin",
                "https://skin.powercal.local/web/index.html",
                evaluateText(web, "document.URL"))
            assertEquals("external CSS must load from a root-relative path", "loaded",
                evaluateText(web,
                    "getComputedStyle(document.documentElement)" +
                        ".getPropertyValue('--pc-css-probe').trim()"))
            assertEquals("external JavaScript must execute", "true",
                evaluate(web, "window.__externalScriptLoaded === true"))
            assertEquals("uppercase PNG must decode and render", "true",
                evaluate(web,
                    "document.getElementById('valid-png').naturalWidth === 2 && " +
                        "document.getElementById('valid-png').naturalHeight === 2"))
            assertEquals("uppercase WOFF2 must be accepted and loaded by CSS", "loaded",
                evaluateText(web, "window.__fontLoadState"))

            assertEquals("existing root-relative resource with a query must fail", "failed",
                evaluateText(web, "window.__imageResults['query-png']"))
            assertEquals("missing root-relative resource must fail", "failed",
                evaluateText(web, "window.__imageResults['missing-png']"))

            assertEquals("CSP must prevent the inline script from executing", "true",
                evaluate(web, "typeof window.__inlineScriptExecuted === 'undefined'"))
            assertEquals("CSP must block connect-src fetch", "blocked",
                evaluateText(web, "window.__connectProbe"))
            assertEquals("CSP must report inline script violation", "true",
                evaluate(web,
                    "window.__cspViolations.some(function (v) {" +
                        "return v.directive.indexOf('script-src') === 0; })"))
            assertEquals("CSP must report the blocked connection", "true",
                evaluate(web,
                    "window.__cspViolations.some(function (v) {" +
                        "return v.directive === 'connect-src'; })"))
            assertNull("successful and rejected resource probes must not trigger fallback",
                shown.failure.get())
        } finally {
            compose.runOnUiThread {
                compose.activity.setContent { }
                PowerCalSkinRegistry.activate(previousPack)
            }
            compose.waitForIdle()
        }
    }
}
