package com.tonny.messages.sms

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.BlockedNumberContract
import android.provider.Telephony
import android.telephony.SmsMessage
import com.tonny.messages.data.Prefs
import com.tonny.messages.data.SpamFilter
import com.tonny.messages.data.SmsRepository

/** Result of SmsManager's send attempt; flips the stored message to SENT or FAILED. */
class SendStatusReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val uriString = intent.getStringExtra(EXTRA_URI) ?: return
        val uri = Uri.parse(uriString)
        val last = intent.getBooleanExtra(EXTRA_LAST, true)
        if (resultCode == Activity.RESULT_OK) {
            if (last) SmsSender.markSent(context, uri)
        } else {
            SmsSender.markFailed(context, uri)
        }
        MessagesEvents.bump()
    }

    companion object {
        const val ACTION_SENT = "com.tonny.messages.SMS_SENT"
        const val EXTRA_URI = "uri"
        const val EXTRA_LAST = "last"
    }
}

/**
 * Delivered to the default SMS app for every incoming SMS. The app is then responsible for
 * writing the message into the provider.
 */
class SmsDeliverReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_DELIVER_ACTION) return
        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent) ?: return
        if (messages.isEmpty()) return
        val address = messages[0].displayOriginatingAddress.orEmpty()
        val body = messages.joinToString("") { it.displayMessageBody.orEmpty() }
        val timestamp = messages[0].timestampMillis.takeIf { it > 0 } ?: System.currentTimeMillis()

        // Spam and blocked senders are still saved (nothing is lost) but arrive silently and already read.
        Prefs.init(context)
        // Betting/promo blasts from senders you haven't saved go to Spam automatically (Settings can turn this off).
        if (SmsRepository.lookup(context, address).name == null) Prefs.setAutoSpam(address, SpamFilter.looksLikePromo(body))
        val silent = Prefs.isSpam(address) || isBlocked(context, address)

        val values = ContentValues().apply {
            put(Telephony.Sms.ADDRESS, address)
            put(Telephony.Sms.BODY, body)
            put(Telephony.Sms.DATE, timestamp)
            put(Telephony.Sms.READ, if (silent) 1 else 0)
            put(Telephony.Sms.SEEN, if (silent) 1 else 0)
            put(Telephony.Sms.TYPE, Telephony.Sms.MESSAGE_TYPE_INBOX)
        }
        runCatching { context.contentResolver.insert(Telephony.Sms.Inbox.CONTENT_URI, values) }
        SmsRepository.clearCaches()
        if (!silent) {
            val threadId = runCatching { SmsRepository.threadId(context, address) }.getOrDefault(-1L)
            MessageNotifier.show(context, address, body, threadId)
        }
        MessagesEvents.bump()
    }
}

/** Whether the sender is on the system block list (shared with the Phone app). */
private fun isBlocked(context: Context, address: String): Boolean =
    runCatching { BlockedNumberContract.isBlocked(context, address) }.getOrDefault(false)

/** Delivery report for a sent SMS: marks it Delivered (or failed) in the message store. */
class DeliveryReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val uri = intent.getStringExtra(SendStatusReceiver.EXTRA_URI)?.let(Uri::parse) ?: return
        val pdu = intent.getByteArrayExtra("pdu")
        val format = intent.getStringExtra("format")
        // GSM TP-Status: below 0x20 delivered, below 0x40 still trying, otherwise failed.
        val status = pdu?.let { runCatching { SmsMessage.createFromPdu(it, format).status }.getOrNull() }
            ?: if (resultCode == Activity.RESULT_OK) 0 else Telephony.Sms.STATUS_FAILED
        val value = when {
            status >= Telephony.Sms.STATUS_FAILED -> Telephony.Sms.STATUS_FAILED
            status >= Telephony.Sms.STATUS_PENDING -> Telephony.Sms.STATUS_PENDING
            else -> Telephony.Sms.STATUS_COMPLETE
        }
        runCatching { context.contentResolver.update(uri, ContentValues().apply { put(Telephony.Sms.STATUS, value) }, null, null) }
        MessagesEvents.bump()
    }
}
