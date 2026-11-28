package com.furugit.app.ui.components

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import com.furugit.app.core.common.tr
import com.furugit.app.core.common.tx
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/** Tombol di dasar dialog. [keepOpen] = dialog tetap terbuka setelah ditekan. */
class DBtn(
    val label: String,
    val primary: Boolean = false,
    val keepOpen: Boolean = false,
    val dimmed: Boolean = false,
    val onClick: (androidx.appcompat.app.AlertDialog) -> Unit = {}
)

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
