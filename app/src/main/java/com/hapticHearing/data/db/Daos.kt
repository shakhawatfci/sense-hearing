package com.hapticHearing.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface PersonDao {
    @Query("SELECT * FROM Person WHERE isSelf = 0 ORDER BY createdAt")
    fun observeFamily(): Flow<List<Person>>

    @Query("SELECT * FROM Person WHERE isSelf = 1 LIMIT 1")
    fun observeSelf(): Flow<Person?>

    @Query("SELECT * FROM Person WHERE id = :id")
    suspend fun get(id: Long): Person?

    @Insert
    suspend fun insert(person: Person): Long

    @Update
    suspend fun update(person: Person)

    @Delete
    suspend fun delete(person: Person)
}

@Dao
interface VoiceSampleDao {
    @Query("SELECT * FROM VoiceSample WHERE kind = :kind")
    suspend fun allOfKind(kind: String): List<VoiceSample>

    @Query("SELECT * FROM VoiceSample WHERE personId = :personId")
    suspend fun forPerson(personId: Long): List<VoiceSample>

    @Insert
    suspend fun insert(sample: VoiceSample): Long

    @Query("DELETE FROM VoiceSample WHERE personId = :personId")
    suspend fun deleteForPerson(personId: Long)
}

@Dao
interface NameTemplateDao {
    @Query("SELECT * FROM NameTemplate")
    suspend fun all(): List<NameTemplate>

    @Insert
    suspend fun insert(template: NameTemplate): Long

    @Delete
    suspend fun delete(template: NameTemplate)
}

@Dao
interface SoundCategoryRuleDao {
    @Query("SELECT * FROM SoundCategoryRule")
    fun observeAll(): Flow<List<SoundCategoryRule>>

    @Upsert
    suspend fun upsert(rule: SoundCategoryRule)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertDefaults(rules: List<SoundCategoryRule>)
}

@Dao
interface EventLogDao {
    @Query("SELECT * FROM EventLog ORDER BY time DESC LIMIT :limit")
    fun observeRecent(limit: Int = 500): Flow<List<EventLog>>

    @Insert
    suspend fun insert(event: EventLog): Long

    @Query("DELETE FROM EventLog")
    suspend fun clear()
}

@Dao
interface CustomPatternDao {
    @Query("SELECT * FROM CustomPattern")
    fun observeAll(): Flow<List<CustomPattern>>

    @Upsert
    suspend fun upsert(pattern: CustomPattern)

    @Delete
    suspend fun delete(pattern: CustomPattern)
}
