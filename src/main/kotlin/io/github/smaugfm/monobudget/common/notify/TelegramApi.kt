package io.github.smaugfm.monobudget.common.notify

import com.elbekd.bot.Bot
import com.elbekd.bot.model.ChatId
import com.elbekd.bot.model.TelegramApiError
import com.elbekd.bot.types.CallbackQuery
import com.elbekd.bot.types.InlineKeyboardMarkup
import com.elbekd.bot.types.ParseMode
import com.elbekd.bot.types.ReplyKeyboard
import io.github.oshai.kotlinlogging.KotlinLogging
import io.github.smaugfm.monobudget.common.model.settings.TelegramBotSettings
import io.github.smaugfm.monobudget.common.util.pp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.koin.core.annotation.Single
import org.koin.core.component.KoinComponent

private val log = KotlinLogging.logger {}

@Single
class TelegramApi(
    botSettings: TelegramBotSettings,
    private val scope: CoroutineScope,
) : KoinComponent {
    private val bot: Bot =
        Bot.createPolling(botSettings.token, botSettings.username)

    suspend fun sendMessage(
        chatId: ChatId,
        text: String,
        parseMode: ParseMode? = null,
        disableNotification: Boolean? = null,
        replyMarkup: ReplyKeyboard? = null,
    ) {
        bot.sendMessage(
            chatId = chatId,
            text = text,
            messageThreadId = null,
            parseMode = parseMode,
            entities = null,
            disableWebPagePreview = true,
            disableNotification = disableNotification,
            protectContent = null,
            replyToMessageId = null,
            allowSendingWithoutReply = null,
            replyMarkup = replyMarkup,
        ).also {
            log.debug { "Sent message. \n\tTo: $chatId\n\ttext: $text\n\tkeyboard: ${replyMarkup?.pp()}" }
        }
    }

    suspend fun editKeyboard(
        chatId: ChatId,
        messageId: Long,
        replyMarkup: InlineKeyboardMarkup,
    ) {
        try {
            bot.editMessageReplyMarkup(
                chatId,
                messageId,
                null,
                replyMarkup,
            )
        } catch (e: TelegramApiError) {
            if (e.isBenign()) {
                log.debug { "Skipped Telegram keyboard edit: ${e.description}" }
                return
            }
            throw e
        }
    }

    suspend fun editMessage(
        chatId: ChatId,
        messageId: Long,
        text: String,
        parseMode: ParseMode?,
        replyMarkup: InlineKeyboardMarkup,
    ) {
        log.debug { "Updating message. \n\tTo: $chatId\n\ttext: $text\n\tkeyboard: ${replyMarkup.pp()}" }
        bot.editMessageText(
            chatId,
            messageId,
            null,
            text,
            parseMode,
            null,
            true,
            replyMarkup,
        )
    }

    suspend fun answerCallbackQuery(id: String) {
        try {
            bot.answerCallbackQuery(id, cacheTime = 5)
        } catch (e: TelegramApiError) {
            if (e.isBenign()) {
                log.debug { "Skipped answering stale callback query: ${e.description}" }
                return
            }
            throw e
        }
    }

    fun start(callbackHandler: suspend (CallbackQuery) -> Unit): Job {
        bot.onCallbackQuery {
            log.debug { "Received callbackQuery.\n$it" }
            callbackHandler(it)
        }
        return scope.launch(context = Dispatchers.IO) {
            bot.start()
        }
    }

    companion object {
        const val UNKNOWN_ERROR_MSG = "Виникла невідома помилка"
    }
}
