package kr.hs.msji.simpletimetable

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TodayActionSummaryTest {
    @Test
    fun nextUpcomingCalendarItem_returnsNearestFutureEvent() {
        val items = listOf(
            CalendarItem(1, "지난 일정", "2026-10-02"),
            CalendarItem(2, "다음 일정", "2026-10-05"),
            CalendarItem(3, "더 나중 일정", "2026-10-07")
        )
        val result = nextUpcomingCalendarItem(items, LocalDate.of(2026, 10, 3))
        assertEquals("다음 일정", result?.title)
    }

    @Test
    fun nextUpcomingCalendarItem_returnsNullWhenNoFutureEvent() {
        val items = listOf(CalendarItem(1, "지난 일정", "2026-10-02"))
        assertNull(nextUpcomingCalendarItem(items, LocalDate.of(2026, 10, 3)))
    }
}
