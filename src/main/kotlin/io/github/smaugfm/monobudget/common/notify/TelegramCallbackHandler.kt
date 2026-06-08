package io.github.smaugfm.monobudget.common.notify

import com.elbekd.bot.model.ChatId
import com.elbekd.bot.model.TelegramApiError
import com.elbekd.bot.types.CallbackQuery
import com.elbekd.bot.types.InlineKeyboardMarkup
import com.elbekd.bot.types.Message
import com.elbekd.bot.types.ParseMode
import io.github.oshai.kotlinlogging.KotlinLogging
import io.github.smaugfm.monobudget.common.category.CategoryService
import io.github.smaugfm.monobudget.common.model.callback.ActionCallbackType
import io.github.smaugfm.monobudget.common.model.callback.ActionCallbackType.CategoryPage
import io.github.smaugfm.monobudget.common.model.callback.ActionCallbackType.ChooseCategory
import io.github.smaugfm.monobudget.common.model.callback.CallbackType
import io.github.smaugfm.monobudget.common.model.callback.TransactionUpdateType
import io.github.smaugfm.monobudget.common.model.settings.MultipleAccountSettings
import io.github.smaugfm.monobudget.common.statement.lifecycle.StatementEvents
import io.github.smaugfm.monobudget.common.transaction.TransactionMessageFormatter
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

private val log = KotlinLogging.logger {}

abstract class TelegramCallbackHandler<TTransaction> : KoinComponent {
    protected val categoryService: CategoryService by inject()
    private val telegram: TelegramApi by inject()
    private val formatter: TransactionMessageFormatter<TTransaction> by inject()
    private val monoSettings: MultipleAccountSettings by inject()
    private val telegramChatIds = monoSettings.telegramChatIds
    private val statementEvents by inject<StatementEvents>()
    private val callbackParser = CallbackQueryParser()

    suspend fun handle(callbackQuery: CallbackQuery) {
        var callbackType: CallbackType? = null
        try {
            if (callbackQuery.from.id !in telegramChatIds) {
                log.warn { "Received Telegram callbackQuery from unknown chatId: ${callbackQuery.from.id}" }
                return
            }

            val res = callbackParser.parse(callbackQuery) ?: return
            callbackType = res.updateType

            log.debug { "Parsed callback query id=${callbackQuery.id}of callbackType: $callbackType" }

            telegram.answerCallbackQuery(callbackQuery.id)

            when (callbackType) {
                is ActionCallbackType -> handleAction(callbackType, res.message)
                is TransactionUpdateType -> handleUpdate(callbackType, res.message)
            }
        } catch (e: TelegramApiError) {
            if (e.isBenign()) {
                log.debug { "Ignoring benign Telegram callback error: ${e.description}" }
            } else {
                statementEvents.onCallbackError(callbackQuery, callbackType, e)
            }
        } catch (e: Throwable) {
            statementEvents.onCallbackError(callbackQuery, callbackType, e)
        }
    }

    private suspend fun handleAction(
        callbackType: ActionCallbackType,
        message: Message,
    ) {
        when (callbackType) {
            is ChooseCategory -> showCategoryKeyboard(message, page = 0)
            is CategoryPage -> showCategoryKeyboard(message, page = callbackType.page)
        }
    }

    private suspend fun showCategoryKeyboard(
        message: Message,
        page: Int,
    ) {
        telegram.editKeyboard(
            ChatId.IntegerId(message.chat.id),
            message.messageId,
            CategoryInlineKeyboard.build(
                categoryService.categoryIdToNameList(),
                page,
            ),
        )
    }

    private suspend fun handleUpdate(
        callbackType: TransactionUpdateType,
        message: Message,
    ) {
        val updatedTransaction = updateTransaction(callbackType)
        val updatedText = updateHTMLStatementMessage(updatedTransaction, message)
        val updatedMarkup = formatter.getReplyKeyboard(updatedTransaction)

        if (stripHTMLTagsFromMessage(updatedText) != message.text ||
            updatedMarkup != message.replyMarkup
        ) {
            editMessage(message, updatedText, updatedMarkup)
        }
    }

    private suspend fun TelegramCallbackHandler<TTransaction>.editMessage(
        message: Message,
        updatedText: String,
        updatedMarkup: InlineKeyboardMarkup,
    ) {
        with(message) {
            try {
                telegram.editMessage(
                    ChatId.IntegerId(chat.id),
                    messageId,
                    updatedText,
                    ParseMode.Html,
                    updatedMarkup,
                )
            } catch (e: TelegramApiError) {
                if (e.isBenign()) {
                    return
                }
                throw e
            }
        }
    }

    protected abstract suspend fun updateTransaction(callbackType: TransactionUpdateType): TTransaction

    protected abstract suspend fun updateHTMLStatementMessage(
        updatedTransaction: TTransaction,
        oldMessage: Message,
    ): String

    private fun stripHTMLTagsFromMessage(messageText: String): String {
        val replaceHtml = Regex("<.*?>")
        return replaceHtml.replace(messageText, "")
    }
}
