package io.github.smaugfm.monobudget.ynab

object YnabMonoLink {
    private const val PREFIX = "[mono:"

    fun marker(statementId: String): String = "$PREFIX$statementId]"

    fun isLinked(memo: String?): Boolean = memo?.contains(PREFIX) == true

    fun isLinkedToStatement(
        memo: String?,
        statementId: String,
    ): Boolean = memo?.contains(marker(statementId)) == true

    fun appendMemo(
        existingMemo: String?,
        statementId: String,
        fallbackMemo: String?,
    ): String {
        val marker = marker(statementId)
        val base = existingMemo?.takeIf { it.isNotBlank() } ?: fallbackMemo.orEmpty()
        if (base.contains(marker)) {
            return base
        }
        return if (base.isBlank()) marker else "$base $marker"
    }
}
