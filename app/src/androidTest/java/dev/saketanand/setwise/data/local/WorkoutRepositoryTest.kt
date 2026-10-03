package dev.saketanand.setwise.data.local

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.saketanand.setwise.data.local.entity.ExerciseEntity
import dev.saketanand.setwise.data.local.entity.SetEntity
import dev.saketanand.setwise.data.local.entity.TemplateExerciseEntity
import dev.saketanand.setwise.data.local.entity.WorkoutEntity
import dev.saketanand.setwise.data.local.entity.WorkoutExerciseEntity
import dev.saketanand.setwise.data.repository.TemplateRepositoryImpl
import dev.saketanand.setwise.data.repository.WorkoutRepositoryImpl
import dev.saketanand.setwise.domain.model.CardioValues
import dev.saketanand.setwise.domain.model.ExerciseType
import dev.saketanand.setwise.domain.model.PreviousSet
import dev.saketanand.setwise.domain.model.TemplateDraft
import dev.saketanand.setwise.domain.model.TemplateDraftExercise
import dev.saketanand.setwise.domain.repository.WorkoutRepository
import java.time.Instant
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** The active-workout writes and reads, on a real (in-memory) database. */
@RunWith(AndroidJUnit4::class)
class WorkoutRepositoryTest {

    private lateinit var db: SetwiseDatabase
    private lateinit var repository: WorkoutRepository

    private var bench = 0L
    private var squat = 0L

