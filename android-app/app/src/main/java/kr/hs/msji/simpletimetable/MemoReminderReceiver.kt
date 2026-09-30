package kr.hs.msji.simpletimetable

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class MemoReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val memoId = intent.getLongExtra("memo_id", 0L)
        val title = intent.getStringExtra("title").orEmpty().ifBlank { "메모 알림" }
        val text = intent.getStringExtra("text").orEmpty()
        NotificationHelper.showMemoReminder(context, memoId, title, text)
    }
}
