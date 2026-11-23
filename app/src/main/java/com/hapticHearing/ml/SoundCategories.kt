package com.hapticHearing.ml

import android.content.Context
import org.json.JSONObject

/** INFO = shown on Home only (voice, vehicle, bird…); never plays an event pattern. */
enum class SoundPriority { DANGER, HIGH, NORMAL, LOW, INFO }

data class SoundCategory(
    val id: String,
    val priority: SoundPriority,
    /** Plays its vibration pattern (and is logged in History) by default. */
    val alertByDefault: Boolean,
    val minConfidence: Float,
    val cooldownSec: Int,
    val patternId: String,
    val labels: List<String>,
)

data class LabelScore(val label: String, val score: Float)

/** Best-scoring YAMNet label of a category in one window. */
data class CategoryScore(val categoryId: String, val label: String, val score: Float)

/** The bundled `assets/sound_categories.json` table: YAMNet label → app category. */
class SoundCategoryTable(val categories: List<SoundCategory>) {
    private val byId = categories.associateBy { it.id }
    private val byLabel: Map<String, SoundCategory> = buildMap {
        categories.forEach { c -> c.labels.forEach { l -> put(l, c) } }
    }

    operator fun get(id: String): SoundCategory? = byId[id]

    fun categoryOf(label: String): SoundCategory? = byLabel[label]

    /** Max score per category over the classifier's labels. */
    fun score(results: List<LabelScore>): Map<String, CategoryScore> {
        val out = HashMap<String, CategoryScore>()
        for (r in results) {
            val c = byLabel[r.label] ?: continue
            val prev = out[c.id]
            if (prev == null || r.score > prev.score) out[c.id] = CategoryScore(c.id, r.label, r.score)
        }
        return out
    }

    companion object {
        const val ASSET = "sound_categories.json"

        fun load(context: Context): SoundCategoryTable =
            parse(context.assets.open(ASSET).bufferedReader().use { it.readText() })

        fun parse(json: String): SoundCategoryTable {
            val arr = JSONObject(json).getJSONArray("categories")
            val list = (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                val labels = o.getJSONArray("labels")
                SoundCategory(
                    id = o.getString("id"),
                    priority = SoundPriority.valueOf(o.getString("priority")),
                    alertByDefault = o.getBoolean("alert"),
                    minConfidence = o.getDouble("minConfidence").toFloat(),
                    cooldownSec = o.getInt("cooldownSec"),
                    patternId = o.getString("pattern"),
                    labels = (0 until labels.length()).map { labels.getString(it) },
                )
            }
            return SoundCategoryTable(list)
        }
    }
}
