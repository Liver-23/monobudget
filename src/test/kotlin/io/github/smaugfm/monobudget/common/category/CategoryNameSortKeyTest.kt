package io.github.smaugfm.monobudget.common.category

import assertk.assertThat
import assertk.assertions.isEqualTo
import org.junit.jupiter.api.Test

class CategoryNameSortKeyTest {
    @Test
    fun `sorts by text without leading emoji`() {
        val names =
            listOf(
                "🎯 Goals",
                "Bills",
                "🏠 Everyday",
            ).sortedBy { CategoryNameSortKey.of(it) }

        assertThat(names).isEqualTo(
            listOf(
                "Bills",
                "🏠 Everyday",
                "🎯 Goals",
            ),
        )
    }
}
