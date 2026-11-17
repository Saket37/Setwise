package dev.saketanand.setwise.data.local

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.saketanand.setwise.data.local.entity.ExerciseEntity
import dev.saketanand.setwise.data.local.entity.TemplateEntity
import dev.saketanand.setwise.data.local.entity.TemplateExerciseEntity
import dev.saketanand.setwise.data.local.entity.WorkoutEntity
import dev.saketanand.setwise.data.local.entity.WorkoutExerciseEntity
import dev.saketanand.setwise.data.repository.ExerciseEditorImpl
import dev.saketanand.setwise.data.repository.ExerciseRepositoryImpl
import dev.saketanand.setwise.domain.model.CreateExerciseResult
import dev.saketanand.setwise.domain.model.EditExerciseResult
import dev.saketanand.setwise.domain.model.ExerciseType
import dev.saketanand.setwise.domain.model.ExerciseUsage
import dev.saketanand.setwise.domain.model.NewExercise
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

/** Editing, deleting and merging custom exercises (#146), on the real SQL: device and Robolectric. */
@RunWith(AndroidJUnit4::class)
class ExerciseEditorTest {

    private lateinit var db: SetwiseDatabase
    private lateinit var repository: ExerciseRepositoryImpl
    private lateinit var editor: ExerciseEditorImpl

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        // The checks below read the tables with plain SQL, on the test's thread.
        db = Room.inMemoryDatabaseBuilder(context, SetwiseDatabase::class.java).allowMainThreadQueries().build()
        repository = ExerciseRepositoryImpl(db.exerciseDao())
        editor = ExerciseEditorImpl(db.exerciseDao())
    }

    @After
    fun tearDown() = db.close()

    private fun new(name: String, type: ExerciseType = ExerciseType.STRENGTH, timed: Boolean = false) =
        NewExercise(name, type, isTimed = timed, muscleGroup = "Chest", equipment = "Dumbbell", restSec = 90)

    private suspend fun create(name: String, type: ExerciseType = ExerciseType.STRENGTH, timed: Boolean = false): Long =
        (repository.createExercise(new(name, type, timed)) as CreateExerciseResult.Created).exerciseId

    private suspend fun builtIn(name: String): Long =
        db.exerciseDao().insert(ExerciseEntity(name = name, type = ExerciseType.STRENGTH, muscleGroup = "Chest", equipment = "Barbell", defaultRestSec = 120))

    private suspend fun workoutWith(vararg exerciseIds: Long): Long {
        val workoutId = db.workoutDao().insertWorkout(WorkoutEntity(name = "Push", startedAt = 1_000, endedAt = 2_000))
        exerciseIds.forEachIndexed { i, id -> db.workoutDao().insertWorkoutExercise(WorkoutExerciseEntity(workoutId = workoutId, exerciseId = id, position = i)) }
        return workoutId
    }

    private suspend fun templateWith(vararg exerciseIds: Long): Long {
        val templateId = db.templateDao().insertTemplate(TemplateEntity(name = "Push", createdAt = 1_000))
        db.templateDao().insertTemplateExercises(
            exerciseIds.mapIndexed { i, id -> TemplateExerciseEntity(templateId = templateId, exerciseId = id, position = i, targetSets = 3) },
        )
        return templateId
    }

    private fun exerciseIdsIn(table: String, column: String, id: Long): List<Long> {
        val cursor = db.query("SELECT exerciseId FROM $table WHERE $column = ? ORDER BY position", arrayOf<Any>(id))
        return cursor.use { generateSequence { if (it.moveToNext()) it.getLong(0) else null }.toList() }
    }

    @Test
    fun editChangesTheDetailsButNotHowItsLogged() = runTest {
        val id = create("Bnech press")

        val result = editor.editExercise(id, NewExercise("  Bench  press ", ExerciseType.CARDIO, isTimed = true, muscleGroup = "Triceps", equipment = "Barbell", restSec = 150))

        assertEquals(EditExerciseResult.Saved, result)
        val saved = repository.observeExercise(id).first()!!
        assertEquals("Bench press", saved.name)
        assertEquals("Triceps", saved.muscleGroup)
        assertEquals("Barbell", saved.equipment)
        assertEquals(150, saved.defaultRestSec)
        assertEquals(ExerciseType.STRENGTH, saved.type)
        assertFalse(saved.isTimed)
    }

    @Test
    fun editKeepsANameAnotherExerciseHasAndAllowsItsOwnInAnotherCase() = runTest {
        val press = create("Spoto Press")
        val other = create("Seal Row")

        val taken = editor.editExercise(other, new("spoto press"))
        val ownName = editor.editExercise(press, new("SPOTO press"))

        assertEquals(press, (taken as EditExerciseResult.NameTaken).existing.id)
        assertEquals("Seal Row", repository.observeExercise(other).first()!!.name)
        assertEquals(EditExerciseResult.Saved, ownName)
        assertEquals("SPOTO press", repository.observeExercise(press).first()!!.name)
    }

    @Test
    fun builtInExercisesAreNotEditedDeletedOrMerged() = runTest {
        val bench = builtIn("Bench Press (Barbell)")
        val custom = create("Bnech press")

        editor.editExercise(bench, new("Renamed"))

        assertEquals("Bench Press (Barbell)", repository.observeExercise(bench).first()!!.name)
        assertFalse(runCatching { editor.deleteExercise(bench) }.getOrDefault(false))
        assertFalse(editor.mergeExercise(bench, custom))
        assertNotNull(repository.observeExercise(bench).first())
    }

    @Test
    fun anUnusedExerciseIsDeletedAndTakenOutOfItsTemplates() = runTest {
        val typo = create("Bnech press")
        val fly = create("Cable fly")
        val template = templateWith(typo, fly)

        assertEquals(ExerciseUsage(workouts = 0, templates = 1), editor.usage(typo))
        assertTrue(editor.deleteExercise(typo))

        assertNull(repository.observeExercise(typo).first())
        assertEquals(listOf(fly), exerciseIdsIn("template_exercises", "templateId", template))
    }

    @Test
    fun anExerciseInAWorkoutIsNotDeleted() = runTest {
        val typo = create("Bnech press")
        val template = templateWith(typo)
        workoutWith(typo)

        assertEquals(ExerciseUsage(workouts = 1, templates = 1), editor.usage(typo))
        assertFalse(editor.deleteExercise(typo))

        assertNotNull(repository.observeExercise(typo).first())
        assertEquals(listOf(typo), exerciseIdsIn("template_exercises", "templateId", template))
    }

    @Test
    fun mergeMovesWorkoutsAndTemplatesThenDeletes() = runTest {
        val typo = create("Bnech press")
        val bench = builtIn("Bench Press (Barbell)")
        val fly = create("Cable fly")
        val workout = workoutWith(typo, fly)
        val onlyTypo = templateWith(typo, fly)
        val both = templateWith(bench, typo)

        assertTrue(editor.mergeExercise(typo, bench))

        assertNull(repository.observeExercise(typo).first())
        assertEquals(listOf(bench, fly), exerciseIdsIn("workout_exercises", "workoutId", workout))
        assertEquals(listOf(bench, fly), exerciseIdsIn("template_exercises", "templateId", onlyTypo))
        // It already had Bench Press: listed once.
        assertEquals(listOf(bench), exerciseIdsIn("template_exercises", "templateId", both))
        assertEquals(ExerciseUsage(workouts = 1, templates = 2), editor.usage(bench))
    }

    @Test
    fun mergeNeedsAnExerciseLoggedTheSameWay() = runTest {
        val plank = create("Plank hold", ExerciseType.BODYWEIGHT, timed = true)
        val pushUp = create("Push up", ExerciseType.BODYWEIGHT)
        val rowing = create("Rowing", ExerciseType.CARDIO)
        workoutWith(plank)

        assertFalse(editor.mergeExercise(plank, pushUp))
        assertFalse(editor.mergeExercise(plank, rowing))
        assertFalse(editor.mergeExercise(plank, plank))
        assertFalse(editor.mergeExercise(plank, 999))
        assertEquals(1, editor.usage(plank).workouts)
    }
}
