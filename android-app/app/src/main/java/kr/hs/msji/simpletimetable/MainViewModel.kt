package kr.hs.msji.simpletimetable

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.YearMonth
import java.util.concurrent.ConcurrentHashMap

internal fun shouldShowTimetableLoading(
    forceLoading: Boolean,
    cachedRows: List<TimetableItem>?,
    visibleRows: List<TimetableItem>
): Boolean = forceLoading || (cachedRows == null && visibleRows.isEmpty())

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
    val calendarTimetableUserId: String = "",
    val calendarTimetableError: String = "",
    val calendarTimetableLoading: Boolean = false,
    val calendarTimetableComplete: Boolean = false,
    val classrooms: List<Classroom> = emptyList(),
    val lastLoginId: String = "",
    val timetableError: String = "",
    val message: String = ""
)

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val store = LocalStore(application)
    private val api = ScerpApi(store)
    private val trashRetentionMillis = 30L * 24L * 60L * 60L * 1000L
    @Volatile private var calendarTimetableRequestKey: String = ""
    @Volatile private var calendarTimetableRequestGeneration: Long = 0L
    private var calendarTimetableJob: Job? = null
    private val calendarTimetableLoadedDates = mutableSetOf<String>()
    private val noteMutationLocks = ConcurrentHashMap<String, Mutex>()
    private val startupUserId = store.userId
    private val startupLoggedIn = startupUserId.isNotBlank() && store.sessionCookie.isNotBlank()
    private val _state = MutableStateFlow(
        AppUiState(
            loggedIn = startupLoggedIn,
            profile = UserProfile(startupUserId, store.displayName),
            lastLoginId = store.loginId,
            message = if (startupUserId.isNotBlank() && store.sessionCookie.isBlank()) {
                "업데이트 후 최초 1회 로그인이 필요합니다."
            } else ""
        )
    )
    val state: StateFlow<AppUiState> = _state

    private val timetableRequests = TimetableRequests()
    private val timetableCache = ConcurrentHashMap<String, List<TimetableItem>>()
    private val allTimetableCache = ConcurrentHashMap<String, List<TimetableItem>>()

    init {
        // Keep Android's launch path free of JSON parsing and note migration work.
        // A large or damaged local dataset must never hold the system splash screen.
        viewModelScope.launch(Dispatchers.IO) {
            hydrateStartupState()
        }
    }

    private fun hydrateStartupState() {
        store.ensureNoteAccount(startupUserId)
        val initialNotePages = store.loadNotePages()
        val initialMemos = store.loadMemos().let { items ->
            val cutoff = System.currentTimeMillis() - trashRetentionMillis
            val cleaned = items.filterNot { it.deletedAt > 0L && it.deletedAt < cutoff }
            if (cleaned.size != items.size) store.saveMemos(cleaned)
            cleaned
        }
        val initialTodos = store.loadTodos()
        val initialCalendar = store.loadCalendar()
        val persistedTimetable = decodeTimetable(store.latestTimetableJson)

        _state.update { current ->
            val sameStartupAccount = current.profile.userId == startupUserId
            current.copy(
                myTimetable = if (sameStartupAccount) persistedTimetable else current.myTimetable,
                memos = initialMemos,
                notePages = if (sameStartupAccount) initialNotePages else current.notePages,
                todos = initialTodos,
                calendar = initialCalendar
            )
        }

        if (!startupLoggedIn || startupUserId.isBlank()) return
        val current = _state.value
        if (!current.loggedIn || current.profile.userId != startupUserId) return

        val today = LocalDate.now().toString()
        if (persistedTimetable.isNotEmpty() && persistedTimetable.all { it.date == today }) {
            timetableCache[timetableCacheKey(startupUserId, today)] = persistedTimetable
            store.saveTimetableCacheJson(startupUserId, today, encodeTimetable(persistedTimetable))
        }
        startTimetableRefresh(today, startupUserId)
    }

    fun login(loginId: String, password: String): Job {
        val request = beginTimetableRequest(LocalDate.now().toString(), forceLoading = true)
        return launchTimetableRequest(request) {
            val previousUserId = store.userId
            val profile = try {
                timetableApi(request).login(loginId, password)
            } catch (e: Exception) {
                timetableRequests.applyIfCurrent(request) {
                    handleTaskError(e)
                }
                return@launchTimetableRequest
            }
            // Publish the account only while this login still owns the request generation.
            if (!timetableRequests.applyIfCurrent(request) {
                store.switchNoteAccount(previousUserId, profile.userId)
                store.userId = profile.userId
                store.displayName = profile.displayName
                store.loginId = loginId.trim()
                val accountNotes = store.loadNotePages()
                calendarTimetableRequestKey = ""
                calendarTimetableJob?.cancel()
                calendarTimetableJob = null
                _state.update { it.copy(
                    loggedIn = true,
                    profile = profile,
                    notePages = accountNotes,
                    pinnedNoteCount = null,
                    calendarTimetable = emptyList(),
                    calendarTimetableMonth = "",
                    calendarTimetableUserId = "",
                    calendarTimetableError = "",
                    calendarTimetableLoading = false,
                    calendarTimetableComplete = false
                ) }
            }) return@launchTimetableRequest
            executeTimetableRequest(request, profile.userId)
        }
    }

    fun refreshToday() {
        val userId = _state.value.profile.userId.ifBlank { store.userId }
        if (userId.isNotBlank()) startTimetableRefresh(_state.value.today, userId)
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

    private fun loadDate(date: LocalDate) {
        val userId = _state.value.profile.userId.ifBlank { store.userId }
        if (userId.isNotBlank()) startTimetableRefresh(date.toString(), userId)
    }

    private fun timetableCacheKey(userId: String, date: String) = "$userId|$date"

    // Reserve before launching IO so rapid taps and login publication cannot reorder ownership.
    // Cached dates switch immediately; network refresh continues without blanking the timetable.
    private fun beginTimetableRequest(
        date: String,
        cachedRows: List<TimetableItem>? = null,
        forceLoading: Boolean = false
    ) = timetableRequests.begin(date) {
        _state.update { current ->
            val visibleRows = cachedRows ?: if (current.today == date) current.myTimetable else emptyList()
            current.copy(
                today = date,
                myTimetable = visibleRows,
                loading = shouldShowTimetableLoading(forceLoading, cachedRows, visibleRows),
                timetableError = "",
                message = ""
            )
        }
    }

    private fun launchTimetableRequest(
        request: TimetableRequests.Request,
        block: () -> Unit
    ): Job =
        viewModelScope.launch(Dispatchers.IO) {
            timetableRequests.execute(
                request,
                block = block,
                onError = { error ->
                    val apiError = error as? ScerpApiException
                    if (apiError?.code == "authentication_required") {
                        timetableRequests.applyIfCurrent(request) {
                            handleTaskError(error)
                        }
                    } else {
                        timetableRequests.applyIfCurrent(request) {
                            _state.update {
                                it.copy(
                                    timetableError = apiError?.message ?: error.message ?: "시간표를 불러오지 못했습니다."
                                )
                            }
                        }
                    }
                },
                onFinished = { _state.update { it.copy(loading = false) } }
            )
        }

    private fun startTimetableRefresh(date: String, userId: String): Job {
        val cacheKey = timetableCacheKey(userId, date)
        val cachedRows = timetableCache[cacheKey]
            ?: store.loadTimetableCacheJson(userId, date)
                ?.let(::decodeTimetable)
                ?.also { timetableCache[cacheKey] = it }
        val request = beginTimetableRequest(date, cachedRows)
        return launchTimetableRequest(request) { executeTimetableRequest(request, userId) }
    }

    // HTTP Set-Cookie is also a session write; stale responses must not replace it.
    private fun timetableApi(request: TimetableRequests.Request) = ScerpApi(store) { update ->
        timetableRequests.applyIfCurrent(request, update)
    }

    private fun executeTimetableRequest(request: TimetableRequests.Request, userId: String) {
        // Compare today's persisted cache, never rows from a different selected date.
        val before = decodeTimetable(store.latestTimetableJson)
        val displayName = _state.value.profile.displayName.ifBlank { store.displayName }
        val rows = timetableApi(request).fetchMyTimetable(request.date, userId, displayName)
        timetableCache[timetableCacheKey(userId, request.date)] = rows
        store.saveTimetableCacheJson(userId, request.date, encodeTimetable(rows))
        val applied = timetableRequests.applyIfCurrent(request) {
            if (request.date == LocalDate.now().toString()) {
                val changed = timetableChangedForDate(request.date, before, rows)
                store.latestTimetableJson = encodeTimetable(rows)
                TimetableWidget.updateAll(getApplication())
                if (changed) NotificationHelper.showTimetableChanged(getApplication())
            }
            _state.update { it.copy(today = request.date, myTimetable = rows, timetableError = "") }
        }
        if (applied) prefetchAdjacentTimetable(request.date, userId, displayName)
    }

    private fun prefetchAdjacentTimetable(anchorDate: String, userId: String, displayName: String) {
        val anchor = runCatching { LocalDate.parse(anchorDate) }.getOrNull() ?: return
        listOf(anchor.minusDays(1), anchor.plusDays(1)).forEach { date ->
            val dateKey = date.toString()
            val cacheKey = timetableCacheKey(userId, dateKey)
            if (timetableCache.containsKey(cacheKey)) return@forEach
            viewModelScope.launch(Dispatchers.IO) {
                runCatching {
                    // Prefetch must never let an old/background response replace the active session cookie.
                    ScerpApi(store) { _ -> }.fetchMyTimetable(dateKey, userId, displayName)
                }.onSuccess { rows ->
                    timetableCache.putIfAbsent(cacheKey, rows)
                    store.saveTimetableCacheJson(userId, dateKey, encodeTimetable(rows))
                }
            }
        }
    }

    fun preloadAllIfNeeded() {
        if (!_state.value.loggedIn || _state.value.allTimetable.isNotEmpty()) return
        val anchor = _state.value.today
        publishCachedAllTimetable(anchor)
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { refreshAllWeekDirect(anchor) }
        }
    }

    fun loadCalendarTimetableMonth(month: YearMonth, priorityDate: LocalDate? = null) {
        if (!_state.value.loggedIn) return
        val userId = _state.value.profile.userId.ifBlank { store.userId }
        if (userId.isBlank()) return

        val monthKey = month.toString()
        val current = _state.value
        if (
            current.calendarTimetableMonth == monthKey &&
            current.calendarTimetableUserId == userId &&
            current.calendarTimetableComplete &&
            current.calendarTimetableError.isBlank()
        ) return

        val sameMonth =
            current.calendarTimetableMonth == monthKey &&
            current.calendarTimetableUserId == userId

        if (!sameMonth) {
            calendarTimetableLoadedDates.clear()
        }

        val orderedDates = calendarTimetableFetchDates(month, priorityDate)
        val priorityKey = priorityDate
            ?.takeIf { YearMonth.from(it) == month && it.dayOfWeek.value in 1..5 }
            ?.toString()

        val generation = ++calendarTimetableRequestGeneration
        val requestKey = userId + "|" + monthKey + "|" + generation
        calendarTimetableRequestKey = requestKey
        calendarTimetableJob?.cancel()

        _state.update { state ->
            state.copy(
                calendarTimetable = if (sameMonth) state.calendarTimetable else emptyList(),
                calendarTimetableMonth = monthKey,
                calendarTimetableUserId = userId,
                calendarTimetableError = "",
                calendarTimetableLoading = priorityKey != null && priorityKey !in calendarTimetableLoadedDates,
                calendarTimetableComplete = false
            )
        }

        calendarTimetableJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                val displayName = _state.value.profile.displayName.ifBlank { store.displayName }

                for (date in orderedDates) {
                    if (calendarTimetableRequestKey != requestKey) return@launch
                    val dateKey = date.toString()

                    if (dateKey in calendarTimetableLoadedDates) {
                        if (dateKey == priorityKey && calendarTimetableRequestKey == requestKey) {
                            _state.update { it.copy(calendarTimetableLoading = false) }
                        }
                        continue
                    }

                    val fetched = api.fetchMyTimetable(dateKey, userId, displayName)
                    if (calendarTimetableRequestKey != requestKey) return@launch

                    calendarTimetableLoadedDates += dateKey
                    val dayRows = fetched.filter {
                        it.subject.isNotBlank() || it.room.isNotBlank() || it.startTime.isNotBlank()
                    }

                    _state.update { state ->
                        val merged = (state.calendarTimetable.filterNot { it.date == dateKey } + dayRows)
                            .distinctBy { "${it.date}-${it.period}-${it.subject}-${it.room}" }
                            .sortedWith(compareBy<TimetableItem>({ it.date }, { it.period }))
                        state.copy(
                            calendarTimetable = merged,
                            calendarTimetableLoading = if (dateKey == priorityKey) false else state.calendarTimetableLoading
                        )
                    }
                }

                if (calendarTimetableRequestKey == requestKey) {
                    _state.update {
                        it.copy(
                            calendarTimetableError = "",
                            calendarTimetableLoading = false,
                            calendarTimetableComplete = true
                        )
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val apiError = e as? ScerpApiException
                if (apiError?.code == "authentication_required") {
                    store.clearSession()
                    calendarTimetableRequestKey = ""
                    calendarTimetableLoadedDates.clear()
                    _state.value = _state.value.copy(
                        loggedIn = false,
                        classrooms = emptyList(),
                        calendarTimetable = emptyList(),
                        calendarTimetableMonth = "",
                        calendarTimetableUserId = "",
                        calendarTimetableError = "",
                        calendarTimetableLoading = false,
                        calendarTimetableComplete = false,
                        message = "로그인 세션이 만료되었습니다. 다시 로그인해 주세요."
                    )
                } else if (calendarTimetableRequestKey == requestKey) {
                    _state.update {
                        it.copy(
                            calendarTimetableError = apiError?.message ?: e.message ?: "수업을 불러오지 못했습니다.",
                            calendarTimetableLoading = false,
                            calendarTimetableComplete = false
                        )
                    }
                }
            } finally {
                if (calendarTimetableRequestKey == requestKey) {
                    _state.update { it.copy(calendarTimetableLoading = false) }
                }
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
        if (publishCachedAllTimetable(date)) {
            try {
                refreshAllWeekDirect(date)
            } catch (e: Exception) {
                handleTaskError(e)
            }
        } else {
            runTask { refreshAllWeekDirect(date) }
        }
    }

    fun moveAllWeek(weeks: Long) = viewModelScope.launch(Dispatchers.IO) {
        val base = runCatching { LocalDate.parse(_state.value.today) }.getOrDefault(LocalDate.now())
        val target = base.plusWeeks(weeks).toString()
        _state.update { it.copy(today = target) }
        if (publishCachedAllTimetable(target)) {
            try {
                refreshAllWeekDirect(target)
            } catch (e: Exception) {
                handleTaskError(e)
            }
        } else {
            runTask { refreshAllWeekDirect(target) }
        }
    }

    private fun weekMonday(anchorDate: String): LocalDate {
        val anchor = runCatching { LocalDate.parse(anchorDate) }.getOrDefault(LocalDate.now())
        return anchor.minusDays((anchor.dayOfWeek.value - 1).toLong())
    }

    private fun publishCachedAllTimetable(anchorDate: String): Boolean {
        val monday = weekMonday(anchorDate)
        val key = monday.toString()
        val cached = allTimetableCache[key]
            ?: store.loadPublicWeekTimetableJson(key)
                ?.let(::decodeTimetable)
                ?.also { allTimetableCache[key] = it }
            ?: return false
        val anchor = runCatching { LocalDate.parse(anchorDate) }.getOrDefault(LocalDate.now())
        _state.update { it.copy(allTimetable = cached, today = anchor.toString(), loading = false) }
        return true
    }

    private fun refreshAllWeekDirect(anchorDate: String) {
        val anchor = runCatching { LocalDate.parse(anchorDate) }.getOrDefault(LocalDate.now())
        val monday = weekMonday(anchorDate)
        val friday = monday.plusDays(4)
        val rows = api.fetchPublicTimetableRange(monday.toString(), friday.toString())
        val key = monday.toString()
        allTimetableCache[key] = rows
        store.savePublicWeekTimetableJson(key, encodeTimetable(rows))
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

        store.deletedNoteIds().toList().sorted().forEach { id ->
            runCatching {
                api.archiveNote(id)
                store.clearNoteDeleted(id)
            }
        }

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
            val fetched = api.fetchNote(id)
            val page = if (fetched.blocks.isEmpty()) {
                fetched.copy(
                    blocks = listOf(
                        NoteBlock(
                            id = java.util.UUID.randomUUID().toString(),
                            type = NoteBlockType.TEXT,
                            position = 0
                        )
                    )
                )
            } else {
                fetched
            }
            val list = _state.value.notePages.filterNot { it.id == id } + page
            store.saveNotePages(list)
            _state.value = _state.value.copy(
                notePages = list,
                pinnedNoteCount = list.count { it.pinned && !it.archived }
            )
        }
    }

    fun saveNotePage(page: NotePage) = viewModelScope.launch(Dispatchers.IO) {
        val lock = noteMutationLocks.computeIfAbsent(page.id) { Mutex() }
        lock.withLock {
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
    }

    fun archiveNotePage(id: String) = viewModelScope.launch(Dispatchers.IO) {
        val lock = noteMutationLocks.computeIfAbsent(id) { Mutex() }
        lock.withLock {
            val next = _state.value.notePages.filterNot { it.id == id }
            store.markNoteDeleted(id)
            store.saveNotePages(next)
            _state.value = _state.value.copy(
                notePages = next,
                pinnedNoteCount = next.count { it.pinned && !it.archived }
            )
            runTask {
                api.archiveNote(id)
                store.clearNoteDeleted(id)
            }
        }
        noteMutationLocks.remove(id, lock)
    }

    fun archiveNotePages(ids: Set<String>) = viewModelScope.launch(Dispatchers.IO) {
        if (ids.isEmpty()) return@launch

        val orderedIds = ids.toList().sorted()
        val locks = orderedIds.map { id -> id to noteMutationLocks.computeIfAbsent(id) { Mutex() } }

        suspend fun <T> withLocks(index: Int = 0, block: suspend () -> T): T {
            if (index >= locks.size) return block()
            return locks[index].second.withLock {
                withLocks(index + 1, block)
            }
        }

        withLocks {
            val next = _state.value.notePages.filterNot { it.id in ids }
            ids.forEach { store.markNoteDeleted(it) }
            store.saveNotePages(next)
            _state.value = _state.value.copy(
                notePages = next,
                pinnedNoteCount = next.count { it.pinned && !it.archived }
            )

            for (id in orderedIds) {
                runCatching {
                    api.archiveNote(id)
                    store.clearNoteDeleted(id)
                }
            }
        }

        locks.forEach { (id, lock) -> noteMutationLocks.remove(id, lock) }
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
            handleTaskError(e)
        } finally {
            _state.value = _state.value.copy(loading = false)
        }
    }

    private fun handleTaskError(e: Exception) {
        if (e is CancellationException) throw e
        val apiError = e as? ScerpApiException
        if (apiError?.code == "authentication_required") {
            store.clearSession()
            calendarTimetableRequestKey = ""
            calendarTimetableJob?.cancel()
            calendarTimetableJob = null
            _state.value = _state.value.copy(
                loggedIn = false,
                classrooms = emptyList(),
                calendarTimetable = emptyList(),
                calendarTimetableMonth = "",
                calendarTimetableUserId = "",
                message = "로그인 세션이 만료되었습니다. 다시 로그인해 주세요."
            )
        } else {
            _state.value = _state.value.copy(
                message = apiError?.message ?: e.message ?: "오류가 발생했습니다."
            )
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
