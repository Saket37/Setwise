package dev.saketanand.setwise.data.local

import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import dev.saketanand.setwise.data.local.dao.BodyMeasurementDao
import dev.saketanand.setwise.data.local.dao.DayMarkDao
import dev.saketanand.setwise.data.local.dao.ExerciseDao
import dev.saketanand.setwise.data.local.dao.TemplateDao
import dev.saketanand.setwise.data.local.dao.WorkoutDao
import dev.saketanand.setwise.data.local.entity.BodyMeasurementEntity
import dev.saketanand.setwise.data.local.entity.BodySegmentEntity
import dev.saketanand.setwise.data.local.entity.DayMarkEntity
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
 * Schema changes: bump [version] and add a migration (an AutoMigration when Room can work it
 * out), and extend MigrationTest. Never edit an exported app/schemas/.../N.json: Android's
 * backup can restore an older database on any phone.
 */
@Database(
    entities = [
        ExerciseEntity::class,
        SetEntity::class,
        WorkoutEntity::class,
        WorkoutExerciseEntity::class,
        TemplateEntity::class,
        TemplateExerciseEntity::class,
        DayMarkEntity::class,
        BodyMeasurementEntity::class,
        BodySegmentEntity::class,
    ],
    version = 3,
    exportSchema = true,
    autoMigrations = [
        // 2: target reps on template and workout exercises (nullable columns).
        AutoMigration(from = 1, to = 2),
        // 3: body measurements move here from DataStore, with a full report's details and its
        // segments (new tables; LegacyBodyMeasurements copies the old ones).
        AutoMigration(from = 2, to = 3),
    ],
)
@TypeConverters(Converters::class)
abstract class SetwiseDatabase : RoomDatabase() {

    abstract fun exerciseDao(): ExerciseDao

    abstract fun workoutDao(): WorkoutDao

    abstract fun templateDao(): TemplateDao

    abstract fun dayMarkDao(): DayMarkDao

    abstract fun bodyMeasurementDao(): BodyMeasurementDao
}
