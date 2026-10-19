package com.tonny.messages.mms

import java.nio.charset.Charset

/** One attachment or text block of an MMS. */
class MmsPart(
    val contentType: String,
    val name: String?,
    val contentId: String?,
    val contentLocation: String?,
    val charset: Int,
    val data: ByteArray,
) {
    val isText: Boolean get() = contentType.startsWith("text/plain", ignoreCase = true)
    val isSmil: Boolean get() = contentType.equals("application/smil", ignoreCase = true)
    val isImage: Boolean get() = contentType.startsWith("image/", ignoreCase = true)
    fun text(): String = String(data, Pdu.charset(charset)).trimEnd('\u0000')
}

/** The fields we use from an MMS PDU (OMA MMS encapsulation, WSP-encoded). */
class MmsPdu(
    val messageType: Int,
    val transactionId: String?,
    val from: String?,
    val to: List<String>,
    val cc: List<String>,
    val subject: String?,
    val contentLocation: String?,
    /** Seconds since the epoch, or 0. */
    val date: Long,
    val messageId: String?,
    val contentType: String?,
    val size: Long,
    val parts: List<MmsPart>,
)

/**
 * A small reader for the two PDUs an MMS client receives: the notification that a message is
 * waiting (m-notification-ind) and the downloaded message itself (m-retrieve-conf).
 */
object Pdu {
    const val NOTIFICATION_IND = 0x82
    const val RETRIEVE_CONF = 0x84

    fun parse(bytes: ByteArray): MmsPdu? = runCatching { Reader(bytes).pdu() }.getOrNull()

    fun charset(mib: Int): Charset = when (mib) {
        4 -> Charsets.ISO_8859_1
        3 -> Charsets.US_ASCII
        1015, 1000 -> Charsets.UTF_16
        else -> Charsets.UTF_8
    }

    /** "+254712…/TYPE=PLMN" -> "+254712…". */
    fun cleanAddress(raw: String): String = raw.substringBefore("/TYPE").trim()

    // WSP well-known content types we may meet.
    private val wellKnownTypes = mapOf(
        0x00 to "*/*", 0x01 to "text/*", 0x02 to "text/html", 0x03 to "text/plain", 0x07 to "text/x-vCard",
        0x0C to "multipart/mixed", 0x0F to "multipart/alternative", 0x1C to "image/*", 0x1D to "image/gif",
        0x1E to "image/jpeg", 0x1F to "image/tiff", 0x20 to "image/png", 0x21 to "image/vnd.wap.wbmp",
        0x22 to "application/vnd.wap.multipart.*", 0x23 to "application/vnd.wap.multipart.mixed",
        0x26 to "application/vnd.wap.multipart.alternative", 0x27 to "application/xml", 0x28 to "text/xml",
        0x33 to "application/vnd.wap.multipart.related", 0x3E to "application/vnd.wap.mms-message",
    )

    private class ContentType(val type: String, val charset: Int, val name: String?)

    private class Reader(val b: ByteArray) {
        var pos = 0

        fun u8(): Int = b[pos++].toInt() and 0xFF
        fun peek(): Int = b[pos].toInt() and 0xFF
        fun eof() = pos >= b.size

        fun uintvar(): Long {
            var v = 0L
            while (true) {
                val x = u8()
                v = (v shl 7) or (x and 0x7F).toLong()
                if (x and 0x80 == 0) return v
            }
        }

        fun valueLength(): Int {
            val x = u8()
            return when {
                x < 31 -> x
                x == 31 -> uintvar().toInt()
                else -> error("not a value-length")
            }
        }

        /** Text-string or quoted-string, NUL terminated. */
        fun text(charset: Charset = Charsets.UTF_8): String {
            if (peek() == 0x7F || peek() == 0x22) pos++
            val start = pos
            while (pos < b.size && b[pos].toInt() != 0) pos++
            val s = String(b, start, pos - start, charset)
            if (pos < b.size) pos++
            return s
        }

        fun longInt(): Long {
            val n = u8()
            var v = 0L
            repeat(n) { v = (v shl 8) or u8().toLong() }
            return v
        }

        /** Short-integer or long-integer. */
        fun integer(): Long {
            val x = peek()
            return if (x and 0x80 != 0) { pos++; (x and 0x7F).toLong() } else longInt()
        }

