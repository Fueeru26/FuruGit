package com.furugit.app.ui

import android.widget.CheckBox
import android.widget.LinearLayout
import com.furugit.app.R
import com.furugit.app.data.BranchInfo
import com.furugit.app.data.RepoEntry
import com.furugit.app.data.StashInfo
import com.furugit.app.data.StatusInfo
import com.furugit.app.git.GitEngine

class BranchesFragment : BaseFragment() {
    override val tabPage: Boolean get() = true

    override fun refresh() {
        content.removeAllViews()
        val r = needRepo() ?: return
        content.label(r.name, 16f, bold = true)
        content.button("Branch baru") { newBranch(r) }
        val body = LinearLayout(ctx)
        body.orientation = LinearLayout.VERTICAL
        content.addView(body, LinearLayout.LayoutParams(MATCH, WRAP))
        gitQuiet("Branch", {
            GitEngine.use(r.path) { g -> Triple(GitEngine.branches(g), GitEngine.stashList(g), GitEngine.status(g)) }
        }) { (bs, stashes, st) -> render(r, body, bs, stashes, st) }
    }

    private fun render(r: RepoEntry, body: LinearLayout, bs: List<BranchInfo>, stashes: List<StashInfo>, st: StatusInfo) {
        body.removeAllViews()
        body.label("Aktif: " + st.branch, 14f, dim = true)
        if (st.isMerging) body.hint("⚠ Ada merge yang belum selesai. Selesaikan di tab Perubahan.")

        body.title("Lokal")
        val local = bs.filter { !it.isRemote }
        if (local.isEmpty()) body.hint("Belum ada branch (buat commit pertama dulu).")
        for (b in local) {
            val tb = if (b.isCurrent) Badge(ctx, b.name, semibold = true) else null
            body.rowCard(R.drawable.ic_branch, b.name,
                if (b.isCurrent) "Branch aktif" else null, onClick = { branchActions(r, b, st) }, titleBadge = tb)
            if (tb != null) checkRemote(r.path, null, tb)
        }
        val remote = bs.filter { it.isRemote }
        if (remote.isNotEmpty()) {
            body.title("Remote")
            for (b in remote) {
                body.rowCard(R.drawable.ic_branch, b.name, null, onClick = { branchActions(r, b, st) })
            }
        }

        body.title("Stash")
        body.hrow {
            button("Stash perubahan") { stashDialog(r) }
            button("Hapus semua", true) {
                if (stashes.isEmpty()) Dialogs.toast(ctx, "Tidak ada stash")
                else Dialogs.confirm(ctx, "Hapus semua stash?", "Tidak bisa dikembalikan.", "Hapus") {
                    git("Hapus semua stash", { GitEngine.use(r.path) { GitEngine.stashClear(it) } }) { refresh() }
                }
            }
        }
        if (stashes.isEmpty()) body.hint("Tidak ada stash.")
        for (s in stashes) {
            body.rowCard(R.drawable.ic_edit, "stash@{" + s.index + "}  " + s.message, fmtTime(s.time),
                onClick = { stashActions(r, s) })
        }
    }

    private fun newBranch(r: RepoEntry) {
        Dialogs.prompt(ctx, "Branch baru (langsung pindah)", "Nama branch, mis. feature-translation") { name ->
            if (name.isNotEmpty()) {
                git("Buat branch $name", { GitEngine.use(r.path) { GitEngine.createBranch(it, name) } }) { refresh() }
            }
        }
    }

    private fun branchActions(r: RepoEntry, b: BranchInfo, st: StatusInfo) {
        val acts = ArrayList<Pair<String, () -> Unit>>()
        fun add(label: String, action: () -> Unit) {
            acts.add(Pair(label, action))
        }
        if (!b.isCurrent) {
            add(if (b.isRemote) "Checkout sebagai branch lokal" else "Pindah ke branch ini") {
                sure("Pindah branch?", "Working tree akan berpindah ke branch tersebut.\n\n${b.name}", "Pindah") {
                    git("Pindah ke ${b.name}", { GitEngine.use(r.path) { GitEngine.switchBranch(it, b.name, b.isRemote) } }) { refresh() }
                }
            }
            add("Merge ke ${st.branch}") {
                Dialogs.confirm(ctx, "Merge?", "Gabungkan ${b.name} ke ${st.branch}.", "Merge") {
                    git("Merge ${b.name}", { GitEngine.use(r.path) { GitEngine.merge(it, b.name) } }) { res ->
                        showResult(res)
                        refresh()
                    }
                }
            }
            if (!b.isRemote) {
                add("Hapus branch") { deleteBranch(r, b, false) }
                add("Hapus paksa (belum di-merge)") { deleteBranch(r, b, true) }
            }
        }
        if (acts.isEmpty()) {
            Dialogs.info(ctx, b.name, "Ini branch yang sedang aktif.")
            return
        }
        Dialogs.choose(ctx, b.name, acts.map { it.first }) { acts[it].second() }
    }

    private fun deleteBranch(r: RepoEntry, b: BranchInfo, force: Boolean) {
        Dialogs.confirm(ctx, "Hapus branch?", b.name, "Hapus") {
            git("Hapus branch ${b.name}", { GitEngine.use(r.path) { GitEngine.deleteBranch(it, b.name, force) } }) { refresh() }
        }
    }

    private fun stashDialog(r: RepoEntry) {
        var etMsg: android.widget.EditText? = null
        var cb: CheckBox? = null
        Dialogs.form(ctx, "Stash perubahan", "Stash", { box ->
            etMsg = box.input("Pesan (opsional)")
            val c = CheckBox(ctx)
            c.text = tx("Sertakan file baru (untracked)")
            box.addView(c)
            cb = c
        }) {
            val m = etMsg?.text?.toString()?.trim() ?: ""
            val inc = cb?.isChecked ?: false
            git("Stash", { GitEngine.use(r.path) { GitEngine.stashSave(it, m, inc) } }) { refresh() }
        }
    }

    private fun stashActions(r: RepoEntry, s: StashInfo) {
        Dialogs.choose(ctx, "stash@{" + s.index + "}", listOf("Apply (stash tetap ada)", "Pop (apply lalu hapus)", "Drop (hapus)")) {
            when (it) {
                0 -> sure("Apply stash?", "Perubahan dari stash diterapkan; stash tetap ada.", "Apply") {
                    git("Apply stash", { GitEngine.use(r.path) { g -> GitEngine.stashApply(g, s.index) } }) { refresh() }
                }
                1 -> sure("Pop stash?", "Perubahan dari stash diterapkan lalu stash dihapus.", "Pop") {
                    git("Pop stash", { GitEngine.use(r.path) { g -> GitEngine.stashPop(g, s.index) } }) { refresh() }
                }
                else -> Dialogs.confirm(ctx, "Drop stash?", s.message, "Drop") {
                    git("Drop stash", { GitEngine.use(r.path) { g -> GitEngine.stashDrop(g, s.index) } }) { refresh() }
                }
            }
        }
    }
}
