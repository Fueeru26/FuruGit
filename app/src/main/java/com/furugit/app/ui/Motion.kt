package com.furugit.app.ui

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.animation.LinearInterpolator
import android.view.animation.PathInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.dynamicanimation.animation.DynamicAnimation
import androidx.dynamicanimation.animation.FloatValueHolder
import androidx.dynamicanimation.animation.SpringAnimation
import androidx.dynamicanimation.animation.SpringForce
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/*
 * Animasi FuruArc (Anim.kt + ScreenTransitions.kt) untuk View biasa:
 *  - efek tekan berpegas: mengecil cepat saat ditekan, memantul saat dilepas;
 *  - animasi masuk bertahap: kartu muncul satu per satu dari atas ke bawah setelah pindah layar;
 *  - jelly saat scroll: kartu sedikit memanjang mengikuti kecepatan scroll lalu memantul kembali;
 *  - transisi layar: tab bergeser ke samping, halaman baru naik dari bawah, kembali = turun ke bawah.
 */

/** Efek tekan berpegas (pressScale di FuruArc): redaman 0.58 / kekakuan 1275 saat ditekan, 0.36 / 241 saat dilepas. */
fun View.pressScale(pressed: Float = 0.96f) {
    var sx: SpringAnimation? = null
    var sy: SpringAnimation? = null
    fun go(down: Boolean, v: View) {
        val ax = sx ?: SpringAnimation(v, DynamicAnimation.SCALE_X).also { it.spring = SpringForce(1f); sx = it }
        val ay = sy ?: SpringAnimation(v, DynamicAnimation.SCALE_Y).also { it.spring = SpringForce(1f); sy = it }
        val d = if (down) 0.58f else 0.36f
        val k = if (down) 1275f else 241f
        ax.spring.setDampingRatio(d).setStiffness(k)
        ay.spring.setDampingRatio(d).setStiffness(k)
        val t = if (down) pressed else 1f
        ax.animateToFinalPosition(t)
        ay.animateToFinalPosition(t)
    }
    setOnTouchListener { v, e ->
        if (!v.isEnabled) return@setOnTouchListener false
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> go(true, v)
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> go(false, v)
        }
        false
    }
}

/** Nomor urut animasi masuk: hanya dalam 450 ms setelah pindah layar (EntranceClock di FuruArc). */
object EntranceClock {
    private var startedAt = 0L
    private var slot = 0

    fun restart() {
        startedAt = SystemClock.uptimeMillis()
        slot = 0
    }

    /** Nomor urut berikutnya, atau -1 bila di luar jendela waktu (kartu langsung tampil). */
    fun take(): Int = if (SystemClock.uptimeMillis() - startedAt < 450L) slot++ else -1
}

/**
 * Jelly saat scroll (ScrollJelly di FuruArc): makin cepat daftar digulir, makin panjang kartu ditarik
 * searah gulir; saat berhenti kartu memantul kembali dengan sedikit undershoot.
 */
object Jelly {
    private val cards = ArrayList<GlassCard>()
    private val handler = Handler(Looper.getMainLooper())
    private var live = 0f
    private var smoothed = 0f
    private var lastNanos = 0L
    private var releasing = false
    private var anim: SpringAnimation? = null

    private fun maxVelocity(): Float = 3200f * com.furugit.app.App.instance.resources.displayMetrics.density

    fun register(c: GlassCard) {
        if (!cards.contains(c)) cards.add(c)
        c.setJelly(live)
    }

    fun unregister(c: GlassCard) {
        cards.remove(c)
    }

    private fun push() {
        for (c in cards.toList()) c.setJelly(live)
    }

    private val release = Runnable {
        releasing = true
        smoothed = 0f
        val a = SpringAnimation(FloatValueHolder(live))
        a.spring = SpringForce(0f).setDampingRatio(0.36f).setStiffness(241f)
        a.setMinimumVisibleChange(0.001f)
        a.addUpdateListener { _, v, _ ->
            live = max(v, -0.6f)
            push()
        }
        a.addEndListener { _, canceled, _, _ ->
            if (!canceled) {
                releasing = false
                live = 0f
                push()
            }
        }
        anim = a
        a.start()
    }

    /** Dipanggil dari OnScrollChangeListener dengan selisih posisi gulir (px). */
    fun onScrolled(dy: Int) {
        if (dy == 0) return
        val now = System.nanoTime()
        var dt = (now - lastNanos) / 1_000_000_000f
        lastNanos = now
        val maxV = maxVelocity()
        if (releasing) {
            anim?.cancel()
            anim = null
            releasing = false
            smoothed = max(live, 0f) * maxV
        }
        if (dt > 0.12f) {
            smoothed = max(live, 0f) * maxV
            dt = 0.016f
        }
        val velocity = abs(dy) / max(dt, 0.004f)
        smoothed += (velocity - smoothed) * 0.3f
        live = (smoothed / maxV).coerceIn(0f, 1f)
        push()
        handler.removeCallbacks(release)
        handler.postDelayed(release, 48L)
    }
}

