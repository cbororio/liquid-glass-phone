package com.tonny.dial.data

import android.content.Context
import android.provider.CallLog
import java.util.Calendar

/** One call log entry. */
data class CallEntry(val date: Long, val type: Int, val duration: Long) {
    val isMissed: Boolean
        get() = type == CallLog.Calls.MISSED_TYPE || type == CallLog.Calls.REJECTED_TYPE
}

/** A Recents row: one or more consecutive same-day calls with a number, newest first in [entries]. */
data class RecentCall(
    val ids: List<Long>,
    val number: String,
    val name: String?,
    val photoUri: String?,
    val type: Int,
    val date: Long,
    val duration: Long,
    val entries: List<CallEntry>,
) {
    val count: Int get() = ids.size
    val title: String get() = name?.takeIf { it.isNotBlank() } ?: number.ifBlank { "Private number" }
    val isMissed: Boolean
        get() = type == CallLog.Calls.MISSED_TYPE || type == CallLog.Calls.REJECTED_TYPE
}

object CallLogRepository {

    fun load(context: Context, limit: Int = 400): List<RecentCall> {
        val uri = CallLog.Calls.CONTENT_URI.buildUpon()
            .appendQueryParameter(CallLog.Calls.LIMIT_PARAM_KEY, limit.toString())
            .build()
        val projection = arrayOf(
            CallLog.Calls._ID, CallLog.Calls.NUMBER, CallLog.Calls.CACHED_NAME,
            CallLog.Calls.CACHED_PHOTO_URI, CallLog.Calls.TYPE, CallLog.Calls.DATE, CallLog.Calls.DURATION,
        )
        val out = mutableListOf<RecentCall>()
        context.contentResolver.query(uri, projection, null, null, "${CallLog.Calls.DATE} DESC")?.use { c ->
            while (c.moveToNext()) {
                val entry = RecentCall(
                    ids = listOf(c.getLong(0)),
                    number = c.getString(1).orEmpty(),
                    name = c.getString(2),
                    photoUri = c.getString(3),
                    type = c.getInt(4),
                    date = c.getLong(5),
                    duration = c.getLong(6),
                    entries = listOf(CallEntry(c.getLong(5), c.getInt(4), c.getLong(6))),
                )
                // Collapse consecutive calls with the same number and direction into one row.
                val last = out.lastOrNull()
                if (last != null && last.number == entry.number && last.isMissed == entry.isMissed &&
                    sameDay(last.date, entry.date)
                ) {
                    out[out.lastIndex] = last.copy(ids = last.ids + entry.ids, entries = last.entries + entry.entries)
                } else {
                    out += entry
                }
            }
        }
        return out
    }

    fun delete(context: Context, ids: List<Long>) {
        if (ids.isEmpty()) return
        context.contentResolver.delete(
            CallLog.Calls.CONTENT_URI,
            "${CallLog.Calls._ID} IN (${ids.joinToString(",")})",
            null,
        )
    }

    private fun sameDay(a: Long, b: Long): Boolean {
        val ca = Calendar.getInstance().apply { timeInMillis = a }
        val cb = Calendar.getInstance().apply { timeInMillis = b }
        return ca.get(Calendar.YEAR) == cb.get(Calendar.YEAR) && ca.get(Calendar.DAY_OF_YEAR) == cb.get(Calendar.DAY_OF_YEAR)
    }
}
