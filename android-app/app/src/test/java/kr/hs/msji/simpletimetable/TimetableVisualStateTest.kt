package kr.hs.msji.simpletimetable

import java.time.LocalDate
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TimetableVisualStateTest {
    @Test
    fun currentLesson_matchesDateAndTimeWindow() {
        val row = TimetableItem(
            date = "2026-10-02",
            grade = 3,
            classCode = "경영1",
            period = 3,
            subject = "회실",
            teacher = "강성",
            room = "공용실습실",
            startTime = "10:55",
            endTime = "11:45"
        )

        assertTrue(
            isCurrentTimetableLesson(
                row = row,
                today = LocalDate.of(2026, 10, 2),
                now = LocalTime.of(11, 20)
            )
        )
        assertFalse(
            isCurrentTimetableLesson(
                row = row,
                today = LocalDate.of(2026, 10, 1),
                now = LocalTime.of(11, 20)
            )
        )
        assertFalse(
            isCurrentTimetableLesson(
                row = row,
                today = LocalDate.of(2026, 10, 2),
                now = LocalTime.of(11, 50)
            )
        )
    }

    @Test
    fun subjectColorIndexes_areStableAndDistinctWithinPaletteCapacity() {
        val subjects = listOf("세실", "영문", "총무", "회실", "기자", "전실", "스생", "진로")
        val first = timetableSubjectColorIndexes(subjects)
        val second = timetableSubjectColorIndexes(subjects.reversed())

        assertEquals(first, second)
        assertEquals(subjects.size, first.values.toSet().size)
    }

    @Test
    fun subjectColorIndexes_doNotChangeWhenOtherSubjectsAreAddedOrRemoved() {
        val single = timetableSubjectColorIndexes(listOf("세실", "회실"))
        val larger = timetableSubjectColorIndexes(listOf("영문", "기차", "총무", "세실", "회실", "전실"))
        assertEquals(single["세실"], larger["세실"])
        assertEquals(single["회실"], larger["회실"])
        assertEquals(2, single["세실"])
        assertEquals(10, single["회실"])
    }

    @Test
    fun subjectColorIndexes_trimNamesAndPreserveNewSubjectColors() {
        val name = "신규상업과목"
        val fromOneWeek = timetableSubjectColorIndexes(listOf(" $name ", "세실"))
        val fromAnotherWeek = timetableSubjectColorIndexes(listOf("전실", name, "총무"))
        assertEquals(fromOneWeek[name], fromAnotherWeek[name])
    }
    @Test
    fun noteNumberOrdinal_resetsAfterNonNumberBlock() {
        val blocks = listOf(
            NoteBlock("a", NoteBlockType.TEXT),
            NoteBlock("b", NoteBlockType.NUMBER),
            NoteBlock("c", NoteBlockType.NUMBER),
            NoteBlock("d", NoteBlockType.TEXT),
            NoteBlock("e", NoteBlockType.NUMBER)
        )
        assertEquals(1, noteNumberOrdinal(blocks, 1))
        assertEquals(2, noteNumberOrdinal(blocks, 2))
        assertEquals(1, noteNumberOrdinal(blocks, 4))
    }

    @Test
    fun pinnedNotePageCount_ignoresLegacyAndArchivedPages() {
        val pages = listOf(
            NotePage(id = "active", title = "활성 고정", pinned = true, archived = false),
            NotePage(id = "archived", title = "보관 고정", pinned = true, archived = true),
            NotePage(id = "plain", title = "일반", pinned = false, archived = false)
        )

        assertEquals(1, countPinnedActiveNotePages(pages))
        assertEquals(0, countPinnedActiveNotePages(emptyList()))
    }

}
