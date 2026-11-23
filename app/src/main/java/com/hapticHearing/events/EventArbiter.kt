package com.hapticHearing.events

import com.hapticHearing.haptics.VibrationPattern

/** Spec §9.1: NAME_CALL > DANGER > PERSON > HIGH > NORMAL > continuous vibration. */
enum class EventPriority(val rank: Int) { NORMAL(1), HIGH(2), PERSON(3), DANGER(4), NAME_CALL(5) }

class ArbiterEvent(val id: String, val priority: EventPriority, val pattern: VibrationPattern)

/**
 * Decides which event pattern plays. A higher priority interrupts a lower one; equal or lower waits in a
 * queue of at most [maxQueue] (the oldest NORMAL is dropped first). Call [poll] regularly; both methods
 * return the event that should start playing now, or null.
 */
class EventArbiter(private val maxQueue: Int = 3) {
    private var playing: ArbiterEvent? = null
    private var playingUntilMs = 0L
    private val queue = ArrayList<ArbiterEvent>()

    @Synchronized
    fun submit(event: ArbiterEvent, nowMs: Long): ArbiterEvent? {
        val cur = playing
        if (cur == null || nowMs >= playingUntilMs || event.priority.rank > cur.priority.rank) {
            return start(event, nowMs)
        }
        if (queue.size >= maxQueue) {
            val victim = queue.firstOrNull { it.priority == EventPriority.NORMAL } ?: queue.minBy { it.priority.rank }
            if (victim.priority.rank > event.priority.rank) return null // everything queued matters more
            queue.remove(victim)
        }
        queue += event
        return null
    }

    @Synchronized
    fun poll(nowMs: Long): ArbiterEvent? {
        if (playing != null && nowMs < playingUntilMs) return null
        playing = null
        if (queue.isEmpty()) return null
        val next = queue.maxBy { it.priority.rank } // maxBy keeps the first (oldest) on ties
        queue.remove(next)
        return start(next, nowMs)
    }

    @Synchronized
    fun reset() {
        playing = null
        queue.clear()
    }

    val isPlaying: Boolean @Synchronized get() = playing != null

    private fun start(event: ArbiterEvent, nowMs: Long): ArbiterEvent {
        playing = event
        playingUntilMs = nowMs + event.pattern.totalMs
        return event
    }
}
