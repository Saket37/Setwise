package dev.saketanand.setwise.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey

/** One body segment (arm, trunk, leg) of a report: its lean and fat, as the report rated them. */
@Entity(
    tableName = "body_segments",
    primaryKeys = ["measurementId", "segment"],
    foreignKeys = [
        ForeignKey(
            entity = BodyMeasurementEntity::class,
            parentColumns = ["id"],
            childColumns = ["measurementId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class BodySegmentEntity(
    val measurementId: Long,
    /** BodySegment name: "RightArm", "Trunk"… */
    val segment: String,
    val leanKg: Double?,
    /** Rating name: "Under", "Normal" or "Over". */
    val leanRating: String?,
    val fatPercent: Double?,
    val fatKg: Double?,
    val fatRating: String?,
)
