package dev.saketanand.setwise.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** One body check (BodyMeasurement): typed in, or read from a body composition report. */
@Entity(tableName = "body_measurements", indices = [Index("measuredOn")])
data class BodyMeasurementEntity(
    /** Chosen when it's added (epoch millis then), so saving the same id again edits it. */
    @PrimaryKey val id: Long,
    /** LocalDate.toEpochDay(). */
    val measuredOn: Long,
    val weightKg: Double?,
    val bodyFatPercent: Double?,
    val muscleMassKg: Double?,
    val bmrKcal: Int?,
    val visceralFat: Double?,
    /** BodyMeasurement.Source name: "Manual" or "Report". */
    val source: String,
)
