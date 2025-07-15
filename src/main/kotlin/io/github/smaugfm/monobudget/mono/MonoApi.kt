package io.github.smaugfm.monobudget.mono

import io.github.oshai.kotlinlogging.KotlinLogging
import io.github.smaugfm.monobank.MonobankPersonalApi
import io.github.smaugfm.monobudget.common.model.financial.BankAccountId
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.reactor.awaitSingleOrNull
import java.net.URI

private val log = KotlinLogging.logger {}

class MonoApi(token: String, val accountId: BankAccountId, val alias: String) {
    init {
        require(token.isNotBlank())
    }

    val api = MonobankPersonalApi(token)

    suspend fun setupWebhook(
        url: URI,
        port: Int,
    ) {
        require(url.toASCIIString() == url.toString())

        val waitForWebhook = CompletableDeferred<Unit>()
        val tempServer =
            embeddedServer(Netty, port = port) {
                routing {
                    get(url.path) {
                        call.response.status(HttpStatusCode.OK)
                        call.respondText("OK\n", ContentType.Text.Plain)
                        log.info { "Webhook setup for $alias successful: $url" }
                        waitForWebhook.complete(Unit)
                    }
                }
            }
        log.info { "Starting temporary webhook setup server..." }
        tempServer.start(wait = false)

        try {
            retrySetWebhook(url)
            waitForWebhook.await()
            log.info { "Webhook setup completed. Stopping temporary server..." }
        } finally {
            tempServer.stop(SERVER_STOP_GRACE_PERIOD, SERVER_STOP_GRACE_PERIOD)
        }
    }

    private suspend fun retrySetWebhook(url: URI) {
        var delayMs = INITIAL_RETRY_DELAY_MS
        repeat(MAX_RETRY_ATTEMPTS) { attempt ->
            try {
                log.info { "Attempt ${attempt + 1} to set webhook..." }
                api.setClientWebhook(url.toString()).awaitSingleOrNull()
                return
            } catch (e: Throwable) {
                val is429 =
                    (e::class.simpleName == "MonoApiResponseError" &&
                        (e.message?.contains("429") == true ||
                         e.message?.contains("Too many requests", ignoreCase = true) == true)) ||
                    (e.message?.contains("429") == true ||
                     e.message?.contains("Too many requests", ignoreCase = true) == true)
                if (is429 && attempt < MAX_RETRY_ATTEMPTS - 1) {
                    log.warn {
                        "Received 429 Too Many Requests from Monobank API. " +
                        "Retrying in ${delayMs}ms (attempt ${attempt + 1}/$MAX_RETRY_ATTEMPTS)..."
                    }
                    kotlinx.coroutines.delay(delayMs)
                    delayMs *= RETRY_BACKOFF_MULTIPLIER
                } else {
                    throw e
                }
            }
        }
    }

    companion object {
        private const val SERVER_STOP_GRACE_PERIOD = 100L
        private const val MAX_RETRY_ATTEMPTS = 5
        private const val INITIAL_RETRY_DELAY_MS = 10_000L // 10 seconds
        private const val RETRY_BACKOFF_MULTIPLIER = 2
    }
}
