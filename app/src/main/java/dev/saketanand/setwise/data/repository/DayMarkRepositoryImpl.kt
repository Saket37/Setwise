package dev.saketanand.setwise.data.repository

import dev.saketanand.setwise.data.local.dao.DayMarkDao
import dev.saketanand.setwise.data.local.entity.DayMarkEntity
import dev.saketanand.setwise.domain.model.DayStatus
import dev.saketanand.setwise.domain.repository.DayMarkRepository
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class DayMarkRepositoryImpl(
    private val dayMarkDao: DayMarkDao,
) : DayMarkRepository {

    override fun observeMarks(): Flow<Map<LocalDate, DayStatus>> =
        dayMarkDao.observeAll().map { rows ->
            // An unknown status (e.g. from a newer app version) is ignored rather than crashing.
            rows.mapNotNull { row ->
                DayStatus.entries.firstOrNull { it.name.equals(row.status, ignoreCase = true) }
                    ?.let { LocalDate.ofEpochDay(row.epochDay) to it }
            }.toMap()
        }

    override suspend fun mark(dates: Collection<LocalDate>, status: DayStatus?, at: Instant) {
        if (status == null) {
            dates.forEach { dayMarkDao.delete(it.toEpochDay()) }
        } else {
            dayMarkDao.upsert(dates.map { DayMarkEntity(it.toEpochDay(), status.name.uppercase(), at.toEpochMilli()) })
        }
    }
}
