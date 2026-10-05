package dev.saketanand.setwise.data.local.entity

import androidx.room.Embedded
import androidx.room.Relation

/** A body check with its segments (none when typed in). */
data class BodyMeasurementWithSegments(
    @Embedded val measurement: BodyMeasurementEntity,
    @Relation(parentColumn = "id", entityColumn = "measurementId")
    val segments: List<BodySegmentEntity>,
)
