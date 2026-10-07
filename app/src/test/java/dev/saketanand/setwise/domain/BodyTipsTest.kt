package dev.saketanand.setwise.domain

import dev.saketanand.setwise.domain.model.BodyMeasurement
import dev.saketanand.setwise.domain.model.BodySegment
import dev.saketanand.setwise.domain.model.BodyTip
import dev.saketanand.setwise.domain.model.BodyTips
import dev.saketanand.setwise.domain.model.Rating
import dev.saketanand.setwise.domain.model.ReportDetails
import dev.saketanand.setwise.domain.model.SegmentValues
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Made-up reports. */
class BodyTipsTest {

    /** [leanUnder]: segments whose lean mass is rated Under; [fatOver]: whose fat is rated Over. */
    private fun report(
        day: LocalDate,
        fatKg: Double,
        muscle: Double,
        leanUnder: Set<BodySegment> = emptySet(),
        fatOver: Set<BodySegment> = emptySet(),
    ): BodyMeasurement {
        fun lean(s: BodySegment) = if (s in leanUnder) Rating.Under else Rating.Normal
        fun fat(s: BodySegment) = if (s in fatOver) Rating.Over else Rating.Normal
        return BodyMeasurement(
            id = day.toEpochDay(), measuredOn = day, weightKg = 78.0, muscleMassKg = muscle, source = BodyMeasurement.Source.Report,
            details = ReportDetails(
                fatMassKg = fatKg,
                segments = listOf(
                    SegmentValues(BodySegment.RightArm, 3.6, lean(BodySegment.RightArm), 17.9, 0.8, fat(BodySegment.RightArm)),
                    SegmentValues(BodySegment.LeftArm, 3.5, lean(BodySegment.LeftArm), 18.6, 0.8, fat(BodySegment.LeftArm)),
                    SegmentValues(BodySegment.Trunk, 28.0, lean(BodySegment.Trunk), 20.3, 7.4, fat(BodySegment.Trunk)),
                    SegmentValues(BodySegment.RightLeg, 9.4, lean(BodySegment.RightLeg), 16.1, 2.0, fat(BodySegment.RightLeg)),
                    SegmentValues(BodySegment.LeftLeg, 9.3, lean(BodySegment.LeftLeg), 16.4, 2.0, fat(BodySegment.LeftLeg)),
                ),
            ),
        )
    }

    private val before = report(LocalDate.of(2026, 9, 22), fatKg = 14.9, muscle = 34.9)

    @Test
    fun `an uneven leg, the change since the last report, and fat rated Over`() {
        val now = report(
            LocalDate.of(2026, 10, 6), fatKg = 14.5, muscle = 35.1,
            leanUnder = setOf(BodySegment.LeftLeg, BodySegment.LeftArm), fatOver = setOf(BodySegment.Trunk),
        )

        val tips = BodyTips.of(now, before)

        assertEquals(
            listOf(
                BodyTip.Uneven(BodySegment.LeftLeg, BodySegment.RightLeg), // legs before arms; one uneven tip
                BodyTip.SinceLast(LocalDate.of(2026, 9, 22), fatKg = -0.4, muscleKg = 0.2),
                BodyTip.FatOver(listOf(BodySegment.Trunk), trunkPercent = 20.3),
            ),
            tips,
        )
        assertTrue((tips[1] as BodyTip.SinceLast).isOnTrack)
        assertEquals(
            """
            Lean muscle: left leg rated Under, right leg not. Idea: single-leg work like split squats or step-ups
            Since the last report on 22 Sep: fat mass -0.4 kg, muscle +0.2 kg. Idea: keep the current training and eating
            Fat rated Over: trunk (trunk 20.3%). Idea: steady overall fat loss brings it down
            """.trimIndent(),
            BodyTips.factLines(tips),
        )
    }

    @Test
    fun `nothing to suggest from a first, all-normal report`() {
        assertEquals(emptyList<BodyTip>(), BodyTips.of(report(LocalDate.of(2026, 10, 6), 14.5, 35.1), previous = null))
    }

    @Test
    fun `both sides Under isn't uneven, and more fat isn't on track`() {
        val now = report(LocalDate.of(2026, 10, 6), fatKg = 15.4, muscle = 34.9, leanUnder = setOf(BodySegment.LeftArm, BodySegment.RightArm))
        val tips = BodyTips.of(now, before)
        assertEquals(listOf(BodyTip.SinceLast(LocalDate.of(2026, 9, 22), fatKg = 0.5, muscleKg = 0.0)), tips)
        assertEquals(false, (tips.single() as BodyTip.SinceLast).isOnTrack)
    }
}
