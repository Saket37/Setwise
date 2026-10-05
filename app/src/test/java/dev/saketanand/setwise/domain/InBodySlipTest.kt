package dev.saketanand.setwise.domain

import dev.saketanand.setwise.domain.model.BodyMetric
import dev.saketanand.setwise.domain.model.BodyReportParser
import dev.saketanand.setwise.domain.model.BodySegment
import dev.saketanand.setwise.domain.model.NormalRange
import dev.saketanand.setwise.domain.model.OcrLine
import dev.saketanand.setwise.domain.model.Rating
import dev.saketanand.setwise.domain.model.ReportDetails
import dev.saketanand.setwise.domain.model.SegmentValues
import dev.saketanand.setwise.domain.model.Sex
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * A whole InBody 170 thermal slip as the phone's text recognition reads it: the lines and
 * positions of a real photo, with made-up values but the same misreadings (lost decimal
 * points, "l" for 1, a lost slash, "ℓ" read as 0, ratings and numbers on separate lines).
 */
class InBodySlipTest {

    private fun ocr(text: String, left: Int, top: Int, right: Int, bottom: Int) = OcrLine(text, left, top, right, bottom)

    private val slip = listOf(
        ocr("-- Ther mal Pr inter Ver 1. 7B", 785, 211, 1490, 275),
        ocr("InBody 170 12 08/26 18:40", 766, 526, 1592, 622),
        ocr("ID", 766, 628, 807, 664),
        ocr("Gender: Male", 763, 702, 1042, 744),
        ocr("Age :31", 1192, 705, 1430, 755),
        ocr("Height : 174,0 cm Weight: 81.2 ke", 761, 764, 1526, 833),
        ocr("Body Composition", 785, 873, 1184, 939),
        ocr("Weight 812 kg", 761, 949, 1143, 1006), ocr("(560-75.8)", 1261, 957, 1544, 1007),
        ocr("Muscle 33.4 kg", 752, 1108, 1138, 1166), ocr("(28.9-35.3", 1256, 1117, 1516, 1168),
        ocr("Fat", 744, 1272, 809, 1310), ocr("19.7 kg", 965, 1267, 1129, 1325), ocr("(8.116.I", 1281, 1272, 1503, 1328),
        ocr("TBW", 740, 1428, 840, 1464), ocr("44.6 0", 961, 1425, 1100, 1477), ocr("(382-46.7)", 1245, 1429, 1527, 1489),
        ocr("FFM", 735, 1498, 821, 1535), ocr("61.5 kg", 957, 1493, 1122, 1554), ocr("(52.0-63.5)", 1242, 1501, 1524, 1561),
        ocr("Obesity Diagnosis", 731, 1605, 1143, 1667),
        ocr("BMI", 731, 1688, 809, 1725), ocr("26.8 kg/un² (18.5-230)", 949, 1684, 1520, 1753),
        ocr("PBF", 725, 1757, 817, 1790), ocr("24.3 %", 968, 1750, 1111, 1811), ocr("(10.0-20.0)", 1234, 1758, 1515, 1825),
        ocr("WHR", 717, 1818, 827, 1852), ocr("0.92", 944, 1821, 1045, 1863), ocr("(0.80-0.90)", 1232, 1832, 1519, 1879),
        ocr("Visceral Fat 1llevel", 725, 1871, 1162, 1937), ocr("(4 10)", 1256, 1889, 1499, 1949),
        ocr("BMR", 714, 1966, 813, 2002), ocr("1702 kcal", 923, 1956, 1147, 2021), ocr("(1744-2051)", 1209, 1964, 1556, 2032),
        ocr("Segmental Lean", 747, 2072, 1092, 2128),
        ocr("Lean Mass(kg) Evaluauon", 1112, 2149, 1534, 2192),
        ocr("Right Arm", 722, 2195, 945, 2241), ocr("372", 1172, 2196, 1269, 2244), ocr("Normal", 1381, 2208, 1523, 2249),
        ocr("Left Arm", 720, 2268, 914, 2309), ocr("3.58", 1170, 2270, 1270, 2318), ocr("Noumal", 1377, 2279, 1520, 2321),
        ocr("Trunk", 725, 2345, 850, 2382), ocr("28.4", 1166, 2344, 1265, 2392), ocr("Nomal", 1378, 2354, 1516, 2393),
        ocr("Right Leg", 715, 2416, 930, 2470), ocr("9.12", 1167, 2418, 1261, 2466), ocr("Under", 1382, 2429, 1505, 2468),
        ocr("Left Leg", 712, 2491, 893, 2543), ocr("9.05", 1164, 2494, 1259, 2542), ocr("Under", 1407, 2499, 1504, 2548),
        ocr("Segmental Fat", 731, 2592, 1037, 2655),
        ocr("PBF(°%) Fat Mass (kg) Evaluauon", 978, 2668, 1516, 2714),
        ocr("Right Arm 24.1 l4", 702, 2716, 1265, 2781), ocr("Over", 1393, 2732, 1497, 2771),
        ocr("Left Arm 25.2 1.5", 698, 2797, 1266, 2849), ocr("Over", 1392, 2810, 1490, 2847),
        ocr("Trunk", 697, 2887, 831, 2925), ocr("27.0 11,2", 995, 2873, 1264, 2930), ocr("Over", 1390, 2883, 1491, 2927),
        ocr("Right Leg", 691, 2964, 913, 3019), ocr("21.6 2.6", 992, 2956, 1258, 3008), ocr("Normal", 1430, 2977, 1490, 3003),
        ocr("Left Leg 21.9 2.6", 687, 3022, 1253, 3106), ocr("Normal", 1389, 3041, 1486, 3082),
        ocr("Muscle- Fat Control", 697, 3152, 1149, 3208),
        ocr("Muscle", 666, 3250, 836, 3289), ocr("0.0 kg Fat -9.6 kg", 980, 3227, 1485, 3297),
        ocr("Fitness Score", 664, 3373, 996, 3417), ocr("71 Points", 1276, 3361, 1493, 3415),
        ocr("Impedance", 653, 3488, 920, 3553),
        ocr("RA LA TR RL L", 831, 3547, 1449, 3624),
        ocr("20 301.4 299.8 25.1 270.3 268.9", 682, 3607, 1522, 3706),
        ocr("100 270.2 268.5 22.0 240.7 239.9", 655, 3684, 1519, 3776),
    ).shuffled(kotlin.random.Random(43)) // recognition order isn't reading order

