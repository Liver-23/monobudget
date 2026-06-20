package io.github.smaugfm.monobudget.ynab

import com.elbekd.bot.types.InlineKeyboardMarkup
import io.github.smaugfm.monobudget.common.category.CategoryService
import io.github.smaugfm.monobudget.common.model.callback.ActionCallbackType
import io.github.smaugfm.monobudget.common.model.callback.PressedButtons
import io.github.smaugfm.monobudget.common.model.callback.TransactionUpdateType
import io.github.smaugfm.monobudget.common.model.financial.Amount
import io.github.smaugfm.monobudget.common.model.settings.YnabBudgetWatcherSettings
import io.github.smaugfm.monobudget.common.model.telegram.MessageWithReplyKeyboard
import io.github.smaugfm.monobudget.common.transaction.TransactionMessageFormatter.Companion.formatBudget
import io.github.smaugfm.monobudget.ynab.model.YnabCleared
import io.github.smaugfm.monobudget.ynab.model.YnabTransactionDetail
import org.koin.core.annotation.Single
import java.util.Currency

data class YnabWatcherMessageContent(
    val budgetName: String,
    val accountName: String,
    val accountBalance: String,
    val description: String,
    val amount: String,
    val category: CategoryService.BudgetedCategory?,
    val payee: String,
    val transactionId: String,
)

fun formatWatcherHtml(content: YnabWatcherMessageContent): String =
    with(content) {
        buildString {
            append(YnabWatcherMessage.header(budgetName))
            append('\n')
            append(YnabWatcherMessage.accountLine(accountName))
            append('\n')
            append(YnabWatcherMessage.balanceLine(accountBalance))
            append('\n')
            append("\uD83D\uDCB3 <b>$description</b>\n")
            append("      <u>$amount</u>\n")
            append("      <code>Category: ${category?.categoryName ?: ""}</code>\n")
            append("      <code>Payee:    $payee</code>\n")
            category?.budget?.let {
                append('\n')
                formatBudget(it, this)
            }
            append("\n\n")
            append("<pre>$transactionId</pre>")
        }
    }

@Single
class YnabWatcherMessageFormatter(
    private val budgetDataService: YnabBudgetDataService,
    private val api: YnabApi,
) {
    suspend fun format(
        settings: YnabBudgetWatcherSettings,
        transaction: YnabTransactionDetail,
        currency: Currency,
    ): MessageWithReplyKeyboard {
        val account = api.getAccount(transaction.accountId, settings.budgetId)
        val accountBalance = Amount.fromYnabAmount(account.balance, currency).format()
        val category = budgetDataService.budgetedCategoryById(settings.budgetId, transaction.categoryId)
        val description = transaction.payeeName ?: transaction.memo ?: "Transaction"
        val amount = Amount.fromYnabAmount(transaction.amount, currency).format()

        return MessageWithReplyKeyboard(
            message =
                formatWatcherHtml(
                    YnabWatcherMessageContent(
                        budgetName = settings.name,
                        accountName = transaction.accountName,
                        accountBalance = accountBalance,
                        description = description,
                        amount = amount,
                        category = category,
                        payee = transaction.payeeName ?: "",
                        transactionId = transaction.id,
                    ),
                ),
            markup = replyKeyboard(transaction),
            notifyTelegramApp = shouldNotify(transaction),
        )
    }

    suspend fun formatUpdated(
        settings: YnabBudgetWatcherSettings,
        transaction: YnabTransactionDetail,
    ): String {
        val currency = budgetDataService.budgetCurrency(settings.budgetId)
        val account = api.getAccount(transaction.accountId, settings.budgetId)
        val accountBalance = Amount.fromYnabAmount(account.balance, currency).format()
        val category = budgetDataService.budgetedCategoryById(settings.budgetId, transaction.categoryId)
        val description = transaction.payeeName ?: transaction.memo ?: "Transaction"
        val amount = Amount.fromYnabAmount(transaction.amount, currency).format()

        return formatWatcherHtml(
            YnabWatcherMessageContent(
                budgetName = settings.name,
                accountName = transaction.accountName,
                accountBalance = accountBalance,
                description = description,
                amount = amount,
                category = category,
                payee = transaction.payeeName ?: "",
                transactionId = transaction.id,
            ),
        )
    }

    fun shouldNotify(transaction: YnabTransactionDetail): Boolean =
        transaction.categoryId == null || transaction.cleared == YnabCleared.Uncleared

    fun replyKeyboard(transaction: YnabTransactionDetail): InlineKeyboardMarkup {
        val pressed = PressedButtons(null)
        return InlineKeyboardMarkup(
            listOf(
                listOf(
                    YnabApprovalButton.button(transaction, pressed),
                    ActionCallbackType.ChooseCategory.button(pressed),
                    TransactionUpdateType.MakePayee.button(pressed),
                ),
            ),
        )
    }
}
