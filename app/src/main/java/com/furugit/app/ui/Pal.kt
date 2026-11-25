package com.furugit.app.ui

import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import com.furugit.app.R
import kotlin.math.cbrt
import kotlin.math.pow

/**
 * Palet gaya "Blossom" FuruArc (gaya bawaan FuruArc). Warnanya tetap: tidak ada pilihan warna tema,
 * hanya mode terang / gelap. Nilainya disalin dari buildPalette(BLOSSOM) + finish() di FuruArc.
 */
class Palette(
    val dark: Boolean,
    val background: Int, val card: Int, val iconBg: Int, val trailingBg: Int,
    val text: Int, val muted: Int, val accent: Int, val onAccent: Int,
    val disabledBg: Int, val disabledFg: Int, val outline: Int, val cardBorder: Int, val header: Int,
    /** Isi kolom input / dropdown: sedikit lebih bernuansa aksen daripada kartu. */
    val field: Int, val fieldBorder: Int,
    /** Pasangan gradien tombol dan kotak ikon (diagonal kiri-atas ke kanan-bawah). */
    val gradA: Int, val gradB: Int, val onGrad: Int,
    /** Gradien lembut (tombol sekunder, kotak ikon "soft"). */
    val softA: Int, val softB: Int,
    /** Kartu kaca: isi, tepi, dan cahaya biru di sekelilingnya. */
    val glassA: Int, val glassB: Int, val rimA: Int, val rimB: Int, val glowBase: Int, val glowPeak: Float,
    /** Bilah navigasi kaca. */
    val navA: Int, val navB: Int, val navRimA: Int, val navRimB: Int,
    val navPill: Int, val navSel: Int, val navUnsel: Int,
    /** Garis pemisah tipis setelah kotak ikon pada kartu menu. */
    val divider: Int
)

object Pal {
    private val cache = HashMap<Boolean, Palette>()

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

    /** Interpolasi Oklab (sama dengan lerp warna Compose). */
    fun lerp(a: Int, b: Int, t: Float): Int {
        val x = toLab(a)
        val y = toLab(b)
        return fromLab(DoubleArray(3) { x[it] + (y[it] - x[it]) * t })
    }

    /** Memutar hue [deg] derajat lalu mencampur dengan putih / hitam sebesar [light] (-1..1), seperti shiftHue di FuruArc. */
    private fun shiftHue(c: Int, deg: Float, light: Float): Int {
        val hsv = FloatArray(3)
        Color.colorToHSV(c, hsv)
        hsv[0] = ((hsv[0] + deg) % 360f + 360f) % 360f
        val r = Color.HSVToColor(hsv)
        return if (light >= 0f) lerp(r, 0xFFFFFFFF.toInt(), light) else lerp(r, 0xFF000000.toInt(), -light)
    }

    /** Luminans relatif (dipakai untuk memilih warna isi di atas gradien). */
    private fun luminance(c: Int): Float {
        val r = lin(((c shr 16) and 255) / 255.0)
        val g = lin(((c shr 8) and 255) / 255.0)
        val b = lin((c and 255) / 255.0)
        return (0.2126 * r + 0.7152 * g + 0.0722 * b).toFloat()
    }

