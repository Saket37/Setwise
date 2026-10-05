package dev.saketanand.setwise.data.repository

import dev.saketanand.setwise.data.local.dao.BodyMeasurementDao
import dev.saketanand.setwise.data.mapper.segmentEntities
import dev.saketanand.setwise.data.mapper.toDomain
import dev.saketanand.setwise.data.mapper.toEntity
import dev.saketanand.setwise.domain.model.BodyMeasurement
import dev.saketanand.setwise.domain.repository.BodyRepository
import dev.saketanand.setwise.domain.repository.UserSettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** [BodyRepository] in the body_measurements table. */
class BodyRepositoryImpl(
    private val bodyMeasurementDao: BodyMeasurementDao,
    private val userSettings: UserSettingsRepository,
) : BodyRepository {

    override fun observeMeasurements(): Flow<List<BodyMeasurement>> =
        bodyMeasurementDao.observeAll().map { rows -> rows.map { it.toDomain() } }

    override suspend fun add(measurement: BodyMeasurement) {
        bodyMeasurementDao.save(measurement.toEntity(), measurement.segmentEntities())
        // The newest weight is the profile's (calorie estimates use it).
        bodyMeasurementDao.newestWeightKg()?.let { userSettings.setBodyWeightKg(it) }
    }

    override suspend fun delete(id: Long) = bodyMeasurementDao.delete(id)
}
