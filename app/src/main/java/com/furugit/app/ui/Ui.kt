package com.furugit.app.ui

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.text.Editable
import android.text.InputType
import android.text.TextUtils
import android.text.TextWatcher
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.res.ResourcesCompat
import androidx.core.widget.TextViewCompat
import com.furugit.app.R
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout

const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT

fun Context.dp(v: Int): Int = (v * resources.displayMetrics.density + 0.5f).toInt()

object Fonts {
    private val cache = HashMap<Int, Typeface>()

    private fun load(ctx: Context, id: Int): Typeface {
        cache[id]?.let { return it }
        val t = try {
            ResourcesCompat.getFont(ctx, id) ?: Typeface.DEFAULT
        } catch (e: Exception) {
            Typeface.DEFAULT
        }
        cache[id] = t
        return t
    }

    fun regular(ctx: Context) = load(ctx, R.font.qanelas_regular)
    fun semibold(ctx: Context) = load(ctx, R.font.qanelas_semibold)
    fun bold(ctx: Context) = load(ctx, R.font.qanelas_bold)
}

fun Context.color(id: Int): Int = Pal.resolve(this, id)

/** Tinggi baris teks dalam sp (lineHeight di Compose). */
fun TextView.lineH(sp: Float) {
    TextViewCompat.setLineHeight(this, TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, sp, resources.displayMetrics).toInt())
}

fun vbox(ctx: Context, pad: Int = 0): LinearLayout = LinearLayout(ctx).apply {
    orientation = LinearLayout.VERTICAL
    clipChildren = false
    clipToPadding = false
    setPadding(ctx.dp(pad), ctx.dp(pad), ctx.dp(pad), ctx.dp(pad))
}

/** Jarak ke elemen sebelumnya (seperti spacedBy di FuruArc); elemen pertama tidak diberi jarak. */
fun LinearLayout.gap(dp: Int): Int = if (childCount == 0) 0 else context.dp(dp)

/** Judul grup: teks aksen 18sp tebal di tengah dengan garis 2dp di kiri dan kanan (GroupTitle di FuruArc). */
fun LinearLayout.title(text: String): TextView {
    val pal = Pal.of(context)
    val row = LinearLayout(context)
    row.orientation = LinearLayout.HORIZONTAL
    row.gravity = Gravity.CENTER_VERTICAL
    row.setPadding(0, context.dp(8), 0, 0)
    fun line(): View = View(context).apply { setBackgroundColor(pal.accent) }
    row.addView(line(), LinearLayout.LayoutParams(0, context.dp(2), 1f))
    val t = TextView(context)
    t.text = tx(text)
    t.textSize = 18f
    t.lineH(24f)
    t.typeface = Fonts.bold(context)
    t.gravity = Gravity.CENTER
    t.setTextColor(pal.accent)
    t.setPadding(context.dp(14), 0, context.dp(14), 0)
    row.addView(t, LinearLayout.LayoutParams(WRAP, WRAP))
    row.addView(line(), LinearLayout.LayoutParams(0, context.dp(2), 1f))
    addView(row, LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = gap(16) })
    return t
}

/** Judul pilihan di dalam kartu (SectionLabel di FuruArc): 16sp semi-tebal. */
fun LinearLayout.sectionLabel(text: String): TextView {
    val t = TextView(context)
    t.text = tx(text)
    t.textSize = 16f
    t.typeface = Fonts.semibold(context)
    t.setTextColor(context.color(R.color.furu_text))
    addView(t, LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = gap(14) })
    return t
}

fun LinearLayout.label(
    text: CharSequence,
    size: Float = 14f,
    bold: Boolean = false,
    mono: Boolean = false,
    dim: Boolean = false,
    gap: Int = if (size >= 16f) 6 else 4
): TextView {
    val t = TextView(context)
    t.text = tx(text)
    t.textSize = size
    t.typeface = when {
        mono -> if (bold) Typeface.create(Typeface.MONOSPACE, Typeface.BOLD) else Typeface.MONOSPACE
        bold -> Fonts.bold(context)
        else -> Fonts.regular(context)
    }
    t.setTextColor(context.color(if (dim) R.color.furu_muted else R.color.furu_text))
    if (!mono && size < 18f) t.setLineSpacing(0f, 1.25f)
    addView(t, LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = this@label.gap(gap) })
    return t
}

