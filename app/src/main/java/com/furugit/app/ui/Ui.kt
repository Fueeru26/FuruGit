package com.furugit.app.ui

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.util.TypedValue
import androidx.core.widget.TextViewCompat
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.ImageView
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import com.furugit.app.R
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
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

/** Efek menekan 0.97x seperti kartu & tombol di FuruArc. */
fun View.pressScale(scale: Float = 0.97f) {
    setOnTouchListener { v, e ->
        when (e.action) {
            MotionEvent.ACTION_DOWN -> v.animate().scaleX(scale).scaleY(scale).setDuration(80).start()
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> v.animate().scaleX(1f).scaleY(1f).setDuration(120).start()
        }
        false
    }
}

fun vbox(ctx: Context, pad: Int = 0): LinearLayout = LinearLayout(ctx).apply {
    orientation = LinearLayout.VERTICAL
    setPadding(ctx.dp(pad), ctx.dp(pad), ctx.dp(pad), ctx.dp(pad))
}

fun LinearLayout.title(text: String): TextView {
    val t = TextView(context)
    t.text = tx(text)
    t.textSize = 18f
    t.typeface = Fonts.bold(context)
    t.setTextColor(context.color(R.color.furu_text))
    t.setPadding(0, context.dp(20), 0, context.dp(4))
    addView(t, LinearLayout.LayoutParams(MATCH, WRAP))
    return t
}

fun LinearLayout.label(
    text: CharSequence,
    size: Float = 14f,
    bold: Boolean = false,
    mono: Boolean = false,
    dim: Boolean = false
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
    addView(t, LinearLayout.LayoutParams(MATCH, WRAP))
    return t
}

fun LinearLayout.hint(text: String): TextView = label(text, 12f, dim = true)

fun LinearLayout.space(h: Int = 8) {
    addView(android.view.View(context), LinearLayout.LayoutParams(MATCH, context.dp(h)))
}

fun LinearLayout.hrow(block: LinearLayout.() -> Unit): LinearLayout {
    val r = LinearLayout(context)
    r.orientation = LinearLayout.HORIZONTAL
    r.block()
    (r.getChildAt(r.childCount - 1)?.layoutParams as? LinearLayout.LayoutParams)?.marginEnd = 0
    addView(r, LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = context.dp(8) })
    return r
}

/**
 * Tombol utama (aksen) atau, jika [outlined], tombol sekunder seperti SecondaryButton di FuruArc.
 * Tinggi selalu tepat 44dp dan teks satu baris (mengecil otomatis bila terlalu panjang), jadi semua
 * tombol di satu baris sama persis posisi dan ukurannya.
 */
fun LinearLayout.button(text: String, outlined: Boolean = false, onClick: () -> Unit): MaterialButton {
    val b = MaterialButton(context)
    b.text = tx(text)
    b.isAllCaps = false
    b.typeface = Fonts.bold(context)
    b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
    b.setSingleLine(true)
    b.maxLines = 1
    b.ellipsize = TextUtils.TruncateAt.END
    b.gravity = Gravity.CENTER
    b.cornerRadius = context.dp(10)
    b.insetTop = 0
    b.insetBottom = 0
    b.minimumHeight = 0
    b.minHeight = 0
    b.setPadding(context.dp(8), 0, context.dp(8), 0)
    TextViewCompat.setAutoSizeTextTypeUniformWithConfiguration(b, 10, 16, 1, TypedValue.COMPLEX_UNIT_SP)
    b.backgroundTintList = ColorStateList.valueOf(context.color(if (outlined) R.color.furu_trailing_bg else R.color.furu_accent))
    b.setTextColor(context.color(if (outlined) R.color.furu_text else R.color.furu_on_accent))
    b.stateListAnimator = null
    b.setOnClickListener { onClick() }
    b.pressScale()
    val h = context.dp(44)
    val lp = if (orientation == LinearLayout.HORIZONTAL) {
        LinearLayout.LayoutParams(0, h, 1f).apply { marginEnd = context.dp(8) }
    } else {
        LinearLayout.LayoutParams(MATCH, h).apply { topMargin = context.dp(8) }
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
    val til = TextInputLayout(context, null, com.google.android.material.R.attr.textInputOutlinedStyle)
    til.hint = tx(hint)
    val r = context.dp(10).toFloat()
    til.setBoxCornerRadii(r, r, r, r)
    til.typeface = Fonts.regular(context)
    val et = TextInputEditText(til.context)
    et.setText(value)
    et.typeface = Fonts.regular(context)
    et.textSize = 16f
    et.setTextColor(context.color(R.color.furu_text))
    et.inputType = when {
        password -> InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        multiline -> InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
        else -> InputType.TYPE_CLASS_TEXT
    }
    if (multiline) et.minLines = 2
    til.addView(et)
    addView(til, LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = context.dp(8) })
    return et
}

