package com.tonny.messages.data

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.provider.ContactsContract
import android.provider.Telephony

data class Conversation(
    val threadId: Long,
    val address: String,
    val name: String?,
    val photoUri: String?,
    val snippet: String,
    val date: Long,
    val unread: Boolean,
) {
    val title: String get() = name?.takeIf { it.isNotBlank() } ?: address.ifBlank { "Unknown" }
    /** Whether you can reply. Short codes and alphanumeric senders (MPESA, Safaricom) can't be replied to. */
    val replyable: Boolean get() = isReplyable(address)
    /** A saved contact counts as a known sender; everything else is "unknown". */
    val known: Boolean get() = !name.isNullOrBlank()
}

fun isReplyable(address: String): Boolean {
    if (address.isBlank()) return false
    if (address.contains(',')) return false // group conversation: replies would need MMS
    if (address.any { it.isLetter() }) return false
    val digits = address.filter { it.isDigit() }
    return digits.length >= 7
}

data class Message(
    val id: Long,
    val threadId: Long,
    val body: String,
    val date: Long,
    val incoming: Boolean,
    val pending: Boolean,
    val failed: Boolean,
    /** A delivery report came back for this sent text. */
    val delivered: Boolean = false,
    /** Pictures, voice notes and files (content://mms/part/… URIs). */
    val attachments: List<Attachment> = emptyList(),
)

/** One non-text part of a picture message. */
data class Attachment(val uri: String, val type: String, val name: String?) {
    val isImage: Boolean get() = type.startsWith("image/")
    val isAudio: Boolean get() = type.startsWith("audio/")
}

data class ContactInfo(val name: String?, val photoUri: String?)

object SmsRepository {

    private val contactCache = mutableMapOf<String, ContactInfo>()

    fun conversations(context: Context): List<Conversation> {
        val uri = Telephony.MmsSms.CONTENT_CONVERSATIONS_URI.buildUpon()
            .appendQueryParameter("simple", "true").build()
        val projection = arrayOf("_id", "recipient_ids", "snippet", "date", "read")
        // Two bulk queries up front instead of two lookups per conversation, which took seconds.
        loadRecipients(context)
        val contacts = runCatching { contactsByNumber(context) }.getOrDefault(emptyMap())
        val out = mutableListOf<Conversation>()
        context.contentResolver.query(uri, projection, null, null, "date DESC")?.use { c ->
            val idI = c.getColumnIndex("_id")
            val recI = c.getColumnIndex("recipient_ids")
            val snipI = c.getColumnIndex("snippet")
            val dateI = c.getColumnIndex("date")
            val readI = c.getColumnIndex("read")
            while (c.moveToNext()) {
                val recipientIds = c.getString(recI).orEmpty()
                // A group conversation has several recipients; its address joins them with commas.
                val addresses = recipientIds.split(' ').mapNotNull { it.toLongOrNull() }
                    .map { addressForRecipient(context, it) }.filter { it.isNotBlank() }
                val address = addresses.joinToString(",")
                val info = if (addresses.size > 1) {
                    ContactInfo(addresses.joinToString(", ") { contacts[numberKey(it)]?.name ?: it }, null)
                } else contacts[numberKey(address)] ?: ContactInfo(null, null)
                out += Conversation(
                    threadId = c.getLong(idI),
                    address = address,
                    name = info.name,
                    photoUri = info.photoUri,
                    snippet = c.getString(snipI).orEmpty(),
                    date = c.getLong(dateI),
                    unread = c.getInt(readI) == 0,
                )
            }
        }
        return out
    }

    private val recipientCache = mutableMapOf<Long, String>()

    /** Fills [recipientCache] with every canonical address in one query. */
    private fun loadRecipients(context: Context) {
        runCatching {
            context.contentResolver.query(Uri.parse("content://mms-sms/canonical-addresses"), arrayOf("_id", "address"), null, null, null)?.use { c ->
                while (c.moveToNext()) recipientCache[c.getLong(0)] = c.getString(1).orEmpty()
            }
        }
    }

