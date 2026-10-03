package kr.hs.msji.simpletimetable

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class CalendarTimetableLoadingTest {
    @Test
    fun selectedWeekdayIsFetchedFirst() {
        val month = YearMonth.of(2026, 10)
        val selected = LocalDate.of(2026, 10, 13)

        val dates = calendarTimetableFetchDates(month, selected)

        assertEquals(selected, dates.first())
        assertEquals(dates.distinct().size, dates.size)
        assertTrue(dates.all { it.dayOfWeek.value in 1..5 })
    }

    @Test
    fun weekendPriorityDoesNotAddWeekendRequest() {
        val month = YearMonth.of(2026, 10)
        val saturday = LocalDate.of(2026, 10, 3)

        val dates = calendarTimetableFetchDates(month, saturday)

        assertFalse(dates.contains(saturday))
        assertTrue(dates.all { it.dayOfWeek.value in 1..5 })
    }

    @Test
    fun priorityOutsideVisibleMonthIsIgnored() {
        val month = YearMonth.of(2026, 10)
        val outside = LocalDate.of(2026, 11, 2)

        val dates = calendarTimetableFetchDates(month, outside)

        assertEquals(LocalDate.of(2026, 10, 1), dates.first())
        assertFalse(dates.contains(outside))
    }
}
