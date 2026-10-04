package dev.saketanand.setwise.testing

import dev.saketanand.setwise.domain.model.BodyMeasurement
import dev.saketanand.setwise.domain.repository.BodyRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

/** Body checks in memory, newest first like the real one. */
class FakeBodyRepository(initial: List<BodyMeasurement> = emptyList()) : BodyRepository {
    val measurements = MutableStateFlow(initial)

    override fun observeMeasurements(): Flow<List<BodyMeasurement>> = measurements

    override suspend fun add(measurement: BodyMeasurement) =
        measurements.update { all -> (all.filter { it.id != measurement.id } + measurement).sortedByDescending { it.measuredOn } }

    override suspend fun delete(id: Long) = measurements.update { all -> all.filter { it.id != id } }
}
