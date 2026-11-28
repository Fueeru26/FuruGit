package com.furugit.app.ui.actions

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.BackgroundColorSpan
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import com.furugit.app.MainActivity
import com.furugit.app.R
import com.furugit.app.core.common.L
import com.furugit.app.core.common.friendly
import com.furugit.app.core.common.tr
import com.furugit.app.core.storage.safeFileName
import com.furugit.app.git.remote.GitRemote
import com.furugit.app.github.actions.ActionsApi
import com.furugit.app.ui.components.BaseFragment
import com.furugit.app.ui.components.Dialogs
import com.furugit.app.ui.components.Fonts
import com.furugit.app.ui.components.MATCH
import com.furugit.app.ui.components.Pal
import com.furugit.app.ui.components.WRAP
import com.furugit.app.ui.components.button
import com.furugit.app.ui.components.card
import com.furugit.app.ui.components.color
import com.furugit.app.ui.components.dp
import com.furugit.app.ui.components.hint
import com.furugit.app.ui.components.hrow
import com.furugit.app.ui.components.input
import com.furugit.app.ui.components.pageNav
import com.furugit.app.ui.components.withAlpha
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Halaman log sebuah run. Tiap job satu kartu yang bisa dibuka / ditutup (seperti Panduan); isinya dibagi per halaman
 * 50 baris dengan navigasi halaman ala FuruArc, jadi seluruh log tampil tanpa batas. Baris error berwarna merah,
 * warning kuning, judul langkah tebal. Ada pencarian di seluruh job, tombol "Ke error pertama", serta salin / bagikan log.
 */
class RunLogFragment : BaseFragment() {

    private val pageSize = 50
    private val stamp = Regex("""^\d{4}-\d\d-\d\dT[\d:.]+Z """)
    private val ansi = Regex("\u001B\\[[0-9;]*[A-Za-z]")
    private val errorRe = Regex("""##\[error\]|\be: |\berror\b:|\bERROR\b|\bFAILED\b|FAILURE:|Exception\b|\bfatal\b""")
    private val warnRe = Regex("""##\[warning\]|\bwarning\b:|\bWARNING\b|^w: """)

    private val secs = ArrayList<Sec>()
    private val hits = ArrayList<Pair<Int, Int>>()
    private var hitPos = -1
    private var resultBox: LinearLayout? = null

