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
    badge: Badge? = null,
    titleBadge: Badge? = null
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
        if (titleBadge != null) {
            titleBadge.textView.textSize = 16f
            titleBadge.textView.lineH(24f)
            c.addView(titleBadge.view, LinearLayout.LayoutParams(MATCH, WRAP))
        } else {
            val t = TextView(context)
            t.text = tx(title)
            t.textSize = 16f
            t.lineH(24f)
            t.typeface = Fonts.semibold(context)
            t.setTextColor(pal.text)
            c.addView(t, LinearLayout.LayoutParams(MATCH, WRAP))
        }
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
            // penanda di ujung kanan kartu
            row.addView(badge.view, LinearLayout.LayoutParams(WRAP, WRAP).apply { marginStart = context.dp(8) })
        }
        addView(row, LinearLayout.LayoutParams(MATCH, WRAP))
        col = c
    }
    return col!!
}

/** Satu kotak menu pada grid. [dot] diisi [tileGrid]: titik merah di pojok kanan atas ikon (disembunyikan sampai dibutuhkan). */
class MenuTile(val icon: Int, val label: String, val onClick: () -> Unit) {
    var dot: View? = null
}

/**
 * Grid kotak menu: [columns] kotak per baris, tiap kotak kartu kaca dengan kotak ikon bergradien di atas
 * dan label tanpa keterangan di bawahnya (maksimal 2 baris, rata atas, tinggi semua kotak sama).
 */
fun LinearLayout.tileGrid(tiles: List<MenuTile>, columns: Int = 3) {
    val ctx = context
    val pal = Pal.of(ctx)
    var row: LinearLayout? = null
    tiles.forEachIndexed { i, t ->
        if (i % columns == 0) {
            val r = LinearLayout(ctx)
            r.orientation = LinearLayout.HORIZONTAL
            r.clipChildren = false
            r.clipToPadding = false
            addView(r, LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = gap(18) })
            row = r
        }
        val card = GlassCard(ctx, true)
        val inner = LinearLayout(ctx)
        inner.orientation = LinearLayout.VERTICAL
        inner.gravity = Gravity.CENTER_HORIZONTAL
        inner.clipChildren = false
        inner.setPadding(ctx.dp(6), ctx.dp(16), ctx.dp(6), ctx.dp(14))

        val box = FrameLayout(ctx)
        val bg = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(pal.gradA, pal.gradB))
        bg.cornerRadius = ctx.dpf(12f)
        box.background = bg
        val iv = ImageView(ctx)
        iv.setImageResource(t.icon)
        iv.imageTintList = ColorStateList.valueOf(pal.onGrad)
        box.addView(iv, FrameLayout.LayoutParams(ctx.dp(28), ctx.dp(28), Gravity.CENTER))
        val dot = ctx.redDot(10)
        (dot.layoutParams as FrameLayout.LayoutParams).apply {
            gravity = Gravity.TOP or Gravity.END
            topMargin = ctx.dp(4)
            marginEnd = ctx.dp(4)
        }
        dot.visibility = View.GONE
        box.addView(dot)
        t.dot = dot
        inner.addView(box, LinearLayout.LayoutParams(ctx.dp(52), ctx.dp(52)))

        val label = TextView(ctx)
        label.text = tx(t.label)
        label.textSize = 13f
        label.typeface = Fonts.semibold(ctx)
        label.setTextColor(pal.text)
        label.gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
        label.setLineSpacing(0f, 1.1f)
        label.minLines = 2
        label.maxLines = 2
        label.ellipsize = TextUtils.TruncateAt.END
        inner.addView(label, LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = ctx.dp(12) })

        card.addView(inner, FrameLayout.LayoutParams(MATCH, MATCH))
        card.makeClickable(t.onClick)
        row!!.addView(card, LinearLayout.LayoutParams(0, MATCH, 1f).apply {
            if (i % columns != 0) marginStart = ctx.dp(14)
        })
    }
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

