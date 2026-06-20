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
                iconText(if (pressed) PRESSED_CHAR else "➕", "Payee")

            TransactionUpdateType.Uncategorize::class ->
                iconText(if (pressed) PRESSED_CHAR else "❌", "Categorize")

            TransactionUpdateType.Unapprove::class ->
                iconText("🚫", "Unapprove")

            TransactionUpdateType.Approve::class ->
                iconText("✅", "Approve")

            ActionCallbackType.ChooseCategory::class ->
                iconText(if (pressed) PRESSED_CHAR else "⤴️", "Categorize")

            else -> error("Unsupported class $cls")
        }

    private fun iconText(
        icon: String,
        text: String,
    ) = "$icon $text"

    companion object {
        private const val PRESSED_CHAR: String = "✅"
    }
}
