package com.furugit.app.ui.changes

import android.graphics.Typeface
import android.view.Gravity
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import com.furugit.app.git.branch.GitMerge
import com.furugit.app.git.commit.GitCommit
import com.furugit.app.git.diff.GitDiff
import com.furugit.app.git.history.GitHistory
import com.furugit.app.git.remote.GitSync
import com.furugit.app.git.repository.GitProgress
import com.furugit.app.git.repository.GitRepository
import com.furugit.app.git.repository.RepoEntry
import com.furugit.app.git.status.ChangeKind
import com.furugit.app.git.status.FileChange
import com.furugit.app.git.status.GitStatus
import com.furugit.app.git.status.StatusInfo
import com.furugit.app.ui.components.BaseFragment
import com.furugit.app.ui.components.Dialogs
import com.furugit.app.ui.components.MATCH
import com.furugit.app.ui.components.WRAP
import com.furugit.app.ui.components.button
import com.furugit.app.ui.components.card
import com.furugit.app.ui.components.colorizeDiff
import com.furugit.app.ui.components.dp
import com.furugit.app.ui.components.hint
import com.furugit.app.ui.components.hrow
import com.furugit.app.ui.components.input
import com.furugit.app.ui.components.label
import com.furugit.app.ui.components.onChange
import com.furugit.app.ui.components.title

class ChangesFragment : BaseFragment() {
    override val tabPage: Boolean get() = true

    private var msg = ""
    private var desc = ""
    private val selected = HashSet<String>()
    private var unstaged: List<FileChange> = emptyList()

    override fun refresh() {
        content.removeAllViews()
        val r = needRepo() ?: return
        content.label(r.name, 16f, bold = true)
        val body = LinearLayout(ctx)
        body.orientation = LinearLayout.VERTICAL
        content.addView(body, LinearLayout.LayoutParams(MATCH, WRAP))
        gitQuiet("Status", { GitRepository.use(r.path) { GitStatus.status(it) } }) { st -> render(r, body, st) }
    }

    private fun tag(c: FileChange): String = when (c.kind) {
        ChangeKind.ADDED -> "[A]"
        ChangeKind.MODIFIED -> "[M]"
        ChangeKind.DELETED -> "[D]"
        ChangeKind.UNTRACKED -> "[?]"
    }

    private fun render(r: RepoEntry, body: LinearLayout, st: StatusInfo) {
        body.removeAllViews()
        unstaged = st.unstaged
        selected.clear()
        for (c in st.unstaged) selected.add(c.path)

        body.label("Branch: " + st.branch + (if (st.hasTracking) "   ↑${st.ahead} ↓${st.behind}" else ""), 14f, dim = true)

        if (st.isMerging) {
            body.card {
                label("Operasi merge sedang berlangsung (${st.state})", bold = true)
                hint("Selesaikan konflik, lalu Commit untuk menyelesaikan merge.")
                button("Batalkan merge", true) {
                    Dialogs.confirm(ctx, "Batalkan merge?", "Working tree dikembalikan ke commit terakhir (HEAD).", "Batalkan") {
                        git("Batalkan merge", { GitRepository.use(r.path) { GitMerge.abortMerge(it) } }) { refresh() }
                    }
                }
            }
        }

        if (st.conflicts.isNotEmpty()) {
            body.title("Konflik (${st.conflicts.size})")
            for (p in st.conflicts) {
                body.card {
                    label(p, 14f, bold = true, mono = true)
                    hrow {
                        button("Milik saya", true) { resolve(r, p, true) }
                        button("Milik remote", true) { resolve(r, p, false) }
                        button("Edit", true) { editConflict(r, p) }
                    }
                }
            }
        }

        if (st.staged.isNotEmpty()) {
            body.title("Di-stage (${st.staged.size})")
            for (c in st.staged) {
                body.card(onClick = { showDiff(r, c) }, onLong = {
                    Dialogs.confirm(ctx, "Unstage?", c.path, "Unstage") {
                        git("Unstage ${c.path}", { GitRepository.use(r.path) { GitCommit.unstage(it, c.path) } }) { refresh() }
                    }
                }) {
                    label(tag(c) + "  " + c.path, 14f, mono = true)
                }
            }
            body.hint("Tap = lihat diff · tekan lama = unstage")
        }

        body.title("Belum di-stage (${st.unstaged.size})")
        if (st.unstaged.isEmpty()) body.hint("Tidak ada perubahan di working tree.")
        for (c in st.unstaged) {
            val row = LinearLayout(ctx)
            row.orientation = LinearLayout.HORIZONTAL
            row.gravity = Gravity.CENTER_VERTICAL
            val cb = CheckBox(ctx)
            cb.isChecked = true
            cb.setOnCheckedChangeListener { _, on -> if (on) selected.add(c.path) else selected.remove(c.path) }
            val tv = TextView(ctx)
            tv.text = tag(c) + "  " + c.path
            tv.typeface = Typeface.MONOSPACE
            tv.textSize = 13f
            tv.setPadding(0, ctx.dp(10), 0, ctx.dp(10))
            tv.setOnClickListener { showDiff(r, c) }
            row.addView(cb)
            row.addView(tv, LinearLayout.LayoutParams(0, WRAP, 1f))
            body.addView(row, LinearLayout.LayoutParams(MATCH, WRAP))
        }
        if (st.unstaged.isNotEmpty()) {
            body.hrow {
                button("Stage terpilih") { stageSelected(r) }
                button("Stage semua", true) {
                    sure("Stage semua file?", "Semua perubahan di working tree akan di-stage.", "Stage") {
                        git("Stage semua", { GitRepository.use(r.path) { GitCommit.stageAll(it) } }) { refresh() }
                    }
                }
            }
            body.button("Buang perubahan terpilih", true) { discardSelected(r) }
        }

        body.title("Commit")
        val etMsg = body.input("Pesan commit", msg)
        etMsg.onChange { msg = it }
        val etDesc = body.input("Deskripsi (opsional)", desc, multiline = true)
        etDesc.onChange { desc = it }
        body.button("Pesan terakhir", true) {
            gitQuiet("Pesan terakhir", { GitRepository.use(r.path) { GitHistory.lastMessage(it) } }) { last ->
                if (last.isNullOrBlank()) {
                    Dialogs.toast(ctx, "Belum ada commit.")
                } else {
                    val head = last.substringBefore("\n").trim()
                    val rest = last.substringAfter("\n", "").trim()
                    etMsg.setText(head)
                    etDesc.setText(rest)
                    Dialogs.toast(ctx, "Pesan commit terakhir dimuat")
                }
            }
        }
        body.hrow {
            button("Commit") { doCommit(r) }
            button("Commit & Push") { doQuickSync(r) }
        }
        body.hint("Commit = hanya yang sudah di-stage. Commit & Push = otomatis stage file tercentang → commit → cek remote → push.")
    }

