package com.marcuspaulo.tarefas.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.sqlite.db.SupportSQLiteDatabase
import com.marcuspaulo.tarefas.sync.Session
import com.marcuspaulo.tarefas.sync.SessionStore

@Database(
    entities = [Task::class, Tag::class, TaskTagCrossRef::class, SyncState::class],
    version = 7,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun taskDao(): TaskDao
    abstract fun tagDao(): TagDao
    abstract fun syncStateDao(): SyncStateDao

    companion object {
        private val instances = mutableMapOf<String, AppDatabase>()

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

        fun getInstance(context: Context): AppDatabase = checkNotNull(activeOrNull(context)) {
            "Entre na conta para abrir suas tarefas."
        }

        fun activeOrNull(context: Context): AppDatabase? =
            forSession(context, SessionStore.get(context).session.value)

        fun forSession(context: Context, session: Session?): AppDatabase? {
            val name = SessionStore.get(context).databaseName(session) ?: return null
            return synchronized(this) {
                instances.getOrPut(name) {
                    val builder = Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, name)
                        .addMigrations(*ALL_MIGRATIONS)
                        .fallbackToDestructiveMigrationFrom(1, 2, 3)
                    if (name == "tarefas.db") builder.addCallback(SEED_TAGS_CALLBACK)
                    builder.build()
                }
            }
        }
    }
}
