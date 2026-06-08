package io.github.smaugfm.monobudget.ynab

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import org.junit.jupiter.api.Test

class YnabMonoLinkTest {
    @Test
    fun `appends mono marker to memo`() {
        assertThat(
            YnabMonoLink.appendMemo("VBET.UA", "statement-1", "fallback"),
        ).isEqualTo("VBET.UA ${YnabMonoLink.marker("statement-1")}")
    }

    @Test
    fun `does not duplicate marker`() {
        val memo = YnabMonoLink.appendMemo("VBET.UA", "statement-1", null)

        assertThat(YnabMonoLink.appendMemo(memo, "statement-1", null)).isEqualTo(memo)
    }

    @Test
    fun `detects linked memo`() {
        assertThat(YnabMonoLink.isLinked("VBET ${YnabMonoLink.marker("statement-1")}")).isTrue()
        assertThat(YnabMonoLink.isLinked("VBET.UA")).isFalse()
    }
}
