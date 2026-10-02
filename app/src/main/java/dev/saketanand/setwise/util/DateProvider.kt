package dev.saketanand.setwise.util

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * The app's source of "now". Injected instead of calling LocalDate.now() directly, so tests
 * can use a fixed date and screens can react when the day changes.
 */
interface DateProvider {
    val zone: ZoneId

    fun now(): Instant

    /** Today's date, emitted again just after each midnight while collected. */
    fun today(): Flow<LocalDate>
}

class SystemDateProvider : DateProvider {

    override val zone: ZoneId get() = ZoneId.systemDefault()

    override fun now(): Instant = Instant.now()

    override fun today(): Flow<LocalDate> = flow {
        while (true) {
            val zone = zone
            val today = LocalDate.now(zone)
            emit(today)
            val nextMidnight = today.plusDays(1).atStartOfDay(zone).toInstant()
            delay(Duration.between(Instant.now(), nextMidnight).toMillis().coerceAtLeast(1_000))
        }
    }
}
