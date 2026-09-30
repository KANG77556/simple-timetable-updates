package kr.hs.msji.simpletimetable

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent

object MemoReminderScheduler {
    fun schedule(context: Context, memoId: Long, triggerAt: Long, title: String, text: String) {
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        val intent = Intent(context, MemoReminderReceiver::class.java).apply {
            putExtra("memo_id", memoId)
            putExtra("title", title)
            putExtra("text", text)
        }
        val pending = PendingIntent.getBroadcast(
            context,
            reminderRequestCode(memoId),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending)
    }

    fun cancel(context: Context, memoId: Long) {
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        val intent = Intent(context, MemoReminderReceiver::class.java)
        val pending = PendingIntent.getBroadcast(
            context,
            reminderRequestCode(memoId),
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        if (pending != null) {
            alarmManager.cancel(pending)
            pending.cancel()
        }
    }

    private fun reminderRequestCode(id: Long): Int =
        (id xor (id ushr 32)).toInt() and 0x7fffffff
}
