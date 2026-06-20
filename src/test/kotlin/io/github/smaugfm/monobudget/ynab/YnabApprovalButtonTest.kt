package io.github.smaugfm.monobudget.ynab

import assertk.assertThat
import assertk.assertions.isEqualTo
import io.github.smaugfm.monobudget.common.model.callback.PressedButtons
import io.github.smaugfm.monobudget.ynab.model.YnabCleared
import io.github.smaugfm.monobudget.ynab.model.YnabTransactionDetail
import kotlinx.datetime.LocalDate
import org.junit.jupiter.api.Test

class YnabApprovalButtonTest {
    @Test
    fun `shows approve when transaction is unapproved`() {
        val button =
            YnabApprovalButton.button(
                transaction = transaction(approved = false),
                pressed = PressedButtons(null),
            )

        assertThat(button.text).isEqualTo("✅ Approve")
        assertThat(button.callbackData).isEqualTo("Approve")
    }

    @Test
    fun `shows unapprove when transaction is approved`() {
        val button =
            YnabApprovalButton.button(
                transaction = transaction(approved = true),
                pressed = PressedButtons(null),
            )

        assertThat(button.text).isEqualTo("🚫 Unapprove")
        assertThat(button.callbackData).isEqualTo("Unapprove")
    }

    private fun transaction(approved: Boolean) =
        YnabTransactionDetail(
            id = "tx-id",
            date = LocalDate(2026, 6, 20),
            amount = -1000,
            memo = null,
            cleared = YnabCleared.Cleared,
            approved = approved,
            flagColor = null,
            accountId = "account-id",
            payeeId = null,
            categoryId = "category-id",
            transferAccountId = null,
            transferTransactionId = null,
            matchedTransactionId = null,
            importId = null,
            deleted = false,
            accountName = "Chequing",
            payeeName = "Store",
            categoryName = "Goods",
            subtransactions = emptyList(),
        )
}
