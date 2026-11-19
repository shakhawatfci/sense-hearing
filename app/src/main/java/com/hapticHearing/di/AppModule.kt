package com.hapticHearing.di

import android.content.Context
import androidx.room.Room
import com.hapticHearing.data.db.AppDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides
    @Singleton
    fun database(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, "haptic_hearing.db").build()

    @Provides fun personDao(db: AppDatabase) = db.personDao()
    @Provides fun voiceSampleDao(db: AppDatabase) = db.voiceSampleDao()
    @Provides fun nameTemplateDao(db: AppDatabase) = db.nameTemplateDao()
    @Provides fun soundCategoryRuleDao(db: AppDatabase) = db.soundCategoryRuleDao()
    @Provides fun eventLogDao(db: AppDatabase) = db.eventLogDao()
    @Provides fun customPatternDao(db: AppDatabase) = db.customPatternDao()
}
