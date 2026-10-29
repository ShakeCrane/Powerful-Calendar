package dev.powerfulcalendar.ui.v2

import android.Manifest
import android.content.Context
import android.content.ContextWrapper
import android.database.DatabaseErrorHandler
import android.database.sqlite.SQLiteDatabase
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.*
import androidx.test.core.app.ApplicationProvider
import dev.powerfulcalendar.core.PowerStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.File
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class WebSkinNotificationPermissionIntegrationTest {
    @get:Rule val ui = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun clearSettingsProjectsPermissionAndPreservesNativeRecoveryWithoutStoreWrites() {
        val backing = ApplicationProvider.getApplicationContext<android.app.Application>()
        shadowOf(backing).denyPermissions(Manifest.permission.POST_NOTIFICATIONS)
        val namespace = "notification-${UUID.randomUUID().toString().take(10)}"
        val context = IsolatedContext(ui.activity, backing, namespace)
        val store = PowerStore(context, "events.db")
        store.readableDatabase
        context.getSharedPreferences("powercal-onboarding", Context.MODE_PRIVATE)
            .edit().putBoolean("system-calendar-import-asked", true).commit()
        PowerCalSkinRegistry.reset()

        val before = StoreSnapshot(
            rows = store.all(),
            identityCount = store.identityCount(),
            nextSequence = store.nextSequencePreview(),
            pendingCount = store.pendingCount()
        )

        ui.activity.setContent {
            CompositionLocalProvider(LocalContext provides context) {
                PowerCalTheme { PowerCalApp(store) }
            }
        }
        ui.waitUntil(20_000) {
            shadowOf(Looper.getMainLooper()).idle()
            WebSkinBridgeRuntime.owner != null
        }

        ui.runOnIdle { WebSkinBridgeRuntime.onAction(WebSkinAction.Routed("settings")) }
        ui.waitUntil(20_000) { WebSkinBridgeRuntime.state().optString("page") == "SETTINGS" }
        val permission = WebSkinBridgeRuntime.state().getJSONObject("notificationPermission")
        assertTrue(permission.getBoolean("required"))
        assertFalse(permission.getBoolean("allowed"))

        val revisionBeforeRequest = WebSkinBridgeRuntime.stateRevision
        ui.runOnIdle {
            WebSkinBridgeRuntime.onAction(WebSkinAction.RequestNotificationPermission)
        }
        ui.waitUntil(10_000) { WebSkinBridgeRuntime.stateRevision > revisionBeforeRequest }
        val permissionRequest = shadowOf(ui.activity).lastRequestedPermission
        assertNotNull(permissionRequest)
        assertEquals(listOf(Manifest.permission.POST_NOTIFICATIONS),
            permissionRequest.requestedPermissions.toList())
        assertEquals(before, StoreSnapshot(
            rows = store.all(),
            identityCount = store.identityCount(),
            nextSequence = store.nextSequencePreview(),
            pendingCount = store.pendingCount()
        ))

        ui.runOnIdle { WebSkinBridgeRuntime.onAction(WebSkinAction.Routed("nativeSettings")) }
        ui.waitUntil(20_000) { WebSkinBridgeRuntime.state().optString("page") == "TOOLS" }
        ui.onNodeWithText("工具与设置").assertExists()
        assertEquals(0, WebSkinBridgeRuntime.state()
            .getJSONObject("notificationPermission").length())
        assertTrue(store.all().isEmpty())

        ui.activity.runOnUiThread { ui.activity.setContent {} }
        ui.waitForIdle()
    }

    private data class StoreSnapshot(
        val rows: List<dev.powerfulcalendar.core.TemporalRecord>,
        val identityCount: Long,
        val nextSequence: Long,
        val pendingCount: Int
    )

    private class IsolatedContext(
        target: Context,
        private val backing: Context,
        private val namespace: String
    ) : ContextWrapper(target) {
        private val root = File(backing.cacheDir, namespace).apply {
            check(mkdirs() || isDirectory)
        }

        private fun databaseAlias(name: String) = "$namespace-$name"
        private fun databasePath(name: String): File = backing.getDatabasePath(databaseAlias(name)).also {
            check(it.parentFile?.mkdirs() == true || it.parentFile?.isDirectory == true)
        }

        override fun getApplicationContext(): Context = this
        override fun getSharedPreferences(name: String, mode: Int) =
            backing.getSharedPreferences("$namespace-$name", mode)
        override fun getFilesDir(): File = root
        override fun getCacheDir(): File = File(root, "cache").apply { mkdirs() }
        override fun getNoBackupFilesDir(): File = File(root, "no_backup").apply { mkdirs() }
        override fun getDir(name: String, mode: Int): File = File(root, "dirs/$name").apply { mkdirs() }
        override fun getDatabasePath(name: String): File = databasePath(name)
        override fun openOrCreateDatabase(
            name: String,
            mode: Int,
            factory: SQLiteDatabase.CursorFactory?
        ): SQLiteDatabase {
            databasePath(name)
            return backing.openOrCreateDatabase(databaseAlias(name), mode, factory)
        }
        override fun openOrCreateDatabase(
            name: String,
            mode: Int,
            factory: SQLiteDatabase.CursorFactory?,
            errorHandler: DatabaseErrorHandler?
        ): SQLiteDatabase {
            databasePath(name)
            return backing.openOrCreateDatabase(databaseAlias(name), mode, factory, errorHandler)
        }
        override fun deleteDatabase(name: String): Boolean =
            backing.deleteDatabase(databaseAlias(name))
    }
}
