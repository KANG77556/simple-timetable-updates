package kr.hs.msji.simpletimetable

import org.junit.Assert.assertEquals
import org.junit.Test

class BottomNavigationInsetTest {
    @Test
    fun gestureNavigation_keepsCompactContentHeightWhenInsetIsZero() {
        assertEquals(72, COMPACT_BOTTOM_NAV_HEIGHT_DP)
        assertEquals(72, bottomNavigationTotalHeightDp(bottomInsetDp = 0))
    }

    @Test
    fun threeButtonNavigation_addsSystemInsetOutsideCompactContent() {
        assertEquals(120, bottomNavigationTotalHeightDp(bottomInsetDp = 48))
    }

    @Test
    fun invalidNegativeInset_neverShrinksCompactContent() {
        assertEquals(72, bottomNavigationTotalHeightDp(bottomInsetDp = -1))
    }
    @Test
    fun calendarCellCount_usesOnlyRequiredMonthRows() {
        assertEquals(28, calendarCellCount(leadingBlankCount = 0, daysInMonth = 28))
        assertEquals(35, calendarCellCount(leadingBlankCount = 4, daysInMonth = 31))
        assertEquals(42, calendarCellCount(leadingBlankCount = 6, daysInMonth = 31))
    }

    @Test
    fun calendarCellCount_clampsInvalidLeadingBlankCount() {
        assertEquals(35, calendarCellCount(leadingBlankCount = -2, daysInMonth = 31))
        assertEquals(42, calendarCellCount(leadingBlankCount = 99, daysInMonth = 31))
    }

}
