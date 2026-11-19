package com.hapticHearing.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** Data model of spec §11. Embeddings and MFCCs are float32 little-endian blobs. */

@Entity
data class Person(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val relation: String,
    val photoPath: String?,
    val patternId: String,
    val isSelf: Boolean = false,
    val createdAt: Long,
)

@Entity(
    foreignKeys = [ForeignKey(entity = Person::class, parentColumns = ["id"], childColumns = ["personId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("personId")],
)
data class VoiceSample(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val personId: Long,
    val wavPath: String,
    val embedding: ByteArray,
    val embedderId: String,
    /** "voice" | "name_call" */
    val kind: String,
    val createdAt: Long,
) {
    override fun equals(other: Any?): Boolean = other is VoiceSample && other.id == id
    override fun hashCode(): Int = id.hashCode()
}

@Entity(
    foreignKeys = [ForeignKey(entity = Person::class, parentColumns = ["id"], childColumns = ["speakerPersonId"], onDelete = ForeignKey.SET_NULL)],
    indices = [Index("speakerPersonId")],
)
data class NameTemplate(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val speakerPersonId: Long?,
    val wavPath: String,
    val mfcc: ByteArray,
    val frames: Int,
) {
    override fun equals(other: Any?): Boolean = other is NameTemplate && other.id == id
    override fun hashCode(): Int = id.hashCode()
}

@Entity
data class SoundCategoryRule(
    @PrimaryKey val categoryId: String,
    val enabled: Boolean,
    val priority: String,
    val patternId: String,
    val minConfidence: Float,
    val cooldownSec: Int,
)

@Entity(indices = [Index("time")])
data class EventLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val time: Long,
    val type: String,
    val label: String,
    val personId: Long?,
    val confidence: Float,
    val levelDb: Float,
)

@Entity
data class CustomPattern(
    @PrimaryKey val id: String,
    val name: String,
    /** Comma-separated ms values. */
    val timings: String,
    /** Comma-separated 0..255 values. */
    val amplitudes: String,
)
