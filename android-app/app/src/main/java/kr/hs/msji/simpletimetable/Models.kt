package kr.hs.msji.simpletimetable

data class UserProfile(
    val userId: String = "",
    val displayName: String = "",
    val schoolName: String = "밀성제일고등학교"
)

data class TimetableItem(
    val date: String,
    val grade: Int = 0,
    val classCode: String = "",
    val period: Int,
    val subject: String,
    val teacher: String = "",
    val room: String = "",
    val startTime: String = "",
    val endTime: String = ""
)

data class MemoItem(val id: Long, val text: String, val createdAt: Long)
data class TodoItem(val id: Long, val text: String, val done: Boolean, val dueDate: String)
data class CalendarItem(val id: Long, val title: String, val date: String)
data class Classroom(val id: String, val name: String, val onlineCount: Int = 0)
