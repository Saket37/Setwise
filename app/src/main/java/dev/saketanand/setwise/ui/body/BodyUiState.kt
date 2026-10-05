package dev.saketanand.setwise.ui.body

import androidx.compose.runtime.Immutable
import dev.saketanand.setwise.domain.model.BmrEstimate
import dev.saketanand.setwise.domain.model.BodyMeasurement
import java.time.LocalDate
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

/** Everything [BodyScreen] draws. */
@Immutable
data class BodyUiState(
    val isLoading: Boolean = true,
    /** Newest first. */
    val history: List<BodyMeasurement> = emptyList(),
    val bmr: BmrEstimate? = null,
    /** The newest value of each, from any check. */
    val latest: LatestBody = LatestBody(),
    /** Up to the last [CHART_POINTS] checks with a value, oldest first. */
    val weightTrend: ImmutableList<Double?> = persistentListOf(),
    val bodyFatTrend: ImmutableList<Double?> = persistentListOf(),
    /** A photo is being read. */
    val isReading: Boolean = false,
    /** The last photo had nothing that looked like a report. */
    val readFailed: Boolean = false,
    /** The check-and-save sheet, if open. */
    val editor: BodyEditor? = null,
)

@Immutable
data class LatestBody(
    val weightKg: Double? = null,
    val bodyFatPercent: Double? = null,
    val muscleMassKg: Double? = null,
    val visceralFat: Double? = null,
)

/** A body check being entered or checked: typed in, or read from a report. */
@Immutable
data class BodyEditor(
    /** Changes when a new sheet opens, so its fields start fresh. */
    val key: Long,
    val day: LocalDate,
    val weightKg: Double? = null,
    val bodyFatPercent: Double? = null,
    val muscleMassKg: Double? = null,
    val bmrKcal: Int? = null,
    val visceralFat: Double? = null,
    val fromReport: Boolean = false,
    /** From the report's header, for profile values not set yet ("Also fills your profile: height 169 cm, age 26"). */
    val profileHeightCm: Double? = null,
    val profileAge: Int? = null,
    val profileSex: dev.saketanand.setwise.domain.model.Sex? = null,
    /** The on-device model filled some values. */
    val byModel: Boolean = false,
    /** The last Save had a value that isn't believable, or nothing at all. */
    val isInvalid: Boolean = false,
)

const val CHART_POINTS = 8
