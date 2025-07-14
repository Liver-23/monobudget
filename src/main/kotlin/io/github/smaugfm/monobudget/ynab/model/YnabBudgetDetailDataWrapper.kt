package io.github.smaugfm.monobudget.ynab.model

import kotlinx.serialization.Serializable

@Serializable
data class YnabBudgetDetailDataWrapper(
    val budget: YnabBudgetDetailShort,
)