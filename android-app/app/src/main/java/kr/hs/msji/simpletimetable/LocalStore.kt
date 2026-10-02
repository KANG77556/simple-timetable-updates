package kr.hs.msji.simpletimetable

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

class LocalStore(context: Context) {
    private val prefs = context.getSharedPreferences("simple_timetable", Context.MODE_PRIVATE)

    var userId: String
        get() = prefs.getString("user_id", "") ?: ""
        set(value) = prefs.edit().putString("user_id", value).apply()

    var displayName: String
        get() = prefs.getString("display_name", "") ?: ""
        set(value) = prefs.edit().putString("display_name", value).apply()

    var loginId: String
        get() = prefs.getString("login_id", "") ?: ""
        set(value) = prefs.edit().putString("login_id", value).apply()

    var sessionCookie: String
        get() = prefs.getString("session_cookie", "") ?: ""
        set(value) = prefs.edit().putString("session_cookie", value).apply()

    fun clearSession() {
        prefs.edit().remove("session_cookie").apply()
    }

    var latestTimetableJson: String
        get() = prefs.getString("today_timetable", "[]") ?: "[]"
        set(value) = prefs.edit().putString("today_timetable", value).apply()

    fun saveMemos(items: List<MemoItem>) {
        val arr = JSONArray()
        items.forEach { memo ->
            val checks = JSONArray()
            memo.checkItems.forEach { item ->
                checks.put(JSONObject().put("text", item.text).put("done", item.done))
            }
            val attachments = JSONArray()
            memo.attachmentUris.forEach { attachments.put(it) }
            arr.put(
                JSONObject()
                    .put("id", memo.id)
                    .put("text", memo.text)
                    .put("createdAt", memo.createdAt)
                    .put("updatedAt", memo.updatedAt)
                    .put("pinned", memo.pinned)
                    .put("category", memo.category)
                    .put("checklist", memo.checklist)
                    .put("checkItems", checks)
                    .put("title", memo.title)
                    .put("priority", memo.priority)
                    .put("archived", memo.archived)
                    .put("attachmentUris", attachments)
                    .put("deletedAt", memo.deletedAt)
                    .put("reminderAt", memo.reminderAt)
            )
        }
        prefs.edit().putString("memos", arr.toString()).apply()
    }

    fun exportMemosJson(): String = prefs.getString("memos", "[]") ?: "[]"

    fun importMemosJson(raw: String): List<MemoItem> {
        JSONArray(raw)
        prefs.edit().putString("memos", raw).apply()
        return loadMemos()
    }

