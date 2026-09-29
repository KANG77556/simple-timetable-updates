package kr.hs.msji.simpletimetable

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

data class AppUiState(
    val loading: Boolean = false,
    val loggedIn: Boolean = false,
    val profile: UserProfile = UserProfile(),
    val today: String = LocalDate.now().toString(),
    val myTimetable: List<TimetableItem> = emptyList(),
    val allTimetable: List<TimetableItem> = emptyList(),
    val memos: List<MemoItem> = emptyList(),
    val todos: List<TodoItem> = emptyList(),
    val calendar: List<CalendarItem> = emptyList(),
    val classrooms: List<Classroom> = emptyList(),
    val message: String = ""
)

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val store = LocalStore(application)
    private val api = ScerpApi(store)
    private val _state = MutableStateFlow(
        AppUiState(
            loggedIn = store.userId.isNotBlank() && store.sessionCookie.isNotBlank(),
            profile = UserProfile(store.userId, store.displayName),
            myTimetable = decodeTimetable(store.latestTimetableJson),
            memos = store.loadMemos(),
            todos = store.loadTodos(),
            calendar = store.loadCalendar()
        )
    )
    val state: StateFlow<AppUiState> = _state

    fun login(loginId: String, password: String) = viewModelScope.launch(Dispatchers.IO) {
        runTask {
            val profile = api.login(loginId, password)
            store.userId = profile.userId
            store.displayName = profile.displayName
            _state.value = _state.value.copy(loggedIn = true, profile = profile)
            refreshTodayDirect(profile.userId)
        }
    }

    fun refreshToday() = viewModelScope.launch(Dispatchers.IO) {
        val userId = _state.value.profile.userId.ifBlank { store.userId }
        if (userId.isBlank()) return@launch
        runTask { refreshTodayDirect(userId) }
    }

    private fun refreshTodayDirect(userId: String) {
        val before = _state.value.myTimetable
        val rows = api.fetchMyTimetable(_state.value.today, userId)
        val changed = before.isNotEmpty() && before != rows
        store.latestTimetableJson = encodeTimetable(rows)
        TimetableWidget.updateAll(getApplication())
        if (changed) {
            NotificationHelper.showTimetableChanged(getApplication())
        }
        _state.value = _state.value.copy(myTimetable = rows)
    }

    fun refreshAll(date: String = _state.value.today) = viewModelScope.launch(Dispatchers.IO) {
        runTask {
            val rows = api.fetchPublicTimetable(date)
            _state.value = _state.value.copy(allTimetable = rows, today = date)
        }
    }

    fun addMemo(text: String) {
        if (text.isBlank()) return
        val list = _state.value.memos + MemoItem(System.currentTimeMillis(), text.trim(), System.currentTimeMillis())
        store.saveMemos(list)
        _state.value = _state.value.copy(memos = list)
    }

    fun deleteMemo(id: Long) {
        val list = _state.value.memos.filterNot { it.id == id }
        store.saveMemos(list)
        _state.value = _state.value.copy(memos = list)
    }

    fun addTodo(text: String, dueDate: String) {
        if (text.isBlank()) return
        val list = _state.value.todos + TodoItem(System.currentTimeMillis(), text.trim(), false, dueDate)
        store.saveTodos(list)
        _state.value = _state.value.copy(todos = list)
    }

    fun toggleTodo(id: Long) {
        val list = _state.value.todos.map { if (it.id == id) it.copy(done = !it.done) else it }
        store.saveTodos(list)
        _state.value = _state.value.copy(todos = list)
    }

    fun addCalendar(title: String, date: String) {
        if (title.isBlank() || date.isBlank()) return
        val list = _state.value.calendar + CalendarItem(System.currentTimeMillis(), title.trim(), date)
        store.saveCalendar(list)
        _state.value = _state.value.copy(calendar = list)
    }

    fun loadClassrooms() = viewModelScope.launch(Dispatchers.IO) {
        runTask { _state.value = _state.value.copy(classrooms = api.fetchClassrooms()) }
    }

    fun sendBroadcast(ids: List<String>, text: String, tts: Boolean) = viewModelScope.launch(Dispatchers.IO) {
        runTask {
            val result = api.sendBroadcast(ids, text, tts)
            _state.value = _state.value.copy(message = result)
        }
    }

    private suspend fun runTask(block: suspend () -> Unit) {
        _state.value = _state.value.copy(loading = true, message = "")
        try {
            block()
        } catch (e: Exception) {
            val message = e.message ?: "오류가 발생했습니다."
            val authExpired = message.contains("로그인", ignoreCase = true) ||
                message.contains("authentication", ignoreCase = true) ||
                message.contains("authentication_required", ignoreCase = true)
            if (authExpired) {
                store.clearSession()
                _state.value = _state.value.copy(
                    loggedIn = false,
                    classrooms = emptyList(),
                    message = "로그인 세션이 만료되었습니다. 다시 로그인해 주세요."
                )
            } else {
                _state.value = _state.value.copy(message = message)
            }
        } finally {
            _state.value = _state.value.copy(loading = false)
        }
    }

    companion object {
        private fun encodeTimetable(rows: List<TimetableItem>): String {
            val arr = JSONArray()
            rows.forEach {
                arr.put(
                    JSONObject()
                        .put("date", it.date)
                        .put("grade", it.grade)
                        .put("classCode", it.classCode)
                        .put("period", it.period)
                        .put("subject", it.subject)
                        .put("teacher", it.teacher)
                        .put("room", it.room)
                        .put("startTime", it.startTime)
                        .put("endTime", it.endTime)
                )
            }
            return arr.toString()
        }

        private fun decodeTimetable(raw: String): List<TimetableItem> = runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                TimetableItem(
                    date = o.optString("date"),
                    grade = o.optInt("grade"),
                    classCode = o.optString("classCode"),
                    period = o.optInt("period"),
                    subject = o.optString("subject"),
                    teacher = o.optString("teacher"),
                    room = o.optString("room"),
                    startTime = o.optString("startTime"),
                    endTime = o.optString("endTime")
                )
            }
        }.getOrDefault(emptyList())
    }
}

