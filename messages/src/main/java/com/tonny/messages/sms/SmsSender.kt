package com.tonny.messages.sms

import android.app.PendingIntent
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Telephony
import android.telephony.SmsManager
import android.telephony.SubscriptionManager
import com.tonny.messages.data.Prefs
import com.tonny.messages.data.SmsRepository
import com.tonny.messages.mms.MmsDownloader

/** Sends an SMS and keeps the provider in sync, the way a default SMS app must. */
object SmsSender {

    /** Sends from [subId], or the SIM last used for this person, or the phone's default SMS SIM. */
    fun send(context: Context, address: String, body: String, subId: Int? = null): Long {
        val app = context.applicationContext
        Prefs.init(app)
        val sub = subId ?: Prefs.simFor(address) ?: SubscriptionManager.getDefaultSmsSubscriptionId()
        val threadId = SmsRepository.threadId(app, address)
        // Record it immediately as outbox so the UI shows it right away.
        val values = ContentValues().apply {
            put(Telephony.Sms.ADDRESS, address)
            put(Telephony.Sms.BODY, body)
            put(Telephony.Sms.DATE, System.currentTimeMillis())
            put(Telephony.Sms.READ, 1)
            put(Telephony.Sms.SEEN, 1)
            put(Telephony.Sms.TYPE, Telephony.Sms.MESSAGE_TYPE_OUTBOX)
            put(Telephony.Sms.THREAD_ID, threadId)
            put(Telephony.Sms.STATUS, Telephony.Sms.STATUS_PENDING)
            if (SubscriptionManager.isValidSubscriptionId(sub)) put(Telephony.Sms.SUBSCRIPTION_ID, sub)
        }
        val uri = app.contentResolver.insert(Telephony.Sms.CONTENT_URI, values)

        val sms = MmsDownloader.smsManager(app, sub)
        val parts = sms.divideMessage(body)
        val sentIntents = ArrayList<PendingIntent>()
        val deliveryIntents = ArrayList<PendingIntent?>()
        parts.forEachIndexed { i, _ ->
            val intent = Intent(app, SendStatusReceiver::class.java)
                .setAction("${SendStatusReceiver.ACTION_SENT}.$i")
                .putExtra(SendStatusReceiver.EXTRA_URI, uri?.toString())
                .putExtra(SendStatusReceiver.EXTRA_LAST, i == parts.lastIndex)
            // A distinct request code per message part; sharing one let a second send overwrite the first's status.
            val requestCode = ((uri?.lastPathSegment?.toLongOrNull() ?: System.nanoTime()) * 16 + i).toInt()
            sentIntents += PendingIntent.getBroadcast(
                app, requestCode, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            // One delivery report, on the last part. Mutable so the system can attach the report.
            deliveryIntents += if (i == parts.lastIndex) PendingIntent.getBroadcast(
                app, requestCode,
                Intent(app, DeliveryReceiver::class.java).setAction("com.tonny.messages.DELIVERED")
                    .putExtra(SendStatusReceiver.EXTRA_URI, uri?.toString()),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
            ) else null
        }
        runCatching { sms.sendMultipartTextMessage(address, null, parts, sentIntents, deliveryIntents) }
            .onFailure { markFailed(app, uri) }
        return threadId
    }

    internal fun markSent(context: Context, uri: Uri) {
        val values = ContentValues().apply {
            put(Telephony.Sms.TYPE, Telephony.Sms.MESSAGE_TYPE_SENT)
            put(Telephony.Sms.DATE, System.currentTimeMillis())
        }
        context.contentResolver.update(uri, values, null, null)
    }

    internal fun markFailed(context: Context, uri: Uri?) {
        uri ?: return
        val values = ContentValues().apply { put(Telephony.Sms.TYPE, Telephony.Sms.MESSAGE_TYPE_FAILED) }
        context.contentResolver.update(uri, values, null, null)
    }
}
