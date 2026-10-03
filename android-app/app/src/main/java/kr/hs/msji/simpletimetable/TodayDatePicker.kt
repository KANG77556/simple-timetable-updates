package kr.hs.msji.simpletimetable

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

internal data class CalendarDayCell(
    val date: LocalDate?,
    val holidayName: String? = null
)

internal fun monthCalendarCells(month: YearMonth): List<CalendarDayCell> {
    val firstDay = month.atDay(1)
    val sundayBasedOffset = firstDay.dayOfWeek.value % 7
    val cells = MutableList<CalendarDayCell>(42) { CalendarDayCell(null) }

    for (day in 1..month.lengthOfMonth()) {
        val date = month.atDay(day)
        cells[sundayBasedOffset + day - 1] = CalendarDayCell(
            date = date,
            holidayName = KoreanHolidays.name(date)
        )
    }
    return cells
}

@Composable
internal fun TimetableDatePickerDialog(
    initialDate: LocalDate,
    onDismiss: () -> Unit,
    onConfirm: (LocalDate) -> Unit
) {
    var selectedDate by remember(initialDate) { mutableStateOf(initialDate) }
    var visibleMonth by remember(initialDate) { mutableStateOf(YearMonth.from(initialDate)) }
    val monthTitle = remember(visibleMonth) {
        visibleMonth.format(DateTimeFormatter.ofPattern("yyyy년 M월", Locale.KOREAN))
    }
    val selectedTitle = remember(selectedDate) {
        selectedDate.format(DateTimeFormatter.ofPattern("M월 d일 (E)", Locale.KOREAN))
    }
    val cells = remember(visibleMonth) { monthCalendarCells(visibleMonth) }
    val weekdays = listOf("일", "월", "화", "수", "목", "금", "토")

    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val fontScale = density.fontScale
    val maxDialogHeight = (configuration.screenHeightDp - 48).coerceAtLeast(48).dp
    val maxDialogWidth = (configuration.screenWidthDp - 24).coerceIn(48, 420).dp
    val minimumGridWidth = (7 * maxOf(48f, 24f * fontScale + 2f)).dp

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        // Keep the caller's font scaling inside the dialog composition.
        CompositionLocalProvider(LocalDensity provides density) {
            Surface(
                modifier = Modifier
                    .widthIn(max = maxDialogWidth)
                    .fillMaxWidth()
                    .heightIn(max = maxDialogHeight),
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 10.dp,
                shadowElevation = 12.dp
            ) {
                Column {
                    // Scroll the header too: large fonts must not consume the action row.
                    Column(
                        modifier = Modifier
                            .weight(1f, fill = false)
                            .verticalScroll(rememberScrollState())
                    ) {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Column(
                                modifier = Modifier.padding(horizontal = 22.dp, vertical = 18.dp)
                            ) {
                                Text(
                                    selectedDate.year.toString() + "년",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    selectedTitle,
                                    style = MaterialTheme.typography.headlineMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }

                        Column(
                            modifier = Modifier.padding(vertical = 12.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                IconButton(
                                    onClick = {
                                        visibleMonth = visibleMonth.minusMonths(1)
                                    }
                                ) {
                                    Icon(
                                        Icons.Filled.KeyboardArrowLeft,
                                        contentDescription = "이전 달"
                                    )
                                }
                                Text(
                                    monthTitle,
                                    modifier = Modifier.weight(1f),
                                    textAlign = TextAlign.Center,
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold
                                )
                                IconButton(
                                    onClick = {
                                        visibleMonth = visibleMonth.plusMonths(1)
                                    }
                                ) {
                                    Icon(
                                        Icons.Filled.KeyboardArrowRight,
                                        contentDescription = "다음 달"
                                    )
                                }
                            }

                            Spacer(Modifier.height(4.dp))

                            // Narrow windows scroll the grid instead of shrinking touch targets.
                            BoxWithConstraints(Modifier.fillMaxWidth()) {
                                val gridWidth = maxOf(maxWidth, minimumGridWidth)
                                val horizontalScroll = rememberScrollState()
                                val viewportModifier = if (gridWidth > maxWidth) {
                                    Modifier.horizontalScroll(horizontalScroll)
                                } else {
                                    Modifier
                                }
                                Column(viewportModifier) {
                                    Column(Modifier.width(gridWidth)) {
                                        Row(Modifier.fillMaxWidth()) {
                                            weekdays.forEachIndexed { index, label ->
                                                val color = when (index) {
                                                    0 -> MaterialTheme.colorScheme.error
                                                    6 -> MaterialTheme.colorScheme.primary
                                                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                                                }
                                                Text(
                                                    label,
                                                    modifier = Modifier.weight(1f),
                                                    textAlign = TextAlign.Center,
                                                    style = MaterialTheme.typography.labelLarge,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = color
                                                )
                                            }
                                        }

                                        Spacer(Modifier.height(6.dp))

                                        cells.chunked(7).forEach { week ->
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                week.forEach { cell ->
                                                    CalendarDay(
                                                        cell = cell,
                                                        selected = cell.date == selectedDate,
                                                        onClick = { date ->
                                                            selectedDate = date
                                                            visibleMonth = YearMonth.from(date)
                                                        },
                                                        modifier = Modifier.weight(1f)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        TextButton(
                            onClick = onDismiss,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("취소")
                        }
                        Button(
                            onClick = { onConfirm(selectedDate) },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("확인")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CalendarDay(
    cell: CalendarDayCell,
    selected: Boolean,
    onClick: (LocalDate) -> Unit,
    modifier: Modifier = Modifier
) {
    val date = cell.date
    val holiday = cell.holidayName != null
    val isSunday = date?.dayOfWeek == DayOfWeek.SUNDAY
    val isSaturday = date?.dayOfWeek == DayOfWeek.SATURDAY

    val foreground = when {
        date == null -> Color.Transparent
        selected -> MaterialTheme.colorScheme.onPrimary
        holiday || isSunday -> MaterialTheme.colorScheme.error
        isSaturday -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurface
    }
    val selectionColor = when {
        holiday || isSunday -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.primary
    }

    val circleSize = maxOf(34f, 24f * LocalDensity.current.fontScale).dp
    val dayModifier = if (date != null) {
        Modifier.selectable(selected = selected, role = Role.Button, onClick = { onClick(date) })
    } else {
        Modifier
    }

    Column(
        modifier = modifier
            .heightIn(min = 58.dp)
            .then(dayModifier)
            .padding(horizontal = 1.dp, vertical = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top
    ) {
        if (date == null) {
            Spacer(Modifier.height(34.dp))
        } else {
            Box(
                modifier = Modifier
                    .size(circleSize)
                    .background(
                        color = if (selected) selectionColor else Color.Transparent,
                        shape = RoundedCornerShape(circleSize / 2)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    date.dayOfMonth.toString(),
                    fontWeight = if (selected || holiday) FontWeight.Bold else FontWeight.Medium,
                    color = foreground
                )
            }

            if (cell.holidayName != null) {
                Text(
                    cell.holidayName,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp),
                    color = MaterialTheme.colorScheme.error,
                    maxLines = 1
                )
            }
        }
    }
}
