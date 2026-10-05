package dev.saketanand.setwise.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import dev.saketanand.setwise.data.local.entity.BodyMeasurementEntity
import dev.saketanand.setwise.data.local.entity.BodyMeasurementWithSegments
import dev.saketanand.setwise.data.local.entity.BodySegmentEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BodyMeasurementDao {

    /** Newest first; on the same day, the one added last first. */
    @Transaction
    @Query("SELECT * FROM body_measurements ORDER BY measuredOn DESC, id DESC")
    fun observeAll(): Flow<List<BodyMeasurementWithSegments>>

    /** Adds it, or edits the one with its id. */
    @Upsert
    suspend fun upsert(measurement: BodyMeasurementEntity)

    @Query("DELETE FROM body_segments WHERE measurementId = :measurementId")
    suspend fun deleteSegments(measurementId: Long)

    @Insert
    suspend fun insertSegments(segments: List<BodySegmentEntity>)

    /** A check and its segments, replacing any it had. */
    @Transaction
    suspend fun save(measurement: BodyMeasurementEntity, segments: List<BodySegmentEntity>) {
        upsert(measurement)
        deleteSegments(measurement.id)
        if (segments.isNotEmpty()) insertSegments(segments)
    }

    /** Copies measurements in; one whose id is already here is kept as it is. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(measurements: List<BodyMeasurementEntity>)

    @Query("DELETE FROM body_measurements WHERE id = :id")
    suspend fun delete(id: Long)

    /** The newest measurement's weight, if any has one. */
    @Query("SELECT weightKg FROM body_measurements WHERE weightKg IS NOT NULL ORDER BY measuredOn DESC, id DESC LIMIT 1")
    suspend fun newestWeightKg(): Double?
}
