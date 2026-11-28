package com.furugit.app.ui.components

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.LinearGradient
import android.graphics.Outline
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewOutlineProvider
import androidx.appcompat.widget.AppCompatTextView
import androidx.core.widget.TextViewCompat

/** Latar tombol: gradien diagonal (utama), gradien lembut (sekunder), atau datar (nonaktif); mode gelap diberi cahaya putih tipis. */
private class ButtonBg(
    private val pal: Palette,
    private val kind: Int,
    private val radius: Float,
    private val density: Float
) : Drawable() {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()
    private var lw = -1f
    private var lh = -1f
    private var shader: Shader? = null

    override fun draw(canvas: Canvas) {
        val b = bounds
        val w = b.width().toFloat()
        val h = b.height().toFloat()
        if (w <= 0f || h <= 0f) return
        canvas.save()
        canvas.translate(b.left.toFloat(), b.top.toFloat())
        if (pal.dark && kind != 2) {
            // cahaya tipis (darkGlow di FuruArc): cincin putih memudar, sedikit turun
            val steps = 10
            val stepPx = 5f * density / steps
            val dy = 2f * density
            paint.style = Paint.Style.FILL
            paint.shader = null
            for (i in steps downTo 1) {
                val grow = stepPx * i
                val f = 1f - (i - 1) / steps.toFloat()
                paint.color = Color.WHITE.withAlpha((0.12f * f * f / steps * 2.2f * 255f + 0.5f).toInt())
                rect.set(-grow, -grow + dy, w + grow, h + grow + dy)
                canvas.drawRoundRect(rect, radius + grow, radius + grow, paint)
            }
        }
        if (w != lw || h != lh) {
            lw = w
            lh = h
            shader = when (kind) {
                0 -> LinearGradient(0f, 0f, w, h, pal.gradA, pal.gradB, Shader.TileMode.CLAMP)
                1 -> LinearGradient(0f, 0f, w, h, pal.softA, pal.softB, Shader.TileMode.CLAMP)
                else -> null
            }
        }
        rect.set(0f, 0f, w, h)
        paint.style = Paint.Style.FILL
        paint.color = if (kind == 2) pal.disabledBg else Color.BLACK
        paint.shader = shader
        canvas.drawRoundRect(rect, radius, radius, paint)
        paint.shader = null
        canvas.restore()
    }

    override fun setAlpha(alpha: Int) {}
    override fun setColorFilter(colorFilter: ColorFilter?) {}

    @Suppress("OVERRIDE_DEPRECATION")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}

/**
 * Tombol FuruArc (PrimaryButton / SecondaryButton): tinggi 44dp, radius 10dp, gradien, teks 16sp tebal satu baris
 * yang mengecil otomatis (minimal 10sp), efek tekan berpegas 0.95x.
 */
class FuruButton(context: Context, private val primary: Boolean) : AppCompatTextView(context) {
    private val pal = Pal.of(context)
    private val radius = context.dpf(10f)
    private var ready = false
    private var dimmed = false

    init {
        isClickable = true
        isFocusable = true
        gravity = Gravity.CENTER
        typeface = Fonts.bold(context)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
        setSingleLine(true)
        maxLines = 1
        ellipsize = TextUtils.TruncateAt.END
        setPadding(context.dp(8), 0, context.dp(8), 0)
        TextViewCompat.setAutoSizeTextTypeUniformWithConfiguration(this, 10, 16, 1, TypedValue.COMPLEX_UNIT_SP)
        outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                outline.setRoundRect(0, 0, view.width, view.height, radius)
            }
        }
        ready = true
        applyState()
        pressScale(0.95f)
    }

    override fun setEnabled(enabled: Boolean) {
        super.setEnabled(enabled)
        if (ready) applyState()
    }

    /** Tampil seperti nonaktif, tetapi tetap bisa ditekan (mis. untuk menampilkan petunjuk). */
    fun setDimmed(d: Boolean) {
        dimmed = d
        if (ready) applyState()
    }

    private fun applyState() {
        val kind = if (!isEnabled || dimmed) 2 else if (primary) 0 else 1
        val fg = when (kind) {
            0 -> pal.onGrad
            1 -> pal.text
            else -> pal.disabledFg
        }
        val mask = GradientDrawable()
        mask.cornerRadius = radius
        mask.setColor(Color.WHITE)
        setTextColor(fg)
        background = RippleDrawable(
            ColorStateList.valueOf(fg.withAlpha(60)),
            ButtonBg(pal, kind, radius, resources.displayMetrics.density),
            mask
        )
        // mode terang: bayangan tipis 2dp; mode gelap: cahaya putih tipis (digambar oleh ButtonBg)
        elevation = if (!pal.dark && isEnabled && !dimmed) context.dpf(2f) else 0f
    }
}
