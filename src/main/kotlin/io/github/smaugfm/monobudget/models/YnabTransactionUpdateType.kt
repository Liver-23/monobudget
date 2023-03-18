package io.github.smaugfm.monobudget.models

import com.elbekd.bot.types.Message
import com.elbekd.bot.types.MessageEntity
import io.ktor.util.logging.error
import mu.KotlinLogging
import java.util.UUID
import kotlin.reflect.KClass

private val logger = KotlinLogging.logger { }

sealed class YnabTransactionUpdateType {
    abstract val transactionId: String

    data class Uncategorize(override val transactionId: String) : YnabTransactionUpdateType()
    data class Unapprove(override val transactionId: String) : YnabTransactionUpdateType()
    data class Unknown(override val transactionId: String) : YnabTransactionUpdateType()
    data class MakePayee(override val transactionId: String, val payee: String) : YnabTransactionUpdateType()

    companion object {
        fun deserialize(callbackData: String, message: Message): YnabTransactionUpdateType? {
            val cls =
                YnabTransactionUpdateType::class.sealedSubclasses.find { it.simpleName == callbackData }
            if (cls == null) {
                logger.error("Cannot find TransactionActionType. Data: $callbackData")
                return null
            }

            val (payee, transactionId) =
                extractDescriptionAndTransactionId(message) ?: return null

            return when (cls) {
                Uncategorize::class -> Uncategorize(transactionId)
                Unapprove::class -> Unapprove(transactionId)
                Unknown::class -> Unknown(transactionId)
                MakePayee::class -> MakePayee(transactionId, payee)
                else -> throw IllegalArgumentException("cls: ${cls.simpleName}")
            }
        }

        inline fun <reified T : YnabTransactionUpdateType> serialize(): String {
            return T::class.simpleName!!
        }

        fun KClass<out YnabTransactionUpdateType>.buttonWord(): String {
            return when (this) {
                Uncategorize::class -> "категорию"
                Unapprove::class -> "unapprove"
                Unknown::class -> "невыясненные"
                MakePayee::class -> "payee"
                else -> throw IllegalArgumentException("Unknown ${YnabTransactionUpdateType::class.simpleName} $this")
            }
        }

        fun KClass<out YnabTransactionUpdateType>.buttonSymbol(): String {
            return when (this) {
                Uncategorize::class -> "❌"
                Unapprove::class -> "🚫"
                Unknown::class -> "➡️"
                MakePayee::class -> "➕"
                else -> throw IllegalArgumentException("Unknown ${YnabTransactionUpdateType::class.simpleName} $this")
            }
        }

        inline fun <reified T : YnabTransactionUpdateType> buttonText(pressed: Boolean): String = if (pressed) {
            "$pressedChar${T::class.buttonWord()}"
        } else {
            "${T::class.buttonSymbol()}${T::class.buttonWord()}"
        }

        private fun extractDescriptionAndTransactionId(message: Message): Pair<String, String>? {
            val text = message.text!!
            val id = try {
                UUID.fromString(text.substring(text.length - UUIDwidth, text.length))
            } catch (e: IllegalArgumentException) {
                logger.error(e)
                return null
            }.toString()
            val payee =
                message.entities.find { it.type == MessageEntity.Type.BOLD }?.run {
                    text.substring(offset, offset + length)
                } ?: return null

            return Pair(payee, id)
        }

        const val pressedChar: Char = '✅'
        private const val UUIDwidth = 36
    }
}
