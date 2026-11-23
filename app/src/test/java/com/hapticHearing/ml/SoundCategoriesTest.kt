package com.hapticHearing.ml

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.zip.ZipFile

class SoundCategoriesTest {
    private val assets = File("src/main/assets")
    private val table = SoundCategoryTable.parse(File(assets, SoundCategoryTable.ASSET).readText())

    @Test
    fun `every label exists in the bundled YAMNet class map`() {
        val labels = ZipFile(File(assets, "models/yamnet.tflite")).use { zip ->
            zip.getInputStream(zip.getEntry("yamnet_label_list.txt")).bufferedReader().readLines().map { it.trim() }.toSet()
        }
        assertEquals(521, labels.size)
        val missing = table.categories.flatMap { it.labels }.filterNot { it in labels }
        assertTrue("labels not in YAMNet: $missing", missing.isEmpty())
    }

    @Test
    fun `each label belongs to one category`() {
        val all = table.categories.flatMap { it.labels }
        assertEquals(all.size, all.toSet().size)
    }

    @Test
    fun `requested display categories exist`() {
        for (id in listOf("speech", "vehicle", "truck", "bird")) assertNotNull(table[id])
        assertEquals("speech", table.categoryOf("Speech")?.id)
        assertEquals("truck", table.categoryOf("Truck")?.id)
        assertEquals("bird", table.categoryOf("Chirp, tweet")?.id)
        assertNull(table.categoryOf("Silence"))
    }

    @Test
    fun `score keeps the best label per category`() {
        val s = table.score(listOf(LabelScore("Bird", 0.3f), LabelScore("Chirp, tweet", 0.6f), LabelScore("Silence", 0.9f)))
        assertEquals(1, s.size)
        assertEquals("Chirp, tweet", s.getValue("bird").label)
        assertEquals(0.6f, s.getValue("bird").score, 0f)
    }
}

class SoundEventDetectorTest {
    private val table = SoundCategoryTable.parse(File("src/main/assets/sound_categories.json").readText())

    private fun w(vararg pairs: Pair<String, Float>) =
        pairs.associate { (label, score) -> table.categoryOf(label)!!.id to CategoryScore(table.categoryOf(label)!!.id, label, score) }

    @Test
    fun `needs 2 of 3 windows`() {
        val d = SoundEventDetector(table)
        assertNull(d.onWindow(w("Speech" to 0.8f), true, 0).display)
        assertEquals("speech", d.onWindow(w("Speech" to 0.8f), true, 500).display?.categoryId)
    }

    @Test
    fun `one-off spike is ignored`() {
        val d = SoundEventDetector(table)
        d.onWindow(w("Doorbell" to 0.9f), true, 0)
        d.onWindow(emptyMap(), true, 500)
        val out = d.onWindow(emptyMap(), true, 1000)
        assertNull(out.display)
        assertTrue(out.alerts.isEmpty())
    }

    @Test
    fun `quiet sounds below the dB threshold do not fire, danger does`() {
        val d = SoundEventDetector(table)
        d.onWindow(w("Truck" to 0.8f, "Siren" to 0.8f), false, 0)
        val out = d.onWindow(w("Truck" to 0.8f, "Siren" to 0.8f), false, 500)
        assertEquals("siren", out.display?.categoryId)
        assertEquals(listOf("siren"), out.alerts.map { it.categoryId })
    }

    @Test
    fun `info categories display without alerts, alert categories alert once per cooldown`() {
        val d = SoundEventDetector(table)
        d.onWindow(w("Bird" to 0.7f), true, 0)
        val bird = d.onWindow(w("Bird" to 0.7f), true, 500)
        assertEquals("bird", bird.display?.categoryId)
        assertTrue(bird.alerts.isEmpty())

        val d2 = SoundEventDetector(table)
        d2.onWindow(w("Doorbell" to 0.7f), true, 0)
        assertEquals(1, d2.onWindow(w("Doorbell" to 0.7f), true, 500).alerts.size)
        assertEquals(0, d2.onWindow(w("Doorbell" to 0.7f), true, 1000).alerts.size)
        d2.onWindow(w("Doorbell" to 0.7f), true, 10_400)
        assertEquals(1, d2.onWindow(w("Doorbell" to 0.7f), true, 10_600).alerts.size)
    }

    @Test
    fun `important sound wins the display over voice`() {
        val d = SoundEventDetector(table)
        d.onWindow(w("Speech" to 0.9f, "Baby cry, infant cry" to 0.5f), true, 0)
        assertEquals("baby_cry", d.onWindow(w("Speech" to 0.9f, "Baby cry, infant cry" to 0.5f), true, 500).display?.categoryId)
    }

    @Test
    fun `display holds 8 s after the sound stops`() {
        val d = SoundEventDetector(table)
        d.onWindow(w("Car" to 0.6f), true, 0)
        d.onWindow(w("Car" to 0.6f), true, 500)
        d.onWindow(emptyMap(), true, 1000)
        d.onWindow(emptyMap(), true, 1500)
        // still confirmed at 1000 (2 of last 3), so it is held until 1000 + 8000
        assertEquals("vehicle", d.onWindow(emptyMap(), true, 8900).display?.categoryId)
        assertNull(d.onWindow(emptyMap(), true, 9100).display)
    }
}
