package de.laurik.openalarm

import android.content.Context
import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

// 1. TYPE CONVERTERS (To save Lists and Enums)
class Converters {
    @TypeConverter
    fun fromIntListNullable(list: List<Int>?): String? = list?.joinToString(",")

    @TypeConverter
    fun toIntListNullable(data: String?): List<Int>? {
        if (data == null || data.isEmpty()) return null
        return data.split(",").mapNotNull { it.toIntOrNull() }
    }

    @TypeConverter
    fun fromAlarmType(type: AlarmType): String = type.name
    @TypeConverter
    fun toAlarmType(data: String): AlarmType = AlarmType.valueOf(data)

    @TypeConverter
    fun fromTtsMode(mode: TtsMode): String = mode.name
    @TypeConverter
    fun toTtsMode(data: String): TtsMode = TtsMode.NONE.let { try { TtsMode.valueOf(data) } catch(e:Exception){it} }

    @TypeConverter
    fun fromRingingScreenMode(mode: RingingScreenMode): String = mode.name
    @TypeConverter
    fun toRingingScreenMode(data: String): RingingScreenMode = RingingScreenMode.DEFAULT.let { try { RingingScreenMode.valueOf(data) } catch(e:Exception){it} }

    @TypeConverter
    fun fromCustomRingtoneSelectionMode(mode: CustomRingtoneSelectionMode): String = mode.name
    @TypeConverter
    fun toCustomRingtoneSelectionMode(data: String): CustomRingtoneSelectionMode = CustomRingtoneSelectionMode.SINGLE.let { try { CustomRingtoneSelectionMode.valueOf(data) } catch(e:Exception){it} }

    @TypeConverter
    fun fromHurdleType(type: HurdleType): String = type.name
    @TypeConverter
    fun toHurdleType(data: String): HurdleType = HurdleType.MATH_EASY.let { try { HurdleType.valueOf(data) } catch(e:Exception){it} }

    @TypeConverter
    fun fromHurdleTypeList(list: List<HurdleType>): String = list.joinToString(",") { it.name }
    @TypeConverter
    fun toHurdleTypeList(data: String): List<HurdleType> {
        if (data.isBlank()) return emptyList()
        return data.split(",").mapNotNull { try { HurdleType.valueOf(it) } catch(e:Exception){null} }
    }
}

// 2. DATA ACCESS OBJECT (The SQL commands)
@Dao
interface AlarmDao {
    // --- GROUPS & ALARMS ---

    @Transaction
    @Query("SELECT * FROM alarm_groups")
    suspend fun getAllGroups(): List<AlarmGroupEntity>

    @Update
    suspend fun updateGroup(group: AlarmGroupEntity)

    @Delete
    suspend fun deleteGroup(group: AlarmGroupEntity) // Cascades (deletes alarms) due to ForeignKey

    @Query("UPDATE alarms SET groupId = :targetGroupId WHERE groupId = :oldGroupId")
    suspend fun moveAlarmsToGroup(oldGroupId: String, targetGroupId: String)

