package io.github.smaugfm.monobudget.ynab

import io.github.oshai.kotlinlogging.KotlinLogging
import io.github.smaugfm.monobudget.common.model.BudgetBackend.YNAB
import io.github.smaugfm.monobudget.common.util.logError
import io.github.smaugfm.monobudget.common.util.makeJson
import io.github.smaugfm.monobudget.ynab.model.YnabAccount
import io.github.smaugfm.monobudget.ynab.model.YnabAccountResponse
import io.github.smaugfm.monobudget.ynab.model.YnabAccountsResponse
import io.github.smaugfm.monobudget.ynab.model.YnabBudgetDetailResponseShort
import io.github.smaugfm.monobudget.ynab.model.YnabBudgetDetailShort
import io.github.smaugfm.monobudget.ynab.model.YnabCategoriesResponse
import io.github.smaugfm.monobudget.ynab.model.YnabCategoryGroupWithCategories
import io.github.smaugfm.monobudget.ynab.model.YnabPayee
import io.github.smaugfm.monobudget.ynab.model.YnabPayeesResponse
import io.github.smaugfm.monobudget.ynab.model.YnabSaveTransaction
import io.github.smaugfm.monobudget.ynab.model.YnabSaveTransactionWrapper
import io.github.smaugfm.monobudget.ynab.model.YnabTransactionDetail
import io.github.smaugfm.monobudget.ynab.model.YnabTransactionsResponse
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.URLProtocol
import io.ktor.http.contentType
import io.ktor.http.path
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.util.url
import kotlinx.datetime.LocalDate
import org.koin.core.annotation.Single
import kotlin.reflect.KFunction

private val log = KotlinLogging.logger { }

@Single
@Suppress("TooManyFunctions")
class YnabApi(backend: YNAB) {
    private val token = backend.token
    private val budgetId = backend.ynabBudgetId

    private val json = makeJson(true)
    private val httpClient =
        HttpClient {
            install(ContentNegotiation) {
                json(json)
            }
        }

    private fun buildUrl(vararg path: String): String =
        url {
            protocol = URLProtocol.HTTPS
            host = "api.ynab.com"
            parameters.append("access_token", token)
            path("v1", *path)
        }

    private inline fun <reified T : Any> catching(
        method: KFunction<Any>,
        block: () -> T,
    ): T =
        logError("YNAB", log, method.name, block) {
            if (it.response.status.value == HttpStatusCode.TooManyRequests.value) {
                throw YnabRateLimitException()
            }
        }

    private inline fun <reified T : Any> catchingNoLogging(
        method: KFunction<Any>,
        block: () -> T,
    ): T =
        logError("YNAB", null, method.name, block) {
            if (it.response.status.value == HttpStatusCode.TooManyRequests.value) {
                throw YnabRateLimitException()
            }
        }

    suspend fun getBudget(budgetId: String = this.budgetId): YnabBudgetDetailShort =
        catching(this::getBudget) {
            httpClient.get(buildUrl("budgets", budgetId))
                .body<YnabBudgetDetailResponseShort>()
        }.data.budget

    suspend fun getAccount(
        accountId: String,
        budgetId: String = this.budgetId,
    ): YnabAccount =
        catching(this::getAccount) {
            val url = buildUrl("budgets", budgetId, "accounts", accountId)
            log.debug { "YNAB getAccount request URL: $url" }
            val response = httpClient.get(url)
            val rawBody = response.body<String>()
            log.debug { "Raw YNAB getAccount response: $rawBody" }
            json.decodeFromString<YnabAccountResponse>(rawBody).data.account
        }

    @Suppress("MemberVisibilityCanBePrivate", "unused")
    suspend fun getAccounts(budgetId: String = this.budgetId): List<YnabAccount> =
        catching(this::getAccounts) {
            httpClient.get(buildUrl("budgets", budgetId, "accounts"))
                .body<YnabAccountsResponse>()
        }.data.accounts

    suspend fun getPayees(budgetId: String = this.budgetId): List<YnabPayee> =
        catchingNoLogging(this::getPayees) {
            httpClient.get(buildUrl("budgets", budgetId, "payees"))
                .body<YnabPayeesResponse>()
        }.data.payees

    suspend fun getCategoryGroups(budgetId: String = this.budgetId): List<YnabCategoryGroupWithCategories> =
        catching(this::getCategoryGroups) {
            httpClient.get(buildUrl("budgets", budgetId, "categories"))
                .body<YnabCategoriesResponse>()
        }.data.categoryGroups

    suspend fun createTransaction(transaction: YnabSaveTransaction): YnabTransactionDetail =
        catching(this::createTransaction) {
            val response =
                httpClient.post(buildUrl("budgets", budgetId, "transactions")) {
                    contentType(ContentType.Application.Json)
                    setBody(YnabSaveTransactionWrapper(transaction))
                }
            YnabResponseParser.parseCreatedTransaction(json, response.body<String>())
        }

    suspend fun updateTransaction(
        transactionId: String,
        transaction: YnabSaveTransaction,
        budgetId: String = this.budgetId,
    ): YnabTransactionDetail =
        catching(this::updateTransaction) {
            val response =
                httpClient.put(
                    buildUrl(
                        "budgets",
                        budgetId,
                        "transactions",
                        transactionId,
                    ),
                ) {
                    contentType(ContentType.Application.Json)
                    setBody(YnabSaveTransactionWrapper(transaction))
                }
            YnabResponseParser.parseUpdatedTransaction(json, response.body<String>())
        }

    suspend fun getTransaction(
        transactionId: String,
        budgetId: String = this.budgetId,
    ): YnabTransactionDetail =
        catching(this::getTransaction) {
            val response =
                httpClient.get(
                    buildUrl(
                        "budgets",
                        budgetId,
                        "transactions",
                        transactionId,
                    ),
                )
            YnabResponseParser.parseTransaction(json, response.body<String>())
        }

    suspend fun getAccountTransactions(
        accountId: String,
        sinceDate: LocalDate,
        budgetId: String = this.budgetId,
    ): List<YnabTransactionDetail> =
        catching(this::getAccountTransactions) {
            val requestUrl =
                url {
                    protocol = URLProtocol.HTTPS
                    host = "api.ynab.com"
                    parameters.append("access_token", token)
                    parameters.append("since_date", sinceDate.toString())
                    path("v1", "budgets", budgetId, "accounts", accountId, "transactions")
                }
            httpClient.get(requestUrl)
                .body<YnabTransactionsResponse>()
        }.data.transactions

    suspend fun getDeltaTransactions(
        budgetId: String,
        lastKnowledgeOfServer: Int?,
    ): YnabDeltaTransactions =
        catching(this::getDeltaTransactions) {
            val requestUrl =
                url {
                    protocol = URLProtocol.HTTPS
                    host = "api.ynab.com"
                    parameters.append("access_token", token)
                    lastKnowledgeOfServer?.let {
                        parameters.append("last_knowledge_of_server", it.toString())
                    }
                    path("v1", "budgets", budgetId, "transactions")
                }
            val response = httpClient.get(requestUrl).body<YnabTransactionsResponse>()
            YnabDeltaTransactions(
                transactions = response.data.transactions,
                serverKnowledge =
                    response.data.serverKnowledge
                        ?: error("YNAB delta response missing server_knowledge for budget $budgetId"),
            )
        }
}

data class YnabDeltaTransactions(
    val transactions: List<YnabTransactionDetail>,
    val serverKnowledge: Int,
)
