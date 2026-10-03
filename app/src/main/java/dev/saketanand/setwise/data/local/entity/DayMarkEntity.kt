package dev.saketanand.setwise.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * What the user said about a day without a workout: "REST" or "MISSED" (day check-in, History).
 * Trained days aren't stored here: they're the days with a finished workout.
 */
@Entity(tableName = "day_marks")
data class DayMarkEntity(
    /** LocalDate.toEpochDay(): one mark per calendar day. */
    @PrimaryKey val epochDay: Long,
    val status: String,
    /** Epoch millis of the answer. */
    val markedAt: Long,
)
