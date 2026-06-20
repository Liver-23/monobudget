package io.github.smaugfm.monobudget.ynab

import com.elbekd.bot.types.InlineKeyboardMarkup
import io.github.smaugfm.monobudget.common.category.CategoryService
import io.github.smaugfm.monobudget.common.model.callback.ActionCallbackType
import io.github.smaugfm.monobudget.common.model.callback.PressedButtons
import io.github.smaugfm.monobudget.common.model.callback.TransactionUpdateType
import io.github.smaugfm.monobudget.common.model.financial.Amount
import io.github.smaugfm.monobudget.common.model.financial.StatementItem
import io.github.smaugfm.monobudget.common.transaction.TransactionMessageFormatter
import io.github.smaugfm.monobudget.common.util.MCCRegistry
import io.github.smaugfm.monobudget.common.util.replaceNewLines
import io.github.smaugfm.monobudget.ynab.model.YnabCleared
import io.github.smaugfm.monobudget.ynab.model.YnabTransactionDetail
import org.koin.core.annotation.Single
import java.util.Currency

@Single
class YnabTransactionMessageFormatter(
    private val categoryService: CategoryService,
) : TransactionMessageFormatter<YnabTransactionDetail>() {
    override suspend fun formatHTMLStatementMessage(
        statementItem: StatementItem,
        accountCurrency: Currency,
        transaction: YnabTransactionDetail,
    ): String {
        with(statementItem) {
            val category = categoryService.budgetedCategoryById(transaction.categoryId)
            val ynabAmount = Amount.fromYnabAmount(transaction.amount, accountCurrency)

            return formatHTMLStatementMessage(
                "YNAB",
                (description ?: "").replaceNewLines(),
                (MCCRegistry.map[mcc]?.fullDescription ?: "Невідомий MCC") + " ($mcc)",
                ynabAmount.format(),
                category,
                transaction.payeeName ?: "",
                transaction.id,
                accountName = bankAccounts.getAccountAlias(accountId),
                accountBalance = balanceAfterTransaction?.format(),
            )
        }
    }

    override fun shouldNotify(transaction: YnabTransactionDetail): Boolean =
        transaction.categoryId == null || transaction.cleared == YnabCleared.Uncleared

    override fun getReplyKeyboardPressedButtons(
        transaction: YnabTransactionDetail,
        callbackType: TransactionUpdateType?,
    ): PressedButtons {
        val pressed = PressedButtons(callbackType)

        return pressed
    }

    override fun buildReplyKeyboard(
        transaction: YnabTransactionDetail,
        pressed: PressedButtons,
    ) = InlineKeyboardMarkup(
        listOf(
            listOf(
                YnabApprovalButton.button(transaction, pressed),
                ActionCallbackType.ChooseCategory.button(pressed),
                TransactionUpdateType.MakePayee.button(pressed),
            ),
        ),
    )
}
