package com.tonny.messages.mms

import android.app.Activity
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Telephony
import android.provider.Telephony.Mms
import android.telephony.SmsManager
import android.telephony.SubscriptionManager
import android.util.Log
import androidx.core.content.FileProvider
import com.tonny.messages.R
import com.tonny.messages.data.Prefs
import com.tonny.messages.data.SmsRepository
import com.tonny.messages.sms.MessageNotifier
import com.tonny.messages.sms.MessagesEvents
import java.io.File

private const val TAG = "Mms"

/**
 * An incoming MMS first arrives as a WAP push saying a message is waiting on the carrier's
 * server. We then ask the system to download it (over mobile data) and store it ourselves.
 */
class MmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.WAP_PUSH_DELIVER_ACTION) return
        val data = intent.getByteArrayExtra("data") ?: return
        val pdu = Pdu.parse(data) ?: return
        if (pdu.messageType != Pdu.NOTIFICATION_IND) return
        val location = pdu.contentLocation ?: return
        val subId = intent.getIntExtra("subscription", SubscriptionManager.getDefaultSmsSubscriptionId())
        MmsDownloader.download(context, location, pdu.from.orEmpty(), subId)
    }
}

object MmsDownloader {
    internal const val AUTHORITY = "com.tonny.messages.mmsfiles"
    internal const val EXTRA_PATH = "path"
    internal const val EXTRA_FROM = "from"
    internal const val EXTRA_SUB = "sub"

    fun download(context: Context, location: String, from: String, subId: Int) {
        val dir = File(context.cacheDir, "mms").apply { mkdirs() }
        val file = File(dir, "dl_${System.nanoTime()}.pdu")
        val uri = FileProvider.getUriForFile(context, AUTHORITY, file)
        // Mutable: the system adds the download result to this intent.
        val done = PendingIntent.getBroadcast(
            context, file.name.hashCode(),
            Intent(context, MmsDownloadedReceiver::class.java)
                .putExtra(EXTRA_PATH, file.absolutePath).putExtra(EXTRA_FROM, from).putExtra(EXTRA_SUB, subId),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
        )
        runCatching { smsManager(context, subId).downloadMultimediaMessage(context, location, uri, null, done) }
            .onFailure { Log.w(TAG, "download request failed", it); MmsStore.notifyFailed(context, from) }
    }

    @Suppress("DEPRECATION")
    internal fun smsManager(context: Context, subId: Int): SmsManager {
        val valid = SubscriptionManager.isValidSubscriptionId(subId)
        return when {
            !valid -> context.getSystemService(SmsManager::class.java)
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> context.getSystemService(SmsManager::class.java).createForSubscriptionId(subId)
            else -> SmsManager.getSmsManagerForSubscriptionId(subId)
        }
    }
}

class MmsDownloadedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val path = intent.getStringExtra(MmsDownloader.EXTRA_PATH) ?: return
        val from = intent.getStringExtra(MmsDownloader.EXTRA_FROM).orEmpty()
        val subId = intent.getIntExtra(MmsDownloader.EXTRA_SUB, -1)
        val ok = resultCode == Activity.RESULT_OK
        val pending = goAsync()
        Thread {
            val file = File(path)
            try {
                val pdu = if (ok && file.exists()) Pdu.parse(file.readBytes()) else null
                if (pdu == null || pdu.messageType != Pdu.RETRIEVE_CONF) {
                    Log.w(TAG, "download failed ok=$ok type=${pdu?.messageType}")
                    MmsStore.notifyFailed(context, from)
                } else {
                    MmsStore.saveAndNotify(context, pdu, subId)
                }
            } catch (t: Throwable) {
                Log.w(TAG, "store failed", t)
            } finally {
                file.delete()
                MessagesEvents.bump()
                pending.finish()
            }
        }.start()
    }
}

/** Writes a downloaded MMS into the system message store, the same way a default SMS app must. */
object MmsStore {

    fun saveAndNotify(context: Context, pdu: MmsPdu, subId: Int) {
        Prefs.init(context)
        val from = pdu.from.orEmpty()
        val threadId = save(context, pdu, subId) ?: return
        val text = pdu.parts.filter { it.isText }.joinToString("\n") { it.text() }.trim()
        val preview = text.ifBlank { if (pdu.parts.any { it.isImage }) "Photo" else pdu.subject ?: "Multimedia message" }
        if (!Prefs.isSpam(from)) MessageNotifier.show(context, from, preview, threadId)
    }

