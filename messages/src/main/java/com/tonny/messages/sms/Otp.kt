package com.tonny.messages.sms

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PersistableBundle
import android.widget.Toast

/** Finds one-time codes in incoming texts, like "Your OTP is 482913" or "G-123456 is your code". */
object OtpDetector {

    private val keywords = Regex(
        "\\b(?:otp|one[- ]?time(?: pin| password| code)?|code|passcode|password|pin|verification|token)\\b",
        RegexOption.IGNORE_CASE,
    )

    // 4–8 digits, or two groups of 3–4 split by a space or dash ("123 456", "1234-5678").
    // Lookarounds skip amounts (1,000.50), dates (10/10/26), times (10:15) and reference ids
    // mixed with letters (UJALI9K2PU), while still allowing "G-123456".
    private val candidate = Regex(
        "(?<![\\w.,:/+])(?<!\\d-)(\\d{3,4}[ -]\\d{3,4}|\\d{4,8})(?![\\w]|[.,:/]\\d)",
    )

    /** How close (in characters) the number must be to a word like "code" or "OTP". */
    private const val WINDOW = 30

    fun find(body: String): String? {
        val near = keywords.findAll(body).map { it.range }.toList()
        if (near.isEmpty()) return null
        return candidate.findAll(body).firstOrNull { m ->
            val digits = m.value.filter(Char::isDigit)
            digits.length in 4..8 && !looksLikeYear(digits) && !looksLikePhone(digits) &&
                near.any { k -> kotlin.math.abs(m.range.first - k.last) <= WINDOW || kotlin.math.abs(k.first - m.range.last) <= WINDOW }
        }?.value?.filter(Char::isDigit)
    }

    private fun looksLikeYear(digits: String) = digits.length == 4 && digits.toInt() in 1990..2099

    // Fragments of Kenyan numbers (07…, 01…, 254…) in "call us" lines.
    private fun looksLikePhone(digits: String) = digits.length >= 7 && (digits.startsWith("07") || digits.startsWith("01") || digits.startsWith("254"))
}

/** "Copy code" button on a code notification. */
class CopyCodeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val code = intent.getStringExtra(EXTRA_CODE) ?: return
        val clip = ClipData.newPlainText("Code", code).apply {
            // Keeps the code out of clipboard previews and keyboard suggestions on Android 13+.
            description.extras = PersistableBundle().apply {
                putBoolean(if (Build.VERSION.SDK_INT >= 33) ClipDescription.EXTRA_IS_SENSITIVE else "android.content.extra.IS_SENSITIVE", true)
            }
        }
        context.getSystemService(ClipboardManager::class.java).setPrimaryClip(clip)
        Toast.makeText(context, "Code $code copied", Toast.LENGTH_SHORT).show()
        context.getSystemService(NotificationManager::class.java).cancel(intent.getIntExtra(EXTRA_NOTIFICATION, 0))
    }

    companion object {
        const val EXTRA_CODE = "code"
        const val EXTRA_NOTIFICATION = "notification"
    }
}
