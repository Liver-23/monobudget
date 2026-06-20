package io.github.smaugfm.monobudget.ynab

class InMemoryYnabWatcherStateRepository : YnabWatcherStateRepository {
    private val states = mutableMapOf<String, YnabWatcherState>()

    override suspend fun load(budgetId: String): YnabWatcherState? = states[budgetId]

    override suspend fun save(state: YnabWatcherState) {
        states[state.budgetId] = state
    }
}
