package io.github.smaugfm.monobudget.common.model.callback

import com.elbekd.bot.types.InlineKeyboardButton
import kotlin.reflect.KClass

open class ButtonBase(private val cls: KClass<out CallbackType>) {
    fun button(pressed: PressedButtons) = button(pressed.isPressed(cls))

    private fun button(pressed: Boolean) =
        InlineKeyboardButton(
            buttonText(pressed),
            callbackData = cls.simpleName,
        )

    private fun buttonText(pressed: Boolean) =
        when (cls) {
            TransactionUpdateType.MakePayee::class ->
                (if (pressed) PRESSED_CHAR else "➕") + "Payee"

            TransactionUpdateType.Uncategorize::class ->
                (if (pressed) PRESSED_CHAR else "❌") + "Categorize"

            TransactionUpdateType.Unapprove::class ->
                (if (pressed) PRESSED_CHAR else "🚫") + "Unapprove"

            ActionCallbackType.ChooseCategory::class ->
                (if (pressed) PRESSED_CHAR else "⤴️") + "Categorize"

            else -> error("Unsupported class $cls")
        }

    companion object {
        private const val PRESSED_CHAR: String = "✅"
    }
}