    @Query("SELECT * FROM alarms WHERE groupId = :groupId")
    suspend fun getAlarmsForGroup(groupId: String): List<AlarmItem>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGroup(group: AlarmGroupEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAlarm(alarm: AlarmItem)

    @Update
    suspend fun updateAlarm(alarm: AlarmItem)

    @Delete
    suspend fun deleteAlarm(alarm: AlarmItem)

    // --- TIMERS ---

    @Query("SELECT * FROM timers")
    suspend fun getAllTimers(): List<TimerItem>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTimer(timer: TimerItem)

    @Delete
    suspend fun deleteTimer(timer: TimerItem)

    // --- INTERRUPTED STACK ---

    @Query("SELECT * FROM interrupted_items ORDER BY dbId ASC")
    suspend fun getInterruptedItems(): List<InterruptedItem>

    @Insert
    suspend fun insertInterrupted(item: InterruptedItem)

    @Delete
    suspend fun deleteInterrupted(item: InterruptedItem)

    @Query("DELETE FROM alarms")
    suspend fun clearAllAlarms()

    @Query("DELETE FROM alarm_groups")
    suspend fun clearAllGroups()

    @Query("DELETE FROM timers")
    suspend fun clearAllTimers()

    @Query("DELETE FROM interrupted_items")
    suspend fun clearAllInterrupted()

    @Query("DELETE FROM custom_ringtones")
    suspend fun clearAllCustomRingtones()

    // --- ID GENERATION ---
    @Query("SELECT MAX(id) FROM alarms")
    suspend fun getMaxAlarmId(): Int?

    @Query("SELECT MAX(id) FROM timers")
    suspend fun getMaxTimerId(): Int?

    // --- CUSTOM RINGTONES ---
    @Query("SELECT * FROM custom_ringtones")
    suspend fun getAllCustomRingtones(): List<CustomRingtoneEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCustomRingtone(ringtone: CustomRingtoneEntity)

    @Delete
    suspend fun deleteCustomRingtone(ringtone: CustomRingtoneEntity)
    
    @Query("SELECT * FROM custom_ringtones WHERE id = :id")
    suspend fun getCustomRingtone(id: String): CustomRingtoneEntity?
}

// 3. DATABASE INSTANCE
@Database(
    entities = [AlarmGroupEntity::class, AlarmItem::class, TimerItem::class, InterruptedItem::class, CustomRingtoneEntity::class],
    version = 4,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun alarmDao(): AlarmDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "openalarm_db"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                addColumnIfNotExists(db, "alarms", "ttsText", "TEXT")
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `custom_ringtones` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `uri` TEXT NOT NULL, `mode` TEXT NOT NULL, PRIMARY KEY(`id`))"
                )
                addColumnIfNotExists(db, "alarms", "ringtoneRotationIndex", "INTEGER NOT NULL DEFAULT 0")
                addColumnIfNotExists(db, "alarm_groups", "skippedUntil", "INTEGER NOT NULL DEFAULT 0")
                addColumnIfNotExists(db, "alarm_groups", "colorArgb", "INTEGER NOT NULL DEFAULT -1")
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Hurdles
                addColumnIfNotExists(db, "alarms", "hurdleEnabled", "INTEGER NOT NULL DEFAULT 0")
                addColumnIfNotExists(db, "alarms", "selectedHurdles", "TEXT NOT NULL DEFAULT ''")
                
                // Snooze Settings
                addColumnIfNotExists(db, "alarms", "isSnoozeEnabled", "INTEGER NOT NULL DEFAULT 1")
                addColumnIfNotExists(db, "alarms", "directSnooze", "INTEGER NOT NULL DEFAULT 0")
                addColumnIfNotExists(db, "alarms", "maxSnoozes", "INTEGER")
                addColumnIfNotExists(db, "alarms", "currentSnoozeCount", "INTEGER NOT NULL DEFAULT 0")
                
                // Ringing Screen Revamp
                addColumnIfNotExists(db, "alarms", "ringingScreenMode", "TEXT NOT NULL DEFAULT 'DEFAULT'")
                addColumnIfNotExists(db, "alarms", "backgroundType", "TEXT NOT NULL DEFAULT 'COLOR'")
                addColumnIfNotExists(db, "alarms", "backgroundValue", "TEXT NOT NULL DEFAULT '0xFF000000'")
                addColumnIfNotExists(db, "alarms", "snoozePresets", "TEXT")
                
                // Timers (NEW fields for 0.3.0)
                addColumnIfNotExists(db, "timers", "isPaused", "INTEGER NOT NULL DEFAULT 0")
                addColumnIfNotExists(db, "timers", "remainingMillis", "INTEGER NOT NULL DEFAULT 0")
                
                // Interrupted Items (Safety for new field)
                addColumnIfNotExists(db, "interrupted_items", "label", "TEXT NOT NULL DEFAULT ''")
            }
        }

        private fun addColumnIfNotExists(db: SupportSQLiteDatabase, tableName: String, columnName: String, columnDefinition: String) {
            val cursor = db.query("PRAGMA table_info($tableName)")
            var exists = false
            while (cursor.moveToNext()) {
                val name = cursor.getString(1) // Column name is at index 1
                if (name == columnName) {
                    exists = true
                    break
                }
            }
            cursor.close()
            if (!exists) {
                db.execSQL("ALTER TABLE $tableName ADD COLUMN $columnName $columnDefinition")
            }
        }
    }
}