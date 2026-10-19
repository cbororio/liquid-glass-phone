package com.tonny.messages.data

import android.content.Context
import android.provider.ContactsContract.CommonDataKinds.Phone

data class ContactMatch(
    val name: String,
    val number: String,
    val label: String,
    val photoUri: String?,
)

/** Looks up contacts by name or number for the "To:" field. */
object ContactSearch {

    fun search(context: Context, query: String, limit: Int = 20): List<ContactMatch> {
        val q = query.trim()
        if (q.isEmpty()) return emptyList()
        val uri = Phone.CONTENT_FILTER_URI.buildUpon().appendPath(q).build()
        val projection = arrayOf(
            Phone.DISPLAY_NAME_PRIMARY, Phone.NUMBER, Phone.TYPE, Phone.LABEL, Phone.PHOTO_THUMBNAIL_URI,
        )
        val out = mutableListOf<ContactMatch>()
        val seen = HashSet<String>()
        context.contentResolver.query(uri, projection, null, null, "${Phone.DISPLAY_NAME_PRIMARY} ASC")?.use { c ->
            while (c.moveToNext() && out.size < limit) {
                val number = c.getString(1) ?: continue
                val key = number.filter { it.isDigit() }.takeLast(9)
                if (!seen.add(key)) continue
                out += ContactMatch(
                    name = c.getString(0) ?: number,
                    number = number,
                    label = Phone.getTypeLabel(context.resources, c.getInt(2), c.getString(3)).toString(),
                    photoUri = c.getString(4),
                )
            }
        }
        return out
    }
}
