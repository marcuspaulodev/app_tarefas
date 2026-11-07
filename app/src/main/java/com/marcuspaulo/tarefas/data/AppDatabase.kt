package com.marcuspaulo.tarefas.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [Task::class, Tag::class, TaskTagCrossRef::class],
    version = 5,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun taskDao(): TaskDao
    abstract fun tagDao(): TagDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        private val DEFAULT_TAGS = listOf(
            "Trabalho" to "#3D5AFE",
            "Casa" to "#00C853",
            "Faculdade" to "#FF6D00"
        )

        private val SEED_TAGS_CALLBACK = object : RoomDatabase.Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                super.onCreate(db)
                val now = System.currentTimeMillis()
                DEFAULT_TAGS.forEach { (name, color) ->
                    db.execSQL(
                        "INSERT INTO tags (id, name, colorHex, updatedAt, deletedAt) VALUES (?, ?, ?, ?, NULL)",
                        arrayOf<Any?>(Ids.newId(now), name, color, now)
                    )
                }
            }
        }

        fun getInstance(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "tarefas.db"
                )
                    .addMigrations(*ALL_MIGRATIONS)
                    // As versões 1 a 3 são anteriores ao uso real do app e nunca tiveram
                    // migration. A partir da 4, mudar o schema exige uma em Migrations.kt.
                    .fallbackToDestructiveMigrationFrom(1, 2, 3)
                    .addCallback(SEED_TAGS_CALLBACK)
                    .build().also { INSTANCE = it }
            }
    }
}
