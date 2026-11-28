package com.furugit.app.ui.components

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.view.MotionEvent
import android.widget.FrameLayout
import androidx.core.content.ContextCompat
import androidx.dynamicanimation.animation.FloatPropertyCompat
import androidx.dynamicanimation.animation.SpringAnimation
import androidx.dynamicanimation.animation.SpringForce
import com.furugit.app.R
import com.furugit.app.core.common.L
import kotlin.math.min

/**
 * Kartu kaca gaya Blossom FuruArc (BlossomCard): isi bergradien tidak tembus, tepi terang 1,5dp, cahaya biru 4dp.
 * [deco] menambahkan bunga kecil di sudut kanan bawah. Animasi bawaan kartu FuruArc:
 * masuk bertahap, jelly saat scroll, dan efek tekan berpegas (0.92x) bila bisa diketuk.
 * Cahaya digambar di luar batas kartu, jadi induknya harus memakai clipChildren = false.
 */
class GlassCard(
    context: Context,
    private val deco: Boolean = false,
    private val nav: Boolean = false
) : FrameLayout(context) {

    private val pal = Pal.of(context)
    private val painter = GlassPainter(if (nav) GlassStyle.nav(context) else GlassStyle.card(context))
    private val decoDrawable: Drawable? = if (deco) {
        ContextCompat.getDrawable(context, if (pal.dark) R.drawable.blossom_deco_dark else R.drawable.blossom_deco_light)?.mutate()
    } else null

    private var press = 1f
    private var ent = 1f
    private var jelly = 0f
    private var pressAnim: SpringAnimation? = null
    private var entAnim: SpringAnimation? = null
    private var entranceChecked = false
    private val startEntrance = Runnable {
        val a = SpringAnimation(this, ENT)
        a.spring = SpringForce(1f).setDampingRatio(0.75f).setStiffness(300f)
        a.setMinimumVisibleChange(0.002f)
        a.setStartValue(0f)
        entAnim = a
        a.start()
    }

    init {
        setWillNotDraw(false)
        clipChildren = false
    }

    /** Menjadikan kartu bisa diketuk: riak, dan efek tekan berpegas 0.92x. */
    fun makeClickable(onClick: () -> Unit) {
        isClickable = true
        setOnClickListener { onClick() }
        val mask = GradientDrawable()
        mask.cornerRadius = context.dpf(10f)
        mask.setColor(Color.WHITE)
        foreground = RippleDrawable(ColorStateList.valueOf(pal.text.withAlpha(30)), null, mask)
        setOnTouchListener { _, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> pressTo(true)
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> pressTo(false)
            }
            false
        }
    }

    private fun pressTo(down: Boolean) {
        val a = pressAnim ?: SpringAnimation(this, PRESS).also {
            it.spring = SpringForce(1f)
            it.setMinimumVisibleChange(0.002f)
            pressAnim = it
        }
        if (down) a.spring.setDampingRatio(0.58f).setStiffness(1275f)
        else a.spring.setDampingRatio(0.36f).setStiffness(241f)
        a.animateToFinalPosition(if (down) 0.92f else 1f)
    }

    /** Memutar ulang animasi masuk bertahap (dipakai saat tab yang sudah termuat ditampilkan kembali). */
    fun replayEntrance() {
        if (nav) return
        val slot = EntranceClock.take()
        if (slot < 0) return
        removeCallbacks(startEntrance)
        entAnim?.cancel()
        ent = 0f
        applyTransform()
        postDelayed(startEntrance, min(slot * 45L, 360L))
    }

    /** Dipanggil [Jelly] saat daftar digulir. */
    fun setJelly(amount: Float) {
        if (amount == jelly) return
        jelly = amount
        applyTransform()
    }

    private fun applyTransform() {
        if (nav) return
        val e = 0.92f + 0.08f * ent
        scaleX = press * e * (1f - 0.0175f * jelly)
        scaleY = press * e * (1f + 0.035f * jelly)
        alpha = (ent * 1.6f).coerceIn(0f, 1f)
        translationY = (1f - ent) * context.dpf(28f)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (nav) return
        Jelly.register(this)
        if (!entranceChecked) {
            entranceChecked = true
            val slot = EntranceClock.take()
            if (slot >= 0) {
                ent = 0f
                applyTransform()
                postDelayed(startEntrance, min(slot * 45L, 360L))
            }
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        if (nav) return
        Jelly.unregister(this)
        removeCallbacks(startEntrance)
        entAnim?.cancel()
        pressAnim?.cancel()
        press = 1f
        ent = 1f
        applyTransform()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        painter.drawBack(canvas, w, h)
        val d = decoDrawable ?: return
        canvas.save()
        painter.clipShape(canvas, w, h)
        // kotak 190 x 110 dp di kanan bawah; gambar dipaskan (ContentScale.Fit) di dalamnya
        val bw = context.dpf(190f)
        val bh = context.dpf(110f)
        val iw = d.intrinsicWidth.toFloat()
        val ih = d.intrinsicHeight.toFloat()
        if (iw > 0f && ih > 0f) {
            val sc = min(bw / iw, bh / ih)
            val dw = iw * sc
            val dh = ih * sc
            val left = w - bw + (bw - dw) / 2f
            val top = h - bh + (bh - dh) / 2f
            d.setBounds(left.toInt(), top.toInt(), (left + dw).toInt(), (top + dh).toInt())
            d.alpha = if (pal.dark) 217 else 242
            d.draw(canvas)
        }
        canvas.restore()
    }

    override fun dispatchDraw(canvas: Canvas) {
        super.dispatchDraw(canvas)
        painter.drawRim(canvas, width.toFloat(), height.toFloat())
    }

    companion object {
        private val PRESS = object : FloatPropertyCompat<GlassCard>("press") {
            override fun getValue(o: GlassCard): Float = o.press
            override fun setValue(o: GlassCard, value: Float) {
                o.press = value
                o.applyTransform()
            }
        }
        private val ENT = object : FloatPropertyCompat<GlassCard>("entrance") {
            override fun getValue(o: GlassCard): Float = o.ent
            override fun setValue(o: GlassCard, value: Float) {
                o.ent = value
                o.applyTransform()
            }
        }
    }
}

/** Latar panel kaca untuk jendela dialog (RowPanel di FuruArc): isi, tepi, dan cahaya yang sama dengan kartu. */
class GlassPanelDrawable(ctx: Context) : Drawable() {
    private val painter = GlassPainter(GlassStyle.card(ctx))

    override fun draw(canvas: Canvas) {
        val b = bounds
        canvas.save()
        canvas.translate(b.left.toFloat(), b.top.toFloat())
        painter.drawBack(canvas, b.width().toFloat(), b.height().toFloat())
        painter.drawRim(canvas, b.width().toFloat(), b.height().toFloat())
        canvas.restore()
    }

    override fun setAlpha(alpha: Int) {}
    override fun setColorFilter(colorFilter: ColorFilter?) {}

    @Suppress("OVERRIDE_DEPRECATION")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}
