package com.tonny.dial.telecom

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Person
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import com.tonny.dial.CallActivity
import com.tonny.dial.R

class CallNotifier(private val context: Context) {

    private val manager = context.getSystemService(NotificationManager::class.java)

    init {
        manager.createNotificationChannels(
            listOf(
                NotificationChannel(CH_INCOMING, "Incoming calls", NotificationManager.IMPORTANCE_HIGH).apply {
                    // Telecom plays the ringtone; the notification stays silent.
                    setSound(null, null)
                    enableVibration(false)
                },
                NotificationChannel(CH_ONGOING, "Ongoing calls", NotificationManager.IMPORTANCE_LOW),
            )
        )
    }

    fun show(call: CallUi, headsUp: Boolean) {
        val content = PendingIntent.getActivity(
            context, 0, CallActivity.intent(context),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val hangup = action(CallActionReceiver.ACTION_HANGUP, 1)
        // Answer opens the call screen directly: Android 12+ blocks receiver-to-activity trampolines.
        val answer = PendingIntent.getActivity(
            context, 2, CallActivity.intent(context).setAction(CallActivity.ACTION_ANSWER),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        // Only alert with a heads-up banner when the call screen isn't already in front.
        val alert = call.isRinging && headsUp
        val builder = Notification.Builder(context, if (alert) CH_INCOMING else CH_ONGOING)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(call.title)
            .setContentIntent(content)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(Notification.CATEGORY_CALL)
            .setVisibility(Notification.VISIBILITY_PUBLIC)

        if (alert) {
            builder.setFullScreenIntent(content, true)
            builder.setContentText("Incoming call")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val person = Person.Builder().setName(call.title).setImportant(true).build()
                builder.setStyle(
                    Notification.CallStyle.forIncomingCall(person, hangup, answer)
                )
            } else {
                builder.addAction(Notification.Action.Builder(null as Icon?, "Decline", hangup).build())
                builder.addAction(
                    Notification.Action.Builder(null as Icon?, "Answer", answer).build()
                )
            }
        } else {
            builder.setContentText(
                when {
                    call.isRinging -> "Incoming call"
                    call.isHolding -> "On hold"
                    call.isDialing -> "Calling…"
                    else -> "Ongoing call"
                }
            )
            if (call.isActive && call.connectTimeMillis > 0) {
                builder.setUsesChronometer(true).setWhen(call.connectTimeMillis)
            }
            builder.addAction(Notification.Action.Builder(null as Icon?, if (call.isRinging) "Decline" else "Hang up", hangup).build())
        }
        manager.notify(ID, builder.build())
    }

    fun cancel() = manager.cancel(ID)

    private fun action(action: String, code: Int): PendingIntent = PendingIntent.getBroadcast(
        context, code,
        Intent(context, CallActionReceiver::class.java).setAction(action),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    companion object {
        private const val CH_INCOMING = "incoming"
        private const val CH_ONGOING = "ongoing"
        private const val ID = 7
    }
}
