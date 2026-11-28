package com.furugit.app.ui.components

import android.content.Context
import android.text.TextUtils
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView

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
