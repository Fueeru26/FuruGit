package com.furugit.app.ui.history

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.LinearLayout
import com.furugit.app.R
import com.furugit.app.core.common.GitUserError
import com.furugit.app.core.common.fmtTime
import com.furugit.app.core.common.tx
import com.furugit.app.git.branch.GitBranch
import com.furugit.app.git.history.CommitInfo
import com.furugit.app.git.history.GitHistory
import com.furugit.app.git.remote.GitRemote
import com.furugit.app.git.remote.GitSync
import com.furugit.app.git.repository.GitProgress
import com.furugit.app.git.repository.GitRepository
import com.furugit.app.git.repository.RepoEntry
import com.furugit.app.ui.components.BaseFragment
import com.furugit.app.ui.components.Dialogs
import com.furugit.app.ui.components.MATCH
import com.furugit.app.ui.components.WRAP
import com.furugit.app.ui.components.button
import com.furugit.app.ui.components.dp
import com.furugit.app.ui.components.hint
import com.furugit.app.ui.components.hrow
import com.furugit.app.ui.components.label
import com.furugit.app.ui.components.rowCard
import org.eclipse.jgit.api.ResetCommand

class HistoryFragment : BaseFragment() {

    private var viewRef: String? = null

    override fun refresh() {
        content.removeAllViews()
        val r = needRepo() ?: return
        content.label(r.name, 16f, bold = true)
        content.hrow {
            button("Branch: " + (viewRef ?: "HEAD"), true) { pickBranch(r) }
            button("Tags", true) { showTags(r) }
        }
        val list = LinearLayout(ctx)
        list.orientation = LinearLayout.VERTICAL
        content.addView(list, LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = ctx.dp(16) })
        gitQuiet("Log", { GitRepository.use(r.path) { GitHistory.log(it, viewRef) } }) { commits ->
            list.removeAllViews()
            if (commits.isEmpty()) list.hint("Belum ada commit.")
            for (c in commits) {
                list.rowCard(R.drawable.ic_history, c.message,
                    c.shortId + " · " + c.author + " · " + fmtTime(c.time), onClick = { commitActions(r, c) })
            }
            if (commits.isNotEmpty()) list.hint("Menampilkan maksimal 150 commit terbaru.")
        }
    }

    private fun pickBranch(r: RepoEntry) {
        gitQuiet("Daftar branch", { GitRepository.use(r.path) { GitBranch.branches(it) } }) { bs ->
            val names = listOf("HEAD") + bs.map { it.name }
            Dialogs.choose(ctx, "Lihat riwayat branch", names) {
                viewRef = if (it == 0) null else names[it]
                refresh()
            }
        }
    }

    private fun commitActions(r: RepoEntry, c: CommitInfo) {
        val items = listOf(
            "Cherry-pick ke branch aktif",
            "Revert commit ini",
            "Reset soft ke sini (simpan perubahan, staged)",
            "Reset mixed ke sini (simpan perubahan, unstaged)",
            "Reset HARD ke sini (buang perubahan)",
            "Buat tag di sini",
            "Salin hash"
        )
        Dialogs.choose(ctx, c.shortId + " " + c.message, items) {
            when (it) {
                0 -> Dialogs.confirm(ctx, "Cherry-pick?", "Salin commit ${c.shortId} ke branch aktif.", "Cherry-pick") {
                    git("Cherry-pick ${c.shortId}", { GitRepository.use(r.path) { g -> GitHistory.cherryPick(g, c.id) } }) { res ->
                        showResult(res)
                        refresh()
                    }
                }
                1 -> Dialogs.confirm(ctx, "Revert?", "Membuat commit baru yang membatalkan ${c.shortId}.", "Revert") {
                    git("Revert ${c.shortId}", { GitRepository.use(r.path) { g -> GitHistory.revert(g, c.id) } }) { res ->
                        showResult(res)
                        refresh()
                    }
                }
                2 -> sure("Reset SOFT?", tx("Branch dipindah ke {}; perubahan tetap disimpan.").replace("{}", c.shortId), "Reset") {
                    reset(r, c, ResetCommand.ResetType.SOFT)
                }
                3 -> sure("Reset MIXED?", tx("Branch dipindah ke {}; perubahan tetap disimpan.").replace("{}", c.shortId), "Reset") {
                    reset(r, c, ResetCommand.ResetType.MIXED)
                }
                4 -> Dialogs.confirm(
                    ctx, "Reset HARD?",
                    "Semua perubahan yang belum di-commit akan HILANG dan branch dipindah ke ${c.shortId}.", "Reset HARD"
                ) { reset(r, c, ResetCommand.ResetType.HARD) }
                5 -> Dialogs.prompt(ctx, "Nama tag", "mis. v1.0.0") { name ->
                    if (name.isNotEmpty()) {
                        git("Tag $name", { GitRepository.use(r.path) { g -> GitHistory.createTag(g, name, c.id) } }) { refresh() }
                    }
                }
                else -> {
                    val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    cm.setPrimaryClip(ClipData.newPlainText("commit", c.id))
                    Dialogs.toast(ctx, "Hash disalin")
                }
            }
        }
    }

    private fun reset(r: RepoEntry, c: CommitInfo, mode: ResetCommand.ResetType) {
        git("Reset ${mode.name.lowercase()} ke ${c.shortId}", {
            GitRepository.use(r.path) { g -> GitHistory.reset(g, c.id, mode) }
        }) { refresh() }
    }

    private fun showTags(r: RepoEntry) {
        gitQuiet("Tags", { GitRepository.use(r.path) { GitHistory.tags(it) } }) { tags ->
            if (tags.isEmpty()) {
                Dialogs.info(ctx, "Tags", "Belum ada tag. Tekan sebuah commit → Buat tag di sini.")
            } else {
                Dialogs.choose(ctx, "Tags", tags) { i ->
                    val t = tags[i]
                    Dialogs.choose(ctx, t, listOf("Push tag ke remote", "Hapus tag lokal")) { a ->
                        if (a == 0) {
                            sure("Push tag?", t, "Push") {
                                git("Push tag $t", {
                                    GitRepository.use(r.path) { g ->
                                        val rn = GitRemote.defaultRemote(g) ?: throw com.furugit.app.core.common.GitUserError("Belum ada remote.")
                                        GitSync.pushTag(g, rn, t, GitProgress.monitor())
                                    }
                                })
                            }
                        } else {
                            sure("Hapus tag?", t, "Hapus") {
                                git("Hapus tag $t", { GitRepository.use(r.path) { g -> GitHistory.deleteTag(g, t) } })
                            }
                        }
                    }
                }
            }
        }
    }
}
