package com.furugit.app.ui

import android.content.Context
import android.content.res.Configuration
import com.furugit.app.R
import kotlin.math.cbrt
import kotlin.math.pow

/** Warna aksen, sama dengan FuruArc. */
enum class Accent(val id: String, val nameId: String, val nameEn: String, val light: Int, val dark: Int) {
    BLUE("blue", "Biru", "Blue", 0xFF307DEF.toInt(), 0xFF307DEF.toInt()),
    LIGHT_BLUE("lightblue", "Biru Muda", "Light Blue", 0xFF0277BD.toInt(), 0xFF81D4FA.toInt()),
    GREEN("green", "Hijau", "Green", 0xFF1B7F3B.toInt(), 0xFF6BDB8F.toInt()),
    TEAL("teal", "Teal", "Teal", 0xFF00796B.toInt(), 0xFF5EDBC8.toInt()),
    PURPLE("purple", "Ungu", "Purple", 0xFF6750A4.toInt(), 0xFFD0BCFF.toInt()),
    PINK("pink", "Merah Muda", "Pink", 0xFFB0285E.toInt(), 0xFFFFB0CC.toInt()),
    ORANGE("orange", "Oranye", "Orange", 0xFFB75C00.toInt(), 0xFFFFB86B.toInt()),
    RED("red", "Merah", "Red", 0xFFB3261E.toInt(), 0xFFFF9A94.toInt()),
    YELLOW("yellow", "Kuning", "Yellow", 0xFF8A6F00.toInt(), 0xFFF2D95B.toInt());

    companion object {
        fun of(id: String) = values().firstOrNull { it.id == id } ?: BLUE
    }
}

/** Palet gaya Classic FuruArc: turunan aksen lewat interpolasi Oklab (sama dengan lerp Compose). */
class Palette(
    val dark: Boolean, val background: Int, val card: Int, val iconBg: Int, val trailingBg: Int,
    val text: Int, val muted: Int, val accent: Int, val onAccent: Int,
    val disabledBg: Int, val disabledFg: Int, val outline: Int, val cardBorder: Int, val header: Int
)

object Pal {
    private val cache = HashMap<String, Palette>()

    private fun lin(c: Double) = if (c <= 0.04045) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
    private fun gam(c: Double) = if (c <= 0.0031308) c * 12.92 else 1.055 * c.pow(1 / 2.4) - 0.055

    private fun toLab(c: Int): DoubleArray {
        val r = lin(((c shr 16) and 255) / 255.0)
        val g = lin(((c shr 8) and 255) / 255.0)
        val b = lin((c and 255) / 255.0)
        val l = cbrt(0.4122214708 * r + 0.5363325363 * g + 0.0514459929 * b)
        val m = cbrt(0.2119034982 * r + 0.6806995451 * g + 0.1073969566 * b)
        val s = cbrt(0.0883024619 * r + 0.2817188376 * g + 0.6299787005 * b)
        return doubleArrayOf(
            0.2104542553 * l + 0.7936177850 * m - 0.0040720468 * s,
            1.9779984951 * l - 2.4285922050 * m + 0.4505937099 * s,
            0.0259040371 * l + 0.7827717662 * m - 0.8086757660 * s
        )
    }

    private fun fromLab(lab: DoubleArray): Int {
        val l = (lab[0] + 0.3963377774 * lab[1] + 0.2158037573 * lab[2]).pow(3)
        val m = (lab[0] - 0.1055613458 * lab[1] - 0.0638541728 * lab[2]).pow(3)
        val s = (lab[0] - 0.0894841775 * lab[1] - 1.2914855480 * lab[2]).pow(3)
        val r = 4.0767416621 * l - 3.3077115913 * m + 0.2309699292 * s
        val g = -1.2684380046 * l + 2.6097574011 * m - 0.3413193965 * s
        val b = -0.0041960863 * l - 0.7034186147 * m + 1.7076147010 * s
        fun to8(v: Double) = (gam(v.coerceIn(0.0, 1.0)) * 255.0 + 0.5).toInt().coerceIn(0, 255)
        return (0xFF shl 24) or (to8(r) shl 16) or (to8(g) shl 8) or to8(b)
    }

    fun lerp(a: Int, b: Int, t: Float): Int {
        val x = toLab(a)
        val y = toLab(b)
        return fromLab(DoubleArray(3) { x[it] + (y[it] - x[it]) * t })
    }

    fun build(dark: Boolean, accent: Accent): Palette {
        val a = if (dark) accent.dark else accent.light
        val white = 0xFFFFFFFF.toInt()
        val black = 0xFF000000.toInt()
        return if (dark) {
            val card = lerp(0xFF1A1D21.toInt(), a, 0.08f)
            Palette(
                true, lerp(0xFF0F1114.toInt(), a, 0.05f), card, lerp(card, a, 0.30f), lerp(card, a, 0.20f),
                0xFFE6E8EB.toInt(), lerp(0xFFB0B5BA.toInt(), a, 0.15f), a, 0xFF101214.toInt(),
                lerp(card, white, 0.08f), 0xFF8C9196.toInt(), lerp(0xFF8A8F94.toInt(), a, 0.30f),
                lerp(card, white, 0.11f), white
            )
        } else {
            val card = lerp(white, a, 0.08f)
            Palette(
                false, lerp(0xFFF4F6F8.toInt(), a, 0.06f), card, lerp(card, a, 0.18f), lerp(card, a, 0.12f),
                0xFF1B1C1E.toInt(), lerp(0xFF5F6368.toInt(), a, 0.10f), a, white,
                lerp(card, black, 0.08f), 0xFF9AA0A6.toInt(), lerp(0xFF6B7075.toInt(), a, 0.40f),
                lerp(card, black, 0.12f), a
            )
        }
    }

    fun isDark(ctx: Context) =
        (ctx.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES

    fun of(ctx: Context): Palette {
        val dark = isDark(ctx)
        val acc = Accent.of(com.furugit.app.data.Prefs.accent)
        return cache.getOrPut(acc.id + dark) { build(dark, acc) }
    }

    /** Menerjemahkan id R.color.furu_* ke warna palet yang sedang aktif. */
    fun resolve(ctx: Context, id: Int): Int {
        val p = of(ctx)
        return when (id) {
            R.color.furu_background -> p.background
            R.color.furu_card -> p.card
            R.color.furu_icon_bg -> p.iconBg
            R.color.furu_trailing_bg -> p.trailingBg
            R.color.furu_text -> p.text
            R.color.furu_muted -> p.muted
            R.color.furu_accent -> p.accent
            R.color.furu_on_accent -> p.onAccent
            R.color.furu_disabled_bg -> p.disabledBg
            R.color.furu_disabled_fg -> p.disabledFg
            R.color.furu_outline -> p.outline
            R.color.furu_card_border -> p.cardBorder
            R.color.furu_header -> p.header
            else -> androidx.core.content.ContextCompat.getColor(ctx, id)
        }
    }
}
