package com.tonny.messages.mms

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class PduRoundTripTest {

    private val photo = ByteArray(40_000) { (it * 31 % 251).toByte() }
    private val parts = listOf(
        MmsPart("application/smil", "smil.xml", "<smil>", "smil.xml", 106, "<smil><body/></smil>".toByteArray()),
        MmsPart("image/jpeg", "image_1.jpg", "<image_1.jpg>", "image_1.jpg", 0, photo),
        MmsPart("text/plain", "text_0.txt", "<text_0>", "text_0.txt", 106, "Habari! Picha hii 📷".toByteArray()),
    )

    @Test
    fun sendReqParsesBack() {
        val pdu = Pdu.parse(PduComposer.sendReq(listOf("0712345678", "+254700000001"), parts))
        assertNotNull(pdu)
        pdu!!
        assertEquals(0x80, pdu.messageType)
        assertEquals(listOf("0712345678", "+254700000001"), pdu.to)
        assertEquals("application/vnd.wap.multipart.related", pdu.contentType)
        assertEquals(3, pdu.parts.size)
        assertEquals("image/jpeg", pdu.parts[1].contentType)
        assertEquals("image_1.jpg", pdu.parts[1].name)
        assertEquals("<image_1.jpg>", pdu.parts[1].contentId)
        assertArrayEquals(photo, pdu.parts[1].data)
        assertEquals("Habari! Picha hii 📷", pdu.parts[2].text())
        assertEquals(106, pdu.parts[2].charset)
    }

    /** A retrieve-conf like a carrier sends, built by hand with well-known (binary) content types. */
    @Test
    fun parsesCarrierStyleRetrieveConf() {
        val o = java.io.ByteArrayOutputStream()
        fun b(vararg x: Int) = x.forEach { o.write(it) }
        fun t(s: String) { o.write(s.toByteArray()); o.write(0) }
        b(0x8C, 0x84); b(0x98); t("tx1"); b(0x8D, 0x92)
        b(0x85, 0x04, 0x66, 0x00, 0x00, 0x00)                    // Date (long-int, 4 bytes)
        val from = "+254711111111/TYPE=PLMN"
        b(0x89, from.length + 2, 0x80); t(from)                  // From: address-present
        b(0x97); t("+254722222222/TYPE=PLMN")
        b(0x96); t("Hi")
        b(0x84, 0xA3)                                            // multipart.mixed (well-known)
        b(2)                                                     // 2 parts
        val txt = "hello".toByteArray()
        b(3, txt.size, 0x83, 0x81, 0xEA)                         // headers: text/plain, then a one-byte charset header
        o.write(txt)
        val img = ByteArray(10) { 7 }
        b(1, img.size, 0x9E); o.write(img)                       // image/jpeg
        val pdu = Pdu.parse(o.toByteArray())
        assertNotNull(pdu); pdu!!
        assertEquals(0x84, pdu.messageType)
        assertEquals("+254711111111", pdu.from)
        assertEquals(listOf("+254722222222"), pdu.to)
        assertEquals("Hi", pdu.subject)
        assertEquals(2, pdu.parts.size)
        assertEquals("text/plain", pdu.parts[0].contentType)
        assertEquals("image/jpeg", pdu.parts[1].contentType)
        assertArrayEquals(img, pdu.parts[1].data)
    }
}
