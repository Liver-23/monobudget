package io.github.smaugfm.monobudget.ynab

import io.github.smaugfm.monobudget.common.exception.BudgetBackendException
import io.github.smaugfm.monobudget.ynab.model.YnabErrorResponse
import io.github.smaugfm.monobudget.ynab.model.YnabSaveTransactionResponse
import io.github.smaugfm.monobudget.ynab.model.YnabTransactionDetail
import io.github.smaugfm.monobudget.ynab.model.YnabTransactionResponse
import kotlinx.serialization.json.Json

internal object YnabResponseParser {
    fun parseCreatedTransaction(
        json: Json,
        rawBody: String,
    ): YnabTransactionDetail {
        parseError(json, rawBody)?.let { throw it }
        return json.decodeFromString<YnabSaveTransactionResponse>(rawBody).data.transaction
    }

    fun parseUpdatedTransaction(
        json: Json,
        rawBody: String,
    ): YnabTransactionDetail {
        parseError(json, rawBody)?.let { throw it }
        return json.decodeFromString<YnabTransactionResponse>(rawBody).data.transaction
    }

    private fun parseError(
        json: Json,
        rawBody: String,
    ): BudgetBackendException? {
        val error =
            runCatching {
                json.decodeFromString<YnabErrorResponse>(rawBody).error
            }.getOrNull()
        return error?.detail?.let { detail ->
            BudgetBackendException(IllegalStateException(detail), detail)
        }
    }
}
