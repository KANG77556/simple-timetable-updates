package kr.hs.msji.simpletimetable

/** Serializes selection and result application without holding a lock during network IO. */
internal class TimetableRequests {
    data class Request(val generation: Long, val date: String)
    private var generation = 0L

    @Synchronized
    fun begin(date: String, select: () -> Unit = {}): Request {
        val request = Request(++generation, date)
        select()
        return request
    }

    @Synchronized
    fun applyIfCurrent(request: Request, apply: () -> Unit): Boolean {
        if (request.generation != generation) return false
        apply()
        return true
    }
}

internal fun timetableChangedForDate(
    date: String,
    before: List<TimetableItem>,
    after: List<TimetableItem>
): Boolean = before.isNotEmpty() && before.all { it.date == date } && before != after
