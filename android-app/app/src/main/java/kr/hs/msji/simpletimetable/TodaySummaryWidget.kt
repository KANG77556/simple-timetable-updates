package kr.hs.msji.simpletimetable

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import java.time.LocalDate

class TodaySummaryWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        ids.forEach { updateOne(context, manager, it) }
    }

    companion object {
        fun updateAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val component = ComponentName(context, TodaySummaryWidget::class.java)
            manager.getAppWidgetIds(component).forEach { updateOne(context, manager, it) }
        }

        private fun updateOne(context: Context, manager: AppWidgetManager, id: Int) {
            val store = LocalStore(context)
            val today = LocalDate.now().toString()
            val todoCount = store.loadTodos().count { !it.done }
            val eventCount = store.loadCalendar().count { it.date == today }
            val memoCount = store.loadMemos().count { it.pinned && it.deletedAt == 0L && !it.archived }

            val views = RemoteViews(context.packageName, R.layout.today_summary_widget)
            views.setTextViewTextSize(R.id.summary_heading, android.util.TypedValue.COMPLEX_UNIT_SP, WidgetDisplaySettings.scaled(context, 16f))
            views.setTextViewTextSize(R.id.summary_todo_label, android.util.TypedValue.COMPLEX_UNIT_SP, WidgetDisplaySettings.scaled(context, 11f))
            views.setTextViewTextSize(R.id.summary_event_label, android.util.TypedValue.COMPLEX_UNIT_SP, WidgetDisplaySettings.scaled(context, 11f))
            views.setTextViewTextSize(R.id.summary_memo_label, android.util.TypedValue.COMPLEX_UNIT_SP, WidgetDisplaySettings.scaled(context, 11f))
            views.setTextViewTextSize(R.id.summary_todo_count, android.util.TypedValue.COMPLEX_UNIT_SP, WidgetDisplaySettings.scaled(context, 26f))
            views.setTextViewTextSize(R.id.summary_event_count, android.util.TypedValue.COMPLEX_UNIT_SP, WidgetDisplaySettings.scaled(context, 26f))
            views.setTextViewTextSize(R.id.summary_memo_count, android.util.TypedValue.COMPLEX_UNIT_SP, WidgetDisplaySettings.scaled(context, 26f))
            views.setTextViewText(R.id.summary_todo_count, todoCount.toString())
            views.setTextViewText(R.id.summary_event_count, eventCount.toString())
            views.setTextViewText(R.id.summary_memo_count, memoCount.toString())

            val open = PendingIntent.getActivity(
                context, id, Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.summary_widget_root, open)
            manager.updateAppWidget(id, views)
        }
    }
}
