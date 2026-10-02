package dev.saketanand.setwise.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import dev.saketanand.setwise.domain.model.CalorieMethod
import dev.saketanand.setwise.domain.model.CardioMetric
import dev.saketanand.setwise.domain.model.ExerciseType

@Entity(
    tableName = "exercises",
    // Unique name makes seeding idempotent (insert with OnConflictStrategy.IGNORE).
    indices = [Index(value = ["name"], unique = true)],
)
data class ExerciseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val type: ExerciseType,
    val muscleGroup: String,
    val equipment: String,
    val defaultRestSec: Int,
    val isTimed: Boolean = false,
    val isCustom: Boolean = false,
    /** Stored as a comma-separated string via Converters. */
    val metrics: List<CardioMetric>? = null,
    val calorieMethod: CalorieMethod? = null,
    val met: Double? = null,
)
