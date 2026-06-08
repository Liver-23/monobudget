package io.github.smaugfm.monobudget.common.category

object CategoryNameSortKey {
    private val NON_SORTABLE = Regex("""[^\p{L}\p{N}\s]""")

    fun of(name: String): String =
        NON_SORTABLE
            .replace(name, "")
            .replace(Regex("""\s+"""), " ")
            .trim()
            .lowercase()
}
