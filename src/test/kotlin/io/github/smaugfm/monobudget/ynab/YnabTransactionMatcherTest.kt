package io.github.smaugfm.monobudget.ynab

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import io.github.smaugfm.monobudget.ynab.model.YnabCleared
import io.github.smaugfm.monobudget.ynab.model.YnabSaveTransaction
import io.github.smaugfm.monobudget.ynab.model.YnabTransactionDetail
import kotlinx.datetime.LocalDate
import org.junit.jupiter.api.Test

class YnabTransactionMatcherTest {
    private val desired =
        YnabSaveTransaction(
            accountId = ACCOUNT_ID,
            date = LocalDate(2026, 6, 7),
            amount = -382_000,
            payeeId = null,
            payeeName = "Сільпо",
            categoryId = "cat-1",
            memo = "Сільпо",
            cleared = YnabCleared.Cleared,
            approved = true,
            flagColor = null,
            importId = null,
            subtransactions = emptyList(),
        )

    @Test
    fun `matches uncleared direct import transaction on same day`() {
        val existing =
            transaction(
                CandidateTransaction(
                    id = "ynab-1",
                    date = LocalDate(2026, 6, 7),
                    amount = -382_000,
                    cleared = YnabCleared.Uncleared,
                    importId = "YNAB:-382000:2026-06-07:1",
                ),
            )

        assertThat(YnabTransactionMatcher.isMatchCandidate(existing, desired)).isTrue()
    }

    @Test
    fun `rejects cleared transaction`() {
        val existing =
            transaction(
                CandidateTransaction(
                    id = "ynab-1",
                    date = LocalDate(2026, 6, 7),
                    amount = -382_000,
                    cleared = YnabCleared.Cleared,
                ),
            )

        assertThat(YnabTransactionMatcher.isMatchCandidate(existing, desired)).isFalse()
    }

    @Test
    fun `rejects reconciled transaction`() {
        val existing =
            transaction(
                CandidateTransaction(
                    id = "ynab-1",
                    date = LocalDate(2026, 6, 7),
                    amount = -382_000,
                    cleared = YnabCleared.Reconciled,
                ),
            )

        assertThat(YnabTransactionMatcher.isMatchCandidate(existing, desired)).isFalse()
    }

    @Test
    fun `rejects approved uncleared transaction`() {
        val existing =
            transaction(
                CandidateTransaction(
                    id = "ynab-1",
                    date = LocalDate(2026, 6, 7),
                    amount = -382_000,
                    cleared = YnabCleared.Uncleared,
                    approved = true,
                ),
            )

        assertThat(YnabTransactionMatcher.isMatchCandidate(existing, desired)).isFalse()
    }

    @Test
    fun `rejects transaction on different day`() {
        val existing =
            transaction(
                CandidateTransaction(
                    id = "ynab-1",
                    date = LocalDate(2026, 5, 31),
                    amount = -382_000,
                    cleared = YnabCleared.Uncleared,
                ),
            )

        assertThat(YnabTransactionMatcher.isMatchCandidate(existing, desired)).isFalse()
    }

    @Test
    fun `rejects transaction already synced by monobudget`() {
        val existing =
            transaction(
                CandidateTransaction(
                    id = "ynab-1",
                    date = LocalDate(2026, 6, 7),
                    amount = -382_000,
                    cleared = YnabCleared.Uncleared,
                    importId = YnabMonoImportId.forStatement("mono-statement-1"),
                ),
            )

        assertThat(YnabTransactionMatcher.isMatchCandidate(existing, desired)).isFalse()
    }

    @Test
    fun `rejects transaction already linked in memo`() {
        val existing =
            transaction(
                CandidateTransaction(
                    id = "ynab-1",
                    date = LocalDate(2026, 6, 7),
                    amount = -382_000,
                    cleared = YnabCleared.Uncleared,
                    memo = "VBET ${YnabMonoLink.marker("older-mono-statement")}",
                ),
            )

        assertThat(YnabTransactionMatcher.isMatchCandidate(existing, desired)).isFalse()
    }

