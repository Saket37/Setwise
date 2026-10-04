package dev.saketanand.setwise.domain.repository

import dev.saketanand.setwise.domain.model.BodyMeasurement
import kotlinx.coroutines.flow.Flow

/** Body checks (weight, body fat, BMR…), typed in or read from reports. */
interface BodyRepository {
    /** Newest first. */
    fun observeMeasurements(): Flow<List<BodyMeasurement>>

    /** Saves it; the newest weight also becomes the profile's body weight (calories use it). */
    suspend fun add(measurement: BodyMeasurement)

    suspend fun delete(id: Long)
}
