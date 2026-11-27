package com.furugit.app.ui

import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Outline
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.text.TextUtils
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewOutlineProvider
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.widget.AppCompatImageView
import androidx.appcompat.widget.AppCompatTextView
import androidx.core.content.ContextCompat
import androidx.core.widget.TextViewCompat
import androidx.dynamicanimation.animation.FloatPropertyCompat
import androidx.dynamicanimation.animation.SpringAnimation
import androidx.dynamicanimation.animation.SpringForce
import com.furugit.app.R
import kotlin.math.max
import kotlin.math.min

/** dp dalam bentuk Float (untuk radius, tebal garis, dll.). */
fun Context.dpf(v: Float): Float = v * resources.displayMetrics.density

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

/** Gambar latar yang diskalakan memenuhi layar (Crop) dan diratakan ke atas-tengah (ContentScale.Crop + TopCenter). */
class TopCropImageView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) :
    AppCompatImageView(context, attrs) {

    init {
        scaleType = ScaleType.MATRIX
    }

    override fun setFrame(l: Int, t: Int, r: Int, b: Int): Boolean {
        val changed = super.setFrame(l, t, r, b)
        val d = drawable
        val vw = (r - l) - paddingLeft - paddingRight
        val vh = (b - t) - paddingTop - paddingBottom
        if (d != null && d.intrinsicWidth > 0 && d.intrinsicHeight > 0 && vw > 0 && vh > 0) {
            val dw = d.intrinsicWidth.toFloat()
            val dh = d.intrinsicHeight.toFloat()
            val s = max(vw / dw, vh / dh)
            val m = Matrix()
            m.setScale(s, s)
            m.postTranslate((vw - dw * s) / 2f, 0f)
            imageMatrix = m
        }
        return changed
    }
}

/**
 * Bilah navigasi kaca (GlassBar di FuruArc): kartu kaca setinggi 76dp melayang dengan jarak 12dp di sisi, pil di belakang
 * ikon tab yang dipilih (64 x 34dp, warnanya beranimasi 300 ms), label 12sp, efek tekan 0.9x.
 */
class GlassNav(
    context: Context,
    private val items: List<Item>,
    private val onSelect: (Int) -> Unit
) : FrameLayout(context) {

    class Item(val icon: Int, val label: String)

    private val pal = Pal.of(context)
    private val pills = ArrayList<GradientDrawable>()
    private val pillColors = ArrayList<Int>()
    private val pillAnims = HashMap<Int, ValueAnimator>()
    private val icons = ArrayList<ImageView>()
    private val labels = ArrayList<TextView>()
    private var selected = -1

    init {
        clipChildren = false
        clipToPadding = false
        setPadding(context.dp(12), context.dp(6), context.dp(12), context.dp(10))
        val bar = GlassCard(context, nav = true)
        val row = LinearLayout(context)
        row.orientation = LinearLayout.HORIZONTAL
        row.clipChildren = false
        row.setPadding(context.dp(8), 0, context.dp(8), 0)
        items.forEachIndexed { i, item ->
            val col = LinearLayout(context)
            col.orientation = LinearLayout.VERTICAL
            col.gravity = Gravity.CENTER
            col.clipChildren = false
            val pill = FrameLayout(context)
            val bg = GradientDrawable()
            bg.cornerRadius = context.dpf(10f)
            val clear = pal.navPill.withAlpha(0)
            bg.setColor(clear)
            pill.background = bg
            pills.add(bg)
            pillColors.add(clear)
            val iv = ImageView(context)
            iv.setImageResource(item.icon)
            icons.add(iv)
            pill.addView(iv, LayoutParams(context.dp(24), context.dp(24), Gravity.CENTER))
            col.addView(pill, LinearLayout.LayoutParams(context.dp(64), context.dp(34)))
            val tv = TextView(context)
            tv.text = item.label
            tv.textSize = 12f
            tv.gravity = Gravity.CENTER
            tv.maxLines = 1
            tv.ellipsize = TextUtils.TruncateAt.END
            tv.typeface = Fonts.regular(context)
            tv.setTextColor(pal.navUnsel)
            iv.imageTintList = ColorStateList.valueOf(pal.navUnsel)
            labels.add(tv)
            col.addView(tv, LinearLayout.LayoutParams(WRAP, WRAP).apply { topMargin = context.dp(3) })
            col.isClickable = true
            col.setOnClickListener { onSelect(i) }
            col.pressScale(0.9f)
            row.addView(col, LinearLayout.LayoutParams(0, MATCH, 1f))
        }
        bar.addView(row, LayoutParams(MATCH, MATCH))
        addView(bar, LayoutParams(MATCH, context.dp(76)))
    }

    private fun setPill(i: Int, target: Int, animate: Boolean) {
        pillAnims.remove(i)?.cancel()
        val bg = pills[i]
        if (!animate) {
            pillColors[i] = target
            bg.setColor(target)
            return
        }
        val a = ValueAnimator.ofObject(ArgbEvaluator(), pillColors[i], target)
        a.duration = 300L
        a.addUpdateListener {
            val c = it.animatedValue as Int
            pillColors[i] = c
            bg.setColor(c)
        }
        pillAnims[i] = a
        a.start()
    }

    /** Memilih tab [index]; [animate] = false untuk keadaan awal. */
    fun select(index: Int, animate: Boolean) {
        if (index == selected) return
        val old = selected
        selected = index
        for (i in items.indices) {
            val sel = i == index
            val tint = if (sel) pal.navSel else pal.navUnsel
            icons[i].imageTintList = ColorStateList.valueOf(tint)
            labels[i].setTextColor(tint)
            labels[i].typeface = if (sel) Fonts.bold(context) else Fonts.regular(context)
            if (i == index || i == old) {
                setPill(i, if (sel) pal.navPill else pal.navPill.withAlpha(0), animate && old >= 0)
            }
        }
    }
}

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
