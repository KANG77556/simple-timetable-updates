package kr.hs.msji.simpletimetable

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import org.json.JSONArray
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime

class TimetableWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
        appWidgetIds.forEach { updateOne(context, manager, it) }
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: android.os.Bundle
    ) {
        updateOne(context, appWidgetManager, appWidgetId)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_REFRESH_WIDGET) updateAll(context)
    }

    companion object {
        const val ACTION_REFRESH_WIDGET = "kr.hs.msji.simpletimetable.action.REFRESH_WIDGET"

        fun updateAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val component = ComponentName(context, TimetableWidget::class.java)
            manager.getAppWidgetIds(component).forEach { updateOne(context, manager, it) }
        }

        private fun updateOne(context: Context, manager: AppWidgetManager, appWidgetId: Int) {
            val store = LocalStore(context)
            val rows = decode(store.latestTimetableJson).sortedBy { it.period }
            val now = LocalTime.now()
            val today = LocalDate.now().toString()
            val current = rows.firstOrNull { row ->
                val start = parseTime(row.startTime)
                val end = parseTime(row.endTime)
                start != null && end != null && !now.isBefore(start) && now.isBefore(end)
            }
            val next = rows.firstOrNull { row ->
                val start = parseTime(row.startTime)
                start != null && now.isBefore(start)
            }

            val options = manager.getAppWidgetOptions(appWidgetId)
            val minHeight = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 180)
            val minWidth = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 250)
            val compact = minHeight < 150 || minWidth < 220
            val roomy = minHeight >= 240
            val views = RemoteViews(context.packageName, R.layout.timetable_widget)

            val status = when {
                current != null -> listOf("${current.period}교시", current.subject, current.classCode)
                    .filter { it.isNotBlank() }.joinToString(" · ")
                next != null -> "다음 " + listOf("${next.period}교시", next.subject, next.classCode)
                    .filter { it.isNotBlank() }.joinToString(" · ")
                rows.isEmpty() -> "오늘 수업 없음"
                else -> "오늘 수업 종료"
            }
            val detail = when {
                current != null -> {
                    val end = parseTime(current.endTime)
                    val remain = end?.let { Duration.between(now, it).toMinutes().coerceAtLeast(0) } ?: 0
                    val room = current.room.takeIf { it.isNotBlank() } ?: "교실 미지정"
                    "${remain}분 남음 · $room"
                }
                next != null -> {
                    val start = parseTime(next.startTime)
                    val remain = start?.let { Duration.between(now, it).toMinutes().coerceAtLeast(0) } ?: 0
                    val room = next.room.takeIf { it.isNotBlank() } ?: "교실 미지정"
                    "${remain}분 후 시작 · $room"
                }
                else -> "SCERP.cloud · ${LocalDate.now()}"
            }

            val visibleRows = when {
                compact -> rows.take(2)
                roomy -> rows
                else -> rows.take(5)
            }
            val timetableText = visibleRows.joinToString("\n") { row ->
                val active = current?.period == row.period
                val marker = if (active) "▶ " else ""
                val time = row.startTime.take(5)
                val room = row.room.takeIf { it.isNotBlank() }?.let { " · $it" }.orEmpty()
                "$marker${row.period}교시  ${row.subject}${row.classCode.takeIf { it.isNotBlank() }?.let { " · $it" }.orEmpty()}${if (time.isNotBlank()) "  $time" else ""}$room"
            }

            val pendingTodos = store.loadTodos().filter { !it.done }
            val dueToday = pendingTodos.count { it.dueDate == today }
            val todayEvents = store.loadCalendar().filter { it.date == today }
            val summary = buildList {
                if (pendingTodos.isNotEmpty()) add("할 일 ${pendingTodos.size}개" + if (dueToday > 0) " · 오늘 마감 $dueToday" else "")
                if (todayEvents.isNotEmpty()) add("일정 ${todayEvents.size}개")
            }.joinToString("   ")

            views.setTextViewText(R.id.widget_status, status)
            views.setTextViewText(R.id.widget_detail, detail)
            views.setTextViewText(R.id.widget_rows, if (timetableText.isBlank()) "등록된 수업이 없습니다." else timetableText)
            views.setTextViewText(R.id.widget_summary, if (summary.isBlank()) "할 일·일정 없음" else summary)
            views.setViewVisibility(R.id.widget_rows, if (compact) View.GONE else View.VISIBLE)
            views.setViewVisibility(R.id.widget_summary, if (minHeight >= 180) View.VISIBLE else View.GONE)

            val openIntent = Intent(context, MainActivity::class.java)
            val openPending = PendingIntent.getActivity(
                context, appWidgetId, openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_root, openPending)
            views.setOnClickPendingIntent(R.id.widget_open, openPending)

            val refreshIntent = Intent(context, TimetableWidget::class.java).apply { action = ACTION_REFRESH_WIDGET }
            val refreshPending = PendingIntent.getBroadcast(
                context, appWidgetId, refreshIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_refresh, refreshPending)

            manager.updateAppWidget(appWidgetId, views)
        }

        private fun parseTime(value: String): LocalTime? =
            runCatching { LocalTime.parse(value.take(5)) }.getOrNull()

        private fun decode(raw: String): List<TimetableItem> = runCatching {
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
