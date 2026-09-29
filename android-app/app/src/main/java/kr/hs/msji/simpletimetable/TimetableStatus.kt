package kr.hs.msji.simpletimetable

import java.time.Duration
import java.time.LocalTime

data class ClassStatus(
    val current: TimetableItem? = null,
    val next: TimetableItem? = null,
    val minutesRemaining: Long? = null,
    val minutesUntilNext: Long? = null
)

object TimetableStatus {
    fun calculate(rows: List<TimetableItem>, now: LocalTime = LocalTime.now()): ClassStatus {
        val ordered = rows.sortedBy { it.period }
        val current = ordered.firstOrNull { row ->
            val start = parse(row.startTime)
            val end = parse(row.endTime)
            start != null && end != null && !now.isBefore(start) && now.isBefore(end)
        }
        if (current != null) {
            val end = parse(current.endTime)
            val remaining = end?.let { Duration.between(now, it).toMinutes().coerceAtLeast(0) }
            val next = ordered.firstOrNull { it.period > current.period }
            return ClassStatus(current = current, next = next, minutesRemaining = remaining)
        }

        val next = ordered.firstOrNull { row ->
            val start = parse(row.startTime)
            start != null && now.isBefore(start)
        }
        val until = next?.let { row ->
            parse(row.startTime)?.let { Duration.between(now, it).toMinutes().coerceAtLeast(0) }
        }
        return ClassStatus(next = next, minutesUntilNext = until)
    }

    fun parse(value: String): LocalTime? = runCatching {
        LocalTime.parse(value.take(5))
    }.getOrNull()
}
