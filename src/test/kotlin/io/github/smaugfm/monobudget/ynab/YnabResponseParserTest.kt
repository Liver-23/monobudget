package io.github.smaugfm.monobudget.ynab

import assertk.assertFailure
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import assertk.assertions.prop
import io.github.smaugfm.monobudget.common.exception.BudgetBackendException
import io.github.smaugfm.monobudget.common.util.makeJson
import org.junit.jupiter.api.Test

class YnabResponseParserTest {
    private val json = makeJson(true)

    @Test
    fun `throws BudgetBackendException for YNAB error response`() {
        val body =
            """
            {"error":{"id":"123","name":"bad_request","detail":"Account does not exist."}}
            """.trimIndent()

        assertFailure {
            YnabResponseParser.parseCreatedTransaction(json, body)
        }.isInstanceOf(BudgetBackendException::class)
            .prop(BudgetBackendException::userMessage)
            .isEqualTo("Account does not exist.")
    }

    @Test
    fun `throws BudgetBackendException for YNAB error response on get transaction`() {
        val body =
            """
            {"error":{"id":"404","name":"not_found","detail":"Transaction not found."}}
            """.trimIndent()

        assertFailure {
            YnabResponseParser.parseTransaction(json, body)
        }.isInstanceOf(BudgetBackendException::class)
            .prop(BudgetBackendException::userMessage)
            .isEqualTo("Transaction not found.")
    }
}
