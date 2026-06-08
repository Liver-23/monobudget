package io.github.smaugfm.monobudget.ynab

import io.github.oshai.kotlinlogging.KotlinLogging
import io.github.smaugfm.monobudget.common.model.financial.StatementItem
import io.github.smaugfm.monobudget.common.util.jaroWinklerSimilarity
import io.github.smaugfm.monobudget.ynab.model.YnabCleared
import io.github.smaugfm.monobudget.ynab.model.YnabSaveTransaction
import io.github.smaugfm.monobudget.ynab.model.YnabTransactionDetail
import org.koin.core.annotation.Single

private val log = KotlinLogging.logger {}

@Single
class YnabTransactionMatcher(
    private val api: YnabApi,
) {
    suspend fun findExisting(
        statement: StatementItem,
        desired: YnabSaveTransaction,
    ): YnabTransactionDetail? {
        val transactions = api.getAccountTransactions(desired.accountId, desired.date)

        findPreviouslySynced(statement, transactions)?.let { return it }

        val candidates =
            transactions
                .filter { isMatchCandidate(it, desired) }
                .sortedWith(matchCandidateComparator(desired))

        if (candidates.isEmpty()) {
            return null
        }

        if (candidates.size > 1) {
            log.warn {
                "Multiple YNAB match candidates for mono statement ${statement.id}; " +
                    "picking ${candidates.first().id} among ${candidates.map { it.id }}"
            }
        }

        return candidates.first().also {
            log.info {
                "Matched mono statement ${statement.id} to existing YNAB transaction ${it.id} " +
                    "(import_id=${it.importId}, cleared=${it.cleared})"
            }
        }
    }

    fun mergeForUpdate(
        existing: YnabTransactionDetail,
        desired: YnabSaveTransaction,
        statementId: String,
    ): YnabSaveTransaction =
        desired.copy(
            accountId = existing.accountId,
            date = existing.date,
            importId = existing.importId,
            memo = YnabMonoLink.appendMemo(existing.memo, statementId, desired.memo),
            cleared = YnabCleared.Cleared,
            approved = true,
        )

    private fun findPreviouslySynced(
        statement: StatementItem,
        transactions: List<YnabTransactionDetail>,
    ): YnabTransactionDetail? {
        val monoImportId = YnabMonoImportId.forStatement(statement.id)
        return transactions.find { it.importId == monoImportId && !it.deleted }
            ?: transactions.find { !it.deleted && YnabMonoLink.isLinkedToStatement(it.memo, statement.id) }
    }

    internal companion object {
        fun isMatchCandidate(
            existing: YnabTransactionDetail,
            desired: YnabSaveTransaction,
        ): Boolean =
            !existing.deleted &&
                existing.accountId == desired.accountId &&
                existing.amount == desired.amount &&
                existing.date == desired.date &&
                isPendingForMatch(existing) &&
                existing.transferTransactionId == null &&
                !YnabMonoImportId.isMonoImport(existing.importId) &&
                !YnabMonoLink.isLinked(existing.memo) &&
                payeeAndDescriptionMatch(existing, desired)

        fun isPendingForMatch(existing: YnabTransactionDetail): Boolean =
            existing.cleared == YnabCleared.Uncleared && !existing.approved

        fun payeeAndDescriptionMatch(
            existing: YnabTransactionDetail,
            desired: YnabSaveTransaction,
        ): Boolean {
            val desiredLabels = collectLabels(desired.payeeName, desired.memo)
            if (desiredLabels.isEmpty()) {
                return true
            }

            val existingLabels = collectLabels(existing.payeeName, existing.memo)
            if (existingLabels.isEmpty()) {
                return false
            }

            return desiredLabels.any { desiredLabel ->
                existingLabels.any { existingLabel ->
                    labelsMatch(desiredLabel, existingLabel)
                }
            }
        }

        fun matchCandidateComparator(desired: YnabSaveTransaction): Comparator<YnabTransactionDetail> =
            compareBy<YnabTransactionDetail> { candidate ->
                when {
                    candidate.importId?.startsWith("YNAB:") == true &&
                        payeeAndDescriptionMatch(candidate, desired) -> 0
                    payeeAndDescriptionMatch(candidate, desired) -> 1
                    candidate.importId?.startsWith("YNAB:") == true -> IMPORT_ONLY_PRIORITY
                    candidate.categoryId == null -> UNCATEGORIZED_PRIORITY
                    else -> DEFAULT_PRIORITY
                }
            }

        private fun collectLabels(vararg values: String?): List<String> =
            values.mapNotNull { value ->
                value?.trim()?.takeIf { it.isNotEmpty() }?.let(::normalizeLabel)
            }

        private fun normalizeLabel(value: String): String = value.lowercase().replace(Regex("\\s+"), " ")

        private fun labelsMatch(
            a: String,
            b: String,
        ): Boolean {
            if (a == b) {
                return true
            }
            if (jaroWinklerSimilarity(a, b, ignoreCase = true) >= PAYEE_MATCH_THRESHOLD) {
                return true
            }
            val shorter: String
            val longer: String
            if (a.length <= b.length) {
                shorter = a
                longer = b
            } else {
                shorter = b
                longer = a
            }
            return shorter.length >= MIN_PREFIX_MATCH_LENGTH && longer.startsWith(shorter)
        }

        private const val PAYEE_MATCH_THRESHOLD = 0.88
        private const val MIN_PREFIX_MATCH_LENGTH = 4
        private const val IMPORT_ONLY_PRIORITY = 2
        private const val UNCATEGORIZED_PRIORITY = 3
        private const val DEFAULT_PRIORITY = 4
    }
}
