package com.rachav2.civiclauncher

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.View
import kotlin.math.max
import kotlin.math.min

/** Semicircular digital speedometer for the trip screen. */
internal class SpeedometerView(context: Context) : View(context) {
    private val track = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
    private val progress = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
    private val ticks = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
    private val limitMarker = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
    private val number = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER; typeface = android.graphics.Typeface.DEFAULT_BOLD }
    private val caption = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER; typeface = android.graphics.Typeface.DEFAULT_BOLD }
    private val tickText = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER; typeface = android.graphics.Typeface.DEFAULT_BOLD }
    private val arc = RectF()
    private var speedKmh = 0
    private var limitKmh = 90

    fun setReading(speed: Int, limit: Int) {
        speedKmh = speed.coerceAtLeast(0)
        limitKmh = limit.coerceAtLeast(1)
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val stroke = height * 0.075f
        val inset = stroke * 1.2f
        track.strokeWidth = stroke
        track.color = 0xFF1B3152.toInt()
        progress.strokeWidth = stroke
        progress.color = if (speedKmh > limitKmh) 0xFFFF4545.toInt() else 0xFF4B91FF.toInt()
        ticks.strokeWidth = max(2f, stroke * 0.14f)
        ticks.color = 0xFF8FA6C9.toInt()

        val centerY = height * 0.83f
        val radiusX = width / 2f - inset * 2f
        val radiusY = height * 0.78f
        arc.set(width / 2f - radiusX, centerY - radiusY, width / 2f + radiusX, centerY + radiusY)
        canvas.drawArc(arc, 180f, 180f, false, track)

        val maxDisplay = max(180, limitKmh)
        val fraction = min(speedKmh.toFloat() / maxDisplay, 1f)
        canvas.drawArc(arc, 180f, 180f * fraction, false, progress)

        val limitAngle = Math.toRadians(180.0 + 180.0 * limitKmh / maxDisplay)
        val markerOuterX = radiusX + stroke * 0.9f
        val markerOuterY = radiusY + stroke * 0.9f
        val markerInnerX = radiusX - stroke * 0.9f
        val markerInnerY = radiusY - stroke * 0.9f
        limitMarker.strokeWidth = stroke * 0.35f
        limitMarker.color = 0xFFFF6A6A.toInt()
        canvas.drawLine(
            width / 2f + (kotlin.math.cos(limitAngle) * markerOuterX).toFloat(), centerY + (kotlin.math.sin(limitAngle) * markerOuterY).toFloat(),
            width / 2f + (kotlin.math.cos(limitAngle) * markerInnerX).toFloat(), centerY + (kotlin.math.sin(limitAngle) * markerInnerY).toFloat(), limitMarker
        )

        for (i in 0..6) {
            val angle = Math.toRadians((180 + i * 30).toDouble())
            val rOuterX = radiusX + stroke * 0.75f
            val rOuterY = radiusY + stroke * 0.75f
            val rInnerX = radiusX - stroke * 0.6f
            val rInnerY = radiusY - stroke * 0.6f
            val cx = width / 2f
            canvas.drawLine(
                cx + (kotlin.math.cos(angle) * rOuterX).toFloat(), centerY + (kotlin.math.sin(angle) * rOuterY).toFloat(),
                cx + (kotlin.math.cos(angle) * rInnerX).toFloat(), centerY + (kotlin.math.sin(angle) * rInnerY).toFloat(), ticks
            )
        }

        // Cifras del tacómetro (0 a la velocidad máxima de la escala) dentro del arco.
        tickText.color = 0xFF8FA6C9.toInt()
        tickText.textSize = height * 0.075f
        for (i in 0..6) {
            val angle = Math.toRadians((180 + i * 30).toDouble())
            val lx = width / 2f + (kotlin.math.cos(angle) * (radiusX - stroke * 2.3f)).toFloat()
            val ly = centerY + (kotlin.math.sin(angle) * (radiusY - stroke * 2.3f)).toFloat()
            canvas.drawText((maxDisplay * i / 6).toString(), lx, ly + tickText.textSize * 0.35f, tickText)
        }

        number.color = if (speedKmh > limitKmh) 0xFFFF6666.toInt() else 0xFFFFFFFF.toInt()
        number.textSize = height * 0.36f
        canvas.drawText(speedKmh.toString(), width / 2f, height * 0.76f, number)
        caption.color = 0xFFB7C8E2.toInt()
        caption.textSize = height * 0.105f
        canvas.drawText("KM/H", width / 2f, height * 0.92f, caption)
    }
}
