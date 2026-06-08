package io.github.smaugfm.monobudget.common.notify

import com.elbekd.bot.model.TelegramApiError

internal fun TelegramApiError.isBenign(): Boolean =
    description.contains("message is not modified") ||
        description.contains("query is too old")
