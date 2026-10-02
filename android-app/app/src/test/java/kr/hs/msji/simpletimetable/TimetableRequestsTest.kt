package kr.hs.msji.simpletimetable

import org.junit.Assert.*
import org.junit.Test

class TimetableRequestsTest {
    private val today = "2026-10-02"
    private val yesterday = "2026-10-01"
    private fun rows(date: String, subject: String = "회계") =
        listOf(TimetableItem(date, 1, "경영1", 1, subject, "교사", "101"))

    @Test fun rolloverUpdatesTodayCacheWithoutFalseAlert() {
        val requests = TimetableRequests()
        var cache = rows(yesterday)
        var alerts = 0
        val request = requests.begin(today)
        val fetched = rows(today)
        assertTrue(requests.applyIfCurrent(request) {
            if (timetableChangedForDate(today, cache, fetched)) alerts++
            cache = fetched
        })
        assertEquals(fetched, cache)
        assertEquals(0, alerts)
    }

    @Test fun normalStartupAppliesFreshRowsAndSameDayChangeAlert() {
        val requests = TimetableRequests()
        var selected = today
        var displayed = rows(today)
        var cache = displayed
        var alerts = 0
        val startup = requests.begin(today) { selected = today }
        val fresh = rows(today, "세무")
        assertTrue(requests.applyIfCurrent(startup) {
            if (timetableChangedForDate(today, cache, fresh)) alerts++
            cache = fresh
            displayed = fresh
            selected = startup.date
        })
        assertEquals(today, selected)
        assertEquals(fresh, displayed)
        assertEquals(fresh, cache)
        assertEquals(1, alerts)
    }

    @Test fun navigationRejectsLateStartupBeforeNavigationResponse() {
        for (destination in listOf(yesterday, "2026-10-03")) {
            val requests = TimetableRequests()
            var selected = today
            var displayed = rows(today)
            val startup = requests.begin(today)
            val navigation = requests.begin(destination) {
                selected = destination
                displayed = emptyList()
            }
            assertFalse(requests.applyIfCurrent(startup) {
                selected = today
                displayed = rows(today)
                fail("Stale startup must not write UI, cache, widget or notification")
            })
            assertEquals(destination, selected)
            assertTrue(displayed.isEmpty())
            assertTrue(requests.applyIfCurrent(navigation) { displayed = rows(destination) })
            assertEquals(rows(destination), displayed)
        }
    }

    @Test fun navigationResponseSurvivesStartupCompletingLast() {
        val requests = TimetableRequests()
        var displayed = rows(today)
        val startup = requests.begin(today)
        val navigation = requests.begin(yesterday)
        requests.applyIfCurrent(navigation) { displayed = rows(yesterday) }
        assertFalse(requests.applyIfCurrent(startup) { displayed = rows(today) })
        assertEquals(rows(yesterday), displayed)
    }

    @Test fun returningToTodayStillRejectsOriginalStartup() {
        val requests = TimetableRequests()
        val startup = requests.begin(today)
        requests.begin(yesterday)
        val returnToToday = requests.begin(today)
        assertFalse(requests.applyIfCurrent(startup) { fail("Date equality alone is insufficient") })
        assertTrue(requests.applyIfCurrent(returnToToday) {})
    }

    @Test fun unchangedOrEmptyCacheDoesNotAlert() {
        assertFalse(timetableChangedForDate(today, rows(today), rows(today)))
        assertFalse(timetableChangedForDate(today, emptyList(), rows(today)))
        assertFalse(timetableChangedForDate(today, rows(yesterday) + rows(today), rows(today)))
    }

    @Test fun sameDayRemovedLessonsStillAlert() {
        assertTrue(timetableChangedForDate(today, rows(today), emptyList()))
    }
}
