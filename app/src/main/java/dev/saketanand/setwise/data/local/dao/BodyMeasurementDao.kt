package dev.saketanand.setwise.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import dev.saketanand.setwise.data.local.entity.BodyMeasurementEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BodyMeasurementDao {

    /** Newest first; on the same day, the one added last first. */
    @Query("SELECT * FROM body_measurements ORDER BY measuredOn DESC, id DESC")
    fun observeAll(): Flow<List<BodyMeasurementEntity>>

    /** Adds it, or edits the one with its id. */
    @Upsert
    suspend fun upsert(measurement: BodyMeasurementEntity)

    /** Copies measurements in; one whose id is already here is kept as it is. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(measurements: List<BodyMeasurementEntity>)

    @Query("DELETE FROM body_measurements WHERE id = :id")
    suspend fun delete(id: Long)

    /** The newest measurement's weight, if any has one. */
    @Query("SELECT weightKg FROM body_measurements WHERE weightKg IS NOT NULL ORDER BY measuredOn DESC, id DESC LIMIT 1")
    suspend fun newestWeightKg(): Double?
}
