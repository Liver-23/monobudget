package io.github.smaugfm.monobudget.ynab.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class YnabAccount(
    val id: String,
    val name: String,
    val type: YnabAccountType,
    @SerialName("on_budget") val onBudget: Boolean,
    val closed: Boolean,
    val note: String?,
    val balance: Long,
    @SerialName("cleared_balance") val clearedBalance: Long,
    @SerialName("uncleared_balance") val unclearedBalance: Long,
    @SerialName("transfer_payee_id") val transferPayeeId: String,
    @SerialName("direct_import_linked") val directImportLinked: Boolean,
    @SerialName("direct_import_in_error") val directImportInError: Boolean,
    @SerialName("last_reconciled_at") val lastReconciledAt: String? = null,
    @SerialName("debt_original_balance") val debtOriginalBalance: Long? = null,
    @SerialName("debt_interest_rates") val debtInterestRates: Map<String, Int>? = null,
    @SerialName("debt_minimum_payments") val debtMinimumPayments: Map<String, Long>? = null,
    @SerialName("debt_escrow_amounts") val debtEscrowAmounts: Map<String, Long>? = null,
    val deleted: Boolean,
)
