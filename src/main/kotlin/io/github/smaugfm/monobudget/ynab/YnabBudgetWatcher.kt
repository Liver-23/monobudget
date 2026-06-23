package io.github.smaugfm.monobudget.ynab

import com.elbekd.bot.model.ChatId
import com.elbekd.bot.types.ParseMode
import io.github.oshai.kotlinlogging.KotlinLogging
import io.github.smaugfm.monobudget.common.model.settings.Settings
import io.github.smaugfm.monobudget.common.model.settings.YnabBudgetWatcherSettings
import io.github.smaugfm.monobudget.common.notify.TelegramApi
import io.github.smaugfm.monobudget.ynab.model.YnabTransactionDetail
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import org.koin.core.annotation.Single
import java.util.Currency

private val log = KotlinLogging.logger {}

@Single(createdAtStart = true)
@Suppress("LongParameterList")
class YnabBudgetWatcher(
    private val settings: Settings,
    private val api: YnabApi,
    private val stateRepo: YnabWatcherStateRepository,
    private val messageFormatter: YnabWatcherMessageFormatter,
    private val budgetDataService: YnabBudgetDataService,
    private val telegramApi: TelegramApi,
    private val scope: CoroutineScope,
) {
    init {
        val watcherSettings = settings.ynabBudgetWatcher
        if (watcherSettings == null || !watcherSettings.enabled) {
            log.info { "YNAB budget watcher is disabled" }
        } else {
            scope.launch(Dispatchers.IO) {
                runWatcher(watcherSettings)
            }
        }
    }

    private suspend fun runWatcher(watcherSettings: YnabBudgetWatcherSettings) {
        val pollTimeZone = TimeZone.currentSystemDefault()
        log.info {
            "Starting YNAB watcher for budget=${watcherSettings.name}(${watcherSettings.budgetId}), " +
                "interval=${watcherSettings.pollInterval}, timezone=$pollTimeZone (clock-aligned)"
        }

        while (true) {
            try {
                pollOnce(watcherSettings)
            } catch (e: Throwable) {
                log.error(e) { "YNAB watcher polling failed for budget ${watcherSettings.budgetId}" }
            }
            val wait = watcherSettings.pollInterval.delayUntilNextClockBoundary(pollTimeZone)
            log.debug {
                "YNAB watcher next poll in $wait (at ${Clock.System.now().plus(wait)})"
            }
            delay(wait)
        }
    }

    private suspend fun pollOnce(watcherSettings: YnabBudgetWatcherSettings) {
        val existingState = stateRepo.load(watcherSettings.budgetId)
        val delta = api.getDeltaTransactions(watcherSettings.budgetId, existingState?.serverKnowledge)

        if (existingState == null) {
            // Initial snapshot: store knowledge and known IDs, skip historical notifications.
            stateRepo.save(
                YnabWatcherState(
                    budgetId = watcherSettings.budgetId,
                    serverKnowledge = delta.serverKnowledge,
                    notifiedTransactionIds = delta.transactions.map { it.id }.toSet(),
                ),
            )
            log.info {
                "YNAB watcher initialized for ${watcherSettings.budgetId}; " +
                    "captured ${delta.transactions.size} existing transactions"
            }
            return
        }

        val currency = budgetDataService.budgetCurrency(watcherSettings.budgetId)
        val newTransactions = filterNewTransactions(watcherSettings, delta, existingState)
        val notifyResult = sendNewNotifications(watcherSettings, newTransactions, currency, existingState)
        finalizePollState(existingState, delta, newTransactions, notifyResult)

        if (notifyResult.sentCount > 0) {
            log.info {
                "YNAB watcher: sent ${notifyResult.sentCount} new notifications for budget ${watcherSettings.budgetId}"
            }
        }
    }

    private fun filterNewTransactions(
        watcherSettings: YnabBudgetWatcherSettings,
        delta: YnabDeltaTransactions,
        existingState: YnabWatcherState,
    ) = delta.transactions
        .asSequence()
        .filterNot { it.deleted }
        .filterNot { it.accountId in watcherSettings.excludedAccountIds }
        .filterNot { it.id in existingState.notifiedTransactionIds }
        .toList()

    private suspend fun sendNewNotifications(
        watcherSettings: YnabBudgetWatcherSettings,
        newTransactions: List<YnabTransactionDetail>,
        currency: Currency,
        existingState: YnabWatcherState,
    ): NotifyResult {
        var notifiedIds = existingState.notifiedTransactionIds
        var sentCount = 0
        var notifyFailed = false

        for (transaction in newTransactions) {
            try {
                val message = messageFormatter.format(watcherSettings, transaction, currency)
                telegramApi.sendMessage(
                    chatId = ChatId.IntegerId(watcherSettings.telegramChatId),
                    text = message.message,
                    parseMode = ParseMode.Html,
                    disableNotification = !message.notifyTelegramApp,
                    replyMarkup = message.markup,
                )
                notifiedIds = notifiedIds + transaction.id
                sentCount++
                stateRepo.save(
                    existingState.copy(
                        serverKnowledge = existingState.serverKnowledge,
                        notifiedTransactionIds = notifiedIds,
                    ),
                )
            } catch (e: Throwable) {
                notifyFailed = true
                log.error(e) {
                    "YNAB watcher: failed to notify for transaction ${transaction.id} " +
                        "in budget ${watcherSettings.budgetId}"
                }
            }
        }

        return NotifyResult(notifiedIds, sentCount, notifyFailed)
    }

    private suspend fun finalizePollState(
        existingState: YnabWatcherState,
        delta: YnabDeltaTransactions,
        newTransactions: List<YnabTransactionDetail>,
        notifyResult: NotifyResult,
    ) {
        if (newTransactions.isNotEmpty() && notifyResult.notifyFailed) {
            return
        }

        stateRepo.save(
            existingState.copy(
                serverKnowledge = delta.serverKnowledge,
                notifiedTransactionIds = notifyResult.notifiedIds + delta.transactions.map { it.id },
            ),
        )
    }

    private data class NotifyResult(
        val notifiedIds: Set<String>,
        val sentCount: Int,
        val notifyFailed: Boolean,
    )
}
