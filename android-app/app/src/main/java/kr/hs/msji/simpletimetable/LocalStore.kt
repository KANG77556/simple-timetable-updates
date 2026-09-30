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
}
