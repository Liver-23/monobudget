package io.github.smaugfm.monobudget.ynab

import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.doesNotContain
import io.github.smaugfm.monobudget.common.category.CategoryService
import io.github.smaugfm.monobudget.common.model.financial.Amount
import org.junit.jupiter.api.Test
import java.util.Currency

class YnabWatcherMessageFormatterTest {
    @Test
    fun `formats watcher message without internal marker or direct import`() {
        val message =
            formatWatcherHtml(
                YnabWatcherMessageContent(
                    budgetName = "Canada budget",
                    accountName = "BMO Chequing",
                    accountBalance = "1407.24 CAD",
                    description = "Tennis Giant",
                    amount = "-10.00 CAD",
                    category =
                        CategoryService.BudgetedCategory(
                            "Goods",
                            CategoryService.BudgetedCategory.CategoryBudget(
                                Amount.fromYnabAmount(-117_000, Currency.getInstance("CAD")),
                                Amount.fromYnabAmount(60_000, Currency.getInstance("CAD")),
                            ),
                        ),
                    payee = "Tennis Giant",
                    transactionId = "a844c366-1775-4da4-92bd-f32a3d2d1397",
                ),
            )

        assertThat(message).contains("Account: BMO Chequing")
        assertThat(message).contains("Balance: 1407.24 CAD")
        assertThat(message).contains("Tennis Giant")
        assertThat(message).contains("Залишок")
        assertThat(message).doesNotContain("YNAB_WATCHER")
        assertThat(message).doesNotContain("direct import")
    }
}