fun LinearLayout.hint(text: String): TextView = label(text, 12f, dim = true)

fun LinearLayout.space(h: Int = 8) {
    if (childCount == 0) return
    addView(android.view.View(context), LinearLayout.LayoutParams(MATCH, context.dp(h)))
}

fun LinearLayout.hrow(block: LinearLayout.() -> Unit): LinearLayout {
    val r = LinearLayout(context)
    r.orientation = LinearLayout.HORIZONTAL
    r.clipChildren = false
    r.block()
    (r.getChildAt(r.childCount - 1)?.layoutParams as? LinearLayout.LayoutParams)?.marginEnd = 0
    addView(r, LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = gap(12) })
    return r
}

/**
 * Tombol utama (gradien) atau, jika [outlined], tombol sekunder (gradien lembut) seperti PrimaryButton / SecondaryButton
 * di FuruArc. Tinggi selalu 44dp dan teks satu baris (mengecil otomatis bila terlalu panjang).
 */
fun LinearLayout.button(text: String, outlined: Boolean = false, onClick: () -> Unit): FuruButton {
    val b = FuruButton(context, !outlined)
    b.text = tx(text)
    b.setOnClickListener { onClick() }
    val h = context.dp(44)
    val lp = if (orientation == LinearLayout.HORIZONTAL) {
        LinearLayout.LayoutParams(0, h, 1f).apply { marginEnd = context.dp(8) }
    } else {
        LinearLayout.LayoutParams(MATCH, h).apply { topMargin = gap(12) }
    }
    addView(b, lp)
    return b
}

fun LinearLayout.input(
    hint: String,
    value: String = "",
    multiline: Boolean = false,
    password: Boolean = false
): EditText {
    val p = Pal.of(context)
    val til = TextInputLayout(context, null, com.google.android.material.R.attr.textInputOutlinedStyle)
    til.hint = tx(hint)
    til.boxBackgroundMode = TextInputLayout.BOX_BACKGROUND_OUTLINE
    val r = context.dp(10).toFloat()
    til.setBoxCornerRadii(r, r, r, r)
    til.boxBackgroundColor = p.field
    til.setBoxStrokeColorStateList(
        ColorStateList(arrayOf(intArrayOf(android.R.attr.state_focused), intArrayOf()), intArrayOf(p.accent, p.fieldBorder))
    )
    til.boxStrokeWidth = context.dp(1)
    til.boxStrokeWidthFocused = context.dp(2)
    til.defaultHintTextColor = ColorStateList.valueOf(p.muted)
    til.hintTextColor = ColorStateList.valueOf(p.accent)
    til.typeface = Fonts.regular(context)
    val et = TextInputEditText(til.context)
    et.setText(value)
    et.typeface = Fonts.regular(context)
    et.textSize = 16f
    et.setTextColor(p.text)
    et.inputType = when {
        password -> InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        multiline -> InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
        else -> InputType.TYPE_CLASS_TEXT
    }
    if (multiline) et.minLines = 2
    til.addView(et)
    addView(til, LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = gap(12) })
    return et
}

/** Kartu kaca dengan isi kolom 16dp. [deco] = bunga kecil di sudut kanan bawah (kartu menu). */
private fun LinearLayout.glassCard(
    deco: Boolean,
    onClick: (() -> Unit)?,
    onLong: (() -> Unit)?,
    block: LinearLayout.() -> Unit
): LinearLayout {
    val c = GlassCard(context, deco)
    val inner = LinearLayout(context)
    inner.orientation = LinearLayout.VERTICAL
    inner.clipChildren = false
    inner.setPadding(context.dp(16), context.dp(16), context.dp(16), context.dp(16))
    inner.block()
    c.addView(inner, FrameLayout.LayoutParams(MATCH, WRAP))
    if (onClick != null) c.makeClickable(onClick)
    if (onLong != null) {
        c.isLongClickable = true
        c.setOnLongClickListener { onLong(); true }
    }
    addView(c, LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = gap(16) })
    return inner
}

