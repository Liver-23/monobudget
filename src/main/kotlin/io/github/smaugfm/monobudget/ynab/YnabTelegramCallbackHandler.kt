package io.github.smaugfm.monobudget.ynab

import com.elbekd.bot.types.InlineKeyboardMarkup
import com.elbekd.bot.types.Message
import io.github.smaugfm.monobudget.common.category.CategoryService
import io.github.smaugfm.monobudget.common.model.BudgetBackend
import io.github.smaugfm.monobudget.common.model.callback.TransactionUpdateType
import io.github.smaugfm.monobudget.common.model.settings.Settings
import io.github.smaugfm.monobudget.common.notify.TelegramCallbackHandler
import io.github.smaugfm.monobudget.common.transaction.TransactionMessageFormatter.Companion.extractAccountBalance
import io.github.smaugfm.monobudget.common.transaction.TransactionMessageFormatter.Companion.extractAccountName
import io.github.smaugfm.monobudget.common.transaction.TransactionMessageFormatter.Companion.extractFromOldMessage
import io.github.smaugfm.monobudget.common.transaction.TransactionMessageFormatter.Companion.formatHTMLStatementMessage
import io.github.smaugfm.monobudget.ynab.model.YnabTransactionDetail
import org.koin.core.annotation.Single

@Single
class YnabTelegramCallbackHandler(
    private val api: YnabApi,
    private val budgetDataService: YnabBudgetDataService,
    private val backend: BudgetBackend.YNAB,
    private val settings: Settings,
    private val watcherMessageFormatter: YnabWatcherMessageFormatter,
) : TelegramCallbackHandler<YnabTransactionDetail>() {
    override suspend fun updateTransaction(
        callbackType: TransactionUpdateType,
        message: Message,
    ): YnabTransactionDetail {
        val budgetId = watcherBudgetId(message) ?: backend.ynabBudgetId
        val transactionDetail = api.getTransaction(callbackType.transactionId, budgetId)
        val saveTransaction = transactionDetail.toSaveTransaction()

        val newTransaction =
            when (callbackType) {
                is TransactionUpdateType.Uncategorize ->
                    saveTransaction.copy(categoryId = null, payeeName = null, payeeId = null)

                is TransactionUpdateType.Unapprove ->
                    saveTransaction.copy(approved = false)

                is TransactionUpdateType.Approve ->
                    saveTransaction.copy(approved = true)

                is TransactionUpdateType.MakePayee ->
                    saveTransaction.copy(
                        payeeId = null,
                        payeeName = callbackType.payee,
                    )

                is TransactionUpdateType.UpdateCategory ->
                    saveTransaction.copy(
                        categoryId = callbackType.categoryId,
                    )
            }

        return api.updateTransaction(transactionDetail.id, newTransaction, budgetId)
    }

    override suspend fun updateHTMLStatementMessage(
        updatedTransaction: YnabTransactionDetail,
        oldMessage: Message,
    ): String {
        val watcherSettings = settings.ynabBudgetWatcher
        if (watcherSettings != null && YnabWatcherMessage.isWatcherMessage(oldMessage)) {
            return watcherMessageFormatter.formatUpdated(watcherSettings, updatedTransaction)
        }

        val (description, mcc, currency) = extractFromOldMessage(oldMessage)
        val category = categoryService.budgetedCategoryById(updatedTransaction.categoryId)

        return formatHTMLStatementMessage(
            "YNAB",
            description,
            mcc,
            currency,
            category,
            updatedTransaction.payeeName ?: "",
            updatedTransaction.id,
            accountName = extractAccountName(oldMessage) ?: updatedTransaction.accountName,
            accountBalance = extractAccountBalance(oldMessage),
        )
    }

    override suspend fun categoryGroupsForMessage(message: Message): List<CategoryService.CategoryGroup> {
        val budgetId = watcherBudgetId(message) ?: return super.categoryGroupsForMessage(message)
        return budgetDataService.categoryGroups(budgetId)
    }

    override suspend fun transactionForReplyKeyboard(
        message: Message,
        transactionId: String,
    ): YnabTransactionDetail {
        val budgetId = watcherBudgetId(message) ?: backend.ynabBudgetId
        return api.getTransaction(transactionId, budgetId)
    }

    override fun replyKeyboardForTransaction(
        message: Message,
        transaction: YnabTransactionDetail,
    ): InlineKeyboardMarkup =
        if (YnabWatcherMessage.isWatcherMessage(message)) {
            watcherMessageFormatter.replyKeyboard(transaction)
        } else {
            super.replyKeyboardForTransaction(message, transaction)
        }

    private fun watcherBudgetId(message: Message): String? =
        settings.ynabBudgetWatcher
            ?.budgetId
            ?.takeIf { YnabWatcherMessage.isWatcherMessage(message) }
}
