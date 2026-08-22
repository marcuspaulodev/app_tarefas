package com.marcuspaulo.tarefas.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [Task::class, Tag::class, TaskTagCrossRef::class],
    version = 4,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun taskDao(): TaskDao
    abstract fun tagDao(): TagDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        private val SEED_TAGS_CALLBACK = object : RoomDatabase.Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                super.onCreate(db)
                db.execSQL("INSERT INTO tags (name, colorHex) VALUES ('Trabalho', '#3D5AFE')")
                db.execSQL("INSERT INTO tags (name, colorHex) VALUES ('Casa', '#00C853')")
                db.execSQL("INSERT INTO tags (name, colorHex) VALUES ('Faculdade', '#FF6D00')")
            }
        }

        fun getInstance(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "tarefas.db"
                )
                    // App em desenvolvimento: em vez de escrever migrations, recria o banco
                    // ao mudar o schema. Perde dados de teste, mas evita crash por schema divergente.
                    .fallbackToDestructiveMigration()
                    .addCallback(SEED_TAGS_CALLBACK)
                    .build().also { INSTANCE = it }
            }
    }
}
