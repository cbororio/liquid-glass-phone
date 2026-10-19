package com.tonny.dial.data

import android.content.ContentValues
import android.content.Context
import android.provider.BlockedNumberContract
import android.provider.BlockedNumberContract.BlockedNumbers

/**
 * The system block list. Only the default dialer/SMS app may read or write it; the platform
 * then rejects calls from these numbers before they ever reach us.
 */
object BlockedRepository {

    fun available(context: Context): Boolean =
        runCatching { BlockedNumberContract.canCurrentUserBlockNumbers(context) }.getOrDefault(false)

    /** Every blocked number, as it was originally entered, newest first. */
    fun load(context: Context): List<String> {
        val out = mutableListOf<String>()
        context.contentResolver.query(
            BlockedNumbers.CONTENT_URI,
            arrayOf(BlockedNumbers.COLUMN_ORIGINAL_NUMBER),
            null, null, BlockedNumbers.COLUMN_ID + " DESC",
        )?.use { c ->
            while (c.moveToNext()) c.getString(0)?.takeIf { it.isNotBlank() }?.let { out += it }
        }
        return out
    }

    fun block(context: Context, number: String) {
        val values = ContentValues().apply { put(BlockedNumbers.COLUMN_ORIGINAL_NUMBER, number) }
        context.contentResolver.insert(BlockedNumbers.CONTENT_URI, values)
    }

    fun unblock(context: Context, number: String) {
        // Delete by the stored text so non-numeric sender names are removed too.
        val removed = context.contentResolver.delete(
            BlockedNumbers.CONTENT_URI, "${BlockedNumbers.COLUMN_ORIGINAL_NUMBER} = ?", arrayOf(number),
        )
        if (removed == 0) BlockedNumberContract.unblock(context, number)
    }

    fun key(number: String): String = ContactsRepository.normalize(number).filter { it.isDigit() }.takeLast(9)
}
