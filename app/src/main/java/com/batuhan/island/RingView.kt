package com.batuhan.island

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.view.View

class RingView(ctx: Context) : View(ctx) {
    private var pct = 0
    private var charging = false
    private var cell = 0
    private var wifi = -1

    private val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val arc = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val rect = RectF()
    private val on = Color.WHITE
    private val off = Color.argb(70, 255, 255, 255)
    private val chg = Color.rgb(160, 255, 170)

    fun update(p: Int, c: Boolean, cl: Int, w: Int) {
        pct = p; charging = c; cell = cl; wifi = w
        invalidate()
    }

    override fun onDraw(c: Canvas) {
        val s = minOf(width, height).toFloat()
        val cx = width / 2f
        val cy = height / 2f

        // Dış halka: pil (20 nokta, her biri %5)
        val n = 20
        val lit = Math.round(pct / 100f * n)
        val ringR = s * 0.44f
        val dotR = s * 0.045f
        for (i in 0 until n) {
            val a = Math.toRadians(-90.0 + i * 360.0 / n)
            dot.color = if (i < lit) (if (charging) chg else on) else off
            c.drawCircle(
                cx + (ringR * Math.cos(a)).toFloat(),
                cy + (ringR * Math.sin(a)).toFloat(),
                dotR, dot
            )
        }

        // İç halka: hat çekimi (4 yay parçası)
        arc.strokeWidth = s * 0.06f
        val cr = s * 0.33f
        rect.set(cx - cr, cy - cr, cx + cr, cy + cr)
        for (i in 0 until 4) {
            arc.color = if (i < cell) on else off
            c.drawArc(rect, 135f + i * 67.5f + 4f, 59.5f, false, arc)
        }

        // Orta: Wi-Fi
        val wy = cy + s * 0.12f
        val lv = if (wifi < 0) 0 else (wifi * 3 + 3) / 4
        arc.strokeWidth = s * 0.055f
        for (k in 1..3) {
            val r = s * (0.02f + 0.07f * k)
            rect.set(cx - r, wy - r, cx + r, wy + r)
            arc.color = if (wifi >= 0 && k <= lv) on else off
            c.drawArc(rect, 225f, 90f, false, arc)
        }
        dot.color = if (wifi >= 0) on else off
        c.drawCircle(cx, wy, s * 0.035f, dot)
    }
}
