package com.tonny.messages.data

/**
 * Spots betting and promo blasts ("Cheza Aviator… STOP*456*9*5#"). Tuned on a real inbox so banks,
 * M-PESA and service alerts aren't caught: a text needs an opt-out code or several promo words,
 * and at least one actual gambling word.
 */
object SpamFilter {

    private val optOut = Regex(
        "\\bSTOP\\b[^\\n]{0,15}?\\*\\d{2,4}(?:\\*\\d+)*#|\\bSTOP\\s+(?:to\\s+)?\\d{4,6}\\b|\\bto (?:opt[- ]?out|unsubscribe)\\b",
        RegexOption.IGNORE_CASE,
    )
    private val promoWords = Regex(
        "\\b(aviator|freebets?|jackpot|casino|multibet|odds|bet|bets|betting|stake|spin|shinda|cheza|bonus|cashback|win up to|mamilioni|millioni?)\\b",
        RegexOption.IGNORE_CASE,
    )
    private val gamblingWords = Regex(
        "\\b(aviator|freebets?|jackpot|casino|multibet|odds|bet|bets|betting|stake|spin)\\b",
        RegexOption.IGNORE_CASE,
    )

    fun looksLikePromo(body: String): Boolean {
        if (!gamblingWords.containsMatchIn(body)) return false
        val words = promoWords.findAll(body).map { it.value.lowercase() }.toSet().size.coerceAtMost(3)
        val score = words + if (optOut.containsMatchIn(body)) 2 else 0
        return score >= 3
    }
}
