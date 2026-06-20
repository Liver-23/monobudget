package io.github.smaugfm.monobudget.ynab

import com.elbekd.bot.types.Message
import io.github.smaugfm.monobudget.common.transaction.TransactionMessageFormatter.Companion.extractAccountName

object YnabWatcherMessage {
    const val HEADER_PREFIX = "Нова транзакція імпортована в YNAB"

    fun isWatcherMessage(message: Message): Boolean = message.text?.contains(HEADER_PREFIX) == true

    fun header(budgetName: String): String = "$HEADER_PREFIX ($budgetName)"

    fun accountLine(accountName: String): String = "      <code>Account: $accountName</code>"

    fun balanceLine(accountBalance: String): String = "      <code>Balance: $accountBalance</code>"

    fun extractAccountNameFromWatcherMessage(message: Message): String? = extractAccountName(message)
}