    private fun save(context: Context, pdu: MmsPdu, subId: Int): Long? {
        val resolver = context.contentResolver
        val from = pdu.from.orEmpty()
        val own = ownNumbers(context).map(::key).toSet()
        val others = (listOf(from) + pdu.to + pdu.cc)
            .filter { it.isNotBlank() && key(it) !in own }
            .distinctBy(::key)
        val recipients = others.ifEmpty { listOf(from) }.toSet()
        val threadId = Telephony.Threads.getOrCreateThreadId(context, recipients)

        val now = System.currentTimeMillis() / 1000
        val values = ContentValues().apply {
            put(Mms.THREAD_ID, threadId)
            put(Mms.DATE, now)
            put(Mms.DATE_SENT, if (pdu.date > 0) pdu.date else now)
            put(Mms.MESSAGE_BOX, Mms.MESSAGE_BOX_INBOX)
            put(Mms.READ, 0)
            put(Mms.SEEN, 0)
            put(Mms.MESSAGE_TYPE, Pdu.RETRIEVE_CONF)
            put(Mms.MMS_VERSION, 0x12)
            put(Mms.CONTENT_TYPE, pdu.contentType ?: "application/vnd.wap.multipart.related")
            pdu.subject?.let { put(Mms.SUBJECT, it); put(Mms.SUBJECT_CHARSET, 106) }
            pdu.messageId?.let { put(Mms.MESSAGE_ID, it) }
            pdu.transactionId?.let { put(Mms.TRANSACTION_ID, it) }
            put(Mms.MESSAGE_SIZE, pdu.parts.sumOf { it.data.size })
            if (SubscriptionManager.isValidSubscriptionId(subId)) put(Mms.SUBSCRIPTION_ID, subId)
        }
        val uri = resolver.insert(Mms.Inbox.CONTENT_URI, values) ?: return null
        val id = ContentUris.parseId(uri)
        writeParts(context, id, pdu.parts)
        addr(context, id, from, 137)
        pdu.to.forEach { addr(context, id, it, 151) }
        pdu.cc.forEach { addr(context, id, it, 130) }
        SmsRepository.clearCaches()
        return threadId
    }

    /** Stores a message you're sending, in the outbox until the carrier confirms. */
    fun saveOutgoing(context: Context, threadId: Long, recipients: List<String>, parts: List<MmsPart>, subId: Int): Uri? {
        val values = ContentValues().apply {
            put(Mms.THREAD_ID, threadId)
            put(Mms.DATE, System.currentTimeMillis() / 1000)
            put(Mms.MESSAGE_BOX, Mms.MESSAGE_BOX_OUTBOX)
            put(Mms.READ, 1)
            put(Mms.SEEN, 1)
            put(Mms.MESSAGE_TYPE, 0x80) // m-send-req
            put(Mms.MMS_VERSION, 0x12)
            put(Mms.CONTENT_TYPE, "application/vnd.wap.multipart.related")
            put(Mms.MESSAGE_SIZE, parts.sumOf { it.data.size })
            if (SubscriptionManager.isValidSubscriptionId(subId)) put(Mms.SUBSCRIPTION_ID, subId)
        }
        val uri = context.contentResolver.insert(Mms.Outbox.CONTENT_URI, values) ?: return null
        val id = ContentUris.parseId(uri)
        writeParts(context, id, parts)
        addr(context, id, "insert-address-token", 137)
        recipients.forEach { addr(context, id, it, 151) }
        return ContentUris.withAppendedId(Mms.CONTENT_URI, id)
    }

