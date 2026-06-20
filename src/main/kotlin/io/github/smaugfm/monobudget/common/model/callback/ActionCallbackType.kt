package io.github.smaugfm.monobudget.common.model.callback

import com.elbekd.bot.types.InlineKeyboardButton
import kotlin.reflect.KClass

sealed class ActionCallbackType : CallbackType() {
    data class ChooseCategory(override val transactionId: String) : ActionCallbackType() {
        companion object : ButtonBase(ChooseCategory::class)
    }

    data class CategoryGroupPage(
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
                callbackData = "${CategoryGroupPage::class.simpleName}$DELIMITER$page",
            )

            fun extractPage(callbackData: String): Int = callbackData.substringAfter(DELIMITER).toInt()
        }
    }

    data class ChooseCategoryGroup(
        override val transactionId: String,
        val groupId: String,
    ) : ActionCallbackType() {
        companion object {
            private const val DELIMITER = "#"
            private const val BUTTON_TEXT_MAX_LENGTH = 64

            fun button(
                groupName: String,
                groupId: String,
            ) = InlineKeyboardButton(
                groupName.take(BUTTON_TEXT_MAX_LENGTH),
                callbackData = "${ChooseCategoryGroup::class.simpleName}$DELIMITER$groupId",
            )

            fun extractGroupId(callbackData: String): String = callbackData.substringAfter(DELIMITER)
        }
    }

    data class CategoryPage(
        override val transactionId: String,
        val groupId: String,
        val page: Int,
    ) : ActionCallbackType() {
        companion object {
            private const val DELIMITER = "#"

            fun button(
                label: String,
                groupId: String,
                page: Int,
            ) = InlineKeyboardButton(
                label,
                callbackData = "${CategoryPage::class.simpleName}$DELIMITER$groupId$DELIMITER$page",
            )

            fun extract(callbackData: String): Pair<String, Int> {
                val parts = callbackData.split(DELIMITER)
                return parts[1] to parts[2].toInt()
            }
        }
    }

    data class BackToCategoryGroups(override val transactionId: String) : ActionCallbackType() {
        companion object {
            fun button() =
                InlineKeyboardButton(
                    "◀️ Back",
                    callbackData = BackToCategoryGroups::class.simpleName!!,
                )
        }
    }

    data class CancelCategoryPicker(override val transactionId: String) : ActionCallbackType() {
        companion object {
            fun button() =
                InlineKeyboardButton(
                    "❌ Cancel",
                    callbackData = CancelCategoryPicker::class.simpleName!!,
                )
        }
    }

    companion object {
        fun classFromCallbackData(callbackData: String?): KClass<out ActionCallbackType>? =
            when {
                callbackData == null -> null
                callbackData.startsWith("${CategoryPage::class.simpleName}#") -> CategoryPage::class
                callbackData.startsWith("${CategoryGroupPage::class.simpleName}#") -> CategoryGroupPage::class
                callbackData.startsWith("${ChooseCategoryGroup::class.simpleName}#") ->
                    ChooseCategoryGroup::class
                callbackData == BackToCategoryGroups::class.simpleName -> BackToCategoryGroups::class
                callbackData == CancelCategoryPicker::class.simpleName -> CancelCategoryPicker::class
                else ->
                    ActionCallbackType::class
                        .sealedSubclasses.find { callbackData == it.simpleName }
            }
    }
}
