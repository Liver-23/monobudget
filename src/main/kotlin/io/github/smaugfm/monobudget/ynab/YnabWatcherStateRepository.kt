package io.github.smaugfm.monobudget.ynab

data class YnabWatcherState(
    val budgetId: String,
    val serverKnowledge: Int,
    val notifiedTransactionIds: Set<String> = emptySet(),
)

interface YnabWatcherStateRepository {
    suspend fun load(budgetId: String): YnabWatcherState?

    suspend fun save(state: YnabWatcherState)
}
