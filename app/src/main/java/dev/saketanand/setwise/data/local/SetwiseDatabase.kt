package dev.saketanand.setwise.data.local

import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import dev.saketanand.setwise.data.local.dao.ExerciseDao
import dev.saketanand.setwise.data.local.dao.TemplateDao
import dev.saketanand.setwise.data.local.dao.WorkoutDao
import dev.saketanand.setwise.data.local.entity.ExerciseEntity
import dev.saketanand.setwise.data.local.entity.SetEntity
import dev.saketanand.setwise.data.local.entity.TemplateEntity
import dev.saketanand.setwise.data.local.entity.TemplateExerciseEntity
import dev.saketanand.setwise.data.local.entity.WorkoutEntity
import dev.saketanand.setwise.data.local.entity.WorkoutExerciseEntity

/**
 * The single instance is created by Koin (Room.databaseBuilder in AppModule), so there is
 * no companion-object singleton here.
 *
 * Schema changes: bump [version] and add a Migration; the exported schema in app/schemas
 * is what migrations are checked against.
 */
@Database(
    entities = [
        ExerciseEntity::class,
        SetEntity::class,
        WorkoutEntity::class,
        WorkoutExerciseEntity::class,
        TemplateEntity::class,
        TemplateExerciseEntity::class,
    ],
    version = 2,
    exportSchema = true,
    autoMigrations = [
        // v2 only adds the templates + template_exercises tables, so Room can generate the
        // migration from the exported schemas (app/schemas/.../1.json → 2.json).
        AutoMigration(from = 1, to = 2),
    ],
)
@TypeConverters(Converters::class)
abstract class SetwiseDatabase : RoomDatabase() {

    abstract fun exerciseDao(): ExerciseDao

    abstract fun workoutDao(): WorkoutDao

    abstract fun templateDao(): TemplateDao
}
