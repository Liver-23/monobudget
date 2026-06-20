package io.github.smaugfm.monobudget.ynab

import assertk.assertThat
import assertk.assertions.isEqualTo
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class ClockAlignedPollDelayTest {
    private val utc = TimeZone.UTC

    @Test
    fun `1h aligns to next hour`() {
        val now = Instant.parse("2026-06-20T15:57:30Z")
        assertThat(1.hours.delayUntilNextClockBoundary(utc, now))
            .isEqualTo(2.minutes + 30.seconds)
    }

    @Test
    fun `4h aligns to next 4-hour boundary`() {
        val now = Instant.parse("2026-06-20T15:57:30Z")
        assertThat(4.hours.delayUntilNextClockBoundary(utc, now))
            .isEqualTo(2.minutes + 30.seconds)
    }

    @Test
    fun `4h on boundary waits until next slot`() {
        val now = Instant.parse("2026-06-20T16:00:00Z")
        assertThat(4.hours.delayUntilNextClockBoundary(utc, now))
            .isEqualTo(4.hours)
    }

    @Test
    fun `15m aligns to quarter hour`() {
        val now = Instant.parse("2026-06-20T15:57:30Z")
        assertThat(15.minutes.delayUntilNextClockBoundary(utc, now))
            .isEqualTo(2.minutes + 30.seconds)
    }

    @Test
    fun `wraps to midnight when interval crosses day end`() {
        val now = Instant.parse("2026-06-20T23:30:00Z")
        assertThat(1.hours.delayUntilNextClockBoundary(utc, now))
            .isEqualTo(30.minutes)
    }
}
