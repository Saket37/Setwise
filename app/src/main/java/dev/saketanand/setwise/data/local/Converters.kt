package dev.saketanand.setwise.data.local

import androidx.room.TypeConverter
import dev.saketanand.setwise.domain.model.CardioMetric

/** Register on the database with @TypeConverters(Converters::class). */
class Converters {

    @TypeConverter
    fun fromCardioMetrics(metrics: List<CardioMetric>?): String? =
        metrics?.joinToString(separator = ",") { it.name }

    @TypeConverter
    fun toCardioMetrics(value: String?): List<CardioMetric>? =
        value?.split(",")?.filter { it.isNotBlank() }?.map { CardioMetric.valueOf(it) }
}
