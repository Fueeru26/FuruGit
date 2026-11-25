package com.furugit.app.ui

import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import com.furugit.app.R
import com.furugit.app.data.AuthStore
import com.furugit.app.data.Prefs
import com.furugit.app.data.RemoteInfo
import com.furugit.app.data.RepoEntry
import com.furugit.app.data.StatusInfo
import com.furugit.app.git.GitEngine

class SyncFragment : BaseFragment() {
    override val tabPage: Boolean get() = true

    private var remote: String? = null

    override fun refresh() {
        content.removeAllViews()
        val r = needRepo() ?: return
        content.label(r.name, 16f, bold = true)
        val body = LinearLayout(ctx)
        body.orientation = LinearLayout.VERTICAL
        content.addView(body, LinearLayout.LayoutParams(MATCH, WRAP))
        gitQuiet("Sync", {
            GitEngine.use(r.path) { g -> Pair(GitEngine.status(g), GitEngine.remotes(g)) }
        }) { (st, remotes) -> render(r, body, st, remotes) }
    }

    private fun render(r: RepoEntry, body: LinearLayout, st: StatusInfo, remotes: List<RemoteInfo>) {
        body.removeAllViews()
        if (remotes.isEmpty()) {
            body.label("Repository ini belum punya remote.")
            body.button("Tambah remote") { addRemote(r) }
            return
        }
        if (remote == null || remotes.none { it.name == remote }) {
            remote = remotes.firstOrNull { it.name == "origin" }?.name ?: remotes[0].name
        }
        val rn = remote!!
        val cur = remotes.first { it.name == rn }

        body.label("Branch: " + st.branch + (if (st.hasTracking) "   ↑${st.ahead} (belum dikirim)  ↓${st.behind} (belum diambil)" else "   (belum ada tracking)"), 14f)
        body.label("Remote: ${cur.name} → ${cur.url}", 13f, dim = true)
        val acc = try {
            AuthStore.accountFor(cur.url)
        } catch (e: Exception) {
            null
        }
        if (acc == null) body.hint("⚠ Belum ada akun untuk host ini. Repo publik bisa fetch/pull; push & repo private butuh akun di Pengaturan.")
        else body.hint("Akun: ${acc.host} (${acc.username}, ${acc.type})")

        body.hrow {
            button("Fetch", true) {
                sure("Jalankan Fetch?", tx("Mengambil data terbaru dari {} tanpa mengubah file.").replace("{}", rn), "Fetch") {
                    git("Fetch $rn", { GitEngine.use(r.path) { GitEngine.fetch(it, rn, GitEngine.progress()) } }) { refresh() }
                }
            }
            button("Pull") {
                sure("Jalankan Pull?", tx("Mengambil dan menggabungkan perubahan dari {} ke branch aktif.").replace("{}", rn), "Pull") {
                    val rebase = Prefs.pullRebase
                    git("Pull $rn", { GitEngine.use(r.path) { GitEngine.pull(it, rn, rebase, GitEngine.progress()) } }) { res ->
                        showResult(res)
                        refresh()
                    }
                }
            }
            button("Push") {
                sure("Jalankan Push?", tx("Mengirim commit branch aktif ke {}.").replace("{}", rn), "Push") {
                    git("Push $rn", { GitEngine.use(r.path) { GitEngine.safePush(it, rn, GitEngine.progress()) } }) { res ->
                        showResult(res)
                        refresh()
                    }
                }
            }
        }
        val cb = CheckBox(ctx)
        cb.text = tx("Pull dengan rebase (lanjutan; default: merge)")
        cb.isChecked = Prefs.pullRebase
        cb.setOnCheckedChangeListener { _, on -> Prefs.pullRebase = on }
        body.addView(cb, LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = ctx.dp(16) })
        body.hint("Push selalu fetch dulu dan menolak jika remote lebih baru. Tidak ada push paksa.")

        body.title("Daftar remote")
        for (rm in remotes) {
            body.rowCard(R.drawable.ic_sync, (if (rm.name == rn) "● " else "") + rm.name, rm.url,
                onClick = { remoteActions(r, rm) })
        }
        body.button("Tambah remote", true) { addRemote(r) }
    }

    private fun remoteActions(r: RepoEntry, rm: RemoteInfo) {
        Dialogs.choose(ctx, rm.name, listOf("Jadikan remote aktif", "Ubah URL", "Hapus remote")) {
            when (it) {
                0 -> {
                    remote = rm.name
                    refresh()
                }
                1 -> Dialogs.prompt(ctx, "URL baru untuk ${rm.name}", "URL", rm.url) { url ->
                    git("Ubah URL ${rm.name}", {
                        GitEngine.use(r.path) { g ->
                            GitEngine.removeRemote(g, rm.name)
                            GitEngine.addRemote(g, rm.name, url)
                        }
                    }) { refresh() }
                }
                else -> Dialogs.confirm(ctx, "Hapus remote?", rm.name, "Hapus") {
                    git("Hapus remote ${rm.name}", { GitEngine.use(r.path) { g -> GitEngine.removeRemote(g, rm.name) } }) { refresh() }
                }
            }
        }
    }

    private fun addRemote(r: RepoEntry) {
        lateinit var etName: EditText
        lateinit var etUrl: EditText
        Dialogs.form(ctx, "Tambah remote", "Tambah", { box ->
            etName = box.input("Nama", "origin")
            etUrl = box.input("URL (https://github.com/user/repo.git)")
        }) {
            val n = etName.text.toString().trim()
            val u = etUrl.text.toString().trim()
            git("Tambah remote $n", { GitEngine.use(r.path) { GitEngine.addRemote(it, n, u) } }) { refresh() }
        }
    }
}
