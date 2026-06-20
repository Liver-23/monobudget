package io.github.smaugfm.monobudget.ynab

import io.github.smaugfm.monobudget.common.category.CategoryNameSortKey
import io.github.smaugfm.monobudget.common.category.CategoryService
import io.github.smaugfm.monobudget.common.model.financial.Amount
import io.github.smaugfm.monobudget.common.util.misc.PeriodicFetcherFactory
import io.github.smaugfm.monobudget.ynab.model.YnabCategory
import org.koin.core.annotation.Single
import java.util.Currency
import java.util.concurrent.ConcurrentHashMap

@Single(createdAtStart = true)
class YnabBudgetDataService(
    private val api: YnabApi,
    private val periodicFetcherFactory: PeriodicFetcherFactory,
) {
    private data class BudgetCategoryCache(
        val groups: List<CategoryService.CategoryGroup>,
        val categoriesById: Map<String, YnabCategory>,
    )

    private val categoryFetchers =
        ConcurrentHashMap<String, PeriodicFetcherFactory.PeriodicFetcher<BudgetCategoryCache>>()
    private val currencyFetchers =
        ConcurrentHashMap<String, PeriodicFetcherFactory.PeriodicFetcher<Currency>>()

    suspend fun categoryGroups(budgetId: String): List<CategoryService.CategoryGroup> =
        categoryFetcher(budgetId).fetched().groups

    suspend fun budgetedCategoryById(
        budgetId: String,
        categoryId: String?,
    ): CategoryService.BudgetedCategory? {
        if (categoryId == null) {
            return null
        }
        val category = categoryFetcher(budgetId).fetched().categoriesById[categoryId] ?: return null
        val currency = currencyFetcher(budgetId).fetched()
        return CategoryService.BudgetedCategory(
            category.name,
            if (category.budgeted > 0) {
                CategoryService.BudgetedCategory.CategoryBudget(
                    Amount.fromYnabAmount(category.balance, currency),
                    Amount.fromYnabAmount(category.budgeted, currency),
                )
            } else {
                null
            },
        )
    }

    suspend fun budgetCurrency(budgetId: String): Currency = currencyFetcher(budgetId).fetched()

    private fun categoryFetcher(budgetId: String) =
        categoryFetchers.getOrPut(budgetId) {
            periodicFetcherFactory.create("YNAB categories $budgetId") {
                val categoriesById = mutableMapOf<String, YnabCategory>()
                val groups =
                    api.getCategoryGroups(budgetId)
                        .filter { group -> !group.hidden && !group.deleted }
                        .map { group ->
                            val categories =
                                group.categories
                                    .filter { category -> !category.hidden && !category.deleted }
                                    .sortedBy { CategoryNameSortKey.of(it.name) }
                                    .onEach { category -> categoriesById[category.id] = category }
                                    .map { category -> category.id to category.name }
                            CategoryService.CategoryGroup(group.id, group.name, categories)
                        }.sortedBy { CategoryNameSortKey.of(it.name) }

                BudgetCategoryCache(groups, categoriesById)
            }
        }

    private fun currencyFetcher(budgetId: String) =
        currencyFetchers.getOrPut(budgetId) {
            periodicFetcherFactory.create("YNAB currency $budgetId") {
                Currency.getInstance(api.getBudget(budgetId).currencyFormat.isoCode)
            }
        }
}
