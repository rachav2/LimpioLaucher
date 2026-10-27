package com.rachav2.civiclauncher

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.View
import kotlin.math.max

/**
 * Anillo del velocímetro: un arco de 270° que se llena en azul según la velocidad (0 a 200 km/h).
 */
class SpeedRingView(context: Context, private val strokeWidth: Float) : View(context) {

    private val track = Paint(Paint.ANTI_ALIAS_FLAG)
    private val glow = Paint(Paint.ANTI_ALIAS_FLAG)
    private val arc = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()
    private var fraction = 0f

    init {
        track.style = Paint.Style.STROKE
        track.strokeCap = Paint.Cap.ROUND
        track.strokeWidth = strokeWidth
        track.color = 0xFF16305C.toInt()

        glow.style = Paint.Style.STROKE
        glow.strokeCap = Paint.Cap.ROUND
        glow.strokeWidth = strokeWidth * 2.2f
        glow.color = 0x442F8CFF

        arc.style = Paint.Style.STROKE
        arc.strokeCap = Paint.Cap.ROUND
        arc.strokeWidth = strokeWidth
        arc.color = 0xFF2F8CFF.toInt()
    }

    fun setSpeed(kmh: Int) {
        val f = (kmh / 200f).coerceIn(0f, 1f)
        if (f != fraction) {
            fraction = f
            invalidate()
        }
    }

    override fun onDraw(canvas: Canvas) {
        val inset = strokeWidth * 1.6f
        rect.set(inset, inset, width - inset, height - inset)
        canvas.drawArc(rect, 135f, 270f, false, track)
        val sweep = max(fraction * 270f, 1.5f)
        canvas.drawArc(rect, 135f, sweep, false, glow)
        canvas.drawArc(rect, 135f, sweep, false, arc)
    }
}
