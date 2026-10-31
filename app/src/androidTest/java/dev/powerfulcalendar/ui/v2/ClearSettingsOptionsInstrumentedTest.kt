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
import android.content.ContextWrapper
import android.content.ContentUris
import android.database.DatabaseErrorHandler
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.os.Environment
import android.os.SystemClock
import android.provider.CalendarContract
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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.powerfulcalendar.core.BackupEnvelope
import dev.powerfulcalendar.core.DraftForm
import dev.powerfulcalendar.core.PowerStore
import dev.powerfulcalendar.core.TemporalKind
import dev.powerfulcalendar.ai.AiFileFallbackConsent
import dev.powerfulcalendar.ai.AiChatHistoryStore
import dev.powerfulcalendar.ai.AiSettings
import dev.powerfulcalendar.ai.AiSettingsStore
import dev.powerfulcalendar.interop.IcsCodec
import org.json.JSONObject
import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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
import java.net.InetAddress
import java.net.ServerSocket
import kotlin.math.abs
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

    private fun tapWebSelector(web: WebView, selector: String) {
        val probe = JSONObject(evaluate(web, """(() => {
          const target=document.querySelector(${JSONObject.quote(selector)});
          if(!target)return {found:false};
          target.scrollIntoView({block:'center',inline:'nearest'});
          const rect=target.getBoundingClientRect();
          const receipt={down:false,up:false,click:false};
          window.__settingsParityTouch=receipt;
          target.addEventListener('pointerdown',event=>{receipt.down=event.isTrusted},{once:true});
          target.addEventListener('pointerup',event=>{receipt.up=event.isTrusted},{once:true});
          target.addEventListener('click',event=>{receipt.click=event.isTrusted},{once:true});
          return {found:true,x:rect.left+rect.width/2,y:rect.top+rect.height/2,
            cssWidth:document.documentElement.clientWidth,cssHeight:window.innerHeight,
            visible:rect.width>0&&rect.height>0&&rect.bottom>0&&rect.top<innerHeight&&
              rect.right>0&&rect.left<document.documentElement.clientWidth};
        })()"""))
        assertTrue("Clear settings control must be visible in the WebView: $selector $probe",
            probe.optBoolean("found") && probe.optBoolean("visible"))
        val location = IntArray(2)
        val size = AtomicReference<Pair<Int, Int>>()
        compose.runOnUiThread {
            web.getLocationOnScreen(location)
            size.set(web.width to web.height)
        }
        val (widthPx, heightPx) = requireNotNull(size.get())
        val scaleX = widthPx / probe.getDouble("cssWidth")
        val scaleY = heightPx / probe.getDouble("cssHeight")
        assertEquals("Clear touch mapping must preserve WebView aspect ratio", scaleX, scaleY, 0.02)
        val x = location[0] + (probe.getDouble("x") * scaleX).toFloat()
        val y = location[1] + (probe.getDouble("y") * scaleY).toFloat()
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        val downTime = SystemClock.uptimeMillis()
        val down = MotionEvent.obtain(downTime, downTime, MotionEvent.ACTION_DOWN, x, y, 0).apply {
            source = InputDevice.SOURCE_TOUCHSCREEN
        }
        val up = MotionEvent.obtain(downTime, downTime + 80, MotionEvent.ACTION_UP, x, y, 0).apply {
            source = InputDevice.SOURCE_TOUCHSCREEN
        }
        try {
            assertTrue("Clear settings touch-down must be injected",
                automation.injectInputEvent(down, true))
            SystemClock.sleep(80)
            assertTrue("Clear settings touch-up must be injected",
                automation.injectInputEvent(up, true))
        } finally {
            down.recycle()
            up.recycle()
        }
        val deadline = SystemClock.uptimeMillis() + 1_500
        var receipt = JSONObject(evaluate(web, "window.__settingsParityTouch||{}"))
        while (!receipt.optBoolean("click") && SystemClock.uptimeMillis() < deadline) {
            SystemClock.sleep(50)
            receipt = JSONObject(evaluate(web, "window.__settingsParityTouch||{}"))
        }
        assertTrue("Clear control must receive trusted touchscreen events: " +
            "selector=$selector probe=$probe receipt=$receipt",
            receipt.optBoolean("down") && receipt.optBoolean("up") && receipt.optBoolean("click"))
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
    fun nativeAndClearSystemCalendarPermissionDenialLeavesStoresIdentical() {
        assumeTrue("permission parity test requires an isolated Android emulator",
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

        val databaseName = "native-clear-calendar-permission-${UUID.randomUUID()}.db"
        val store = PowerStore(context, databaseName)
        val skinManager = SkinPackageManager(context)
        val originalSkin = skinManager.currentId()
        val onboarding = context.getSharedPreferences("powercal-onboarding", Context.MODE_PRIVATE)
        val hadOnboardingFlag = onboarding.contains("system-calendar-import-asked")
        val originalOnboardingFlag = onboarding.getBoolean("system-calendar-import-asked", false)
        var web: WebView? = null

        fun storeSnapshot() = listOf(
            store.all(), store.draftCount(), store.pendingCount(), store.commitCount(),
            store.identityCount(), store.nextSequencePreview(),
            store.readableDatabase.rawQuery(
                "SELECT COUNT(*) FROM migrations WHERE source='android-calendar'", null
            ).use { cursor -> cursor.moveToFirst(); cursor.getInt(0) }
        )

        try {
            executeShell("pm clear-permission-flags $packageName $permission user-set user-fixed")
            check(context.checkSelfPermission(permission) == PackageManager.PERMISSION_DENIED)
            check(onboarding.edit().putBoolean("system-calendar-import-asked", true).commit())
            PowerCalSkinRegistry.reset()
            skinManager.select("powercal-default")
            compose.runOnUiThread {
                compose.activity.setContent { PowerCalTheme { PowerCalApp(store) } }
            }
            compose.waitUntil(15_000) {
                compose.onAllNodesWithText("时间轴").fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithText("设置").performClick()
            compose.onNodeWithText("工具与设置").assertExists()
            val beforeNative = storeSnapshot()
            compose.onNodeWithText("从系统日历一次性导入")
                .performScrollTo().performClick()
            tapPickerNode(waitForPermissionAction("Don't allow", "Don’t allow", "不允许", "拒绝"))
            compose.waitUntil(10_000) {
                context.checkSelfPermission(permission) == PackageManager.PERMISSION_DENIED
            }
            val afterNativeDenial = storeSnapshot()
            assertEquals("Native denial must leave all Store state unchanged",
                beforeNative, afterNativeDenial)

            executeShell("pm clear-permission-flags $packageName $permission user-set user-fixed")
            check(context.checkSelfPermission(permission) == PackageManager.PERMISSION_DENIED)
            compose.runOnUiThread {
                compose.activity.setContent { }
                PowerCalSkinRegistry.reset()
                skinManager.select("powercal-clear-default")
                compose.activity.setContent { PowerCalTheme { PowerCalApp(store) } }
            }
            compose.waitUntil(30_000) {
                compose.runOnUiThread {
                    web = findWebView(compose.activity.findViewById(android.R.id.content))
                }
                web != null
            }
            val view = requireNotNull(web)
            compose.waitUntil(15_000) {
                evaluate(view, "!!window.PowerCalSkin?.receive") == "true"
            }
            val beforeClear = storeSnapshot()
            assertEquals("Both implementations start the denied request with the same Store state",
                afterNativeDenial, beforeClear)
            compose.waitUntil(10_000) { WebSkinBridgeRuntime.state().isNull("hostNotice") }
            tapWebSelector(view, ".clear-tabbar [data-action=settings]")
            compose.waitUntil(15_000) {
                WebSkinBridgeRuntime.state().optString("page") == "SETTINGS"
            }
            tapWebSelector(view,
                "#settingsOptionGroups [data-option-id='calendar.import.system']")
            tapPickerNode(waitForPermissionAction("Don't allow", "Don’t allow", "不允许", "拒绝"))
            compose.waitUntil(10_000) {
                context.checkSelfPermission(permission) == PackageManager.PERMISSION_DENIED &&
                    evaluate(view, "document.getElementById('hostNotice')?.textContent===" +
                        "${JSONObject.quote("未授权读取系统日历")}") == "true"
            }
            val afterSkinDenial = storeSnapshot()
            assertEquals("Clear denial must leave the same Store state as Native denial",
                beforeClear, afterSkinDenial)
            assertEquals("The paired denial paths must have equal business outcomes",
                beforeNative, afterSkinDenial)
            println("SEP11_NATIVE_CLEAR_SYSTEM_CALENDAR_DENIAL_PARITY " +
                "permission=denied storeUnchanged=true trustedClearTouch=true emptyProvider=true")
        } finally {
            if (InstrumentationRegistry.getInstrumentation().uiAutomation.rootInActiveWindow
                    ?.packageName?.toString()?.contains("permissioncontroller", ignoreCase = true) == true
            ) {
                InstrumentationRegistry.getInstrumentation().uiAutomation.performGlobalAction(
                    AccessibilityService.GLOBAL_ACTION_BACK
                )
                SystemClock.sleep(200)
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
            if (initialUserSet) executeShell("pm set-permission-flags $packageName $permission user-set")
            if (initialUserFixed) executeShell("pm set-permission-flags $packageName $permission user-fixed")
            assertEquals(initialGranted,
                context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED)
        }
    }

    @Test
    fun nativeAndClearSystemCalendarPermissionGrantWithEmptyProviderLeavesStoresIdentical() {
        assumeTrue("permission parity test requires an isolated Android emulator",
            Build.HARDWARE == "ranchu" || Build.HARDWARE == "goldfish")
        val context = compose.activity.applicationContext
        val permission = Manifest.permission.READ_CALENDAR
        val packageName = context.packageName
        val initialGranted = context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED
        assumeTrue("The AVD must begin with calendar permission denied", !initialGranted)
        val calendarRows = executeShell(
            "content query --uri content://com.android.calendar/calendars --projection _id"
        )
        assumeTrue("The isolated AVD must contain no system calendars or user events",
            calendarRows.contains("No result found", ignoreCase = true))

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
        val databaseName = "native-clear-calendar-grant-${UUID.randomUUID()}.db"
        val store = PowerStore(context, databaseName)
        val skinManager = SkinPackageManager(context)
        val originalSkin = skinManager.currentId()
        val onboarding = context.getSharedPreferences("powercal-onboarding", Context.MODE_PRIVATE)
        val hadOnboardingFlag = onboarding.contains("system-calendar-import-asked")
        val originalOnboardingFlag = onboarding.getBoolean("system-calendar-import-asked", false)
        var web: WebView? = null

        fun storeSnapshot() = listOf(
            store.all(), store.draftCount(), store.pendingCount(), store.commitCount(),
            store.identityCount(), store.nextSequencePreview(),
            store.readableDatabase.rawQuery(
                "SELECT COUNT(*) FROM migrations WHERE source='android-calendar'", null
            ).use { cursor -> cursor.moveToFirst(); cursor.getInt(0) }
        )

        try {
            executeShell("pm clear-permission-flags $packageName $permission user-set user-fixed")
            check(context.checkSelfPermission(permission) == PackageManager.PERMISSION_DENIED)
            check(onboarding.edit().putBoolean("system-calendar-import-asked", true).commit())
            PowerCalSkinRegistry.reset()
            skinManager.select("powercal-default")
            compose.runOnUiThread {
                compose.activity.setContent { PowerCalTheme { PowerCalApp(store) } }
            }
            compose.waitUntil(15_000) {
                compose.onAllNodesWithText("时间轴").fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithText("设置").performClick()
            compose.onNodeWithText("工具与设置").assertExists()
            val beforeNative = storeSnapshot()
            compose.onNodeWithText("从系统日历一次性导入").performScrollTo().performClick()
            tapPickerNode(waitForPermissionAction("Allow", "允许", "允许访问日历"))
            compose.waitUntil(15_000) {
                context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED &&
                    compose.onAllNodesWithText("系统日历没有可导入的事件")
                        .fetchSemanticsNodes().isNotEmpty()
            }
            assertEquals("Native grant + empty provider must not write a migration batch",
                beforeNative, storeSnapshot())

            compose.runOnUiThread {
                compose.activity.setContent { }
                PowerCalSkinRegistry.reset()
                skinManager.select("powercal-clear-default")
                compose.activity.setContent { PowerCalTheme { PowerCalApp(store) } }
            }
            compose.waitUntil(30_000) {
                compose.runOnUiThread {
                    web = findWebView(compose.activity.findViewById(android.R.id.content))
                }
                web != null
            }
            val view = requireNotNull(web)
            compose.waitUntil(15_000) {
                evaluate(view, "!!window.PowerCalSkin?.receive") == "true"
            }
            assertEquals("Skin begins with the same state as the completed Native empty import",
                beforeNative, storeSnapshot())
            compose.waitUntil(10_000) { WebSkinBridgeRuntime.state().isNull("hostNotice") }
            tapWebSelector(view, ".clear-tabbar [data-action=settings]")
            compose.waitUntil(15_000) {
                WebSkinBridgeRuntime.state().optString("page") == "SETTINGS"
            }
            tapWebSelector(view,
                "#settingsOptionGroups [data-option-id='calendar.import.system']")
            compose.waitUntil(15_000) {
                context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED &&
                    evaluate(view, "document.getElementById('hostNotice')?.textContent===" +
                        "${JSONObject.quote("系统日历没有可导入的事件")}") == "true"
            }
            assertEquals("Clear grant + empty provider must preserve the identical Store snapshot",
                beforeNative, storeSnapshot())
            println("SEP11_NATIVE_CLEAR_SYSTEM_CALENDAR_EMPTY_GRANT_PARITY " +
                "permission=granted provider=empty storeUnchanged=true trustedClearTouch=true")
        } finally {
            if (InstrumentationRegistry.getInstrumentation().uiAutomation.rootInActiveWindow
                    ?.packageName?.toString()?.contains("permissioncontroller", ignoreCase = true) == true
            ) {
                InstrumentationRegistry.getInstrumentation().uiAutomation.performGlobalAction(
                    AccessibilityService.GLOBAL_ACTION_BACK
                )
                SystemClock.sleep(200)
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
            if (initialUserSet) executeShell("pm set-permission-flags $packageName $permission user-set")
            if (initialUserFixed) executeShell("pm set-permission-flags $packageName $permission user-fixed")
            // Runtime permission revocation kills the in-process instrumentation runner;
            // the connected-test harness restores the initial grant state after am instrument exits.
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
    fun nativeAndClearAiContextPreferenceSaveAndReloadMatchWithoutProjectingSecret() {
        assumeTrue("settings parity test requires an isolated Android emulator",
            Build.HARDWARE == "ranchu" || Build.HARDWARE == "goldfish")
        val context = compose.activity.applicationContext
        val databaseName = "native-clear-ai-settings-${UUID.randomUUID()}.db"
        val store = PowerStore(context, databaseName)
        val skinManager = SkinPackageManager(context)
        val originalSkin = skinManager.currentId()
        val aiPreferences = context.getSharedPreferences("powercal-ai", Context.MODE_PRIVATE)
        val originalAiSettings = AiSettingsStore.load(context)
        val onboarding = context.getSharedPreferences("powercal-onboarding", Context.MODE_PRIVATE)
        val hadOnboardingFlag = onboarding.contains("system-calendar-import-asked")
        val originalOnboardingFlag = onboarding.getBoolean("system-calendar-import-asked", false)
        val secretSentinel = "PC_QA_SECRET_${UUID.randomUUID()}"
        val initialSettings = AiSettings(
            endpoint = "https://settings.example.invalid/v1",
            model = "settings-parity-test",
            apiKey = secretSentinel,
            enabled = true,
            shareContext = false
        )
        val expectedSettings = initialSettings.copy(shareContext = true)
        val initialStoreSnapshot = listOf(
            store.all(), store.draftCount(), store.pendingCount(), store.commitCount(),
            store.identityCount(), store.nextSequencePreview()
        )

        fun shareContextSwitch() = run {
            val label = compose.onNodeWithText("允许按次共享事件上下文")
                .performScrollTo().fetchSemanticsNode()
            val labelCenterY = label.boundsInRoot.center.y
            val switches = compose.onAllNodes(isToggleable()).fetchSemanticsNodes()
            assertTrue("AI settings must expose the context-sharing switch", switches.isNotEmpty())
            val nearest = switches.withIndex().minByOrNull {
                abs(it.value.boundsInRoot.center.y - labelCenterY)
            } ?: throw AssertionError("AI context-sharing switch was not found")
            val verticalDistance = abs(nearest.value.boundsInRoot.center.y - labelCenterY)
            assertTrue("Context label must align with its switch: distance=$verticalDistance",
                verticalDistance < 100f)
            compose.onAllNodes(isToggleable())[nearest.index]
        }

        fun expandAiSettings() {
            compose.onNodeWithText("单一 AI").assertExists()
            val expand = compose.onAllNodesWithText("配置端点、模型与密钥")
                .fetchSemanticsNodes().isNotEmpty()
            if (expand) {
                compose.onNodeWithText("配置端点、模型与密钥").performScrollTo().performClick()
            } else {
                compose.onNodeWithText("收起模型配置").assertExists()
            }
            compose.onNodeWithText("允许按次共享事件上下文").performScrollTo().assertExists()
        }

        fun saveEnabledShareContext() {
            shareContextSwitch().assertIsOff()
            shareContextSwitch().performClick()
            shareContextSwitch().assertIsOn()
            compose.onNodeWithText("保存").performScrollTo().performClick()
            compose.waitUntil(10_000) { AiSettingsStore.load(context) == expectedSettings }
        }

        fun assertSavedSettings() {
            val saved = AiSettingsStore.load(context)
            assertEquals("Endpoint preference", expectedSettings.endpoint, saved.endpoint)
            assertEquals("Model preference", expectedSettings.model, saved.model)
            assertEquals("Enabled preference", expectedSettings.enabled, saved.enabled)
            assertEquals("Context-sharing preference", expectedSettings.shareContext,
                saved.shareContext)
            assertTrue("Credential must remain unchanged in Host storage",
                saved.apiKey == expectedSettings.apiKey)
        }

        fun openNativeSettings() {
            compose.runOnUiThread {
                compose.activity.setContent { }
                PowerCalSkinRegistry.reset()
                skinManager.select("powercal-default")
                compose.activity.setContent { PowerCalTheme { PowerCalApp(store) } }
            }
            compose.waitUntil(15_000) {
                compose.onAllNodesWithText("时间轴").fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithText("设置").performClick()
            compose.onNodeWithText("工具与设置").assertExists()
            expandAiSettings()
        }

        fun openClearSettings() {
            compose.runOnUiThread {
                compose.activity.setContent { }
                PowerCalSkinRegistry.reset()
                if (skinManager.currentId() == "powercal-clear-default") {
                    skinManager.select("powercal-default")
                }
                skinManager.select("powercal-clear-default")
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
            compose.waitUntil(20_000) {
                evaluate(view, "!!window.PowerCalSkin?.receive") == "true"
            }
            tapWebSelector(view, ".clear-tabbar [data-action=settings]")
            compose.waitUntil(15_000) {
                WebSkinBridgeRuntime.state().optString("page") == "SETTINGS"
            }
            tapWebSelector(view, "#openAiSettings")
            compose.waitUntil(15_000) {
                compose.onAllNodesWithText("单一 AI").fetchSemanticsNodes().isNotEmpty()
            }
            expandAiSettings()
        }

        try {
            assertTrue(onboarding.edit().putBoolean("system-calendar-import-asked", true).commit())
            AiSettingsStore.save(context, initialSettings)
            assertTrue(aiPreferences.edit().commit())

            openNativeSettings()
            saveEnabledShareContext()
            assertSavedSettings()
            assertEquals("Settings interaction must not change calendar state",
                initialStoreSnapshot, listOf(store.all(), store.draftCount(), store.pendingCount(),
                    store.commitCount(), store.identityCount(), store.nextSequencePreview()))

            openNativeSettings()
            shareContextSwitch().assertIsOn()

            AiSettingsStore.save(context, initialSettings)
            assertTrue(aiPreferences.edit().commit())
            openClearSettings()
            saveEnabledShareContext()
            assertSavedSettings()
            val webProjection = WebSkinBridgeRuntime.state().toString()
            assertFalse("Clear projection must not expose a credential field",
                webProjection.contains("apiKey"))
            assertFalse("Clear projection must not expose the credential value",
                webProjection.contains(secretSentinel))
            assertEquals("Clear settings interaction must not change calendar state",
                initialStoreSnapshot, listOf(store.all(), store.draftCount(), store.pendingCount(),
                    store.commitCount(), store.identityCount(), store.nextSequencePreview()))

            println("SEP11_NATIVE_CLEAR_AI_CONTEXT_SETTINGS_PARITY " +
                "preference=shareContext:true nativeRemountRestored=true clearSaved=true " +
                "secretProjected=false storeUnchanged=true")
        } finally {
            compose.runOnUiThread {
                compose.activity.setContent { }
                PowerCalSkinRegistry.reset()
            }
            compose.waitForIdle()
            store.close()
            context.deleteDatabase(databaseName)
            skinManager.select(originalSkin)
            AiSettingsStore.save(context, originalAiSettings)
            check(aiPreferences.edit().commit())
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
    fun nativeAndClearAiFileFallbackConsentRevocationMatch() {
        assumeTrue("AI consent parity test requires an isolated Android emulator",
            Build.HARDWARE == "ranchu" || Build.HARDWARE == "goldfish")
        val context = compose.activity.applicationContext
        val databaseName = "native-clear-ai-consent-${UUID.randomUUID()}.db"
        val store = PowerStore(context, databaseName)
        val skinManager = SkinPackageManager(context)
        val originalSkin = skinManager.currentId()
        val originalSettings = AiSettingsStore.load(context)
        val aiPreferences = context.getSharedPreferences("powercal-ai", Context.MODE_PRIVATE)
        val consentPreferences = context.getSharedPreferences(
            "powercal-file-ai-consent", Context.MODE_PRIVATE)
        val hadConsentScope = consentPreferences.contains("provider-scope")
        val originalConsentScope = consentPreferences.getString("provider-scope", null)
        val onboarding = context.getSharedPreferences("powercal-onboarding", Context.MODE_PRIVATE)
        val hadOnboardingFlag = onboarding.contains("system-calendar-import-asked")
        val originalOnboardingFlag = onboarding.getBoolean("system-calendar-import-asked", false)
        val secretSentinel = "PC_QA_CONSENT_SECRET_${UUID.randomUUID()}"
        val settings = AiSettings(
            endpoint = "https://consent.example.invalid/v1",
            model = "consent-revoke-parity-test",
            apiKey = secretSentinel,
            enabled = true,
            shareContext = false
        )

        fun storeSnapshot() = listOf(
            store.all(), store.draftCount(), store.pendingCount(), store.commitCount(),
            store.identityCount(), store.nextSequencePreview()
        )

        fun expandAiSettings() {
            compose.onNodeWithText("单一 AI").assertExists()
            val collapsed = compose.onAllNodesWithText("配置端点、模型与密钥")
                .fetchSemanticsNodes().isNotEmpty()
            if (collapsed) {
                compose.onNodeWithText("配置端点、模型与密钥").performScrollTo().performClick()
            } else {
                compose.onNodeWithText("收起模型配置").assertExists()
            }
            compose.waitUntil(10_000) {
                compose.onAllNodesWithText("撤销文件 AI 自动处理授权")
                    .fetchSemanticsNodes().isNotEmpty()
            }
        }

        fun openNativeAiSettings() {
            compose.runOnUiThread {
                compose.activity.setContent { }
                PowerCalSkinRegistry.reset()
                skinManager.select("powercal-default")
                compose.activity.setContent { PowerCalTheme { PowerCalApp(store) } }
            }
            compose.waitUntil(15_000) {
                compose.onAllNodesWithText("时间轴").fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithText("设置").performClick()
            compose.waitUntil(10_000) {
                compose.onAllNodesWithText("工具与设置").fetchSemanticsNodes().isNotEmpty()
            }
            expandAiSettings()
        }

        fun openClearAiSettings() {
            compose.runOnUiThread {
                compose.activity.setContent { }
                PowerCalSkinRegistry.reset()
                skinManager.select("powercal-clear-default")
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
            compose.waitUntil(20_000) {
                evaluate(view, "!!window.PowerCalSkin?.receive") == "true"
            }
            tapWebSelector(view, ".clear-tabbar [data-action=settings]")
            compose.waitUntil(15_000) {
                WebSkinBridgeRuntime.state().optString("page") == "SETTINGS"
            }
            tapWebSelector(view, "#openAiSettings")
            compose.waitUntil(15_000) {
                compose.onAllNodesWithText("单一 AI").fetchSemanticsNodes().isNotEmpty()
            }
            val projection = WebSkinBridgeRuntime.state().toString()
            assertFalse("AI consent must remain Host-only", projection.contains("consent"))
            assertFalse("AI credential must remain Host-only", projection.contains(secretSentinel))
            expandAiSettings()
        }

        fun revokeAndVerify(label: String) {
            assertTrue("$label must begin with provider-scoped consent",
                AiFileFallbackConsent.allowed(context, settings))
            compose.onNodeWithText("撤销文件 AI 自动处理授权")
                .performScrollTo().performClick()
            compose.waitUntil(10_000) {
                !AiFileFallbackConsent.allowed(context, settings) &&
                    compose.onAllNodesWithText("撤销文件 AI 自动处理授权")
                        .fetchSemanticsNodes().isEmpty()
            }
            assertFalse("$label must revoke the persisted provider-scoped consent",
                AiFileFallbackConsent.allowed(context, settings))
        }

        val beforeStore = storeSnapshot()
        try {
            assertTrue(onboarding.edit().putBoolean("system-calendar-import-asked", true).commit())
            AiSettingsStore.save(context, settings)
            assertTrue(aiPreferences.edit().commit())

            AiFileFallbackConsent.grant(context, settings)
            openNativeAiSettings()
            revokeAndVerify("Native Host settings")
            assertEquals("Native revocation must not write calendar data", beforeStore,
                storeSnapshot())

            AiFileFallbackConsent.grant(context, settings)
            openClearAiSettings()
            revokeAndVerify("Clear → protected Host settings")
            assertEquals("Clear-reachable revocation must not write calendar data", beforeStore,
                storeSnapshot())
            assertEquals("Revocation does not change the configured AI provider", settings,
                AiSettingsStore.load(context))
            assertEquals("Clear Settings must leave the selected skin active",
                "powercal-clear-default", skinManager.currentId())
            println("SEP11_NATIVE_CLEAR_AI_FALLBACK_CONSENT_REVOCATION " +
                "nativeRevoked=true clearHostRevoked=true providerUnchanged=true " +
                "consentHostOnly=true storeUnchanged=true")
        } finally {
            compose.runOnUiThread {
                compose.activity.setContent { }
                PowerCalSkinRegistry.reset()
            }
            compose.waitForIdle()
            store.close()
            context.deleteDatabase(databaseName)
            skinManager.select(originalSkin)
            AiSettingsStore.save(context, originalSettings)
            check(aiPreferences.edit().commit())
            val restoreConsent = consentPreferences.edit()
            if (hadConsentScope && originalConsentScope != null) {
                restoreConsent.putString("provider-scope", originalConsentScope)
            } else {
                restoreConsent.remove("provider-scope")
            }
            check(restoreConsent.commit())
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
    fun nativeAndClearAiFileConsentGrantAndUploadMatchThroughRealDocumentsUi() {
        assumeTrue("AI file consent replay requires an isolated Android emulator",
            Build.HARDWARE == "ranchu" || Build.HARDWARE == "goldfish")
        assumeTrue("Downloads MediaStore requires API 29+", Build.VERSION.SDK_INT >= 29)

        val backing = compose.activity.applicationContext
        val namespace = "pc-ai-file-grant-${UUID.randomUUID()}"
        val context = IsolatedAppContext(compose.activity, backing, namespace)
        val databaseName = "ai-file-grant.db"
        val store = PowerStore(context, databaseName)
        val skinManager = SkinPackageManager(context)
        val onboarding = context.getSharedPreferences("powercal-onboarding", Context.MODE_PRIVATE)
        val sourceText = "PRIVATE_ONLY=REMOVE_BEFORE_UPLOAD; Study group on 2035-03-04 14:00 to 15:00."
        val reviewedText = "Study group on 2035-03-04 14:00 to 15:00."
        val storeBefore = listOf(
            store.all(), store.draftCount(), store.pendingCount(), store.commitCount(),
            store.identityCount(), store.nextSequencePreview()
        )
        val filename = "powercal-ai-review-${UUID.randomUUID()}.txt"
        val fixtureBytes = sourceText.toByteArray(Charsets.UTF_8)
        val fixtureValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
            put(MediaStore.MediaColumns.MIME_TYPE, "text/plain")
            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/")
        }
        var fixtureUri: Uri? = null
        val proposal = JSONObject().put("events", JSONArray().put(JSONObject()
            .put("operation", "CREATE")
            .put("kind", "ACTIVITY")
            .put("title", "Study group")
            .put("start", "2035-03-04 14:00")
            .put("end", "2035-03-04 15:00")
            .put("zoneId", ZoneId.systemDefault().id))).toString()
        val requestBodies = java.util.concurrent.CopyOnWriteArrayList<String>()
        val consentAtProvider = java.util.concurrent.CopyOnWriteArrayList<Boolean>()
        val providerFailure = AtomicReference<Throwable?>()
        val receivedRequests = CountDownLatch(2)

        fun storeSnapshot() = listOf(
            store.all(), store.draftCount(), store.pendingCount(), store.commitCount(),
            store.identityCount(), store.nextSequencePreview()
        )

        fun showSkin(skinId: String) {
            compose.runOnUiThread {
                compose.activity.setContent { }
                PowerCalSkinRegistry.reset()
                skinManager.select(skinId)
                compose.activity.setContent {
                    CompositionLocalProvider(LocalContext provides context) {
                        PowerCalTheme { PowerCalApp(store) }
                    }
                }
            }
            compose.waitForIdle()
        }

        fun pickFileAndWaitForReview(fromClear: Boolean, web: WebView? = null) {
            if (fromClear) {
                val view = requireNotNull(web)
                tapWebSelector(view, ".clear-tabbar [data-action=ai]")
                compose.waitUntil(15_000) {
                    WebSkinBridgeRuntime.state().optString("page") == "AI" &&
                        WebSkinBridgeRuntime.state().optBoolean("chatAttachmentAvailable")
                }
                tapWebSelector(view,
                    ".chat-attachment-controls button[aria-label='选择日历或文档附件']")
            } else {
                compose.waitUntil(15_000) {
                    compose.onAllNodesWithText("AI 助手").fetchSemanticsNodes().isNotEmpty()
                }
                compose.onNodeWithText("AI 助手").performClick()
                compose.onNodeWithContentDescription("添加图片或文件").performClick()
                compose.onNodeWithText("添加 ICS / 文档 / 表格").performClick()
            }

            val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
            val rootsButton = waitForPickerNode(contentDescription = "Show roots")
            assertEquals("The actual Android DocumentsUI must own file selection",
                "com.google.android.documentsui",
                automation.rootInActiveWindow?.packageName?.toString())
            tapPickerNode(rootsButton)
            tapPickerNode(waitForPickerNode(text = "Downloads"))
            tapPickerNode(waitForPickerNode(text = filename))

            val openDeadline = SystemClock.elapsedRealtime() + 20_000
            while (SystemClock.elapsedRealtime() < openDeadline &&
                automation.rootInActiveWindow?.packageName?.toString() ==
                "com.google.android.documentsui"
            ) {
                val root = automation.rootInActiveWindow
                val open = findAccessibilityNode(root, text = "OPEN")
                    ?: findAccessibilityNode(root, text = "Open")
                    ?: findAccessibilityNode(root, contentDescription = "Open")
                    ?: findAccessibilityNode(root, viewId = "android:id/button1")
                if (open != null) tapPickerNode(open) else SystemClock.sleep(150)
            }
            compose.waitUntil(30_000) {
                compose.onAllNodesWithText("核对文件 · $filename")
                    .fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithText("本次将发送的提取文本（可删除隐私内容）").assertExists()
        }

        fun grantAndSubmit(fromClear: Boolean, web: WebView? = null): List<Pair<String, String>> {
            val settings = AiSettingsStore.load(context)
            val requestCountBefore = requestBodies.size
            val before = storeSnapshot()
            assertFalse("Each skin path must begin without remembered file consent",
                AiFileFallbackConsent.allowed(context, settings))
            pickFileAndWaitForReview(fromClear, web)
            assertEquals("Opening and extracting a local file must not write calendar data",
                before, storeSnapshot())
            assertEquals("No Provider call may happen before Host review is submitted",
                requestCountBefore, requestBodies.size)

            compose.onNode(isToggleable()).assertIsOff().performClick().assertIsOn()
            compose.onNode(hasText(sourceText, substring = false) and hasSetTextAction())
                .performTextReplacement(reviewedText)
            compose.onNodeWithText("交给 AI 分析并澄清").performClick()

            compose.waitUntil(30_000) {
                AiFileFallbackConsent.allowed(context, settings) &&
                    requestBodies.size > requestCountBefore
            }
            assertNull("local attachment Provider failed", providerFailure.get())
            assertTrue("consent must already be active when the reviewed text reaches Provider",
                consentAtProvider.getOrNull(requestCountBefore) == true)
            val request = requestBodies[requestCountBefore]
            assertTrue("Provider must receive the user-reviewed text", request.contains(reviewedText))
            assertFalse("removed private text must not reach Provider",
                request.contains("PRIVATE_ONLY=REMOVE_BEFORE_UPLOAD"))
            compose.waitUntil(20_000) {
                AiChatHistoryStore.load(context).any {
                    it.role == "assistant" && it.content.contains("已从文件识别 1 项候选日程")
                }
            }
            assertEquals("Candidate review must remain read-only", before, storeSnapshot())
            val history = AiChatHistoryStore.load(context).map { it.role to it.content }
            assertTrue("extracted source text must not be copied into chat history",
                history.none { it.second.contains(sourceText) || it.second.contains(reviewedText) })
            if (fromClear) {
                val bridge = WebSkinBridgeRuntime.state().toString()
                assertFalse("Clear Bridge must not expose consent", bridge.contains("consent"))
                assertFalse("Clear Bridge must not expose the test credential", bridge.contains("PC_LOCAL_ATTACHMENT_STUB"))
                val proposals = WebSkinBridgeRuntime.state().optJSONObject("aiProposalReview")
                    ?.optJSONArray("proposals")
                assertEquals("Clear must render the same single read-only AI proposal", 1,
                    proposals?.length())
                assertEquals("Clear proposal title must match the Provider candidate",
                    "Study group", proposals?.optJSONObject(0)?.optString("title"))
            } else {
                compose.onNodeWithText("识别到新增 · 是否创建草稿？").assertExists()
                compose.onNodeWithText("Study group · 活动", substring = true).assertExists()
            }
            return history
        }

        try {
            assumeTrue("The isolated provider scope must start without consent",
                !AiFileFallbackConsent.allowed(context, AiSettingsStore.load(context)))
            assertTrue(onboarding.edit().putBoolean("system-calendar-import-asked", true).commit())
            fixtureUri = requireNotNull(backing.contentResolver.insert(
                MediaStore.Downloads.EXTERNAL_CONTENT_URI, fixtureValues
            )) { "Could not create the isolated AI text fixture in Downloads" }
            backing.contentResolver.openOutputStream(fixtureUri!!)?.use { it.write(fixtureBytes) }
                ?: error("Could not write isolated AI text fixture")

            ServerSocket(0, 4, InetAddress.getByName("127.0.0.1")).use { server ->
                server.soTimeout = 30_000
                val settings = AiSettings(
                    endpoint = "http://127.0.0.1:${server.localPort}/v1",
                    model = "local-stub:free",
                    apiKey = "PC_LOCAL_ATTACHMENT_STUB",
                    enabled = true,
                    shareContext = false
                )
                AiSettingsStore.save(context, settings)
                AiFileFallbackConsent.revoke(context)
                val serverThread = Thread {
                    try {
                        repeat(2) {
                            server.accept().use { socket ->
                                socket.soTimeout = 15_000
                                requestBodies += readAttachmentRequest(socket)
                                consentAtProvider += AiFileFallbackConsent.allowed(context, settings)
                                writeAttachmentReply(socket, proposal)
                            }
                            receivedRequests.countDown()
                        }
                    } catch (error: Throwable) {
                        providerFailure.set(error)
                        while (receivedRequests.count > 0) receivedRequests.countDown()
                    }
                }.apply { name = "pc-native-clear-file-consent-stub"; isDaemon = true; start() }

                showSkin("powercal-default")
                val nativeHistory = grantAndSubmit(fromClear = false)
                assertTrue("Native path must remember endpoint/model-scoped consent",
                    AiFileFallbackConsent.allowed(context, settings))
                compose.onNodeWithText("批量拒绝").performClick()
                compose.waitUntil(10_000) {
                    compose.onAllNodesWithText("识别到新增 · 是否创建草稿？")
                        .fetchSemanticsNodes().isEmpty()
                }
                assertEquals("Dismissing the read-only Native proposal must not write data",
                    storeBefore, storeSnapshot())

                AiFileFallbackConsent.revoke(context)
                AiChatHistoryStore.save(context, emptyList())
                showSkin("powercal-clear-default")
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
                val clearHistory = grantAndSubmit(fromClear = true, web = view)
                assertEquals("Native and Clear must append the same file-analysis messages",
                    nativeHistory, clearHistory.takeLast(nativeHistory.size))
                assertTrue("Clear path must remember the same endpoint/model-scoped consent",
                    AiFileFallbackConsent.allowed(context, settings))
                assertTrue("the real DocumentsUI picker must route both uploads to Provider",
                    receivedRequests.await(5, TimeUnit.SECONDS))
                serverThread.join(2_000)
                assertNull("local attachment Provider failed", providerFailure.get())
                assertEquals("Each skin must generate exactly one Stub request", 2,
                    requestBodies.size)
                assertEquals("The Store must stay unchanged through both reviews",
                    storeBefore, storeSnapshot())
                assertTrue("Both requests must follow the user grant",
                    consentAtProvider.size == 2 && consentAtProvider.all { it })
                assertEquals("The synthetic file selection must not stage proposals automatically",
                    0, store.pendingCount())
                assertEquals("Neither skin may write formal events from an AI candidate",
                    0, store.all().size)
                println("SEP11_NATIVE_CLEAR_AI_FILE_CONSENT_GRANT_UPLOAD " +
                    "documentsUi=true nativeUploaded=true clearUploaded=true reviewedTextOnly=true " +
                    "providerScopedConsent=true readOnlyProposal=true storeUnchanged=true")
            }
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
            fixtureUri?.let { runCatching { backing.contentResolver.delete(it, null, null) } }
            compose.runOnUiThread {
                compose.activity.setContent { }
                PowerCalSkinRegistry.reset()
            }
            compose.waitForIdle()
            store.close()
            context.deleteDatabase(databaseName)
            listOf("powercal-ai", "powercal-onboarding", "powercal-ui", "powercal-skin-choice",
                "powercal-profile", "powercal-file-ai-consent")
                .forEach { name -> backing.deleteSharedPreferences("$namespace-$name") }
            File(backing.cacheDir, namespace).deleteRecursively()
        }
    }

    @Test
    fun nativeAndClearProtectedProfileSaveMatchThroughHostTools() {
        assumeTrue("profile parity test requires an isolated Android emulator",
            Build.HARDWARE == "ranchu" || Build.HARDWARE == "goldfish")
        val context = compose.activity.applicationContext
        val databaseName = "native-clear-profile-${UUID.randomUUID()}.db"
        val store = PowerStore(context, databaseName)
        val skinManager = SkinPackageManager(context)
        val originalSkin = skinManager.currentId()
        val profile = context.getSharedPreferences("powercal-profile", Context.MODE_PRIVATE)
        val hadDisplayName = profile.contains("displayName")
        val originalDisplayName = profile.getString("displayName", null)
        val onboarding = context.getSharedPreferences("powercal-onboarding", Context.MODE_PRIVATE)
        val hadOnboardingFlag = onboarding.contains("system-calendar-import-asked")
        val originalOnboardingFlag = onboarding.getBoolean("system-calendar-import-asked", false)
        val baseName = "PC Profile Base ${UUID.randomUUID()}"
        val savedName = "PC Profile Saved ${UUID.randomUUID()}"
        val beforeStore = listOf(
            store.all(), store.draftCount(), store.pendingCount(), store.commitCount(),
            store.identityCount(), store.nextSequencePreview()
        )

        fun setBaseProfile() {
            assertTrue(profile.edit().putString("displayName", baseName).commit())
        }

        fun profileNameField() = compose.onNode(hasSetTextAction())

        fun saveProfileFromHostTools() {
            compose.onNodeWithText("个人信息").assertExists()
            val nameField = profileNameField()
            assertEquals(baseName, nameField.fetchSemanticsNode()
                .config[SemanticsProperties.EditableText].text)
            nameField.performTextReplacement(savedName)
            compose.onNodeWithText("保存个人信息").performScrollTo().performClick()
            compose.waitUntil(10_000) { profile.getString("displayName", null) == savedName }
            assertEquals("Profile form must leave calendar data unchanged", beforeStore, listOf(
                store.all(), store.draftCount(), store.pendingCount(), store.commitCount(),
                store.identityCount(), store.nextSequencePreview()
            ))
        }

        fun openNativeTools() {
            compose.runOnUiThread {
                compose.activity.setContent { }
                PowerCalSkinRegistry.reset()
                skinManager.select("powercal-default")
                compose.activity.setContent { PowerCalTheme { PowerCalApp(store) } }
            }
            compose.waitUntil(15_000) {
                compose.onAllNodesWithText("时间轴").fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithText("设置").performClick()
            compose.waitUntil(10_000) {
                compose.onAllNodesWithText("工具与设置").fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithText("个人信息").assertExists()
        }

        fun openClearNativeRecoveryTools() {
            compose.runOnUiThread {
                compose.activity.setContent { }
                PowerCalSkinRegistry.reset()
                skinManager.select("powercal-clear-default")
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
            compose.waitUntil(20_000) {
                evaluate(view, "!!window.PowerCalSkin?.receive") == "true"
            }
            tapWebSelector(view, ".clear-tabbar [data-action=settings]")
            compose.waitUntil(15_000) {
                WebSkinBridgeRuntime.state().optString("page") == "SETTINGS"
            }
            tapWebSelector(view, "#nativeRecoverySettings")
            compose.waitUntil(15_000) {
                compose.onAllNodesWithText("个人信息").fetchSemanticsNodes().isNotEmpty()
            }
            assertEquals("Clear selection remains active in the explicit Native recovery tools",
                "powercal-clear-default", skinManager.currentId())
            val projection = WebSkinBridgeRuntime.state().toString()
            assertFalse("Protected profile value must not be projected into Bridge state",
                projection.contains(baseName) || projection.contains(savedName) ||
                    projection.contains("displayName"))
        }

        try {
            assertTrue(onboarding.edit().putBoolean("system-calendar-import-asked", true).commit())
            setBaseProfile()
            openNativeTools()
            saveProfileFromHostTools()

            setBaseProfile()
            openClearNativeRecoveryTools()
            saveProfileFromHostTools()
            assertEquals("Native and Clear-reachable Host tools save the same profile value",
                savedName, profile.getString("displayName", null))
            println("SEP11_NATIVE_CLEAR_PROFILE_SETTINGS_PARITY nativeSaved=true " +
                "clearRecoverySaved=true valueHostOwned=true selectedSkinPreserved=true " +
                "storeUnchanged=true")
        } finally {
            compose.runOnUiThread {
                compose.activity.setContent { }
                PowerCalSkinRegistry.reset()
            }
            compose.waitForIdle()
            store.close()
            context.deleteDatabase(databaseName)
            skinManager.select(originalSkin)
            val restoreProfile = profile.edit()
            if (hadDisplayName && originalDisplayName != null) {
                restoreProfile.putString("displayName", originalDisplayName)
            } else {
                restoreProfile.remove("displayName")
            }
            check(restoreProfile.commit())
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
    fun nativeAndClearAuthoredProfileEditorSaveMatchWithScopedBridgeProjection() {
        assumeTrue("profile parity test requires an isolated Android emulator",
            Build.HARDWARE == "ranchu" || Build.HARDWARE == "goldfish")
        val context = compose.activity.applicationContext
        val databaseName = "native-clear-authored-profile-" + UUID.randomUUID() + ".db"
        val store = PowerStore(context, databaseName)
        val skinManager = SkinPackageManager(context)
        val originalSkin = skinManager.currentId()
        val profile = context.getSharedPreferences("powercal-profile", Context.MODE_PRIVATE)
        val hadDisplayName = profile.contains("displayName")
        val originalDisplayName = profile.getString("displayName", null)
        val onboarding = context.getSharedPreferences("powercal-onboarding", Context.MODE_PRIVATE)
        val hadOnboardingFlag = onboarding.contains("system-calendar-import-asked")
        val originalOnboardingFlag = onboarding.getBoolean("system-calendar-import-asked", false)
        val baseName = "PCProfileBase" + UUID.randomUUID().toString().replace("-", "")
        val savedName = "PCProfileSaved" + UUID.randomUUID().toString().replace("-", "")
        val storeBefore = listOf(
            store.all(), store.draftCount(), store.pendingCount(), store.commitCount(),
            store.identityCount(), store.nextSequencePreview()
        )
        var web: WebView? = null

        fun setProfileName(value: String) {
            assertTrue(profile.edit().putString("displayName", value).commit())
        }

        fun saveNativeProfile() {
            compose.runOnUiThread {
                compose.activity.setContent { }
                PowerCalSkinRegistry.reset()
                skinManager.select("powercal-default")
                compose.activity.setContent { PowerCalTheme { PowerCalApp(store) } }
            }
            compose.waitUntil(15_000) {
                compose.onAllNodesWithText("时间轴").fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithText("设置").performClick()
            compose.waitUntil(10_000) {
                compose.onAllNodesWithText("工具与设置").fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithText("个人信息").assertExists()
            val field = compose.onNode(hasSetTextAction())
            assertEquals(baseName, field.fetchSemanticsNode()
                .config[SemanticsProperties.EditableText].text)
            field.performTextReplacement(savedName)
            compose.onNodeWithText("保存个人信息").performScrollTo().performClick()
            compose.waitUntil(10_000) { profile.getString("displayName", null) == savedName }
        }

        try {
            assertTrue(onboarding.edit().putBoolean("system-calendar-import-asked", true).commit())
            setProfileName(baseName)
            saveNativeProfile()
            setProfileName(baseName)
            compose.runOnUiThread {
                compose.activity.setContent { }
                PowerCalSkinRegistry.reset()
                skinManager.select("powercal-clear-default")
                compose.activity.setContent { PowerCalTheme { PowerCalApp(store) } }
            }
            compose.waitUntil(30_000) {
                compose.runOnUiThread {
                    web = findWebView(compose.activity.findViewById(android.R.id.content))
                }
                web != null
            }
            val view = requireNotNull(web)
            compose.waitUntil(20_000) {
                evaluate(view, "!!window.PowerCalSkin?.receive") == "true"
            }
            tapWebSelector(view, ".clear-tabbar [data-action=settings]")
            compose.waitUntil(15_000) {
                WebSkinBridgeRuntime.state().optString("page") == "SETTINGS"
            }
            val closedProjection = WebSkinBridgeRuntime.state()
            assertTrue("Clear Bridge must omit profile data until the user opens its editor",
                closedProjection.isNull("profileEditor") &&
                    !closedProjection.toString().contains(baseName))

            tapWebSelector(view, "#openProfileSettings")
            compose.waitUntil(10_000) {
                val profileState = WebSkinBridgeRuntime.state().optJSONObject("profileEditor")
                profileState?.optString("displayName") == baseName &&
                    evaluate(view, "document.querySelector('#profileEditorDialog').open") == "true"
            }
            assertEquals("The opened skin-owned form receives only the stored display name",
                baseName, WebSkinBridgeRuntime.state().getJSONObject("profileEditor")
                    .getString("displayName"))
            assertEquals("Profile input keeps the Native 80-character contract",
                "true", evaluate(view,
                    "Number(document.querySelector('#profileDisplayName').getAttribute('maxlength'))===80"))
            assertTrue("The editor must expose an accessible label and status",
                evaluate(view, "document.querySelector('label[for=profileDisplayName]')!==null&&" +
                    "document.querySelector('#profileEditorStatus').getAttribute('aria-live')==='polite'") ==
                    "true")
            val savedLiteral = JSONObject.quote(savedName)
            assertEquals("Test input edits the actual Clear form field",
                "true", evaluate(view, "(()=>{const input=document.querySelector('#profileDisplayName');" +
                    "input.value=$savedLiteral;input.dispatchEvent(new Event('input',{bubbles:true}));" +
                    "return input.value===$savedLiteral})()"))
            tapWebSelector(view, "#profileSaveButton")
            compose.waitUntil(10_000) {
                profile.getString("displayName", null) == savedName &&
                    WebSkinBridgeRuntime.state().optJSONObject("profileEditor")
                        ?.optBoolean("saved") == true
            }
            assertEquals("Native and Clear forms persist the same profile value",
                savedName, profile.getString("displayName", null))
            assertEquals("Skin profile editing must not write calendar data",
                storeBefore, listOf(
                    store.all(), store.draftCount(), store.pendingCount(), store.commitCount(),
                    store.identityCount(), store.nextSequencePreview()
                ))

            evaluate(view, "(()=>{const input=document.querySelector('#profileDisplayName');" +
                "input.value='UNSAVED_PROFILE_VALUE';input.dispatchEvent(new Event('input',{bubbles:true}));" +
                "return true})()")
            InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(
                android.view.KeyEvent.KEYCODE_BACK
            )
            compose.waitUntil(10_000) {
                WebSkinBridgeRuntime.state().isNull("profileEditor") &&
                    evaluate(view, "document.querySelector('#profileEditorDialog').open") == "false"
            }
            assertEquals("Android Back closes the skin editor without saving later edits",
                savedName, profile.getString("displayName", null))
            assertFalse("Closed profile values must be removed from the Bridge frame",
                WebSkinBridgeRuntime.state().toString().contains(savedName) ||
                    WebSkinBridgeRuntime.state().toString().contains("UNSAVED_PROFILE_VALUE"))
            tapWebSelector(view, "#skinChoices [data-skin-id=powercal-default]")
            compose.waitUntil(15_000) {
                compose.onAllNodesWithText("切换皮肤前请确认").fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithText("确认切换").performClick()
            compose.waitUntil(15_000) {
                skinManager.currentId() == "powercal-default" &&
                    WebSkinBridgeRuntime.state().isNull("profileEditor")
            }
            assertFalse("Changing the active skin must not retain the prior profile projection",
                WebSkinBridgeRuntime.state().toString().contains(savedName))
            println("SEP11_NATIVE_CLEAR_AUTHORED_PROFILE_EDITOR " +
                "nativeSaved=true clearSkinSaved=true valueOnlyWhileOpen=true backDiscardsDraft=true " +
                "skinSwitchClearsProjection=true storeUnchanged=true")
        } finally {
            compose.runOnUiThread {
                compose.activity.setContent { }
                PowerCalSkinRegistry.reset()
            }
            compose.waitForIdle()
            store.close()
            context.deleteDatabase(databaseName)
            skinManager.select(originalSkin)
            val restoreProfile = profile.edit()
            if (hadDisplayName && originalDisplayName != null) {
                restoreProfile.putString("displayName", originalDisplayName)
            } else {
                restoreProfile.remove("displayName")
            }
            check(restoreProfile.commit())
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
    fun clearHostAiSettingsBackRestoresSelectedSkinTimeline() {
        assumeTrue("navigation parity test requires an isolated Android emulator",
            Build.HARDWARE == "ranchu" || Build.HARDWARE == "goldfish")
        val context = compose.activity.applicationContext
        val databaseName = "clear-ai-settings-back-${UUID.randomUUID()}.db"
        val store = PowerStore(context, databaseName)
        val skinManager = SkinPackageManager(context)
        val originalSkin = skinManager.currentId()
        val onboarding = context.getSharedPreferences("powercal-onboarding", Context.MODE_PRIVATE)
        val hadOnboardingFlag = onboarding.contains("system-calendar-import-asked")
        val originalOnboardingFlag = onboarding.getBoolean("system-calendar-import-asked", false)
        var web: WebView? = null

        fun storeSnapshot() = listOf(
            store.all(), store.draftCount(), store.pendingCount(), store.commitCount(),
            store.identityCount(), store.nextSequencePreview()
        )

        val before = storeSnapshot()
        try {
            assertTrue(onboarding.edit().putBoolean("system-calendar-import-asked", true).commit())
            PowerCalSkinRegistry.reset()
            skinManager.select("powercal-default")
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
            val clearTimeline = requireNotNull(web)
            compose.waitUntil(20_000) {
                evaluate(clearTimeline, "!!window.PowerCalSkin?.receive && " +
                    "!document.querySelector('#timelinePage')?.hidden") == "true"
            }
            tapWebSelector(clearTimeline, ".clear-tabbar [data-action=settings]")
            compose.waitUntil(15_000) {
                WebSkinBridgeRuntime.state().optString("page") == "SETTINGS"
            }
            tapWebSelector(clearTimeline, "#openAiSettings")
            compose.waitUntil(15_000) {
                compose.onAllNodesWithText("单一 AI").fetchSemanticsNodes().isNotEmpty()
            }
            assertEquals("Host AI Settings opens the Native Tools page", 1,
                compose.onAllNodesWithText("工具与设置").fetchSemanticsNodes().size)

            compose.onNodeWithContentDescription("返回时间轴").performClick()
            var restored = false
            try {
                compose.waitUntil(15_000) {
                    compose.runOnUiThread {
                        web = findWebView(compose.activity.findViewById(android.R.id.content))
                    }
                    val candidate = web
                    if (candidate == null) false else {
                        val attached = AtomicReference(false)
                        compose.runOnUiThread { attached.set(candidate.isAttachedToWindow) }
                        attached.get() && evaluate(candidate,
                            "!!window.PowerCalSkin?.receive && " +
                                "!document.querySelector('#timelinePage')?.hidden") == "true"
                    }
                }
                restored = true
            } catch (_: androidx.compose.ui.test.ComposeTimeoutException) {
                val activeSkin = PowerCalSkinRegistry.active.key
                val entrypoint = PowerCalSkinRegistry.active.web?.entrypoint ?: "<no-web-entrypoint>"
                val page = WebSkinBridgeRuntime.state().optString("page", "<missing>")
                val attached = AtomicReference(false)
                web?.let { candidate ->
                    compose.runOnUiThread { attached.set(candidate.isAttachedToWindow) }
                }
                println("SEP12_CLEAR_HOST_AI_BACK_DIAGNOSTIC " +
                    "selectedSkin=${skinManager.currentId()} activeSkin=$activeSkin " +
                    "entrypoint=$entrypoint hostPage=$page webViewFound=${web != null} " +
                    "webViewAttached=${attached.get()}")
            }
            assertTrue("Host AI Settings Back must restore the selected Clear timeline",
                restored)
            assertEquals("Host route/back must not create or modify calendar business data",
                before, storeSnapshot())
            assertEquals("Returning from protected Host settings retains the selected skin",
                "powercal-clear-default", skinManager.currentId())
            println("SEP12_CLEAR_HOST_AI_SETTINGS_BACK_PARITY selectedSkin=" +
                "${skinManager.currentId()} trustedEntry=true timelineRestored=true " +
                "storeUnchanged=true")
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
            if (hadOnboardingFlag) {
                restoreOnboarding.putBoolean("system-calendar-import-asked", originalOnboardingFlag)
            } else {
                restoreOnboarding.remove("system-calendar-import-asked")
            }
            check(restoreOnboarding.commit())
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

    @Test
    fun nativeAndClearImportTheSameRealSystemCalendarEventIntoEquivalentStores() {
        assumeTrue("system calendar provider parity requires an isolated Android emulator",
            Build.HARDWARE == "ranchu" || Build.HARDWARE == "goldfish")
        val context = compose.activity.applicationContext
        assumeTrue("the app must have the existing READ_CALENDAR grant",
            context.checkSelfPermission(Manifest.permission.READ_CALENDAR) ==
                PackageManager.PERMISSION_GRANTED)
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        val resolver = context.contentResolver
        fun <T> withCalendarProviderAccess(block: () -> T): T {
            automation.adoptShellPermissionIdentity(
                Manifest.permission.READ_CALENDAR,
                Manifest.permission.WRITE_CALENDAR
            )
            return try { block() } finally { automation.dropShellPermissionIdentity() }
        }
        fun syncAdapterUri(uri: Uri, accountName: String): Uri = uri.buildUpon()
            .appendQueryParameter(CalendarContract.CALLER_IS_SYNCADAPTER, "true")
            .appendQueryParameter(CalendarContract.Calendars.ACCOUNT_NAME, accountName)
            .appendQueryParameter(CalendarContract.Calendars.ACCOUNT_TYPE,
                CalendarContract.ACCOUNT_TYPE_LOCAL)
            .build()
        fun providerRowCount(uri: Uri): Int = resolver.query(
            uri, arrayOf(CalendarContract.Calendars._ID), null, null, null
        )?.use { it.count } ?: 0

        val calendarsBefore = withCalendarProviderAccess {
            providerRowCount(CalendarContract.Calendars.CONTENT_URI)
        }
        val eventsBefore = withCalendarProviderAccess {
            resolver.query(CalendarContract.Events.CONTENT_URI,
                arrayOf(CalendarContract.Events._ID), null, null, null)?.use { it.count } ?: 0
        }
        assumeTrue("the API 35 fixture emulator must have no pre-existing calendar data",
            calendarsBefore == 0 && eventsBefore == 0)

        val fixtureId = UUID.randomUUID().toString().replace("-", "")
        val accountName = "powercal-sep11-$fixtureId"
        val title = "PowerCal SEP11 synthetic import $fixtureId"
        val notes = "PowerCal isolated system-calendar fixture"
        val location = "QA fixture only"
        val zone = ZoneId.systemDefault()
        val startMillis = LocalDate.now(zone).plusDays(10).atTime(10, 30)
            .atZone(zone).toInstant().toEpochMilli()
        val endMillis = startMillis + 45 * 60_000L
        val nativeDatabase = "pc-sep11-native-calendar-$fixtureId.db"
        val clearDatabase = "pc-sep11-clear-calendar-$fixtureId.db"
        val skinManager = SkinPackageManager(context)
        val originalSkin = skinManager.currentId()
        val onboarding = context.getSharedPreferences("powercal-onboarding", Context.MODE_PRIVATE)
        val hadOnboarding = onboarding.contains("system-calendar-import-asked")
        val originalOnboarding = onboarding.getBoolean("system-calendar-import-asked", false)
        var calendarId: Long? = null
        var eventId: Long? = null
        var nativeStore: PowerStore? = null
        var clearStore: PowerStore? = null
        var web: WebView? = null

        fun systemImportBatches(store: PowerStore): List<Pair<String, Triple<Int, Int, List<String>>>> =
            store.readableDatabase.rawQuery(
                "SELECT batch_id FROM migrations WHERE source=? ORDER BY started_ms ASC",
                arrayOf("android-calendar")
            ).use { cursor ->
                buildList {
                    while (cursor.moveToNext()) {
                        val id = cursor.getString(0)
                        add(id to requireNotNull(store.migrationSummary(id)))
                    }
                }
            }

        fun hasFinishedImportedBatch(store: PowerStore): Boolean {
            val id = systemImportBatches(store).firstOrNull { it.second.first == 1 }?.first
                ?: return false
            return store.readableDatabase.rawQuery(
                "SELECT finished_ms FROM migrations WHERE batch_id=?", arrayOf(id)
            ).use { cursor -> cursor.moveToFirst() && !cursor.isNull(0) }
        }

        fun assertImported(store: PowerStore) {
            val externalId = "${requireNotNull(eventId)}:$startMillis"
            val records = store.all()
            assertEquals("The empty isolated Store must receive exactly the fixture event",
                1, records.size)
            val record = records.single()
            assertEquals(title, record.title)
            assertEquals(notes, record.notes)
            assertEquals(location, record.location)
            assertEquals(TemporalKind.ACTIVITY, record.kind)
            assertEquals(startMillis, record.startMillis)
            assertEquals(endMillis, record.endMillis)
            assertEquals("android-calendar", record.source)
            assertEquals(record.eventSeq, store.sourceLink("android-calendar", externalId))
            assertEquals(1L, store.identityCount())
            assertEquals(2L, store.nextSequencePreview())
            assertEquals(1, store.commitCount())
            assertEquals(0, store.pendingCount())
            val batches = systemImportBatches(store)
            assertEquals(1, batches.size)
            assertEquals(Triple(1, 0, emptyList<String>()), batches.first().second)
            val finished = store.readableDatabase.rawQuery(
                "SELECT finished_ms FROM migrations WHERE batch_id=?",
                arrayOf(batches.first().first)
            ).use { cursor -> cursor.moveToFirst() && !cursor.isNull(0) }
            assertTrue("Native and Clear must finish the import batch", finished)
        }

        try {
            check(onboarding.edit().putBoolean("system-calendar-import-asked", true).commit())
            withCalendarProviderAccess {
                val calendarValues = ContentValues().apply {
                    put(CalendarContract.Calendars.ACCOUNT_NAME, accountName)
                    put(CalendarContract.Calendars.ACCOUNT_TYPE, CalendarContract.ACCOUNT_TYPE_LOCAL)
                    put(CalendarContract.Calendars.NAME, accountName)
                    put(CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
                        "PowerCal isolated test calendar")
                    put(CalendarContract.Calendars.CALENDAR_COLOR, 0xFF4B72A8.toInt())
                    put(CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL,
                        CalendarContract.Calendars.CAL_ACCESS_OWNER)
                    put(CalendarContract.Calendars.OWNER_ACCOUNT, accountName)
                    put(CalendarContract.Calendars.CALENDAR_TIME_ZONE, zone.id)
                    put(CalendarContract.Calendars.VISIBLE, 1)
                    put(CalendarContract.Calendars.SYNC_EVENTS, 1)
                }
                val insertedCalendar = requireNotNull(resolver.insert(
                    syncAdapterUri(CalendarContract.Calendars.CONTENT_URI, accountName),
                    calendarValues
                )) { "The isolated system CalendarProvider rejected the UUID calendar fixture" }
                calendarId = ContentUris.parseId(insertedCalendar)
                val eventValues = ContentValues().apply {
                    put(CalendarContract.Events.CALENDAR_ID, requireNotNull(calendarId))
                    put(CalendarContract.Events.TITLE, title)
                    put(CalendarContract.Events.DESCRIPTION, notes)
                    put(CalendarContract.Events.EVENT_LOCATION, location)
                    put(CalendarContract.Events.DTSTART, startMillis)
                    put(CalendarContract.Events.DTEND, endMillis)
                    put(CalendarContract.Events.EVENT_TIMEZONE, zone.id)
                    put(CalendarContract.Events.ALL_DAY, 0)
                    put(CalendarContract.Events.STATUS, CalendarContract.Events.STATUS_CONFIRMED)
                    put(CalendarContract.Events.AVAILABILITY,
                        CalendarContract.Events.AVAILABILITY_BUSY)
                    put(CalendarContract.Events.ACCESS_LEVEL, CalendarContract.Events.ACCESS_PRIVATE)
                    put(CalendarContract.Events.HAS_ALARM, 0)
                }
                val insertedEvent = requireNotNull(resolver.insert(
                    syncAdapterUri(CalendarContract.Events.CONTENT_URI, accountName), eventValues
                )) { "The isolated system CalendarProvider rejected the UUID event fixture" }
                eventId = ContentUris.parseId(insertedEvent)
            }

            val externalId = "${requireNotNull(eventId)}:$startMillis"
            compose.waitUntil(15_000) {
                withCalendarProviderAccess {
                    dev.powerfulcalendar.interop.SystemCalendar.readUpcoming(context)
                        .any { it.externalId == externalId && it.title == title }
                }
            }
            assertEquals("The real provider must expose exactly this one synthetic event",
                listOf(externalId), withCalendarProviderAccess {
                    dev.powerfulcalendar.interop.SystemCalendar.readUpcoming(context)
                        .mapNotNull { it.externalId }
                })

            nativeStore = PowerStore(context, nativeDatabase)
            clearStore = PowerStore(context, clearDatabase)
            check(nativeStore!!.all().isEmpty() && clearStore!!.all().isEmpty())

            compose.runOnUiThread {
                compose.activity.setContent { }
                PowerCalSkinRegistry.reset()
                skinManager.select("powercal-default")
                compose.activity.setContent { PowerCalTheme { PowerCalApp(nativeStore!!) } }
            }
            compose.waitUntil(15_000) {
                compose.onAllNodesWithText("时间轴").fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithText("设置").performClick()
            compose.waitUntil(10_000) {
                compose.onAllNodesWithText("工具与设置").fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithText("从系统日历一次性导入").performScrollTo().performClick()
            compose.waitUntil(20_000) {
                nativeStore!!.sourceLink("android-calendar", externalId) != null &&
                    hasFinishedImportedBatch(nativeStore!!)
            }
            assertImported(nativeStore!!)
            val nativeRecord = nativeStore!!.all().single()

            compose.runOnUiThread {
                compose.activity.setContent { }
                PowerCalSkinRegistry.reset()
                skinManager.select("powercal-clear-default")
                compose.activity.setContent { PowerCalTheme { PowerCalApp(clearStore!!) } }
            }
            compose.waitUntil(30_000) {
                compose.runOnUiThread {
                    web = findWebView(compose.activity.findViewById(android.R.id.content))
                }
                web != null
            }
            val clearWeb = requireNotNull(web)
            compose.waitUntil(20_000) {
                evaluate(clearWeb, "!!window.PowerCalSkin?.receive") == "true"
            }
            tapWebSelector(clearWeb, ".clear-tabbar [data-action=settings]")
            compose.waitUntil(15_000) {
                WebSkinBridgeRuntime.state().optString("page") == "SETTINGS"
            }
            tapWebSelector(clearWeb,
                "#settingsOptionGroups [data-option-id='calendar.import.system']")
            compose.waitUntil(20_000) {
                clearStore!!.sourceLink("android-calendar", externalId) != null &&
                    hasFinishedImportedBatch(clearStore!!)
            }
            assertImported(clearStore!!)
            val clearRecord = clearStore!!.all().single()
            assertEquals("Native and Clear must persist the same structured event snapshot",
                nativeRecord.copy(createdAtMillis = 0L, updatedAtMillis = 0L),
                clearRecord.copy(createdAtMillis = 0L, updatedAtMillis = 0L))
            assertEquals("One-shot system import must not create an editor draft",
                0, clearStore!!.draftCount())
            println("SEP11_NATIVE_CLEAR_NONEMPTY_SYSTEM_CALENDAR_IMPORT " +
                "providerEvent=1 nativeImported=1 clearImported=1 sameRecord=true " +
                "sameSourceLink=true oneFormalPerStore=true noPending=true")
        } finally {
            compose.runOnUiThread {
                compose.activity.setContent { }
                PowerCalSkinRegistry.reset()
            }
            compose.waitForIdle()
            nativeStore?.close()
            clearStore?.close()
            context.deleteDatabase(nativeDatabase)
            context.deleteDatabase(clearDatabase)
            skinManager.select(originalSkin)
            val restoreOnboarding = onboarding.edit()
            if (hadOnboarding) {
                restoreOnboarding.putBoolean("system-calendar-import-asked", originalOnboarding)
            } else {
                restoreOnboarding.remove("system-calendar-import-asked")
            }
            check(restoreOnboarding.commit())
            withCalendarProviderAccess {
                val eventSelection = "${CalendarContract.Events.CALENDAR_ID}=?"
                calendarId?.let { id ->
                    resolver.delete(
                        syncAdapterUri(CalendarContract.Events.CONTENT_URI, accountName),
                        eventSelection, arrayOf(id.toString())
                    )
                }
                resolver.delete(
                    syncAdapterUri(CalendarContract.Calendars.CONTENT_URI, accountName),
                    "${CalendarContract.Calendars.ACCOUNT_NAME}=? AND " +
                        "${CalendarContract.Calendars.ACCOUNT_TYPE}=?",
                    arrayOf(accountName, CalendarContract.ACCOUNT_TYPE_LOCAL)
                )
                eventId?.let { id ->
                    resolver.delete(ContentUris.withAppendedId(
                        syncAdapterUri(CalendarContract.Events.CONTENT_URI, accountName), id
                    ), null, null)
                }
                assertEquals("The UUID calendar fixture must be removed after the test",
                    0, resolver.query(CalendarContract.Calendars.CONTENT_URI,
                        arrayOf(CalendarContract.Calendars._ID),
                        "${CalendarContract.Calendars.ACCOUNT_NAME}=?",
                        arrayOf(accountName), null)?.use { it.count } ?: 0)
                eventId?.let { id ->
                    assertEquals("The UUID event fixture must be removed after the test", 0,
                        resolver.query(CalendarContract.Events.CONTENT_URI,
                            arrayOf(CalendarContract.Events._ID),
                            "${CalendarContract.Events._ID}=?", arrayOf(id.toString()), null)
                            ?.use { it.count } ?: 0)
                }
            }
            PowerCalSkinRegistry.reset()
        }
    }

    private fun readAttachmentRequest(socket: java.net.Socket): String {
        val input = socket.getInputStream().buffered()
        fun readLine(): String {
            val bytes = ByteArrayOutputStream()
            while (true) {
                val byte = input.read()
                require(byte >= 0) { "truncated attachment request header" }
                if (byte == '\n'.code) break
                if (byte != '\r'.code) bytes.write(byte)
                require(bytes.size() <= 8_192) { "oversized attachment request header" }
            }
            return bytes.toString(Charsets.US_ASCII.name())
        }
        require(readLine().startsWith("POST /v1/chat/completions HTTP/1.1"))
        var contentLength = -1
        while (true) {
            val header = readLine()
            if (header.isEmpty()) break
            if (header.startsWith("Content-Length:", ignoreCase = true)) {
                contentLength = header.substringAfter(':').trim().toInt()
            }
        }
        require(contentLength in 1..100_000) { "invalid attachment request length=$contentLength" }
        val body = ByteArray(contentLength)
        var offset = 0
        while (offset < body.size) {
            val count = input.read(body, offset, body.size - offset)
            require(count > 0) { "truncated attachment request body" }
            offset += count
        }
        return String(body, Charsets.UTF_8)
    }

    private fun writeAttachmentReply(socket: java.net.Socket, content: String) {
        val body = JSONObject().put("choices", JSONArray().put(JSONObject()
            .put("message", JSONObject().put("role", "assistant").put("content", content))
            .put("finish_reason", "stop"))).toString().toByteArray(Charsets.UTF_8)
        val headers = ("HTTP/1.1 200 OK\r\n" +
            "Content-Type: application/json; charset=utf-8\r\n" +
            "Content-Length: ${body.size}\r\n" +
            "Connection: close\r\n\r\n").toByteArray(Charsets.US_ASCII)
        socket.getOutputStream().apply { write(headers); write(body); flush() }
    }

    private class IsolatedAppContext(
        target: Context,
        private val backing: Context,
        private val namespace: String
    ) : ContextWrapper(target) {
        private val root = File(backing.cacheDir, namespace).apply {
            check(mkdirs() || isDirectory)
        }

        private fun alias(name: String) = "$namespace-$name"

        private fun databasePath(name: String): File = backing.getDatabasePath(alias(name)).also {
            check(it.parentFile?.mkdirs() == true || it.parentFile?.isDirectory == true)
        }

        override fun getApplicationContext(): Context = this
        override fun getSharedPreferences(name: String, mode: Int) =
            backing.getSharedPreferences(alias(name), mode)
        override fun getFilesDir(): File = root
        override fun getCacheDir(): File = File(root, "cache").apply { mkdirs() }
        override fun getNoBackupFilesDir(): File = File(root, "no_backup").apply { mkdirs() }
        override fun getDir(name: String, mode: Int): File = File(root, "dirs/$name").apply { mkdirs() }
        override fun getDatabasePath(name: String): File = databasePath(name)
        override fun openOrCreateDatabase(name: String, mode: Int,
            factory: SQLiteDatabase.CursorFactory?): SQLiteDatabase {
            databasePath(name)
            return backing.openOrCreateDatabase(alias(name), mode, factory)
        }
        override fun openOrCreateDatabase(name: String, mode: Int,
            factory: SQLiteDatabase.CursorFactory?, errorHandler: DatabaseErrorHandler?): SQLiteDatabase {
            databasePath(name)
            return backing.openOrCreateDatabase(alias(name), mode, factory, errorHandler)
        }
        override fun deleteDatabase(name: String): Boolean = backing.deleteDatabase(alias(name))
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
