package io.github.smaugfm.monobudget.ynab

import java.security.MessageDigest

object YnabMonoImportId {
    private const val PREFIX = "M:"
    private const val LEGACY_PREFIX = "MONO:"
    private const val MAX_LENGTH = 36

    fun forStatement(statementId: String): String {
        val legacy = "$LEGACY_PREFIX$statementId"
        if (legacy.length <= MAX_LENGTH) {
            return legacy
        }
        return PREFIX + sha256Hex(statementId).take(MAX_LENGTH - PREFIX.length)
    }

    fun isMonoImport(importId: String?): Boolean =
        importId != null && (importId.startsWith(PREFIX) || importId.startsWith(LEGACY_PREFIX))

    private fun sha256Hex(value: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        return digest
            .digest(value.toByteArray(Charsets.UTF_8))
            .joinToString(separator = "") { byte -> "%02x".format(byte) }
    }
}