/** Tombol di dasar dialog. [keepOpen] = dialog tetap terbuka setelah ditekan. */
class DBtn(
    val label: String,
    val primary: Boolean = false,
    val keepOpen: Boolean = false,
    val dimmed: Boolean = false,
    val onClick: (androidx.appcompat.app.AlertDialog) -> Unit = {}
)

/**
 * Penanda status berawalan titik besar ("● Aktif"). Warna teks menunjukkan keadaan:
 * memuat = putih (mode gelap) / hitam (mode terang), terhubung = hijau, gagal = merah.
 */
class Badge(private val ctx: Context, private var base: String, size: Float = 14f, semibold: Boolean = false, state: Int = LOADING) {
    /** Akar: titik + teks dalam satu baris. */
    val view = LinearLayout(ctx)
    val textView = TextView(ctx)
    private val dot = DotView(ctx)

    init {
        view.orientation = LinearLayout.HORIZONTAL
        view.gravity = Gravity.CENTER_VERTICAL
        view.clipChildren = false
        view.addView(dot, LinearLayout.LayoutParams(ctx.dp(13), ctx.dp(13)).apply { marginEnd = ctx.dp(6) })
        textView.textSize = size
        textView.typeface = if (semibold) Fonts.semibold(ctx) else Fonts.bold(ctx)
        textView.setSingleLine(true)
        textView.ellipsize = TextUtils.TruncateAt.END
        view.addView(textView, LinearLayout.LayoutParams(WRAP, WRAP))
        set(state)
    }

    /** Mengubah keadaan; [text] opsional untuk mengganti tulisan (mis. "Proses" jadi "Aktif" / "Gagal"). */
    fun set(state: Int, text: String = base) {
        base = text
        val pal = Pal.of(ctx)
        val col = when (state) {
            OK -> 0xFF2FBF71.toInt()
            FAIL -> 0xFFE5484D.toInt()
            WARN, RUN -> 0xFFF2C230.toInt()
            MUTED -> pal.muted
            else -> pal.text
        }
        textView.text = text.replaceFirstChar { it.uppercaseChar() }
        textView.setTextColor(col)
        dot.dotColor = col
        dot.spinning = state == RUN
    }

    companion object {
        const val LOADING = 0
        const val OK = 1
        const val FAIL = 2
        const val WARN = 3
        const val MUTED = 4
        /** Kuning dengan titik berputar: proses sedang berjalan. */
        const val RUN = 5
    }
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

