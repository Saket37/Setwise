package dev.saketanand.setwise.data.mapper

import dev.saketanand.setwise.data.local.entity.ExerciseEntity
import dev.saketanand.setwise.data.seed.ExerciseSeedDto
import dev.saketanand.setwise.domain.model.CalorieMethod
import dev.saketanand.setwise.domain.model.CardioMetric
import dev.saketanand.setwise.domain.model.ExerciseType

/**
 * Built-in exercise from the seed file. valueOf() throws on an unknown value, so a typo in
 * exercises.json fails loudly (see ExerciseSeedTest) instead of being stored silently.
 */
fun ExerciseSeedDto.toEntity(): ExerciseEntity = ExerciseEntity(
    name = name,
    type = ExerciseType.valueOf(type),
    muscleGroup = muscleGroup,
    equipment = equipment,
    defaultRestSec = defaultRestSec,
    isTimed = timed,
    isCustom = false,
    // JSON metrics are lowercase ("duration"); enum constants are uppercase.
    metrics = metrics?.map { CardioMetric.valueOf(it.uppercase()) },
    calorieMethod = calorieMethod?.let { CalorieMethod.valueOf(it) },
    met = met,
)