    private fun fullMessage(): String =
        if (desc.isBlank()) msg.trim() else msg.trim() + "\n\n" + desc.trim()

    private fun stageSelected(r: RepoEntry) {
        val items = unstaged.filter { it.path in selected }
        if (items.isEmpty()) {
            Dialogs.toast(ctx, "Pilih file dulu")
            return
        }
        sure("Stage file terpilih?", "Stage ${items.size} file", "Stage") {
            git("Stage ${items.size} file", { GitRepository.use(r.path) { GitCommit.stage(it, items) } }) { refresh() }
        }
    }

    private fun discardSelected(r: RepoEntry) {
        val items = unstaged.filter { it.path in selected }
        if (items.isEmpty()) {
            Dialogs.toast(ctx, "Pilih file dulu")
            return
        }
        val untracked = items.count { it.kind == ChangeKind.UNTRACKED }
        val warn = "Perubahan di ${items.size} file akan HILANG permanen." +
            (if (untracked > 0) "\n$untracked file baru (untracked) akan DIHAPUS dari HP." else "")
        Dialogs.confirm(ctx, "Buang perubahan?", warn, "Buang") {
            git("Buang ${items.size} file", {
                GitRepository.use(r.path) { g -> for (c in items) GitCommit.discard(g, c) }
            }) { refresh() }
        }
    }

    private fun doCommit(r: RepoEntry) {
        val m = fullMessage()
        sure("Buat commit?", "Hanya file yang sudah di-stage yang masuk ke commit.\n\n$m", "Commit") {
            git("Commit", { GitRepository.use(r.path) { GitCommit.commit(it, m) } }) { id ->
                msg = ""
                desc = ""
                Dialogs.toast(ctx, "Commit $id dibuat")
                refresh()
            }
        }
    }

    private fun doQuickSync(r: RepoEntry) {
        val m = fullMessage()
        val include = unstaged.filter { it.path in selected }
        sure("Commit & Push?", "File tercentang akan di-stage, di-commit, lalu dikirim ke remote.\n\n$m", "Commit & Push") {
            git("Commit & Push", {
                GitRepository.use(r.path) { GitSync.quickSync(it, include, m, GitProgress.monitor()) }
            }) { res ->
                if (res.ok) {
                    msg = ""
                    desc = ""
                }
                showResult(res)
                refresh()
            }
        }
    }

    private fun showDiff(r: RepoEntry, c: FileChange) {
        gitQuiet("Diff ${c.path}", { GitRepository.use(r.path) { GitDiff.diff(it, c) } }) { text ->
            Dialogs.text(ctx, tag(c) + " " + c.path, colorizeDiff(text))
        }
    }

    private fun resolve(r: RepoEntry, path: String, mine: Boolean) {
        sure(if (mine) "Pakai versi milik saya?" else "Pakai versi milik remote?", "Konflik pada $path diselesaikan dengan versi tersebut.", "Pakai") {
            git("Selesaikan $path", { GitRepository.use(r.path) { GitMerge.resolveKeep(it, path, mine) } }) { refresh() }
        }
    }

    private fun editConflict(r: RepoEntry, path: String) {
        gitQuiet("Baca $path", { GitRepository.use(r.path) { GitMerge.readWorkFile(it, path) } }) { text ->
            var et: android.widget.EditText? = null
            Dialogs.form(ctx, path, "Simpan & tandai selesai", { box ->
                box.hint("Hapus penanda <<<<<<<, =======, >>>>>>> lalu pilih isi akhirnya.")
                val e = box.input("Isi file", text, multiline = true)
                e.typeface = Typeface.MONOSPACE
                e.textSize = 12f
                e.minLines = 10
                et = e
            }) {
                val newText = et?.text?.toString() ?: text
                if (newText.contains("<<<<<<<") || newText.contains(">>>>>>>")) {
                    Dialogs.info(ctx, "Masih ada penanda konflik", "Hapus baris <<<<<<< / ======= / >>>>>>> dulu, lalu simpan lagi.")
                } else {
                    git("Simpan $path", { GitRepository.use(r.path) { GitMerge.writeResolved(it, path, newText) } }) { refresh() }
                }
            }
        }
    }
}
