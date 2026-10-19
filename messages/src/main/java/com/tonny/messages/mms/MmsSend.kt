package com.tonny.messages.mms

import android.app.Activity
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.provider.OpenableColumns
import android.provider.Telephony
import android.provider.Telephony.Mms
import android.telephony.SmsManager
import android.telephony.SubscriptionManager
import android.util.Log
import android.widget.Toast
import androidx.core.content.FileProvider
import com.tonny.messages.data.Prefs
import com.tonny.messages.sms.MessagesEvents
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.math.max

private const val TAG = "MmsSend"

/** A photo, voice note or document ready to send. */
class OutgoingAttachment(val bytes: ByteArray, val contentType: String, val name: String) {
    val isImage get() = contentType.startsWith("image/")
    val isAudio get() = contentType.startsWith("audio/")
}

object MmsSender {

    /** The carrier's MMS size cap, or 300 KB when it doesn't say. */
    fun maxSize(context: Context, subId: Int?): Int = runCatching {
        @Suppress("DEPRECATION")
        MmsDownloader.smsManager(context, subId ?: SubscriptionManager.getDefaultSmsSubscriptionId())
            .carrierConfigValues.getInt(SmsManager.MMS_CONFIG_MAX_MESSAGE_SIZE)
    }.getOrNull()?.takeIf { it > 0 } ?: 300 * 1024

    /** Room left for the attachment after headers, the layout file and the text. */
    fun attachmentBudget(context: Context, subId: Int?, textBytes: Int = 0): Int =
        (maxSize(context, subId) * 0.92).toInt() - 2_000 - textBytes

    /**
     * Saves the message to the outbox, then hands it to the system to send over mobile data.
     * Returns the conversation it went into.
     */
    fun send(context: Context, recipients: List<String>, text: String?, attachment: OutgoingAttachment?, subId: Int?): Long {
        val app = context.applicationContext
        Prefs.init(app)
        val sub = subId ?: Prefs.simFor(recipients.first()) ?: SubscriptionManager.getDefaultSmsSubscriptionId()
        val parts = buildParts(text, attachment)
        val threadId = Telephony.Threads.getOrCreateThreadId(app, recipients.toSet())
        val msgUri = MmsStore.saveOutgoing(app, threadId, recipients, parts, sub) ?: error("couldn't save message")
        transmit(app, msgUri, recipients, parts, sub)
        return threadId
    }

    /** Sends a failed picture/voice/document message again with the same content. */
    fun resend(context: Context, mmsId: Long, subId: Int?) {
        val app = context.applicationContext
        val (recipients, parts) = MmsStore.loadOutgoing(app, mmsId)
        if (recipients.isEmpty() || parts.isEmpty()) return
        val msgUri = ContentUris.withAppendedId(Mms.CONTENT_URI, mmsId)
        app.contentResolver.update(msgUri, ContentValues().apply {
            put(Mms.MESSAGE_BOX, Mms.MESSAGE_BOX_OUTBOX); put(Mms.DATE, System.currentTimeMillis() / 1000)
        }, null, null)
        val sub = subId ?: Prefs.simFor(recipients.first()) ?: SubscriptionManager.getDefaultSmsSubscriptionId()
        transmit(app, msgUri, recipients, parts, sub)
    }

    private fun transmit(app: Context, msgUri: Uri, recipients: List<String>, parts: List<MmsPart>, sub: Int) {
        val pdu = PduComposer.sendReq(recipients, parts)
        val dir = File(app.cacheDir, "mms").apply { mkdirs() }
        val file = File(dir, "send_${System.nanoTime()}.pdu").apply { writeBytes(pdu) }
        val uri = FileProvider.getUriForFile(app, MmsDownloader.AUTHORITY, file)
        val done = PendingIntent.getBroadcast(
            app, file.name.hashCode(),
            Intent(app, MmsSentReceiver::class.java).putExtra(EXTRA_MSG, msgUri.toString()).putExtra(EXTRA_PATH, file.absolutePath),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
        )
        runCatching { MmsDownloader.smsManager(app, sub).sendMultimediaMessage(app, uri, null, null, done) }
            .onFailure {
                Log.w(TAG, "send failed", it)
                MmsStore.setBox(app, msgUri, Mms.MESSAGE_BOX_FAILED)
                file.delete()
            }
        MessagesEvents.bump()
    }

