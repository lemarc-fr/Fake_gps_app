package com.lemarc.fake_gps.ui

import androidx.compose.foundation.Canvas
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

/** Petit graphique de l'allure d'un signal de vitesse (valeurs ≥ 0, échelle de 0 au maximum). */
@Composable
fun Sparkline(values: DoubleArray, modifier: Modifier = Modifier) {
    val lineColor = MaterialTheme.colorScheme.primary
    val axisColor = MaterialTheme.colorScheme.outline
    Canvas(modifier) {
        val lineWidth = 2.dp.toPx()
        val axisY = size.height - lineWidth
        drawLine(axisColor, Offset(0f, axisY), Offset(size.width, axisY), strokeWidth = 1.dp.toPx())
        if (values.size < 2) return@Canvas

        val maxV = values.maxOrNull()?.takeIf { it > 0 } ?: 1.0
        val path = Path()
        values.forEachIndexed { i, v ->
            val x = size.width * i / (values.size - 1)
            val y = axisY - ((v / maxV) * (size.height - 2 * lineWidth)).toFloat()
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, lineColor, style = Stroke(width = lineWidth, join = StrokeJoin.Round))
    }
}
