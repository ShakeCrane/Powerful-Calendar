package dev.powerfulcalendar.ui.v2

import android.Manifest
import android.app.Activity
import android.app.Instrumentation
import android.accessibilityservice.AccessibilityService
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.os.Environment
import android.os.SystemClock
import android.provider.MediaStore
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.view.InputDevice
import android.view.MotionEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.webkit.WebView
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.powerfulcalendar.core.BackupEnvelope
import dev.powerfulcalendar.core.DraftForm
import dev.powerfulcalendar.core.PowerStore
import dev.powerfulcalendar.core.TemporalKind
import dev.powerfulcalendar.interop.IcsCodec
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.Assume.assumeTrue
import org.junit.runner.RunWith
import java.security.MessageDigest
import java.util.UUID
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

@RunWith(AndroidJUnit4::class)
class ClearSettingsOptionsInstrumentedTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private fun findWebView(view: View): WebView? {
        if (view is WebView) return view
        if (view is ViewGroup) for (index in 0 until view.childCount) {
            findWebView(view.getChildAt(index))?.let { return it }
        }
        return null
    }

    private fun evaluate(web: WebView, expression: String): String {
        val result = AtomicReference<String>()
        val complete = CountDownLatch(1)
        compose.runOnUiThread {
            web.evaluateJavascript(expression) { value ->
                result.set(value)
                complete.countDown()
            }
        }
        assertTrue("WebView JavaScript response timed out", complete.await(10, TimeUnit.SECONDS))
        return result.get()
    }

    private fun findAccessibilityNode(
        root: AccessibilityNodeInfo?,
        text: String? = null,
        contentDescription: String? = null,
        viewId: String? = null
    ): AccessibilityNodeInfo? {
        if (root == null) return null
        if ((text == null || root.text?.toString() == text) &&
            (contentDescription == null || root.contentDescription?.toString() == contentDescription) &&
            (viewId == null || root.viewIdResourceName == viewId)) return root
        for (index in 0 until root.childCount) {
            findAccessibilityNode(root.getChild(index), text, contentDescription, viewId)
                ?.let { return it }
        }
        return null
    }

    private fun waitForPickerNode(
        text: String? = null,
        contentDescription: String? = null,
        viewId: String? = null,
        timeoutMillis: Long = 15_000
    ): AccessibilityNodeInfo {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val deadline = SystemClock.elapsedRealtime() + timeoutMillis
        while (SystemClock.elapsedRealtime() < deadline) {
            val root = instrumentation.uiAutomation.rootInActiveWindow
            if (root?.packageName?.toString() == "com.google.android.documentsui") {
                findAccessibilityNode(root, text, contentDescription, viewId)?.let { return it }
            }
            SystemClock.sleep(150)
        }
        throw AssertionError("DocumentsUI picker control did not appear")
    }

    private fun waitForPermissionAction(vararg labels: String): AccessibilityNodeInfo {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val deadline = SystemClock.elapsedRealtime() + 15_000
        while (SystemClock.elapsedRealtime() < deadline) {
            val root = instrumentation.uiAutomation.rootInActiveWindow
            val packageName = root?.packageName?.toString().orEmpty()
            if (packageName.contains("permissioncontroller", ignoreCase = true)) {
                labels.forEach { label ->
                    findAccessibilityNode(root, text = label)?.let { return it }
                }
            }
            SystemClock.sleep(150)
        }
        val root = instrumentation.uiAutomation.rootInActiveWindow
        throw AssertionError(
            "Android calendar permission action ${labels.toList()} did not appear; " +
                "package=${root?.packageName}; ${accessibilitySnapshot(root)}"
        )
    }

    private fun executeShell(command: String): String {
        val descriptor = InstrumentationRegistry.getInstrumentation()
            .uiAutomation.executeShellCommand(command)
        return ParcelFileDescriptor.AutoCloseInputStream(descriptor)
            .bufferedReader().use { it.readText() }
    }

    private fun waitForPickerEditableNode(timeoutMillis: Long = 15_000): AccessibilityNodeInfo {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val deadline = SystemClock.elapsedRealtime() + timeoutMillis
        while (SystemClock.elapsedRealtime() < deadline) {
            val root = instrumentation.uiAutomation.rootInActiveWindow
            if (root?.packageName?.toString() == "com.google.android.documentsui") {
                fun findEditable(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
                    if (node == null) return null
                    if (node.isEditable) return node
                    for (index in 0 until node.childCount) {
                        findEditable(node.getChild(index))?.let { return it }
                    }
                    return null
                }
                findEditable(root)?.let { return it }
            }
            SystemClock.sleep(150)
        }
        throw AssertionError("DocumentsUI filename field did not appear")
    }

    private fun setPickerText(node: AccessibilityNodeInfo, text: String) {
        val arguments = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        }
        assertTrue("DocumentsUI filename field must accept text",
            node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments))
    }

    private fun accessibilitySnapshot(root: AccessibilityNodeInfo?): String {
        val nodes = mutableListOf<String>()
        fun visit(node: AccessibilityNodeInfo?) {
            if (node == null || nodes.size >= 100) return
            val bounds = Rect().also(node::getBoundsInScreen)
            val label = listOfNotNull(
                node.text?.toString()?.takeIf(String::isNotBlank),
                node.contentDescription?.toString()?.takeIf(String::isNotBlank),
                node.viewIdResourceName?.takeIf(String::isNotBlank)
            ).distinct().joinToString("|")
            if (label.isNotEmpty() || node.isClickable) {
                nodes += "${node.className}:$label:click=${node.isClickable}:" +
                    "selected=${node.isSelected}:checked=${node.isChecked}:" +
                    "visible=${node.isVisibleToUser}:actions=${node.actionList.map { it.id }}:$bounds"
            }
            for (index in 0 until node.childCount) visit(node.getChild(index))
        }
        visit(root)
        return nodes.joinToString(" < ")
    }

    private fun tapPickerNode(node: AccessibilityNodeInfo) {
        var clickableAncestor: AccessibilityNodeInfo? = node
        while (clickableAncestor != null) {
            if (clickableAncestor.isClickable &&
                clickableAncestor.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            ) {
                SystemClock.sleep(250)
                return
            }
            clickableAncestor = clickableAncestor.parent
        }
        val bounds = Rect()
        node.getBoundsInScreen(bounds)
        assertFalse("Picker control must have a visible screen rectangle", bounds.isEmpty)
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        val downTime = SystemClock.uptimeMillis()
        val x = bounds.centerX().toFloat()
        val y = bounds.centerY().toFloat()
        val down = MotionEvent.obtain(downTime, downTime, MotionEvent.ACTION_DOWN, x, y, 0).apply {
            source = InputDevice.SOURCE_TOUCHSCREEN
        }
        val up = MotionEvent.obtain(downTime, downTime + 80, MotionEvent.ACTION_UP, x, y, 0).apply {
            source = InputDevice.SOURCE_TOUCHSCREEN
        }
        try {
            assertTrue("Picker touch-down must be injected", automation.injectInputEvent(down, true))
            assertTrue("Picker touch-up must be injected", automation.injectInputEvent(up, true))
        } finally {
            down.recycle()
            up.recycle()
        }
        SystemClock.sleep(250)
    }

    @Test
    fun packagedClearSettingsExposeHostOptionsAndKeepMigrationBehindConfirmation() {
        val context = compose.activity.applicationContext
        val databaseName = "clear-settings-options-${UUID.randomUUID()}.db"
        val store = PowerStore(context, databaseName)
        val skinManager = SkinPackageManager(context)
        val originalSkin = skinManager.currentId()
        val onboarding = context.getSharedPreferences("powercal-onboarding", Context.MODE_PRIVATE)
        val hadAskedForCalendarImport = onboarding.contains("system-calendar-import-asked")
        val askedForCalendarImport = onboarding.getBoolean("system-calendar-import-asked", false)

        try {
            onboarding.edit().putBoolean("system-calendar-import-asked", true).commit()
            PowerCalSkinRegistry.reset()
            skinManager.select("powercal-clear-default")
            compose.runOnUiThread {
                compose.activity.setContent { PowerCalTheme { PowerCalApp(store) } }
            }

            var web: WebView? = null
            compose.waitUntil(30_000) {
                compose.runOnUiThread {
                    web = findWebView(compose.activity.findViewById(android.R.id.content))
                }
                web != null
            }
            val view = requireNotNull(web)
            compose.waitUntil(30_000) {
                evaluate(view, "!!window.PowerCalSkin && !!window.PowerCalSkin.receive") == "true"
            }

            evaluate(view, "document.querySelector('.clear-tabbar [data-action=settings]').click(); true")
            compose.waitUntil(15_000) {
                val state = AtomicReference<String>()
                compose.runOnUiThread { state.set(WebSkinBridgeRuntime.state().optString("page")) }
                state.get() == "SETTINGS"
            }
            val hostOptionCount = AtomicReference<Int>()
            compose.runOnUiThread {
                hostOptionCount.set(WebSkinBridgeRuntime.state().optJSONArray("optionGroups")
                    ?.let { groups -> (0 until groups.length()).sumOf { index ->
                        groups.optJSONObject(index)?.optJSONArray("items")?.length() ?: 0
                    } } ?: 0)
            }
            assertEquals("Host must project all registered calendar/data options", 6, hostOptionCount.get())
            try {
                compose.waitUntil(15_000) {
                    val visible = evaluate(view,
                        "!document.querySelector('#settingsPage').hidden && " +
                            "document.querySelectorAll('#settingsOptionGroups [data-option-id]').length===6")
                    if (visible != "true") SystemClock.sleep(150)
                    visible == "true"
                }
            } catch (error: Throwable) {
                val webState = runCatching {
                    evaluate(view, "JSON.stringify({settings:!!document.querySelector('#settingsPage')," +
                        "hidden:document.querySelector('#settingsPage')?.hidden," +
                        "options:document.querySelectorAll('#settingsOptionGroups [data-option-id]').length," +
                        "pageText:document.querySelector('#settingsPage')?.innerText?.slice(-500)})")
                }.getOrDefault("unavailable")
                throw AssertionError("Host option count=" + hostOptionCount.get() + ", WebView=" + webState, error)
            }
            assertEquals("true", evaluate(view, "[" +
                "'calendar.import.file','calendar.import.system','data.legacy.migrate'," +
                "'calendar.export.ics','data.backup.export','data.backup.restore'" +
                "].every(id=>Array.from(document.querySelectorAll(" +
                "'#settingsOptionGroups [data-option-id]')).some(el=>el.dataset.optionId===id))"))
            assertTrue(evaluate(view,
                "document.querySelector('[data-action=nativeSettings]')?.isConnected===true") == "true")

            val projectedSkins = requireNotNull(WebSkinBridgeRuntime.state().optJSONArray("skinChoices"))
            val projectedSkinIds = (0 until projectedSkins.length()).mapNotNull { index ->
                projectedSkins.optJSONObject(index)?.optString("id")
            }
            assertTrue("Settings must expose the Native recovery skin", "powercal-default" in projectedSkinIds)
            assertTrue("Settings must expose the active Clear skin", "powercal-clear-default" in projectedSkinIds)
            assertEquals("The selected skin is host-projected", "powercal-clear-default",
                WebSkinBridgeRuntime.state().optString("selectedSkinId"))
            assertTrue("The skin projection must contain catalogue metadata only",
                projectedSkins.toString().contains("powercal-clear-default") &&
                    !projectedSkins.toString().contains("path") &&
                    !projectedSkins.toString().contains("endpoint") &&
                    !projectedSkins.toString().contains("apiKey"))
            assertEquals("true", evaluate(view,
                "document.querySelectorAll('#skinChoices [data-action=selectSkin]').length>=2"))
            assertEquals("true", evaluate(view,
                "document.querySelector('#skinChoices [data-skin-id=powercal-clear-default]')?.disabled===true"))
            evaluate(view, "document.querySelector('#skinChoices [data-skin-id=powercal-default]').click(); true")
            compose.waitUntil(15_000) {
                compose.onAllNodesWithText("切换皮肤前请确认").fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithText("取消").performClick()
            compose.waitUntil(10_000) {
                compose.onAllNodesWithText("切换皮肤前请确认").fetchSemanticsNodes().isEmpty()
            }
            assertEquals("Cancelling the Host confirmation must keep Clear active",
                "powercal-clear-default", skinManager.currentId())

            val initialFormal = store.all()
            val initialPending = store.pendingCount()
            val initialCommits = store.commitCount()
            val initialIdentities = store.identityCount()
            val initialSequence = store.nextSequencePreview()
            evaluate(view, "Array.from(document.querySelectorAll(" +
                "'#settingsOptionGroups [data-option-id]')).find(" +
                "el=>el.dataset.optionId==='data.legacy.migrate').click(); true")
            compose.waitUntil(15_000) {
                compose.onAllNodesWithText("迁移旧版事件？").fetchSemanticsNodes().isNotEmpty()
            }

            assertEquals("Opening migration review must not write formal records", initialFormal, store.all())
            assertEquals(initialPending, store.pendingCount())
            assertEquals(initialCommits, store.commitCount())
            assertEquals(initialIdentities, store.identityCount())
            assertEquals(initialSequence, store.nextSequencePreview())

            compose.onNodeWithText("取消").performClick()
            compose.waitUntil(10_000) {
                compose.onAllNodesWithText("迁移旧版事件？").fetchSemanticsNodes().isEmpty()
            }
            assertEquals("SETTINGS", WebSkinBridgeRuntime.state().optString("page"))
            assertEquals(initialFormal, store.all())
            assertEquals(initialPending, store.pendingCount())
            assertEquals(initialCommits, store.commitCount())
            assertEquals(initialIdentities, store.identityCount())
            assertEquals(initialSequence, store.nextSequencePreview())

            evaluate(view, "document.querySelector('#skinChoices [data-skin-id=powercal-default]').click(); true")
            compose.waitUntil(15_000) {
                compose.onAllNodesWithText("切换皮肤前请确认").fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithText("确认切换").performClick()
            compose.waitUntil(15_000) { skinManager.currentId() == "powercal-default" }
            assertEquals("Confirmed activation uses the existing Host package manager",
                "powercal-default", skinManager.currentId())
        } finally {
            compose.runOnUiThread {
                compose.activity.setContent { }
                PowerCalSkinRegistry.reset()
            }
            compose.waitForIdle()
            store.close()
            context.deleteDatabase(databaseName)
            skinManager.select(originalSkin)
            val restoreOnboarding = onboarding.edit()
            if (hadAskedForCalendarImport) {
                restoreOnboarding.putBoolean("system-calendar-import-asked", askedForCalendarImport)
            } else {
                restoreOnboarding.remove("system-calendar-import-asked")
            }
            restoreOnboarding.commit()
        }
    }

    @Test
    fun clearSystemCalendarPermissionDenyAndAllowAreVisibleAndDoNotWriteWhenProviderIsEmpty() {
        assumeTrue("permission decision test requires an isolated Android emulator",
            Build.HARDWARE == "ranchu" || Build.HARDWARE == "goldfish")
        val context = compose.activity.applicationContext
        val permission = Manifest.permission.READ_CALENDAR
        val packageName = context.packageName
        val initialGranted = context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED
        assumeTrue("The AVD must begin with calendar permission denied", !initialGranted)
        fun permissionFlagNames(): Set<String> {
            val dump = executeShell("dumpsys package $packageName")
            val flags = Regex(
                "android\\.permission\\.READ_CALENDAR: granted=(?:true|false), flags=\\[(.*?)]",
                RegexOption.DOT_MATCHES_ALL
            ).find(dump)?.groupValues?.get(1).orEmpty()
            return flags.split('|').map(String::trim).filter(String::isNotEmpty).toSet()
        }
        val initialFlags = permissionFlagNames()
        val initialUserSet = "USER_SET" in initialFlags
        val initialUserFixed = "USER_FIXED" in initialFlags
        val calendarRows = executeShell(
            "content query --uri content://com.android.calendar/calendars --projection _id"
        )
        assumeTrue("The isolated AVD must contain no system calendars or user events",
            calendarRows.contains("No result found", ignoreCase = true))

        val databaseName = "clear-calendar-permission-${UUID.randomUUID()}.db"
        val store = PowerStore(context, databaseName)
        val skinManager = SkinPackageManager(context)
        val originalSkin = skinManager.currentId()
        val onboarding = context.getSharedPreferences("powercal-onboarding", Context.MODE_PRIVATE)
        val hadOnboardingFlag = onboarding.contains("system-calendar-import-asked")
        val originalOnboardingFlag = onboarding.getBoolean("system-calendar-import-asked", false)
        var web: WebView? = null

        fun storeSnapshot() = listOf(
            store.all(), store.pendingCount(), store.commitCount(),
            store.identityCount(), store.nextSequencePreview(),
            store.readableDatabase.rawQuery(
                "SELECT COUNT(*) FROM migrations WHERE source='android-calendar'", null
            ).use { cursor -> cursor.moveToFirst(); cursor.getInt(0) }
        )

        try {
            executeShell("pm clear-permission-flags $packageName $permission user-set user-fixed")
            assertEquals(PackageManager.PERMISSION_DENIED, context.checkSelfPermission(permission))
            onboarding.edit().putBoolean("system-calendar-import-asked", true).commit()
            PowerCalSkinRegistry.reset()
            skinManager.select("powercal-clear-default")
            compose.runOnUiThread {
                compose.activity.setContent { PowerCalTheme { PowerCalApp(store) } }
            }

            compose.waitUntil(30_000) {
                compose.runOnUiThread {
                    web = findWebView(compose.activity.findViewById(android.R.id.content))
                }
                web != null
            }
            val view = requireNotNull(web)
            compose.waitUntil(30_000) {
                evaluate(view, "!!window.PowerCalSkin?.receive") == "true"
            }
            evaluate(view,
                "document.querySelector('.clear-tabbar [data-action=settings]').click(); true")
            compose.waitUntil(15_000) {
                WebSkinBridgeRuntime.state().optString("page") == "SETTINGS"
            }
            compose.waitUntil(15_000) {
                evaluate(view, "Array.from(document.querySelectorAll(" +
                    "'#settingsOptionGroups [data-option-id]')).some(" +
                    "el=>el.dataset.optionId==='calendar.import.system')") == "true"
            }

            val before = storeSnapshot()
            fun requestSystemCalendarImport() {
                evaluate(view, "Array.from(document.querySelectorAll(" +
                    "'#settingsOptionGroups [data-option-id]')).find(" +
                    "el=>el.dataset.optionId==='calendar.import.system').click(); true")
            }
            fun hostNoticeIsVisibleAndAccessible(text: String): Boolean {
                val expected = JSONObject.quote(text)
                return evaluate(view, "(()=>{const e=document.getElementById('hostNotice');" +
                    "return !!e&&e.textContent===$expected&&!e.hidden&&" +
                    "getComputedStyle(e).display!=='none'&&e.getClientRects().length>0&&" +
                    "e.getAttribute('role')==='status'&&e.getAttribute('aria-live')==='polite'})()") == "true"
            }

            requestSystemCalendarImport()
            tapPickerNode(waitForPermissionAction("Don't allow", "Don’t allow", "不允许", "拒绝"))
            compose.waitUntil(10_000) {
                hostNoticeIsVisibleAndAccessible("未授权读取系统日历")
            }
            assertEquals(PackageManager.PERMISSION_DENIED, context.checkSelfPermission(permission))
            assertEquals("Denying system-calendar permission must not query-import or write the Store",
                before, storeSnapshot())

            requestSystemCalendarImport()
            tapPickerNode(waitForPermissionAction("Allow", "允许", "允许访问日历"))
            compose.waitUntil(15_000) {
                context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED &&
                    hostNoticeIsVisibleAndAccessible("系统日历没有可导入的事件")
            }
            assertEquals("The real granted path reads the empty system provider without creating a batch",
                before, storeSnapshot())
        } finally {
            repeat(2) {
                if (InstrumentationRegistry.getInstrumentation().uiAutomation.rootInActiveWindow
                        ?.packageName?.toString()?.contains("permissioncontroller", ignoreCase = true) == true
                ) {
                    InstrumentationRegistry.getInstrumentation().uiAutomation.performGlobalAction(
                        AccessibilityService.GLOBAL_ACTION_BACK
                    )
                    SystemClock.sleep(200)
                }
            }
            compose.runOnUiThread {
                compose.activity.setContent { }
                PowerCalSkinRegistry.reset()
            }
            compose.waitForIdle()
            store.close()
            context.deleteDatabase(databaseName)
            skinManager.select(originalSkin)
            val restoreOnboarding = onboarding.edit()
            if (hadOnboardingFlag) {
                restoreOnboarding.putBoolean("system-calendar-import-asked", originalOnboardingFlag)
            } else {
                restoreOnboarding.remove("system-calendar-import-asked")
            }
            check(restoreOnboarding.commit())

            executeShell("pm clear-permission-flags $packageName $permission user-set user-fixed")
            if (initialUserSet) {
                executeShell("pm set-permission-flags $packageName $permission user-set")
            }
            if (initialUserFixed) {
                executeShell("pm set-permission-flags $packageName $permission user-fixed")
            }
            // Revoking a granted runtime permission kills this package's process, which also
            // hosts the instrumentation runner. The ADB test harness restores grant state after
            // am instrument exits; this test restores only permission decision flags here.
            val finalFlags = permissionFlagNames()
            assertEquals(initialUserSet, "USER_SET" in finalFlags)
            assertEquals(initialUserFixed, "USER_FIXED" in finalFlags)
        }
    }

    @Test
    fun clearSettingsRoutesAiCredentialsAndContextControlsToProtectedHostUi() {
        val context = compose.activity.applicationContext
        val databaseName = "clear-ai-settings-${UUID.randomUUID()}.db"
        val store = PowerStore(context, databaseName)
        val skinManager = SkinPackageManager(context)
        val originalSkin = skinManager.currentId()
        val onboarding = context.getSharedPreferences("powercal-onboarding", Context.MODE_PRIVATE)
        val hadAskedForCalendarImport = onboarding.contains("system-calendar-import-asked")
        val askedForCalendarImport = onboarding.getBoolean("system-calendar-import-asked", false)
        try {
            onboarding.edit().putBoolean("system-calendar-import-asked", true).commit()
            PowerCalSkinRegistry.reset()
            skinManager.select("powercal-clear-default")
            compose.runOnUiThread {
                compose.activity.setContent { PowerCalTheme { PowerCalApp(store) } }
            }

            var web: WebView? = null
            compose.waitUntil(30_000) {
                compose.runOnUiThread {
                    web = findWebView(compose.activity.findViewById(android.R.id.content))
                }
                web != null
            }
            val view = requireNotNull(web)
            compose.waitUntil(30_000) {
                evaluate(view, "!!window.PowerCalSkin && !!window.PowerCalSkin.receive") == "true"
            }
            evaluate(view, "document.querySelector('.clear-tabbar [data-action=settings]').click(); true")
            compose.waitUntil(15_000) {
                WebSkinBridgeRuntime.state().optString("page") == "SETTINGS"
            }
            compose.waitUntil(10_000) {
                evaluate(view, "!!document.querySelector('#openAiSettings')") == "true"
            }

            evaluate(view, "document.querySelector('#openAiSettings').click(); true")
            compose.waitUntil(15_000) {
                compose.onAllNodesWithText("单一 AI").fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithText("配置端点、模型与密钥").performClick()
            compose.waitUntil(10_000) {
                compose.onAllNodesWithText("允许按次共享事件上下文")
                    .fetchSemanticsNodes().isNotEmpty()
            }
            assertTrue(compose.onAllNodesWithText("端点 · https://api.example.com/v1")
                .fetchSemanticsNodes().isNotEmpty())
            assertTrue(compose.onAllNodesWithText("启用 AI").fetchSemanticsNodes().isNotEmpty())
            assertFalse("Credentials must not be projected to the Clear WebView",
                WebSkinBridgeRuntime.state().toString().contains("apiKey"))
        } finally {
            compose.runOnUiThread {
                compose.activity.setContent { }
                PowerCalSkinRegistry.reset()
            }
            compose.waitForIdle()
            store.close()
            context.deleteDatabase(databaseName)
            skinManager.select(originalSkin)
            val restoreOnboarding = onboarding.edit()
            if (hadAskedForCalendarImport) {
                restoreOnboarding.putBoolean("system-calendar-import-asked", askedForCalendarImport)
            } else {
                restoreOnboarding.remove("system-calendar-import-asked")
            }
            restoreOnboarding.commit()
        }
    }

    @Test
    fun clearSkinCatalogUsesHostPickersAndConfirmedDeleteWithoutCalendarWrites() {
        assumeTrue("skin catalogue fixture must run on an isolated Android emulator",
            Build.HARDWARE == "ranchu" || Build.HARDWARE == "goldfish")
        val context = compose.activity.applicationContext
        val databaseName = "clear-skin-catalog-${UUID.randomUUID()}.db"
        val store = PowerStore(context, databaseName)
        val skinManager = SkinPackageManager(context)
        val originalSkin = skinManager.currentId()
        val onboarding = context.getSharedPreferences("powercal-onboarding", Context.MODE_PRIVATE)
        val hadAskedForCalendarImport = onboarding.contains("system-calendar-import-asked")
        val askedForCalendarImport = onboarding.getBoolean("system-calendar-import-asked", false)
        val existingId = "qa-clear-${UUID.randomUUID().toString().replace("-", "")}"
        val importId = "qa-clear-${UUID.randomUUID().toString().replace("-", "")}"
        val existingInput = File(context.cacheDir, "$existingId.pcskin")
        val importInput = File(context.cacheDir, "$importId.pcskin")
        val clearOutput = File(context.cacheDir, "${UUID.randomUUID()}-clear-export.pcskin")
        val installedOutput = File(context.cacheDir, "${UUID.randomUUID()}-installed-export.pcskin")
        val existingArchive = skinPackageFixture(existingId)
        val importArchive = skinPackageFixture(importId)
        var existingInstalled = false
        var importedInstalled = false
        try {
            assumeTrue("test fixture IDs must not already be installed",
                skinManager.installed().none { it.id == existingId || it.id == importId })
            assumeTrue("installed skin capacity must be available",
                skinManager.installed().count { !it.builtIn } < 12)
            assertFalse(clearOutput.exists())
            assertFalse(installedOutput.exists())
            existingInput.writeBytes(existingArchive)
            importInput.writeBytes(importArchive)
            assertEquals(existingId, skinManager.install(Uri.fromFile(existingInput)).id)
            existingInstalled = true
            skinManager.select("powercal-clear-default")
            onboarding.edit().putBoolean("system-calendar-import-asked", true).commit()
            compose.runOnUiThread {
                compose.activity.setContent { PowerCalTheme { PowerCalApp(store) } }
            }

            var web: WebView? = null
            compose.waitUntil(30_000) {
                compose.runOnUiThread {
                    web = findWebView(compose.activity.findViewById(android.R.id.content))
                }
                web != null
            }
            val view = requireNotNull(web)
            compose.waitUntil(30_000) {
                evaluate(view, "!!window.PowerCalSkin && !!window.PowerCalSkin.receive") == "true"
            }
            evaluate(view, "document.querySelector('.clear-tabbar [data-action=settings]').click(); true")
            compose.waitUntil(15_000) {
                WebSkinBridgeRuntime.state().optString("page") == "SETTINGS"
            }
            compose.waitUntil(15_000) {
                evaluate(view, "!!document.querySelector('#skinChoices [data-skin-id=$existingId]')") == "true"
            }

            fun dispatchPickerResult(action: String, uri: Uri, clickExpression: String) {
                val instrumentation = InstrumentationRegistry.getInstrumentation()
                val filter = IntentFilter(action).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    addDataType("*/*")
                }
                val monitor = instrumentation.addMonitor(
                    filter,
                    Instrumentation.ActivityResult(Activity.RESULT_OK, Intent().setData(uri)),
                    true
                )
                try {
                    evaluate(view, "$clickExpression; true")
                    compose.waitUntil(10_000) { monitor.hits > 0 }
                    instrumentation.waitForIdleSync()
                } finally {
                    instrumentation.removeMonitor(monitor)
                }
            }

            dispatchPickerResult(Intent.ACTION_OPEN_DOCUMENT, Uri.fromFile(importInput),
                "document.querySelector('#importSkinButton').click()")
            compose.waitUntil(20_000) { skinManager.installed().any { it.id == importId } }
            importedInstalled = true
            assertEquals("Import never activates the candidate skin",
                "powercal-clear-default", skinManager.currentId())
            compose.waitUntil(15_000) {
                evaluate(view, "!!document.querySelector('#skinChoices [data-skin-id=$importId]')") == "true"
            }

            dispatchPickerResult(Intent.ACTION_CREATE_DOCUMENT, Uri.fromFile(clearOutput),
                "document.querySelector('#exportClearSkinButton').click()")
            compose.waitUntil(20_000) { clearOutput.isFile && clearOutput.length() > 0 }
            val bundledClear = context.assets.open("powercal-clear-default.pcskin").use { it.readBytes() }
            assertTrue("Clear export returns the canonical bundle", bundledClear.contentEquals(clearOutput.readBytes()))

            dispatchPickerResult(Intent.ACTION_CREATE_DOCUMENT, Uri.fromFile(installedOutput),
                "document.querySelector('#skinChoices [data-action=exportInstalledSkin][data-skin-id=$existingId]').click()")
            compose.waitUntil(20_000) { installedOutput.isFile && installedOutput.length() > 0 }
            assertTrue("installed export preserves the original Studio package",
                existingArchive.contentEquals(installedOutput.readBytes()))

            val formalBefore = store.all()
            val commitsBefore = store.commitCount()
            val identitiesBefore = store.identityCount()
            val sequenceBefore = store.nextSequencePreview()
            evaluate(view,
                "document.querySelector('#skinChoices [data-action=deleteSkin][data-skin-id=$existingId]').click()")
            compose.waitUntil(10_000) {
                compose.onAllNodesWithText("删除皮肤？").fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithText("取消").performClick()
            compose.waitUntil(10_000) {
                compose.onAllNodesWithText("删除皮肤？").fetchSemanticsNodes().isEmpty()
            }
            assertTrue("Cancel keeps the third-party archive installed",
                File(context.filesDir, "skin-packs/$existingId.pcskin").isFile)
            evaluate(view,
                "document.querySelector('#skinChoices [data-action=deleteSkin][data-skin-id=$existingId]').click()")
            compose.waitUntil(10_000) {
                compose.onAllNodesWithText("删除皮肤？").fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithText("确认删除").performClick()
            compose.waitUntil(15_000) {
                skinManager.installed().none { it.id == existingId } &&
                    evaluate(view, "!document.querySelector('#skinChoices [data-skin-id=$existingId]')") == "true"
            }
            existingInstalled = false
            assertEquals("Management actions never modify formal events", formalBefore, store.all())
            assertEquals(commitsBefore, store.commitCount())
            assertEquals(identitiesBefore, store.identityCount())
            assertEquals(sequenceBefore, store.nextSequencePreview())
            assertEquals("Deleting another skin leaves active Clear selected",
                "powercal-clear-default", skinManager.currentId())
        } finally {
            compose.runOnUiThread {
                compose.activity.setContent { }
                PowerCalSkinRegistry.reset()
            }
            compose.waitForIdle()
            store.close()
            context.deleteDatabase(databaseName)
            skinManager.select(originalSkin)
            if (existingInstalled) runCatching { skinManager.delete(existingId) }
            if (importedInstalled) runCatching { skinManager.delete(importId) }
            existingInput.delete()
            importInput.delete()
            clearOutput.delete()
            installedOutput.delete()
            val restoreOnboarding = onboarding.edit()
            if (hadAskedForCalendarImport) {
                restoreOnboarding.putBoolean("system-calendar-import-asked", askedForCalendarImport)
            } else {
                restoreOnboarding.remove("system-calendar-import-asked")
            }
            restoreOnboarding.commit()
        }
    }

    @Test
    fun clearInstalledSkinExportUsesDocumentsUiAndPreservesArchive() {
        assumeTrue("installed-skin export must run on an isolated Android emulator",
            Build.HARDWARE == "ranchu" || Build.HARDWARE == "goldfish")
        assumeTrue("Downloads filesystem path requires API 29+", Build.VERSION.SDK_INT >= 29)

        val arguments = InstrumentationRegistry.getArguments()
        val requestedName = arguments.getString("outputName")
        val outputName = requestedName?.takeIf {
            it.matches(Regex("powercal-qa-skin-[a-fA-F0-9-]+\\.pcskin"))
        } ?: "powercal-qa-skin-${UUID.randomUUID()}.pcskin"
        val retainArtifact = arguments.getString("retainArtifact") == "true"
        val outputPath = "/sdcard/Download/$outputName"
        val context = compose.activity.applicationContext
        val databaseName = "clear-skin-saf-export-${UUID.randomUUID()}.db"
        val store = PowerStore(context, databaseName)
        val skinManager = SkinPackageManager(context)
        val skinPreferences = context.getSharedPreferences("powercal-skin-choice", Context.MODE_PRIVATE)
        val hadSkinSelection = skinPreferences.contains("selected")
        val originalSkin = skinPreferences.getString("selected", skinManager.currentId())
            ?: skinManager.currentId()
        val onboarding = context.getSharedPreferences("powercal-onboarding", Context.MODE_PRIVATE)
        val hadOnboardingFlag = onboarding.contains("system-calendar-import-asked")
        val originalOnboardingFlag = onboarding.getBoolean("system-calendar-import-asked", false)
        val installedId = "qa-clear-${UUID.randomUUID().toString().replace("-", "")}"
        val input = File(context.cacheDir, "$installedId.pcskin")
        val expectedArchive = skinPackageFixture(installedId)
        val storeBefore = listOf(
            store.all(), store.pendingCount(), store.commitCount(),
            store.identityCount(), store.nextSequencePreview()
        )
        var installed = false
        var saveActionInvoked = false

        try {
            assertTrue("The generated skin ID must not already be installed",
                skinManager.installed().none { it.id == installedId })
            input.writeBytes(expectedArchive)
            assertEquals(installedId, skinManager.install(Uri.fromFile(input)).id)
            installed = true
            PowerCalSkinRegistry.reset()
            skinManager.select("powercal-clear-default")
            onboarding.edit().putBoolean("system-calendar-import-asked", true).commit()
            compose.runOnUiThread {
                compose.activity.setContent { PowerCalTheme { PowerCalApp(store) } }
            }

            var web: WebView? = null
            compose.waitUntil(30_000) {
                compose.runOnUiThread {
                    web = findWebView(compose.activity.findViewById(android.R.id.content))
                }
                web != null
            }
            val view = requireNotNull(web)
            compose.waitUntil(30_000) {
                evaluate(view, "!!window.PowerCalSkin?.receive") == "true"
            }
            evaluate(view, "document.querySelector(" +
                "'.clear-tabbar [data-action=settings]').click(); true")
            compose.waitUntil(15_000) {
                WebSkinBridgeRuntime.state().optString("page") == "SETTINGS"
            }
            compose.waitUntil(15_000) {
                evaluate(view,
                    "!!document.querySelector('#skinChoices [data-skin-id=$installedId]')") == "true"
            }
            evaluate(view, "document.querySelector(" +
                "'#skinChoices [data-action=exportInstalledSkin][data-skin-id=$installedId]').click(); true")

            val rootsButton = waitForPickerNode(contentDescription = "Show roots")
            assertEquals("Installed skin export must use the real Android DocumentsUI picker",
                "com.google.android.documentsui",
                InstrumentationRegistry.getInstrumentation().uiAutomation.rootInActiveWindow
                    ?.packageName?.toString())
            tapPickerNode(rootsButton)
            tapPickerNode(waitForPickerNode(text = "Downloads"))
            setPickerText(waitForPickerEditableNode(), outputName)

            val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
            val deadline = SystemClock.elapsedRealtime() + 15_000
            var save: AccessibilityNodeInfo? = null
            while (SystemClock.elapsedRealtime() < deadline && save == null) {
                val root = automation.rootInActiveWindow
                save = findAccessibilityNode(root, text = "SAVE")
                    ?: findAccessibilityNode(root, text = "Save")
                    ?: findAccessibilityNode(root, contentDescription = "Save")
                    ?: findAccessibilityNode(root, viewId = "android:id/button1")
                if (save == null) SystemClock.sleep(150)
            }
            assertTrue("DocumentsUI Save action must be available", save != null)
            saveActionInvoked = true
            tapPickerNode(requireNotNull(save))
            compose.waitUntil(30_000) {
                automation.rootInActiveWindow?.packageName?.toString() == compose.activity.packageName
            }

            val descriptor = automation.executeShellCommand("cat $outputPath")
            val exportedArchive = ParcelFileDescriptor.AutoCloseInputStream(descriptor)
                .buffered().use { it.readBytes() }
            assertTrue("The saved .pcskin must preserve the installed archive byte-for-byte",
                expectedArchive.contentEquals(exportedArchive))
            assertEquals("Export must leave Clear selected", "powercal-clear-default",
                skinManager.currentId())
            assertEquals("Export must not mutate calendar data or identity counters", storeBefore,
                listOf(store.all(), store.pendingCount(), store.commitCount(),
                    store.identityCount(), store.nextSequencePreview()))
            val digest = MessageDigest.getInstance("SHA-256").digest(exportedArchive)
                .joinToString("") { "%02x".format(it.toInt() and 0xff) }
            Log.i("ClearSkinSafExport", "artifact=$outputName; bytes=${exportedArchive.size}; sha256=$digest")
        } finally {
            val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
            repeat(2) {
                if (automation.rootInActiveWindow?.packageName?.toString() ==
                    "com.google.android.documentsui"
                ) {
                    automation.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
                    SystemClock.sleep(200)
                }
            }
            if (saveActionInvoked && !retainArtifact) {
                listOf(outputPath, "$outputPath.zip").forEach { path ->
                    val cleanup = automation.executeShellCommand("rm -f $path")
                    ParcelFileDescriptor.AutoCloseInputStream(cleanup).close()
                }
            }
            compose.runOnUiThread {
                compose.activity.setContent { }
                PowerCalSkinRegistry.reset()
            }
            compose.waitForIdle()
            store.close()
            context.deleteDatabase(databaseName)
            if (hadSkinSelection) {
                skinManager.select(originalSkin)
            } else {
                check(skinPreferences.edit().remove("selected").commit())
                PowerCalSkinRegistry.reset()
            }
            if (installed) runCatching { skinManager.delete(installedId) }
            input.delete()
            val restoreOnboarding = onboarding.edit()
            if (hadOnboardingFlag) {
                restoreOnboarding.putBoolean("system-calendar-import-asked", originalOnboardingFlag)
            } else {
                restoreOnboarding.remove("system-calendar-import-asked")
            }
            check(restoreOnboarding.commit())
        }
    }

    @Test
    fun clearSkinImportUsesDocumentsUiAndDoesNotActivateTheCandidate() {
        assumeTrue("DocumentsUI flow must run on an isolated Android emulator",
            Build.HARDWARE == "ranchu" || Build.HARDWARE == "goldfish")
        assumeTrue("Downloads MediaStore requires API 29+", Build.VERSION.SDK_INT >= 29)
        val context = compose.activity.applicationContext
        val databaseName = "clear-skin-saf-${UUID.randomUUID()}.db"
        val store = PowerStore(context, databaseName)
        val skinManager = SkinPackageManager(context)
        val originalSkin = skinManager.currentId()
        val onboarding = context.getSharedPreferences("powercal-onboarding", Context.MODE_PRIVATE)
        val hadAskedForCalendarImport = onboarding.contains("system-calendar-import-asked")
        val askedForCalendarImport = onboarding.getBoolean("system-calendar-import-asked", false)
        val fixtureId = "qa-saf-${UUID.randomUUID().toString().replace("-", "")}"
        val requestedImportName = "powercal-saf-${UUID.randomUUID()}.pcskin"
        val fixtureBytes = skinPackageFixture(fixtureId)
        var importUri: Uri? = null
        var fixtureInstalled = false
        try {
            assumeTrue("test fixture ID must not already be installed",
                skinManager.installed().none { it.id == fixtureId })
            assumeTrue("installed skin capacity must be available",
                skinManager.installed().count { !it.builtIn } < 12)
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, requestedImportName)
                put(MediaStore.MediaColumns.MIME_TYPE, "application/zip")
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/")
            }
            importUri = requireNotNull(context.contentResolver.insert(
                MediaStore.Downloads.EXTERNAL_CONTENT_URI, values
            )) { "Could not create isolated SAF fixture in Downloads" }
            context.contentResolver.openOutputStream(importUri!!)?.use { it.write(fixtureBytes) }
                ?: error("Could not write isolated SAF fixture")
            val pickerName = requireNotNull(context.contentResolver.query(
                importUri!!, arrayOf(MediaStore.MediaColumns.DISPLAY_NAME), null, null, null
            )).use { cursor ->
                assertTrue("Downloads provider must return the isolated fixture row", cursor.moveToFirst())
                cursor.getString(0)
            }
            assertTrue("Downloads provider should retain the package suffix",
                pickerName.startsWith(requestedImportName))

            onboarding.edit().putBoolean("system-calendar-import-asked", true).commit()
            PowerCalSkinRegistry.reset()
            skinManager.select("powercal-clear-default")
            compose.runOnUiThread {
                compose.activity.setContent { PowerCalTheme { PowerCalApp(store) } }
            }
            var web: WebView? = null
            compose.waitUntil(30_000) {
                compose.runOnUiThread {
                    web = findWebView(compose.activity.findViewById(android.R.id.content))
                }
                web != null
            }
            val view = requireNotNull(web)
            compose.waitUntil(30_000) {
                evaluate(view, "!!window.PowerCalSkin && !!window.PowerCalSkin.receive") == "true"
            }
            evaluate(view, "document.querySelector('.clear-tabbar [data-action=settings]').click(); true")
            compose.waitUntil(15_000) { WebSkinBridgeRuntime.state().optString("page") == "SETTINGS" }

            fun storeSnapshot() = listOf(
                store.all(), store.pendingCount(), store.commitCount(),
                store.identityCount(), store.nextSequencePreview()
            )
            val storeBefore = storeSnapshot()
            evaluate(view, "document.querySelector('#importSkinButton').click(); true")
            val rootButton = waitForPickerNode(contentDescription = "Show roots")
            assertEquals("The real Android document provider must own the picker",
                "com.google.android.documentsui",
                InstrumentationRegistry.getInstrumentation().uiAutomation.rootInActiveWindow
                    ?.packageName?.toString())
            tapPickerNode(rootButton)
            tapPickerNode(waitForPickerNode(text = "Downloads"))
            tapPickerNode(waitForPickerNode(text = pickerName))

            val importDeadline = SystemClock.elapsedRealtime() + 20_000
            while (SystemClock.elapsedRealtime() < importDeadline &&
                skinManager.installed().none { it.id == fixtureId }) {
                val root = InstrumentationRegistry.getInstrumentation().uiAutomation.rootInActiveWindow
                if (root?.packageName?.toString() == "com.google.android.documentsui") {
                    val open = findAccessibilityNode(root, text = "OPEN")
                        ?: findAccessibilityNode(root, text = "Open")
                        ?: findAccessibilityNode(root, contentDescription = "Open")
                        ?: findAccessibilityNode(root, viewId = "android:id/button1")
                    if (open != null) tapPickerNode(open)
                }
                SystemClock.sleep(200)
            }
            fixtureInstalled = skinManager.installed().any { it.id == fixtureId }
            val pickerRoot = InstrumentationRegistry.getInstrumentation().uiAutomation.rootInActiveWindow
            val diagnostic = "requested=$requestedImportName actual=$pickerName " +
                "activePackage=${pickerRoot?.packageName} installed=$fixtureInstalled " +
                "screen=${accessibilitySnapshot(pickerRoot)}"
            if (!fixtureInstalled) Log.e("ClearSkinSAF", diagnostic)
            assertTrue(
                "Selecting the Downloads package in DocumentsUI must install it through Host: $diagnostic",
                fixtureInstalled
            )
            assertEquals("A real picker import must not auto-activate its candidate",
                "powercal-clear-default", skinManager.currentId())
            assertEquals("Import must not write calendar data", storeBefore, storeSnapshot())
        } finally {
            val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
            repeat(2) {
                if (automation.rootInActiveWindow?.packageName?.toString() == "com.google.android.documentsui") {
                    automation.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
                    SystemClock.sleep(200)
                }
            }
            importUri?.let { runCatching { context.contentResolver.delete(it, null, null) } }
            compose.runOnUiThread {
                compose.activity.setContent { }
                PowerCalSkinRegistry.reset()
            }
            compose.waitForIdle()
            store.close()
            context.deleteDatabase(databaseName)
            skinManager.select(originalSkin)
            if (fixtureInstalled) runCatching { skinManager.delete(fixtureId) }
            val restoreOnboarding = onboarding.edit()
            if (hadAskedForCalendarImport) {
                restoreOnboarding.putBoolean("system-calendar-import-asked", askedForCalendarImport)
            } else {
                restoreOnboarding.remove("system-calendar-import-asked")
            }
            restoreOnboarding.commit()
        }
    }

    @Test
    fun clearBackupExportUsesDocumentsUiAndVerifiesTheWrittenSnapshot() {
        assumeTrue("DocumentsUI export flow must run on an isolated Android emulator",
            Build.HARDWARE == "ranchu" || Build.HARDWARE == "goldfish")
        assumeTrue("Downloads MediaStore requires API 29+", Build.VERSION.SDK_INT >= 29)
        val context = compose.activity.applicationContext
        val databaseName = "clear-backup-export-${UUID.randomUUID()}.db"
        val store = PowerStore(context, databaseName)
        val skinManager = SkinPackageManager(context)
        val originalSkin = skinManager.currentId()
        val onboarding = context.getSharedPreferences("powercal-onboarding", Context.MODE_PRIVATE)
        val hadAskedForCalendarImport = onboarding.contains("system-calendar-import-asked")
        val askedForCalendarImport = onboarding.getBoolean("system-calendar-import-asked", false)
        val outputName = "powercal-qa-backup-${UUID.randomUUID()}.json"
        val outputPath = "/sdcard/Download/$outputName"
        var saveActionInvoked = false
        val zone = ZoneId.of("Asia/Shanghai")
        val memo = store.newDraft(DraftForm(
            kind = TemporalKind.MEMO,
            title = "CLEAR_BACKUP_EXPORT_FIXTURE",
            startText = LocalDate.now(zone).plusDays(1).toString(),
            zoneId = zone.id,
            trackingEnabled = true
        ))
        assertTrue(store.commit(memo.draftId) is PowerStore.CommitOutcome.Committed)
        val storeBefore = listOf(
            store.all(), store.pendingCount(), store.commitCount(),
            store.identityCount(), store.nextSequencePreview()
        )
        val expectedPayload = BackupEnvelope.verify(store.exportBackup(), PowerStore.VERSION).payload

        try {
            onboarding.edit().putBoolean("system-calendar-import-asked", true).commit()
            PowerCalSkinRegistry.reset()
            skinManager.select("powercal-clear-default")
            compose.runOnUiThread {
                compose.activity.setContent { PowerCalTheme { PowerCalApp(store) } }
            }
            var web: WebView? = null
            compose.waitUntil(30_000) {
                compose.runOnUiThread {
                    web = findWebView(compose.activity.findViewById(android.R.id.content))
                }
                web != null
            }
            val view = requireNotNull(web)
            compose.waitUntil(30_000) {
                evaluate(view, "!!window.PowerCalSkin && !!window.PowerCalSkin.receive") == "true"
            }
            evaluate(view, "document.querySelector('.clear-tabbar [data-action=settings]').click(); true")
            compose.waitUntil(15_000) { WebSkinBridgeRuntime.state().optString("page") == "SETTINGS" }
            evaluate(view, "Array.from(document.querySelectorAll(" +
                "'#settingsOptionGroups [data-option-id]')).find(" +
                "el=>el.dataset.optionId==='data.backup.export').click(); true")

            val rootButton = waitForPickerNode(contentDescription = "Show roots")
            assertEquals("A real Android DocumentsUI Save dialog must own the picker",
                "com.google.android.documentsui",
                InstrumentationRegistry.getInstrumentation().uiAutomation.rootInActiveWindow
                    ?.packageName?.toString())
            tapPickerNode(rootButton)
            tapPickerNode(waitForPickerNode(text = "Downloads"))

            val filenameNode = waitForPickerEditableNode()
            setPickerText(filenameNode, outputName)
            val instrumentation = InstrumentationRegistry.getInstrumentation()
            val saveDeadline = SystemClock.elapsedRealtime() + 15_000
            var saveButton: AccessibilityNodeInfo? = null
            var saveScreen = ""
            while (SystemClock.elapsedRealtime() < saveDeadline && saveButton == null) {
                val root = instrumentation.uiAutomation.rootInActiveWindow
                saveScreen = accessibilitySnapshot(root)
                saveButton = findAccessibilityNode(root, text = "SAVE")
                    ?: findAccessibilityNode(root, text = "Save")
                    ?: findAccessibilityNode(root, contentDescription = "Save")
                    ?: findAccessibilityNode(root, viewId = "android:id/button1")
                if (saveButton == null) SystemClock.sleep(150)
            }
            assertTrue("DocumentsUI must expose its Save action: $saveScreen", saveButton != null)
            assertTrue("Save action must be a positive save button, not Cancel",
                saveButton?.text?.toString().equals("Save", ignoreCase = true) ||
                    saveButton?.contentDescription?.toString().equals("Save", ignoreCase = true))
            println("CLEAR_BACKUP_EXPORT_PICKER_SAVE=" +
                "${saveButton?.text}|${saveButton?.contentDescription}|${saveButton?.viewIdResourceName}")
            tapPickerNode(requireNotNull(saveButton))
            saveActionInvoked = true

            compose.waitUntil(30_000) {
                instrumentation.uiAutomation.rootInActiveWindow?.packageName?.toString() ==
                    compose.activity.packageName
            }
            assertEquals("Backup export must not mutate the source database", storeBefore, listOf(
                store.all(), store.pendingCount(), store.commitCount(),
                store.identityCount(), store.nextSequencePreview()
            ))

            val descriptor = instrumentation.uiAutomation.executeShellCommand("cat $outputPath")
            val savedJson = ParcelFileDescriptor.AutoCloseInputStream(descriptor)
                .bufferedReader(Charsets.UTF_8).use { it.readText() }
            assertTrue("The uniquely named backup must exist in Downloads after saving", savedJson.isNotBlank())
            val verified = BackupEnvelope.verify(JSONObject(savedJson), PowerStore.VERSION)
            assertEquals("The exported snapshot must contain the seeded formal Memo",
                1, verified.payload.getJSONArray("objects").length())
            assertEquals("Downloads backup payload must match the isolated Store snapshot",
                BackupEnvelope.canonicalJson(expectedPayload),
                BackupEnvelope.canonicalJson(verified.payload))
            println("CLEAR_BACKUP_EXPORT_VERIFIED=$outputName; bytes=${savedJson.toByteArray().size}; " +
                "result=SHA-256-and-payload-match")
        } finally {
            val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
            repeat(2) {
                if (automation.rootInActiveWindow?.packageName?.toString() == "com.google.android.documentsui") {
                    automation.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
                    SystemClock.sleep(200)
                }
            }
            if (saveActionInvoked) {
                val cleanup = InstrumentationRegistry.getInstrumentation().uiAutomation
                    .executeShellCommand("rm -f $outputPath")
                ParcelFileDescriptor.AutoCloseInputStream(cleanup).close()
            }
            compose.runOnUiThread {
                compose.activity.setContent { }
                PowerCalSkinRegistry.reset()
            }
            compose.waitForIdle()
            store.close()
            context.deleteDatabase(databaseName)
            skinManager.select(originalSkin)
            val restoreOnboarding = onboarding.edit()
            if (hadAskedForCalendarImport) {
                restoreOnboarding.putBoolean("system-calendar-import-asked", askedForCalendarImport)
            } else {
                restoreOnboarding.remove("system-calendar-import-asked")
            }
            restoreOnboarding.commit()
        }
    }

    @Test
    fun clearBackupRestoreUsesDocumentsUiAndRestoresOnlyTheEmptyIsolatedStore() {
        assumeTrue("DocumentsUI restore flow must run on an isolated Android emulator",
            Build.HARDWARE == "ranchu" || Build.HARDWARE == "goldfish")
        assumeTrue("Downloads MediaStore requires API 29+", Build.VERSION.SDK_INT >= 29)
        val context = compose.activity.applicationContext
        val sourceDatabase = "clear-backup-restore-source-${UUID.randomUUID()}.db"
        val targetDatabase = "clear-backup-restore-target-${UUID.randomUUID()}.db"
        val sourceStore = PowerStore(context, sourceDatabase)
        val store = PowerStore(context, targetDatabase)
        val skinManager = SkinPackageManager(context)
        val originalSkin = skinManager.currentId()
        val onboarding = context.getSharedPreferences("powercal-onboarding", Context.MODE_PRIVATE)
        val hadAskedForCalendarImport = onboarding.contains("system-calendar-import-asked")
        val askedForCalendarImport = onboarding.getBoolean("system-calendar-import-asked", false)
        val fixtureName = "powercal-qa-restore-${UUID.randomUUID()}.json"
        var backupUri: Uri? = null

        try {
            val zone = ZoneId.of("Asia/Shanghai")
            val memo = sourceStore.newDraft(DraftForm(
                kind = TemporalKind.MEMO,
                title = "CLEAR_BACKUP_RESTORE_FIXTURE",
                startText = LocalDate.now(zone).plusDays(2).toString(),
                zoneId = zone.id,
                trackingEnabled = true
            ))
            assertTrue(sourceStore.commit(memo.draftId) is PowerStore.CommitOutcome.Committed)
            val sourceBackup = sourceStore.exportBackup()
            val expectedPayload = BackupEnvelope.verify(sourceBackup, PowerStore.VERSION).payload
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fixtureName)
                put(MediaStore.MediaColumns.MIME_TYPE, "application/json")
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/")
            }
            backupUri = requireNotNull(context.contentResolver.insert(
                MediaStore.Downloads.EXTERNAL_CONTENT_URI, values
            )) { "Could not create isolated backup fixture in Downloads" }
            context.contentResolver.openOutputStream(backupUri!!)?.bufferedWriter(Charsets.UTF_8)?.use {
                it.write(sourceBackup.toString(2))
            } ?: error("Could not write isolated backup fixture")

            val emptyTarget = listOf(
                store.all(), store.pendingCount(), store.commitCount(),
                store.identityCount(), store.nextSequencePreview()
            )
            assertTrue(store.all().isEmpty())
            assertEquals(0, store.pendingCount())
            assertEquals(0L, store.commitCount())
            assertEquals(0L, store.identityCount())

            onboarding.edit().putBoolean("system-calendar-import-asked", true).commit()
            PowerCalSkinRegistry.reset()
            skinManager.select("powercal-clear-default")
            compose.runOnUiThread {
                compose.activity.setContent { PowerCalTheme { PowerCalApp(store) } }
            }
            var web: WebView? = null
            compose.waitUntil(30_000) {
                compose.runOnUiThread {
                    web = findWebView(compose.activity.findViewById(android.R.id.content))
                }
                web != null
            }
            val view = requireNotNull(web)
            compose.waitUntil(30_000) {
                evaluate(view, "!!window.PowerCalSkin && !!window.PowerCalSkin.receive") == "true"
            }
            evaluate(view, "document.querySelector('.clear-tabbar [data-action=settings]').click(); true")
            compose.waitUntil(15_000) { WebSkinBridgeRuntime.state().optString("page") == "SETTINGS" }
            evaluate(view, "Array.from(document.querySelectorAll(" +
                "'#settingsOptionGroups [data-option-id]')).find(" +
                "el=>el.dataset.optionId==='data.backup.restore').click(); true")

            tapPickerNode(waitForPickerNode(contentDescription = "Show roots"))
            tapPickerNode(waitForPickerNode(text = "Downloads"))
            assertEquals("Opening the real picker must not mutate the empty target Store",
                emptyTarget, listOf(
                    store.all(), store.pendingCount(), store.commitCount(),
                    store.identityCount(), store.nextSequencePreview()
                ))
            tapPickerNode(waitForPickerNode(text = fixtureName))
            val instrumentation = InstrumentationRegistry.getInstrumentation()
            val restoreDeadline = SystemClock.elapsedRealtime() + 30_000
            while (SystemClock.elapsedRealtime() < restoreDeadline && store.all().isEmpty()) {
                val root = instrumentation.uiAutomation.rootInActiveWindow
                if (root?.packageName?.toString() == "com.google.android.documentsui") {
                    val open = findAccessibilityNode(root, text = "OPEN")
                        ?: findAccessibilityNode(root, text = "Open")
                        ?: findAccessibilityNode(root, contentDescription = "Open")
                        ?: findAccessibilityNode(root, viewId = "android:id/button1")
                    if (open != null) tapPickerNode(open)
                }
                SystemClock.sleep(150)
            }
            compose.waitUntil(30_000) { store.all().size == 1 }
            val restoredPayload = BackupEnvelope.verify(store.exportBackup(), PowerStore.VERSION).payload
            assertEquals("Restore must preserve all verified logical backup tables",
                BackupEnvelope.canonicalJson(expectedPayload),
                BackupEnvelope.canonicalJson(restoredPayload))
            assertEquals("Restore preserves formal records", sourceStore.all(), store.all())
            assertEquals("Restore preserves pending drafts", sourceStore.pendingCount(), store.pendingCount())
            assertEquals("Restore preserves commit history", sourceStore.commitCount(), store.commitCount())
            assertEquals("Restore preserves persistent identities", sourceStore.identityCount(), store.identityCount())
            assertEquals("Restore preserves the sequence high-water mark",
                sourceStore.nextSequencePreview(), store.nextSequencePreview())
            println("CLEAR_BACKUP_RESTORE_VERIFIED=$fixtureName; objects=" +
                "${store.all().size}; commits=${store.commitCount()}; identities=${store.identityCount()}; " +
                "sequence=${store.nextSequencePreview()}; payload=canonical-match")
        } finally {
            val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
            repeat(2) {
                if (automation.rootInActiveWindow?.packageName?.toString() == "com.google.android.documentsui") {
                    automation.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
                    SystemClock.sleep(200)
                }
            }
            backupUri?.let { runCatching { context.contentResolver.delete(it, null, null) } }
            compose.runOnUiThread {
                compose.activity.setContent { }
                PowerCalSkinRegistry.reset()
            }
            compose.waitForIdle()
            sourceStore.close()
            store.close()
            context.deleteDatabase(sourceDatabase)
            context.deleteDatabase(targetDatabase)
            skinManager.select(originalSkin)
            val restoreOnboarding = onboarding.edit()
            if (hadAskedForCalendarImport) {
                restoreOnboarding.putBoolean("system-calendar-import-asked", askedForCalendarImport)
            } else {
                restoreOnboarding.remove("system-calendar-import-asked")
            }
            restoreOnboarding.commit()
        }
    }

    @Test
    fun clearIcsExportUsesDocumentsUiAndContainsOnlyActivitySnapshots() {
        assumeTrue("DocumentsUI export flow must run on an isolated Android emulator",
            Build.HARDWARE == "ranchu" || Build.HARDWARE == "goldfish")
        assumeTrue("Downloads filesystem path requires API 29+", Build.VERSION.SDK_INT >= 29)

        val context = compose.activity.applicationContext
        val databaseName = "clear-ics-export-${UUID.randomUUID()}.db"
        val store = PowerStore(context, databaseName)
        val skinManager = SkinPackageManager(context)
        val skinPreferences = context.getSharedPreferences("powercal-skin-choice", Context.MODE_PRIVATE)
        val hadSkinSelection = skinPreferences.contains("selected")
        val originalSkin = skinPreferences.getString("selected", skinManager.currentId())
            ?: skinManager.currentId()
        val onboarding = context.getSharedPreferences("powercal-onboarding", Context.MODE_PRIVATE)
        val hadOnboardingFlag = onboarding.contains("system-calendar-import-asked")
        val originalOnboardingFlag = onboarding.getBoolean("system-calendar-import-asked", false)
        val outputName = "powercal-qa-ics-${UUID.randomUUID()}.ics"
        val outputPath = "/sdcard/Download/$outputName"
        val zone = ZoneId.of("Asia/Shanghai")
        val date = LocalDate.of(2035, 3, 4)
        val start = date.atTime(9, 30).atZone(zone).toInstant().toEpochMilli()
        val end = date.atTime(10, 45).atZone(zone).toInstant().toEpochMilli()
        var saveActionInvoked = false

        try {
            fun createFormal(form: DraftForm): Long {
                val result = store.createFormalEvent(form, "clear-ics-${UUID.randomUUID()}")
                assertTrue("ICS fixture must be a committed formal event: $form",
                    result is PowerStore.CommitOutcome.Committed)
                return (result as PowerStore.CommitOutcome.Committed).eventSeq
            }

            val includedDisabledSeq = createFormal(DraftForm(
                kind = TemporalKind.ACTIVITY,
                title = "CLEAR_ICS_ACTIVITY_DISABLED",
                startText = DraftForm.formatInstant(start, zone),
                endText = DraftForm.formatInstant(end, zone),
                enabled = false,
                reminderMinutes = 15,
                zoneId = zone.id
            ))
            createFormal(DraftForm(
                kind = TemporalKind.ACTIVITY,
                title = "CLEAR_ICS_ACTIVITY_ENABLED",
                startText = DraftForm.formatInstant(start + 86_400_000L, zone),
                endText = DraftForm.formatInstant(end + 86_400_000L, zone),
                zoneId = zone.id
            ))
            createFormal(DraftForm(
                kind = TemporalKind.ALARM,
                title = "CLEAR_ICS_EXCLUDED_ALARM",
                startText = DraftForm.formatInstant(start, zone),
                zoneId = zone.id
            ))
            createFormal(DraftForm(
                kind = TemporalKind.DEADLINE,
                title = "CLEAR_ICS_EXCLUDED_DEADLINE",
                endText = date.plusDays(2).toString(),
                zoneId = zone.id
            ))
            createFormal(DraftForm(
                kind = TemporalKind.MEMO,
                title = "CLEAR_ICS_EXCLUDED_MEMO",
                zoneId = zone.id
            ))

            val storeBefore = listOf(
                store.all(), store.pendingCount(), store.commitCount(),
                store.identityCount(), store.nextSequencePreview()
            )

            onboarding.edit().putBoolean("system-calendar-import-asked", true).commit()
            PowerCalSkinRegistry.reset()
            skinManager.select("powercal-clear-default")
            compose.runOnUiThread {
                compose.activity.setContent { PowerCalTheme { PowerCalApp(store) } }
            }

            var web: WebView? = null
            compose.waitUntil(30_000) {
                compose.runOnUiThread {
                    web = findWebView(compose.activity.findViewById(android.R.id.content))
                }
                web != null
            }
            val view = requireNotNull(web)
            compose.waitUntil(30_000) {
                evaluate(view, "!!window.PowerCalSkin?.receive") == "true"
            }
            evaluate(view, "document.querySelector(" +
                "'.clear-tabbar [data-action=settings]').click(); true")
            compose.waitUntil(15_000) {
                WebSkinBridgeRuntime.state().optString("page") == "SETTINGS"
            }
            evaluate(view, "Array.from(document.querySelectorAll(" +
                "'#settingsOptionGroups [data-option-id]')).find(" +
                "item=>item.dataset.optionId==='calendar.export.ics').click(); true")

            val rootsButton = waitForPickerNode(contentDescription = "Show roots")
            assertEquals("The export must use Android's actual DocumentsUI picker",
                "com.google.android.documentsui",
                InstrumentationRegistry.getInstrumentation().uiAutomation.rootInActiveWindow
                    ?.packageName?.toString())
            tapPickerNode(rootsButton)
            tapPickerNode(waitForPickerNode(text = "Downloads"))
            setPickerText(waitForPickerEditableNode(), outputName)

            val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
            val deadline = SystemClock.elapsedRealtime() + 15_000
            var save: AccessibilityNodeInfo? = null
            while (SystemClock.elapsedRealtime() < deadline && save == null) {
                val root = automation.rootInActiveWindow
                save = findAccessibilityNode(root, text = "SAVE")
                    ?: findAccessibilityNode(root, text = "Save")
                    ?: findAccessibilityNode(root, contentDescription = "Save")
                    ?: findAccessibilityNode(root, viewId = "android:id/button1")
                if (save == null) SystemClock.sleep(150)
            }
            assertTrue("DocumentsUI Save action must be available", save != null)
            tapPickerNode(requireNotNull(save))
            saveActionInvoked = true

            compose.waitUntil(30_000) {
                automation.rootInActiveWindow?.packageName?.toString() == compose.activity.packageName
            }
            val descriptor = automation.executeShellCommand("cat $outputPath")
            val ics = ParcelFileDescriptor.AutoCloseInputStream(descriptor)
                .bufferedReader(Charsets.UTF_8).use { it.readText() }
            assertTrue("The uniquely named ICS export must exist", ics.isNotBlank())
            assertTrue("Export must have a VCALENDAR envelope", ics.startsWith("BEGIN:VCALENDAR\r\n"))
            assertTrue("Export must close its VCALENDAR envelope", ics.endsWith("END:VCALENDAR\r\n"))
            assertEquals("ICS output must contain exactly the two formal Activity records",
                2, Regex("BEGIN:VEVENT").findAll(ics).count())
            assertTrue("Disabled Activities remain included; ICS does not preserve enabled state",
                ics.contains("UID:powercal-$includedDisabledSeq@local"))
            assertFalse("ALARM records must not be exported", ics.contains("CLEAR_ICS_EXCLUDED_ALARM"))
            assertFalse("DEADLINE records must not be exported", ics.contains("CLEAR_ICS_EXCLUDED_DEADLINE"))
            assertFalse("MEMO records must not be exported", ics.contains("CLEAR_ICS_EXCLUDED_MEMO"))
            assertFalse("Activity reminders are not represented in this ICS contract",
                ics.contains("BEGIN:VALARM"))

            val parsed = IcsCodec.parse(ics)
            assertTrue("The exported ICS must parse without warnings: ${parsed.warnings}",
                parsed.warnings.isEmpty())
            assertEquals(setOf("CLEAR_ICS_ACTIVITY_DISABLED", "CLEAR_ICS_ACTIVITY_ENABLED"),
                parsed.events.map { it.title }.toSet())
            assertEquals("Exporting a snapshot must not mutate the Store", storeBefore, listOf(
                store.all(), store.pendingCount(), store.commitCount(),
                store.identityCount(), store.nextSequencePreview()
            ))
            println("CLEAR_ICS_EXPORT_VERIFIED=$outputName; eventCount=${parsed.events.size}; " +
                "includedKinds=ACTIVITY; excludedKinds=ALARM,DEADLINE,MEMO; storeUnchanged=true")
        } finally {
            val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
            repeat(2) {
                if (automation.rootInActiveWindow?.packageName?.toString() ==
                    "com.google.android.documentsui"
                ) {
                    automation.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
                    SystemClock.sleep(200)
                }
            }
            if (saveActionInvoked) {
                val cleanup = automation.executeShellCommand("rm -f $outputPath")
                ParcelFileDescriptor.AutoCloseInputStream(cleanup).close()
            }
            compose.runOnUiThread {
                compose.activity.setContent { }
                PowerCalSkinRegistry.reset()
            }
            compose.waitForIdle()
            store.close()
            context.deleteDatabase(databaseName)
            if (hadSkinSelection) {
                skinManager.select(originalSkin)
            } else {
                check(skinPreferences.edit().remove("selected").commit())
                PowerCalSkinRegistry.reset()
            }
            val restoreOnboarding = onboarding.edit()
            if (hadOnboardingFlag) {
                restoreOnboarding.putBoolean("system-calendar-import-asked", originalOnboardingFlag)
            } else {
                restoreOnboarding.remove("system-calendar-import-asked")
            }
            check(restoreOnboarding.commit())
        }
    }

    private fun skinPackageFixture(id: String): ByteArray {
        val original = InstrumentationRegistry.getInstrumentation().context.assets
            .open("skin-delivery-fixture.pcskin").use { it.readBytes() }
        val result = ByteArrayOutputStream()
        ZipInputStream(ByteArrayInputStream(original)).use { input ->
            ZipOutputStream(result).use { output ->
                while (true) {
                    val entry = input.nextEntry ?: break
                    val name = entry.name
                    val source = input.readBytes()
                    input.closeEntry()
                    val content = if (name == "manifest.json") {
                        JSONObject(String(source, Charsets.UTF_8))
                            .put("id", id)
                            .put("name", "Skin Settings QA Fixture")
                            .toString().toByteArray(Charsets.UTF_8)
                    } else source
                    output.putNextEntry(ZipEntry(name))
                    output.write(content)
                    output.closeEntry()
                }
            }
        }
        return result.toByteArray()
    }
}
