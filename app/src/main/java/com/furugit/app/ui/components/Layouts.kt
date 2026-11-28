package com.furugit.app.ui.components

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import com.furugit.app.R
import com.furugit.app.core.common.tx
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout

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