/** Kartu kaca gaya Blossom FuruArc (AppCard): radius 10dp, isi bergradien, tepi terang, cahaya biru. */
fun LinearLayout.card(
    onClick: (() -> Unit)? = null,
    onLong: (() -> Unit)? = null,
    block: LinearLayout.() -> Unit
): LinearLayout = glassCard(false, onClick, onLong, block)

/**
 * Kartu menu (BlossomMenuRow): kotak ikon bergradien 48dp, garis tipis, judul 16sp + keterangan 14sp,
 * dengan bunga kecil di sudut. Mengembalikan kolom teks.
 */
fun LinearLayout.rowCard(
    iconRes: Int,
    title: String,
    sub: String? = null,
    onClick: (() -> Unit)? = null,
    onLong: (() -> Unit)? = null,
    badge: String? = null
): LinearLayout {
    var col: LinearLayout? = null
    glassCard(true, onClick, onLong) {
        val pal = Pal.of(context)
        val row = LinearLayout(context)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER_VERTICAL
        val box = FrameLayout(context)
        val bg = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(pal.gradA, pal.gradB))
        bg.cornerRadius = context.dpf(8f)
        box.background = bg
        val iv = ImageView(context)
        iv.setImageResource(iconRes)
        iv.imageTintList = ColorStateList.valueOf(pal.onGrad)
        box.addView(iv, FrameLayout.LayoutParams(context.dp(24), context.dp(24), Gravity.CENTER))
        row.addView(box, LinearLayout.LayoutParams(context.dp(48), context.dp(48)))
        val divider = View(context)
        divider.setBackgroundColor(pal.divider)
        row.addView(divider, LinearLayout.LayoutParams(context.dp(1), context.dp(44)).apply {
            marginStart = context.dp(10)
            marginEnd = context.dp(10)
        })
        val c = LinearLayout(context)
        c.orientation = LinearLayout.VERTICAL
        row.addView(c, LinearLayout.LayoutParams(0, WRAP, 1f))
        val t = TextView(context)
        t.text = tx(title)
        t.textSize = 16f
        t.lineH(24f)
        t.typeface = Fonts.semibold(context)
        t.setTextColor(pal.text)
        c.addView(t, LinearLayout.LayoutParams(MATCH, WRAP))
        if (sub != null) {
            val s = TextView(context)
            s.text = tx(sub)
            s.textSize = 14f
            s.lineH(20f)
            s.typeface = Fonts.regular(context)
            s.setTextColor(pal.muted)
            c.addView(s, LinearLayout.LayoutParams(MATCH, WRAP))
        }
        if (badge != null) {
            // penanda di ujung kanan kartu: titik besar + teks, hijau
            val b = TextView(context)
            val sp = android.text.SpannableString("● $badge")
            sp.setSpan(android.text.style.RelativeSizeSpan(1.6f), 0, 1, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            b.text = sp
            b.textSize = 14f
            b.typeface = Fonts.bold(context)
            b.setTextColor(0xFF2FBF71.toInt())
            b.setSingleLine(true)
            row.addView(b, LinearLayout.LayoutParams(WRAP, WRAP).apply { marginStart = context.dp(8) })
        }
        addView(row, LinearLayout.LayoutParams(MATCH, WRAP))
        col = c
    }
    return col!!
}

fun EditText.onChange(block: (String) -> Unit) {
    addTextChangedListener(object : TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        override fun afterTextChanged(s: Editable?) {
            block(s?.toString() ?: "")
        }
    })
}

object Dialogs {
    fun toast(ctx: Context, msg: String) {
        Toast.makeText(ctx, tx(msg), Toast.LENGTH_SHORT).show()
    }

