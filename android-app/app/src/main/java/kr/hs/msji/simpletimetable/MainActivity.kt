package kr.hs.msji.simpletimetable

import android.Manifest
import android.app.Activity
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.speech.RecognizerIntent
import android.widget.ImageView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        NotificationHelper.ensureChannel(this)
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 7001)
        }
        setContent {
            val prefs = remember { getSharedPreferences("appearance", MODE_PRIVATE) }
            var themeMode by remember {
                mutableStateOf(
                    runCatching { AppThemeMode.valueOf(prefs.getString("theme_mode", AppThemeMode.SYSTEM.name) ?: AppThemeMode.SYSTEM.name) }
                        .getOrDefault(AppThemeMode.SYSTEM)
                )
            }
            val systemDark = isSystemInDarkTheme()
            val dark = when (themeMode) {
                AppThemeMode.DARK -> true
                AppThemeMode.LIGHT -> false
                AppThemeMode.SYSTEM -> systemDark
            }
            val colors = if (dark) {
                darkColorScheme(
                    primary = Color(0xFF77B9FF),
                    onPrimary = Color(0xFF07111D),
                    primaryContainer = Color(0xFF163A5A),
                    onPrimaryContainer = Color(0xFFD7EAFF),
                    secondary = Color(0xFFA9C7E8),
                    secondaryContainer = Color(0xFF203040),
                    background = Color(0xFF071019),
                    surface = Color(0xFF0D1721),
                    surfaceVariant = Color(0xFF131E2A),
                    onSurface = Color(0xFFF4F7FB),
                    onSurfaceVariant = Color(0xFFB5C0CE),
                    outline = Color(0xFF2B3B4D)
                )
            } else {
                lightColorScheme(
                    primary = Color(0xFF1769AA),
                    onPrimary = Color.White,
                    primaryContainer = Color(0xFFD5E9FF),
                    onPrimaryContainer = Color(0xFF001D35),
                    secondary = Color(0xFF4F6377),
                    secondaryContainer = Color(0xFFD3E4F5),
                    background = Color(0xFFF7FAFD),
                    surface = Color(0xFFFFFFFF),
                    surfaceVariant = Color(0xFFEAF0F6),
                    onSurface = Color(0xFF111418),
                    onSurfaceVariant = Color(0xFF46515C),
                    outline = Color(0xFFB6C2CE)
                )
            }

            MaterialTheme(colorScheme = colors) {
                val vm: MainViewModel = viewModel()
                SimpleTimetableApp(
                    vm = vm,
                    themeMode = themeMode,
                    onThemeModeChange = { mode ->
                        themeMode = mode
                        prefs.edit().putString("theme_mode", mode.name).apply()
                    }
                )
            }
        }
    }
}

enum class AppTab(val label: String) {
    TODAY("오늘"), ALL("전체"), MEMO("메모"), TODO("TODO"), CALENDAR("캘린더"), BROADCAST("방송")
}

enum class AppThemeMode(val label: String) {
    SYSTEM("시스템"), LIGHT("라이트"), DARK("다크")
}

@Composable
private fun AppTabIcon(tab: AppTab) {
    val image = when (tab) {
        AppTab.TODAY -> Icons.Filled.Home
        AppTab.ALL -> Icons.Filled.List
        AppTab.MEMO -> Icons.Filled.Edit
        AppTab.TODO -> Icons.Filled.CheckCircle
        AppTab.CALENDAR -> Icons.Filled.DateRange
        AppTab.BROADCAST -> Icons.Filled.Notifications
    }
    Icon(imageVector = image, contentDescription = tab.label)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SimpleTimetableApp(
    vm: MainViewModel,
    themeMode: AppThemeMode,
    onThemeModeChange: (AppThemeMode) -> Unit
) {
    val state by vm.state.collectAsState()
    var tab by remember { mutableStateOf(AppTab.TODAY) }
    var globalSearch by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }

    if (!state.loggedIn) {
        LoginScreen(state, vm)
        return
    }

    LaunchedEffect(state.loggedIn) {
        kotlinx.coroutines.delay(150)
        vm.preloadAllIfNeeded()
        kotlinx.coroutines.delay(100)
        vm.preloadClassroomsIfNeeded()
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                ),
                title = {
                    Text(
                        when {
                            showSettings -> "설정"
                            globalSearch -> "통합 검색"
                            tab == AppTab.TODAY -> "오늘의 시간표"
                            tab == AppTab.ALL -> "전체 시간표"
                            tab == AppTab.MEMO -> "메모"
                            tab == AppTab.TODO -> "TODO"
                            tab == AppTab.CALENDAR -> "캘린더"
                            else -> "전자칠판 방송"
                        },
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold
                    )
                },
                actions = {
                    IconButton(
                        onClick = {
                            showSettings = false
                            globalSearch = !globalSearch
                        }
                    ) {
                        Icon(
                            if (globalSearch) Icons.Filled.Close else Icons.Filled.Search,
                            contentDescription = if (globalSearch) "통합 검색 닫기" else "통합 검색"
                        )
                    }
                    IconButton(
                        onClick = {
                            globalSearch = false
                            showSettings = !showSettings
                        }
                    ) {
                        Icon(Icons.Filled.Settings, contentDescription = "설정")
                    }
                }
            )
        },
        bottomBar = {
            NavigationBar {
                AppTab.entries.forEach { item ->
                    NavigationBarItem(
                        selected = tab == item,
                        onClick = {
                            globalSearch = false
                            showSettings = false
                            tab = item
                        },
                        icon = { AppTabIcon(item) },
                        label = { Text(item.label) }
                    )
                }
            }
        }
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            if (showSettings) {
                SettingsScreen(
                    themeMode = themeMode,
                    onThemeModeChange = onThemeModeChange
                )
            } else if (globalSearch) {
                GlobalSearchScreen(
                    state = state,
                    onOpen = { target ->
                        globalSearch = false
                        tab = target
                    }
                )
            } else {
                when (tab) {
                    AppTab.TODAY -> TodayScreen(
                        state = state,
                        vm = vm,
                        onOpen = { target -> tab = target }
                    )
                    AppTab.ALL -> AllTimetableScreen(state, vm)
                    AppTab.MEMO -> MemoScreen(state, vm)
                    AppTab.TODO -> TodoScreen(state, vm)
                    AppTab.CALENDAR -> CalendarScreen(state, vm)
                    AppTab.BROADCAST -> BroadcastScreen(state, vm)
                }
            }
            if (state.loading) {
                CircularProgressIndicator(Modifier.align(Alignment.Center))
            }
        }
    }
}