/** Kartu: radius 10dp, isi warna card, garis tepi tipis (gaya Classic FuruArc). */
fun LinearLayout.card(
    onClick: (() -> Unit)? = null,
    onLong: (() -> Unit)? = null,
    block: LinearLayout.() -> Unit
): LinearLayout {
    val c = MaterialCardView(context)
    c.radius = context.dp(10).toFloat()
    c.cardElevation = 0f
    c.setCardBackgroundColor(context.color(R.color.furu_card))
    c.strokeWidth = context.dp(1)
    c.setStrokeColor(ColorStateList.valueOf(context.color(R.color.furu_card_border)))
    val inner = LinearLayout(context)
    inner.orientation = LinearLayout.VERTICAL
    inner.setPadding(context.dp(16), context.dp(14), context.dp(16), context.dp(14))
    inner.block()
    c.addView(inner, FrameLayout.LayoutParams(MATCH, WRAP))
    if (onClick != null) {
        c.isClickable = true
        c.setOnClickListener { onClick() }
        c.pressScale()
    }
    if (onLong != null) c.setOnLongClickListener { onLong(); true }
    addView(c, LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = context.dp(10) })
    return inner
}

/** Kartu dengan kotak ikon 40dp di kiri, judul + keterangan di kanan. Mengembalikan kolom teks. */
fun LinearLayout.rowCard(
    iconRes: Int,
    title: String,
    sub: String? = null,
    onClick: (() -> Unit)? = null,
    onLong: (() -> Unit)? = null
): LinearLayout {
    var col: LinearLayout? = null
    card(onClick, onLong) {
        val row = LinearLayout(context)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER_VERTICAL
        val box = FrameLayout(context)
        val bg = GradientDrawable()
        bg.cornerRadius = context.dp(8).toFloat()
        bg.setColor(context.color(R.color.furu_icon_bg))
        box.background = bg
        val iv = ImageView(context)
        iv.setImageResource(iconRes)
        iv.imageTintList = ColorStateList.valueOf(context.color(R.color.furu_accent))
        box.addView(iv, FrameLayout.LayoutParams(context.dp(24), context.dp(24), Gravity.CENTER))
        row.addView(box, LinearLayout.LayoutParams(context.dp(40), context.dp(40)))
        val c = LinearLayout(context)
        c.orientation = LinearLayout.VERTICAL
        row.addView(c, LinearLayout.LayoutParams(0, WRAP, 1f).apply { marginStart = context.dp(14) })
        c.label(title, 16f, bold = true)
        if (sub != null) c.label(sub, 12f, dim = true)
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

    fun info(ctx: Context, title: String, msg: String) {
        MaterialAlertDialogBuilder(ctx).setTitle(tx(title)).setMessage(tx(msg)).setPositiveButton("OK", null).show()
    }

    fun confirm(ctx: Context, title: String, msg: String, okLabel: String = "Ya", onYes: () -> Unit) {
        MaterialAlertDialogBuilder(ctx).setTitle(tx(title)).setMessage(tx(msg))
            .setPositiveButton(tx(okLabel)) { _, _ -> onYes() }
            .setNegativeButton(tx("Batal"), null).show()
    }

    fun prompt(ctx: Context, title: String, hint: String, default: String = "", onOk: (String) -> Unit) {
        val box = vbox(ctx, 20)
        val et = box.input(hint, default)
        MaterialAlertDialogBuilder(ctx).setTitle(tx(title)).setView(box)
            .setPositiveButton("OK") { _, _ -> onOk(et.text.toString().trim()) }
            .setNegativeButton(tx("Batal"), null).show()
    }

    fun choose(ctx: Context, title: String, items: List<String>, onPick: (Int) -> Unit) {
        MaterialAlertDialogBuilder(ctx).setTitle(tx(title))
            .setItems(items.map { tx(it) }.toTypedArray()) { _, i -> onPick(i) }
            .setNegativeButton(tx("Batal"), null).show()
    }

    /** Dialog dengan isi bebas. [build] mengisi kotak; [onOk] dipanggil saat tombol utama ditekan. */
    fun form(ctx: Context, title: String, okLabel: String, build: (LinearLayout) -> Unit, onOk: () -> Unit) {
        val box = vbox(ctx, 20)
        build(box)
        val sv = android.widget.ScrollView(ctx)
        sv.addView(box)
        MaterialAlertDialogBuilder(ctx).setTitle(tx(title)).setView(sv)
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
        MaterialAlertDialogBuilder(ctx).setTitle(tx(title)).setView(v).setPositiveButton(tx("Tutup"), null).show()
    }
}
