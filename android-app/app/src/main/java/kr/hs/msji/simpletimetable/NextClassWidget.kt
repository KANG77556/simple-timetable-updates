package kr.hs.msji.simpletimetable

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import org.json.JSONArray
import java.time.Duration
import java.time.LocalTime

class NextClassWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        ids.forEach { updateOne(context, manager, it) }
    }

    companion object {
        fun updateAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val component = ComponentName(context, NextClassWidget::class.java)
            manager.getAppWidgetIds(component).forEach { updateOne(context, manager, it) }
        }

        private fun updateOne(context: Context, manager: AppWidgetManager, id: Int) {
            val rows = decode(LocalStore(context).latestTimetableJson).sortedBy { it.period }
            val now = LocalTime.now()
            val current = rows.firstOrNull {
                val s = parseTime(it.startTime); val e = parseTime(it.endTime)
                s != null && e != null && !now.isBefore(s) && now.isBefore(e)
            }
            val next = rows.firstOrNull {
                val s = parseTime(it.startTime)
                s != null && now.isBefore(s)
            }
            val target = current ?: next
            val views = RemoteViews(context.packageName, R.layout.next_class_widget)
            views.setTextViewTextSize(R.id.next_widget_label, android.util.TypedValue.COMPLEX_UNIT_SP, WidgetDisplaySettings.scaled(context, 12f))
            views.setTextViewTextSize(R.id.next_widget_subject, android.util.TypedValue.COMPLEX_UNIT_SP, WidgetDisplaySettings.scaled(context, 20f))
            views.setTextViewTextSize(R.id.next_widget_detail, android.util.TypedValue.COMPLEX_UNIT_SP, WidgetDisplaySettings.scaled(context, 12f))
            views.setTextViewTextSize(R.id.next_widget_time, android.util.TypedValue.COMPLEX_UNIT_SP, WidgetDisplaySettings.scaled(context, 13f))

            if (target == null) {
                views.setTextViewText(R.id.next_widget_label, "오늘")
                views.setTextViewText(R.id.next_widget_subject, if (rows.isEmpty()) "수업 없음" else "오늘 수업 종료")
                views.setTextViewText(R.id.next_widget_detail, "")
                views.setTextViewText(R.id.next_widget_time, "")
            } else {
                views.setTextViewText(R.id.next_widget_label, if (current != null) "현재 수업" else "다음 수업")
                views.setTextViewText(R.id.next_widget_subject, target.subject.ifBlank { "과목 미지정" })
                views.setTextViewText(
                    R.id.next_widget_detail,
                    listOf("${target.period}교시", target.classCode, target.room).filter { it.isNotBlank() }.joinToString(" · ")
                )
                val time = if (current != null) {
                    val end = parseTime(target.endTime)
                    val remain = end?.let { Duration.between(now, it).toMinutes().coerceAtLeast(0) } ?: 0
                    "${formatDurationMinutes(remain)} 남음"
                } else {
                    val start = parseTime(target.startTime)
                    val remain = start?.let { Duration.between(now, it).toMinutes().coerceAtLeast(0) } ?: 0
                    "${formatDurationMinutes(remain)} 후 시작"
                }
                views.setTextViewText(R.id.next_widget_time, time)
            }

            val open = PendingIntent.getActivity(
                context, id, Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.next_widget_root, open)
            manager.updateAppWidget(id, views)
        }

        private fun formatDurationMinutes(totalMinutes: Long): String {
            val minutes = totalMinutes.coerceAtLeast(0)
            if (minutes < 60) return "${minutes}분"
            val hours = minutes / 60
            val remain = minutes % 60
            return if (remain == 0L) "${hours}시간" else "${hours}시간 ${remain}분"
        }

        private fun parseTime(value: String): LocalTime? = runCatching { LocalTime.parse(value.take(5)) }.getOrNull()

        private fun decode(raw: String): List<TimetableItem> = runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                TimetableItem(
                    date = o.optString("date"), grade = o.optInt("grade"),
                    classCode = o.optString("classCode"), period = o.optInt("period"),
                    subject = o.optString("subject"), teacher = o.optString("teacher"),
                    room = o.optString("room"), startTime = o.optString("startTime"),
                    endTime = o.optString("endTime")
                )
            }
        }.getOrDefault(emptyList())
    }
}
