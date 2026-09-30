package kr.hs.msji.simpletimetable

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

class PinnedMemoWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        ids.forEach { updateOne(context, manager, it) }
    }

    companion object {
        fun updateAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val component = ComponentName(context, PinnedMemoWidget::class.java)
            manager.getAppWidgetIds(component).forEach { updateOne(context, manager, it) }
        }

        private fun updateOne(context: Context, manager: AppWidgetManager, id: Int) {
            val memo = LocalStore(context).loadMemos()
                .filter { it.pinned && it.deletedAt == 0L && !it.archived }
                .maxByOrNull { it.updatedAt }

            val views = RemoteViews(context.packageName, R.layout.pinned_memo_widget)
            if (memo == null) {
                views.setTextViewText(R.id.memo_widget_title, "고정된 메모가 없습니다.")
                views.setTextViewText(R.id.memo_widget_text, "앱에서 자주 보는 메모를 고정해 보세요.")
                views.setTextViewText(R.id.memo_widget_meta, "")
            } else {
                views.setTextViewText(R.id.memo_widget_title, memo.title.ifBlank { memo.category })
                views.setTextViewText(R.id.memo_widget_text, memo.text)
                val time = Instant.ofEpochMilli(memo.updatedAt)
                    .atZone(ZoneId.of("Asia/Seoul"))
                    .format(DateTimeFormatter.ofPattern("M월 d일 HH:mm", Locale.KOREA))
                views.setTextViewText(R.id.memo_widget_meta, "${memo.category} · ${time}")
            }

            val open = PendingIntent.getActivity(
                context, id, Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.memo_widget_root, open)
            manager.updateAppWidget(id, views)
        }
    }
}
