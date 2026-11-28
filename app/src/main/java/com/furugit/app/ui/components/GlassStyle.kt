package com.furugit.app.ui.components

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import kotlin.math.max

/** Gaya satu permukaan kaca: isi bergradien, tepi bergradien, dan cahaya biru tipis di sekelilingnya. */
class GlassStyle(
    val radius: Float,
    val fillA: Int, val fillB: Int, val vertical: Boolean,
    val rimA: Int, val rimB: Int, val rimWidth: Float,
    val glowBase: Int, val glowPeak: Float, val reach: Float
) {
    companion object {
        /** Kartu dan panel (BlossomCard / RowPanel di FuruArc): radius 10dp, tepi 1,5dp, cahaya 4dp. */
        fun card(ctx: Context): GlassStyle {
            val p = Pal.of(ctx)
            return GlassStyle(
                ctx.dpf(10f), p.glassA, p.glassB, false, p.rimA, p.rimB, ctx.dpf(1.5f),
                p.glowBase, p.glowPeak, ctx.dpf(4f)
            )
        }

        /** Bilah navigasi kaca (GlassBar di FuruArc): isi vertikal, tepi 1dp. */
        fun nav(ctx: Context): GlassStyle {
            val p = Pal.of(ctx)
            return GlassStyle(
                ctx.dpf(10f), p.navA, p.navB, true, p.navRimA, p.navRimB, ctx.dpf(1f),
                p.glowBase, p.glowPeak, ctx.dpf(4f)
            )
        }
    }
}

/** Menggambar permukaan kaca: cahaya di luar, isi bergradien, lalu tepi (tepi digambar paling akhir, di atas isi). */
class GlassPainter(private val s: GlassStyle) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()
    private val rect2 = RectF()
    private val path = Path()
    private var lw = -1f
    private var lh = -1f
    private var fillShader: Shader? = null
    private var rimShader: Shader? = null

    private fun ensure(w: Float, h: Float) {
        if (w == lw && h == lh) return
        lw = w
        lh = h
        fillShader = if (s.vertical) LinearGradient(0f, 0f, 0f, h, s.fillA, s.fillB, Shader.TileMode.CLAMP)
        else LinearGradient(0f, 0f, w, h, s.fillA, s.fillB, Shader.TileMode.CLAMP)
        rimShader = LinearGradient(0f, 0f, w, h, s.rimA, s.rimB, Shader.TileMode.CLAMP)
    }

    /** Cahaya + isi. Cahaya digambar di luar batas (permukaannya sendiri dipotong keluar agar tidak tembus). */
    fun drawBack(c: Canvas, w: Float, h: Float) {
        ensure(w, h)
        val r = s.radius
        rect.set(0f, 0f, w, h)
        if (s.reach > 0f && s.glowPeak > 0f) {
            path.reset()
            path.addRoundRect(rect, r, r, Path.Direction.CW)
            c.save()
            c.clipOutPath(path)
            val steps = 8
            val dy = s.reach * 0.4f
            paint.style = Paint.Style.FILL
            paint.shader = null
            for (i in steps downTo 1) {
                val grow = s.reach * i / steps
                val f = 1f - (i - 1) / steps.toFloat()
                paint.color = s.glowBase.withAlpha((s.glowPeak * f * 255f + 0.5f).toInt())
                rect2.set(-grow, -grow + dy, w + grow, h + grow + dy)
                c.drawRoundRect(rect2, r + grow, r + grow, paint)
            }
            c.restore()
        }
        paint.style = Paint.Style.FILL
        paint.color = Color.BLACK
        paint.shader = fillShader
        c.drawRoundRect(rect, r, r, paint)
        paint.shader = null
    }

    /** Memotong kanvas sesuai bentuk permukaan (untuk gambar hiasan). Panggil di antara save() dan restore(). */
    fun clipShape(c: Canvas, w: Float, h: Float) {
        rect.set(0f, 0f, w, h)
        path.reset()
        path.addRoundRect(rect, s.radius, s.radius, Path.Direction.CW)
        c.clipPath(path)
    }

    /** Tepi bergradien, tergambar di bagian dalam bentuk. */
    fun drawRim(c: Canvas, w: Float, h: Float) {
        ensure(w, h)
        val hw = s.rimWidth / 2f
        rect.set(hw, hw, w - hw, h - hw)
        val rr = max(s.radius - hw, 0f)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = s.rimWidth
        paint.color = Color.BLACK
        paint.shader = rimShader
        c.drawRoundRect(rect, rr, rr, paint)
        paint.shader = null
        paint.style = Paint.Style.FILL
    }
}