    fun loadMemos(): List<MemoItem> = runCatching {
        val arr = JSONArray(prefs.getString("memos", "[]"))
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            val createdAt = o.optLong("createdAt")
            val checks = o.optJSONArray("checkItems")
            val checkItems = if (checks == null) emptyList() else {
                (0 until checks.length()).map { index ->
                    val item = checks.getJSONObject(index)
                    MemoCheckItem(item.optString("text"), item.optBoolean("done"))
                }
            }
            val attachments = o.optJSONArray("attachmentUris")
            val attachmentUris = if (attachments == null) emptyList() else {
                (0 until attachments.length()).map { index -> attachments.optString(index) }
                    .filter { it.isNotBlank() }
            }
            MemoItem(
                id = o.getLong("id"),
                text = o.optString("text"),
                createdAt = createdAt,
                updatedAt = o.optLong("updatedAt", createdAt),
                pinned = o.optBoolean("pinned"),
                category = o.optString("category", "일반"),
                checklist = o.optBoolean("checklist"),
                checkItems = checkItems,
                title = o.optString("title"),
                priority = o.optInt("priority", 0),
                archived = o.optBoolean("archived", false),
                attachmentUris = attachmentUris,
                deletedAt = o.optLong("deletedAt", 0L),
                reminderAt = o.optLong("reminderAt", 0L)
            )
        }
    }.getOrDefault(emptyList())

    var memoDraftText: String
        get() = prefs.getString("memo_draft_text", "") ?: ""
        set(value) = prefs.edit().putString("memo_draft_text", value).apply()

    var memoDraftCategory: String
        get() = prefs.getString("memo_draft_category", "일반") ?: "일반"
        set(value) = prefs.edit().putString("memo_draft_category", value).apply()

    var memoDraftChecklist: Boolean
        get() = prefs.getBoolean("memo_draft_checklist", false)
        set(value) = prefs.edit().putBoolean("memo_draft_checklist", value).apply()

    var memoDraftTitle: String
        get() = prefs.getString("memo_draft_title", "") ?: ""
        set(value) = prefs.edit().putString("memo_draft_title", value).apply()

    var memoDraftPriority: Int
        get() = prefs.getInt("memo_draft_priority", 0)
        set(value) = prefs.edit().putInt("memo_draft_priority", value).apply()

    fun clearMemoDraft() {
        prefs.edit()
            .remove("memo_draft_text")
            .remove("memo_draft_category")
            .remove("memo_draft_checklist")
            .remove("memo_draft_title")
            .remove("memo_draft_priority")
            .apply()
    }

    fun saveTodos(items: List<TodoItem>) {
        val arr = JSONArray()
        items.forEach {
            arr.put(JSONObject().put("id", it.id).put("text", it.text).put("done", it.done).put("dueDate", it.dueDate))
        }
        prefs.edit().putString("todos", arr.toString()).apply()
    }

    fun loadTodos(): List<TodoItem> = runCatching {
        val arr = JSONArray(prefs.getString("todos", "[]"))
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            TodoItem(o.getLong("id"), o.getString("text"), o.optBoolean("done"), o.optString("dueDate"))
        }
    }.getOrDefault(emptyList())

    fun saveCalendar(items: List<CalendarItem>) {
        val arr = JSONArray()
        items.forEach {
            arr.put(JSONObject().put("id", it.id).put("title", it.title).put("date", it.date))
        }
        prefs.edit().putString("calendar", arr.toString()).apply()
    }

    fun loadCalendar(): List<CalendarItem> = runCatching {
        val arr = JSONArray(prefs.getString("calendar", "[]"))
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            CalendarItem(o.getLong("id"), o.getString("title"), o.getString("date"))
        }
    }.getOrDefault(emptyList())

    fun saveNotePages(items: List<NotePage>) {
        val arr = JSONArray()
        items.forEach { page ->
            val tags = JSONArray().also { a -> page.tags.forEach(a::put) }
            val blocks = JSONArray()
            page.blocks.sortedBy { it.position }.forEach { block ->
                blocks.put(
                    JSONObject()
                        .put("id", block.id)
                        .put("type", block.type.wireName)
                        .put("content", block.content)
                        .put("checked", block.checked)
                        .put("position", block.position)
                )
            }
            arr.put(
                JSONObject()
                    .put("id", page.id)
                    .put("title", page.title)
                    .put("category", page.category)
                    .put("tags", tags)
                    .put("pinned", page.pinned)
                    .put("archived", page.archived)
                    .put("version", page.version)
                    .put("createdAt", page.createdAt)
                    .put("updatedAt", page.updatedAt)
                    .put("syncState", page.syncState)
                    .put("blocks", blocks)
            )
        }
        prefs.edit()
            .putString("note_pages_v1", arr.toString())
            .putBoolean("note_pages_initialized_v1", true)
            .apply()
    }

    fun deletedNoteIds(): Set<String> = prefs.getStringSet("deleted_note_ids_v1", emptySet())?.toSet() ?: emptySet()

    fun markNoteDeleted(id: String) {
        if (id.isBlank()) return
        prefs.edit().putStringSet("deleted_note_ids_v1", deletedNoteIds() + id).apply()
    }

    fun loadNotePages(): List<NotePage> {
        val stored = runCatching {
            val arr = JSONArray(prefs.getString("note_pages_v1", "[]"))
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                val tagsJson = o.optJSONArray("tags") ?: JSONArray()
                val blocksJson = o.optJSONArray("blocks") ?: JSONArray()
                NotePage(
                    id = o.optString("id"),
                    title = o.optString("title"),
                    category = o.optString("category", "개인"),
                    tags = (0 until tagsJson.length()).map { j -> tagsJson.optString(j) }.filter { it.isNotBlank() },
                    pinned = o.optBoolean("pinned"),
                    archived = o.optBoolean("archived"),
                    version = o.optLong("version", 0),
                    createdAt = o.optString("createdAt"),
                    updatedAt = o.optString("updatedAt"),
                    syncState = o.optString("syncState", "LOCAL"),
                    blocks = (0 until blocksJson.length()).map { j ->
                        val b = blocksJson.getJSONObject(j)
                        NoteBlock(
                            id = b.optString("id"),
                            type = NoteBlockType.fromWire(b.optString("type", "text")),
                            content = b.optString("content"),
                            checked = b.optBoolean("checked"),
                            position = b.optInt("position", j)
                        )
                    }
                )
            }
        }.getOrDefault(emptyList())
        if (stored.isNotEmpty()) return stored
        if (prefs.getBoolean("note_pages_initialized_v1", false)) return emptyList()

        val legacy = loadMemos().filter { it.deletedAt == 0L && !it.archived }
        if (legacy.isEmpty()) return emptyList()
        val migrated = legacy.map { memo ->
            val blocks = if (memo.checklist && memo.checkItems.isNotEmpty()) {
                memo.checkItems.mapIndexed { index, item ->
                    NoteBlock(
                        id = java.util.UUID.randomUUID().toString(),
                        type = NoteBlockType.TODO,
                        content = item.text,
                        checked = item.done,
                        position = index
                    )
                }
            } else {
                listOf(
                    NoteBlock(
                        id = java.util.UUID.randomUUID().toString(),
                        type = NoteBlockType.TEXT,
                        content = memo.text,
                        position = 0
                    )
                )
            }
            NotePage(
                id = java.util.UUID.randomUUID().toString(),
                title = memo.title.ifBlank { memo.text.lineSequence().firstOrNull()?.take(60).orEmpty().ifBlank { "메모" } },
                category = memo.category.ifBlank { "개인" },
                pinned = memo.pinned,
                version = 0,
                createdAt = java.time.Instant.ofEpochMilli(memo.createdAt).toString(),
                updatedAt = java.time.Instant.ofEpochMilli(memo.updatedAt).toString(),
                blocks = blocks,
                syncState = "LOCAL"
            )
        }
        saveNotePages(migrated)
        return migrated
    }
}
