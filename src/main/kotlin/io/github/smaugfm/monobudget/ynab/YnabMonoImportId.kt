package io.github.smaugfm.monobudget.ynab

object YnabMonoImportId {
    private const val PREFIX = "MONO:"

    fun forStatement(statementId: String): String = "$PREFIX$statementId"

    fun isMonoImport(importId: String?): Boolean = importId?.startsWith(PREFIX) == true
}
