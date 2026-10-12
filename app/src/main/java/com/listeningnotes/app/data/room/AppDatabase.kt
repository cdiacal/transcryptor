package com.listeningnotes.app.data.room

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [TranscriptionEntity::class, SegmentEntity::class, VocabEntity::class],
    version = 2,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun transcriptionDao(): TranscriptionDao
    abstract fun vocabDao(): VocabDao

    companion object {
        /** v1 -> v2: añade la tabla de vocabulario sin tocar transcripciones ni frases. */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `vocabulary` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`palabra` TEXT NOT NULL, `nota` TEXT NOT NULL, `contexto` TEXT NOT NULL, " +
                        "`transcriptionId` INTEGER NOT NULL, `inicioMs` INTEGER NOT NULL, " +
                        "`tituloOrigen` TEXT NOT NULL, `fecha` INTEGER NOT NULL)"
                )
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_vocabulary_palabra` ON `vocabulary` (`palabra`)")
            }
        }

        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "listening_notes.db")
                .addMigrations(MIGRATION_1_2)
                .build()
    }
}
