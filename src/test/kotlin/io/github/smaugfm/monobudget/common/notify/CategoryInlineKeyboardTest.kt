package io.github.smaugfm.monobudget.common.notify

import assertk.assertThat
import assertk.assertions.hasSize
import assertk.assertions.isEqualTo
import org.junit.jupiter.api.Test

class CategoryInlineKeyboardTest {
    private val groups =
        listOf(
            "group-b" to "Bills",
            "group-a" to "Everyday",
            "group-c" to "Goals",
        )

    private val categories =
        (1..45).map { index ->
            "category-$index" to "Category $index"
        }

    @Test
    fun `shows groups in two columns`() {
        val keyboard = CategoryInlineKeyboard.buildGroups(groups, page = 0)

        assertThat(keyboard.inlineKeyboard).hasSize(3)
        assertThat(keyboard.inlineKeyboard[0].map { it.text }).isEqualTo(listOf("Bills", "Everyday"))
        assertThat(keyboard.inlineKeyboard[1].single().text).isEqualTo("Goals")
        assertThat(keyboard.inlineKeyboard[2].single().text).isEqualTo("❌ Cancel")
    }

    @Test
    fun `paginates groups and adds navigation row`() {
        val manyGroups = (1..25).map { index -> "group-$index" to "Group $index" }

        val firstPage = CategoryInlineKeyboard.buildGroups(manyGroups, page = 0)
        val secondPage = CategoryInlineKeyboard.buildGroups(manyGroups, page = 1)

        assertThat(firstPage.inlineKeyboard).hasSize(12)
        assertThat(firstPage.inlineKeyboard[firstPage.inlineKeyboard.lastIndex - 1].single().text)
            .isEqualTo("Next ▶️")
        assertThat(firstPage.inlineKeyboard[firstPage.inlineKeyboard.lastIndex].single().text)
            .isEqualTo("❌ Cancel")
        assertThat(secondPage.inlineKeyboard[secondPage.inlineKeyboard.lastIndex - 1].single().text)
            .isEqualTo("◀️ Prev")
        assertThat(secondPage.inlineKeyboard.last().single().text).isEqualTo("❌ Cancel")
    }

    @Test
    fun `paginates categories adds navigation and back row`() {
        val firstPage = CategoryInlineKeyboard.buildCategories(categories, groupId = "group-a", page = 0)
        val secondPage = CategoryInlineKeyboard.buildCategories(categories, groupId = "group-a", page = 1)
        val thirdPage = CategoryInlineKeyboard.buildCategories(categories, groupId = "group-a", page = 2)

        assertThat(firstPage.inlineKeyboard).hasSize(12)
        assertThat(firstPage.inlineKeyboard[firstPage.inlineKeyboard.lastIndex - 1].single().text)
            .isEqualTo("Next ▶️")
        assertThat(firstPage.inlineKeyboard.last().single().text).isEqualTo("◀️ Back")

        assertThat(secondPage.inlineKeyboard[secondPage.inlineKeyboard.lastIndex - 1].map { it.text })
            .isEqualTo(listOf("◀️ Prev", "Next ▶️"))
        assertThat(secondPage.inlineKeyboard.last().single().text).isEqualTo("◀️ Back")

        assertThat(thirdPage.inlineKeyboard.dropLast(2)).hasSize(3)
        assertThat(thirdPage.inlineKeyboard[thirdPage.inlineKeyboard.lastIndex - 1].single().text)
            .isEqualTo("◀️ Prev")
        assertThat(thirdPage.inlineKeyboard.last().single().text).isEqualTo("◀️ Back")
    }

    @Test
    fun `shows only back button when category list is empty`() {
        val keyboard = CategoryInlineKeyboard.buildCategories(emptyList(), groupId = "group-a", page = 0)

        assertThat(keyboard.inlineKeyboard).hasSize(1)
        assertThat(keyboard.inlineKeyboard.single().single().text).isEqualTo("◀️ Back")
    }
}
