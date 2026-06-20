package io.github.smaugfm.monobudget.ynab

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Duration
import kotlin.time.Duration.Companion.nanoseconds
import kotlin.time.Duration.Companion.seconds

private const val SECONDS_PER_DAY = 86_400L
private const val SECONDS_PER_HOUR = 3_600L
private const val SECONDS_PER_MINUTE = 60L

/**
 * Delay until the next wall-clock boundary aligned from local midnight.
 * E.g. 1h -> :00 each hour; 4h -> 00:00, 04:00, 08:00, …; 15m -> :00, :15, :30, :45.
 */
fun Duration.delayUntilNextClockBoundary(
    timeZone: TimeZone = TimeZone.currentSystemDefault(),
    now: Instant = Clock.System.now(),
): Duration {
    val intervalSeconds = inWholeSeconds.coerceAtLeast(1)
    val local = now.toLocalDateTime(timeZone)
    val secondsSinceMidnight = local.hour * SECONDS_PER_HOUR + local.minute * SECONDS_PER_MINUTE + local.second
    val subSecond = local.nanosecond.nanoseconds

    val nextBoundarySeconds = ((secondsSinceMidnight / intervalSeconds) + 1) * intervalSeconds
    val delaySeconds =
        if (nextBoundarySeconds >= SECONDS_PER_DAY) {
            SECONDS_PER_DAY - secondsSinceMidnight
        } else {
            nextBoundarySeconds - secondsSinceMidnight
        }

    return (delaySeconds.seconds - subSecond).coerceAtLeast(Duration.ZERO)
}
