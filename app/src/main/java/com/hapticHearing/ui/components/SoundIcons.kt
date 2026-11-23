package com.hapticHearing.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Celebration
import androidx.compose.material.icons.filled.ChildCare
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Doorbell
import androidx.compose.material.icons.filled.Emergency
import androidx.compose.material.icons.filled.Flight
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.MeetingRoom
import androidx.compose.material.icons.filled.Microwave
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.RingVolume
import androidx.compose.material.icons.filled.SentimentVerySatisfied
import androidx.compose.material.icons.filled.Train
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.filled.WineBar
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp
import com.hapticHearing.R

/** Bundled icon + display name per sound category (spec §6: no images from the web). */
object SoundIcons {
    fun icon(categoryId: String): ImageVector = when (categoryId) {
        "fire_alarm" -> Icons.Filled.LocalFireDepartment
        "siren" -> Icons.Filled.Emergency
        "car_horn" -> Icons.Filled.Campaign
        "glass" -> Icons.Filled.WineBar
        "baby_cry" -> Icons.Filled.ChildCare
        "doorbell" -> Icons.Filled.Doorbell
        "knock" -> Icons.Filled.MeetingRoom
        "phone" -> Icons.Filled.RingVolume
        "alarm_clock" -> Icons.Filled.Alarm
        "dog", "cat" -> Icons.Filled.Pets
        "water" -> Icons.Filled.WaterDrop
        "kitchen" -> Icons.Filled.Microwave
        "speech" -> Icons.Filled.RecordVoiceOver
        "laughter" -> Icons.Filled.SentimentVerySatisfied
        "clapping" -> Icons.Filled.Celebration
        "vehicle" -> Icons.Filled.DirectionsCar
        "truck" -> Icons.Filled.LocalShipping
        "train" -> Icons.Filled.Train
        "aircraft" -> Icons.Filled.Flight
        "bird" -> Bird
        "music" -> Icons.Filled.MusicNote
        else -> Icons.Filled.GraphicEq
    }

    fun name(categoryId: String): Int = when (categoryId) {
        "fire_alarm" -> R.string.cat_fire_alarm
        "siren" -> R.string.cat_siren
        "car_horn" -> R.string.cat_car_horn
        "glass" -> R.string.cat_glass
        "baby_cry" -> R.string.cat_baby_cry
        "doorbell" -> R.string.cat_doorbell
        "knock" -> R.string.cat_knock
        "phone" -> R.string.cat_phone
        "alarm_clock" -> R.string.cat_alarm_clock
        "dog" -> R.string.cat_dog
        "cat" -> R.string.cat_cat
        "water" -> R.string.cat_water
        "kitchen" -> R.string.cat_kitchen
        "speech" -> R.string.cat_speech
        "laughter" -> R.string.cat_laughter
        "clapping" -> R.string.cat_clapping
        "vehicle" -> R.string.cat_vehicle
        "truck" -> R.string.cat_truck
        "train" -> R.string.cat_train
        "aircraft" -> R.string.cat_aircraft
        "bird" -> R.string.cat_bird
        "music" -> R.string.cat_music
        else -> R.string.cat_unknown
    }

    /** Simple perched-bird silhouette (Material has no bird icon). 24×24 grid like Material icons. */
    val Bird: ImageVector by lazy {
        ImageVector.Builder("Bird", 24.dp, 24.dp, 24f, 24f).apply {
            path(fill = SolidColor(Color.Black)) {
                // body + head, beak pointing right
                moveTo(16.5f, 4f)
                curveTo(14.6f, 4f, 13.1f, 5.4f, 13f, 7.3f)
                curveTo(12.2f, 7.1f, 11.4f, 7f, 10.5f, 7f)
                curveTo(6f, 7f, 2.5f, 10.1f, 2f, 14f)
                lineTo(6.5f, 12.8f)
                curveTo(7.1f, 15.8f, 9.8f, 18f, 13f, 18f)
                lineTo(14f, 18f)
                lineTo(14f, 21f)
                lineTo(15.5f, 21f)
                lineTo(15.5f, 18f)
                lineTo(17f, 18f)
                lineTo(17f, 21f)
                lineTo(18.5f, 21f)
                lineTo(18.5f, 17.4f)
                curveTo(19.4f, 16.4f, 20f, 15f, 20f, 13.5f)
                lineTo(20f, 8.5f)
                lineTo(23f, 7.5f)
                lineTo(20f, 6.2f)
                curveTo(19.4f, 4.9f, 18.1f, 4f, 16.5f, 4f)
                close()
            }
        }.build()
    }
}
