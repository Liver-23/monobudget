package io.github.smaugfm.monobudget.ynab.model

import kotlinx.serialization.Serializable

@Serializable
data class YnabErrorResponse(
    val error: YnabErrorDetail? = null,
)

@Serializable
data class YnabErrorDetail(
    val id: String? = null,
    val name: String? = null,
    val detail: String,
)
