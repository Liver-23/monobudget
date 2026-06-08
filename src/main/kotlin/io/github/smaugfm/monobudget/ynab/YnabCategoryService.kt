package io.github.smaugfm.monobudget.ynab

import io.github.smaugfm.monobudget.common.category.CategoryService
import io.github.smaugfm.monobudget.common.model.BudgetBackend
import io.github.smaugfm.monobudget.common.model.financial.Amount
import io.github.smaugfm.monobudget.common.util.misc.PeriodicFetcherFactory
import io.github.smaugfm.monobudget.ynab.model.YnabCategory
import org.koin.core.annotation.Single
import java.util.Currency

@Single(createdAtStart = true)
class YnabCategoryService(
    periodicFetcherFactory: PeriodicFetcherFactory,
    private val api: YnabApi,
    private val ynab: BudgetBackend.YNAB,
) : CategoryService() {
    private data class CategoryCache(
        val groups: List<CategoryGroup>,
        val categoriesById: Map<String, YnabCategory>,
    )

    private val categoriesFetcher =
        periodicFetcherFactory.create("YNAB categories") {
            val categoriesById = mutableMapOf<String, YnabCategory>()
            val groups =
                api.getCategoryGroups()
                    .filter { group -> !group.hidden && !group.deleted }
                    .map { group ->
                        val categories =
                            group.categories
                                .filter { category -> !category.hidden && !category.deleted }
                                .sortedBy { it.name.lowercase() }
                                .onEach { category -> categoriesById[category.id] = category }
                                .map { category -> category.id to category.name }
                        CategoryGroup(
                            id = group.id,
                            name = group.name,
                            categories = categories,
                        )
                    }.sortedBy { it.name.lowercase() }
            CategoryCache(groups, categoriesById)
        }

    private val budgetCurrencyFetcher =
        periodicFetcherFactory.create("YNAB budget summary") {
            Currency.getInstance(api.getBudget(ynab.ynabBudgetId).currencyFormat.isoCode)
        }

    override suspend fun categoryGroups(): List<CategoryGroup> = categoriesFetcher.fetched().groups

    override suspend fun budgetedCategoryByIdInternal(categoryId: String): BudgetedCategory? {
        val category = categoriesFetcher.fetched().categoriesById[categoryId] ?: return null
        val currency = budgetCurrencyFetcher.fetched()

        return BudgetedCategory(
            category.name,
            if (category.budgeted > 0) {
                BudgetedCategory.CategoryBudget(
                    Amount.fromYnabAmount(category.balance, currency),
                    Amount.fromYnabAmount(category.budgeted, currency),
                )
            } else {
                null
            },
        )
    }

    override suspend fun categoryIdByName(categoryName: String): String? =
        categoriesFetcher.fetched().categoriesById.values
            .firstOrNull { it.name == categoryName }
            ?.id
}
