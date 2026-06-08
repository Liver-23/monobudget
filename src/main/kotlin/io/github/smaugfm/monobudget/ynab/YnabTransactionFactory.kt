package io.github.smaugfm.monobudget.ynab

import io.github.oshai.kotlinlogging.KotlinLogging
import io.github.smaugfm.monobudget.common.account.BankAccountService
import io.github.smaugfm.monobudget.common.account.MaybeTransfer
import io.github.smaugfm.monobudget.common.model.BudgetBackend.YNAB
import io.github.smaugfm.monobudget.common.model.financial.StatementItem
import io.github.smaugfm.monobudget.common.statement.lifecycle.StatementProcessingScopeComponent
import io.github.smaugfm.monobudget.common.transaction.TransactionFactory
import io.github.smaugfm.monobudget.ynab.model.YnabCleared
import io.github.smaugfm.monobudget.ynab.model.YnabSaveTransaction
import io.github.smaugfm.monobudget.ynab.model.YnabTransactionDetail
import org.koin.core.annotation.Scope
import org.koin.core.annotation.Scoped
import java.util.concurrent.ConcurrentHashMap

private val log = KotlinLogging.logger {}

@Scoped
@Scope(StatementProcessingScopeComponent::class)
class YnabTransactionFactory(
    private val api: YnabApi,
    private val bankAccounts: BankAccountService,
    private val backend: YNAB,
    private val matcher: YnabTransactionMatcher,
) : TransactionFactory<YnabTransactionDetail, YnabSaveTransaction>() {
    private val transferPayeeIdsCache = ConcurrentHashMap<String, String>()

    override suspend fun create(maybeTransfer: MaybeTransfer<YnabTransactionDetail>) =
        when (maybeTransfer) {
            is MaybeTransfer.Transfer ->
                processTransfer(
                    maybeTransfer.statement,
                    maybeTransfer.processed(),
                )

            is MaybeTransfer.NotTransfer -> maybeTransfer.consume(::processSingle)
        }

    private suspend fun processTransfer(
        statement: StatementItem,
        existingTransaction: YnabTransactionDetail,
    ): YnabTransactionDetail {
        log.debug {
            "Processing transfer transaction: $statement. " +
                "Existing YnabTransactionDetail: $existingTransaction"
        }

        val ynabAccountId = bankAccounts.getBudgetAccountId(statement.accountId)!!
        val transferPayeeId =
            transferPayeeIdsCache.getOrPut(ynabAccountId) {
                api.getAccount(ynabAccountId).transferPayeeId
            }

        val existingTransactionUpdated =
            api
                .updateTransaction(
                    existingTransaction.id,
                    existingTransaction
                        .toSaveTransaction()
                        .copy(payeeId = transferPayeeId, memo = "Переказ між рахунками"),
                )

        val transfer = api.getTransaction(existingTransactionUpdated.transferTransactionId!!)

        return api.updateTransaction(
            transfer.id,
            transfer.toSaveTransaction().copy(cleared = YnabCleared.Cleared),
        )
    }

    private suspend fun processSingle(statement: StatementItem): YnabTransactionDetail {
        log.debug { "Processing transaction: $statement" }

        val desired = newTransactionFactory.create(statement)

        if (!backend.matchExistingTransactions) {
            return api.createTransaction(desired)
        }

        matcher.findExisting(statement, desired)?.let { existing ->
            return api.updateTransaction(
                existing.id,
                matcher.mergeForUpdate(existing, desired, statement.id),
            )
        }

        return api.createTransaction(
            desired.copy(importId = YnabMonoImportId.forStatement(statement.id)),
        )
    }
}
