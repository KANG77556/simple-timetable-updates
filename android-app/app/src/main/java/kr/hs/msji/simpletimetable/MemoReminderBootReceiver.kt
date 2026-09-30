package kr.hs.msji.simpletimetable

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class MemoReminderBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val now = System.currentTimeMillis()
        LocalStore(context).loadMemos()
            .filter { it.deletedAt == 0L && it.reminderAt > now }
            .forEach { memo ->
                MemoReminderScheduler.schedule(
                    context,
                    memo.id,
                    memo.reminderAt,
                    memo.title.ifBlank { "메모 알림" },
                    memo.text
                )
            }
    }
}