@Composable
private fun SettingsScreen(
    themeMode: AppThemeMode,
    onThemeModeChange: (AppThemeMode) -> Unit
) {
    val context = LocalContext.current
    var widgetFontScale by remember { mutableFloatStateOf(WidgetDisplaySettings.fontScale(context)) }
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .padding(bottom = 110.dp)
    ) {
        Text(
            "색상 모드",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        Text(
            "앱의 색상 테마를 설정합니다.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(10.dp))

        ElevatedCard(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AppThemeMode.entries.forEach { mode ->
                    FilterChip(
                        selected = themeMode == mode,
                        onClick = { onThemeModeChange(mode) },
                        label = { Text(mode.label) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        Spacer(Modifier.height(14.dp))

        Text(
            "위젯 글자 크기",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        Text(
            "홈 화면 위젯의 글자 크기를 조절합니다.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(8.dp))
        ElevatedCard(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp)
        ) {
            Column(Modifier.fillMaxWidth().padding(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    WidgetDisplaySettings.options.forEach { (scale, label) ->
                        FilterChip(
                            selected = kotlin.math.abs(widgetFontScale - scale) < 0.01f,
                            onClick = {
                                widgetFontScale = scale
                                WidgetDisplaySettings.setFontScale(context, scale)
                                WidgetDisplaySettings.refreshAllWidgets(context)
                            },
                            label = { Text(label) }
                        )
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    "현재 " + (widgetFontScale * 100).toInt() + "% · 변경 즉시 위젯에 적용됩니다.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(Modifier.height(14.dp))

        Text(
            "업데이트",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(8.dp))
        ElevatedCard(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text("업데이트 확인", fontWeight = FontWeight.SemiBold)
                    Text(
                        "현재 v" + BuildConfig.VERSION_NAME + " · 최신 버전을 확인합니다.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                UpdateAction()
            }
        }

        Spacer(Modifier.height(14.dp))

        Text(
            "앱 정보",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(8.dp))
        ElevatedCard(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp)
        ) {
            Column(Modifier.fillMaxWidth().padding(14.dp)) {
                Text("밀성제일고 시간표", fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(4.dp))
                Text(
                    "버전 " + BuildConfig.VERSION_NAME,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun GlobalSearchScreen(
    state: AppUiState,
    onOpen: (AppTab) -> Unit
) {
    var query by remember { mutableStateOf("") }
    val needle = query.trim()

    val memoResults = remember(state.memos, needle) {
        if (needle.isBlank()) emptyList()
        else state.memos.filter {
            it.text.contains(needle, ignoreCase = true) ||
                it.category.contains(needle, ignoreCase = true)
        }.take(20)
    }
    val todoResults = remember(state.todos, needle) {
        if (needle.isBlank()) emptyList()
        else state.todos.filter {
            it.text.contains(needle, ignoreCase = true) ||
                it.dueDate.contains(needle, ignoreCase = true)
        }.take(20)
    }
    val calendarResults = remember(state.calendar, needle) {
        if (needle.isBlank()) emptyList()
        else state.calendar.filter {
            it.title.contains(needle, ignoreCase = true) ||
                it.date.contains(needle, ignoreCase = true)
        }.take(20)
    }
    val timetableResults = remember(state.myTimetable, state.allTimetable, needle) {
        if (needle.isBlank()) emptyList()
        else (state.myTimetable + state.allTimetable)
            .distinctBy { listOf(it.date, it.classCode, it.period, it.subject, it.teacher, it.room).joinToString("|") }
            .filter {
                it.subject.contains(needle, ignoreCase = true) ||
                    it.teacher.contains(needle, ignoreCase = true) ||
                    it.room.contains(needle, ignoreCase = true) ||
                    it.classCode.contains(needle, ignoreCase = true) ||
                    it.date.contains(needle, ignoreCase = true)
            }.take(20)
    }
    val total = memoResults.size + todoResults.size + calendarResults.size + timetableResults.size

    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text("통합 검색", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Text(
                    "시간표·메모·TODO·캘린더를 한 번에 찾습니다.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (needle.isNotBlank()) {
                Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.primaryContainer) {
                    Text(
                        total.toString() + "건",
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            placeholder = { Text("과목, 교사, 메모, 할 일, 일정 검색") },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            trailingIcon = {
                if (query.isNotBlank()) {
                    IconButton(onClick = { query = "" }) {
                        Icon(Icons.Filled.Close, contentDescription = "검색어 지우기")
                    }
                }
            },
            shape = RoundedCornerShape(16.dp)
        )

        Spacer(Modifier.height(10.dp))

        if (needle.isBlank()) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.24f)
            ) {
                Column(
                    modifier = Modifier.padding(vertical = 34.dp, horizontal = 18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(Icons.Filled.Search, contentDescription = null, modifier = Modifier.size(32.dp))
                    Spacer(Modifier.height(8.dp))
                    Text("검색어를 입력하세요.", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "업무 기록과 일정, 시간표를 메뉴 이동 없이 찾을 수 있습니다.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else if (total == 0) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.24f)
            ) {
                Text(
                    "검색 결과가 없습니다.",
                    modifier = Modifier.fillMaxWidth().padding(vertical = 34.dp),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.titleMedium
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(7.dp),
                contentPadding = PaddingValues(bottom = 14.dp)
            ) {
                timetableResults.forEach { row ->
                    item(key = "t-" + row.date + row.classCode + row.period + row.subject) {
                        SearchResultCard(
                            badge = "시간표",
                            title = row.subject.ifBlank { "과목 미지정" },
                            detail = listOf(row.date, row.classCode, row.teacher, row.room).filter { it.isNotBlank() }.joinToString(" · "),
                            onClick = { onOpen(AppTab.ALL) }
                        )
                    }
                }
                memoResults.forEach { memo ->
                    item(key = "m-" + memo.id) {
                        SearchResultCard(
                            badge = "메모 · " + memo.category,
                            title = memo.text.lineSequence().firstOrNull().orEmpty(),
                            detail = if (memo.checklist) "체크리스트" else "메모",
                            onClick = { onOpen(AppTab.MEMO) }
                        )
                    }
                }
                todoResults.forEach { todo ->
                    item(key = "d-" + todo.id) {
                        SearchResultCard(
                            badge = if (todo.done) "TODO · 완료" else "TODO",
                            title = todo.text,
                            detail = "마감 " + todo.dueDate,
                            onClick = { onOpen(AppTab.TODO) }
                        )
                    }
                }
                calendarResults.forEach { event ->
                    item(key = "c-" + event.id) {
                        SearchResultCard(
                            badge = "캘린더",
                            title = event.title,
                            detail = event.date,
                            onClick = { onOpen(AppTab.CALENDAR) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchResultCard(
    badge: String,
    title: String,
    detail: String,
    onClick: () -> Unit
) {
    ElevatedCard(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(17.dp)
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 11.dp)) {
            Surface(
                shape = RoundedCornerShape(9.dp),
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Text(
                    badge,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                title.ifBlank { "내용 없음" },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            if (detail.isNotBlank()) {
                Spacer(Modifier.height(3.dp))
                Text(
                    detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun LoginScreen(state: AppUiState, vm: MainViewModel) {
    var id by remember(state.lastLoginId) { mutableStateOf(state.lastLoginId) }
    var password by remember { mutableStateOf("") }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 28.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "밀성제일고",
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                UpdateAction()
            }
            Text(
                "밀성제일고등학교 · SCERP.cloud",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(28.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                )
            ) {
                Column(Modifier.padding(18.dp)) {
                    Text(
                        "로그인",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(14.dp))

                    OutlinedTextField(
                        value = id,
                        onValueChange = { id = it },
                        label = { Text("아이디") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(Modifier.height(10.dp))

                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text("비밀번호") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(Modifier.height(16.dp))

                    Button(
                        onClick = { vm.login(id, password) },
                        enabled = !state.loading && id.isNotBlank() && password.isNotBlank(),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (state.loading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text("로그인")
                        }
                    }
                }
            }

            if (state.message.isNotBlank()) {
                Spacer(Modifier.height(14.dp))
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.errorContainer
                ) {
                    Text(
                        state.message,
                        modifier = Modifier.fillMaxWidth().padding(14.dp),
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            Spacer(Modifier.height(14.dp))
            Text(
                "한 번 로그인하면 다음 실행부터 인증 세션을 자동으로 복원합니다.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private val SCHOOL_ZONE: ZoneId = ZoneId.of("Asia/Seoul")

internal fun schoolToday(): LocalDate = LocalDate.now(SCHOOL_ZONE)

internal fun countPinnedActiveNotePages(pages: List<NotePage>): Int =
    pages.count { it.pinned && !it.archived }

internal fun isCurrentTimetableLesson(
    row: TimetableItem,
    today: LocalDate = schoolToday(),
    now: LocalTime = LocalTime.now(SCHOOL_ZONE)
): Boolean {
    if (row.date != today.toString()) return false
    val start = runCatching { LocalTime.parse(row.startTime.take(5)) }.getOrNull() ?: return false
    val end = runCatching { LocalTime.parse(row.endTime.take(5)) }.getOrNull() ?: return false
    return !now.isBefore(start) && now.isBefore(end)
}

@Composable
private fun TodayScreen(
    state: AppUiState,
    vm: MainViewModel,
    onOpen: (AppTab) -> Unit
) {
    val rows = remember(state.myTimetable) { state.myTimetable.sortedBy { it.period } }
    val selectedDate = remember(state.today) {
        runCatching { LocalDate.parse(state.today) }.getOrDefault(schoolToday())
    }
    val actualToday = schoolToday()
    val isToday = selectedDate == actualToday
    val status = remember(state.myTimetable, state.today) {
        if (isToday) TimetableStatus.calculate(state.myTimetable) else ClassStatus()
    }
    val dateLabel = remember(selectedDate) {
        selectedDate.format(DateTimeFormatter.ofPattern("yyyy년 M월 d일 (E)", Locale.KOREAN))
    }

    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            FilledTonalButton(
                onClick = vm::goToToday,
                enabled = !isToday,
                shape = RoundedCornerShape(16.dp)
            ) {
                Icon(Icons.Filled.DateRange, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("오늘")
            }
        }

        Spacer(Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Surface(
                modifier = Modifier.size(48.dp),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant
            ) {
                IconButton(onClick = { vm.moveDate(-1) }) {
                    Icon(
                        Icons.Filled.KeyboardArrowLeft,
                        contentDescription = "이전 날짜",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Surface(
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp),
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    MaterialTheme.colorScheme.outline
                )
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        dateLabel,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Surface(
                modifier = Modifier.size(48.dp),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant
            ) {
                IconButton(onClick = { vm.moveDate(1) }) {
                    Icon(
                        Icons.Filled.KeyboardArrowRight,
                        contentDescription = "다음 날짜",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        Spacer(Modifier.height(14.dp))

        val focus = status.current ?: status.next
        if (isToday && focus != null) {
            val current = status.current != null
            val label = if (current) "현재 교시" else "다음 수업"
            val minutes = if (current) status.minutesRemaining else status.minutesUntilNext
            val timeLabel = if (current) "수업 종료까지" else "수업 시작까지"
            val gradeClass = listOf(
                focus.grade.takeIf { it > 0 }?.let { "${it}학년" },
                focus.classCode.takeIf { it.isNotBlank() }
            ).filterNotNull().joinToString(" ")
            val detail = listOf(
                gradeClass,
                focus.room.takeIf { it.isNotBlank() }
            ).filterNotNull().filter { it.isNotBlank() }.joinToString(" · ")

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(22.dp)),
                shape = RoundedCornerShape(22.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.72f)
            ) {
                Row(
                    Modifier.padding(horizontal = 20.dp, vertical = 18.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.22f)
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                label,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                "${focus.period}",
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Spacer(Modifier.width(16.dp))

                    Column(Modifier.weight(1f)) {
                        Text(
                            focus.subject.ifBlank { "과목 미지정" },
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (detail.isNotBlank()) {
                            Spacer(Modifier.height(4.dp))
                            Text(
                                detail,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    if (minutes != null) {
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                timeLabel,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            val displayTime = if (minutes >= 60) {
                                val hours = minutes / 60
                                val remainingMinutes = minutes % 60
                                if (remainingMinutes == 0L) "${hours}시간" else "${hours}시간 ${remainingMinutes}분"
                            } else {
                                "${minutes}분"
                            }
                            Text(
                                displayTime,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        } else {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(22.dp)),
                shape = RoundedCornerShape(22.dp),
                color = if (isToday) {
                    MaterialTheme.colorScheme.surface
                } else {
                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                }
            ) {
                Row(
                    Modifier.padding(horizontal = 20.dp, vertical = 18.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = RoundedCornerShape(15.dp),
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.DateRange,
                            contentDescription = null,
                            modifier = Modifier.padding(13.dp).size(26.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    Spacer(Modifier.width(16.dp))
                    Column {
                        Text(
                            when {
                                !isToday -> "선택 날짜 시간표"
                                rows.isEmpty() -> "오늘 수업 없음"
                                else -> "오늘 수업 종료"
                            },
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            when {
                                !isToday && rows.isEmpty() -> "등록된 수업이 없습니다."
                                !isToday -> "총 ${rows.size}개 수업이 있습니다."
                                rows.isEmpty() -> "오늘 등록된 수업이 없습니다."
                                else -> "오늘 일정이 모두 끝났습니다."
                            },
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(10.dp))

        if (isToday) {
            val todayKey = actualToday.toString()
            val activeTodos = state.todos.filterNot { it.done }
            val overdueTodos = activeTodos.count {
                runCatching { LocalDate.parse(it.dueDate).isBefore(actualToday) }.getOrDefault(false)
            }
            val todayTodos = activeTodos.count { it.dueDate == todayKey }
            val todayEvents = state.calendar.count { it.date == todayKey }
            val pinnedMemos = countPinnedActiveNotePages(state.notePages)

            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp)
            ) {
                Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "오늘 업무",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f)
                        )
                        if (overdueTodos > 0) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.errorContainer
                            ) {
                                Text(
                                    "지연 " + overdueTodos,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(7.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        TodayWorkSummary(
                            label = "TODO",
                            value = todayTodos.toString(),
                            modifier = Modifier.weight(1f),
                            onClick = { onOpen(AppTab.TODO) }
                        )
                        TodayWorkSummary(
                            label = "일정",
                            value = todayEvents.toString(),
                            modifier = Modifier.weight(1f),
                            onClick = { onOpen(AppTab.CALENDAR) }
                        )
                        TodayWorkSummary(
                            label = "고정 메모",
                            value = pinnedMemos.toString(),
                            modifier = Modifier.weight(1f),
                            onClick = { onOpen(AppTab.MEMO) }
                        )
                    }
                }
            }

            Spacer(Modifier.height(10.dp))
        }

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(bottom = 14.dp)
        ) {
            items(rows, key = { "${it.date}-${it.period}-${it.classCode}" }) { row ->
                val active = isToday && status.current?.period == row.period
                val borderColor = if (active) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.outline
                val containerColor = if (active) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surface

                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, borderColor, RoundedCornerShape(18.dp)),
                    shape = RoundedCornerShape(18.dp),
                    color = containerColor
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 13.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = if (active) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            Box(
                                modifier = Modifier.size(56.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    "${row.period}",
                                    style = MaterialTheme.typography.headlineSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = if (active) MaterialTheme.colorScheme.onPrimary
                                    else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }

                        Spacer(Modifier.width(14.dp))

                        Column(Modifier.weight(1f)) {
                            Text(
                                row.subject.ifBlank { "과목 미지정" },
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )

                            val time = listOf(
                                row.startTime.take(5),
                                row.endTime.take(5)
                            ).filter { it.isNotBlank() }.joinToString(" ~ ")

                            val gradeClass = listOf(
                                row.grade.takeIf { it > 0 }?.let { "${it}학년" },
                                row.classCode.takeIf { it.isNotBlank() }
                            ).filterNotNull().joinToString(" ")

                            val detail = listOf(time, gradeClass, row.room)
                                .filter { it.isNotBlank() }
                                .joinToString(" · ")

                            if (detail.isNotBlank()) {
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    detail,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        if (active) {
                            AssistChip(
                                onClick = {},
                                label = { Text("현재") }
                            )
                        }
                    }
                }
            }
        }

        if (state.message.isNotBlank()) {
            Spacer(Modifier.height(8.dp))
            Text(state.message, color = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
private fun TodayWorkSummary(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 9.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
    }
}

internal fun allTimetableGradeSummary(grade: Int, classCount: Int): String =
    "${grade}학년 · ${classCount}개 학급"

internal fun allTimetableClassSummary(grade: Int, periodCount: Int): String =
    "${grade}학년 · ${periodCount}교시"

private fun classSortKey(value: String): Triple<Int, Int, String> {
    val text = value.trim()
    val group = when {
        text.startsWith("경영") -> 0
        text.uppercase().startsWith("IT") -> 1
        text.startsWith("뷰티") -> 2
        text.startsWith("보건") -> 3
        else -> 99
    }
    val number = Regex("(\\d+)$").find(text)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 999
    return Triple(group, number, text)
}

@Composable
private fun AllTimetableScreen(state: AppUiState, vm: MainViewModel) {
    var selectedGrade by remember { mutableIntStateOf(3) }
    var query by remember { mutableStateOf("") }
    var filtersExpanded by remember { mutableStateOf(false) }
    var schoolClock by remember {
        mutableStateOf(java.time.ZonedDateTime.now(SCHOOL_ZONE))
    }

    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(30_000)
            schoolClock = java.time.ZonedDateTime.now(SCHOOL_ZONE)
        }
    }

    val currentSchoolDate = schoolClock.toLocalDate()
    val currentSchoolTime = schoolClock.toLocalTime()

    val gradeRows = state.allTimetable.filter { it.grade == selectedGrade }
    val classes = gradeRows.map { it.classCode }.distinct().sortedWith(
        compareBy<String>({ classSortKey(it).first }, { classSortKey(it).second }, { classSortKey(it).third })
    )
    var selectedClass by remember(selectedGrade, classes) {
        mutableStateOf(classes.firstOrNull().orEmpty())
    }

    val searchResults = remember(state.allTimetable, query, selectedGrade) {
        searchTimetableRows(state.allTimetable, query, selectedGrade)
            .sortedWith(
                compareBy<TimetableItem>(
                    { classSortKey(it.classCode).first },
                    { classSortKey(it.classCode).second },
                    { classSortKey(it.classCode).third },
                    { it.period }
                )
            )
    }
    val searching = query.isNotBlank()

    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp, vertical = 0.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                if (classes.isEmpty()) selectedGrade.toString() + "학년 없음"
                else selectedGrade.toString() + "학년 · " + selectedClass.ifBlank { classes.firstOrNull().orEmpty() },
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
            TextButton(
                onClick = { filtersExpanded = !filtersExpanded },
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
            ) { Text(if (filtersExpanded) "닫기" else "필터") }
            IconButton(onClick = vm::refreshAll, modifier = Modifier.size(40.dp)) {
                Text("↻", style = MaterialTheme.typography.titleMedium)
            }
        }

        if (filtersExpanded) {
            Spacer(Modifier.height(4.dp))
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                placeholder = { Text("과목·교사·학급·교실 검색") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = "검색") },
                trailingIcon = {
                    if (query.isNotBlank()) {
                        IconButton(onClick = { query = "" }) {
                            Icon(Icons.Filled.Close, contentDescription = "검색어 지우기")
                        }
                    }
                },
                shape = RoundedCornerShape(14.dp)
            )
            Spacer(Modifier.height(4.dp))
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.34f)
            ) {
                Column(Modifier.padding(4.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
                        (1..3).forEach { grade ->
                            FilterChip(
                                selected = selectedGrade == grade,
                                onClick = { selectedGrade = grade },
                                label = { Text(grade.toString() + "학년") },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                    if (classes.isNotEmpty()) {
                        Row(
                            Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            classes.forEach { classCode ->
                                FilterChip(
                                    selected = !searching && selectedClass == classCode,
                                    onClick = {
                                        selectedClass = classCode
                                        query = ""
                                        filtersExpanded = false
                                    },
                                    label = { Text(classCode) }
                                )
                            }
                        }
                    }
                }
            }
        }
        if (searching) {
            Spacer(Modifier.height(10.dp))

            Text(
                "검색 결과 ${searchResults.size}건",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )

            Spacer(Modifier.height(6.dp))

            if (searchResults.isEmpty()) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.28f)
                ) {
                    Column(
                        Modifier.padding(18.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            "검색 결과가 없습니다.",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "과목, 교사, 학급, 교실 또는 교시를 다시 입력해 보세요.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(5.dp),
                    contentPadding = PaddingValues(bottom = 8.dp)
                ) {
                    items(
                        searchResults,
                        key = { "${it.grade}-${it.classCode}-${it.period}-${it.subject}" }
                    ) { row ->
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.22f)
                        ) {
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 9.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    modifier = Modifier.size(46.dp),
                                    shape = RoundedCornerShape(14.dp),
                                    color = MaterialTheme.colorScheme.primaryContainer
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(
                                            "${row.period}",
                                            style = MaterialTheme.typography.titleLarge,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }

                                Spacer(Modifier.width(12.dp))

                                Column(Modifier.weight(1f)) {
                                    Text(
                                        row.subject.ifBlank { "과목 미지정" },
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    val detail = listOf(row.teacher, row.room)
                                        .filter { it.isNotBlank() }
                                        .joinToString(" · ")
                                    if (detail.isNotBlank()) {
                                        Spacer(Modifier.height(2.dp))
                                        Text(
                                            detail,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }

                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        row.classCode,
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        "${row.period}교시",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        } else if (classes.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))

            val anchorDate = remember(state.today) {
                runCatching { LocalDate.parse(state.today) }.getOrDefault(schoolToday())
            }
            val weekStart = remember(anchorDate) {
                anchorDate.minusDays((anchorDate.dayOfWeek.value - 1).toLong())
            }
            val weekDates = remember(weekStart) { (0L..4L).map { weekStart.plusDays(it) } }
            val selectedRows = gradeRows.filter {
                it.classCode == selectedClass && it.hasVisibleLessonContent()
            }
            val subjectColors = remember(selectedRows) {
                timetableSubjectColors(selectedRows.map { it.subject })
            }
            val visiblePeriods = selectedRows.map { it.period }.distinct().sorted()
            val formatter = remember { DateTimeFormatter.ofPattern("M/d", Locale.KOREA) }
            val rangeFormatter = remember { DateTimeFormatter.ofPattern("yyyy.MM.dd", Locale.KOREA) }
            val weekdayLabels = listOf("월", "화", "수", "목", "금")
            val weeklyView = true
            var selectedDayIndex by remember(weekStart) {
                mutableIntStateOf(
                    weekDates.indexOf(schoolToday()).takeIf { it >= 0 } ?: 0
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                FilledTonalIconButton(onClick = { vm.moveAllWeek(-1) }, modifier = Modifier.size(40.dp)) {
                    Icon(Icons.Filled.KeyboardArrowLeft, contentDescription = "이전 주")
                }
                Text(
                    weekStart.format(rangeFormatter) + " - " + weekStart.plusDays(4).format(rangeFormatter),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                FilledTonalIconButton(onClick = { vm.moveAllWeek(1) }, modifier = Modifier.size(40.dp)) {
                    Icon(Icons.Filled.KeyboardArrowRight, contentDescription = "다음 주")
                }
            }

            Spacer(Modifier.height(6.dp))

            if (!weeklyView) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    weekDates.forEachIndexed { index, date ->
                        val isToday = date == schoolToday()
                        FilterChip(
                            selected = selectedDayIndex == index,
                            onClick = { selectedDayIndex = index },
                            label = {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        if (isToday) "오늘·" + weekdayLabels[index] else weekdayLabels[index],
                                        maxLines = 1
                                    )
                                    Text(
                                        date.format(formatter),
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                }
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                Spacer(Modifier.height(8.dp))

                val selectedDate = weekDates[selectedDayIndex]
                val dayRows = selectedRows
                    .filter { it.date == selectedDate.toString() }
                    .sortedBy { it.period }

                if (dayRows.isEmpty()) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        shape = RoundedCornerShape(18.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.24f)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(20.dp),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                weekdayLabels[selectedDayIndex] + "요일 수업이 없습니다.",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                selectedDate.format(formatter),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(7.dp),
                        contentPadding = PaddingValues(bottom = 12.dp)
                    ) {
                        items(dayRows, key = { it.date + "-" + it.classCode + "-" + it.period + "-" + it.subject }) { row ->
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(18.dp),
                                color = subjectColors[row.subject.trim()].orEmptyColor().copy(alpha = 0.92f)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Surface(
                                        modifier = Modifier.size(50.dp),
                                        shape = RoundedCornerShape(16.dp),
                                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.40f)
                                    ) {
                                        Column(
                                            modifier = Modifier.fillMaxSize(),
                                            verticalArrangement = Arrangement.Center,
                                            horizontalAlignment = Alignment.CenterHorizontally
                                        ) {
                                            Text(
                                                row.period.toString(),
                                                style = MaterialTheme.typography.titleLarge,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Text(
                                                "교시",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f)
                                            )
                                        }
                                    }

                                    Spacer(Modifier.width(14.dp))

                                    Column(Modifier.weight(1f)) {
                                        Text(
                                            row.subject.ifBlank { "과목 미지정" },
                                            style = MaterialTheme.typography.titleLarge,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        val detail = listOf(row.teacher, row.room)
                                            .filter { it.isNotBlank() }
                                            .joinToString(" · ")
                                        if (detail.isNotBlank()) {
                                            Spacer(Modifier.height(3.dp))
                                            Text(
                                                detail,
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.82f),
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }

                                    if (row.startTime.isNotBlank()) {
                                        Text(
                                            row.startTime,
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.18f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                            .padding(bottom = 12.dp)
                    ) {
                        Row(Modifier.fillMaxWidth()) {
                            Box(
                                Modifier
                                    .width(54.dp)
                                    .height(56.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("교시", fontWeight = FontWeight.Bold)
                            }

                            weekDates.forEachIndexed { index, date ->
                                val isToday = date == schoolToday()
                                Surface(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(56.dp)
                                        .padding(1.dp),
                                    shape = RoundedCornerShape(10.dp),
                                    color = if (isToday) {
                                        MaterialTheme.colorScheme.primaryContainer
                                    } else {
                                        Color.Transparent
                                    }
                                ) {
                                    Column(
                                        modifier = Modifier.fillMaxSize(),
                                        verticalArrangement = Arrangement.Center,
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        if (isToday) {
                                            Text(
                                                "오늘",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.primary,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                        Text(
                                            weekdayLabels[index],
                                            style = MaterialTheme.typography.labelLarge,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            date.format(formatter),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }

                        visiblePeriods.forEach { period ->
                            val currentPeriodRow = selectedRows
                                .firstOrNull { it.period == period && it.date == currentSchoolDate.toString() }
                                ?.let {
                                    isCurrentTimetableLesson(
                                        row = it,
                                        today = currentSchoolDate,
                                        now = currentSchoolTime
                                    )
                                } == true
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(92.dp)
                                    .then(
                                        if (currentPeriodRow) {
                                            Modifier
                                                .background(
                                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.20f),
                                                    RoundedCornerShape(12.dp)
                                                )
                                                .border(
                                                    2.dp,
                                                    MaterialTheme.colorScheme.primary,
                                                    RoundedCornerShape(12.dp)
                                                )
                                        } else Modifier
                                    )
                            ) {
                                Column(
                                    modifier = Modifier
                                        .width(54.dp)
                                        .fillMaxHeight(),
                                    verticalArrangement = Arrangement.Center,
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        period.toString(),
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.Bold
                                    )
                                    val representative = selectedRows.firstOrNull { it.period == period }
                                    if (!representative?.startTime.isNullOrBlank()) {
                                        Text(
                                            representative?.startTime.orEmpty(),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }

                                weekDates.forEach { date ->
                                    val row = selectedRows.firstOrNull {
                                        it.date == date.toString() && it.period == period
                                    }
                                    if (row == null) {
                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .fillMaxHeight()
                                                .padding(2.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                "—",
                                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                            )
                                        }
                                    } else {
                                        val cellColor = subjectColors[row.subject.trim()].orEmptyColor()
                                        val currentCell = isCurrentTimetableLesson(
                                            row = row,
                                            today = currentSchoolDate,
                                            now = currentSchoolTime
                                        )
                                        Surface(
                                            modifier = Modifier
                                                .weight(1f)
                                                .fillMaxHeight()
                                                .padding(2.dp)
                                                .then(
                                                    if (currentCell) {
                                                        Modifier.border(
                                                            3.dp,
                                                            Color(0xFFFF4F87),
                                                            RoundedCornerShape(10.dp)
                                                        )
                                                    } else Modifier
                                                ),
                                            shape = RoundedCornerShape(10.dp),
                                            color = cellColor
                                        ) {
                                            Column(
                                                modifier = Modifier
                                                    .fillMaxSize()
                                                    .padding(horizontal = 7.dp, vertical = 6.dp),
                                                verticalArrangement = Arrangement.Center
                                            ) {
                                                if (currentCell) {
                                                    Surface(
                                                        shape = RoundedCornerShape(999.dp),
                                                        color = Color(0xFFFFD7E3)
                                                    ) {
                                                        Text(
                                                            "지금 수업",
                                                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                                                            style = MaterialTheme.typography.labelSmall,
                                                            color = Color(0xFF9B173F),
                                                            fontWeight = FontWeight.Bold
                                                        )
                                                    }
                                                    Spacer(Modifier.height(2.dp))
                                                }
                                                Text(
                                                    row.subject.ifBlank { "과목 미지정" },
                                                    style = MaterialTheme.typography.labelLarge,
                                                    fontWeight = FontWeight.Bold,
                                                    maxLines = 2,
                                                    overflow = TextOverflow.Ellipsis
                                                )

                                                if (row.teacher.isNotBlank()) {
                                                    Spacer(Modifier.height(2.dp))
                                                    Text(
                                                        row.teacher,
                                                        style = MaterialTheme.typography.labelSmall,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis,
                                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.88f)
                                                    )
                                                }

                                                if (row.room.isNotBlank()) {
                                                    Spacer(Modifier.height(1.dp))
                                                    Text(
                                                        row.room,
                                                        style = MaterialTheme.typography.labelSmall,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis,
                                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.68f)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                if (visiblePeriods.isEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "선택한 주에 등록된 수업이 없습니다.",
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            Spacer(Modifier.height(16.dp))
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.28f)
            ) {
                Text(
                    "${selectedGrade}학년 시간표 데이터가 없습니다.",
                    modifier = Modifier.padding(18.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun MemoScreen(state: AppUiState, vm: MainViewModel) {
    var selectedId by remember { mutableStateOf<String?>(null) }
    var query by remember { mutableStateOf("") }
    var pageMenuId by remember { mutableStateOf<String?>(null) }
    var pendingDeletePage by remember { mutableStateOf<NotePage?>(null) }
    val selected = state.notePages.firstOrNull { it.id == selectedId }

    LaunchedEffect(Unit) {
        if (state.loggedIn) vm.refreshNotes()
    }

    if (selectedId == null) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
                .padding(top = 2.dp, bottom = 16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(
                    enabled = state.loggedIn && !state.loading,
                    onClick = { vm.refreshNotes() },
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text(
                        if (state.loading) "동기화 중…" else "동기화",
                        style = MaterialTheme.typography.labelLarge
                    )
                }
                FilledTonalButton(
                    enabled = !state.loading,
                    modifier = Modifier.semantics { contentDescription = "새 페이지" },
                    onClick = {
                        val now = java.time.Instant.now().toString()
                        val page = NotePage(
                            id = java.util.UUID.randomUUID().toString(),
                            title = "",
                            category = "개인",
                            version = 0,
                            createdAt = now,
                            updatedAt = now,
                            blocks = listOf(
                                NoteBlock(
                                    id = java.util.UUID.randomUUID().toString(),
                                    type = NoteBlockType.TEXT,
                                    position = 0
                                )
                            ),
                            syncState = "LOCAL"
                        )
                        vm.saveNotePage(page)
                        selectedId = page.id
                    },
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                ) {
                    Text("+")
                }
            }

            Spacer(Modifier.height(2.dp))

            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("페이지 검색") },
                leadingIcon = {
                    Icon(
                        Icons.Default.Search,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                textStyle = MaterialTheme.typography.bodyMedium,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.40f),
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.18f)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 44.dp)
            )

            Spacer(Modifier.height(4.dp))

            val pages = state.notePages
                .filterNot { it.archived }
                .filter {
                    query.isBlank() ||
                        it.title.contains(query.trim(), ignoreCase = true) ||
                        it.category.contains(query.trim(), ignoreCase = true) ||
                        it.blocks.any { b -> b.content.contains(query.trim(), ignoreCase = true) }
                }
                .sortedWith(compareByDescending<NotePage> { it.pinned }.thenByDescending { it.updatedAt })

            if (pages.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 34.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        Icons.Filled.List,
                        contentDescription = null,
                        modifier = Modifier.size(28.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.48f)
                    )
                    Spacer(Modifier.height(7.dp))
                    Text(
                        if (query.isBlank()) "페이지가 없습니다." else "검색 결과가 없습니다.",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        if (query.isBlank()) "새 페이지를 만들어 메모를 시작하세요." else "다른 검색어를 입력해 보세요.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (query.isBlank()) {
                        Spacer(Modifier.height(10.dp))
                        FilledTonalButton(
                            enabled = !state.loading,
                            onClick = {
                                val now = java.time.Instant.now().toString()
                                val page = NotePage(
                                    id = java.util.UUID.randomUUID().toString(),
                                    title = "",
                                    category = "개인",
                                    version = 0,
                                    createdAt = now,
                                    updatedAt = now,
                                    blocks = listOf(
                                        NoteBlock(
                                            id = java.util.UUID.randomUUID().toString(),
                                            type = NoteBlockType.TEXT,
                                            position = 0
                                        )
                                    ),
                                    syncState = "LOCAL"
                                )
                                vm.saveNotePage(page)
                                selectedId = page.id
                            }
                        ) {
                            Text("새 페이지")
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(top = 2.dp, bottom = 84.dp)
                ) {
                    items(pages, key = { it.id }) { page ->
                        val preview = page.blocks.firstOrNull { it.content.isNotBlank() }?.content.orEmpty()
                        val updatedText = runCatching {
                            java.time.Instant.parse(page.updatedAt)
                                .atZone(java.time.ZoneId.systemDefault())
                                .format(java.time.format.DateTimeFormatter.ofPattern("MM.dd HH:mm"))
                        }.getOrDefault("")
                        val syncLabel = when (page.syncState) {
                            "PENDING" -> "동기화 대기"
                            "FAILED" -> "동기화 실패"
                            else -> ""
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TextButton(
                                onClick = {
                                    selectedId = page.id
                                    if (state.loggedIn && page.syncState == "SYNCED" && page.blocks.isEmpty()) {
                                        vm.openNotePage(page.id)
                                    }
                                },
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(horizontal = 2.dp, vertical = 7.dp)
                            ) {
                                Icon(
                                    Icons.Filled.List,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(Modifier.width(10.dp))
                                Column(
                                    modifier = Modifier.weight(1f),
                                    horizontalAlignment = Alignment.Start
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            page.title.ifBlank { "제목 없음" },
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        if (page.pinned) {
                                            Spacer(Modifier.width(6.dp))
                                            Text(
                                                "고정",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    }

                                    if (preview.isNotBlank()) {
                                        Spacer(Modifier.height(1.dp))
                                        Text(
                                            preview,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }

                                    Spacer(Modifier.height(1.dp))
                                    Text(
                                        listOfNotNull(
                                            page.category.ifBlank { "개인" },
                                            updatedText.takeIf { it.isNotBlank() },
                                            syncLabel.takeIf { it.isNotBlank() }
                                        ).joinToString(" · "),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (page.syncState == "FAILED") {
                                            MaterialTheme.colorScheme.error
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        }
                                    )
                                }
                                Text(
                                    "›",
                                    style = MaterialTheme.typography.titleLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
                                )
                            }

                            Box {
                                IconButton(onClick = { pageMenuId = page.id }) {
                                    Icon(
                                        Icons.Filled.MoreVert,
                                        contentDescription = "${page.title.ifBlank { "제목 없음" }} 페이지 더보기"
                                    )
                                }
                                DropdownMenu(
                                    expanded = pageMenuId == page.id,
                                    onDismissRequest = { pageMenuId = null }
                                ) {
                                    DropdownMenuItem(
                                        text = { Text("삭제") },
                                        leadingIcon = { Icon(Icons.Filled.Delete, contentDescription = null) },
                                        onClick = {
                                            pageMenuId = null
                                            pendingDeletePage = page
                                        }
                                    )
                                }
                            }
                        }

                        HorizontalDivider(
                            modifier = Modifier.padding(start = 30.dp),
                            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.08f)
                        )
                    }
                }
            }

            pendingDeletePage?.let { page ->
                AlertDialog(
                    onDismissRequest = { pendingDeletePage = null },
                    title = { Text("페이지 삭제") },
                    text = {
                        Text("‘${page.title.ifBlank { "제목 없음" }}’ 페이지를 삭제할까요?\n삭제한 페이지는 이 목록에서 사라지고 동기화된 데이터에도 삭제 상태가 반영됩니다.")
                    },
                    dismissButton = {
                        TextButton(onClick = { pendingDeletePage = null }) { Text("취소") }
                    },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                vm.archiveNotePage(page.id)
                                pendingDeletePage = null
                            }
                        ) {
                            Text("삭제", color = MaterialTheme.colorScheme.error)
                        }
                    }
                )
            }
        }
        return
    }

    if (selected == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }

    var title by remember(selected.id, selected.title) { mutableStateOf(selected.title) }
    var category by remember(selected.id, selected.category) { mutableStateOf(selected.category) }
    var blocks by remember(selected.id, selected.blocks) {
        mutableStateOf(selected.blocks.ifEmpty { listOf(NoteBlock(java.util.UUID.randomUUID().toString(), NoteBlockType.TEXT, position = 0)) })
    }
    var dirty by remember(selected.id) { mutableStateOf(false) }
    var topMenuExpanded by remember(selected.id) { mutableStateOf(false) }
    var categoryMenuExpanded by remember(selected.id) { mutableStateOf(false) }
    var addMenuExpanded by remember(selected.id) { mutableStateOf(false) }
    var blockMenuId by remember(selected.id) { mutableStateOf<String?>(null) }
    var slashTargetId by remember(selected.id) { mutableStateOf<String?>(null) }
    val density = LocalDensity.current
    val imeVisible = WindowInsets.ime.getBottom(density) > 0

    fun savePage() {
        vm.saveNotePage(
            selected.copy(
                title = title.trim().ifBlank { "제목 없음" },
                category = category.trim().ifBlank { "개인" },
                updatedAt = java.time.Instant.now().toString(),
                blocks = blocks.mapIndexed { index, block -> block.copy(position = index) }
            )
        )
    }

    fun addOrTransformBlock(type: NoteBlockType) {
        val target = slashTargetId
        if (target != null) {
            blocks = blocks.map { block -> if (block.id == target) block.copy(type = type, content = "") else block }
        } else {
            blocks = blocks + NoteBlock(id = java.util.UUID.randomUUID().toString(), type = type, position = blocks.size)
        }
        dirty = true
        slashTargetId = null
        addMenuExpanded = false
    }

    LaunchedEffect(dirty, title, category, blocks) {
        if (dirty) {
            kotlinx.coroutines.delay(650)
            dirty = false
            savePage()
        }
    }

    Column(
        Modifier.fillMaxSize().imePadding().padding(horizontal = 18.dp).padding(bottom = 10.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth().padding(top = 2.dp, bottom = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(
                onClick = {
                    if (dirty) {
                        dirty = false
                        savePage()
                    }
                    selectedId = null
                },
                contentPadding = PaddingValues(horizontal = 2.dp, vertical = 6.dp)
            ) { Text("‹  메모") }

            Spacer(Modifier.weight(1f))

            Text(
                when {
                    dirty -> "저장 중…"
                    selected.syncState == "PENDING" -> "동기화 중…"
                    selected.syncState == "SYNCED" -> "동기화됨 ✓"
                    !state.loggedIn -> "오프라인"
                    else -> "저장됨"
                },
                style = MaterialTheme.typography.labelMedium,
                color = if (!dirty && selected.syncState == "SYNCED") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            )

            Box {
                TextButton(onClick = { topMenuExpanded = true }, contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)) {
                    Text("⋯", style = MaterialTheme.typography.titleLarge)
                }
                DropdownMenu(expanded = topMenuExpanded, onDismissRequest = { topMenuExpanded = false }) {
                    DropdownMenuItem(text = { Text("지금 저장") }, onClick = {
                        topMenuExpanded = false
                        dirty = false
                        savePage()
                    })
                    DropdownMenuItem(text = { Text("보관") }, onClick = {
                        topMenuExpanded = false
                        vm.archiveNotePage(selected.id)
                        selectedId = null
                    })
                }
            }
        }

        Spacer(Modifier.height(14.dp))

        TextField(
            value = title,
            onValueChange = {
                title = it
                dirty = true
            },
            placeholder = {
                Text("제목 없음", style = MaterialTheme.typography.headlineLarge, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.40f))
            },
            textStyle = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Bold),
            minLines = 1,
            maxLines = 3,
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
                disabledContainerColor = Color.Transparent,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                disabledIndicatorColor = Color.Transparent
            ),
            modifier = Modifier.fillMaxWidth()
        )

        if (!imeVisible) {
            Row(modifier = Modifier.fillMaxWidth().padding(top = 2.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("속성", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(10.dp))
                Box {
                    AssistChip(onClick = { categoryMenuExpanded = true }, label = { Text(category.ifBlank { "개인" }) })
                    DropdownMenu(expanded = categoryMenuExpanded, onDismissRequest = { categoryMenuExpanded = false }) {
                        listOf("개인", "업무", "수업", "학생", "회의").forEach { item ->
                            DropdownMenuItem(text = { Text(item) }, onClick = {
                                category = item
                                dirty = true
                                categoryMenuExpanded = false
                            })
                        }
                    }
                }
            }
    
    
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.10f))

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(0.dp),
            contentPadding = PaddingValues(top = 8.dp, bottom = if (imeVisible) 20.dp else 36.dp)
        ) {
            items(blocks, key = { it.id }) { block ->
                val index = blocks.indexOfFirst { it.id == block.id }
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                    if (block.type == NoteBlockType.TODO) {
                        Checkbox(
                            checked = block.checked,
                            onCheckedChange = { checked ->
                                blocks = blocks.map { if (it.id == block.id) it.copy(checked = checked) else it }
                                dirty = true
                            },
                            modifier = Modifier.padding(top = 3.dp)
                        )
                    }

                    if (block.type == NoteBlockType.DIVIDER) {
                        HorizontalDivider(modifier = Modifier.weight(1f).padding(vertical = 22.dp))
                    } else {
                        val hint = when (block.type) {
                            NoteBlockType.LINK -> "링크 붙여넣기"
                            NoteBlockType.IMAGE -> "이미지 URL"
                            NoteBlockType.FILE -> "파일 URL 또는 경로"
                            NoteBlockType.QUOTE -> "인용문"
                            NoteBlockType.CODE -> "코드"
                            NoteBlockType.TODO -> "할 일"
                            NoteBlockType.HEADING1 -> "제목 1"
                            NoteBlockType.HEADING2 -> "제목 2"
                            else -> "내용을 입력하거나 / 를 눌러 명령"
                        }
                        val prefix = when (block.type) {
                            NoteBlockType.BULLET -> "•"
                            NoteBlockType.NUMBER -> noteNumberOrdinal(blocks, index).toString() + "."
                            NoteBlockType.QUOTE -> "│"
                            else -> ""
                        }
                        if (prefix.isNotBlank()) {
                            Text(prefix, modifier = Modifier.padding(top = 14.dp, end = 6.dp), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }

                        TextField(
                            value = block.content,
                            onValueChange = { value ->
                                when {
                                    value == "/" -> {
                                        blocks = blocks.map { if (it.id == block.id) it.copy(content = "") else it }
                                        slashTargetId = block.id
                                        addMenuExpanded = true
                                        dirty = true
                                    }
                                    value.contains("\n") -> {
                                        val split = value.split("\n", limit = 2)
                                        val m = blocks.toMutableList()
                                        m[index] = block.copy(content = split[0])
                                        m.add(index + 1, NoteBlock(
                                            id = java.util.UUID.randomUUID().toString(),
                                            type = NoteBlockType.TEXT,
                                            content = split.getOrElse(1) { "" },
                                            position = index + 1
                                        ))
                                        blocks = m
                                        dirty = true
                                    }
                                    else -> {
                                        blocks = blocks.map { if (it.id == block.id) it.copy(content = value) else it }
                                        dirty = true
                                    }
                                }
                            },
                            placeholder = { Text(hint, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)) },
                            minLines = 1,
                            textStyle = when (block.type) {
                                NoteBlockType.HEADING1 -> MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold)
                                NoteBlockType.HEADING2 -> MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold)
                                NoteBlockType.CODE -> MaterialTheme.typography.bodyMedium
                                else -> MaterialTheme.typography.bodyLarge
                            },
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                disabledContainerColor = Color.Transparent,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent,
                                disabledIndicatorColor = Color.Transparent
                            ),
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Box {
                        TextButton(onClick = { blockMenuId = block.id }, contentPadding = PaddingValues(horizontal = 5.dp, vertical = 8.dp)) {
                            Text("⋮", color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f))
                        }
                        DropdownMenu(expanded = blockMenuId == block.id, onDismissRequest = { blockMenuId = null }) {
                            DropdownMenuItem(text = { Text("위로 이동") }, enabled = index > 0, onClick = {
                                val m = blocks.toMutableList()
                                val item = m.removeAt(index)
                                m.add(index - 1, item)
                                blocks = m
                                dirty = true
                                blockMenuId = null
                            })
                            DropdownMenuItem(text = { Text("아래로 이동") }, enabled = index >= 0 && index < blocks.lastIndex, onClick = {
                                val m = blocks.toMutableList()
                                val item = m.removeAt(index)
                                m.add(index + 1, item)
                                blocks = m
                                dirty = true
                                blockMenuId = null
                            })
                            DropdownMenuItem(text = { Text("복제") }, onClick = {
                                val m = blocks.toMutableList()
                                m.add(index + 1, block.copy(id = java.util.UUID.randomUUID().toString()))
                                blocks = m
                                dirty = true
                                blockMenuId = null
                            })
                            if (block.content.isNotBlank()) {
                                DropdownMenuItem(text = { Text("TODO로 보내기") }, onClick = {
                                    vm.noteBlockToTodo(block.content, schoolToday().toString())
                                    blockMenuId = null
                                })
                                DropdownMenuItem(text = { Text("캘린더로 보내기") }, onClick = {
                                    vm.noteBlockToCalendar(block.content, schoolToday().toString())
                                    blockMenuId = null
                                })
                            }
                            DropdownMenuItem(text = { Text("삭제") }, onClick = {
                                blocks = blocks.filterNot { it.id == block.id }
                                dirty = true
                                blockMenuId = null
                            })
                        }
                    }
                }
            }

            item(key = "add-block-control") {
                    Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.34f)
            ) {
                Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box {
                        TextButton(onClick = {
                            slashTargetId = null
                            addMenuExpanded = true
                        }) { Text("+  블록 추가") }
                    }
                    Spacer(Modifier.weight(1f))
                    Text("/ 명령", modifier = Modifier.padding(end = 10.dp), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            }
        }

        Box(modifier = Modifier.size(1.dp)) {
            DropdownMenu(
                expanded = addMenuExpanded,
                onDismissRequest = {
                    addMenuExpanded = false
                    slashTargetId = null
                }
            ) {
                listOf(
                    NoteBlockType.TEXT to "텍스트",
                    NoteBlockType.HEADING1 to "제목 1",
                    NoteBlockType.HEADING2 to "제목 2",
                    NoteBlockType.TODO to "체크리스트",
                    NoteBlockType.BULLET to "글머리 기호",
                    NoteBlockType.NUMBER to "번호 매기기",
                    NoteBlockType.QUOTE to "인용",
                    NoteBlockType.CODE to "코드",
                    NoteBlockType.LINK to "링크",
                    NoteBlockType.IMAGE to "이미지",
                    NoteBlockType.FILE to "파일",
                    NoteBlockType.DIVIDER to "구분선"
                ).forEach { (type, label) ->
                    DropdownMenuItem(text = { Text(label) }, onClick = { addOrTransformBlock(type) })
                }
            }
        }
    }
}

@Composable
private fun TodoScreen(state: AppUiState, vm: MainViewModel) {
    var text by remember { mutableStateOf("") }
    var date by remember { mutableStateOf(schoolToday().toString()) }
    var showCompleted by remember { mutableStateOf(false) }

    val today = schoolToday()
    val activeTodos = remember(state.todos) { state.todos.filterNot { it.done }.sortedBy { it.dueDate } }
    val completedTodos = remember(state.todos) { state.todos.filter { it.done }.sortedByDescending { it.dueDate } }
    val visibleTodos = if (showCompleted) completedTodos else activeTodos
    val overdueCount = activeTodos.count {
        runCatching { LocalDate.parse(it.dueDate).isBefore(today) }.getOrDefault(false)
    }
    val validDate = runCatching { LocalDate.parse(date) }.isSuccess

    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                if (overdueCount > 0) "지연된 할 일 ${overdueCount}개" else "할 일 ${activeTodos.size}개",
                style = MaterialTheme.typography.bodyMedium,
                color = if (overdueCount > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
            TextButton(
                onClick = { showCompleted = !showCompleted },
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(if (showCompleted) "진행 보기" else "완료 ${completedTodos.size}")
            }
        }

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            tonalElevation = 1.dp
        ) {
            Column(Modifier.padding(horizontal = 10.dp, vertical = 9.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = text,
                        onValueChange = { text = it },
                        modifier = Modifier.weight(1f).heightIn(min = 50.dp),
                        singleLine = true,
                        placeholder = { Text("새 할 일") },
                        shape = RoundedCornerShape(12.dp),
                        textStyle = MaterialTheme.typography.bodyLarge
                    )
                    Spacer(Modifier.width(7.dp))
                    FilledTonalButton(
                        onClick = {
                            vm.addTodo(text, date)
                            text = ""
                        },
                        enabled = text.isNotBlank() && validDate,
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 13.dp)
                    ) {
                        Text("추가")
                    }
                }

                Spacer(Modifier.height(6.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = date,
                        onValueChange = { date = it },
                        modifier = Modifier.weight(1f).heightIn(min = 46.dp),
                        singleLine = true,
                        label = { Text("마감") },
                        placeholder = { Text("YYYY-MM-DD") },
                        isError = date.isNotBlank() && !validDate,
                        shape = RoundedCornerShape(12.dp),
                        textStyle = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(Modifier.width(6.dp))
                    AssistChip(onClick = { date = today.toString() }, label = { Text("오늘") })
                    Spacer(Modifier.width(5.dp))
                    AssistChip(onClick = { date = today.plusDays(1).toString() }, label = { Text("내일") })
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(
                        onClick = { date = today.plusWeeks(1).toString() },
                        contentPadding = PaddingValues(horizontal = 7.dp, vertical = 1.dp)
                    ) {
                        Text("다음 주")
                    }
                }
            }
        }

        Spacer(Modifier.height(7.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            FilterChip(
                selected = !showCompleted,
                onClick = { showCompleted = false },
                label = { Text("진행 ${activeTodos.size}") },
                modifier = Modifier.weight(1f)
            )
            FilterChip(
                selected = showCompleted,
                onClick = { showCompleted = true },
                label = { Text("완료 ${completedTodos.size}") },
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(Modifier.height(5.dp))

        if (visibleTodos.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 44.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    if (showCompleted) "완료된 할 일이 없습니다." else "진행 중인 할 일이 없습니다.",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    if (showCompleted) "완료한 항목이 여기에 표시됩니다." else "위에서 새 할 일을 추가하세요.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
                contentPadding = PaddingValues(bottom = 12.dp)
            ) {
                items(visibleTodos, key = { it.id }) { todo ->
                    val due = runCatching { LocalDate.parse(todo.dueDate) }.getOrNull()
                    val overdue = !todo.done && due != null && due.isBefore(today)
                    val dueToday = due == today

                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = todo.done,
                            onCheckedChange = { vm.toggleTodo(todo.id) }
                        )
                        Column(Modifier.weight(1f)) {
                            Text(
                                todo.text,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = if (todo.done) FontWeight.Normal else FontWeight.Medium,
                                color = if (todo.done) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                when {
                                    overdue -> "마감 지남 · ${todo.dueDate}"
                                    dueToday -> "오늘 · ${todo.dueDate}"
                                    else -> todo.dueDate
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = if (overdue) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (!todo.done) {
                            IconButton(onClick = { vm.todoToCalendar(todo.id) }) {
                                Icon(
                                    Icons.Filled.DateRange,
                                    contentDescription = "일정으로 보내기",
                                    modifier = Modifier.size(19.dp)
                                )
                            }
                        }
                        IconButton(onClick = { vm.deleteTodo(todo.id) }) {
                            Icon(
                                Icons.Filled.Delete,
                                contentDescription = "할 일 삭제",
                                modifier = Modifier.size(19.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    HorizontalDivider(
                        modifier = Modifier.padding(start = 48.dp),
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.08f)
                    )
                }
            }
        }
    }
}

@Composable
private fun CalendarScreen(state: AppUiState, vm: MainViewModel) {
    val today = schoolToday()
    var visibleMonth by remember { mutableStateOf(YearMonth.from(today)) }
    var selectedDate by remember { mutableStateOf(today) }
    var title by remember { mutableStateOf("") }
    var showAdd by remember { mutableStateOf(false) }

    val firstDay = visibleMonth.atDay(1)
    val leadingBlankCount = firstDay.dayOfWeek.value % 7
    val daysInMonth = visibleMonth.lengthOfMonth()
    val cells = remember(visibleMonth) {
        List(42) { index ->
            val day = index - leadingBlankCount + 1
            if (day in 1..daysInMonth) visibleMonth.atDay(day) else null
        }
    }
    val eventsByDate = remember(state.calendar) {
        state.calendar.groupBy { it.date }
    }
    val selectedEvents = eventsByDate[selectedDate.toString()].orEmpty().sortedBy { it.title }
    val holidayName = KoreanHolidays.name(selectedDate)
    val monthFormatter = remember { DateTimeFormatter.ofPattern("yyyy년 M월", Locale.KOREA) }
    val fullDateFormatter = remember { DateTimeFormatter.ofPattern("M월 d일 (E)", Locale.KOREA) }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 12.dp)
            .padding(bottom = 110.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Spacer(Modifier.weight(1f))
            TextButton(
                onClick = {
                    visibleMonth = YearMonth.from(today)
                    selectedDate = today
                }
            ) { Text("오늘") }
            TextButton(onClick = { showAdd = !showAdd }) {
                Text(if (showAdd) "닫기" else "일정 추가")
            }
        }

        if (showAdd) {
            ElevatedCard(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp)) {
                Column(Modifier.padding(12.dp)) {
                    Text(
                        selectedDate.format(fullDateFormatter) + " 일정 추가",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.height(7.dp))
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        placeholder = { Text("일정 내용을 입력하세요") },
                        shape = RoundedCornerShape(14.dp)
                    )
                    Spacer(Modifier.height(7.dp))
                    Button(
                        onClick = {
                            vm.addCalendar(title, selectedDate.toString())
                            title = ""
                            showAdd = false
                        },
                        enabled = title.isNotBlank(),
                        modifier = Modifier.align(Alignment.End),
                        shape = RoundedCornerShape(14.dp)
                    ) { Text("등록") }
                }
            }
            Spacer(Modifier.height(8.dp))
        }

        ElevatedCard(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp)) {
            Column(Modifier.padding(horizontal = 8.dp, vertical = 10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = {
                            visibleMonth = visibleMonth.minusMonths(1)
                            selectedDate = visibleMonth.atDay(1)
                        }
                    ) {
                        Icon(Icons.Filled.KeyboardArrowLeft, contentDescription = "이전 달")
                    }
                    Text(
                        visibleMonth.format(monthFormatter),
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(
                        onClick = {
                            visibleMonth = visibleMonth.plusMonths(1)
                            selectedDate = visibleMonth.atDay(1)
                        }
                    ) {
                        Icon(Icons.Filled.KeyboardArrowRight, contentDescription = "다음 달")
                    }
                }

                Row(Modifier.fillMaxWidth()) {
                    listOf("일", "월", "화", "수", "목", "금", "토").forEachIndexed { index, label ->
                        Text(
                            label,
                            modifier = Modifier.weight(1f).padding(vertical = 5.dp),
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = when (index) {
                                0 -> MaterialTheme.colorScheme.error
                                6 -> MaterialTheme.colorScheme.primary
                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    }
                }

                cells.chunked(7).forEach { week ->
                    Row(Modifier.fillMaxWidth()) {
                        week.forEach { date ->
                            if (date == null) {
                                Spacer(Modifier.weight(1f).height(58.dp))
                            } else {
                                val holiday = KoreanHolidays.name(date)
                                val isSunday = date.dayOfWeek.value == 7
                                val isSaturday = date.dayOfWeek.value == 6
                                val selected = date == selectedDate
                                val isToday = date == today
                                val eventCount = eventsByDate[date.toString()].orEmpty().size

                                Surface(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(58.dp)
                                        .padding(1.dp),
                                    onClick = { selectedDate = date },
                                    shape = RoundedCornerShape(12.dp),
                                    color = when {
                                        selected -> MaterialTheme.colorScheme.primaryContainer
                                        isToday -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f)
                                        else -> Color.Transparent
                                    }
                                ) {
                                    Column(
                                        modifier = Modifier.fillMaxSize().padding(horizontal = 3.dp, vertical = 4.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Text(
                                            date.dayOfMonth.toString(),
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = if (selected || isToday) FontWeight.Bold else FontWeight.Medium,
                                            color = when {
                                                holiday != null || isSunday -> MaterialTheme.colorScheme.error
                                                isSaturday -> MaterialTheme.colorScheme.primary
                                                else -> MaterialTheme.colorScheme.onSurface
                                            }
                                        )
                                        if (holiday != null) {
                                            Text(
                                                holiday,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.error,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        } else if (eventCount > 0) {
                                            Text(
                                                "일정 " + eventCount,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.primary,
                                                maxLines = 1
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    selectedDate.format(fullDateFormatter),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                if (holidayName != null) {
                    Text(
                        holidayName,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
            if (selectedEvents.isNotEmpty()) {
                Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.primaryContainer) {
                    Text(
                        selectedEvents.size.toString() + "개 일정",
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }
        }

        Spacer(Modifier.height(6.dp))

        if (selectedEvents.isEmpty()) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.24f)
            ) {
                Column(
                    modifier = Modifier.padding(vertical = 22.dp, horizontal = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        if (holidayName != null) holidayName else "등록된 일정이 없습니다.",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = if (holidayName != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                    )
                    if (holidayName == null) {
                        Spacer(Modifier.height(3.dp))
                        Text(
                            "일정 추가를 눌러 이 날짜에 일정을 등록할 수 있습니다.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        } else {
            Column(
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                selectedEvents.forEach { item ->
                    ElevatedCard(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 9.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                modifier = Modifier.size(38.dp),
                                shape = RoundedCornerShape(11.dp),
                                color = MaterialTheme.colorScheme.primaryContainer
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(Icons.Filled.DateRange, contentDescription = null, modifier = Modifier.size(20.dp))
                                }
                            }
                            Spacer(Modifier.width(10.dp))
                            Text(
                                item.title,
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            IconButton(onClick = { vm.deleteCalendar(item.id) }) {
                                Icon(Icons.Filled.Delete, contentDescription = "일정 삭제", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
    }
}


@Composable
private fun BroadcastScreen(state: AppUiState, vm: MainViewModel) {
    var text by remember { mutableStateOf("") }
    var tts by remember { mutableStateOf(false) }
    val selected = remember { mutableStateListOf<String>() }
    val onlineTotal = state.classrooms.sumOf { it.onlineCount }
    val allSelected = state.classrooms.isNotEmpty() && state.classrooms.all { it.id in selected }

    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "메시지와 TTS 음성 방송을 교실로 보냅니다.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = vm::loadClassrooms) { Text("새로고침") }
        }

        ElevatedCard(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp)) {
            Column(Modifier.padding(14.dp)) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { if (it.length <= 500) text = it },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                    maxLines = 5,
                    placeholder = { Text("방송 내용을 입력하세요") },
                    supportingText = {
                        Text(
                            text.length.toString() + "/500",
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.End
                        )
                    },
                    shape = RoundedCornerShape(14.dp)
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Switch(checked = tts, onCheckedChange = { tts = it })
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text("TTS 음성 방송", fontWeight = FontWeight.SemiBold)
                        Text(
                            if (tts) "수신 전자칠판에서 내용을 음성으로 읽습니다." else "텍스트 공지만 전송합니다.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("수신 교실", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            Text(
                selected.size.toString() + "개 선택 · 온라인 " + onlineTotal,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            AssistChip(
                onClick = {
                    selected.clear()
                    if (!allSelected) selected.addAll(state.classrooms.map { it.id })
                },
                label = { Text(if (allSelected) "전체 해제" else "전체 선택") }
            )
            AssistChip(
                onClick = {
                    selected.clear()
                    selected.addAll(state.classrooms.filter { it.onlineCount > 0 }.map { it.id })
                },
                label = { Text("온라인만") }
            )
        }

        Spacer(Modifier.height(6.dp))

        if (state.classrooms.isEmpty()) {
            Surface(
                modifier = Modifier.fillMaxWidth().weight(1f),
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.24f)
            ) {
                Column(
                    modifier = Modifier.fillMaxSize().padding(20.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("등록된 수신 교실이 없습니다.", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "새로고침하여 전자칠판 연결 상태를 확인하세요.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                contentPadding = PaddingValues(bottom = 8.dp)
            ) {
                items(state.classrooms, key = { it.id }) { room ->
                    val checked = room.id in selected
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        color = if (checked) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.60f)
                        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.22f)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = checked,
                                onCheckedChange = {
                                    if (it) selected.add(room.id) else selected.remove(room.id)
                                }
                            )
                            Text(room.name, modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (room.onlineCount > 0) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
                            ) {
                                Text(
                                    if (room.onlineCount > 0) "온라인 " + room.onlineCount else "오프라인",
                                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = if (room.onlineCount > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }

        Button(
            onClick = {
                vm.sendBroadcast(selected.toList(), text, tts)
                text = ""
            },
            enabled = selected.isNotEmpty() && text.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp)
        ) {
            Text(
                if (selected.isEmpty()) "수신 교실을 선택하세요"
                else "선택한 " + selected.size + "개 교실에 방송 보내기"
            )
        }

        if (state.message.isNotBlank()) {
            Spacer(Modifier.height(6.dp))
            Text(
                state.message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.height(8.dp))
    }
}


internal fun noteNumberOrdinal(blocks: List<NoteBlock>, index: Int): Int {
    if (index !in blocks.indices || blocks[index].type != NoteBlockType.NUMBER) return 0
    var ordinal = 1
    var cursor = index - 1
    while (cursor >= 0 && blocks[cursor].type == NoteBlockType.NUMBER) {
        ordinal += 1
        cursor -= 1
    }
    return ordinal
}

private val TIMETABLE_SUBJECT_PALETTE = listOf(
    Color(0xFFB97932), Color(0xFF2F6FED), Color(0xFF25A873), Color(0xFFE13F51),
    Color(0xFFF4771F), Color(0xFFE5AA12), Color(0xFF8A43E6), Color(0xFF2AA8B5),
    Color(0xFFD94C8A), Color(0xFF267ABF), Color(0xFF5E9E3E), Color(0xFFD1663B),
    Color(0xFF6D5BD0), Color(0xFF0F9D8B), Color(0xFFB85A88), Color(0xFF7A6A3E)
)

internal fun timetableSubjectColorIndexes(subjects: List<String>): Map<String, Int> {
    val unique = subjects
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .distinct()
        .sortedWith(String.CASE_INSENSITIVE_ORDER)
    return unique.mapIndexed { index, subject ->
        subject to (index % TIMETABLE_SUBJECT_PALETTE.size)
    }.toMap()
}

private fun timetableSubjectColors(subjects: List<String>): Map<String, Color> =
    timetableSubjectColorIndexes(subjects).mapValues { (_, index) -> TIMETABLE_SUBJECT_PALETTE[index] }

private fun Color?.orEmptyColor(): Color = this ?: Color(0xFF405D7A)
