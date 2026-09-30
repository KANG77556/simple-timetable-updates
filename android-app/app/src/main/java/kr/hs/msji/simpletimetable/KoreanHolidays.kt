package kr.hs.msji.simpletimetable

import java.time.LocalDate

object KoreanHolidays {
    private val holidays2026 = mapOf(
        "2026-01-01" to "신정",
        "2026-02-16" to "설날 연휴",
        "2026-02-17" to "설날",
        "2026-02-18" to "설날 연휴",
        "2026-03-01" to "삼일절",
        "2026-03-02" to "대체공휴일",
        "2026-05-01" to "노동절",
        "2026-05-05" to "어린이날",
        "2026-05-24" to "부처님오신날",
        "2026-05-25" to "대체공휴일",
        "2026-06-03" to "전국동시지방선거",
        "2026-06-06" to "현충일",
        "2026-07-17" to "제헌절",
        "2026-08-15" to "광복절",
        "2026-08-17" to "대체공휴일",
        "2026-09-24" to "추석 연휴",
        "2026-09-25" to "추석",
        "2026-09-26" to "추석 연휴",
        "2026-10-03" to "개천절",
        "2026-10-05" to "대체공휴일",
        "2026-10-09" to "한글날",
        "2026-12-25" to "기독탄신일"
    )

    private val holidays2027 = mapOf(
        "2027-01-01" to "신정",
        "2027-02-06" to "설날 연휴",
        "2027-02-07" to "설날",
        "2027-02-08" to "설날 연휴",
        "2027-02-09" to "대체공휴일",
        "2027-03-01" to "삼일절",
        "2027-05-01" to "노동절",
        "2027-05-03" to "대체공휴일",
        "2027-05-05" to "어린이날",
        "2027-05-13" to "부처님오신날",
        "2027-06-06" to "현충일",
        "2027-07-17" to "제헌절",
        "2027-07-19" to "대체공휴일",
        "2027-08-15" to "광복절",
        "2027-08-16" to "대체공휴일",
        "2027-09-14" to "추석 연휴",
        "2027-09-15" to "추석",
        "2027-09-16" to "추석 연휴",
        "2027-10-03" to "개천절",
        "2027-10-04" to "대체공휴일",
        "2027-10-09" to "한글날",
        "2027-10-11" to "대체공휴일",
        "2027-12-25" to "기독탄신일",
        "2027-12-27" to "대체공휴일"
    )

    fun name(date: LocalDate): String? =
        when (date.year) {
            2026 -> holidays2026[date.toString()]
            2027 -> holidays2027[date.toString()]
            else -> fixedHolidayName(date)
        }

    fun isHoliday(date: LocalDate): Boolean = name(date) != null

    private fun fixedHolidayName(date: LocalDate): String? = when {
        date.monthValue == 1 && date.dayOfMonth == 1 -> "신정"
        date.monthValue == 3 && date.dayOfMonth == 1 -> "삼일절"
        date.monthValue == 5 && date.dayOfMonth == 1 -> "노동절"
        date.monthValue == 5 && date.dayOfMonth == 5 -> "어린이날"
        date.monthValue == 6 && date.dayOfMonth == 6 -> "현충일"
        date.monthValue == 7 && date.dayOfMonth == 17 -> "제헌절"
        date.monthValue == 8 && date.dayOfMonth == 15 -> "광복절"
        date.monthValue == 10 && date.dayOfMonth == 3 -> "개천절"
        date.monthValue == 10 && date.dayOfMonth == 9 -> "한글날"
        date.monthValue == 12 && date.dayOfMonth == 25 -> "기독탄신일"
        else -> null
    }
}
