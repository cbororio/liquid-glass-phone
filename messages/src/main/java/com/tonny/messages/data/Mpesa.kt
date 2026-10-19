package com.tonny.messages.data

/** Money in, money out, or a payment that didn't go through. */
enum class MpesaFlow { In, Out, Failed }

/**
 * One M-PESA transaction read from a confirmation text.
 * [headline] reads like "Received KSH 400", [detail] like "from Benard Langat".
 */
data class MpesaTxn(val flow: MpesaFlow, val headline: String, val detail: String?, val balance: String?)

/** Reads Safaricom M-PESA confirmation texts. Patterns were checked against real inbox messages. */
object Mpesa {

    fun isMpesa(address: String): Boolean = address.replace("-", "").equals("MPESA", ignoreCase = true)

    private const val AMT = "(?:Kshs?|KSH|KES)\\s?([\\d,]+(?:\\.\\d{1,2})?)"
    private val opts = setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
    private fun rx(p: String) = Regex(p, opts)

    private class Rule(val flow: MpesaFlow, val verb: String?, val prep: String?, val regex: Regex)

    // Order matters: specific templates first, the generic "sent/paid to" last.
    private val rules = listOf(
        Rule(MpesaFlow.In, "Reversal", null, rx("Reversal of transaction .*?$AMT has been credited")),
        Rule(MpesaFlow.In, "Received", "from", rx("received $AMT from (.+?)(?:\\s+on \\d|\\s+in \\w+ via|\\.?\\s+New M-?PESA|\\.\\s)")),
        Rule(MpesaFlow.In, "Received", "from", rx("transfered $AMT from your (.+?) account")),
        Rule(MpesaFlow.In, "Deposited", "at", rx("Give $AMT cash to (.+?)\\s+New M-?PESA")),
        Rule(MpesaFlow.Out, "Withdrew", "at", rx("Withdraw $AMT from (?:\\d+ - )?(.+?)\\s*New M-?PESA")),
        Rule(MpesaFlow.Out, "Airtime", null, rx("bought $AMT of airtime")),
        Rule(MpesaFlow.Out, "Fuliza repaid", null, rx("$AMT from your M-?PESA has been used to (?:fully |partially )?pay")),
        Rule(MpesaFlow.Out, "Sent", "to", rx("You have sent $AMT to (.+?)\\s+on \\d")),
        Rule(MpesaFlow.Out, null, "to", rx("$AMT (sent|paid|transfered) to (.+?)(?:\\s+for account \\S+)?(?:\\.?\\s+on \\d|\\.\\s|\\s+New M-?PESA)")),
    )
    private val failed = rx("^(?:Failed|Transaction failed)\\b")
    private val failedDetail = rx("pay(?:ment of)? $AMT to (.+?)(?:\\.|\\s+Your|$)")
    private val balance = rx("New M-?PESA balance is $AMT")

    fun parse(body: String): MpesaTxn? {
        val text = body.trim()
        if (failed.containsMatchIn(text)) {
            val m = failedDetail.find(text)
            return if (m == null) MpesaTxn(MpesaFlow.Failed, "Transaction failed", null, null)
            else MpesaTxn(MpesaFlow.Failed, "Failed ${amount(m.groupValues[1])}", "to ${tidy(m.groupValues[2])}", null)
        }
        for (rule in rules) {
            val g = rule.regex.find(text)?.groupValues ?: continue
            val (amt, verb, party) = if (rule.verb == null) {
                Triple(g[1], when (g[2].lowercase()) { "paid" -> "Paid"; "transfered" -> "Transferred"; else -> "Sent" }, g[3])
            } else Triple(g[1], rule.verb, g.getOrNull(2))
            val detail = if (rule.prep != null && !party.isNullOrBlank()) "${rule.prep} ${tidy(party)}" else null
            val bal = balance.find(text)?.groupValues?.get(1)?.let(::amount)
            return MpesaTxn(rule.flow, "$verb ${amount(amt)}", detail, bal)
        }
        return null
    }

    /** "1,000.00" -> "KSH 1,000"; keeps real cents ("KSH 36.57"). */
    private fun amount(raw: String) = "KSH " + raw.removeSuffix(".00")

    /** Cleans a payee: drops account numbers and masked phones, and title-cases ALL CAPS names. */
    private fun tidy(raw: String): String {
        var p = raw.replace(Regex("\\s+for account\\b.*$", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\s+\\d+\\*+\\d+$"), "")
            .replace(Regex("\\s+"), " ")
            .trim(' ', '.', '-')
            .replace(Regex("(\\s\\d{5,})+$"), "")
        if (p == p.uppercase() || p == p.lowercase()) {
            p = p.split(' ').joinToString(" ") { w ->
                if (w.length <= 3 || w.any(Char::isDigit)) w else w.lowercase().replaceFirstChar(Char::uppercase)
            }
        }
        return if (p.length <= 32) p else p.take(31).trimEnd() + "…"
    }
}