    /** Pembuat dialog dengan jendela kaca (RowPanel / AppAlert di FuruArc). */
    fun builder(ctx: Context): MaterialAlertDialogBuilder {
        val b = MaterialAlertDialogBuilder(ctx)
        b.setBackground(GlassPanelDrawable(ctx))
        val m = ctx.dp(16)
        b.setBackgroundInsetStart(m)
        b.setBackgroundInsetEnd(m)
        b.setBackgroundInsetTop(m)
        b.setBackgroundInsetBottom(m)
        return b
    }

    fun info(ctx: Context, title: String, msg: String) {
        builder(ctx).setTitle(tx(title)).setMessage(tx(msg)).setPositiveButton("OK", null).show()
    }

    /**
     * Jendela hasil aksi: ceklis hijau (berhasil) atau silang merah (gagal) beranimasi dengan teks pemberitahuan di bawahnya.
     * Yang berhasil menutup sendiri setelah sekitar 2 detik; keduanya bisa diketuk untuk ditutup.
     */
    fun result(ctx: Context, ok: Boolean, message: String) {
        val box = LinearLayout(ctx)
        box.orientation = LinearLayout.VERTICAL
        box.gravity = Gravity.CENTER_HORIZONTAL
        box.setPadding(ctx.dp(24), ctx.dp(24), ctx.dp(24), ctx.dp(8))
        box.addView(ResultMark(ctx, ok), LinearLayout.LayoutParams(ctx.dp(88), ctx.dp(88)))
        val tv = TextView(ctx)
        tv.text = tx(message)
        tv.textSize = 16f
        tv.typeface = Fonts.semibold(ctx)
        tv.gravity = Gravity.CENTER
        tv.setTextColor(Pal.of(ctx).text)
        tv.setLineSpacing(0f, 1.2f)
        box.addView(tv, LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = ctx.dp(16) })
        val sv = android.widget.ScrollView(ctx)
        sv.addView(box)
        val b = builder(ctx).setView(sv)
        if (!ok) b.setPositiveButton("OK", null)
        val d = b.show()
        if (ok) {
            sv.setOnClickListener { d.dismiss() }
            box.setOnClickListener { d.dismiss() }
            box.postDelayed({ if (d.isShowing) d.dismiss() }, 2000L)
        }
    }

    fun confirm(ctx: Context, title: String, msg: String, okLabel: String = "Ya", onYes: () -> Unit) {
        builder(ctx).setTitle(tx(title)).setMessage(tx(msg))
            .setPositiveButton(tx(okLabel)) { _, _ -> onYes() }
            .setNegativeButton(tx("Batal"), null).show()
    }

    fun prompt(ctx: Context, title: String, hint: String, default: String = "", onOk: (String) -> Unit) {
        val box = vbox(ctx, 20)
        val et = box.input(hint, default)
        builder(ctx).setTitle(tx(title)).setView(box)
            .setPositiveButton("OK") { _, _ -> onOk(et.text.toString().trim()) }
            .setNegativeButton(tx("Batal"), null).show()
    }

    fun choose(ctx: Context, title: String, items: List<String>, onPick: (Int) -> Unit) {
        builder(ctx).setTitle(tx(title))
            .setItems(items.map { tx(it) }.toTypedArray()) { _, i -> onPick(i) }
            .setNegativeButton(tx("Batal"), null).show()
    }

    /** Dialog dengan isi bebas. [build] mengisi kotak; [onOk] dipanggil saat tombol utama ditekan. */
    fun form(ctx: Context, title: String, okLabel: String, build: (LinearLayout) -> Unit, onOk: () -> Unit) {
        val box = vbox(ctx, 20)
        build(box)
        val sv = android.widget.ScrollView(ctx)
        sv.addView(box)
        builder(ctx).setTitle(tx(title)).setView(sv)
            .setPositiveButton(tx(okLabel)) { _, _ -> onOk() }
            .setNegativeButton(tx("Batal"), null).show()
    }

    fun text(ctx: Context, title: String, body: CharSequence) {
        val tv = TextView(ctx)
        tv.text = body
        tv.textSize = 11f
        tv.typeface = Typeface.MONOSPACE
        tv.setHorizontallyScrolling(true)
        tv.setTextIsSelectable(true)
        tv.setPadding(ctx.dp(16), ctx.dp(8), ctx.dp(16), ctx.dp(8))
        val h = android.widget.HorizontalScrollView(ctx)
        h.addView(tv)
        val v = android.widget.ScrollView(ctx)
        v.addView(h)
        builder(ctx).setTitle(tx(title)).setView(v).setPositiveButton(tx("Tutup"), null).show()
    }
}

