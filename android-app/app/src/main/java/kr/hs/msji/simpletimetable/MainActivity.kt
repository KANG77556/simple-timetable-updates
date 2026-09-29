package kr.hs.msji.simpletimetable

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import java.time.LocalDate

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
            MaterialTheme(colorScheme = darkColorScheme()) {
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
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("SimpleTimetable", fontWeight = FontWeight.Bold)
                        Text(
                            state.profile.displayName.ifBlank { "밀성제일고등학교" } + " · v" + BuildConfig.VERSION_NAME,
                            style = MaterialTheme.typography.labelSmall
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
            Modifier.fillMaxSize().padding(horizontal = 28.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                "SimpleTimetable",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold
            )
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

@Composable
private fun TodayScreen(state: AppUiState, vm: MainViewModel) {
    val status = remember(state.myTimetable) { TimetableStatus.calculate(state.myTimetable) }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("오늘의 시간표", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text(state.today)
            }
            Button(onClick = vm::refreshToday) { Text("새로고침") }
        }

        Spacer(Modifier.height(14.dp))

        Card(
            Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
        ) {
            Column(Modifier.padding(18.dp)) {
                val headline = when {
                    status.current != null -> listOf(
                        "${status.current.period}교시",
                        status.current.subject,
                        listOf(status.current.grade.takeIf { it > 0 }?.let { "${it}학년" }, status.current.classCode).filterNotNull().filter { it.isNotBlank() }.joinToString(" ")
                    ).filter { it.isNotBlank() }.joinToString(" · ")
                    status.next != null -> "다음 " + listOf(
                        "${status.next.period}교시",
                        status.next.subject,
                        listOf(status.next.grade.takeIf { it > 0 }?.let { "${it}학년" }, status.next.classCode).filterNotNull().filter { it.isNotBlank() }.joinToString(" ")
                    ).filter { it.isNotBlank() }.joinToString(" · ")
                    state.myTimetable.isEmpty() -> "오늘 수업 없음"
                    else -> "오늘 수업 종료"
                }
                val detail = when {
                    status.current != null -> {
                        val room = status.current.room.takeIf { it.isNotBlank() } ?: "교실 미지정"
                        "현재 수업 · ${status.minutesRemaining ?: 0}분 남음 · $room"
                    }
                    status.next != null -> {
                        val room = status.next.room.takeIf { it.isNotBlank() } ?: "교실 미지정"
                        "수업 시작까지 ${status.minutesUntilNext ?: 0}분 · $room"
                    }
                    else -> "오늘 일정이 모두 끝났습니다."
                }
                Text(headline, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                Text(detail)
            }
        }

        Spacer(Modifier.height(12.dp))

        if (state.myTimetable.isEmpty()) {
            Card(Modifier.fillMaxWidth()) {
                Text("오늘 등록된 수업이 없습니다.", Modifier.padding(18.dp))
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(state.myTimetable, key = { it.period }) { row ->
                    val active = status.current?.period == row.period
                    Card(
                        Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = if (active) MaterialTheme.colorScheme.secondaryContainer
                            else MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                shape = MaterialTheme.shapes.medium,
                                tonalElevation = if (active) 6.dp else 3.dp
                            ) {
                                Text(
                                    "${row.period}",
                                    Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Spacer(Modifier.width(14.dp))
                            Column(Modifier.weight(1f)) {
                                Text(row.subject, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                val time = listOf(row.startTime.take(5), row.endTime.take(5)).filter { it.isNotBlank() }.joinToString(" ~ ")
                                val gradeClass = listOf(
                                    row.grade.takeIf { it > 0 }?.let { "${it}학년" },
                                    row.classCode.takeIf { it.isNotBlank() }
                                ).filterNotNull().joinToString(" ")
                                Text(listOf(gradeClass, time, row.room).filter { it.isNotBlank() }.joinToString(" · "))
                            }
                            if (active) {
                                AssistChip(onClick = {}, label = { Text("현재") })
                            }
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
    val gradeRows = state.allTimetable.filter { it.grade == selectedGrade }
    val classes = gradeRows.map { it.classCode }.distinct().sortedWith(
        compareBy<String>({ classSortKey(it).first }, { classSortKey(it).second }, { classSortKey(it).third })
    )
    var selectedClass by remember(selectedGrade, classes) {
        mutableStateOf(classes.firstOrNull().orEmpty())
    }
    val rows = gradeRows.filter { it.classCode == selectedClass }.sortedBy { it.period }

    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    "전체 시간표",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    if (classes.isEmpty()) "${selectedGrade}학년 시간표 없음"
                    else "${selectedGrade}학년 · ${classes.size}개 학급",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            FilledTonalButton(onClick = vm::refreshAll) {
                Text("새로고침")
            }
        }

        Spacer(Modifier.height(12.dp))

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
        ) {
            Row(
                Modifier.padding(6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
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
        }

        if (classes.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            Row(
                Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                classes.forEach { classCode ->
                    FilterChip(
                        selected = selectedClass == classCode,
                        onClick = { selectedClass = classCode },
                        label = { Text(classCode) }
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                )
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            selectedClass,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "${selectedGrade}학년 · ${rows.size}교시",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    SuggestionChip(
                        onClick = {},
                        label = { Text("${selectedGrade}학년") }
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(6.dp),
                contentPadding = PaddingValues(bottom = 12.dp)
            ) {
                items(rows, key = { "${it.classCode}-${it.period}-${it.subject}" }) { row ->
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.large,
                        tonalElevation = 1.dp,
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)
                    ) {
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape = MaterialTheme.shapes.medium,
                                color = MaterialTheme.colorScheme.primaryContainer
                            ) {
                                Text(
                                    "${row.period}",
                                    modifier = Modifier.padding(horizontal = 13.dp, vertical = 9.dp),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    row.subject.ifBlank { "과목 미지정" },
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1
                                )
                                val detail = listOf(row.teacher, row.room)
                                    .filter { it.isNotBlank() }
                                    .joinToString(" · ")
                                if (detail.isNotBlank()) {
                                    Text(
                                        detail,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1
                                    )
                                }
                            }
                            Text(
                                "${row.period}교시",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        } else {
            Spacer(Modifier.height(20.dp))
            Card(Modifier.fillMaxWidth()) {
                Text(
                    "${selectedGrade}학년 시간표 데이터가 없습니다.",
                    modifier = Modifier.padding(20.dp),
                    style = MaterialTheme.typography.bodyMedium
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
    var date by remember { mutableStateOf(LocalDate.now().toString()) }
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
    var date by remember { mutableStateOf(LocalDate.now().toString()) }
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
