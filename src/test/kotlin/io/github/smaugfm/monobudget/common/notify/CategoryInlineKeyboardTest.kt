package io.github.smaugfm.monobudget.common.notify

import assertk.assertThat
import assertk.assertions.hasSize
import assertk.assertions.isEqualTo
import org.junit.jupiter.api.Test

class CategoryInlineKeyboardTest {
    private val categories =
        (1..45).map { index ->
            "category-$index" to "Category $index"
        }

    @Test
    fun `paginates categories and adds navigation row`() {
        val firstPage = CategoryInlineKeyboard.build(categories, page = 0)
        val secondPage = CategoryInlineKeyboard.build(categories, page = 1)
        val thirdPage = CategoryInlineKeyboard.build(categories, page = 2)

        assertThat(firstPage.inlineKeyboard).hasSize(11)
        assertThat(firstPage.inlineKeyboard.last()).hasSize(1)
        assertThat(firstPage.inlineKeyboard.last().first().text).isEqualTo("Next ▶️")

        assertThat(secondPage.inlineKeyboard.last().map { it.text }).isEqualTo(listOf("◀️ Prev", "Next ▶️"))

        assertThat(thirdPage.inlineKeyboard.dropLast(1)).hasSize(3)
        assertThat(thirdPage.inlineKeyboard.last().single().text).isEqualTo("◀️ Prev")
    }
}
