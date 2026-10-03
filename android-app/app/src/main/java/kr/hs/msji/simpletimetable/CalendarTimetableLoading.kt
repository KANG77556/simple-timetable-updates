package kr.hs.msji.simpletimetable

import java.time.LocalDate
import java.time.YearMonth

internal fun calendarTimetableFetchDates(
    month: YearMonth,
    priorityDate: LocalDate?
): List<LocalDate> {
    val weekdays = (1..month.lengthOfMonth())
        .map(month::atDay)
        .filter { it.dayOfWeek.value in 1..5 }

    val priority = priorityDate?.takeIf {
        YearMonth.from(it) == month && it.dayOfWeek.value in 1..5
    }

    return if (priority == null) {
        weekdays
    } else {
        listOf(priority) + weekdays.filterNot { it == priority }
    }
}