    private fun build(dark: Boolean): Palette {
        val white = 0xFFFFFFFF.toInt()
        val accent = 0xFF307DEF.toInt()
        val background: Int
        val card: Int
        val iconBg: Int
        val trailingBg: Int
        val text: Int
        val muted: Int
        val onAccent: Int
        val disabledBg: Int
        val disabledFg: Int
        val outline: Int
        val cardBorder: Int
        if (dark) {
            background = 0xFF0C142D.toInt(); card = 0xFF17213F.toInt()
            iconBg = 0xFF22346E.toInt(); trailingBg = 0xFF1B2850.toInt()
            text = 0xFFFFFFFF.toInt(); muted = 0xFF8197D6.toInt(); onAccent = 0xFF101214.toInt()
            disabledBg = 0xFF1A2342.toInt(); disabledFg = 0xFF6C7BA8.toInt()
            outline = 0xFF4D6099.toInt(); cardBorder = 0xFF364673.toInt()
        } else {
            background = 0xFFE8EEFF.toInt(); card = 0xFFF4F7FF.toInt()
            iconBg = 0xFFD3DEFF.toInt(); trailingBg = 0xFFDCE5FF.toInt()
            text = 0xFF14214F.toInt(); muted = 0xFF5B6791.toInt(); onAccent = white
            disabledBg = 0xFFDDE3F5.toInt(); disabledFg = 0xFF9AA5C7.toInt()
            outline = 0xFF6F82C8.toInt(); cardBorder = white
        }
        // gradien tombol / kotak ikon: gelap = biru langit ke indigo dalam; terang = diturunkan dari aksen
        val gradA = if (dark) 0xFF3B9BDF.toInt() else shiftHue(accent, -8f, -0.03f)
        val gradB = if (dark) 0xFF1A2F9C.toInt() else shiftHue(accent, 20f, -0.12f)
        val onGrad = if (luminance(accent) > 0.42f) 0xFF101214.toInt() else white
        val field = if (dark) lerp(lerp(card, white, 0.03f), accent, 0.05f) else lerp(card, accent, 0.06f)
        val fieldBorder = lerp(outline, card, if (dark) 0.30f else 0.25f)
        return Palette(
            dark = dark,
            background = background, card = card, iconBg = iconBg, trailingBg = trailingBg,
            text = text, muted = muted, accent = accent, onAccent = onAccent,
            disabledBg = disabledBg, disabledFg = disabledFg, outline = outline, cardBorder = cardBorder,
            header = if (dark) white else accent,
            field = field, fieldBorder = fieldBorder,
            gradA = gradA, gradB = gradB, onGrad = onGrad,
            softA = lerp(card, gradA, if (dark) 0.30f else 0.26f),
            softB = lerp(card, gradB, if (dark) 0.22f else 0.38f),
            glassA = if (dark) 0xFF1F2B4F.toInt() else white,
            glassB = if (dark) 0xFF151F40.toInt() else 0xFFE9EEFF.toInt(),
            rimA = if (dark) 0xFF5B72B0.toInt() else white,
            rimB = if (dark) 0xFF2C3A6A.toInt() else 0xFFC9D6FF.toInt(),
            glowBase = if (dark) 0xFF3A5BC8.toInt() else 0xFF3C5BDB.toInt(),
            glowPeak = if (dark) 0.05f else 0.06f,
            navA = if (dark) 0xFF161D3A.toInt() else white,
            navB = if (dark) 0xFF0F152D.toInt() else 0xFFEAF0FF.toInt(),
            navRimA = if (dark) 0xFF4A5C94.toInt() else white,
            navRimB = if (dark) 0xFF2E3C6C.toInt() else 0xFFC9D6FF.toInt(),
            navPill = if (dark) 0xFF27407B.toInt() else 0xFFD5DFFF.toInt(),
            navSel = if (dark) 0xFF4AB0FF.toInt() else accent,
            navUnsel = if (dark) 0xFFA9BEF5.toInt() else muted,
            divider = if (dark) (0xFF5F77B8.toInt() and 0x00FFFFFF) or (0x8C shl 24)
            else (muted and 0x00FFFFFF) or (0x4D shl 24)
        )
    }

    fun isDark(ctx: Context) =
        (ctx.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES

    fun of(ctx: Context): Palette {
        val dark = isDark(ctx)
        return cache.getOrPut(dark) { build(dark) }
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

/** Mengganti alpha (0..255) dari sebuah warna ARGB. */
fun Int.withAlpha(a: Int): Int = (this and 0x00FFFFFF) or (a.coerceIn(0, 255) shl 24)
