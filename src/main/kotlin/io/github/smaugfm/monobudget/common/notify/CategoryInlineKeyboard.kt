package io.github.smaugfm.monobudget.common.notify

import com.elbekd.bot.types.InlineKeyboardButton
import com.elbekd.bot.types.InlineKeyboardMarkup
import io.github.smaugfm.monobudget.common.model.callback.ActionCallbackType.BackToCategoryGroups
import io.github.smaugfm.monobudget.common.model.callback.ActionCallbackType.CancelCategoryPicker
import io.github.smaugfm.monobudget.common.model.callback.ActionCallbackType.CategoryGroupPage
import io.github.smaugfm.monobudget.common.model.callback.ActionCallbackType.CategoryPage
import io.github.smaugfm.monobudget.common.model.callback.ActionCallbackType.ChooseCategoryGroup
import io.github.smaugfm.monobudget.common.model.callback.TransactionUpdateType.UpdateCategory
import io.github.smaugfm.monobudget.common.util.isEven
import io.github.smaugfm.monobudget.common.util.isOdd

internal object CategoryInlineKeyboard {
    private const val ITEMS_PER_PAGE = 20

    fun buildGroups(
        groups: List<Pair<String, String>>,
        page: Int,
    ): InlineKeyboardMarkup {
        if (groups.isEmpty()) {
            return InlineKeyboardMarkup(emptyList())
        }

        val totalPages = (groups.size + ITEMS_PER_PAGE - 1) / ITEMS_PER_PAGE
        val safePage = page.coerceIn(0, totalPages - 1)
        val pageGroups =
            groups
                .drop(safePage * ITEMS_PER_PAGE)
                .take(ITEMS_PER_PAGE)

        val buttons = pageGroups.map { (id, name) -> ChooseCategoryGroup.button(name, id) }
        val rows = buttonsToTwoColumnRows(buttons)

        addNavigationRow(
            rows = rows,
            safePage = safePage,
            totalPages = totalPages,
            prevButton = { navPage -> CategoryGroupPage.button("◀️ Prev", navPage) },
            nextButton = { navPage -> CategoryGroupPage.button("Next ▶️", navPage) },
        )

        rows.add(listOf(CancelCategoryPicker.button()))

        return InlineKeyboardMarkup(rows)
    }

    fun buildCategories(
        categories: List<Pair<String, String>>,
        groupId: String,
        page: Int,
    ): InlineKeyboardMarkup {
        if (categories.isEmpty()) {
            return InlineKeyboardMarkup(listOf(listOf(BackToCategoryGroups.button())))
        }

        val totalPages = (categories.size + ITEMS_PER_PAGE - 1) / ITEMS_PER_PAGE
        val safePage = page.coerceIn(0, totalPages - 1)
        val pageCategories =
            categories
                .drop(safePage * ITEMS_PER_PAGE)
                .take(ITEMS_PER_PAGE)

        val buttons = pageCategories.map { (id, name) -> UpdateCategory.button(id, name) }
        val rows = buttonsToTwoColumnRows(buttons)

        addNavigationRow(
            rows = rows,
            safePage = safePage,
            totalPages = totalPages,
            prevButton = { navPage -> CategoryPage.button("◀️ Prev", groupId, navPage) },
            nextButton = { navPage -> CategoryPage.button("Next ▶️", groupId, navPage) },
        )

        rows.add(listOf(BackToCategoryGroups.button()))

        return InlineKeyboardMarkup(rows)
    }

    private fun buttonsToTwoColumnRows(
        buttons: List<InlineKeyboardButton>,
    ): MutableList<List<InlineKeyboardButton>> {
        val rows =
            buttons
                .zipWithNext()
                .map { it.toList() }
                .filterIndexed { index, _ -> index.isEven() }
                .toMutableList()
        if (buttons.size.isOdd()) {
            rows.add(listOf(buttons.last()))
        }
        return rows
    }

    private fun addNavigationRow(
        rows: MutableList<List<InlineKeyboardButton>>,
        safePage: Int,
        totalPages: Int,
        prevButton: (Int) -> InlineKeyboardButton,
        nextButton: (Int) -> InlineKeyboardButton,
    ) {
        if (totalPages <= 1) {
            return
        }

        val navigationRow = mutableListOf<InlineKeyboardButton>()
        if (safePage > 0) {
            navigationRow.add(prevButton(safePage - 1))
        }
        if (safePage < totalPages - 1) {
            navigationRow.add(nextButton(safePage + 1))
        }
        rows.add(navigationRow)
    }
}
