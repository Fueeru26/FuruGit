package com.furugit.app.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.EditText
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.lifecycleScope
import android.widget.LinearLayout
import com.furugit.app.data.Account
import com.furugit.app.data.ActivityLog
import com.furugit.app.data.AuthStore
import com.furugit.app.data.DeviceFlow
import com.furugit.app.data.GitHubApi
import com.furugit.app.data.Prefs
import com.furugit.app.git.friendly
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.launch

class GithubSettingsFragment : BaseFragment() {

    override fun refresh() {
        content.removeAllViews()

        content.title("Izin penyimpanan")
        if (StorageAccess.hasAccess(ctx)) {
            content.label("✔ Akses semua file aktif")
        } else {
            content.label("✘ Belum diberi izin. Tanpa ini FuruGit tidak bisa membuka folder repo.")
            content.button("Beri izin") { StorageAccess.request(requireActivity()) }
        }

        content.title("Identitas Git")
        content.hint("Dipakai sebagai author/committer pada setiap commit.")
        val etName = content.input("Nama", Prefs.gitName)
        val etEmail = content.input("Email", Prefs.gitEmail)
        content.button("Simpan identitas") {
            sure("Simpan identitas?", "Nama dan email ini dipakai pada setiap commit berikutnya.", "Simpan") {
                Prefs.gitName = etName.text.toString().trim()
                Prefs.gitEmail = etEmail.text.toString().trim()
                Dialogs.toast(ctx, "Tersimpan")
            }
        }

        content.title("Akun & autentikasi")
        val accs: List<Account> = try {
            AuthStore.accounts()
        } catch (e: Exception) {
            content.label(tx("Gagal membuka penyimpanan aman: {}").replace("{}", e.message ?: ""))
            emptyList()
        }
        if (accs.isEmpty()) content.hint("Belum ada akun.")
        for (a in accs) {
            content.card(onLong = { removeAccount(a) }) {
                label(a.host, 15f, bold = true)
                hint(tx("{} · {} · token tersimpan terenkripsi").replaceFirst("{}", a.username).replaceFirst("{}", a.type))
                button("Hapus", true) { removeAccount(a) }
            }
        }
        content.button("Tambah Personal Access Token") { patDialog() }
        content.space(12)
        content.hint("Login GitHub (OAuth Device Flow) butuh Client ID dari GitHub App / OAuth App milikmu dengan \"Device Flow\" diaktifkan. Jangan memasukkan client secret.")
        val etCid = content.input("GitHub Client ID", Prefs.clientId)
        etCid.onChange { Prefs.clientId = it.trim() }
        content.button("Login GitHub (OAuth)", true) {
            sure("Mulai login GitHub?", "Kode perangkat akan dibuat dan kamu diminta menyetujuinya di GitHub.", "Mulai") { oauth() }
        }
    }

    private fun removeAccount(a: Account) {
        Dialogs.confirm(ctx, "Hapus akun?", a.host + " (" + a.username + ")", "Hapus") {
            AuthStore.remove(a.host)
            refresh()
        }
    }

    private fun patDialog() {
        lateinit var etHost: EditText
        lateinit var etUser: EditText
        lateinit var etToken: EditText
        Dialogs.form(ctx, "Personal Access Token", "Simpan", { box ->
            box.hint("Fine-grained PAT: izin Contents (Read and write) + Metadata (Read). Diperlakukan seperti password.")
            etHost = box.input("Host", "github.com")
            etUser = box.input("Username (opsional untuk GitHub)")
            etToken = box.input("Token", password = true)
        }) {
            val host = etHost.text.toString().trim().ifEmpty { "github.com" }
            val user = etUser.text.toString().trim()
            val token = etToken.text.toString().trim()
            if (token.isEmpty()) {
                Dialogs.toast(ctx, "Token kosong")
            } else if (host.equals("github.com", true)) {
                git("Validasi token GitHub", { GitHubApi.username(token) }) { login ->
                    AuthStore.save(Account(host, login, token, "PAT"))
                    refresh()
                }
            } else {
                AuthStore.save(Account(host, user.ifEmpty { "git" }, token, "PAT"))
                ActivityLog.add("Akun $host disimpan")
                refresh()
            }
        }
    }

    private fun oauth() {
        val cid = Prefs.clientId.trim()
        if (cid.isEmpty()) {
            Dialogs.info(ctx, "Client ID kosong", "Isi GitHub Client ID dulu, atau pakai Personal Access Token.")
            return
        }
        val act = requireActivity() as MainActivity
        act.lifecycleScope.launch {
            act.setBusy(true)
            var dlg: AlertDialog? = null
            try {
                val self = currentCoroutineContext()[Job]
                val code = DeviceFlow.requestCode(cid)
                dlg = Dialogs.builder(act)
                    .setTitle(tx("Login GitHub"))
                    .setMessage(tx("Buka {} lalu masukkan kode:\n\n{}\n\nMenunggu persetujuan…").replaceFirst("{}", code.verificationUri).replaceFirst("{}", code.userCode))
                    .setPositiveButton(tx("Salin kode & buka GitHub"), null)
                    .setNegativeButton(tx("Batal"), null)
                    .setCancelable(false)
                    .create()
                dlg.setOnDismissListener { self?.cancel() }
                dlg.show()
                dlg.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                    val cm = act.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    cm.setPrimaryClip(ClipData.newPlainText("kode", code.userCode))
                    act.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(code.verificationUri)))
                }
                val token = DeviceFlow.awaitToken(cid, code)
                val user = GitHubApi.username(token)
                AuthStore.save(Account("github.com", user, token, "OAuth"))
                ActivityLog.add("✔ Login GitHub sebagai $user")
                if (isAdded && view != null) refresh()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                ActivityLog.add("✘ Login GitHub: " + e.friendly())
                if (isAdded) Dialogs.info(act, "Login gagal", tx(e.friendly()))
            } finally {
                dlg?.dismiss()
                act.setBusy(false)
            }
        }
    }
}

class LogFragment : BaseFragment() {
    override fun refresh() {
        content.removeAllViews()
        content.hrow {
            button("Salin log", true) {
                val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                cm.setPrimaryClip(ClipData.newPlainText("log", ActivityLog.all().joinToString("\n")))
                Dialogs.toast(ctx, "Log disalin")
            }
            button("Bersihkan", true) {
                sure("Bersihkan log?", "Semua catatan aktivitas dihapus.", "Bersihkan") {
                    ActivityLog.clear()
                    refresh()
                }
            }
        }
        content.space(8)
        val lines = ActivityLog.all()
        if (lines.isEmpty()) content.hint("Belum ada aktivitas.")
        else content.label(lines.reversed().joinToString("\n") { tx(it) }, 12f, mono = true)
    }
}