    /** A SMIL layout part (what iPhones and most phones use to arrange the slide), then the content. */
    private fun buildParts(text: String?, attachment: OutgoingAttachment?): List<MmsPart> {
        val parts = mutableListOf<MmsPart>()
        val body = StringBuilder()
        attachment?.let {
            parts += MmsPart(it.contentType, it.name, "<${it.name}>", it.name, 0, it.bytes)
            body.append(
                when {
                    it.isImage -> "<img src=\"${it.name}\" region=\"Image\"/>"
                    it.isAudio -> "<audio src=\"${it.name}\"/>"
                    else -> "<ref src=\"${it.name}\"/>"
                },
            )
        }
        text?.trim()?.takeIf { it.isNotEmpty() }?.let {
            parts += MmsPart("text/plain", "text_0.txt", "<text_0>", "text_0.txt", 106, it.toByteArray(Charsets.UTF_8))
            body.append("<text src=\"text_0.txt\" region=\"Text\"/>")
        }
        val smil = "<smil><head><layout><root-layout/>" +
            "<region id=\"Image\" fit=\"meet\" top=\"0\" left=\"0\" height=\"80%\" width=\"100%\"/>" +
            "<region id=\"Text\" top=\"80%\" left=\"0\" height=\"20%\" width=\"100%\"/>" +
            "</layout></head><body><par dur=\"5000ms\">$body</par></body></smil>"
        return listOf(MmsPart("application/smil", "smil.xml", "<smil>", "smil.xml", 106, smil.toByteArray(Charsets.UTF_8))) + parts
    }

    internal const val EXTRA_MSG = "msg"
    internal const val EXTRA_PATH = "path"

    // ------------------------------------------------------------------ preparing attachments

    /** Shrinks a photo until it fits [budget] bytes as a JPEG (most carriers cap MMS at ~300 KB–1 MB). */
    fun compressImage(context: Context, uri: Uri, budget: Int): OutgoingAttachment? = runCatching {
        val source = ImageDecoder.createSource(context.contentResolver, uri)
        val full = ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            val longest = max(info.size.width, info.size.height)
            if (longest > 1600) {
                val s = 1600f / longest
                decoder.setTargetSize((info.size.width * s).toInt(), (info.size.height * s).toInt())
            }
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        }
        for (side in intArrayOf(1600, 1280, 1024, 800, 640, 480, 360)) {
            val scale = side.toFloat() / max(full.width, full.height)
            val bmp = if (scale < 1f) Bitmap.createScaledBitmap(full, (full.width * scale).toInt(), (full.height * scale).toInt(), true) else full
            for (quality in intArrayOf(85, 70, 55, 40)) {
                val out = ByteArrayOutputStream()
                bmp.compress(Bitmap.CompressFormat.JPEG, quality, out)
                if (out.size() <= budget) return@runCatching OutgoingAttachment(out.toByteArray(), "image/jpeg", "image_${System.currentTimeMillis() % 100000}.jpg")
            }
        }
        null
    }.getOrNull()

    /** Reads any file as-is; null if it's bigger than [budget]. */
    fun readFile(context: Context, uri: Uri, budget: Int): Pair<OutgoingAttachment?, Long> {
        val resolver = context.contentResolver
        var name = "file"
        var size = -1L
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { c ->
            if (c.moveToFirst()) { name = c.getString(0) ?: name; size = if (c.isNull(1)) -1 else c.getLong(1) }
        }
        if (size > budget) return null to size
        val bytes = resolver.openInputStream(uri)?.use { it.readBytes() } ?: return null to size
        if (bytes.size > budget) return null to bytes.size.toLong()
        val type = resolver.getType(uri) ?: "application/octet-stream"
        return OutgoingAttachment(bytes, type, name.replace(Regex("[^\\w.\\-]"), "_")) to bytes.size.toLong()
    }
}

/** Result of a send: marks the message sent, or failed so it can be resent. */
class MmsSentReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val msg = intent.getStringExtra(MmsSender.EXTRA_MSG)?.let(Uri::parse) ?: return
        intent.getStringExtra(MmsSender.EXTRA_PATH)?.let { File(it).delete() }
        val ok = resultCode == Activity.RESULT_OK
        MmsStore.setBox(context, msg, if (ok) Mms.MESSAGE_BOX_SENT else Mms.MESSAGE_BOX_FAILED)
        if (!ok) {
            Log.w(TAG, "carrier rejected send, result=$resultCode")
            Toast.makeText(context, "Picture message not sent. Turn on mobile data and try again.", Toast.LENGTH_LONG).show()
        }
        MessagesEvents.bump()
    }
}
