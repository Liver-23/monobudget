package io.github.smaugfm.monobudget.common.notify

import com.elbekd.bot.types.InlineKeyboardButton
import com.elbekd.bot.types.InlineKeyboardMarkup
import io.github.smaugfm.monobudget.common.model.callback.ActionCallbackType.CategoryPage
import io.github.smaugfm.monobudget.common.model.callback.TransactionUpdateType.UpdateCategory
import io.github.smaugfm.monobudget.common.util.isEven
import io.github.smaugfm.monobudget.common.util.isOdd

internal object CategoryInlineKeyboard {
    private const val CATEGORIES_PER_PAGE = 20

    fun build(
        categoryIdToNameList: List<Pair<String, String>>,
        page: Int,
    ): InlineKeyboardMarkup {
        if (categoryIdToNameList.isEmpty()) {
            return InlineKeyboardMarkup(emptyList())
        }

        val totalPages = (categoryIdToNameList.size + CATEGORIES_PER_PAGE - 1) / CATEGORIES_PER_PAGE
        val safePage = page.coerceIn(0, totalPages - 1)
        val pageCategories =
            categoryIdToNameList
                .drop(safePage * CATEGORIES_PER_PAGE)
                .take(CATEGORIES_PER_PAGE)

        val buttons = pageCategories.map { (id, name) -> UpdateCategory.button(id, name) }
        val rows =
            buttons
                .zipWithNext()
                .map { it.toList() }
                .filterIndexed { index, _ -> index.isEven() }
                .toMutableList()
        if (buttons.size.isOdd()) {
            rows.add(listOf(buttons.last()))
        }

        if (totalPages > 1) {
            val navigationRow = mutableListOf<InlineKeyboardButton>()
            if (safePage > 0) {
                navigationRow.add(CategoryPage.button("◀️ Prev", safePage - 1))
            }
            if (safePage < totalPages - 1) {
                navigationRow.add(CategoryPage.button("Next ▶️", safePage + 1))
            }
            rows.add(navigationRow)
        }

        return InlineKeyboardMarkup(rows)
    }
}
