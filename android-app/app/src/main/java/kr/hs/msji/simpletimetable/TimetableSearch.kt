package kr.hs.msji.simpletimetable

internal fun TimetableItem.hasVisibleLessonContent(): Boolean =
    subject.isNotBlank() || teacher.isNotBlank() || room.isNotBlank()

internal fun searchTimetableRows(
    rows: List<TimetableItem>,
    query: String,
    selectedGrade: Int
): List<TimetableItem> {
    val gradeRows = rows.filter { it.grade == selectedGrade && it.hasVisibleLessonContent() }
    val needle = query.trim().lowercase()
    if (needle.isBlank()) return gradeRows

    return gradeRows.filter { row ->
        val periodText = "${row.period}교시"
        listOf(
            row.classCode,
            row.subject,
            row.teacher,
            row.room,
            row.period.toString(),
            periodText
        ).any { it.lowercase().contains(needle) }
    }
}
