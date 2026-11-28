package com.furugit.app.ui.components

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.view.View
import android.view.animation.LinearInterpolator
import com.furugit.app.core.common.L
import kotlin.math.min

/** Tanda hasil beranimasi: lingkaran lalu ceklis hijau (berhasil) atau silang merah (gagal) tergambar bertahap. */
class ResultMark(context: Context, private val ok: Boolean) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()
    private val path = Path()
    private val seg = Path()
    private var p = 0f

    init {
        paint.style = Paint.Style.STROKE
        paint.strokeCap = Paint.Cap.ROUND
        paint.strokeJoin = Paint.Join.ROUND
        paint.strokeWidth = context.dpf(5f)
        paint.color = if (ok) 0xFF2FBF71.toInt() else 0xFFE5484D.toInt()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        scaleX = 0.6f
        scaleY = 0.6f
        animate().scaleX(1f).scaleY(1f).setDuration(800L)
            .setInterpolator(android.view.animation.OvershootInterpolator(2f)).start()
        val a = ValueAnimator.ofFloat(0f, 1f)
        a.duration = 1200L
        a.addUpdateListener {
            p = it.animatedValue as Float
            invalidate()
        }
        a.start()
    }

    private fun drawPart(c: Canvas, t: Float) {
        if (t <= 0f) return
        val pm = android.graphics.PathMeasure(path, false)
        seg.reset()
        pm.getSegment(0f, pm.length * t, seg, true)
        c.drawPath(seg, paint)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val s = min(width, height).toFloat()
        val inset = paint.strokeWidth
        rect.set(inset, inset, s - inset, s - inset)
        canvas.drawArc(rect, -90f, 360f * min(p / 0.6667f, 1f), false, paint)
        val t = ((p - 0.6667f) / 0.3333f).coerceIn(0f, 1f)
        if (t <= 0f) return
        if (ok) {
            path.reset()
            path.moveTo(0.28f * s, 0.52f * s)
            path.lineTo(0.44f * s, 0.68f * s)
            path.lineTo(0.74f * s, 0.36f * s)
            drawPart(canvas, t)
        } else {
            path.reset()
            path.moveTo(0.34f * s, 0.34f * s)
            path.lineTo(0.66f * s, 0.66f * s)
            drawPart(canvas, (t * 2f).coerceIn(0f, 1f))
            path.reset()
            path.moveTo(0.66f * s, 0.34f * s)
            path.lineTo(0.34f * s, 0.66f * s)
            drawPart(canvas, (t * 2f - 1f).coerceIn(0f, 1f))
        }
    }
}

/** Titik besar penanda status; bila [spinning] digambar sebagai cincin kuning yang berputar (proses berjalan). */
class DotView(context: Context) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()
    private var anim: android.animation.ObjectAnimator? = null
    var dotColor: Int = Color.WHITE
        set(v) {
            field = v
            invalidate()
        }
    var spinning: Boolean = false
        set(v) {
            field = v
            if (isAttachedToWindow) updateAnim()
            invalidate()
        }

    private fun updateAnim() {
        if (spinning) {
            if (anim == null) {
                val a = android.animation.ObjectAnimator.ofFloat(this, View.ROTATION, 0f, 360f)
                a.duration = 900L
                a.repeatCount = ValueAnimator.INFINITE
                a.interpolator = android.view.animation.LinearInterpolator()
                anim = a
                a.start()
            }
        } else {
            anim?.cancel()
            anim = null
            rotation = 0f
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        updateAnim()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        anim?.cancel()
        anim = null
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val s = min(width, height).toFloat()
        paint.color = dotColor
        if (spinning) {
            val sw = context.dpf(2.5f)
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = sw
            paint.strokeCap = Paint.Cap.ROUND
            rect.set(sw / 2f, sw / 2f, s - sw / 2f, s - sw / 2f)
            canvas.drawArc(rect, 0f, 270f, false, paint)
        } else {
            paint.style = Paint.Style.FILL
            canvas.drawCircle(s / 2f, s / 2f, s / 2f, paint)
        }
    }
}
