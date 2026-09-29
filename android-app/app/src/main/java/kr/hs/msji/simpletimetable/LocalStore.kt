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
        items.forEach {
            arr.put(JSONObject().put("id", it.id).put("text", it.text).put("createdAt", it.createdAt))
        }
        prefs.edit().putString("memos", arr.toString()).apply()
    }

    fun loadMemos(): List<MemoItem> = runCatching {
        val arr = JSONArray(prefs.getString("memos", "[]"))
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            MemoItem(o.getLong("id"), o.getString("text"), o.optLong("createdAt"))
        }
    }.getOrDefault(emptyList())

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
