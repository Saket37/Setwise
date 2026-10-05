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
    // A full report's other values (null when typed in).
    val fatMassKg: Double? = null,
    val fatFreeMassKg: Double? = null,
    val bodyWaterL: Double? = null,
    val bmi: Double? = null,
    val waistHipRatio: Double? = null,
    val fitnessScore: Int? = null,
    /** The report's suggested change ("Muscle-Fat Control"). */
    val muscleControlKg: Double? = null,
    val fatControlKg: Double? = null,
    /** The normal range printed beside each value, as JSON ({"Weight": {"low": 53.4, "high": 72.2}}). */
    val ranges: String? = null,
)
