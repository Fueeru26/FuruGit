package com.furugit.app.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.furugit.app.data.ActivityLog
import com.furugit.app.data.OpResult
import com.furugit.app.data.RepoEntry
import com.furugit.app.data.RepoStore
import com.furugit.app.git.friendly
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

abstract class BaseFragment : Fragment() {

    protected lateinit var content: LinearLayout
    protected val ctx get() = requireContext()

    /** true untuk kelima halaman tab (bilah navigasi tampil, jadi isi diberi ruang di bawah). */
    protected open val tabPage: Boolean get() = false

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val c = requireContext()
        val sv = ScrollView(c)
        sv.isFillViewport = true
        sv.clipChildren = false
        sv.clipToPadding = false
        sv.overScrollMode = View.OVER_SCROLL_NEVER
        content = LinearLayout(c)
        content.orientation = LinearLayout.VERTICAL
        content.clipChildren = false
        content.clipToPadding = false
        val p = c.dp(16)
        // halaman tab memberi ruang 92dp untuk bilah navigasi kaca yang melayang di atas isi
        content.setPadding(p, 0, p, p + (if (tabPage) c.dp(92) else 0))
        sv.addView(content, ViewGroup.LayoutParams(MATCH, WRAP))
        sv.setOnScrollChangeListener { _, _, y, _, oy ->
            Jelly.onScrolled(y - oy)
            (activity as? MainActivity)?.onPageScroll(y - oy)
        }
        return sv
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    /** Bangun ulang isi layar. Dipanggil saat layar tampil dan setelah operasi selesai. */
    abstract fun refresh()

    /** Mengembalikan repo aktif, atau menampilkan petunjuk dan mengembalikan null. */
    protected fun needRepo(): RepoEntry? {
        val r = RepoStore.active()
        if (r == null) {
            content.removeAllViews()
            content.title("Belum ada repository aktif")
            content.label("Buka tab Repo, lalu Clone, Init, atau Buka folder repository yang sudah ada.")
        }
        return r
    }

    protected fun <T> git(title: String, block: suspend () -> T, onDone: (T) -> Unit = {}) =
        exec(title, false, block, onDone)

    /** Seperti [git] tetapi tidak mencatat sukses ke log dan error hanya berupa toast. */
    protected fun <T> gitQuiet(title: String, block: suspend () -> T, onDone: (T) -> Unit = {}) =
        exec(title, true, block, onDone)

    private fun <T> exec(title: String, quiet: Boolean, block: suspend () -> T, onDone: (T) -> Unit) {
        val act = requireActivity() as MainActivity
        act.lifecycleScope.launch {
            act.setBusy(true)
            try {
                if (!quiet) ActivityLog.add("▶ $title")
                val result = block()
                if (!quiet) ActivityLog.add("✔ $title")
                if (isAdded && view != null) {
                    onDone(result)
                    // aksi yang mengembalikan OpResult menampilkan hasilnya lewat showResult()
                    if (!quiet && result !is OpResult) {
                        Dialogs.result(requireContext(), true, tx(title) + " " + tr("berhasil", "succeeded"))
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                val msg = e.friendly()
                ActivityLog.add("✘ $title: $msg")
                if (isAdded && view != null) {
                    if (quiet) Dialogs.toast(requireContext(), msg)
                    else Dialogs.result(requireContext(), false, tx("$title gagal") + "\n\n" + msg)
                }
            } finally {
                act.setBusy(false)
            }
        }
    }

    /** Jendela konfirmasi sebelum aksi eksekusi dijalankan. */
    protected fun sure(title: String, msg: String, ok: String = "Jalankan", go: () -> Unit) =
        Dialogs.confirm(ctx, title, msg, ok, go)

    protected fun showResult(res: OpResult) {
        ActivityLog.add((if (res.ok) "✔ " else "⚠ ") + res.message)
        if (res.ok) {
            Dialogs.result(ctx, true, res.message)
        } else {
            val extra = if (res.conflicts.isNotEmpty()) "\n\nFile:\n" + res.conflicts.joinToString("\n") { "• $it" } else ""
            Dialogs.result(ctx, false, res.message + extra)
        }
    }
}
