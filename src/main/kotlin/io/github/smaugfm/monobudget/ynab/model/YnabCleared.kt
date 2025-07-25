package io.github.smaugfm.monobudget.ynab.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class YnabCleared {
    @SerialName("cleared")
    Cleared,

    @SerialName("uncleared")
    Uncleared,

    @SerialName("reconciled")
    Reconciled,
}
