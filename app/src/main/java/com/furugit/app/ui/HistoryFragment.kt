package com.furugit.app.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.LinearLayout
import com.furugit.app.R
import com.furugit.app.data.CommitInfo
import com.furugit.app.data.RepoEntry
import com.furugit.app.git.GitEngine
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
        gitQuiet("Log", { GitEngine.use(r.path) { GitEngine.log(it, viewRef) } }) { commits ->
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
        gitQuiet("Daftar branch", { GitEngine.use(r.path) { GitEngine.branches(it) } }) { bs ->
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
                    git("Cherry-pick ${c.shortId}", { GitEngine.use(r.path) { g -> GitEngine.cherryPick(g, c.id) } }) { res ->
                        showResult(res)
                        refresh()
                    }
                }
                1 -> Dialogs.confirm(ctx, "Revert?", "Membuat commit baru yang membatalkan ${c.shortId}.", "Revert") {
                    git("Revert ${c.shortId}", { GitEngine.use(r.path) { g -> GitEngine.revert(g, c.id) } }) { res ->
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
                        git("Tag $name", { GitEngine.use(r.path) { g -> GitEngine.createTag(g, name, c.id) } }) { refresh() }
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
            GitEngine.use(r.path) { g -> GitEngine.reset(g, c.id, mode) }
        }) { refresh() }
    }

    private fun showTags(r: RepoEntry) {
        gitQuiet("Tags", { GitEngine.use(r.path) { GitEngine.tags(it) } }) { tags ->
            if (tags.isEmpty()) {
                Dialogs.info(ctx, "Tags", "Belum ada tag. Tekan sebuah commit → Buat tag di sini.")
            } else {
                Dialogs.choose(ctx, "Tags", tags) { i ->
                    val t = tags[i]
                    Dialogs.choose(ctx, t, listOf("Push tag ke remote", "Hapus tag lokal")) { a ->
                        if (a == 0) {
                            sure("Push tag?", t, "Push") {
                                git("Push tag $t", {
                                    GitEngine.use(r.path) { g ->
                                        val rn = GitEngine.defaultRemote(g) ?: throw com.furugit.app.data.GitUserError("Belum ada remote.")
                                        GitEngine.pushTag(g, rn, t, GitEngine.progress())
                                    }
                                })
                            }
                        } else {
                            sure("Hapus tag?", t, "Hapus") {
                                git("Hapus tag $t", { GitEngine.use(r.path) { g -> GitEngine.deleteTag(g, t) } })
                            }
                        }
                    }
                }
            }
        }
    }
}
