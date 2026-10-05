package dev.saketanand.setwise.ui.body

import androidx.compose.runtime.Immutable
import dev.saketanand.setwise.domain.model.BmrEstimate
import dev.saketanand.setwise.domain.model.BodyMeasurement
import dev.saketanand.setwise.domain.model.ReportDetails
import dev.saketanand.setwise.ui.designsystem.components.ChartPoint
import java.time.LocalDate
import kotlinx.collections.immutable.ImmutableList

/** Everything [BodyScreen] draws. */
@Immutable
data class BodyUiState(
    val isLoading: Boolean = true,
    /** Newest first. */
    val history: List<BodyMeasurement> = emptyList(),
    val bmr: BmrEstimate? = null,
    /** The newest value of each, from any check. */
    val latest: LatestBody = LatestBody(),
    /** The last 3 months, for the Progress card (design 15); null with no checks. */
    val progress: RecentProgress? = null,
    /** Each check's weight change from the one before it, by id. */
    val weightChanges: Map<Long, Double> = emptyMap(),
    /** The newest check with a full report: its fitness score and a link to it. */
    val latestReport: BodyMeasurement? = null,
    /** A photo is being read. */
    val isReading: Boolean = false,
    /** The last photo had nothing that looked like a report. */
    val readFailed: Boolean = false,
    /** The check-and-save sheet, if open. */
    val editor: BodyEditor? = null,
)

@Immutable
data class LatestBody(
    /** The newest check's day and whether it came from a report ("Latest · Tue, 6 Oct · From a report"). */
    val measuredOn: LocalDate? = null,
    val fromReport: Boolean = false,
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
    /** Today, when the sheet opened: the latest day a reading can be for (#128). */
    val latestDay: LocalDate,
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
    /** What else the report said (segments, body water, ranges…): saved with the check, not edited here. */
    val details: ReportDetails = ReportDetails(),
    /** The last Save had a value that isn't believable, or nothing at all. */
    val isInvalid: Boolean = false,
)

/** What changed in the last 3 months (each null without two values), and the weight line. */
@Immutable
data class RecentProgress(
    val since: LocalDate?,
    val weightChange: Double?,
    val fatChange: Double?,
    val muscleChange: Double?,
    /** x: epoch days. */
    val weightPoints: ImmutableList<ChartPoint>,
)
