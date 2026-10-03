package kr.hs.msji.simpletimetable

import java.time.LocalDate
import java.time.YearMonth
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class TodayDatePickerTest {
    @Test
    fun october2026_marks_korean_holidays_and_keeps_sunday_first_grid() {
        val cells = monthCalendarCells(YearMonth.of(2026, 10))

        assertEquals(42, cells.size)

        val oct1 = cells.firstOrNull { it.date == LocalDate.of(2026, 10, 1) }
        val oct3 = cells.firstOrNull { it.date == LocalDate.of(2026, 10, 3) }
        val oct5 = cells.firstOrNull { it.date == LocalDate.of(2026, 10, 5) }
        val oct9 = cells.firstOrNull { it.date == LocalDate.of(2026, 10, 9) }

        assertNotNull(oct1)
        assertNotNull(oct3)
        assertNotNull(oct5)
        assertNotNull(oct9)

        assertEquals("개천절", oct3.holidayName)
        assertEquals("대체공휴일", oct5.holidayName)
        assertEquals("한글날", oct9.holidayName)

        assertNull(cells[0].date)
        assertNull(cells[1].date)
        assertNull(cells[2].date)
        assertNull(cells[3].date)
        assertEquals(LocalDate.of(2026, 10, 1), cells[4].date)
        assertEquals(LocalDate.of(2026, 10, 3), cells[6].date)
    }
}