    /** Recipients and parts of a stored message, for resending. */
    fun loadOutgoing(context: Context, id: Long): Pair<List<String>, List<MmsPart>> {
        val resolver = context.contentResolver
        val recipients = mutableListOf<String>()
        resolver.query(Uri.parse("content://mms/$id/addr"), arrayOf(Mms.Addr.ADDRESS, Mms.Addr.TYPE), null, null, null)?.use { c ->
            while (c.moveToNext()) if (c.getInt(1) == 151) c.getString(0)?.let { recipients += it }
        }
        val parts = mutableListOf<MmsPart>()
        resolver.query(
            Uri.parse("content://mms/part"),
            arrayOf(Mms.Part._ID, Mms.Part.CONTENT_TYPE, Mms.Part.NAME, Mms.Part.CONTENT_ID, Mms.Part.CONTENT_LOCATION, Mms.Part.CHARSET, Mms.Part.TEXT),
            "${Mms.Part.MSG_ID} = ?", arrayOf(id.toString()), "${Mms.Part.SEQ} ASC",
        )?.use { c ->
            while (c.moveToNext()) {
                val ct = c.getString(1).orEmpty()
                val text = c.getString(6)
                val data = if (ct == "text/plain" || ct == "application/smil") text.orEmpty().toByteArray(Charsets.UTF_8)
                else resolver.openInputStream(Uri.parse("content://mms/part/${c.getLong(0)}"))?.use { it.readBytes() } ?: continue
                parts += MmsPart(ct, c.getString(2), c.getString(3), c.getString(4), if (c.isNull(5)) 0 else c.getInt(5), data)
            }
        }
        return recipients to parts
    }

    fun setBox(context: Context, msg: Uri, box: Int) {
        runCatching { context.contentResolver.update(msg, ContentValues().apply { put(Mms.MESSAGE_BOX, box) }, null, null) }
    }

    private fun writeParts(context: Context, id: Long, parts: List<MmsPart>) {
        val resolver = context.contentResolver
        parts.forEachIndexed { i, p ->
            val pv = ContentValues().apply {
                put(Mms.Part.MSG_ID, id)
                put(Mms.Part.SEQ, if (p.isSmil) -1 else i)
                put(Mms.Part.CONTENT_TYPE, p.contentType)
                p.name?.let { put(Mms.Part.NAME, it); put(Mms.Part.FILENAME, it) }
                p.contentId?.let { put(Mms.Part.CONTENT_ID, it) }
                p.contentLocation?.let { put(Mms.Part.CONTENT_LOCATION, it) }
                if (p.charset > 0) put(Mms.Part.CHARSET, p.charset)
                if (p.isText || p.isSmil) put(Mms.Part.TEXT, p.text())
            }
            val partUri = resolver.insert(Uri.parse("content://mms/$id/part"), pv) ?: return@forEachIndexed
            if (!p.isText && !p.isSmil) resolver.openOutputStream(partUri)?.use { it.write(p.data) }
        }
    }

    private fun addr(context: Context, id: Long, address: String, type: Int) {
        if (address.isBlank()) return
        context.contentResolver.insert(
            Uri.parse("content://mms/$id/addr"),
            ContentValues().apply { put(Mms.Addr.ADDRESS, address); put(Mms.Addr.TYPE, type); put(Mms.Addr.CHARSET, 106) },
        )
    }

    fun notifyFailed(context: Context, from: String) {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel("mms_errors", "Picture message problems", NotificationManager.IMPORTANCE_DEFAULT))
        val who = SmsRepository.lookup(context, from).name ?: from.ifBlank { "someone" }
        nm.notify(
            ("mms" + from).hashCode(),
            android.app.Notification.Builder(context, "mms_errors")
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle("Couldn't download a picture message")
                .setContentText("From $who. Turn on mobile data and ask them to resend.")
                .setAutoCancel(true)
                .build(),
        )
    }

    private fun key(address: String): String {
        val digits = address.filter { it.isDigit() }
        return if (digits.length >= 6) digits.takeLast(9) else address.lowercase()
    }

    /** This phone's own numbers, so we don't add ourselves to group conversations. */
    private fun ownNumbers(context: Context): List<String> = runCatching {
        val sm = context.getSystemService(SubscriptionManager::class.java)
        sm.activeSubscriptionInfoList.orEmpty().mapNotNull { info ->
            @Suppress("DEPRECATION")
            val n = if (Build.VERSION.SDK_INT >= 33) sm.getPhoneNumber(info.subscriptionId) else info.number
            n?.takeIf { it.isNotBlank() }
        }
    }.getOrDefault(emptyList())
}