    override fun refresh() {
        content.removeAllViews()
        secs.clear()
        hits.clear()
        hitPos = -1
        val r = needRepo() ?: return
        val runId = arguments?.getLong("run") ?: 0L
        content.hint(tr("Memuat log…", "Loading log…"))
        val act = requireActivity() as MainActivity
        viewLifecycleOwner.lifecycleScope.launch {
            act.setBusy(true)
            try {
                val gh = ActionsApi.refFor(GitRemote.remoteUrl(r.path))
                val jobs = ActionsApi.jobs(gh, runId)
                val sections = ArrayList<Pair<String, String>>()
                for (j in jobs) {
                    val text = try {
                        ActionsApi.jobLog(gh, j.id)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Throwable) {
                        tr("(Log belum tersedia: ", "(Log not available: ") + e.friendly() + ")"
                    }
                    sections.add(j.name + " — " + (j.conclusion ?: j.status) to text)
                }
                if (view == null) return@launch
                content.removeAllViews()
                if (sections.isEmpty()) {
                    content.hint(tr("Run ini tidak punya job.", "This run has no jobs."))
                } else {
                    searchCard()
                    content.hint(tr("Ketuk judul job untuk membuka atau menutup. Log dibagi 50 baris per halaman.", "Tap a job title to expand or collapse. The log is split into 50 lines per page."))
                    for ((name, raw) in sections) {
                        val s = Sec(name, raw)
                        secs.add(s)
                        s.build(sections.size == 1)
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                if (view != null) {
                    content.removeAllViews()
                    content.hint(e.friendly())
                }
            } finally {
                act.setBusy(false)
            }
        }
    }

    // ───────────── pencarian ─────────────

    private fun searchCard() {
        content.card {
            val et: EditText = input(tr("Cari di log", "Search the log"))
            hrow {
                button(tr("Cari", "Search")) {
                    val q = et.text.toString().trim()
                    if (q.isNotEmpty()) search(Regex(Regex.escape(q), RegexOption.IGNORE_CASE))
                }
                button(tr("Ke error pertama", "First error"), true) { search(errorRe) }
            }
            val box = LinearLayout(context)
            box.orientation = LinearLayout.VERTICAL
            addView(box, LinearLayout.LayoutParams(MATCH, WRAP))
            resultBox = box
        }
    }

    private fun search(re: Regex) {
        for (s in secs) s.clearHighlight()
        hits.clear()
        hitPos = -1
        secs.forEachIndexed { si, s ->
            s.lineList().forEachIndexed { li, l -> if (re.containsMatchIn(l)) hits.add(si to li) }
        }
        val box = resultBox ?: return
        box.removeAllViews()
        if (hits.isEmpty()) {
            box.hint(tr("Tidak ditemukan.", "No match."))
            return
        }
        val status = TextView(ctx)
        status.textSize = 13f
        status.typeface = Fonts.semibold(ctx)
        status.setTextColor(Pal.of(ctx).text)
        status.gravity = Gravity.CENTER
        status.setPadding(0, ctx.dp(10), 0, ctx.dp(10))
        val row = LinearLayout(ctx)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER_VERTICAL
        fun move(delta: Int) {
            hitPos = (hitPos + delta + hits.size) % hits.size
            val (si, li) = hits[hitPos]
            for (s in secs) s.clearHighlight()
            secs[si].showLine(li)
            status.text = tr("Cocok ", "Match ") + (hitPos + 1) + " / " + hits.size + "\n" + secs[si].name + " · " + tr("baris ", "line ") + (li + 1)
        }
        row.button("‹", true) { move(-1) }
        row.addView(status, LinearLayout.LayoutParams(0, WRAP, 2f).apply { marginEnd = ctx.dp(8) })
        val next = row.button("›", true) { move(1) }
        (next.layoutParams as LinearLayout.LayoutParams).marginEnd = 0
        box.addView(row, LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = ctx.dp(12) })
        move(0)
    }

    // ───────────── satu kartu job ─────────────

    private inner class Sec(val name: String, private val raw: String) {
        private var lines: List<String>? = null
        private var page = 0
        private var hl = -1
        private lateinit var head: TextView
        private lateinit var body: LinearLayout
        private var cardView: View? = null

        fun lineList(): List<String> {
            lines?.let { return it }
            val l = raw.split("\n").map { ansi.replace(stamp.replaceFirst(it.trimEnd('\r'), ""), "") }
            lines = l
            return l
        }

        fun build(openAtStart: Boolean) {
            val inner = content.card {
                head = TextView(context)
                head.textSize = 16f
                head.typeface = Fonts.bold(context)
                head.setTextColor(context.color(R.color.furu_text))
                head.text = name + "  ▾"
                addView(head, LinearLayout.LayoutParams(MATCH, WRAP))
                body = LinearLayout(context)
                body.orientation = LinearLayout.VERTICAL
                body.visibility = View.GONE
                addView(body, LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = context.dp(8) })
                head.setOnClickListener { setOpen(body.visibility != View.VISIBLE) }
            }
            cardView = inner.parent as? View
            if (openAtStart) setOpen(true)
        }

        private fun setOpen(open: Boolean) {
            head.text = name + (if (open) "  ▴" else "  ▾")
            body.visibility = if (open) View.VISIBLE else View.GONE
            if (open && body.childCount == 0) render()
        }

        fun clearHighlight() {
            if (hl >= 0) {
                hl = -1
                if (body.visibility == View.VISIBLE && body.childCount > 0) render()
            }
        }

        /** Membuka kartu, pindah ke halaman yang memuat baris [li], menyorotnya, dan menggulir ke kartu. */
        fun showLine(li: Int) {
            hl = li
            page = li / pageSize
            head.text = name + "  ▴"
            body.visibility = View.VISIBLE
            render()
            scrollToCard()
        }

        private fun scrollToCard() {
            val sv = content.parent as? ScrollView
            val c = cardView
            if (sv != null && c != null) sv.post { sv.smoothScrollTo(0, c.top) }
        }

        private fun styled(all: List<String>, from: Int, to: Int): CharSequence {
            val pal = Pal.of(ctx)
            val sb = SpannableStringBuilder()
            for (i in from until to) {
                var line = all[i]
                val start = sb.length
                val group = line.startsWith("##[group]")
                if (group) line = "▸ " + line.removePrefix("##[group]")
                sb.append(line)
                val end = sb.length
                when {
                    errorRe.containsMatchIn(line) -> sb.setSpan(ForegroundColorSpan(0xFFE5484D.toInt()), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    warnRe.containsMatchIn(line) -> sb.setSpan(ForegroundColorSpan(0xFFF2C230.toInt()), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    line.startsWith("##[endgroup]") -> sb.setSpan(ForegroundColorSpan(pal.muted), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
                if (group) sb.setSpan(StyleSpan(Typeface.BOLD), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                if (i == hl) sb.setSpan(BackgroundColorSpan(pal.accent.withAlpha(90)), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                if (i < to - 1) sb.append('\n')
            }
            return sb
        }

        private fun render() {
            val all = lineList()
            val pages = maxOf(1, (all.size + pageSize - 1) / pageSize)
            page = page.coerceIn(0, pages - 1)
            val from = page * pageSize
            val to = minOf(all.size, from + pageSize)
            body.removeAllViews()
            if (pages > 1) body.pageNav(page, pages) { np ->
                page = np
                render()
            }
            body.hint(tr("Baris ${from + 1}–$to dari ${all.size}", "Lines ${from + 1}–$to of ${all.size}"))
            val tv = TextView(ctx)
            tv.text = styled(all, from, to)
            tv.textSize = 11f
            tv.typeface = Typeface.MONOSPACE
            tv.setTextColor(Pal.of(ctx).text)
            tv.setTextIsSelectable(true)
            body.addView(tv, LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = ctx.dp(8) })
            if (pages > 1) body.pageNav(page, pages) { np ->
                page = np
                render()
                scrollToCard()
            }
            body.hrow {
                button(tr("Salin halaman", "Copy page"), true) {
                    val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    cm.setPrimaryClip(ClipData.newPlainText("log", all.subList(from, to).joinToString("\n")))
                    Dialogs.toast(ctx, tr("Halaman log disalin.", "Log page copied."))
                }
                button(tr("Bagikan log", "Share log"), true) { share(all) }
            }
        }

        /** Membagikan seluruh log job sebagai file teks lewat menu bagikan Android. */
        private fun share(all: List<String>) {
            viewLifecycleOwner.lifecycleScope.launch {
                try {
                    val f = withContext(Dispatchers.IO) {
                        val dir = File(ctx.cacheDir, "updates")
                        dir.mkdirs()
                        val out = File(dir, safeFileName(name.substringBefore(" — ")) + ".log")
                        out.writeText(all.joinToString("\n"))
                        out
                    }
                    val uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".files", f)
                    val send = Intent(Intent.ACTION_SEND)
                        .setType("text/plain")
                        .putExtra(Intent.EXTRA_STREAM, uri)
                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    startActivity(Intent.createChooser(send, tr("Bagikan log", "Share log")))
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Throwable) {
                    Dialogs.toast(ctx, e.friendly())
                }
            }
        }
    }
}
