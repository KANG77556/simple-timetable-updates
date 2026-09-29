package kr.hs.msji.simpletimetable

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.compose.foundation.border
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
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import java.time.LocalDate
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
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = Color(0xFF8FC7FF),
                    onPrimary = Color(0xFF07111D),
                    primaryContainer = Color(0xFF16283A),
                    onPrimaryContainer = Color(0xFFD7EAFF),
                    secondary = Color(0xFFA9C7E8),
                    secondaryContainer = Color(0xFF203040),
                    background = Color(0xFF0B0D10),
                    surface = Color(0xFF111419),
                    surfaceVariant = Color(0xFF181C22),
                    onSurface = Color(0xFFF4F6F8),
                    onSurfaceVariant = Color(0xFFB4BBC5),
                    outline = Color(0xFF2A313A)
                )
            ) {
                val vm: MainViewModel = viewModel()
                SimpleTimetableApp(vm)
            }
        }
    }
}

enum class AppTab(val label: String) {
    TODAY("오늘"), ALL("전체"), MEMO("메모"), TODO("TODO"), CALENDAR("캘린더"), BROADCAST("방송")
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
fun SimpleTimetableApp(vm: MainViewModel) {
    val state by vm.state.collectAsState()
    var tab by remember { mutableStateOf(AppTab.TODAY) }

    if (!state.loggedIn) {
        LoginScreen(state, vm)
        return
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                ),
                title = {
                    Column {
                        Text(
                            "밀성제일고",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            state.profile.displayName.ifBlank { "선생님" } + " · v" + BuildConfig.VERSION_NAME,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                actions = { UpdateAction() }
            )
        },
        bottomBar = {
            NavigationBar {
                AppTab.entries.forEach { item ->
                    NavigationBarItem(
                        selected = tab == item,
                        onClick = {
                            tab = item
                            if (item == AppTab.ALL && state.allTimetable.isEmpty()) vm.refreshAll()
                            if (item == AppTab.BROADCAST && state.classrooms.isEmpty()) vm.loadClassrooms()
                        },
                        icon = { AppTabIcon(item) },
                        label = { Text(item.label) }
                    )
                }
            }
        }
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when (tab) {
                AppTab.TODAY -> TodayScreen(state, vm)
                AppTab.ALL -> AllTimetableScreen(state, vm)
                AppTab.MEMO -> MemoScreen(state, vm)
                AppTab.TODO -> TodoScreen(state, vm)
                AppTab.CALENDAR -> CalendarScreen(state, vm)
                AppTab.BROADCAST -> BroadcastScreen(state, vm)
            }
            if (state.loading) {
                CircularProgressIndicator(Modifier.align(Alignment.Center))
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

@Composable
private fun TodayScreen(state: AppUiState, vm: MainViewModel) {
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
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                if (isToday) "오늘의 시간표" else "시간표",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
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

        Spacer(Modifier.height(12.dp))

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

        Spacer(Modifier.height(14.dp))

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
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "전체 시간표",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            Text(
                if (classes.isEmpty()) "${selectedGrade}학년 시간표 없음"
                else allTimetableGradeSummary(selectedGrade, classes.size),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.width(4.dp))
            TextButton(
                onClick = vm::refreshAll,
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Text("↻", style = MaterialTheme.typography.titleLarge)
            }
        }

        Spacer(Modifier.height(4.dp))

        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            placeholder = { Text("과목·교사·학급·교실 검색") },
            leadingIcon = {
                Icon(Icons.Filled.Search, contentDescription = "검색")
            },
            trailingIcon = {
                if (query.isNotBlank()) {
                    IconButton(onClick = { query = "" }) {
                        Icon(Icons.Filled.Close, contentDescription = "검색어 지우기")
                    }
                }
            },
            shape = RoundedCornerShape(18.dp)
        )

        Spacer(Modifier.height(4.dp))

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f)
        ) {
            Column(Modifier.padding(6.dp)) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    (1..3).forEach { grade ->
                        FilterChip(
                            selected = selectedGrade == grade,
                            onClick = { selectedGrade = grade },
                            label = { Text("${grade}학년") },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                if (classes.isNotEmpty()) {
                    Spacer(Modifier.height(4.dp))
                    Row(
                        Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        classes.forEach { classCode ->
                            FilterChip(
                                selected = !searching && selectedClass == classCode,
                                onClick = {
                                    selectedClass = classCode
                                    query = ""
                                },
                                label = { Text(classCode) }
                            )
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
            val visiblePeriods = selectedRows.map { it.period }.distinct().sorted()
            val formatter = remember { DateTimeFormatter.ofPattern("M/d", Locale.KOREA) }
            val rangeFormatter = remember { DateTimeFormatter.ofPattern("yyyy.MM.dd", Locale.KOREA) }
            val weekdayLabels = listOf("월", "화", "수", "목", "금")

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                FilledTonalIconButton(onClick = { vm.moveAllWeek(-1) }) {
                    Icon(Icons.Filled.KeyboardArrowLeft, contentDescription = "이전 주")
                }
                Text(
                    "${weekStart.format(rangeFormatter)} - ${weekStart.plusDays(4).format(rangeFormatter)}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                FilledTonalIconButton(onClick = { vm.moveAllWeek(1) }) {
                    Icon(Icons.Filled.KeyboardArrowRight, contentDescription = "다음 주")
                }
            }

            Spacer(Modifier.height(6.dp))

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.18f),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Column(Modifier.fillMaxWidth()) {
                    Row(Modifier.fillMaxWidth()) {
                        Box(
                            Modifier
                                .width(54.dp)
                                .height(62.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("교시", fontWeight = FontWeight.Bold)
                        }

                        weekDates.forEachIndexed { index, date ->
                            val isToday = date == schoolToday()
                            Surface(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(62.dp)
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
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(76.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .width(54.dp)
                                    .fillMaxHeight(),
                                verticalArrangement = Arrangement.Center,
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    "$period",
                                    style = MaterialTheme.typography.titleMedium,
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
                                    val cellColor = remember(row.subject) { timetableSubjectColor(row.subject) }
                                    Surface(
                                        modifier = Modifier
                                            .weight(1f)
                                            .fillMaxHeight()
                                            .padding(2.dp),
                                        shape = RoundedCornerShape(10.dp),
                                        color = cellColor
                                    ) {
                                        Column(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .padding(horizontal = 7.dp, vertical = 6.dp),
                                            verticalArrangement = Arrangement.Center
                                        ) {
                                            Text(
                                                row.subject,
                                                style = MaterialTheme.typography.labelLarge,
                                                fontWeight = FontWeight.Bold,
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
                                                    style = MaterialTheme.typography.labelSmall,
                                                    maxLines = 2,
                                                    overflow = TextOverflow.Ellipsis,
                                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.82f)
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
    var text by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("메모", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(text, { text = it }, label = { Text("새 메모") }, modifier = Modifier.fillMaxWidth())
        Button(onClick = { vm.addMemo(text); text = "" }, modifier = Modifier.align(Alignment.End)) { Text("추가") }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(state.memos.reversed(), key = { it.id }) { memo ->
                Card(Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(memo.text, Modifier.weight(1f))
                        TextButton(onClick = { vm.deleteMemo(memo.id) }) { Text("삭제") }
                    }
                }
            }
        }
    }
}

@Composable
private fun TodoScreen(state: AppUiState, vm: MainViewModel) {
    var text by remember { mutableStateOf("") }
    var date by remember { mutableStateOf(schoolToday().toString()) }
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("TODO", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        OutlinedTextField(text, { text = it }, label = { Text("할 일") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(date, { date = it }, label = { Text("마감일 YYYY-MM-DD") }, modifier = Modifier.fillMaxWidth())
        Button(onClick = { vm.addTodo(text, date); text = "" }, modifier = Modifier.align(Alignment.End)) { Text("추가") }
        LazyColumn {
            items(state.todos, key = { it.id }) { todo ->
                Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = todo.done, onCheckedChange = { vm.toggleTodo(todo.id) })
                    Column {
                        Text(todo.text, fontWeight = if (todo.done) FontWeight.Normal else FontWeight.SemiBold)
                        Text(todo.dueDate, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}

@Composable
private fun CalendarScreen(state: AppUiState, vm: MainViewModel) {
    var title by remember { mutableStateOf("") }
    var date by remember { mutableStateOf(schoolToday().toString()) }
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("캘린더", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        OutlinedTextField(title, { title = it }, label = { Text("일정") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(date, { date = it }, label = { Text("날짜 YYYY-MM-DD") }, modifier = Modifier.fillMaxWidth())
        Button(onClick = { vm.addCalendar(title, date); title = "" }, modifier = Modifier.align(Alignment.End)) { Text("등록") }
        LazyColumn {
            items(state.calendar.sortedBy { it.date }, key = { it.id }) { item ->
                ListItem(
                    headlineContent = { Text(item.title) },
                    supportingContent = { Text(item.date) }
                )
            }
        }
    }
}

@Composable
private fun BroadcastScreen(state: AppUiState, vm: MainViewModel) {
    var text by remember { mutableStateOf("") }
    var tts by remember { mutableStateOf(false) }
    val selected = remember { mutableStateListOf<String>() }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("전자칠판 방송", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            TextButton(onClick = vm::loadClassrooms) { Text("새로고침") }
        }
        OutlinedTextField(text, { text = it }, label = { Text("방송 내용") }, modifier = Modifier.fillMaxWidth())
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(tts, { tts = it })
            Text("TTS 음성 방송")
        }
        Text("수신 교실", fontWeight = FontWeight.SemiBold)
        LazyColumn(Modifier.weight(1f)) {
            items(state.classrooms, key = { it.id }) { room ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = room.id in selected,
                        onCheckedChange = {
                            if (it) selected.add(room.id) else selected.remove(room.id)
                        }
                    )
                    Text(room.name, Modifier.weight(1f))
                    Text("온라인 ${room.onlineCount}")
                }
            }
        }
        Button(
            onClick = { vm.sendBroadcast(selected.toList(), text, tts) },
            enabled = selected.isNotEmpty() && text.isNotBlank(),
            modifier = Modifier.fillMaxWidth()
        ) { Text("방송 보내기") }
        if (state.message.isNotBlank()) Text(state.message)
    }
}


private fun timetableSubjectColor(subject: String): Color {
    val palette = listOf(
        Color(0xFF3A275F),
        Color(0xFF244D73),
        Color(0xFF6C3D20),
        Color(0xFF14613F),
        Color(0xFF8A2432),
        Color(0xFF1B6B73),
        Color(0xFF4F6B27)
    )
    val index = (subject.hashCode() and Int.MAX_VALUE) % palette.size
    return palette[index]
}
