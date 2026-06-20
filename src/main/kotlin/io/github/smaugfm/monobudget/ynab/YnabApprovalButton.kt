package io.github.smaugfm.monobudget.ynab

import com.elbekd.bot.types.InlineKeyboardButton
import io.github.smaugfm.monobudget.common.model.callback.PressedButtons
import io.github.smaugfm.monobudget.common.model.callback.TransactionUpdateType
import io.github.smaugfm.monobudget.ynab.model.YnabTransactionDetail

internal object YnabApprovalButton {
    fun button(
        transaction: YnabTransactionDetail,
        pressed: PressedButtons,
    ): InlineKeyboardButton =
        if (transaction.approved) {
            TransactionUpdateType.Unapprove.button(pressed)
        } else {
            TransactionUpdateType.Approve.button(pressed)
        }
}
