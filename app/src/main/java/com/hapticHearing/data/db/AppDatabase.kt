package com.hapticHearing.data.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [Person::class, VoiceSample::class, NameTemplate::class, SoundCategoryRule::class, EventLog::class, CustomPattern::class],
    version = 1,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun personDao(): PersonDao
    abstract fun voiceSampleDao(): VoiceSampleDao
    abstract fun nameTemplateDao(): NameTemplateDao
    abstract fun soundCategoryRuleDao(): SoundCategoryRuleDao
    abstract fun eventLogDao(): EventLogDao
    abstract fun customPatternDao(): CustomPatternDao
}
