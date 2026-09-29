package kr.hs.msji.simpletimetable

import org.junit.Assert.assertEquals
import org.junit.Test

class AllTimetableUiTest {
    @Test
    fun gradeSummary_includesGradeAndClassCount() {
        assertEquals("3학년 · 5개 학급", allTimetableGradeSummary(3, 5))
    }

    @Test
    fun classSummary_includesGradeAndPeriodCount() {
        assertEquals("3학년 · 7교시", allTimetableClassSummary(3, 7))
    }
}
