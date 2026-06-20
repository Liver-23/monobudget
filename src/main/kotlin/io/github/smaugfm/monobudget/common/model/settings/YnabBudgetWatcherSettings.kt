package io.github.smaugfm.monobudget.common.model.settings

import io.github.smaugfm.monobudget.common.model.serializer.SpringLikeDurationDeserializer
import kotlinx.serialization.Serializable
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours

@Serializable
data class YnabBudgetWatcherSettings(
    val enabled: Boolean = true,
    val budgetId: String,
    val name: String,
    val telegramChatId: Long,
    val excludedAccountIds: List<String> = emptyList(),
    @Serializable(SpringLikeDurationDeserializer::class)
    val pollInterval: Duration = 1.hours,
)
