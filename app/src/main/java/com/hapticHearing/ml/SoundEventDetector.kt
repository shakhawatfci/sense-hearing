package com.hapticHearing.ml

/** What Home shows in the "Now hearing" card. */
data class SoundDetection(
    val categoryId: String,
    val label: String,
    val confidence: Float,
    val priority: SoundPriority,
    val firstSeenMs: Long,
    val lastSeenMs: Long,
)

/** A confirmed sound that should play its pattern. */
data class SoundAlert(
    val categoryId: String,
    val label: String,
    val confidence: Float,
    val priority: SoundPriority,
    val patternId: String,
)

/** Per-category rule (defaults from the JSON table; user overrides arrive with the Sounds screen). */
data class CategoryRule(
    val alertEnabled: Boolean,
    val minConfidence: Float,
    val cooldownSec: Int,
    val patternId: String,
)

/**
 * Debounce of spec §6: a category fires only if its score ≥ its minimum confidence in
 * [required] of the last [historySize] windows, and the overall level passed the user's dB
 * threshold during that time. DANGER sounds skip the level check (favour false alarms over misses).
 */
class SoundEventDetector(
    private val table: SoundCategoryTable,
    private val historySize: Int = 3,
    private val required: Int = 2,
    private val displayHoldMs: Long = 8_000,
) {
    private class Window(val scores: Map<String, CategoryScore>, val levelPassed: Boolean)

    private val history = ArrayDeque<Window>()
    private val lastAlertMs = HashMap<String, Long>()
    var current: SoundDetection? = null
        private set

    data class Output(val display: SoundDetection?, val alerts: List<SoundAlert>)

    fun reset() {
        history.clear()
        current = null
    }

    fun onWindow(
        scores: Map<String, CategoryScore>,
        levelPassed: Boolean,
        nowMs: Long,
        ruleOf: (SoundCategory) -> CategoryRule = ::defaultRule,
    ): Output {
        history.addLast(Window(scores, levelPassed))
        while (history.size > historySize) history.removeFirst()

        val confirmed = ArrayList<Pair<SoundCategory, CategoryScore>>()
        val ids = history.flatMap { it.scores.keys }.toSet()
        for (id in ids) {
            val cat = table[id] ?: continue
            val rule = ruleOf(cat)
            val hits = history.filter { w -> (w.scores[id]?.score ?: 0f) >= rule.minConfidence }
            val loudEnough = cat.priority == SoundPriority.DANGER || hits.any { it.levelPassed }
            if (hits.size >= required && loudEnough) {
                val latest = scores[id] ?: hits.last().scores.getValue(id)
                confirmed += cat to latest
            }
        }

        // Display: most important first, then the strongest.
        val best = confirmed.minWithOrNull(compareBy<Pair<SoundCategory, CategoryScore>> { it.first.priority.ordinal }.thenByDescending { it.second.score })
        val prev = current
        current = when {
            best != null && prev?.categoryId == best.first.id ->
                prev.copy(label = best.second.label, confidence = best.second.score, lastSeenMs = nowMs)
            best != null ->
                SoundDetection(best.first.id, best.second.label, best.second.score, best.first.priority, nowMs, nowMs)
            prev != null && nowMs - prev.lastSeenMs < displayHoldMs -> prev
            else -> null
        }

        val alerts = ArrayList<SoundAlert>()
        for ((cat, s) in confirmed) {
            if (cat.priority == SoundPriority.INFO) continue
            val rule = ruleOf(cat)
            if (!rule.alertEnabled) continue
            val last = lastAlertMs[cat.id]
            if (last != null && nowMs - last < rule.cooldownSec * 1000L) continue
            lastAlertMs[cat.id] = nowMs
            alerts += SoundAlert(cat.id, s.label, s.score, cat.priority, rule.patternId)
        }
        return Output(current, alerts)
    }

    companion object {
        fun defaultRule(c: SoundCategory) = CategoryRule(c.alertByDefault, c.minConfidence, c.cooldownSec, c.patternId)
    }
}
