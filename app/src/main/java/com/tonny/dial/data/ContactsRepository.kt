package com.tonny.dial.data

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.provider.ContactsContract
import android.provider.ContactsContract.CommonDataKinds.Phone
import com.tonny.dial.telecom.ContactInfo

data class PhoneNumber(val number: String, val label: String)

data class Contact(
    val id: Long,
    val name: String,
    val photoUri: String?,
    val starred: Boolean,
    val numbers: List<PhoneNumber>,
) {
    /** Digits of the name mapped onto the keypad, for T9 matching. */
    val t9: String by lazy { name.lowercase().map(::t9Digit).joinToString("") }
}

private fun t9Digit(c: Char): Char = when (c) {
    in "abc" -> '2'; in "def" -> '3'; in "ghi" -> '4'; in "jkl" -> '5'
    in "mno" -> '6'; in "pqrs" -> '7'; in "tuv" -> '8'; in "wxyz" -> '9'
    else -> if (c.isDigit()) c else ' '
}

object ContactsRepository {

    fun load(context: Context): List<Contact> {
        val projection = arrayOf(
            Phone.CONTACT_ID, Phone.DISPLAY_NAME_PRIMARY, Phone.NUMBER, Phone.TYPE,
            Phone.LABEL, Phone.PHOTO_THUMBNAIL_URI, Phone.STARRED,
        )
        val byId = linkedMapOf<Long, Contact>()
        context.contentResolver.query(
            Phone.CONTENT_URI, projection, null, null,
            "${Phone.DISPLAY_NAME_PRIMARY} COLLATE LOCALIZED ASC",
        )?.use { c ->
            while (c.moveToNext()) {
                val id = c.getLong(0)
                val number = c.getString(2) ?: continue
                val label = Phone.getTypeLabel(context.resources, c.getInt(3), c.getString(4)).toString()
                val existing = byId[id]
                if (existing == null) {
                    byId[id] = Contact(
                        id = id,
                        name = c.getString(1) ?: number,
                        photoUri = c.getString(5),
                        starred = c.getInt(6) == 1,
                        numbers = listOf(PhoneNumber(number, label)),
                    )
                } else if (existing.numbers.none { normalize(it.number) == normalize(number) }) {
                    byId[id] = existing.copy(numbers = existing.numbers + PhoneNumber(number, label))
                }
            }
        }
        return byId.values.toList()
    }

    fun lookup(context: Context, number: String): ContactInfo {
        val uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(number))
        context.contentResolver.query(
            uri,
            arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME, ContactsContract.PhoneLookup.PHOTO_THUMBNAIL_URI),
            null, null, null,
        )?.use { c -> if (c.moveToFirst()) return ContactInfo(c.getString(0), c.getString(1)) }
        return ContactInfo(null, null)
    }

    fun setStarred(context: Context, contactId: Long, starred: Boolean) {
        val values = ContentValues().apply { put(ContactsContract.Contacts.STARRED, if (starred) 1 else 0) }
        context.contentResolver.update(
            ContactsContract.Contacts.CONTENT_URI, values,
            "${ContactsContract.Contacts._ID} = ?", arrayOf(contactId.toString()),
        )
    }

    fun normalize(number: String): String = number.filter { it.isDigit() || it == '+' }
}
