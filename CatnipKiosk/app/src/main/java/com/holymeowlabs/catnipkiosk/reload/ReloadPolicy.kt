package com.holymeowlabs.catnipkiosk.reload

import com.holymeowlabs.catnipkiosk.settings.ScheduledReload
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

object ReloadPolicy {
    private const val FIRST_RETRY_MS = 5_000L
    private const val MAX_RETRY_MS = 60_000L

    /** Delay before retry [attempt] (1-based): 5 s, 10 s, 20 s, 40 s, then 60 s. */
    fun retryDelayMs(attempt: Int): Long {
        val doublings = (attempt - 1).coerceIn(0, 4)
        return minOf(FIRST_RETRY_MS shl doublings, MAX_RETRY_MS)
    }

    /**
     * Epoch ms of the next scheduled reload, or null when scheduling is off.
     * Stored values are clamped so a bad value can't cause a reload storm or a crash.
     */
    fun nextScheduledReloadMs(schedule: ScheduledReload, lastLoadMs: Long, nowMs: Long, zone: ZoneId): Long? =
        when (schedule) {
            ScheduledReload.Off -> null
            is ScheduledReload.EveryMinutes -> lastLoadMs + schedule.minutes.coerceAtLeast(1) * 60_000L
            is ScheduledReload.DailyAt -> {
                val time = LocalTime.of(schedule.hour.coerceIn(0, 23), schedule.minute.coerceIn(0, 59))
                val now = Instant.ofEpochMilli(nowMs).atZone(zone)
                // ZonedDateTime.of moves a time inside a DST gap forward to a valid instant.
                var next = ZonedDateTime.of(now.toLocalDate(), time, zone)
                if (!next.isAfter(now)) next = ZonedDateTime.of(now.toLocalDate().plusDays(1), time, zone)
                next.toInstant().toEpochMilli()
            }
        }
}
