package dev.powerfulcalendar.interop

import android.Manifest
import android.app.Application
import android.content.ContentProvider
import android.content.ContentValues
import android.content.pm.PackageManager
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.provider.AlarmClock
import android.provider.CalendarContract
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowContentResolver
import java.time.LocalDate
import java.time.ZoneId

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SystemCalendarTest {
    @Test
    fun showingSystemAlarmsOnlyCreatesThePlatformViewerIntent() {
        val intent = SystemCalendar.showSystemAlarms()

        assertEquals(AlarmClock.ACTION_SHOW_ALARMS, intent.action)
        assertNull(intent.data)
        assertNull(intent.component)
        assertNull(intent.extras)
    }

    @Test
    fun settingSystemAlarmRetainsPlatformDefaultInteraction() {
        val intent = SystemCalendar.setSystemAlarm(8, 30, "核心边界测试")

        assertEquals(AlarmClock.ACTION_SET_ALARM, intent.action)
        assertEquals(8, intent.getIntExtra(AlarmClock.EXTRA_HOUR, -1))
        assertEquals(30, intent.getIntExtra(AlarmClock.EXTRA_MINUTES, -1))
        assertEquals("核心边界测试", intent.getStringExtra(AlarmClock.EXTRA_MESSAGE))
        assertFalse(intent.getBooleanExtra(AlarmClock.EXTRA_SKIP_UI, true))
    }

    @Test
    fun readUpcomingWithoutCalendarPermissionReturnsEmptyWithoutQueryingProvider() {
        val application = ApplicationProvider.getApplicationContext<Application>()
        val provider = RecordingCalendarProvider(emptyList())
        shadowOf(application).denyPermissions(Manifest.permission.READ_CALENDAR)
        ShadowContentResolver.registerProviderInternal(CalendarContract.AUTHORITY, provider)

        val events = SystemCalendar.readUpcoming(application)

        assertTrue(events.isEmpty())
        assertEquals(0, provider.queryCount)
        assertEquals(PackageManager.PERMISSION_DENIED,
            application.checkSelfPermission(Manifest.permission.READ_CALENDAR))
    }

    @Test
    fun readUpcomingQueriesInstancesRangeFiltersInvalidRowsAndUsesStableExternalIds() {
        val application = ApplicationProvider.getApplicationContext<Application>()
        shadowOf(application).grantPermissions(Manifest.permission.READ_CALENDAR)
        val validBegin = 1_800_000_000_000L
        val provider = RecordingCalendarProvider(listOf(
            mapOf(
                CalendarContract.Instances.EVENT_ID to 41L,
                CalendarContract.Instances.BEGIN to validBegin,
                CalendarContract.Instances.END to validBegin + 3_600_000L,
                CalendarContract.Instances.TITLE to "Planning",
                CalendarContract.Instances.DESCRIPTION to "Review milestones",
                CalendarContract.Instances.EVENT_LOCATION to "Room 2",
                CalendarContract.Instances.ALL_DAY to 0
            ),
            mapOf(
                CalendarContract.Instances.EVENT_ID to 42L,
                CalendarContract.Instances.BEGIN to validBegin + 4_000_000L,
                CalendarContract.Instances.END to validBegin + 4_000_000L,
                CalendarContract.Instances.TITLE to "Zero length",
                CalendarContract.Instances.DESCRIPTION to "",
                CalendarContract.Instances.EVENT_LOCATION to "",
                CalendarContract.Instances.ALL_DAY to 0
            ),
            mapOf(
                CalendarContract.Instances.EVENT_ID to 43L,
                CalendarContract.Instances.BEGIN to validBegin + 5_000_000L,
                CalendarContract.Instances.END to validBegin + 4_000_000L,
                CalendarContract.Instances.TITLE to "Negative length",
                CalendarContract.Instances.DESCRIPTION to "",
                CalendarContract.Instances.EVENT_LOCATION to "",
                CalendarContract.Instances.ALL_DAY to 0
            )
        ))
        ShadowContentResolver.registerProviderInternal(CalendarContract.AUTHORITY, provider)
        val zone = ZoneId.systemDefault()
        val dateBeforeCall = LocalDate.now(zone)

        val firstRead = SystemCalendar.readUpcoming(application)
        val dateAfterCall = LocalDate.now(zone)
        val firstQuery = requireNotNull(provider.lastQuery)
        val secondRead = SystemCalendar.readUpcoming(application)

        assertEquals(2, provider.queryCount)
        assertEquals(1, firstRead.size)
        val event = firstRead.single()
        assertEquals("Planning", event.title)
        assertEquals(validBegin, event.startMillis)
        assertEquals(validBegin + 3_600_000L, event.endMillis)
        assertEquals("Review milestones", event.notes)
        assertEquals("Room 2", event.location)
        assertEquals("android-calendar", event.source)
        assertEquals("41:$validBegin", event.externalId)
        assertEquals(firstRead.map { it.externalId }, secondRead.map { it.externalId })

        assertEquals(CalendarContract.AUTHORITY, firstQuery.uri.authority)
        assertEquals(listOf("instances", "when"), firstQuery.uri.pathSegments.take(2))
        val from = firstQuery.uri.pathSegments[2].toLong()
        val to = firstQuery.uri.pathSegments[3].toLong()
        val possibleCallDates = listOf(dateBeforeCall, dateAfterCall)
        assertTrue("query endpoints must use one captured local date",
            possibleCallDates.any { date ->
                from == date.minusDays(30).atStartOfDay(zone).toInstant().toEpochMilli() &&
                    to == date.plusDays(365).atStartOfDay(zone).toInstant().toEpochMilli()
            })
        assertEquals(listOf(
            CalendarContract.Instances.EVENT_ID,
            CalendarContract.Instances.BEGIN,
            CalendarContract.Instances.END,
            CalendarContract.Instances.TITLE,
            CalendarContract.Instances.DESCRIPTION,
            CalendarContract.Instances.EVENT_LOCATION,
            CalendarContract.Instances.ALL_DAY
        ), firstQuery.projection)
        assertNull(firstQuery.selection)
        assertNull(firstQuery.selectionArgs)
        assertEquals(CalendarContract.Instances.BEGIN + " ASC", firstQuery.sortOrder)
    }

    private data class Query(
        val uri: Uri,
        val projection: List<String>?,
        val selection: String?,
        val selectionArgs: List<String>?,
        val sortOrder: String?
    )

    private class RecordingCalendarProvider(
        private val rows: List<Map<String, Any?>>
    ) : ContentProvider() {
        var queryCount = 0
            private set
        var lastQuery: Query? = null
            private set

        override fun onCreate(): Boolean = true

        override fun query(
            uri: Uri,
            projection: Array<out String>?,
            selection: String?,
            selectionArgs: Array<out String>?,
            sortOrder: String?
        ): Cursor {
            queryCount += 1
            lastQuery = Query(uri, projection?.toList(), selection, selectionArgs?.toList(), sortOrder)
            val columns: Array<out String> = projection ?: emptyArray()
            return MatrixCursor(columns).apply {
                rows.forEach { row ->
                    addRow(Array<Any?>(columns.size) { index -> row[columns[index]] })
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
}
