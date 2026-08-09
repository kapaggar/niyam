package org.dhamma.gong.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * The appliance's whole store. WAL, on-disk, field-repairable: an admin can
 * pull `gong.db` over USB and open it with any SQLite tool (design doc §03).
 */
@Database(
    entities = [
        CourseTypeEntity::class,
        CourseEntity::class,
        ScheduleEventEntity::class,
        SettingEntity::class,
        StateEntity::class,
        PlayLogEntity::class,
        MediaSlotEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class GongDatabase : RoomDatabase() {
    abstract fun courseTypes(): CourseTypeDao
    abstract fun courses(): CourseDao
    abstract fun scheduleEvents(): ScheduleEventDao
    abstract fun settings(): SettingsDao
    abstract fun state(): StateDao
    abstract fun playLog(): PlayLogDao
    abstract fun mediaSlots(): MediaSlotDao

    companion object {
        const val FILE_NAME = "gong.db"

        @Volatile
        private var instance: GongDatabase? = null

        fun get(context: Context): GongDatabase = instance ?: synchronized(this) {
            instance ?: build(context.applicationContext).also { instance = it }
        }

        private fun build(context: Context): GongDatabase =
            Room.databaseBuilder(context, GongDatabase::class.java, FILE_NAME)
                .setJournalMode(JournalMode.WRITE_AHEAD_LOGGING)
                .addCallback(object : Callback() {
                    override fun onOpen(db: SupportSQLiteDatabase) {
                        db.execSQL("PRAGMA foreign_keys=ON")
                        db.execSQL("PRAGMA synchronous=NORMAL")
                    }
                })
                .build()

        /** Used after a backup restore swaps the file underneath us. */
        fun reset() = synchronized(this) {
            instance?.close()
            instance = null
        }
    }
}
