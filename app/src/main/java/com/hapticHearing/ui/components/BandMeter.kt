package com.hapticHearing.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.hapticHearing.R
import com.hapticHearing.ui.theme.LocalBandColors
import kotlin.math.roundToInt

private const val METER_MIN_DB = 20f
private const val METER_MAX_DB = 110f

private fun frac(db: Float) = ((db - METER_MIN_DB) / (METER_MAX_DB - METER_MIN_DB)).coerceIn(0f, 1f)

/**
 * Three vertical band bars. Each shows the level (bar), the user's minimum loudness (solid line),
 * and the background noise floor + margin (dashed line). Active bands show a vibration icon and the
 * word "Vibrating" so colour is never the only cue.
 */
@Composable
fun BandMeter(
    bandDb: List<Float>,
    floorDb: List<Float>,
    active: List<Boolean>,
    edgesHz: List<Float>,
    minDb: Float,
    noiseMarginDb: Float,
    modifier: Modifier = Modifier,
) {
    val labels = listOf(R.string.band_low, R.string.band_mid, R.string.band_high).map { stringResource(it) }
    val colors = LocalBandColors.current
    val onSurface = MaterialTheme.colorScheme.onSurface
    val track = MaterialTheme.colorScheme.surface
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
        for (b in 0 until 3) {
            val isActive = active.getOrElse(b) { false }
            val db = bandDb.getOrElse(b) { 0f }
            val stateText = stringResource(if (isActive) R.string.band_active else R.string.band_quiet)
            val cd = stringResource(R.string.band_cd, labels[b], db.roundToInt(), stateText)
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .weight(1f)
                    .clearAndSetSemantics { contentDescription = cd },
            ) {
                Icon(
                    if (isActive) Icons.Filled.Vibration else Icons.Outlined.Circle,
                    contentDescription = null,
                    tint = if (isActive) colors[b] else onSurface.copy(alpha = 0.5f),
                    modifier = Modifier.size(32.dp),
                )
                Canvas(
                    Modifier
                        .size(width = 56.dp, height = 180.dp),
                ) {
                    val w = size.width
                    val h = size.height
                    drawRoundRect(track, size = Size(w, h), cornerRadius = CornerRadius(12f))
                    drawRoundRect(onSurface.copy(alpha = 0.4f), size = Size(w, h), cornerRadius = CornerRadius(12f), style = androidx.compose.ui.graphics.drawscope.Stroke(2f))
                    val fh = h * frac(db)
                    drawRoundRect(
                        color = if (isActive) colors[b] else colors[b].copy(alpha = 0.45f),
                        topLeft = Offset(0f, h - fh),
                        size = Size(w, fh),
                        cornerRadius = CornerRadius(12f),
                    )
                    val ty = h - h * frac(minDb)
                    drawLine(onSurface, Offset(-6f, ty), Offset(w + 6f, ty), strokeWidth = 5f)
                    val ny = h - h * frac(floorDb.getOrElse(b) { 0f } + noiseMarginDb)
                    drawLine(
                        onSurface,
                        Offset(0f, ny),
                        Offset(w, ny),
                        strokeWidth = 4f,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 8f)),
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(labels[b], style = MaterialTheme.typography.titleMedium)
                Text(
                    stringResource(
                        R.string.band_hz,
                        edgesHz.getOrElse(b) { 0f }.roundToInt(),
                        edgesHz.getOrElse(b + 1) { 0f }.roundToInt(),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center,
                )
                Text(stateText, style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

/** Legend explaining the solid and dashed lines. */
@Composable
fun BandMeterLegend(modifier: Modifier = Modifier) {
    val onSurface = MaterialTheme.colorScheme.onSurface
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Canvas(Modifier.size(width = 32.dp, height = 12.dp)) {
                drawLine(onSurface, Offset(0f, size.height / 2), Offset(size.width, size.height / 2), strokeWidth = 5f)
            }
            HSpace(8)
            Text(stringResource(R.string.threshold_line), style = MaterialTheme.typography.bodySmall)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Canvas(Modifier.size(width = 32.dp, height = 12.dp)) {
                drawLine(
                    onSurface,
                    Offset(0f, size.height / 2),
                    Offset(size.width, size.height / 2),
                    strokeWidth = 4f,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 8f)),
                )
            }
            HSpace(8)
            Text(stringResource(R.string.noise_floor), style = MaterialTheme.typography.bodySmall)
        }
    }
}