/**
 * Dropdown FuruArc (AppDropdown): kolom isi warna field, tepi 1dp, radius 10dp, teks 16sp + "▾", tinggi minimal 52dp;
 * menu muncul tepat di bawahnya selebar kolom, maksimal 5 pilihan terlihat (lebih dari itu bisa digulir).
 */
fun LinearLayout.dropdown(options: List<String>, selected: Int, onPick: (Int) -> Unit): View {
    val ctx = context
    val pal = Pal.of(ctx)
    val box = FrameLayout(ctx)
    val bg = GradientDrawable()
    bg.cornerRadius = ctx.dpf(10f)
    bg.setColor(pal.field)
    bg.setStroke(ctx.dp(1), pal.fieldBorder)
    val mask = GradientDrawable()
    mask.cornerRadius = ctx.dpf(10f)
    mask.setColor(Color.WHITE)
    box.background = RippleDrawable(ColorStateList.valueOf(pal.text.withAlpha(30)), bg, mask)
    box.isClickable = true
    val row = LinearLayout(ctx)
    row.orientation = LinearLayout.HORIZONTAL
    row.gravity = Gravity.CENTER_VERTICAL
    row.minimumHeight = ctx.dp(52)
    row.setPadding(ctx.dp(16), 0, ctx.dp(16), 0)
    val tv = TextView(ctx)
    tv.text = options.getOrElse(selected) { "-" }
    tv.textSize = 16f
    tv.typeface = Fonts.regular(ctx)
    tv.setTextColor(pal.text)
    tv.setSingleLine(true)
    tv.ellipsize = TextUtils.TruncateAt.END
    row.addView(tv, LinearLayout.LayoutParams(0, WRAP, 1f))
    val arrow = TextView(ctx)
    arrow.text = "▾"
    arrow.textSize = 16f
    arrow.setTextColor(pal.muted)
    row.addView(arrow, LinearLayout.LayoutParams(WRAP, WRAP))
    box.addView(row, FrameLayout.LayoutParams(MATCH, WRAP))
    box.pressScale(0.98f)
    box.setOnClickListener {
        val list = LinearLayout(ctx)
        list.orientation = LinearLayout.VERTICAL
        list.setPadding(0, ctx.dp(4), 0, ctx.dp(4))
        val pop = android.widget.PopupWindow(ctx)
        options.forEachIndexed { i, text ->
            val item = TextView(ctx)
            item.text = text
            item.textSize = 15f
            item.typeface = Fonts.regular(ctx)
            item.gravity = Gravity.CENTER_VERTICAL
            item.setSingleLine(true)
            item.ellipsize = TextUtils.TruncateAt.END
            item.setPadding(ctx.dp(16), 0, ctx.dp(16), 0)
            item.setTextColor(if (i == selected) pal.accent else pal.text)
            item.setOnClickListener {
                pop.dismiss()
                if (i != selected) onPick(i)
            }
            list.addView(item, LinearLayout.LayoutParams(MATCH, ctx.dp(48)))
        }
        val sv = android.widget.ScrollView(ctx)
        sv.addView(list)
        val pbg = GradientDrawable()
        pbg.cornerRadius = ctx.dpf(10f)
        pbg.setColor(pal.field)
        pbg.setStroke(ctx.dp(1), pal.fieldBorder)
        pop.contentView = sv
        pop.width = box.width
        pop.height = minOf(ctx.dp(48 * 5 + 8), ctx.dp(48 * options.size + 8))
        pop.setBackgroundDrawable(pbg)
        pop.isFocusable = true
        pop.isOutsideTouchable = true
        pop.elevation = ctx.dp(4).toFloat()
        pop.showAsDropDown(box, 0, ctx.dp(4))
    }
    addView(box, LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = gap(10) })
    return box
}
