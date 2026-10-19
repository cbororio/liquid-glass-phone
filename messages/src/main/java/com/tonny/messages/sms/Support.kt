package com.tonny.messages.sms

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.RemoteInput
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.os.IBinder
import android.text.SpannableString
import android.text.Spanned
import android.text.TextUtils
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import com.tonny.messages.data.Mpesa
import com.tonny.messages.data.MpesaFlow
import android.text.style.StyleSpan
import android.telephony.SmsManager
import com.tonny.messages.MainActivity
import com.tonny.messages.R
import com.tonny.messages.data.SmsRepository
import com.tonny.messages.data.isReplyable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** A tick that goes up whenever stored messages change, so open screens reload. */
object MessagesEvents {
    private val _version = MutableStateFlow(0)
    val version: StateFlow<Int> = _version
    fun bump() {
        _version.value = _version.value + 1
    }
}

/** Handles "respond with message" from the phone app (RESPOND_VIA_MESSAGE). */
class HeadlessSmsSendService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Telecom puts the number in the data ("smsto:0712…"), not in an extra.
        val recipients = intent?.getStringExtra(Intent.EXTRA_PHONE_NUMBER) ?: intent?.data?.schemeSpecificPart
        val body = intent?.getStringExtra(Intent.EXTRA_TEXT)
        if (!recipients.isNullOrBlank() && !body.isNullOrBlank()) {
            val app = applicationContext
            Thread {
                recipients.split(';', ',').map { it.trim() }.filter { it.isNotEmpty() }.forEach { SmsSender.send(app, it, body) }
                MessagesEvents.bump()
            }.start()
        }
        stopSelf(startId)
        return START_NOT_STICKY
    }
}

/** A simple notification for an incoming message. */
object MessageNotifier {
    private const val CHANNEL = "messages"

    fun show(context: Context, address: String, body: String, threadId: Long) {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, "Messages", NotificationManager.IMPORTANCE_HIGH),
        )
        val title = SmsRepository.lookup(context, address).name ?: address
        val open = PendingIntent.getActivity(
            context, threadId.toInt(),
            MainActivity.threadIntent(context, threadId, address),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val id = threadId.toInt().coerceAtLeast(1)
        val code = OtpDetector.find(body)
        val txn = if (Mpesa.isMpesa(address)) Mpesa.parse(body) else null
        val builder = Notification.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentIntent(open)
            .setAutoCancel(true)
            .setCategory(Notification.CATEGORY_MESSAGE)
        if (txn != null) {
            // "Received KSH 400" in green (red for money out), then who it was from or to.
            val color = when (txn.flow) {
                MpesaFlow.In -> 0xFF34C759.toInt()
                MpesaFlow.Out -> 0xFFFF3B30.toInt()
                MpesaFlow.Failed -> 0xFFFF9500.toInt()
            }
            val headline = SpannableString(txn.headline).apply {
                setSpan(StyleSpan(Typeface.BOLD), 0, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                setSpan(ForegroundColorSpan(color), 0, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            val summary = if (txn.detail != null) TextUtils.concat(headline, " ", txn.detail) else headline
            builder
                .setContentText(summary)
                .setStyle(Notification.BigTextStyle().bigText(TextUtils.concat(summary, "\n", body)))
            txn.balance?.let { builder.setSubText("Balance $it") }
        } else if (code == null) {
            builder.setContentText(body).setStyle(Notification.BigTextStyle().bigText(body))
        } else {
            // Like Truecaller: the code up front in large bold type, with a one-tap copy button.
            val headline = SpannableString(code).apply {
                setSpan(StyleSpan(Typeface.BOLD), 0, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                setSpan(RelativeSizeSpan(1.6f), 0, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            val copy = PendingIntent.getBroadcast(
                context, id,
                Intent(context, CopyCodeReceiver::class.java)
                    .putExtra(CopyCodeReceiver.EXTRA_CODE, code)
                    .putExtra(CopyCodeReceiver.EXTRA_NOTIFICATION, id),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            builder
                .setContentText(headline)
                .setSubText("Code")
                .setStyle(Notification.BigTextStyle().bigText(TextUtils.concat(headline, "\n", body)))
                .addAction(Notification.Action.Builder(null, "Copy code $code", copy).build())
        }
        // Reply right from the notification (people only; short codes can't receive texts), and Mark as read.
        if (isReplyable(address)) {
            val reply = PendingIntent.getBroadcast(
                context, id * 4 + 1,
                NotificationActionReceiver.intent(context, NotificationActionReceiver.ACTION_REPLY, address, threadId, id),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
            )
            val input = RemoteInput.Builder(NotificationActionReceiver.KEY_REPLY).setLabel("Reply").build()
            builder.addAction(Notification.Action.Builder(null, "Reply", reply).addRemoteInput(input).setAllowGeneratedReplies(true).build())
        }
        val read = PendingIntent.getBroadcast(
            context, id * 4 + 2,
            NotificationActionReceiver.intent(context, NotificationActionReceiver.ACTION_READ, address, threadId, id),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        builder.addAction(Notification.Action.Builder(null, "Mark as read", read).build())
        nm.notify(id, builder.build())
    }

    fun clear(context: Context, threadId: Long) {
        context.getSystemService(NotificationManager::class.java).cancel(threadId.toInt().coerceAtLeast(1))
    }
}

/** SmsManager accessor kept here so callers don't repeat the version check. */
internal fun Context.smsManager(): SmsManager = getSystemService(SmsManager::class.java)
