package dev.saketanand.setwise.domain

import dev.saketanand.setwise.domain.ai.BodyReportReader
import dev.saketanand.setwise.domain.ai.ModelBodyReport
import dev.saketanand.setwise.domain.model.BmrEstimate
import dev.saketanand.setwise.domain.model.BodyMeasurement
import dev.saketanand.setwise.domain.model.BodyMetric
import dev.saketanand.setwise.domain.model.BodyReportParser
import dev.saketanand.setwise.domain.model.BodyRules
import dev.saketanand.setwise.domain.model.NormalRange
import dev.saketanand.setwise.domain.model.OcrLine
import dev.saketanand.setwise.domain.model.ReportDetails
import dev.saketanand.setwise.domain.model.ReportValues
import dev.saketanand.setwise.domain.model.Sex
import dev.saketanand.setwise.domain.model.UserSettings
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BodyTest {

    /** Lines as text recognition gives them: one per row unless the report has columns. */
    private fun rows(vararg texts: String) = texts.mapIndexed { i, text -> OcrLine(text, 20, i * 40, 20 + text.length * 12, i * 40 + 30) }

    @Test
    fun `values on the label's line, with reference ranges after them`() {
        val values = BodyReportParser.parse(
            rows(
                "InBody Body Composition Results  12.09.2026",
                "Weight 72.5 kg (55.8~75.5)",
                "Skeletal Muscle Mass 33.1 kg (28.4~34.7)",
                "Body Fat Mass 14.2 kg (7.6~15.2)",
                "Percent Body Fat 19.6 % (10.0~20.0)",
                "Basal Metabolic Rate 1645 kcal",
                "Visceral Fat Level 6",
            ),
        )
        assertEquals(ReportValues(LocalDate.of(2026, 9, 12), 72.5, 19.6, 33.1, 1645, 6.0), values.copy(details = ReportDetails()))
        assertEquals(14.2, values.details.fatMassKg)
        assertEquals(NormalRange(55.8, 75.5), values.details.ranges[BodyMetric.Weight])
    }

    /** An InBody 170 printout, as text recognition reads it (values often a separate block on the row). */
    @Test
    fun `an InBody 170 printout`() {
        fun at(text: String, left: Int, top: Int) = OcrLine(text, left, top, left + text.length * 14, top + 30)
        val lines = listOf(
            at("-- Thermal Printer Ver 1.7B --", 40, 10),
            at("InBody170 12/08/26 18:40", 30, 200),
            at("Gender: Male", 30, 300), at("Age :31", 330, 300),
            at("Height : 174.0 cm", 30, 340), at("Weight: 81.2 kg", 330, 340),
            at("Body Composition", 30, 400),
            at("Weight", 30, 440), at("81.2 kg", 160, 440), at("(56.0~75.8)", 330, 440),
            at("Muscle", 30, 520), at("33.4 kg", 160, 520), at("(28.9~35.3)", 330, 520),
            at("Fat", 30, 600), at("19.7 kg", 160, 600), at("(8.1~16.2)", 330, 600),
            at("Obesity Diagnosis", 30, 780),
            at("BMI 26.8 kg/m2", 30, 820), at("(18.5~23.0)", 330, 820),
            at("PBF 24.3 %", 30, 860), at("(10.0~20.0)", 330, 860),
            at("Visceral Fat 9 level", 30, 940), at("(10)", 330, 940),
            at("BMR 1702 kcal", 30, 980), at("(1744~2051)", 330, 980),
            at("Segmental Fat", 30, 1300), at("PBF(%) Fat Mass(kg) Evaluation", 150, 1340),
            at("Right Arm 24.1 1.4 Over", 30, 1380),
            at("Muscle - Fat Control", 30, 1580), at("Muscle 0.0 kg Fat -9.6 kg", 30, 1620),
        ).shuffled(kotlin.random.Random(7)) // recognition order isn't reading order

        assertEquals(
            ReportValues(
                measuredOn = LocalDate.of(2026, 8, 12),
                weightKg = 81.2, bodyFatPercent = 24.3, muscleMassKg = 33.4, bmrKcal = 1702, visceralFat = 9.0,
                heightCm = 174.0, age = 31, sex = Sex.Male,
            ),
            BodyReportParser.parse(lines).copy(details = ReportDetails()), // the details: InBodySlipTest
        )
    }

    @Test
    fun `values in a column to the right, or just below the label`() {
        val columns = listOf(
            OcrLine("Weight", 20, 100, 120, 130), OcrLine("72.5", 400, 102, 460, 128),
            OcrLine("PBF", 20, 150, 70, 180), OcrLine("19.6", 400, 151, 460, 179),
        )
        assertEquals(72.5, BodyReportParser.parse(columns).weightKg)
        assertEquals(19.6, BodyReportParser.parse(columns).bodyFatPercent)
        // As the recognizer read an InBody 170's "PBF".
        assertEquals(22.4, BodyReportParser.parse(listOf(OcrLine("PBE", 43, 744, 98, 764), OcrLine("22.4 %", 243, 742, 359, 765))).bodyFatPercent)

        val stacked = listOf(OcrLine("BMR", 20, 100, 80, 130), OcrLine("1,645 kcal", 20, 135, 140, 165))
        assertEquals(1645, BodyReportParser.parse(stacked).bmrKcal)
    }

    @Test
    fun `pounds become kg, and what isn't believable is left out`() {
        val values = BodyReportParser.parse(rows("Weight 160.0 lb", "Body Fat % 92", "BMR 12"))
        assertEquals(72.6, values.weightKg)
        assertNull(values.bodyFatPercent)
        assertNull(values.bmrKcal)
    }

    @Test
    fun `BMR from a recent report, else body fat, else the profile`() {
        val today = LocalDate.of(2026, 10, 5)
        val profile = UserSettings(bodyWeightKg = 80.0, heightCm = 180.0, birthYear = 1996, sex = Sex.Male)

        assertEquals(BmrEstimate(1645, BmrEstimate.Source.Report), BodyRules.bmr(listOf(check(today.minusDays(10), bmr = 1645)), profile, today))
        // Report too old (over 90 days) but body fat known: Katch-McArdle, 370 + 21.6 × 64.
        assertEquals(
            BmrEstimate(1752, BmrEstimate.Source.BodyFat),
            BodyRules.bmr(listOf(check(today.minusDays(120), bmr = 1700, fat = 20.0)), profile, today),
        )
        // Mifflin-St Jeor: 800 + 1125 - 150 + 5.
        assertEquals(BmrEstimate(1780, BmrEstimate.Source.Profile), BodyRules.bmr(emptyList(), profile, today))
        assertNull(BodyRules.bmr(emptyList(), profile.copy(sex = null), today))
    }

    @Test
    fun `the model's values must be numbers in the report`() {
        val text = "Weight 72.5\nPBF 19.6\nBMR 1645"
        val read = BodyReportReader.accept(ModelBodyReport(72.5, 19.6, 30.0, 1645, 0.0), text)
        assertEquals(ReportValues(weightKg = 72.5, bodyFatPercent = 19.6, bmrKcal = 1645), read) // 30 isn't in it
    }

    private fun check(day: LocalDate, bmr: Int? = null, fat: Double? = null) =
        BodyMeasurement(day.toEpochDay(), day, weightKg = 80.0, bodyFatPercent = fat, bmrKcal = bmr, source = BodyMeasurement.Source.Report)
}
