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

data class MemoCheckItem(val text: String, val done: Boolean = false)

data class MemoItem(
    val id: Long,
    val text: String,
    val createdAt: Long,
    val updatedAt: Long = createdAt,
    val pinned: Boolean = false,
    val category: String = "일반",
    val checklist: Boolean = false,
    val checkItems: List<MemoCheckItem> = emptyList(),
    val title: String = "",
    val priority: Int = 0,
    val archived: Boolean = false,
    val attachmentUris: List<String> = emptyList(),
    val deletedAt: Long = 0L,
    val reminderAt: Long = 0L
)
data class TodoItem(val id: Long, val text: String, val done: Boolean, val dueDate: String)
data class CalendarItem(val id: Long, val title: String, val date: String)
data class Classroom(val id: String, val name: String, val onlineCount: Int = 0)


enum class NoteBlockType(val wireName: String) {
    TEXT("text"), HEADING1("heading1"), HEADING2("heading2"), BULLET("bullet"), NUMBER("number"),
    TODO("todo"), QUOTE("quote"), DIVIDER("divider"), CODE("code"), LINK("link"), IMAGE("image"), FILE("file");

    companion object {
        fun fromWire(value: String): NoteBlockType =
            entries.firstOrNull { it.wireName == value } ?: TEXT
    }
}

data class NoteBlock(
    val id: String,
    val type: NoteBlockType = NoteBlockType.TEXT,
    val content: String = "",
    val checked: Boolean = false,
    val position: Int = 0
)

data class NotePage(
    val id: String,
    val title: String,
    val category: String = "개인",
    val tags: List<String> = emptyList(),
    val pinned: Boolean = false,
    val archived: Boolean = false,
    val version: Long = 0,
    val createdAt: String = "",
    val updatedAt: String = "",
    val blocks: List<NoteBlock> = emptyList(),
    val syncState: String = "LOCAL"
)