    @Before
    fun setUp() = runTest {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        db = Room.inMemoryDatabaseBuilder(context, SetwiseDatabase::class.java).build()
        repository = WorkoutRepositoryImpl(db, db.workoutDao(), db.templateDao())
        bench = db.exerciseDao().insert(exercise("Bench Press"))
        squat = db.exerciseDao().insert(exercise("Back Squat"))
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun previousSetsComeFromTheLastFinishedSessionOnly() = runTest {
        finishedWorkout(startedAt = 1_000, bench to listOf(50.0 to 10))
        finishedWorkout(startedAt = 2_000, bench to listOf(60.0 to 8, 62.5 to 6))
        val current = repository.startWorkout(templateId = null, startedAt = Instant.ofEpochMilli(3_000))
        repository.addExercises(current, listOf(bench, squat))

        val session = repository.observeSession(current).first()!!

        val (benchRow, squatRow) = session.exercises
        assertEquals(listOf(PreviousSet(60.0, 8), PreviousSet(62.5, 6)), benchRow.previousSets)
        assertTrue("never done before", squatRow.previousSets.isEmpty())
    }

    @Test
    fun addedExercisesGetLastTimesSetCountOrThree() = runTest {
        finishedWorkout(startedAt = 1_000, bench to listOf(60.0 to 8, 60.0 to 8, 60.0 to 8, 60.0 to 8, 60.0 to 8))
        val current = repository.startWorkout(templateId = null, startedAt = Instant.ofEpochMilli(2_000))

        repository.addExercises(current, listOf(squat, bench))

        val session = repository.observeSession(current).first()!!
        assertEquals(listOf("Back Squat", "Bench Press"), session.exercises.map { it.exercise.name })
        assertEquals(listOf(WorkoutRepository.DEFAULT_SET_COUNT, 5), session.exercises.map { it.sets.size })
    }

    @Test
    fun deletingASetRenumbersTheRest() = runTest {
        val current = repository.startWorkout(templateId = null, startedAt = Instant.ofEpochMilli(1_000))
        repository.addExercises(current, listOf(squat)) // 3 sets
        val sets = repository.observeSession(current).first()!!.exercises.single().sets

        repository.deleteSet(sets[0].id)

        val after = repository.observeSession(current).first()!!.exercises.single().sets
        assertEquals(listOf(sets[1].id, sets[2].id), after.map { it.id })
        assertEquals(listOf(1, 2), after.map { it.setNumber })
    }

    @Test
    fun finishDropsOpenSetsAndEmptyExercisesAndRenumbers() = runTest {
        val current = repository.startWorkout(templateId = null, startedAt = Instant.ofEpochMilli(1_000))
        repository.addExercises(current, listOf(bench, squat)) // 3 sets each
        val (benchRow, _) = repository.observeSession(current).first()!!.exercises
        // Bench: sets 1 and 3 done, 2 left open. Squat: nothing done.
        repository.setCompleted(benchRow.sets[0].id, Instant.ofEpochMilli(2_000), 60.0, 8, null)
        repository.setCompleted(benchRow.sets[2].id, Instant.ofEpochMilli(3_000), 60.0, 6, null)

        assertTrue(repository.finishWorkout(current, Instant.ofEpochMilli(4_000)))

        val session = repository.observeSession(current).first()!!
        assertNotNull(session.endedAt)
        val onlyBench = session.exercises.single()
        assertEquals("Bench Press", onlyBench.exercise.name)
        assertEquals(listOf(1, 2), onlyBench.sets.map { it.setNumber })
        assertEquals(listOf(8, 6), onlyBench.sets.map { it.reps })
        assertFalse("already finished", repository.finishWorkout(current, Instant.ofEpochMilli(5_000)))
    }

    @Test
    fun finishingTwiceLeavesTheFinishedWorkoutAlone() = runTest {
        val current = repository.startWorkout(templateId = null, startedAt = Instant.ofEpochMilli(1_000))
        repository.addExercises(current, listOf(bench))
        val firstSet = repository.observeSession(current).first()!!.exercises.single().sets.first()
        repository.setCompleted(firstSet.id, Instant.ofEpochMilli(2_000), 60.0, 8, null)
        repository.finishWorkout(current, Instant.ofEpochMilli(3_000))

        assertFalse(repository.finishWorkout(current, Instant.ofEpochMilli(9_000)))

        val session = repository.observeSession(current).first()!!
        assertEquals(Instant.ofEpochMilli(3_000), session.endedAt)
        assertEquals(1, session.exercises.single().sets.size)
    }

    @Test
    fun finishMarksTheRecordSet() = runTest {
        // Two sets last time → the new workout gets two sets too.
        finishedWorkout(startedAt = 1_000, bench to listOf(60.0 to 8, 57.5 to 8))
        val current = repository.startWorkout(templateId = null, startedAt = Instant.ofEpochMilli(2_000))
        repository.addExercises(current, listOf(bench))
        val sets = repository.observeSession(current).first()!!.exercises.single().sets
        repository.setCompleted(sets[0].id, Instant.ofEpochMilli(2_100), 60.0, 8, null)
        repository.setCompleted(sets[1].id, Instant.ofEpochMilli(2_200), 62.5, 6, null)

        repository.finishWorkout(current, Instant.ofEpochMilli(3_000))

        val done = repository.observeSession(current).first()!!.exercises.single().sets
        assertEquals(listOf(false, true), done.map { it.isPr })
    }

    @Test
    fun recordsOnlyCountWorkoutsThatStartedEarlier() = runTest {
        finishedWorkout(startedAt = 1_000, bench to listOf(60.0 to 8))
        finishedWorkout(startedAt = 9_000, bench to listOf(100.0 to 5)) // later: doesn't count
        val current = repository.startWorkout(templateId = null, startedAt = Instant.ofEpochMilli(5_000))
        repository.addExercises(current, listOf(bench))

        val bests = repository.observeSession(current).first()!!.exercises.single().bestsBefore

        assertEquals(PreviousSet(60.0, 8), bests.heaviest)
    }

    @Test
    fun finishedTimesCanBeCorrectedButNotARunningWorkouts() = runTest {
        val current = repository.startWorkout(templateId = null, startedAt = Instant.ofEpochMilli(1_000))
        assertFalse(repository.updateFinishedTimes(current, Instant.ofEpochMilli(500), Instant.ofEpochMilli(4_000)))

        repository.addExercises(current, listOf(bench))
        val set = repository.observeSession(current).first()!!.exercises.single().sets.first()
        repository.setCompleted(set.id, Instant.ofEpochMilli(2_000), 60.0, 8, null)
        repository.finishWorkout(current, Instant.ofEpochMilli(3_000))

        assertTrue(repository.updateFinishedTimes(current, Instant.ofEpochMilli(500), Instant.ofEpochMilli(4_000)))
        val session = repository.observeSession(current).first()!!
        assertEquals(Instant.ofEpochMilli(500), session.startedAt)
        assertEquals(Instant.ofEpochMilli(4_000), session.endedAt)
    }

    @Test
    fun aFinishedWorkoutBecomesATemplate() = runTest {
        val current = repository.startWorkout(templateId = null, startedAt = Instant.ofEpochMilli(1_000))
        repository.addExercises(current, listOf(squat, bench)) // 3 sets each
        val (squatRow, benchRow) = repository.observeSession(current).first()!!.exercises
        squatRow.sets.take(2).forEach { repository.setCompleted(it.id, Instant.ofEpochMilli(2_000), 100.0, 5, null) }
        repository.setCompleted(benchRow.sets.first().id, Instant.ofEpochMilli(2_000), 60.0, 8, null)
        repository.finishWorkout(current, Instant.ofEpochMilli(3_000))

        val templates = TemplateRepositoryImpl(db, db.templateDao(), db.workoutDao())
        val templateId = templates.createFromWorkout(current, Instant.ofEpochMilli(4_000))

        val template = templates.observeTemplates().first().single { it.id == templateId }
        assertEquals("Workout", template.name)
        assertEquals(listOf("Back Squat" to 2, "Bench Press" to 1), template.exercises.map { it.name to it.targetSets })
    }

    @Test
    fun startingFromATemplateGivesCardioOneEntry() = runTest {
        val elliptical = db.exerciseDao().insert(exercise("Elliptical").copy(type = ExerciseType.CARDIO, muscleGroup = "Cardio"))
        // Saved with 3 "sets" for cardio, as older templates could be.
        val templateId = TemplateRepositoryImpl(db, db.templateDao(), db.workoutDao()).saveTemplate(
            TemplateDraft(0, "Mixed", null, listOf(TemplateDraftExercise(bench, 3), TemplateDraftExercise(elliptical, 3))),
            Instant.EPOCH,
        )
        db.templateDao().deleteTemplateExercises(templateId)
        db.templateDao().insertTemplateExercises(
            listOf(
                TemplateExerciseEntity(templateId = templateId, exerciseId = bench, position = 0, targetSets = 3),
                TemplateExerciseEntity(templateId = templateId, exerciseId = elliptical, position = 1, targetSets = 3),
            )
        )

        val workoutId = repository.startWorkout(templateId, Instant.ofEpochMilli(1_000))

        val exercises = repository.observeSession(workoutId).first()!!.exercises
        assertEquals(listOf(3, 1), exercises.map { it.sets.size })
    }

    @Test
    fun cardioIsOneEntryLoggedOnceAndRemembersLastTime() = runTest {
        val treadmill = db.exerciseDao().insert(exercise("Treadmill").copy(type = ExerciseType.CARDIO, muscleGroup = "Cardio"))
        // Last time: a finished workout with a logged treadmill entry.
        val earlier = repository.startWorkout(templateId = null, startedAt = Instant.ofEpochMilli(1_000))
        val earlierItem = repository.addExercises(earlier, listOf(treadmill)).single()
        repository.logCardio(earlierItem, CardioValues(1_800, inclinePct = 5.0, distanceKm = 3.9), Instant.ofEpochMilli(2_000))
        repository.finishWorkout(earlier, Instant.ofEpochMilli(3_000))

        val current = repository.startWorkout(templateId = null, startedAt = Instant.ofEpochMilli(10_000))
        val item = repository.addExercises(current, listOf(treadmill)).single()
        assertEquals(1, repository.observeSession(current).first()!!.exercises.single().sets.size)

        val before = repository.observeCardioEntry(item).first()!!
        assertNull(before.logged)
        assertEquals(CardioValues(1_800, inclinePct = 5.0, distanceKm = 3.9), before.lastTime)

        repository.logCardio(item, CardioValues(1_500, level = 8), Instant.ofEpochMilli(11_000))
        repository.logCardio(item, CardioValues(1_620, level = 9), Instant.ofEpochMilli(12_000)) // corrected

        val set = repository.observeSession(current).first()!!.exercises.single().sets.single()
        assertTrue(set.isCompleted)
        assertEquals(CardioValues(1_620, level = 9), set.cardio)
    }

    @Test
    fun aPastDayLoggedAfterwardsTakesTheRecordFromLaterWorkouts() = runTest {
        finishedWorkout(startedAt = 1_000, bench to listOf(60.0 to 8))
        val wednesday = finishWithBench(startedAt = 5_000, kg = 80.0) // a record over 60
        assertEquals(true, isRecord(wednesday))

        // Monday, logged afterwards: heavier, and before Wednesday.
        val monday = finishWithBench(startedAt = 3_000, kg = 90.0)

        assertEquals(true, isRecord(monday))
        assertEquals(false, isRecord(wednesday)) // no longer beats what came before it
    }

    @Test
    fun deletingAWorkoutGivesTheRecordBack() = runTest {
        finishedWorkout(startedAt = 1_000, bench to listOf(60.0 to 8))
        val monday = finishWithBench(startedAt = 3_000, kg = 90.0)
        val wednesday = finishWithBench(startedAt = 5_000, kg = 80.0)
        assertEquals(false, isRecord(wednesday))

        assertTrue(repository.deleteFinishedWorkout(monday))

        assertNull(repository.observeSession(monday).first())
        assertEquals(true, isRecord(wednesday))
        assertFalse(repository.deleteFinishedWorkout(monday)) // already gone
    }

    @Test
    fun movingAWorkoutEarlierChecksRecordsAgain() = runTest {
        finishedWorkout(startedAt = 1_000, bench to listOf(60.0 to 8))
        val first = finishWithBench(startedAt = 3_000, kg = 80.0)
        val second = finishWithBench(startedAt = 5_000, kg = 90.0)
        assertEquals(true, isRecord(second))

        // The 90 kg workout really happened before the 80 kg one.
        repository.updateFinishedTimes(second, Instant.ofEpochMilli(2_000), Instant.ofEpochMilli(2_500))

        assertEquals(true, isRecord(second))
        assertEquals(false, isRecord(first))
    }

    @Test
    fun historyListsFinishedWorkoutsNewestFirstWithTotals() = runTest {
        finishedWorkout(startedAt = 1_000, bench to listOf(60.0 to 8, 60.0 to 6))
        finishedWorkout(startedAt = 5_000, squat to listOf(100.0 to 5))
        repository.startWorkout(templateId = null, startedAt = Instant.ofEpochMilli(9_000)) // running: not listed

        val history = repository.observeHistory().first()

        assertEquals(listOf(5_000L, 1_000L), history.map { it.startedAt.toEpochMilli() })
        val first = history.last()
        assertEquals(2, first.completedSets)
        assertEquals(60.0 * 8 + 60.0 * 6, first.volumeKg, 0.001)
        assertEquals(0, first.personalRecords)
    }

    @Test
    fun exerciseSessionsListFinishedWorkoutsNewestFirstWithSetsInOrder() = runTest {
        finishedWorkout(startedAt = 1_000, bench to listOf(60.0 to 8, 62.5 to 6))
        finishedWorkout(startedAt = 5_000, squat to listOf(100.0 to 5), bench to listOf(65.0 to 5))
        finishedWorkout(startedAt = 7_000, squat to listOf(105.0 to 5)) // no bench: not listed
        val running = repository.startWorkout(templateId = null, startedAt = Instant.ofEpochMilli(9_000))
        repository.addExercises(running, listOf(bench)) // running: not listed

        val sessions = repository.observeExerciseSessions(bench).first()

        assertEquals(listOf(5_000L, 1_000L), sessions.map { it.startedAt.toEpochMilli() })
        assertEquals(listOf(65.0), sessions[0].sets.map { it.weightKg })
        assertEquals(listOf(8, 6), sessions[1].sets.map { it.reps })
    }

    @Test
    fun startTimeCanOnlyChangeWhileRunning() = runTest {
        val current = repository.startWorkout(templateId = null, startedAt = Instant.ofEpochMilli(1_000))
        repository.updateStartTime(current, Instant.ofEpochMilli(500))
        assertEquals(Instant.ofEpochMilli(500), repository.observeSession(current).first()!!.startedAt)
    }

    @Test
    fun discardDeletesEverything() = runTest {
        val current = repository.startWorkout(templateId = null, startedAt = Instant.ofEpochMilli(1_000))
        repository.addExercises(current, listOf(bench))

        repository.discardWorkout(current)

        assertNull(repository.observeSession(current).first())
    }

    /** A finished workout of one bench set of [kg] × 8, through the repository (so records are marked). */
    private suspend fun finishWithBench(startedAt: Long, kg: Double): Long {
        val id = repository.startWorkout(templateId = null, startedAt = Instant.ofEpochMilli(startedAt))
        repository.addExercises(id, listOf(bench))
        val sets = repository.observeSession(id).first()!!.exercises.single().sets
        repository.setCompleted(sets.first().id, Instant.ofEpochMilli(startedAt + 100), kg, 8, null)
        repository.finishWorkout(id, Instant.ofEpochMilli(startedAt + 600))
        return id
    }

    private suspend fun isRecord(workoutId: Long): Boolean =
        repository.observeSession(workoutId).first()!!.exercises.single().sets.any { it.isPr }

    private suspend fun finishedWorkout(startedAt: Long, vararg exercises: Pair<Long, List<Pair<Double, Int>>>) {
        val dao = db.workoutDao()
        val workoutId = dao.insertWorkout(WorkoutEntity(name = "W", startedAt = startedAt, endedAt = startedAt + 60_000))
        exercises.forEachIndexed { position, (exerciseId, sets) ->
            val id = dao.insertWorkoutExercise(WorkoutExerciseEntity(workoutId = workoutId, exerciseId = exerciseId, position = position))
            dao.insertSets(sets.mapIndexed { i, (kg, reps) ->
                SetEntity(workoutExerciseId = id, setNumber = i + 1, weightKg = kg, reps = reps, isCompleted = true)
            })
        }
    }

    private fun exercise(name: String) =
        ExerciseEntity(name = name, type = ExerciseType.STRENGTH, muscleGroup = "Chest", equipment = "Barbell", defaultRestSec = 90)
}
