package dev.powerfulcalendar.ui.v2

import android.Manifest
import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.content.ContextWrapper
import android.database.Cursor
import android.database.DatabaseErrorHandler
import android.database.MatrixCursor
import android.database.sqlite.SQLiteDatabaseLockedException
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import android.os.Looper
import android.provider.CalendarContract
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import dev.powerfulcalendar.core.DraftForm
import dev.powerfulcalendar.core.DraftOperation
import dev.powerfulcalendar.core.PowerStore
import dev.powerfulcalendar.core.TemporalKind
import dev.powerfulcalendar.core.TemporalRecord
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowContentResolver
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.runBlocking
import java.io.File
import java.time.ZoneId
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SystemCalendarImportTest {
    @get:Rule val ui = createAndroidComposeRule<ComponentActivity>()

    private lateinit var backing: android.app.Application
    private lateinit var namespace: String
    private lateinit var context: IsolatedContext
    private lateinit var store: PowerStore
    private lateinit var provider: TestCalendarProvider
    private lateinit var rows: List<CalendarRow>
    private var expectedBatchCount = 0

    @Before
    fun setUp() {
        backing = ApplicationProvider.getApplicationContext()
        namespace = "syscal-${UUID.randomUUID().toString().take(10)}"
        context = IsolatedContext(ui.activity, backing, namespace)
        store = PowerStore(context, "events.db")
        store.readableDatabase
        val first = System.currentTimeMillis() + 2 * 86_400_000L
        rows = listOf(
            CalendarRow(101L, first, first + 3_600_000L, "Calendar event 1"),
            CalendarRow(102L, first + 86_400_000L, first + 90_000_000L, "Calendar event 2")
        )
        provider = TestCalendarProvider(rows)
        ShadowContentResolver.registerProviderInternal(CalendarContract.AUTHORITY, provider)
        context.getSharedPreferences("powercal-onboarding", Context.MODE_PRIVATE)
            .edit().putBoolean("system-calendar-import-asked", true).commit()
        PowerCalSkinRegistry.reset()
    }

    @Test
    fun deniedPermissionDoesNotQueryOrWrite() {
        shadowOf(ui.activity).denyPermissions(Manifest.permission.READ_CALENDAR)
        shadowOf(backing).denyPermissions(Manifest.permission.READ_CALENDAR)
        showApp()
        val before = snapshot()

        ui.runOnIdle {
            WebSkinBridgeRuntime.onAction(WebSkinAction.InvokeOption("calendar.import.system"))
        }
        ui.waitUntil(10_000) { shadowOf(ui.activity).lastRequestedPermission != null }

        assertEquals(listOf(Manifest.permission.READ_CALENDAR),
            shadowOf(ui.activity).lastRequestedPermission!!.requestedPermissions.toList())
        assertEquals(0, provider.queryCount)
        assertEquals(before, snapshot())
    }

    @Test
    fun cancellationAtBatchCreationReturnStillLeavesIdForFinalization() {
        val caller = Job()
        var capturedBatchId: String? = null
        var observed: Throwable? = null
        try {
            runBlocking(caller) {
                beginSystemCalendarBatchTracked(store) { createdId ->
                    capturedBatchId = createdId
                    caller.cancel()
                }
            }
        } catch (error: Throwable) {
            observed = error
        }

        assertTrue("cancellation must propagate to the caller", observed is CancellationException)
        val createdId = requireNotNull(capturedBatchId)
        val warnings = listOf("系统日历导入中断；已提交记录可通过重试自动去重")
        runBlocking { finishSystemCalendarBatch(store, createdId, warnings) }

        val batch = waitForBatchCount(1).single()
        assertNotNull("the batch committed before cancellation must still be finalized",
            batch.finishedMillis)
        assertEquals(warnings, batch.warnings)
        assertTrue(store.all().isEmpty())
        assertEquals(0L, store.identityCount())
        assertEquals(1L, store.nextSequencePreview())
        assertEquals(0, commitCount())
    }

    @Test
    fun rejectedItemsArePersistedAsWarningsWithoutCalendarWrites() {
        shadowOf(ui.activity).grantPermissions(Manifest.permission.READ_CALENDAR)
        shadowOf(backing).grantPermissions(Manifest.permission.READ_CALENDAR)
        val pending = store.newDraft(activityForm("Import lock probe"))
        val before = snapshot()
        val gate = provider.blockNextQuery()
        showApp()

        ui.runOnIdle {
            WebSkinBridgeRuntime.onAction(WebSkinAction.InvokeOption("calendar.import.system"))
        }
        assertTrue("provider query should start before the Store lock is acquired",
            gate.started.await(10, TimeUnit.SECONDS))
        val aiSession = store.beginAiBatch(
            mapOf(pending.draftId to pending.draftVersion), setOf(DraftOperation.CREATE)
        )
        gate.release.countDown()
        try {
            val batch = waitForBatchCount(1).single()
            assertNotNull(batch.finishedMillis)
            assertEquals(0, batch.imported)
            assertEquals(0, batch.skipped)
            assertEquals(2, batch.warnings.size)
            assertTrue(batch.warnings.all { it.contains("系统日历事件未导入") })
            assertEquals(before.records, store.all())
            assertEquals(before.identityCount, store.identityCount())
            assertEquals(before.nextSequence, store.nextSequencePreview())
            assertEquals(before.pendingCount, store.pendingCount())
            assertEquals(before.commits, commitCount())
        } finally {
            gate.release.countDown()
            store.finishAiBatch(aiSession.sessionId)
        }
    }

    @Test
    fun systemImportClosesAndRetriesIdempotently() {
        shadowOf(ui.activity).grantPermissions(Manifest.permission.READ_CALENDAR)
        shadowOf(backing).grantPermissions(Manifest.permission.READ_CALENDAR)
        val failedExternalId = "${rows[1].eventId}:${rows[1].begin}"
        store.writableDatabase.execSQL(
            """CREATE TRIGGER fail_calendar_second BEFORE INSERT ON source_links
               WHEN NEW.source='android-calendar' AND NEW.external_id='$failedExternalId'
               BEGIN SELECT RAISE(ABORT, 'injected calendar store failure'); END"""
        )
        showApp()

        val firstBatches = importFromClear()
        assertEquals(1, provider.queryCount)
        assertEquals(1, store.all().size)
        val interrupted = firstBatches.single()
        assertNotNull("failed batch must be closed", interrupted.finishedMillis)
        assertEquals(1, interrupted.imported)
        assertTrue(interrupted.warnings.any { it.contains("导入中断") })
        val first = store.all().single()
        assertEquals("Calendar event 1", first.title)
        assertEquals("android-calendar", first.source)
        assertEquals(first.eventSeq, store.sourceLink("android-calendar",
            "${rows[0].eventId}:${rows[0].begin}"))
        assertEquals(rows[0].begin, store.intentFor(first.eventSeq)!!.instantMillis)
        assertEquals(1L, store.identityCount())
        assertEquals(2L, store.nextSequencePreview())
        assertEquals(1, commitCount())

        store.writableDatabase.execSQL("DROP TRIGGER fail_calendar_second")
        val retryBatches = importFromClear()
        val completeSnapshot = store.all()
        assertEquals(2, completeSnapshot.size)
        assertEquals(listOf("Calendar event 1", "Calendar event 2"),
            completeSnapshot.map(TemporalRecord::title))
        assertEquals(2L, store.identityCount())
        assertEquals(3L, store.nextSequencePreview())
        assertEquals(2, commitCount())
        assertEquals(rows[1].begin,
            store.intentFor(completeSnapshot.last().eventSeq)!!.instantMillis)
        assertEquals(1, retryBatches[1].imported)
        assertEquals(1, retryBatches[1].skipped)
        assertTrue(retryBatches.all { it.finishedMillis != null })

        val repeatedBatches = importFromClear()
        assertEquals(3, provider.queryCount)
        assertEquals(completeSnapshot, store.all())
        assertEquals(2L, store.identityCount())
        assertEquals(3L, store.nextSequencePreview())
        assertEquals(2, commitCount())
        assertEquals(0, store.pendingCount())
        assertTrue(repeatedBatches.all { it.finishedMillis != null })
    }

    private fun showApp() {
        ui.activity.setContent {
            CompositionLocalProvider(LocalContext provides context) {
                PowerCalTheme { PowerCalApp(store) }
            }
        }
        ui.waitUntil(20_000) {
            shadowOf(Looper.getMainLooper()).idle()
            WebSkinBridgeRuntime.owner != null
        }
    }

    private fun importFromClear(): List<MigrationRow> {
        expectedBatchCount += 1
        val expectedCount = expectedBatchCount
        ui.runOnIdle {
            WebSkinBridgeRuntime.onAction(WebSkinAction.InvokeOption("calendar.import.system"))
        }
        return waitForBatchCount(expectedCount)
    }

    private fun waitForBatchCount(count: Int): List<MigrationRow> {
        var completed: List<MigrationRow>? = null
        ui.waitUntil(20_000) {
            shadowOf(Looper.getMainLooper()).idle()
            val observed = migrationRows()
            if (observed.size == count && observed.all { it.finishedMillis != null }) {
                completed = observed
                true
            } else false
        }
        return requireNotNull(completed)
    }

    private fun snapshot() = StoreSnapshot(
        records = store.all(),
        identityCount = store.identityCount(),
        nextSequence = store.nextSequencePreview(),
        pendingCount = store.pendingCount(),
        commits = commitCount()
    )

    private fun migrationRows(): List<MigrationRow> {
        val path = context.getDatabasePath(store.databaseName)
        val db = try {
            SQLiteDatabase.openDatabase(path.path, null, SQLiteDatabase.OPEN_READONLY)
        } catch (_: SQLiteDatabaseLockedException) {
            return emptyList()
        }
        return try {
            db.rawQuery(
                "SELECT imported,skipped,finished_ms,warnings FROM migrations " +
                    "WHERE source='android-calendar' ORDER BY started_ms",
                null
            ).use { cursor ->
                buildList {
                    while (cursor.moveToNext()) {
                        val warningArray = org.json.JSONArray(cursor.getString(3))
                        add(MigrationRow(
                            imported = cursor.getInt(0),
                            skipped = cursor.getInt(1),
                            finishedMillis = if (cursor.isNull(2)) null else cursor.getLong(2),
                            warnings = (0 until warningArray.length()).map(warningArray::getString)
                        ))
                    }
                }
            }
        } catch (_: SQLiteDatabaseLockedException) {
            emptyList()
        } finally {
            db.close()
        }
    }

    private fun commitCount(): Int {
        val path = context.getDatabasePath(store.databaseName)
        val db = SQLiteDatabase.openDatabase(path.path, null, SQLiteDatabase.OPEN_READONLY)
        return try {
            db.rawQuery("SELECT COUNT(*) FROM commits", null).use { cursor ->
                check(cursor.moveToFirst())
                cursor.getInt(0)
            }
        } finally {
            db.close()
        }
    }

    @After
    fun tearDown() {
        ui.activity.runOnUiThread { ui.activity.setContent {} }
        ui.waitForIdle()
        val appJob = PowerCalStores.appScope.coroutineContext[kotlinx.coroutines.Job]
        ui.waitUntil(15_000) {
            shadowOf(Looper.getMainLooper()).idle()
            appJob?.children?.none() ?: true
        }
        store.close()
        context.deleteDatabase(store.databaseName)
        context.deleteSharedPreferences("powercal-onboarding")
        context.cleanupRoot()
        PowerCalSkinRegistry.reset()
    }

    private data class CalendarRow(
        val eventId: Long,
        val begin: Long,
        val end: Long,
        val title: String
    )

    private data class QueryGate(
        val started: CountDownLatch,
        val release: CountDownLatch
    )

    private data class MigrationRow(
        val imported: Int,
        val skipped: Int,
        val finishedMillis: Long?,
        val warnings: List<String>
    )

    private data class StoreSnapshot(
        val records: List<TemporalRecord>,
        val identityCount: Long,
        val nextSequence: Long,
        val pendingCount: Int,
        val commits: Int
    )

    private class TestCalendarProvider(private val rows: List<CalendarRow>) : ContentProvider() {
        var queryCount = 0
            private set
        @Volatile private var queryGate: QueryGate? = null

        fun blockNextQuery(): QueryGate = QueryGate(
            started = CountDownLatch(1),
            release = CountDownLatch(1)
        ).also { queryGate = it }

        override fun onCreate(): Boolean = true

        override fun query(
            uri: Uri,
            projection: Array<out String>?,
            selection: String?,
            selectionArgs: Array<out String>?,
            sortOrder: String?
        ): Cursor {
            queryCount++
            queryGate?.let { gate ->
                queryGate = null
                gate.started.countDown()
                check(gate.release.await(10, TimeUnit.SECONDS)) {
                    "test query gate was not released"
                }
            }
            val columns = projection ?: emptyArray()
            return MatrixCursor(columns).apply {
                rows.forEach { row ->
                    addRow(columns.map { column ->
                        val value: Any? = when (column) {
                            CalendarContract.Instances.EVENT_ID -> row.eventId
                            CalendarContract.Instances.BEGIN -> row.begin
                            CalendarContract.Instances.END -> row.end
                            CalendarContract.Instances.TITLE -> row.title
                            CalendarContract.Instances.DESCRIPTION -> "Imported description"
                            CalendarContract.Instances.EVENT_LOCATION -> "Test location"
                            CalendarContract.Instances.ALL_DAY -> 0
                            else -> null
                        }
                        value
                    }.toTypedArray())
                }
            }
        }

        override fun getType(uri: Uri): String? = null
        override fun insert(uri: Uri, values: ContentValues?): Uri? = null
        override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
        override fun update(
            uri: Uri,
            values: ContentValues?,
            selection: String?,
            selectionArgs: Array<out String>?
        ): Int = 0
    }

    private fun activityForm(title: String): DraftForm {
        val zone = ZoneId.systemDefault()
        val start = System.currentTimeMillis() + 3_600_000L
        return DraftForm(
            kind = TemporalKind.ACTIVITY,
            title = title,
            startText = DraftForm.formatInstant(start, zone),
            endText = DraftForm.formatInstant(start + 3_600_000L, zone),
            zoneId = zone.id
        )
    }

    private class IsolatedContext(
        target: Context,
        private val backing: Context,
        private val namespace: String
    ) : ContextWrapper(target) {
        private val root = File(backing.cacheDir, namespace).apply {
            check(mkdirs() || isDirectory)
        }

        private fun databaseAlias(name: String) = "$namespace-$name"
        private fun databasePath(name: String): File =
            backing.getDatabasePath(databaseAlias(name)).also {
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
        override fun deleteSharedPreferences(name: String): Boolean =
            backing.deleteSharedPreferences("$namespace-$name")
        fun cleanupRoot() { root.deleteRecursively() }
    }
}
