package kr.hs.msji.simpletimetable

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SharedCommonEventTimetableTest {
    private val date = "2026-10-06"

    @Test fun collapsesTeacherlessSharedEventsByPeriodAndSubject() {
        val rows = buildList {
            repeat(15) {
                add(TimetableItem(date, 1, "경영1", 1, "1차 정기시험"))
                add(TimetableItem(date, 2, "경영1", 2, "1차 정기시험"))
            }
        }

        val result = collapseSharedCommonEvents(rows)

        assertEquals(2, result.size)
        assertEquals(listOf(1, 2), result.map { it.period })
        assertTrue(result.all { it.grade == 0 && it.classCode == "전체" })
    }

    @Test fun mergesRegularExamVariantsWithinSamePeriod() {
        val rows = listOf(
            TimetableItem(date, 1, "경영1", 1, "1차 정기시험"),
            TimetableItem(date, 3, "경영1", 1, "2차 정기시험"),
            TimetableItem(date, 1, "경영2", 1, "1차 정기시험")
        )

        val result = collapseSharedCommonEvents(rows)

        assertEquals(1, result.size)
        assertEquals("정기시험", result.single().subject)
        assertEquals(1, result.single().period)
        assertEquals("전체", result.single().classCode)
    }

    @Test fun preservesUnrelatedEventNamesWithinSamePeriod() {
        val rows = listOf(
            TimetableItem(date, 1, "경영1", 1, "진로교육"),
            TimetableItem(date, 3, "경영1", 1, "체육대회")
        )

        val result = collapseSharedCommonEvents(rows)

        assertEquals(listOf("진로교육", "체육대회"), result.map { it.subject })
    }

    @Test fun doesNotTreatNormalTeacherRowsAsCommonEvents() {
        val rows = listOf(
            TimetableItem(date, 1, "경영1", 1, "회계", "강성호", "실습실"),
            TimetableItem(date, 1, "경영2", 1, "영어", "홍길동", "")
        )

        assertTrue(collapseSharedCommonEvents(rows).isEmpty())
    }

    @Test fun ignoresBlankSpecialSlotsWhenCommonEventsExist() {
        val rows = listOf(
            TimetableItem(date, 1, "경영1", 1, "1차 정기시험"),
            TimetableItem(date, 3, "경영1", 1, "2차 정기시험"),
            TimetableItem(date, 3, "IT1", 7, "")
        )

        val result = collapseSharedCommonEvents(rows)

        assertEquals(listOf("정기시험"), result.map { it.subject })
    }

    @Test fun rejectsBlankSubjects() {
        val rows = listOf(TimetableItem(date, 1, "경영1", 1, ""))

        assertTrue(collapseSharedCommonEvents(rows).isEmpty())
    }
}