    /** Every saved phone number keyed by [numberKey], read in one pass over the contacts table. */
    private fun contactsByNumber(context: Context): Map<String, ContactInfo> {
        val out = HashMap<String, ContactInfo>()
        context.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(
                ContactsContract.CommonDataKinds.Phone.NUMBER,
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME_PRIMARY,
                ContactsContract.CommonDataKinds.Phone.PHOTO_THUMBNAIL_URI,
            ),
            null, null, null,
        )?.use { c ->
            while (c.moveToNext()) {
                val key = numberKey(c.getString(0) ?: continue)
                if (key.isNotEmpty() && key !in out) out[key] = ContactInfo(c.getString(1), c.getString(2))
            }
        }
        return out
    }

    /**
     * Each unsaved sender's latest text, run through the promo filter. Used once to sort existing
     * betting blasts into Spam; later texts are checked as they arrive.
     */
    fun promoVerdicts(context: Context): Map<String, Boolean> {
        val contacts = runCatching { contactsByNumber(context) }.getOrDefault(emptyMap())
        val out = LinkedHashMap<String, Boolean>()
        context.contentResolver.query(
            Telephony.Sms.Inbox.CONTENT_URI, arrayOf(Telephony.Sms.ADDRESS, Telephony.Sms.BODY),
            null, null, "${Telephony.Sms.DATE} DESC",
        )?.use { c ->
            while (c.moveToNext()) {
                val address = c.getString(0) ?: continue
                if (address in out) continue
                val key = numberKey(address)
                out[address] = (key.isEmpty() || key !in contacts) && SpamFilter.looksLikePromo(c.getString(1).orEmpty())
            }
        }
        return out
    }

    /** Last 9 digits, so +254712... and 0712... match; empty for sender names like MPESA. */
    private fun numberKey(number: String): String {
        val digits = number.filter { it.isDigit() }
        return if (digits.length >= 6) digits.takeLast(9) else ""
    }

    private fun addressForRecipient(context: Context, id: Long): String = recipientCache.getOrPut(id) {
        val uri = Uri.parse("content://mms-sms/canonical-address/$id")
        context.contentResolver.query(uri, null, null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0).orEmpty() else ""
        } ?: ""
    }

    fun messages(context: Context, threadId: Long): List<Message> {
        val out = mutableListOf<Message>()
        // --- SMS parts of the thread
        context.contentResolver.query(
            Telephony.Sms.CONTENT_URI,
            arrayOf(Telephony.Sms._ID, Telephony.Sms.BODY, Telephony.Sms.DATE, Telephony.Sms.TYPE, Telephony.Sms.STATUS),
            "${Telephony.Sms.THREAD_ID} = ?", arrayOf(threadId.toString()), null,
        )?.use { c ->
            while (c.moveToNext()) {
                val type = c.getInt(3)
                out += Message(
                    id = c.getLong(0),
                    threadId = threadId,
                    body = c.getString(1).orEmpty(),
                    date = c.getLong(2),
                    incoming = type == Telephony.Sms.MESSAGE_TYPE_INBOX,
                    pending = type == Telephony.Sms.MESSAGE_TYPE_OUTBOX || type == Telephony.Sms.MESSAGE_TYPE_QUEUED,
                    failed = type == Telephony.Sms.MESSAGE_TYPE_FAILED,
                    delivered = type == Telephony.Sms.MESSAGE_TYPE_SENT && c.getInt(4) == Telephony.Sms.STATUS_COMPLETE,
                )
            }
        }
        // --- MMS: text and pictures
        context.contentResolver.query(
            Telephony.Mms.CONTENT_URI,
            arrayOf(Telephony.Mms._ID, Telephony.Mms.DATE, Telephony.Mms.MESSAGE_BOX),
            "${Telephony.Mms.THREAD_ID} = ?", arrayOf(threadId.toString()), null,
        )?.use { c ->
            while (c.moveToNext()) {
                val mmsId = c.getLong(0)
                val box = c.getInt(2)
                val (text, attachments) = mmsParts(context, mmsId)
                if (text.isNotBlank() || attachments.isNotEmpty()) {
                    out += Message(
                        id = MMS_ID_OFFSET + mmsId, // keep ids distinct from SMS
                        threadId = threadId,
                        body = text,
                        date = c.getLong(1) * 1000L, // MMS date is in seconds
                        incoming = box == Telephony.Mms.MESSAGE_BOX_INBOX,
                        pending = box == Telephony.Mms.MESSAGE_BOX_OUTBOX,
                        failed = box == Telephony.Mms.MESSAGE_BOX_FAILED,
                        attachments = attachments,
                    )
                }
            }
        }
        return out.sortedBy { it.date }
    }

    /** The text and the attachments of one MMS (the SMIL layout part is skipped). */
    private fun mmsParts(context: Context, mmsId: Long): Pair<String, List<Attachment>> {
        val uri = Uri.parse("content://mms/part")
        val sb = StringBuilder()
        val attachments = mutableListOf<Attachment>()
        context.contentResolver.query(
            uri, arrayOf("_id", "ct", "text", "name", "fn", "cl"), "mid = ?", arrayOf(mmsId.toString()), "seq ASC",
        )?.use { c ->
            while (c.moveToNext()) {
                val ct = c.getString(1).orEmpty().lowercase()
                when {
                    ct == "text/plain" -> c.getString(2)?.let { if (sb.isNotEmpty()) sb.append('\n'); sb.append(it) }
                    ct == "application/smil" -> Unit
                    else -> attachments += Attachment(
                        "content://mms/part/${c.getLong(0)}", ct,
                        c.getString(3) ?: c.getString(4) ?: c.getString(5),
                    )
                }
            }
        }
        return sb.toString() to attachments
    }

    fun markUnread(context: Context, threadId: Long) {
        // Mark the most recent message in the thread unread, which is how iOS shows the blue dot.
        val latest = context.contentResolver.query(
            Telephony.Sms.CONTENT_URI, arrayOf(Telephony.Sms._ID),
            "${Telephony.Sms.THREAD_ID} = ?", arrayOf(threadId.toString()), "${Telephony.Sms.DATE} DESC",
        )?.use { c -> if (c.moveToFirst()) c.getLong(0) else null }
        val values = ContentValues().apply { put(Telephony.Sms.READ, 0); put(Telephony.Sms.SEEN, 0) }
        runCatching {
            if (latest != null) context.contentResolver.update(Telephony.Sms.CONTENT_URI, values, "${Telephony.Sms._ID} = ?", arrayOf(latest.toString()))
        }
    }

    fun markAllRead(context: Context) {
        val values = ContentValues().apply {
            put(Telephony.Sms.READ, 1); put(Telephony.Sms.SEEN, 1)
        }
        runCatching { context.contentResolver.update(Telephony.Sms.CONTENT_URI, values, "${Telephony.Sms.READ} = 0", null) }
        runCatching { context.contentResolver.update(Telephony.Mms.CONTENT_URI, values, "${Telephony.Mms.READ} = 0", null) }
    }

    fun threadId(context: Context, address: String): Long =
        Telephony.Threads.getOrCreateThreadId(context, address)

    fun markRead(context: Context, threadId: Long) {
        val values = ContentValues().apply {
            put(Telephony.Sms.READ, 1)
            put(Telephony.Sms.SEEN, 1)
        }
        runCatching {
            context.contentResolver.update(
                Telephony.Sms.CONTENT_URI, values,
                "${Telephony.Sms.THREAD_ID} = ? AND ${Telephony.Sms.READ} = 0", arrayOf(threadId.toString()),
            )
        }
        runCatching {
            context.contentResolver.update(
                Telephony.Mms.CONTENT_URI, values,
                "${Telephony.Mms.THREAD_ID} = ? AND ${Telephony.Mms.READ} = 0", arrayOf(threadId.toString()),
            )
        }
    }

    /** Deletes one message, SMS or MMS. */
    fun deleteMessage(context: Context, message: Message) {
        val uri = if (message.id >= MMS_ID_OFFSET) ContentUris.withAppendedId(Telephony.Mms.CONTENT_URI, message.id - MMS_ID_OFFSET)
        else ContentUris.withAppendedId(Telephony.Sms.CONTENT_URI, message.id)
        runCatching { context.contentResolver.delete(uri, null, null) }
    }

    const val MMS_ID_OFFSET = 1_000_000_000_000L

    fun deleteThread(context: Context, threadId: Long) {
        runCatching {
            context.contentResolver.delete(
                Uri.parse("content://sms/conversations/$threadId"), null, null,
            )
        }
    }

    fun lookup(context: Context, number: String): ContactInfo {
        if (number.isBlank()) return ContactInfo(null, null)
        return contactCache.getOrPut(number) {
            val uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(number))
            context.contentResolver.query(
                uri,
                arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME, ContactsContract.PhoneLookup.PHOTO_THUMBNAIL_URI),
                null, null, null,
            )?.use { c -> if (c.moveToFirst()) ContactInfo(c.getString(0), c.getString(1)) else ContactInfo(null, null) }
                ?: ContactInfo(null, null)
        }
    }

    fun clearCaches() {
        contactCache.clear()
        recipientCache.clear()
    }
}
