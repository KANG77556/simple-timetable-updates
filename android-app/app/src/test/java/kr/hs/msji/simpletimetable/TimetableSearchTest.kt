package kr.hs.msji.simpletimetable

import org.junit.Assert.assertEquals
import org.junit.Test

class TimetableSearchTest {
    private val rows = listOf(
        TimetableItem("2026-09-30", 3, "경영1", 1, "영문", "고창", "301"),
        TimetableItem("2026-09-30", 3, "경영2", 2, "진로", "김성", "302"),
        TimetableItem("2026-09-30", 3, "IT1", 4, "진로", "김성", "컴응1"),
        TimetableItem("2026-09-30", 2, "경영1", 4, "진로", "김성", "201"),
        TimetableItem("2026-09-30", 3, "경영1", 7, "", "", "")
    )

    @Test
    fun search_matchesTeacherWithinSelectedGrade() {
        assertEquals(
            listOf("경영2", "IT1"),
            searchTimetableRows(rows, "김성", 3).map { it.classCode }
        )
    }

    @Test
    fun search_isCaseInsensitiveForClassCode() {
        assertEquals("IT1", searchTimetableRows(rows, "it1", 3).single().classCode)
    }

    @Test
    fun search_matchesRoomPartially() {
        assertEquals("컴응1", searchTimetableRows(rows, "컴응", 3).single().room)
    }

    @Test
    fun search_matchesPeriodText() {
        assertEquals(4, searchTimetableRows(rows, "4교시", 3).single().period)
    }

    @Test
    fun blankQuery_returnsAllVisibleRowsForSelectedGrade() {
        assertEquals(3, searchTimetableRows(rows, "", 3).size)
    }

    @Test
    fun emptyPeriodPlaceholder_isNotShownOrSearched() {
        assertEquals(0, searchTimetableRows(rows, "7교시", 3).size)
    }
}