/** Transisi antar layar (ScreenTransitions di FuruArc), memakai tangkapan gambar halaman lama sebagai lapisan. */
object PageTransitions {
    const val TAB = 0
    const val PUSH = 1
    const val POP = 2

    private val fastOutSlowIn = PathInterpolator(0.4f, 0f, 0.2f, 1f)
    private val linearOutSlowIn = PathInterpolator(0f, 0f, 0.2f, 1f)
    private var current: ValueAnimator? = null

    /** Respons langkah pegas (redaman [zeta], kekakuan [k]) dari 0 ke 1: pegas halaman tab di FuruArc. */
    private fun springStep(t: Float, zeta: Float = 0.86f, k: Float = 319f): Float {
        if (t <= 0f) return 0f
        val w0 = sqrt(k)
        val wd = w0 * sqrt(1f - zeta * zeta)
        val a = zeta * w0
        return 1f - exp(-a * t) * (cos(wd * t) + (a / wd) * sin(wd * t))
    }

    /**
     * Menjalankan [swap] (mengganti isi halaman) lalu menganimasikannya.
     * [page] = kepala + isi halaman; [root] = induk tempat tangkapan gambar lama ditaruh; [dir] = 1 / -1 untuk TAB.
     */
    fun run(root: FrameLayout, page: View, kind: Int, dir: Int, swap: () -> Unit) {
        current?.cancel()
        val w = page.width
        val h = page.height
        if (w <= 0 || h <= 0) {
            swap()
            return
        }
        val bmp = try {
            Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        } catch (e: Throwable) {
            null
        }
        if (bmp == null) {
            swap()
            return
        }
        page.draw(Canvas(bmp))
        val snap = ImageView(root.context)
        snap.scaleType = ImageView.ScaleType.FIT_XY
        snap.setImageBitmap(bmp)
        val idx = root.indexOfChild(page)
        // kembali: halaman lama turun DI ATAS halaman tujuan; selain itu halaman baru berada di atas
        root.addView(snap, if (kind == POP) idx + 1 else idx, FrameLayout.LayoutParams(w, h))

        try {
            swap()
        } catch (e: Throwable) {
            root.removeView(snap)
            snap.setImageDrawable(null)
            bmp.recycle()
            throw e
        }

        page.setLayerType(View.LAYER_TYPE_HARDWARE, null)
        snap.setLayerType(View.LAYER_TYPE_HARDWARE, null)
        val duration = if (kind == TAB) 450L else 260L
        fun step(t: Float) {
            when (kind) {
                TAB -> {
                    val sp = springStep(t / 1000f)
                    page.translationX = dir * w * (1f - sp)
                    val sc = 0.94f + 0.06f * sp
                    page.scaleX = sc
                    page.scaleY = sc
                    page.alpha = linearOutSlowIn.getInterpolation(min(t / 280f, 1f))
                    snap.translationX = -dir * w * sp
                    val sc2 = 1f - 0.06f * sp
                    snap.scaleX = sc2
                    snap.scaleY = sc2
                    snap.alpha = 1f - fastOutSlowIn.getInterpolation(min(t / 180f, 1f))
                }
                PUSH -> {
                    val e = fastOutSlowIn.getInterpolation(min(t / 260f, 1f))
                    page.translationY = h * (1f - e)
                    snap.alpha = 1f - e
                    val sc = 1f - 0.06f * e
                    snap.scaleX = sc
                    snap.scaleY = sc
                }
                else -> {
                    snap.translationY = h * fastOutSlowIn.getInterpolation(min(t / 240f, 1f))
                    page.alpha = fastOutSlowIn.getInterpolation(min(t / 240f, 1f))
                    val sc = 0.94f + 0.06f * fastOutSlowIn.getInterpolation(min(t / 260f, 1f))
                    page.scaleX = sc
                    page.scaleY = sc
                }
            }
        }
        step(0f)
        val va = ValueAnimator.ofFloat(0f, 1f)
        va.duration = duration
        va.interpolator = LinearInterpolator()
        va.addUpdateListener { step(it.animatedFraction * duration) }
        va.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                page.translationX = 0f
                page.translationY = 0f
                page.scaleX = 1f
                page.scaleY = 1f
                page.alpha = 1f
                page.setLayerType(View.LAYER_TYPE_NONE, null)
                root.removeView(snap)
                snap.setImageDrawable(null)
                bmp.recycle()
                if (current === animation) current = null
            }
        })
        current = va
        va.start()
    }
}
