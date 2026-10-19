package com.tonny.dial.telecom

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.net.Uri
import android.telecom.TelecomManager
import com.tonny.dial.MainActivity
import com.tonny.dial.R
import com.tonny.dial.data.ContactsRepository

/**
 * Telecom hands missed-call notifications to the default dialer through this broadcast, so we
 * post our own with "Call back" and "Message" buttons. A count of 0 means they were cleared.
 */
class MissedCallReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != TelecomManager.ACTION_SHOW_MISSED_CALLS_NOTIFICATION) return
        val nm = context.getSystemService(NotificationManager::class.java)
        val count = intent.getIntExtra(TelecomManager.EXTRA_NOTIFICATION_COUNT, 0)
        if (count <= 0) { nm.cancel(ID); return }

        nm.createNotificationChannel(NotificationChannel(CHANNEL, "Missed calls", NotificationManager.IMPORTANCE_DEFAULT))
        val number = intent.getStringExtra(TelecomManager.EXTRA_NOTIFICATION_PHONE_NUMBER).orEmpty()
        val name = if (number.isNotBlank()) runCatching { ContactsRepository.lookup(context, number).name }.getOrNull() else null
        val who = name ?: number.ifBlank { "Unknown" }

        val open = PendingIntent.getActivity(
            context, 30, Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val builder = Notification.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(if (count == 1) who else "$count missed calls")
            .setContentText(if (count == 1) "Missed call" else "Latest from $who")
            .setContentIntent(open)
            .setAutoCancel(true)
            .setCategory(Notification.CATEGORY_MISSED_CALL)
        @Suppress("DEPRECATION")
        intent.getParcelableExtra<PendingIntent>(EXTRA_CLEAR_INTENT)?.let(builder::setDeleteIntent)

        if (number.isNotBlank()) {
            @Suppress("DEPRECATION")
            val callBack = intent.getParcelableExtra<PendingIntent>(EXTRA_CALL_BACK_INTENT)
                ?: PendingIntent.getActivity(
                    context, 31, Intent(Intent.ACTION_CALL, Uri.fromParts("tel", number, null)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )
            val message = PendingIntent.getActivity(
                context, 32, Intent(Intent.ACTION_SENDTO, Uri.fromParts("smsto", number, null)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            builder.addAction(Notification.Action.Builder(null as Icon?, "Call back", callBack).build())
            builder.addAction(Notification.Action.Builder(null as Icon?, "Message", message).build())
        }
        nm.notify(ID, builder.build())
    }

    companion object {
        private const val CHANNEL = "missed"
        // Not in the public SDK, but Telecom always sets them on this broadcast.
        private const val EXTRA_CLEAR_INTENT = "android.telecom.extra.CLEAR_MISSED_CALLS_INTENT"
        private const val EXTRA_CALL_BACK_INTENT = "android.telecom.extra.CALL_BACK_INTENT"
        const val ID = 9

        fun cancel(context: Context) = context.getSystemService(NotificationManager::class.java).cancel(ID)
    }
}
