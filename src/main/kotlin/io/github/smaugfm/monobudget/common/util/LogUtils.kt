package io.github.smaugfm.monobudget.common.util

import io.github.oshai.kotlinlogging.KLogger
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.plugins.ResponseException

private val log = KotlinLogging.logger {}

fun logExceptionDetails(
    e: Exception,
    context: String = "",
) {
    val prefix = if (context.isNotEmpty()) "[$context] " else ""

    log.error { "${prefix}Exception class: ${e::class.qualifiedName}" }
    log.error { "${prefix}Exception toString(): $e" }
    log.error { "${prefix}Exception message: ${e.message}" }
    log.error { "${prefix}Exception cause: ${e.cause}" }
    log.error { "${prefix}Exception stackTrace: ${e.stackTraceToString()}" }

    e::class.members
        .filter { it.parameters.size == 1 }
        .forEach { member ->
            try {
                val value = member.call(e)
                log.error { "${prefix}Property ${member.name}: $value" }
            } catch (ex: Exception) {
                log.error { "${prefix}Property ${member.name}: <unavailable> (${ex.message})" }
            }
        }
}

@Suppress("LongParameterList")
inline fun <reified T : Any> logError(
    serviceName: String,
    logger: KLogger?,
    methodName: String,
    block: () -> T,
    error: (ResponseException) -> Unit,
) = catchAndLog(
    logger,
    methodName,
    { exception ->
        exception
            .also(error)
            .toString()
    },
) {
    logger?.debug { "Performing $serviceName request $methodName" }
    block().also {
        logger?.debug { "Response:\n\t${it.pp()}" }
    }
}

inline fun <reified T> catchAndLog(
    logger: KLogger?,
    methodName: String,
    errorHandler: (ResponseException) -> String,
    block: () -> T,
): T =
    try {
        block()
    } catch (e: ResponseException) {
        val error = errorHandler(e)
        logger?.error { "Request failed $methodName. Error response:\n\t$error" }
        throw e
    }