    private val read = BodyReportParser.parse(slip)

    @Test
    fun `the summary values, with misread digits put right`() {
        assertEquals(LocalDate.of(2026, 8, 12), read.measuredOn) // "12 08/26": its first slash wasn't read
        assertEquals(81.2, read.weightKg)
        assertEquals(24.3, read.bodyFatPercent)
        assertEquals(33.4, read.muscleMassKg)
        assertEquals(1702, read.bmrKcal)
        assertEquals(11.0, read.visceralFat) // "1llevel"
        assertEquals(listOf(174.0, 31.0), listOf(read.heightCm, read.age?.toDouble()))
        assertEquals(Sex.Male, read.sex)
    }

    @Test
    fun `body composition and health markers`() {
        val details = read.details
        assertEquals(19.7, details.fatMassKg)
        assertEquals(61.5, details.fatFreeMassKg)
        assertEquals(44.6, details.bodyWaterL) // "44.6 0": the ℓ read as 0
        assertEquals(26.8, details.bmi)
        assertEquals(0.92, details.waistHipRatio)
        assertEquals(71, details.fitnessScore)
        assertEquals(0.0, details.muscleControlKg)
        assertEquals(-9.6, details.fatControlKg)
    }

    @Test
    fun `each value's normal range, lost decimal points put back`() {
        assertEquals(
            mapOf(
                BodyMetric.Weight to NormalRange(56.0, 75.8), // "(560-75.8)"
                BodyMetric.Muscle to NormalRange(28.9, 35.3), // no closing bracket
                BodyMetric.BodyWater to NormalRange(38.2, 46.7),
                BodyMetric.FatFreeMass to NormalRange(52.0, 63.5),
                BodyMetric.Bmi to NormalRange(18.5, 23.0), // "(18.5-230)"
                BodyMetric.BodyFat to NormalRange(10.0, 20.0),
                BodyMetric.WaistHip to NormalRange(0.8, 0.9),
                BodyMetric.Visceral to NormalRange(null, 10.0), // "below 10"
                BodyMetric.Bmr to NormalRange(1744.0, 2051.0),
                // Fat mass's "(8.116.I" can't be read: no range rather than a guess.
            ),
            read.details.ranges,
        )
    }

    @Test
    fun `arms, trunk and legs, each rated`() {
        assertEquals(
            listOf(
                SegmentValues(BodySegment.RightArm, 3.72, Rating.Normal, 24.1, 1.4, Rating.Over), // "372", "l4"
                SegmentValues(BodySegment.LeftArm, 3.58, Rating.Normal, 25.2, 1.5, Rating.Over),
                SegmentValues(BodySegment.Trunk, 28.4, Rating.Normal, 27.0, 11.2, Rating.Over), // "11,2"
                SegmentValues(BodySegment.RightLeg, 9.12, Rating.Under, 21.6, 2.6, Rating.Normal),
                SegmentValues(BodySegment.LeftLeg, 9.05, Rating.Under, 21.9, 2.6, Rating.Normal), // name and numbers on one line
            ),
            read.details.segments,
        )
    }

    @Test
    fun `a report without these sections has no details`() {
        val plain = BodyReportParser.parse(listOf(ocr("Weight 72.5 kg", 20, 20, 300, 50), ocr("Body Fat 18.0 %", 20, 70, 300, 100)))
        assertEquals(ReportDetails(), plain.details)
    }
}
