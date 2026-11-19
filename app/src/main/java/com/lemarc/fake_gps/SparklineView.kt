package com.lemarc.fake_gps

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.View

/** Petit graphique de l'allure d'un signal de vitesse. */
class SparklineView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    private val density = resources.displayMetrics.density
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2 * density
        strokeJoin = Paint.Join.ROUND
    }
    private val axisPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = density
    }
    private val path = Path()

    private var values: DoubleArray = DoubleArray(0)

    fun setValues(values: DoubleArray, lineColor: Int, axisColor: Int) {
        this.values = values
        linePaint.color = lineColor
        axisPaint.color = axisColor
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        val pad = linePaint.strokeWidth
        canvas.drawLine(0f, h - pad, w, h - pad, axisPaint)
        if (values.size < 2) return

        val maxV = values.maxOrNull()?.takeIf { it > 0 } ?: 1.0
        path.reset()
        values.forEachIndexed { i, v ->
            val x = w * i / (values.size - 1)
            val y = (h - pad) - ((v / maxV) * (h - 2 * pad)).toFloat()
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        canvas.drawPath(path, linePaint)
    }
}
