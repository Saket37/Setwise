package dev.saketanand.setwise.data.mapper

import dev.saketanand.setwise.data.local.entity.BodyMeasurementEntity
import dev.saketanand.setwise.domain.model.BodyMeasurement
import java.time.LocalDate

fun BodyMeasurementEntity.toDomain() = BodyMeasurement(
    id = id,
    measuredOn = LocalDate.ofEpochDay(measuredOn),
    weightKg = weightKg,
    bodyFatPercent = bodyFatPercent,
    muscleMassKg = muscleMassKg,
    bmrKcal = bmrKcal,
    visceralFat = visceralFat,
    // A source this build doesn't know reads as typed in.
    source = BodyMeasurement.Source.entries.firstOrNull { it.name == source } ?: BodyMeasurement.Source.Manual,
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
)
