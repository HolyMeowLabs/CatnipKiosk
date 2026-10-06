package com.holymeowlabs.catnipkiosk.reload

import com.google.common.truth.Truth.assertThat
import com.holymeowlabs.catnipkiosk.settings.ScheduledReload
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Test

class ReloadPolicyTest {

    private val ny = ZoneId.of("America/New_York")
    private fun ms(y: Int, mo: Int, d: Int, h: Int, mi: Int, s: Int = 0) =
        ZonedDateTime.of(y, mo, d, h, mi, s, 0, ny).toInstant().toEpochMilli()

    @Test
    fun retryDelaysDoubleThenCapAtSixtySeconds() {
        assertThat((1..7).map { ReloadPolicy.retryDelayMs(it) })
            .containsExactly(5_000L, 10_000L, 20_000L, 40_000L, 60_000L, 60_000L, 60_000L).inOrder()
        assertThat(ReloadPolicy.retryDelayMs(1_000)).isEqualTo(60_000L)
    }

    @Test
    fun offHasNoScheduledReload() {
        assertThat(ReloadPolicy.nextScheduledReloadMs(ScheduledReload.Off, 0, 0, ny)).isNull()
    }

    @Test
    fun everyMinutesCountsFromTheLastLoad() {
        val last = ms(2026, 10, 6, 9, 0)
        assertThat(ReloadPolicy.nextScheduledReloadMs(ScheduledReload.EveryMinutes(60), last, last + 5_000, ny))
            .isEqualTo(last + 3_600_000)
    }

    @Test
    fun nonPositiveIntervalsAreTreatedAsOneMinuteNotAReloadStorm() {
        val last = ms(2026, 10, 6, 9, 0)
        for (minutes in listOf(0, -5)) {
            assertThat(ReloadPolicy.nextScheduledReloadMs(ScheduledReload.EveryMinutes(minutes), last, last, ny))
                .isEqualTo(last + 60_000)
        }
    }

    @Test
    fun outOfRangeDailyTimeIsClampedInsteadOfThrowing() {
        val next = ReloadPolicy.nextScheduledReloadMs(ScheduledReload.DailyAt(99, 99), 0, ms(2026, 10, 6, 9, 0), ny)
        assertThat(next).isEqualTo(ms(2026, 10, 6, 23, 59))
    }

    @Test
    fun dailyBeforeTheTimeIsToday() {
        assertThat(ReloadPolicy.nextScheduledReloadMs(ScheduledReload.DailyAt(3, 0), 0, ms(2026, 10, 6, 2, 59), ny))
            .isEqualTo(ms(2026, 10, 6, 3, 0))
    }

    @Test
    fun dailyAtOrAfterTheTimeIsTomorrow() {
        val daily = ScheduledReload.DailyAt(3, 0)
        assertThat(ReloadPolicy.nextScheduledReloadMs(daily, 0, ms(2026, 10, 6, 3, 0), ny)).isEqualTo(ms(2026, 10, 7, 3, 0))
        assertThat(ReloadPolicy.nextScheduledReloadMs(daily, 0, ms(2026, 10, 6, 3, 0, 1), ny)).isEqualTo(ms(2026, 10, 7, 3, 0))
    }

    @Test
    fun dailyTimeInsideASpringForwardGapResolvesToAValidLaterInstant() {
        // 2027-03-14 02:00–03:00 does not exist in New York.
        val next = ReloadPolicy.nextScheduledReloadMs(ScheduledReload.DailyAt(2, 30), 0, ms(2027, 3, 14, 1, 0), ny)!!
        assertThat(next).isGreaterThan(ms(2027, 3, 14, 1, 0))
        assertThat(next).isLessThan(ms(2027, 3, 14, 4, 0))
    }
}
