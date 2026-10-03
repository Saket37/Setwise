package dev.saketanand.setwise.testing

import dev.saketanand.setwise.domain.model.DayStatus
import dev.saketanand.setwise.domain.repository.DayMarkRepository
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

/** In-memory rest / missed marks. */
class FakeDayMarkRepository(initial: Map<LocalDate, DayStatus> = emptyMap()) : DayMarkRepository {
    val marks = MutableStateFlow(initial)

    override fun observeMarks(): Flow<Map<LocalDate, DayStatus>> = marks

    override suspend fun mark(dates: Collection<LocalDate>, status: DayStatus?, at: Instant) = marks.update { current ->
        if (status == null) current - dates.toSet() else current + dates.associateWith { status }
    }
}
