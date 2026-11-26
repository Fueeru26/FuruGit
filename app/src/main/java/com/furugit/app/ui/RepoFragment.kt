package com.furugit.app.ui

import android.widget.EditText
import android.widget.TextView
import com.furugit.app.R
import com.furugit.app.data.Prefs
import com.furugit.app.data.RepoEntry
import com.furugit.app.data.RepoStore
import com.furugit.app.git.GitEngine
import androidx.lifecycle.lifecycleScope
import com.furugit.app.git.friendly
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class RepoFragment : BaseFragment() {
    override val tabPage: Boolean get() = true

    override fun refresh() {
        content.removeAllViews()
        content.hrow {
            button("Clone") { cloneDialog() }
            button("Init", true) { initDialog() }
            button("Buka folder", true) { openDialog() }
        }

        val repos = RepoStore.list()
        if (repos.isEmpty()) {
            content.space(12)
            content.label("Belum ada repository. Clone dari GitHub, buat baru dengan Init, atau buka folder repo yang sudah ada di HP.")
        }
        val active = RepoStore.activePath
        for (r in repos) {
            val isActive = r.path == active
            val badge = if (isActive) Badge(ctx, tr("Proses", "Processing")) else null
            val col = content.rowCard(
                R.drawable.ic_folder,
                r.name,
                r.path,
                onClick = {
                    if (isActive) {
                        (requireActivity() as MainActivity).openTab(R.id.nav_changes)
                    } else {
                        RepoStore.activePath = r.path
                        refresh()
                    }
                },
                onLong = { removeDialog(r) },
                badge = badge
            )
            if (isActive) {
                col.space(6)
                val info = col.label("Memuat status…", 13f)
                loadInfo(r, info, badge!!)
            }
        }
        content.space(12)
        content.hint("Tap = jadikan aktif · tap repo aktif = buka tab Perubahan · tekan lama = hapus dari daftar (file di HP tidak dihapus).")
    }

    /**
     * Memuat status repo aktif lalu mengecek koneksi ke remote. Penanda "Aktif" putih / hitam saat memuat,
     * hijau bila terhubung, dan menjadi "Gagal" merah bila gagal (mis. tidak ada koneksi internet).
     */
    private fun loadInfo(r: RepoEntry, tv: TextView, badge: Badge) {
        val act = requireActivity() as MainActivity
        viewLifecycleOwner.lifecycleScope.launch {
            act.setBusy(true)
            try {
                val (st, remotes) = GitEngine.use(r.path) { g -> Pair(GitEngine.status(g), GitEngine.remotes(g)) }
                val sb = StringBuilder()
                sb.append("Branch: ").append(st.branch)
                if (st.hasTracking) sb.append("  ↑").append(st.ahead).append(" ↓").append(st.behind)
                val n = st.staged.size + st.unstaged.size
                sb.append("\n").append(if (n == 0) "Bersih, tidak ada perubahan" else "$n file berubah")
                if (st.isMerging) sb.append("\nStatus: ").append(st.state)
                sb.append("\nRemote: ").append(if (remotes.isEmpty()) "(belum ada)" else remotes.joinToString { it.name + " → " + it.url })
                if (view == null) return@launch
                tv.text = tx(sb.toString())
                val url = (remotes.firstOrNull { it.name == "origin" } ?: remotes.firstOrNull())?.url
                if (url == null || GitEngine.reachable(url)) {
                    badge.set(Badge.OK, tr("Aktif", "Active"))
                } else {
                    badge.set(Badge.FAIL, tr("Gagal", "Failed"))
                    tv.text = tx(sb.toString()) + "\n" + tr("Tidak bisa terhubung ke remote (periksa koneksi internet).", "Cannot reach the remote (check your internet connection).")
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                if (view != null) {
                    badge.set(Badge.FAIL, tr("Gagal", "Failed"))
                    tv.text = tx(e.friendly())
                }
            } finally {
                act.setBusy(false)
            }
        }
    }

    private fun nameFromUrl(url: String): String =
        url.trim().trimEnd('/').substringAfterLast('/').substringAfterLast(':').removeSuffix(".git")

    private fun cloneDialog() {
        var parent = File(Prefs.lastParent)
        lateinit var etUrl: EditText
        lateinit var etName: EditText
        lateinit var parentLabel: TextView
        var auto = ""
        Dialogs.form(ctx, "Clone repository", "Clone", { box ->
            etUrl = box.input("URL repository (https://github.com/user/repo)")
            etName = box.input("Nama folder")
            parentLabel = box.label("Folder induk: " + parent.absolutePath, 12f, dim = true)
            box.button("Pilih folder induk", true) {
                FolderPicker.pick(ctx, parent, "Folder induk", true) {
                    parent = it
                    parentLabel.text = tx("Folder induk: {}").replace("{}", it.absolutePath)
                }
            }
            etUrl.onChange { url ->
                val n = nameFromUrl(url)
                if (etName.text.toString() == auto) etName.setText(n)
                auto = n
            }
        }) {
            val url = etUrl.text.toString().trim()
            val name = etName.text.toString().trim()
            if (url.isEmpty() || name.isEmpty()) {
                Dialogs.toast(ctx, "URL dan nama folder wajib diisi")
            } else {
                Prefs.lastParent = parent.absolutePath
                val dir = File(parent, name)
                git("Clone $url", { GitEngine.clone(url, dir, GitEngine.progress()) }) {
                    RepoStore.add(RepoEntry(name, dir.absolutePath))
                    refresh()
                }
            }
        }
    }

    private fun initDialog() {
        FolderPicker.pick(ctx, File(Prefs.lastParent), "Pilih folder untuk Init", true) { dir ->
            sure("Jalankan Init?", tx("Membuat repository Git baru di:\n{}").replace("{}", dir.absolutePath), "Init") {
                Prefs.lastParent = dir.parentFile?.absolutePath ?: dir.absolutePath
                git("Init ${dir.name}", { GitEngine.initRepo(dir) }) {
                    RepoStore.add(RepoEntry(dir.name, dir.absolutePath))
                    refresh()
                }
            }
        }
    }

    private fun openDialog() {
        FolderPicker.pick(ctx, File(Prefs.lastParent), "Pilih folder repository", false) { dir ->
            if (File(dir, ".git").exists()) {
                Prefs.lastParent = dir.parentFile?.absolutePath ?: dir.absolutePath
                git("Buka ${dir.name}", {
                    withContext(Dispatchers.IO) { GitEngine.normalizeConfig(dir) }
                }) {
                    RepoStore.add(RepoEntry(dir.name, dir.absolutePath))
                    refresh()
                }
            } else {
                Dialogs.confirm(ctx, "Bukan repository", "Folder ini belum punya .git. Jalankan Init di sini?", "Init") {
                    git("Init ${dir.name}", { GitEngine.initRepo(dir) }) {
                        RepoStore.add(RepoEntry(dir.name, dir.absolutePath))
                        refresh()
                    }
                }
            }
        }
    }

    private fun removeDialog(r: RepoEntry) {
        Dialogs.confirm(ctx, "Hapus dari daftar?", "${r.name}\n${r.path}\n\nFile repository di HP TIDAK dihapus.", "Hapus") {
            RepoStore.remove(r.path)
            refresh()
        }
    }
}
