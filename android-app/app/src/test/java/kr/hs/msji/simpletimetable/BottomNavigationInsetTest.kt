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
}