    @Test
    fun `prefers direct import over manual uncleared transaction on same day`() {
        val directImport =
            transaction(
                CandidateTransaction(
                    id = "direct-import",
                    date = LocalDate(2026, 6, 7),
                    amount = -382_000,
                    cleared = YnabCleared.Uncleared,
                    importId = "YNAB:-382000:2026-06-07:1",
                ),
            )
        val manual =
            transaction(
                CandidateTransaction(
                    id = "manual",
                    date = LocalDate(2026, 6, 7),
                    amount = -382_000,
                    cleared = YnabCleared.Uncleared,
                ),
            )

        val sorted =
            listOf(manual, directImport)
                .sortedWith(YnabTransactionMatcher.matchCandidateComparator(desired))

        assertThat(sorted.first().id).isEqualTo("direct-import")
    }

    @Test
    fun `rejects same-day transaction with different payee`() {
        val existing =
            transaction(
                CandidateTransaction(
                    id = "ynab-vbet",
                    date = LocalDate(2026, 6, 7),
                    amount = -382_000,
                    cleared = YnabCleared.Uncleared,
                    importId = "YNAB:-382000:2026-06-07:1",
                    payeeName = "VBET",
                    memo = "VBET",
                ),
            )

        assertThat(YnabTransactionMatcher.isMatchCandidate(existing, desired)).isFalse()
    }

    @Test
    fun `matches same-day transaction when payee and memo align`() {
        val existing =
            transaction(
                CandidateTransaction(
                    id = "ynab-silpo",
                    date = LocalDate(2026, 6, 7),
                    amount = -382_000,
                    cleared = YnabCleared.Uncleared,
                    importId = "YNAB:-382000:2026-06-07:1",
                    payeeName = "Сільпо",
                    memo = "Сільпо 123",
                ),
            )

        assertThat(YnabTransactionMatcher.isMatchCandidate(existing, desired)).isTrue()
    }

    @Test
    fun `allows second same-day transaction when first is already linked`() {
        val linked =
            transaction(
                CandidateTransaction(
                    id = "linked",
                    date = LocalDate(2026, 6, 7),
                    amount = -382_000,
                    cleared = YnabCleared.Uncleared,
                    memo = YnabMonoLink.marker("first-statement"),
                ),
            )
        val available =
            transaction(
                CandidateTransaction(
                    id = "available",
                    date = LocalDate(2026, 6, 7),
                    amount = -382_000,
                    cleared = YnabCleared.Uncleared,
                    importId = "YNAB:-382000:2026-06-07:2",
                ),
            )

        assertThat(YnabTransactionMatcher.isMatchCandidate(linked, desired)).isFalse()
        assertThat(YnabTransactionMatcher.isMatchCandidate(available, desired)).isTrue()
    }

    private fun transaction(candidate: CandidateTransaction) =
        YnabTransactionDetail(
            id = candidate.id,
            date = candidate.date,
            amount = candidate.amount,
            memo = candidate.memo,
            cleared = candidate.cleared,
            approved = candidate.approved,
            flagColor = null,
            accountId = ACCOUNT_ID,
            payeeId = null,
            categoryId = candidate.categoryId,
            transferAccountId = null,
            transferTransactionId = null,
            matchedTransactionId = null,
            importId = candidate.importId,
            deleted = false,
            accountName = "Mono White",
            payeeName = candidate.payeeName ?: "Сільпо",
            categoryName = null,
            subtransactions = emptyList(),
        )

    private data class CandidateTransaction(
        val id: String,
        val date: LocalDate,
        val amount: Long,
        val cleared: YnabCleared,
        val approved: Boolean = false,
        val importId: String? = null,
        val categoryId: String? = null,
        val memo: String? = null,
        val payeeName: String? = null,
    )

    companion object {
        private const val ACCOUNT_ID = "650de717-9d6a-493a-9ce3-7f152722b675"
    }
}
