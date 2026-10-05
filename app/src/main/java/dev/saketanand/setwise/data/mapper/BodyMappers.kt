package dev.saketanand.setwise.data.mapper

import dev.saketanand.setwise.data.local.entity.BodyMeasurementEntity
import dev.saketanand.setwise.data.local.entity.BodyMeasurementWithSegments
import dev.saketanand.setwise.data.local.entity.BodySegmentEntity
import dev.saketanand.setwise.domain.model.BodyMeasurement
import dev.saketanand.setwise.domain.model.BodyMetric
import dev.saketanand.setwise.domain.model.BodySegment
import dev.saketanand.setwise.domain.model.NormalRange
import dev.saketanand.setwise.domain.model.Rating
import dev.saketanand.setwise.domain.model.ReportDetails
import dev.saketanand.setwise.domain.model.SegmentValues
import java.time.LocalDate
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

fun BodyMeasurementWithSegments.toDomain() = measurement.toDomain(segments)

fun BodyMeasurementEntity.toDomain(segments: List<BodySegmentEntity> = emptyList()) = BodyMeasurement(
    id = id,
    measuredOn = LocalDate.ofEpochDay(measuredOn),
    weightKg = weightKg,
    bodyFatPercent = bodyFatPercent,
    muscleMassKg = muscleMassKg,
    bmrKcal = bmrKcal,
    visceralFat = visceralFat,
    // A source this build doesn't know reads as typed in.
    source = BodyMeasurement.Source.entries.firstOrNull { it.name == source } ?: BodyMeasurement.Source.Manual,
    details = ReportDetails(
        fatMassKg = fatMassKg,
        fatFreeMassKg = fatFreeMassKg,
        bodyWaterL = bodyWaterL,
        bmi = bmi,
        waistHipRatio = waistHipRatio,
        fitnessScore = fitnessScore,
        muscleControlKg = muscleControlKg,
        fatControlKg = fatControlKg,
        ranges = decodeRanges(ranges),
        // Report order (arms, trunk, legs); a segment this build doesn't know is left out.
        segments = segments.mapNotNull { it.toDomain() }.sortedBy { it.segment.ordinal },
    ),
)

fun BodyMeasurement.toEntity() = BodyMeasurementEntity(
    id = id,
    measuredOn = measuredOn.toEpochDay(),
    weightKg = weightKg,
    bodyFatPercent = bodyFatPercent,
    muscleMassKg = muscleMassKg,
    bmrKcal = bmrKcal,
    visceralFat = visceralFat,
    source = source.name,
    fatMassKg = details.fatMassKg,
    fatFreeMassKg = details.fatFreeMassKg,
    bodyWaterL = details.bodyWaterL,
    bmi = details.bmi,
    waistHipRatio = details.waistHipRatio,
    fitnessScore = details.fitnessScore,
    muscleControlKg = details.muscleControlKg,
    fatControlKg = details.fatControlKg,
    ranges = details.ranges.takeIf { it.isNotEmpty() }?.let { ranges ->
        JSON.encodeToString(ranges.entries.associate { (metric, range) -> metric.name to StoredRange(range.low, range.high) })
    },
)

fun BodyMeasurement.segmentEntities(): List<BodySegmentEntity> = details.segments.map {
    BodySegmentEntity(id, it.segment.name, it.leanKg, it.leanRating?.name, it.fatPercent, it.fatKg, it.fatRating?.name)
}

private fun BodySegmentEntity.toDomain(): SegmentValues? {
    val segment = BodySegment.entries.firstOrNull { it.name == segment } ?: return null
    return SegmentValues(segment, leanKg, rating(leanRating), fatPercent, fatKg, rating(fatRating))
}

private fun rating(name: String?) = Rating.entries.firstOrNull { it.name == name }

@Serializable
private data class StoredRange(val low: Double? = null, val high: Double? = null)

/** Unreadable, or a measure this build doesn't know: left out. */
private fun decodeRanges(json: String?): Map<BodyMetric, NormalRange> {
    if (json.isNullOrEmpty()) return emptyMap()
    val stored = runCatching { JSON.decodeFromString<Map<String, StoredRange>>(json) }.getOrDefault(emptyMap())
    return stored.mapNotNull { (name, range) ->
        BodyMetric.entries.firstOrNull { it.name == name }?.let { it to NormalRange(range.low, range.high) }
    }.toMap()
}

private val JSON = Json { ignoreUnknownKeys = true }
