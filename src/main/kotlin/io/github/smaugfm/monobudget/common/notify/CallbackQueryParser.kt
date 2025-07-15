package io.github.smaugfm.monobudget.common.notify

import com.elbekd.bot.types.CallbackQuery
import com.elbekd.bot.types.Message
import io.github.oshai.kotlinlogging.KotlinLogging
import io.github.smaugfm.monobudget.common.model.callback.CallbackType
import io.github.smaugfm.monobudget.common.model.callback.TransactionUpdateType
import io.github.smaugfm.monobudget.common.model.callback.TransactionUpdateType.MakePayee
import io.github.smaugfm.monobudget.common.model.callback.TransactionUpdateType.Unapprove
import io.github.smaugfm.monobudget.common.model.callback.TransactionUpdateType.Uncategorize
import io.github.smaugfm.monobudget.common.model.callback.TransactionUpdateType.UpdateCategory
import io.github.smaugfm.monobudget.common.transaction.TransactionMessageFormatter.Companion.extractPayee
import io.github.smaugfm.monobudget.common.transaction.TransactionMessageFormatter.Companion.extractTransactionId

private val log = KotlinLogging.logger {}

class CallbackQueryParser {
    fun parse(callbackQuery: CallbackQuery): TransactionUpdateCallbackQueryWrapper? {
        val data = callbackQueryData(callbackQuery)
        val message = callbackQueryMessage(callbackQuery)

        if (data == null || message == null) {
            return null
        }

        val callbackType = deserializeCallbackType(data, message)
        if (callbackType == null) {
            log.error { "Unknown callbackType. Skipping this callback query: $callbackQuery" }
            return null
        }

        return TransactionUpdateCallbackQueryWrapper(callbackType, message)
    }

    private fun callbackQueryMessage(callbackQuery: CallbackQuery): Message? =
        callbackQuery.message ?: log.warn { "Received Telegram callbackQuery with empty message" }
            .let { return null }

    private fun callbackQueryData(callbackQuery: CallbackQuery): String? =
        callbackQuery.data.takeUnless { it.isNullOrBlank() }
            ?: log.warn { "Received Telegram callbackQuery with empty data.\n$callbackQuery" }
                .let { return null }

    private fun deserializeCallbackType(
        callbackData: String,
        message: Message,
    ): CallbackType? {
        val cls = CallbackType.classFromCallbackData(callbackData)
        val payee = extractPayee(message) ?: return null
        val transactionId = extractTransactionId(message)

        return when (cls) {
            Uncategorize::class -> Uncategorize(transactionId)
            Unapprove::class -> Unapprove(transactionId)
            MakePayee::class -> MakePayee(transactionId, payee)
            UpdateCategory::class -> UpdateCategory(
                transactionId,
                UpdateCategory.extractCategoryIdFromCallbackData(callbackData),
            )
            else -> throw IllegalArgumentException(
                "Unknown class CallbackType: ${cls?.simpleName}",
            )
        }
    }

    data class TransactionUpdateCallbackQueryWrapper(
        val updateType: CallbackType,
        val message: Message,
    )
} 
