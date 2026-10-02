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
import java.time.YearMonth

data class AppUiState(
    val loading: Boolean = false,
    val loggedIn: Boolean = false,
    val profile: UserProfile = UserProfile(),
    val today: String = LocalDate.now().toString(),
    val myTimetable: List<TimetableItem> = emptyList(),
    val allTimetable: List<TimetableItem> = emptyList(),
    val memos: List<MemoItem> = emptyList(),
    val notePages: List<NotePage> = emptyList(),
    val pinnedNoteCount: Int? = null,
    val todos: List<TodoItem> = emptyList(),
    val calendar: List<CalendarItem> = emptyList(),
    val calendarTimetable: List<TimetableItem> = emptyList(),
    val calendarTimetableMonth: String = "",
    val classrooms: List<Classroom> = emptyList(),
    val lastLoginId: String = "",
    val message: String = ""
)

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val store = LocalStore(application)
    private val api = ScerpApi(store)
    private val trashRetentionMillis = 30L * 24L * 60L * 60L * 1000L
    private val initialNotePages = run {
        store.ensureNoteAccount(store.userId)
        store.loadNotePages()
    }
    private val initialMemos = store.loadMemos().let { items ->
        val cutoff = System.currentTimeMillis() - trashRetentionMillis
        val cleaned = items.filterNot { it.deletedAt > 0L && it.deletedAt < cutoff }
        if (cleaned.size != items.size) store.saveMemos(cleaned)
        cleaned
    }
    private val _state = MutableStateFlow(
        AppUiState(
            loggedIn = store.userId.isNotBlank() && store.sessionCookie.isNotBlank(),
            profile = UserProfile(store.userId, store.displayName),
            myTimetable = decodeTimetable(store.latestTimetableJson),
            memos = initialMemos,
            notePages = initialNotePages,
            todos = store.loadTodos(),
            calendar = store.loadCalendar(),
            lastLoginId = store.loginId,
            message = if (store.userId.isNotBlank() && store.sessionCookie.isBlank()) {
                "업데이트 후 최초 1회 로그인이 필요합니다."
            } else ""
        )
    )
    val state: StateFlow<AppUiState> = _state

    init {
        if (_state.value.loggedIn) {
            viewModelScope.launch(Dispatchers.IO) {
                val userId = _state.value.profile.userId.ifBlank { store.userId }
                if (userId.isNotBlank()) {
                    runTask { refreshTodayDirect(userId) }
                }
            }
        }
    }

    fun login(loginId: String, password: String) = viewModelScope.launch(Dispatchers.IO) {
        runTask {
            val previousUserId = store.userId
            val profile = api.login(loginId, password)
            store.switchNoteAccount(previousUserId, profile.userId)
            store.userId = profile.userId
            store.displayName = profile.displayName
            store.loginId = loginId.trim()
            val accountNotes = store.loadNotePages()
            _state.value = _state.value.copy(
                loggedIn = true,
                profile = profile,
                notePages = accountNotes,
                pinnedNoteCount = null
            )
            refreshTodayDirect(profile.userId)
        }
    }

    fun refreshToday() = viewModelScope.launch(Dispatchers.IO) {
        val userId = _state.value.profile.userId.ifBlank { store.userId }
        if (userId.isBlank()) return@launch
        runTask { refreshDateDirect(_state.value.today, userId) }
    }

    fun moveDate(days: Long) {
        val base = runCatching { LocalDate.parse(_state.value.today) }.getOrDefault(LocalDate.now())
        loadDate(base.plusDays(days))
    }

    fun goToToday() {
        loadDate(LocalDate.now())
    }

    fun selectDate(date: LocalDate) {
        loadDate(date)
    }

    private fun loadDate(date: LocalDate) = viewModelScope.launch(Dispatchers.IO) {
        val userId = _state.value.profile.userId.ifBlank { store.userId }
        if (userId.isBlank()) return@launch
        runTask { refreshDateDirect(date.toString(), userId) }
    }

    private fun refreshTodayDirect(userId: String) {
        refreshDateDirect(LocalDate.now().toString(), userId)
    }

    private fun refreshDateDirect(date: String, userId: String) {
        val before = _state.value.myTimetable
        val displayName = _state.value.profile.displayName.ifBlank { store.displayName }
        val rows = api.fetchMyTimetable(date, userId, displayName)
        val isActualToday = date == LocalDate.now().toString()

        if (isActualToday) {
            val changed = before.isNotEmpty() && before != rows
            store.latestTimetableJson = encodeTimetable(rows)
            TimetableWidget.updateAll(getApplication())
            if (changed) {
                NotificationHelper.showTimetableChanged(getApplication())
            }
        }

        _state.value = _state.value.copy(today = date, myTimetable = rows)
    }

    fun preloadAllIfNeeded() {
        if (!_state.value.loggedIn || _state.value.allTimetable.isNotEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { refreshAllWeekDirect(_state.value.today) }
        }
    }

    fun loadCalendarTimetableMonth(month: YearMonth) {
        if (!_state.value.loggedIn) return
        val monthKey = month.toString()
        if (_state.value.calendarTimetableMonth == monthKey && _state.value.calendarTimetable.isNotEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                val userId = _state.value.profile.userId.ifBlank { store.userId }
                if (userId.isBlank()) return@runCatching
                val displayName = _state.value.profile.displayName.ifBlank { store.displayName }
                val rows = (1..month.lengthOfMonth())
                    .map { month.atDay(it) }
                    .filter { it.dayOfWeek.value in 1..5 }
                    .flatMap { date ->
                        runCatching {
                            api.fetchMyTimetable(date.toString(), userId, displayName)
                        }.getOrDefault(emptyList())
                    }
                    .filter { it.subject.isNotBlank() || it.room.isNotBlank() || it.startTime.isNotBlank() }
                    .distinctBy { "${it.date}-${it.period}-${it.subject}-${it.room}" }
                    .sortedWith(compareBy<TimetableItem>({ it.date }, { it.period }))
                _state.value = _state.value.copy(
                    calendarTimetable = rows,
                    calendarTimetableMonth = monthKey
                )
            }
        }
    }

    fun preloadClassroomsIfNeeded() {
        if (!_state.value.loggedIn || _state.value.classrooms.isNotEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                val rooms = api.fetchClassrooms()
                _state.value = _state.value.copy(classrooms = rooms)
            }
        }
    }

    fun refreshAll(date: String = _state.value.today) = viewModelScope.launch(Dispatchers.IO) {
        runTask { refreshAllWeekDirect(date) }
    }

    fun moveAllWeek(weeks: Long) = viewModelScope.launch(Dispatchers.IO) {
        val base = runCatching { LocalDate.parse(_state.value.today) }.getOrDefault(LocalDate.now())
        runTask { refreshAllWeekDirect(base.plusWeeks(weeks).toString()) }
    }

    private fun refreshAllWeekDirect(anchorDate: String) {
        val anchor = runCatching { LocalDate.parse(anchorDate) }.getOrDefault(LocalDate.now())
        val monday = anchor.minusDays((anchor.dayOfWeek.value - 1).toLong())
        val dates = (0L..4L).map { monday.plusDays(it) }
        val rows = dates.flatMap { api.fetchPublicTimetable(it.toString()) }
        _state.value = _state.value.copy(allTimetable = rows, today = anchor.toString())
    }

    fun preloadNotes() {
        if (!_state.value.loggedIn) return
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                val local = store.loadNotePages()
                val remote = api.fetchNotePages()
                val deletedIds = store.deletedNoteIds()
                val pendingById = local
                    .filter { it.syncState != "SYNCED" }
                    .associateBy { it.id }
                val effective = remote
                    .filterNot { it.id in deletedIds }
                    .map { summary -> pendingById[summary.id] ?: summary }
                    .plus(
                        pendingById.values.filter { page ->
                            page.id !in deletedIds && remote.none { it.id == page.id }
                        }
                    )
                    .distinctBy { it.id }

                _state.value = _state.value.copy(
                    pinnedNoteCount = effective.count { it.pinned && !it.archived }
                )
            }
        }
    }

    fun refreshNotes() = viewModelScope.launch(Dispatchers.IO) {
        runTask { refreshNotesDirect() }
    }

    private fun refreshNotesDirect() {
        val local = store.loadNotePages()
        val uploaded = local.filter { it.syncState != "SYNCED" }.map { page -> api.saveNote(page) }
        val remote = api.fetchNotePages()
        val deletedIds = store.deletedNoteIds()
        val uploadedById = uploaded.associateBy { it.id }
        val merged = remote.filterNot { it.id in deletedIds }.map { summary -> uploadedById[summary.id] ?: summary }
            .plus(uploaded.filter { saved -> saved.id !in deletedIds && remote.none { it.id == saved.id } })
            .distinctBy { it.id }
            .sortedWith(compareByDescending<NotePage> { it.pinned }.thenByDescending { it.updatedAt })
        store.saveNotePages(merged)
        _state.value = _state.value.copy(
            notePages = merged,
            pinnedNoteCount = merged.count { it.pinned && !it.archived }
        )
    }

    fun openNotePage(id: String) = viewModelScope.launch(Dispatchers.IO) {
        runTask {
            val page = api.fetchNote(id)
            val list = _state.value.notePages.filterNot { it.id == id } + page
            store.saveNotePages(list)
            _state.value = _state.value.copy(
                notePages = list,
                pinnedNoteCount = list.count { it.pinned && !it.archived }
            )
        }
    }

    fun saveNotePage(page: NotePage) = viewModelScope.launch(Dispatchers.IO) {
        val pending = page.copy(syncState = "PENDING")
        val local = (_state.value.notePages.filterNot { it.id == page.id } + pending)
            .sortedWith(compareByDescending<NotePage> { it.pinned }.thenByDescending { it.updatedAt })
        store.saveNotePages(local)
        _state.value = _state.value.copy(
            notePages = local,
            pinnedNoteCount = local.count { it.pinned && !it.archived }
        )
        runTask {
            val saved = api.saveNote(pending)
            val next = (_state.value.notePages.filterNot { it.id == saved.id } + saved)
                .sortedWith(compareByDescending<NotePage> { it.pinned }.thenByDescending { it.updatedAt })
            store.saveNotePages(next)
            _state.value = _state.value.copy(
                notePages = next,
                pinnedNoteCount = next.count { it.pinned && !it.archived }
            )
        }
        if (_state.value.message.isNotBlank()) {
            val failed = _state.value.notePages.map {
                if (it.id == page.id && it.syncState == "PENDING") it.copy(syncState = "FAILED") else it
            }
            store.saveNotePages(failed)
            _state.value = _state.value.copy(
                notePages = failed,
                pinnedNoteCount = failed.count { it.pinned && !it.archived }
            )
        }
    }

    fun archiveNotePage(id: String) = viewModelScope.launch(Dispatchers.IO) {
        val next = _state.value.notePages.filterNot { it.id == id }
        store.markNoteDeleted(id)
        store.saveNotePages(next)
        _state.value = _state.value.copy(
            notePages = next,
            pinnedNoteCount = next.count { it.pinned && !it.archived }
        )
        runTask {
            api.archiveNote(id)
        }
    }
    fun addMemo(
        text: String,
        category: String = "일반",
        checklist: Boolean = false,
        title: String = "",
        priority: Int = 0,
        attachmentUris: List<String> = emptyList()
    ) {
        if (text.isBlank()) return
        val now = System.currentTimeMillis()
        val checks = if (checklist) {
            text.lineSequence()
                .map { it.trim().removePrefix("-").removePrefix("•").trim() }
                .filter { it.isNotBlank() }
                .map { MemoCheckItem(it, false) }
                .toList()
        } else {
            emptyList()
        }
        val list = _state.value.memos + MemoItem(
            id = now,
            text = text.trim(),
            createdAt = now,
            updatedAt = now,
            category = category,
            checklist = checklist,
            checkItems = checks,
            title = title.trim(),
            priority = priority.coerceIn(0, 2),
            attachmentUris = attachmentUris.distinct()
        )
        store.saveMemos(list)
        store.clearMemoDraft()
        _state.value = _state.value.copy(memos = list)
    }

    fun updateMemo(
        id: Long,
        text: String,
        category: String? = null,
        checklist: Boolean? = null,
        title: String? = null,
        priority: Int? = null,
        attachmentUris: List<String>? = null
    ) {
        if (text.isBlank()) return
        val now = System.currentTimeMillis()
        val list = _state.value.memos.map { memo ->
            if (memo.id != id) memo else {
                val nextChecklist = checklist ?: memo.checklist
                val nextChecks = if (nextChecklist) {
                    val oldByText = memo.checkItems.associateBy { it.text }
                    text.lineSequence()
                        .map { it.trim().removePrefix("-").removePrefix("•").trim() }
                        .filter { it.isNotBlank() }
                        .map { value -> oldByText[value] ?: MemoCheckItem(value, false) }
                        .toList()
                } else emptyList()
                memo.copy(
                    text = text.trim(),
                    updatedAt = now,
                    category = category ?: memo.category,
                    checklist = nextChecklist,
                    checkItems = nextChecks,
                    title = title?.trim() ?: memo.title,
                    priority = priority?.coerceIn(0, 2) ?: memo.priority,
                    attachmentUris = attachmentUris?.distinct() ?: memo.attachmentUris
                )
            }
        }
        store.saveMemos(list)
        _state.value = _state.value.copy(memos = list)
    }

    fun toggleMemoPin(id: Long) {
        val list = _state.value.memos.map { if (it.id == id) it.copy(pinned = !it.pinned, updatedAt = System.currentTimeMillis()) else it }
        store.saveMemos(list)
        _state.value = _state.value.copy(memos = list)
    }

    fun toggleMemoCheck(id: Long, index: Int) {
        val list = _state.value.memos.map { memo ->
            if (memo.id != id || index !in memo.checkItems.indices) memo else {
                val checks = memo.checkItems.mapIndexed { i, item -> if (i == index) item.copy(done = !item.done) else item }
                memo.copy(checkItems = checks, updatedAt = System.currentTimeMillis())
            }
        }
        store.saveMemos(list)
        _state.value = _state.value.copy(memos = list)
    }

    fun deleteMemo(id: Long) {
        val now = System.currentTimeMillis()
        val list = _state.value.memos.map {
            if (it.id == id) it.copy(deletedAt = now, pinned = false, updatedAt = now) else it
        }
        store.saveMemos(list)
        _state.value = _state.value.copy(memos = list)
    }

    fun restoreMemo(id: Long) {
        val now = System.currentTimeMillis()
        val list = _state.value.memos.map {
            if (it.id == id) it.copy(deletedAt = 0L, updatedAt = now) else it
        }
        store.saveMemos(list)
        _state.value = _state.value.copy(memos = list)
    }

    fun permanentlyDeleteMemo(id: Long) {
        val list = _state.value.memos.filterNot { it.id == id }
        store.saveMemos(list)
        _state.value = _state.value.copy(memos = list)
    }

    fun addMemoAttachments(id: Long, uris: List<String>) {
        if (uris.isEmpty()) return
        val now = System.currentTimeMillis()
        val list = _state.value.memos.map { memo ->
            if (memo.id != id) memo else memo.copy(
                attachmentUris = (memo.attachmentUris + uris).distinct(),
                updatedAt = now
            )
        }
        store.saveMemos(list)
        _state.value = _state.value.copy(memos = list)
    }

    fun removeMemoAttachment(id: Long, uri: String) {
        val now = System.currentTimeMillis()
        val list = _state.value.memos.map { memo ->
            if (memo.id != id) memo else memo.copy(
                attachmentUris = memo.attachmentUris.filterNot { it == uri },
                updatedAt = now
            )
        }
        store.saveMemos(list)
        _state.value = _state.value.copy(memos = list)
    }

    fun setMemoReminder(id: Long, triggerAt: Long) {
        val memo = _state.value.memos.firstOrNull { it.id == id } ?: return
        if (triggerAt <= System.currentTimeMillis()) return
        val now = System.currentTimeMillis()
        val list = _state.value.memos.map {
            if (it.id == id) it.copy(reminderAt = triggerAt, updatedAt = now) else it
        }
        store.saveMemos(list)
        _state.value = _state.value.copy(memos = list)
        MemoReminderScheduler.schedule(
            getApplication(),
            id,
            triggerAt,
            memo.title.ifBlank { "메모 알림" },
            memo.text
        )
    }

    fun clearMemoReminder(id: Long) {
        val now = System.currentTimeMillis()
        val list = _state.value.memos.map {
            if (it.id == id) it.copy(reminderAt = 0L, updatedAt = now) else it
        }
        store.saveMemos(list)
        _state.value = _state.value.copy(memos = list)
        MemoReminderScheduler.cancel(getApplication(), id)
    }

    fun exportMemosJson(): String = store.exportMemosJson()

    fun importMemosJson(raw: String): Boolean = runCatching {
        val list = store.importMemosJson(raw)
        _state.value = _state.value.copy(memos = list)
        true
    }.getOrDefault(false)

    fun cleanupExpiredTrash() {
        val cutoff = System.currentTimeMillis() - trashRetentionMillis
        val list = _state.value.memos.filterNot { it.deletedAt > 0L && it.deletedAt < cutoff }
        if (list.size != _state.value.memos.size) {
            store.saveMemos(list)
            _state.value = _state.value.copy(memos = list)
        }
    }

    fun toggleMemoArchive(id: Long) {
        val list = _state.value.memos.map {
            if (it.id == id) it.copy(archived = !it.archived, updatedAt = System.currentTimeMillis()) else it
        }
        store.saveMemos(list)
        _state.value = _state.value.copy(memos = list)
    }

    fun memoToTodo(id: Long, dueDate: String) {
        val memo = _state.value.memos.firstOrNull { it.id == id } ?: return
        addTodo(memo.text, dueDate)
    }

    fun memoToCalendar(id: Long, date: String) {
        val memo = _state.value.memos.firstOrNull { it.id == id } ?: return
        addCalendar(memo.text.lineSequence().firstOrNull().orEmpty().ifBlank { memo.text }, date)
    }

    fun saveMemoDraft(
        text: String,
        category: String,
        checklist: Boolean,
        title: String = "",
        priority: Int = 0
    ) {
        store.memoDraftText = text
        store.memoDraftCategory = category
        store.memoDraftChecklist = checklist
        store.memoDraftTitle = title
        store.memoDraftPriority = priority
    }

    fun memoDraftText(): String = store.memoDraftText
    fun memoDraftCategory(): String = store.memoDraftCategory
    fun memoDraftChecklist(): Boolean = store.memoDraftChecklist
    fun memoDraftTitle(): String = store.memoDraftTitle
    fun memoDraftPriority(): Int = store.memoDraftPriority

    fun addTodo(text: String, dueDate: String) {
        val normalized = text.trim()
        if (normalized.isBlank()) return
        if (_state.value.todos.any { it.text == normalized && it.dueDate == dueDate }) return
        val list = _state.value.todos + TodoItem(System.currentTimeMillis(), normalized, false, dueDate)
        store.saveTodos(list)
        _state.value = _state.value.copy(todos = list)
    }

    fun noteBlockToTodo(content: String, dueDate: String) {
        addTodo(content, dueDate)
    }

    fun toggleTodo(id: Long) {
        val list = _state.value.todos.map { if (it.id == id) it.copy(done = !it.done) else it }
        store.saveTodos(list)
        _state.value = _state.value.copy(todos = list)
    }

    fun deleteTodo(id: Long) {
        val list = _state.value.todos.filterNot { it.id == id }
        store.saveTodos(list)
        _state.value = _state.value.copy(todos = list)
    }

    fun todoToCalendar(id: Long) {
        val todo = _state.value.todos.firstOrNull { it.id == id } ?: return
        addCalendar(todo.text, todo.dueDate)
    }

    fun addCalendar(title: String, date: String) {
        val normalized = title.trim()
        if (normalized.isBlank() || date.isBlank()) return
        if (_state.value.calendar.any { it.title == normalized && it.date == date }) return
        val list = _state.value.calendar + CalendarItem(System.currentTimeMillis(), normalized, date)
        store.saveCalendar(list)
        _state.value = _state.value.copy(calendar = list)
    }

    fun noteBlockToCalendar(content: String, date: String) {
        addCalendar(content.lineSequence().firstOrNull().orEmpty().ifBlank { content }, date)
    }

    fun deleteCalendar(id: Long) {
        val list = _state.value.calendar.filterNot { it.id == id }
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
            val apiError = e as? ScerpApiException
            if (apiError?.code == "authentication_required") {
                store.clearSession()
                _state.value = _state.value.copy(
                    loggedIn = false,
                    classrooms = emptyList(),
                    message = "로그인 세션이 만료되었습니다. 다시 로그인해 주세요."
                )
            } else {
                _state.value = _state.value.copy(
                    message = apiError?.message ?: e.message ?: "오류가 발생했습니다."
                )
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

