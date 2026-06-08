package io.github.smaugfm.monobudget.common.model.callback

import com.elbekd.bot.types.InlineKeyboardButton
import kotlin.reflect.KClass

sealed class ActionCallbackType : CallbackType() {
    data class ChooseCategory(override val transactionId: String) : ActionCallbackType() {
        companion object : ButtonBase(ChooseCategory::class)
    }

    data class CategoryPage(
        override val transactionId: String,
        val page: Int,
    ) : ActionCallbackType() {
        companion object {
            private const val DELIMITER = "#"

            fun button(
                label: String,
                page: Int,
            ) = InlineKeyboardButton(
                label,
                callbackData = "${CategoryPage::class.simpleName}$DELIMITER$page",
            )

            fun extractPage(callbackData: String): Int = callbackData.substringAfter(DELIMITER).toInt()
        }
    }

    companion object {
        fun classFromCallbackData(callbackData: String?): KClass<out ActionCallbackType>? {
            if (callbackData == null) {
                return null
            }
            if (callbackData.startsWith("${CategoryPage::class.simpleName}#")) {
                return CategoryPage::class
            }
            return ActionCallbackType::class
                .sealedSubclasses.find { callbackData == it.simpleName }
        }
    }
}