        /** Encoded-string-value: plain text, or a length + charset + text. */
        fun encodedString(): String {
            val x = peek()
            if (x > 31) return text()
            val len = valueLength()
            val end = pos + len
            val cs = if (peek() in 32..127) 106 else integer().toInt()
            val s = text(charset(cs))
            pos = end
            return s
        }

        /** Skips any WSP value by its leading byte. */
        fun skipValue() {
            val x = peek()
            when {
                x <= 30 -> { pos++; pos += x }
                x == 31 -> { pos++; pos += uintvar().toInt() }
                x < 128 -> text()
                else -> pos++
            }
        }

        fun mediaType(code: Int) = wellKnownTypes[code] ?: "application/octet-stream"

        fun contentType(): ContentType {
            val x = peek()
            if (x >= 0x80) { pos++; return ContentType(mediaType(x and 0x7F), 0, null) }
            if (x >= 32) return ContentType(text(), 0, null)
            val len = valueLength()
            val end = pos + len
            val y = peek()
            val type = when {
                y >= 0x80 -> { pos++; mediaType(y and 0x7F) }
                y >= 32 -> text()
                else -> mediaType(longInt().toInt())
            }
            var charset = 0
            var name: String? = null
            while (pos < end) {
                val p = peek()
                if (p >= 0x80) {
                    pos++
                    when (p and 0x7F) {
                        0x01 -> charset = if (peek() in 32..127) { text(); 106 } else integer().toInt()
                        0x05, 0x06, 0x17, 0x18 -> name = text()
                        else -> skipValue()
                    }
                } else {
                    text() // untyped parameter name
                    if (pos < end) { if (peek() in 32..127) text() else skipValue() }
                }
            }
            pos = end
            return ContentType(type, charset, name)
        }

        fun pdu(): MmsPdu {
            var type = 0; var trId: String? = null; var from: String? = null
            val to = mutableListOf<String>(); val cc = mutableListOf<String>()
            var subject: String? = null; var location: String? = null; var date = 0L
            var messageId: String? = null; var ct: ContentType? = null; var size = 0L

            headers@ while (!eof()) {
                val h = u8()
                if (h < 0x80) { pos--; text(); skipValue(); continue }
                when (h) {
                    0x8C -> type = u8()
                    0x98 -> trId = text()
                    0x89 -> {
                        val len = valueLength(); val end = pos + len
                        if (u8() == 0x80) from = cleanAddress(encodedString())
                        pos = end
                    }
                    0x97 -> to += cleanAddress(encodedString())
                    0x82 -> cc += cleanAddress(encodedString())
                    0x96 -> subject = encodedString()
                    0x83 -> location = text()
                    0x85 -> date = longInt()
                    0x8B -> messageId = text()
                    0x8E -> size = longInt()
                    0x84 -> { ct = contentType(); break@headers }
                    else -> skipValue()
                }
            }

            val parts = mutableListOf<MmsPart>()
            val bodyType = ct?.type.orEmpty()
            if (ct != null && !eof()) {
                if (bodyType.contains("multipart", ignoreCase = true)) {
                    val count = uintvar().toInt()
                    repeat(count) {
                        val headersLen = uintvar().toInt()
                        val dataLen = uintvar().toInt()
                        val start = pos
                        val partType = contentType()
                        var cid: String? = null; var cl: String? = null
                        while (pos < start + headersLen) {
                            val h = peek()
                            if (h >= 0x80) {
                                pos++
                                when (h) {
                                    0x8E -> cl = text()
                                    0xC0 -> cid = text()
                                    else -> skipValue()
                                }
                            } else {
                                text()
                                if (pos < start + headersLen) { if (peek() in 32..127) text() else skipValue() }
                            }
                        }
                        pos = start + headersLen
                        val data = b.copyOfRange(pos, (pos + dataLen).coerceAtMost(b.size))
                        pos += dataLen
                        parts += MmsPart(partType.type, partType.name ?: cl, cid, cl, partType.charset, data)
                    }
                } else {
                    parts += MmsPart(bodyType, ct.name, null, null, ct.charset, b.copyOfRange(pos, b.size))
                }
            }
            return MmsPdu(type, trId, from, to, cc, subject, location, date, messageId, ct?.type, size, parts)
        }
    }
}
