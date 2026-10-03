package dev.saketanand.setwise.domain.repository

import dev.saketanand.setwise.domain.model.DayStatus
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow

/** Rest / missed answers for days without a workout. */
interface DayMarkRepository {

    fun observeMarks(): Flow<Map<LocalDate, DayStatus>>

    /** Sets (or changes) the answer for [dates]; null removes it. */
    suspend fun mark(dates: Collection<LocalDate>, status: DayStatus?, at: Instant)
}
