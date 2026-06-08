package io.github.smaugfm.monobudget.ynab

import assertk.assertThat
import assertk.assertions.hasLength
import assertk.assertions.isEqualTo
import assertk.assertions.isTrue
import org.junit.jupiter.api.Test

class YnabMonoImportIdTest {
    @Test
    fun `keeps short statement ids in legacy format`() {
        assertThat(YnabMonoImportId.forStatement("mono-statement-1"))
            .isEqualTo("MONO:mono-statement-1")
    }

    @Test
    fun `hashes uuid statement ids to fit YNAB limit`() {
        val statementId = "dd0e74de-1d36-4d39-a66e-6a83557679d9"
        val importId = YnabMonoImportId.forStatement(statementId)

        assertThat(importId).hasLength(36)
        assertThat(importId.startsWith("M:")).isTrue()
        assertThat(YnabMonoImportId.forStatement(statementId)).isEqualTo(importId)
    }

    @Test
    fun `detects mono import ids`() {
        assertThat(YnabMonoImportId.isMonoImport("MONO:mono-statement-1")).isTrue()
        assertThat(
            YnabMonoImportId.isMonoImport(
                YnabMonoImportId.forStatement("dd0e74de-1d36-4d39-a66e-6a83557679d9"),
            ),
        ).isTrue()
    }
}