    /**
     * Dialog kaca dengan tombol disusun dalam SATU baris (kolom sama lebar), bukan bertumpuk per baris.
     * Dialog dengan satu tombol memakai tombol selebar 160dp di bawah tengah.
     */
    fun show(
        ctx: Context,
        title: String?,
        message: CharSequence?,
        view: View?,
        buttons: List<DBtn>,
        cancelable: Boolean = true
    ): androidx.appcompat.app.AlertDialog {
        val pal = Pal.of(ctx)
        val root = LinearLayout(ctx)
        root.orientation = LinearLayout.VERTICAL
        root.setPadding(ctx.dp(24), ctx.dp(24), ctx.dp(24), ctx.dp(20))
        if (title != null) {
            val t = TextView(ctx)
            t.text = tx(title)
            t.textSize = 20f
            t.typeface = Fonts.bold(ctx)
            t.setTextColor(pal.text)
            root.addView(t, LinearLayout.LayoutParams(MATCH, WRAP))
        }
        val body = LinearLayout(ctx)
        body.orientation = LinearLayout.VERTICAL
        if (message != null) {
            val m = TextView(ctx)
            m.text = message
            m.textSize = 15f
            m.typeface = Fonts.regular(ctx)
            m.setTextColor(pal.muted)
            m.setLineSpacing(0f, 1.2f)
            body.addView(m, LinearLayout.LayoutParams(MATCH, WRAP))
        }
        if (view != null) body.addView(view, LinearLayout.LayoutParams(MATCH, WRAP))
        val sv = android.widget.ScrollView(ctx)
        sv.addView(body)
        root.addView(sv, LinearLayout.LayoutParams(MATCH, 0, 1f).apply { topMargin = ctx.dp(if (title != null) 12 else 0) })
        val row = LinearLayout(ctx)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER_HORIZONTAL
        row.clipChildren = false
        val b = builder(ctx).setView(root).setCancelable(cancelable)
        val dlg = b.create()
        buttons.forEachIndexed { i, x ->
            val btn = FuruButton(ctx, x.primary)
            btn.text = tx(x.label)
            if (x.dimmed) btn.setDimmed(true)
            btn.setOnClickListener {
                x.onClick(dlg)
                if (!x.keepOpen) dlg.dismiss()
            }
            val lp = if (buttons.size == 1) LinearLayout.LayoutParams(ctx.dp(160), ctx.dp(44))
            else LinearLayout.LayoutParams(0, ctx.dp(44), 1f).apply { if (i > 0) marginStart = ctx.dp(8) }
            row.addView(btn, lp)
        }
        if (buttons.isNotEmpty()) root.addView(row, LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = ctx.dp(20) })
        dlg.show()
        return dlg
    }

    fun info(ctx: Context, title: String, msg: String) {
        show(ctx, title, tx(msg), null, listOf(DBtn(tr("Tutup", "Close"), true)))
    }

    /**
     * Jendela hasil aksi: ceklis hijau (berhasil) atau silang merah (gagal) beranimasi pelan dengan teks
     * pemberitahuan di bawahnya. Ditutup manual lewat tombol "Tutup" di bawah tengah.
     */
    fun result(ctx: Context, ok: Boolean, message: String, extra: DBtn? = null) {
        val box = LinearLayout(ctx)
        box.orientation = LinearLayout.VERTICAL
        box.gravity = Gravity.CENTER_HORIZONTAL
        box.addView(ResultMark(ctx, ok), LinearLayout.LayoutParams(ctx.dp(88), ctx.dp(88)))
        val tv = TextView(ctx)
        tv.text = tx(message)
        tv.textSize = 16f
        tv.typeface = Fonts.semibold(ctx)
        tv.gravity = Gravity.CENTER
        tv.setTextColor(Pal.of(ctx).text)
        tv.setLineSpacing(0f, 1.2f)
        box.addView(tv, LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = ctx.dp(16) })
        val btns = if (extra == null) listOf(DBtn(tr("Tutup", "Close"), true)) else listOf(DBtn(tr("Tutup", "Close")), extra)
        show(ctx, null, null, box, btns)
    }

    fun confirm(ctx: Context, title: String, msg: String, okLabel: String = "Ya", onYes: () -> Unit) {
        show(ctx, title, tx(msg), null, listOf(DBtn(tr("Batal", "Cancel")), DBtn(okLabel, true) { onYes() }))
    }

    fun prompt(ctx: Context, title: String, hint: String, default: String = "", onOk: (String) -> Unit) {
        val box = vbox(ctx, 0)
        val et = box.input(hint, default)
        show(ctx, title, null, box, listOf(DBtn(tr("Batal", "Cancel")), DBtn("OK", true) { onOk(et.text.toString().trim()) }))
    }

    /** Daftar pilihan: ketuk salah satu baris untuk memilih. */
    fun choose(ctx: Context, title: String, items: List<String>, onPick: (Int) -> Unit) {
        chooseWith(ctx, title, items, emptyList(), onPick)
    }

    /** Seperti [choose], dengan tombol tambahan di dasar dialog (mis. "Pilih folder ini"). */
    fun chooseWith(ctx: Context, title: String, items: List<String>, buttons: List<DBtn>, onPick: (Int) -> Unit) {
        val pal = Pal.of(ctx)
        val list = vbox(ctx, 0)
        var dlg: androidx.appcompat.app.AlertDialog? = null
        items.forEachIndexed { i, text ->
            val tv = TextView(ctx)
            tv.text = text
            tv.textSize = 16f
            tv.typeface = Fonts.regular(ctx)
            tv.setTextColor(pal.text)
            tv.gravity = Gravity.CENTER_VERTICAL
            tv.minimumHeight = ctx.dp(48)
            tv.isClickable = true
            val mask = GradientDrawable()
            mask.cornerRadius = ctx.dpf(8f)
            mask.setColor(Color.WHITE)
            tv.background = RippleDrawable(ColorStateList.valueOf(pal.text.withAlpha(30)), null, mask)
            tv.setPadding(ctx.dp(8), ctx.dp(6), ctx.dp(8), ctx.dp(6))
            tv.setOnClickListener {
                dlg?.dismiss()
                onPick(i)
            }
            list.addView(tv, LinearLayout.LayoutParams(MATCH, WRAP))
        }
        val bs = buttons.ifEmpty { listOf(DBtn(tr("Batal", "Cancel"))) }
        dlg = show(ctx, title, null, list, bs)
    }

    /** Dialog dengan isi bebas. [build] mengisi kotak; [onOk] dipanggil saat tombol utama ditekan. */
    fun form(ctx: Context, title: String, okLabel: String, build: (LinearLayout) -> Unit, onOk: () -> Unit) {
        val box = vbox(ctx, 0)
        build(box)
        show(ctx, title, null, box, listOf(DBtn(tr("Batal", "Cancel")), DBtn(okLabel, true) { onOk() }))
    }

    fun text(ctx: Context, title: String, body: CharSequence) {
        val tv = TextView(ctx)
        tv.text = body
        tv.textSize = 11f
        tv.typeface = Typeface.MONOSPACE
        tv.setHorizontallyScrolling(true)
        tv.setTextIsSelectable(true)
        tv.setTextColor(Pal.of(ctx).text)
        val h = android.widget.HorizontalScrollView(ctx)
        h.addView(tv)
        show(ctx, title, null, h, listOf(DBtn(tr("Tutup", "Close"), true)))
    }
}

