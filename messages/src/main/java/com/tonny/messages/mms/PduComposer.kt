package com.tonny.messages.mms

import java.io.ByteArrayOutputStream

/** Builds the m-send-req PDU the carrier's MMS centre expects (OMA MMS encapsulation 1.2). */
object PduComposer {

    fun sendReq(recipients: List<String>, parts: List<MmsPart>): ByteArray {
        val o = Out()
        o.byte(0x8C); o.byte(0x80)                                   // X-Mms-Message-Type: m-send-req
        o.byte(0x98); o.text("T" + java.lang.Long.toHexString(System.currentTimeMillis())) // Transaction-Id
        o.byte(0x8D); o.byte(0x92)                                   // MMS-Version 1.2
        o.byte(0x89); o.byte(0x01); o.byte(0x81)                     // From: insert-address-token (carrier fills in our number)
        recipients.forEach { o.byte(0x97); o.text(address(it)) }     // To
        o.byte(0x8A); o.byte(0x80)                                   // Message-Class: personal
        o.byte(0x86); o.byte(0x81)                                   // Delivery-Report: no
        o.byte(0x90); o.byte(0x81)                                   // Read-Report: no

        // Content-Type: application/vnd.wap.multipart.related; start=<smil>; type=application/smil
        val ct = Out().apply { byte(0xB3); byte(0x8A); text("<smil>"); byte(0x89); text("application/smil") }.bytes()
        o.byte(0x84); o.valueLength(ct.size); o.raw(ct)

        o.uintvar(parts.size.toLong())
        parts.forEach { p ->
            val type = Out().apply {
                text(p.contentType)
                if (p.charset > 0) { byte(0x81); byte(0x80 or p.charset) } // charset (106 = UTF-8 fits a short-integer)
                p.name?.let { byte(0x85); text(it) }                      // name
            }.bytes()
            val headers = Out().apply {
                valueLength(type.size); raw(type)
                p.contentLocation?.let { byte(0x8E); text(it) }           // Content-Location
                p.contentId?.let { byte(0xC0); quoted(it) }               // Content-ID
            }.bytes()
            o.uintvar(headers.size.toLong()); o.uintvar(p.data.size.toLong())
            o.raw(headers); o.raw(p.data)
        }
        return o.bytes()
    }

    /** Phone numbers are addressed as "0712…/TYPE=PLMN"; emails go as they are. */
    private fun address(raw: String): String {
        val a = raw.trim()
        return if (a.contains('@')) a else a.filter { it.isDigit() || it == '+' } + "/TYPE=PLMN"
    }

    private class Out {
        private val s = ByteArrayOutputStream()
        fun byte(b: Int) = s.write(b and 0xFF)
        fun raw(b: ByteArray) = s.write(b)
        fun bytes(): ByteArray = s.toByteArray()

        fun text(t: String) {
            val b = t.toByteArray(Charsets.UTF_8)
            if (b.isNotEmpty() && (b[0].toInt() and 0xFF) >= 0x80) byte(0x7F)
            raw(b); byte(0)
        }

        fun quoted(t: String) { byte(0x22); raw(t.toByteArray(Charsets.UTF_8)); byte(0) }

        fun valueLength(n: Int) { if (n <= 30) byte(n) else { byte(31); uintvar(n.toLong()) } }

        fun uintvar(value: Long) {
            var v = value
            val groups = ArrayList<Int>()
            do { groups.add((v and 0x7F).toInt()); v = v shr 7 } while (v > 0)
            for (i in groups.indices.reversed()) byte(if (i > 0) groups[i] or 0x80 else groups[i])
        }
    }
}
