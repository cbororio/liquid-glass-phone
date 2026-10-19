package com.tonny.messages.sms

import android.app.NotificationManager
import android.app.RemoteInput
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.tonny.messages.data.SmsRepository

/** "Reply" and "Mark as read" on a message notification. */
class NotificationActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val address = intent.getStringExtra(EXTRA_ADDRESS) ?: return
        val threadId = intent.getLongExtra(EXTRA_THREAD, -1L)
        val notificationId = intent.getIntExtra(EXTRA_NOTIFICATION, 0)
        val reply = RemoteInput.getResultsFromIntent(intent)?.getCharSequence(KEY_REPLY)?.toString()?.trim()
        val app = context.applicationContext
        val pending = goAsync()
        Thread {
            try {
                if (intent.action == ACTION_REPLY && !reply.isNullOrEmpty()) SmsSender.send(app, address, reply)
                if (threadId > 0) SmsRepository.markRead(app, threadId)
            } finally {
                app.getSystemService(NotificationManager::class.java).cancel(notificationId)
                MessagesEvents.bump()
                pending.finish()
            }
        }.start()
    }

    companion object {
        const val ACTION_REPLY = "com.tonny.messages.REPLY"
        const val ACTION_READ = "com.tonny.messages.MARK_READ"
        const val KEY_REPLY = "reply"
        private const val EXTRA_ADDRESS = "address"
        private const val EXTRA_THREAD = "thread"
        private const val EXTRA_NOTIFICATION = "notification"

        fun intent(context: Context, action: String, address: String, threadId: Long, notificationId: Int): Intent =
            Intent(context, NotificationActionReceiver::class.java).setAction(action)
                .putExtra(EXTRA_ADDRESS, address).putExtra(EXTRA_THREAD, threadId).putExtra(EXTRA_NOTIFICATION, notificationId)
    }
}