/**
 * Navigasi halaman FuruArc (PageNav): tombol ‹ , teks "Halaman x / y" di tengah (ketuk untuk "Pergi ke halaman"), tombol ›.
 * [page] dimulai dari 0.
 */
fun LinearLayout.pageNav(page: Int, pages: Int, onPage: (Int) -> Unit) {
    val ctx = context
    val pal = Pal.of(ctx)
    val row = LinearLayout(ctx)
    row.orientation = LinearLayout.HORIZONTAL
    row.gravity = Gravity.CENTER_VERTICAL
    row.clipChildren = false
    val prev = row.button("‹", true) { onPage(page - 1) }
    prev.isEnabled = page > 0
    val label = TextView(ctx)
    label.text = tr("Halaman", "Page") + " " + (page + 1) + " / " + pages
    label.textSize = 13f
    label.typeface = Fonts.semibold(ctx)
    label.setTextColor(pal.text)
    label.gravity = Gravity.CENTER
    label.setSingleLine(true)
    label.setPadding(0, ctx.dp(10), 0, ctx.dp(10))
    label.isClickable = true
    val mask = GradientDrawable()
    mask.cornerRadius = ctx.dpf(8f)
    mask.setColor(Color.WHITE)
    label.background = RippleDrawable(ColorStateList.valueOf(pal.text.withAlpha(30)), null, mask)
    label.setOnClickListener {
        val box = vbox(ctx, 0)
        val et = box.input("1 – $pages", (page + 1).toString())
        et.inputType = InputType.TYPE_CLASS_NUMBER
        et.setSelection(et.text?.length ?: 0)
        Dialogs.show(
            ctx, tr("Pergi ke halaman", "Go to page"), null, box,
            listOf(
                DBtn(tr("Batal", "Cancel")),
                DBtn(tr("Pergi", "Go"), true, keepOpen = true) { dlg ->
                    val n = et.text.toString().toIntOrNull()
                    if (n != null) {
                        dlg.dismiss()
                        onPage((n - 1).coerceIn(0, pages - 1))
                    }
                }
            )
        )
    }
    row.addView(label, LinearLayout.LayoutParams(0, WRAP, 2f).apply { marginEnd = ctx.dp(8) })
    val next = row.button("›", true) { onPage(page + 1) }
    next.isEnabled = page < pages - 1
    (next.layoutParams as LinearLayout.LayoutParams).marginEnd = 0
    addView(row, LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = gap(12) })
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
