package io.github.smaugfm.monobudget.ynab

import com.elbekd.bot.types.InlineKeyboardMarkup
import io.github.smaugfm.monobudget.common.category.CategoryService
import io.github.smaugfm.monobudget.common.model.callback.PressedButtons
import io.github.smaugfm.monobudget.common.model.callback.TransactionUpdateType
import io.github.smaugfm.monobudget.common.model.financial.StatementItem
import io.github.smaugfm.monobudget.common.transaction.TransactionMessageFormatter
import io.github.smaugfm.monobudget.common.util.MCCRegistry
import io.github.smaugfm.monobudget.common.util.replaceNewLines
import io.github.smaugfm.monobudget.ynab.model.YnabCleared
import io.github.smaugfm.monobudget.ynab.model.YnabTransactionDetail
import io.github.smaugfm.monobudget.common.model.financial.Amount
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

        if (transaction.categoryName.isNullOrEmpty()) {
            pressed(TransactionUpdateType.Uncategorize::class)
        }
        if (transaction.cleared == YnabCleared.Uncleared) {
            pressed(TransactionUpdateType.Unapprove::class)
        }

        return pressed
    }

    override fun getReplyKeyboard(pressed: PressedButtons) =
        InlineKeyboardMarkup(
            listOf(
                listOf(
                    TransactionUpdateType.Unapprove.button(pressed),
                    TransactionUpdateType.Uncategorize.button(pressed),
                    TransactionUpdateType.MakePayee.button(pressed),
                ),
            ),
        )
}
