package com.furugit.app.ui.settings

import android.Manifest
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.net.Uri
import android.os.Build
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.widget.SwitchCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.furugit.app.R
import com.furugit.app.core.common.GitUserError
import com.furugit.app.core.common.L
import com.furugit.app.core.common.friendly
import com.furugit.app.core.common.tr
import com.furugit.app.data.backup.Backup
import com.furugit.app.data.preferences.Prefs
import com.furugit.app.ui.components.BaseFragment
import com.furugit.app.ui.components.DBtn
import com.furugit.app.ui.components.Dialogs
import com.furugit.app.ui.components.FolderPicker
import com.furugit.app.ui.components.Fonts
import com.furugit.app.ui.components.MATCH
import com.furugit.app.ui.components.Pal
import com.furugit.app.ui.components.ResultMark
import com.furugit.app.ui.components.WRAP
import com.furugit.app.ui.components.button
import com.furugit.app.ui.components.card
import com.furugit.app.ui.components.dp
import com.furugit.app.ui.components.dropdown
import com.furugit.app.ui.components.gap
import com.furugit.app.ui.components.hint
import com.furugit.app.ui.components.input
import com.furugit.app.ui.components.label
import com.furugit.app.ui.components.onChange
import com.furugit.app.ui.components.sectionLabel
import com.furugit.app.ui.components.title
import com.furugit.app.ui.components.vbox
import com.furugit.app.ui.components.withAlpha
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// ───────────────────────── Pengaturan Aplikasi ─────────────────────────

/** Baris pengaturan dengan judul di kiri dan saklar di kanan. */
private fun LinearLayout.switchRow(text: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    val pal = Pal.of(context)
    val row = LinearLayout(context)
    row.orientation = LinearLayout.HORIZONTAL
    row.gravity = Gravity.CENTER_VERTICAL
    val t = TextView(context)
    t.text = text
    t.textSize = 16f
    t.typeface = Fonts.semibold(context)
    t.setTextColor(pal.text)
    row.addView(t, LinearLayout.LayoutParams(0, WRAP, 1f))
    val sw = SwitchCompat(context)
    val states = arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf())
    sw.thumbTintList = ColorStateList(states, intArrayOf(pal.accent, pal.muted))
    sw.trackTintList = ColorStateList(states, intArrayOf(pal.accent.withAlpha(110), pal.disabledBg))
    sw.isChecked = checked
    sw.setOnCheckedChangeListener { _, on -> onChange(on) }
    row.addView(sw, LinearLayout.LayoutParams(WRAP, WRAP))
    addView(row, LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = gap(16) })
}

class AppSettingsFragment : BaseFragment() {

    private var pendingPassword = ""

    private val createDoc = registerForActivityResult(ActivityResultContracts.CreateDocument()) { uri ->
        if (uri != null) doExport(uri)
    }

    private val openDoc = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) askImportPassword(uri)
    }

    /** Ekspor: minta sandi (file berisi token GitHub), lalu pilih lokasi simpan lewat pemilih berkas sistem. */
    private fun startExport() {
        val box = vbox(ctx, 0)
        box.hint(tr("File cadangan berisi token GitHub, jadi dilindungi sandi. Ingat sandinya: tanpa sandi file tidak bisa dibuka.", "The backup contains your GitHub tokens, so it is protected by a password. Remember it: the file cannot be opened without it."))
        val p1 = box.input(tr("Sandi", "Password"), password = true)
        val p2 = box.input(tr("Ulangi sandi", "Repeat password"), password = true)
        Dialogs.show(
            ctx, tr("Ekspor Data Aplikasi", "Export App Data"), null, box,
            listOf(
                DBtn(tr("Batal", "Cancel")),
                DBtn(tr("Lanjut", "Next"), true, keepOpen = true) { dlg ->
                    val a = p1.text.toString()
                    val b = p2.text.toString()
                    if (a.length < 4) {
                        Dialogs.toast(ctx, tr("Sandi minimal 4 karakter.", "The password needs at least 4 characters."))
                    } else if (a != b) {
                        Dialogs.toast(ctx, tr("Kedua sandi tidak sama.", "The passwords do not match."))
                    } else {
                        pendingPassword = a
                        dlg.dismiss()
                        createDoc.launch("FuruGit-Backup-" + SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(java.util.Date()) + ".fgb")
                    }
                }
            )
        )
    }

    private fun doExport(uri: Uri) {
        val c = ctx
        val password = pendingPassword
        pendingPassword = ""
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val bytes = Backup.encrypt(Backup.build().toByteArray(), password.toCharArray())
                    val out = c.contentResolver.openOutputStream(uri, "wt") ?: throw GitUserError("File tujuan tidak bisa ditulis.")
                    out.use { it.write(bytes) }
                }
                Dialogs.result(c, true, tr("Data aplikasi berhasil diekspor. Simpan file dan sandinya di tempat aman.", "App data exported. Keep the file and its password somewhere safe."))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                Dialogs.result(c, false, e.friendly())
            }
        }
    }

    private fun startImport() {
        Dialogs.confirm(
            ctx, tr("Impor Data Aplikasi?", "Import App Data?"),
            tr("Pengaturan, daftar repository, dan akun GitHub saat ini akan DIGANTI dengan isi file cadangan.", "Your current settings, repository list and GitHub accounts will be REPLACED by the backup file."),
            tr("Pilih File", "Choose File")
        ) { openDoc.launch(arrayOf("*/*")) }
    }

    private fun askImportPassword(uri: Uri) {
        val box = vbox(ctx, 0)
        val pw = box.input(tr("Sandi", "Password"), password = true)
        Dialogs.show(
            ctx, tr("Sandi Cadangan", "Backup Password"), null, box,
            listOf(
                DBtn(tr("Batal", "Cancel")),
                DBtn(tr("Impor", "Import"), true, keepOpen = true) { dlg ->
                    val s = pw.text.toString()
                    if (s.isNotEmpty()) {
                        dlg.dismiss()
                        doImport(uri, s)
                    }
                }
            )
        )
    }

    private fun doImport(uri: Uri, password: String) {
        val c = ctx
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val bytes = c.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: throw GitUserError("File tidak bisa dibaca.")
                    Backup.apply(String(Backup.decrypt(bytes, password.toCharArray())))
                }
                L.reload()
                AppCompatDelegate.setDefaultNightMode(
                    when (Prefs.themeMode) {
                        1 -> AppCompatDelegate.MODE_NIGHT_NO
                        2 -> AppCompatDelegate.MODE_NIGHT_YES
                        else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
                    }
                )
                val box = LinearLayout(c)
                box.orientation = LinearLayout.VERTICAL
                box.gravity = Gravity.CENTER_HORIZONTAL
                box.addView(ResultMark(c, true), LinearLayout.LayoutParams(c.dp(88), c.dp(88)))
                val tv = TextView(c)
                tv.text = tr("Data aplikasi berhasil diimpor. Aplikasi dimuat ulang untuk menerapkan pengaturan.", "App data imported. The app reloads to apply the settings.")
                tv.textSize = 16f
                tv.typeface = Fonts.semibold(c)
                tv.gravity = Gravity.CENTER
                tv.setTextColor(Pal.of(c).text)
                box.addView(tv, LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = c.dp(16) })
                Dialogs.show(c, null, null, box, listOf(DBtn(tr("Tutup", "Close"), true) { activity?.recreate() }), cancelable = false)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                Dialogs.result(c, false, e.friendly())
            }
        }
    }

    private val notifPerm = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        Prefs.downloadNotify = granted
        if (!granted) Dialogs.toast(requireContext(), tr("Izin notifikasi ditolak.", "Notification permission denied."))
        if (isAdded && view != null) refresh()
    }

    override fun refresh() {
        content.removeAllViews()

        content.title(tr("Pengaturan Tampilan", "Appearance"))
        content.card {
            sectionLabel(tr("Mode Tema", "Theme Mode"))
            dropdown(
                listOf(tr("Ikuti Sistem", "Follow System"), tr("Terang", "Light"), tr("Gelap", "Dark")),
                Prefs.themeMode
            ) { i ->
                Prefs.themeMode = i
                AppCompatDelegate.setDefaultNightMode(
                    when (i) {
                        1 -> AppCompatDelegate.MODE_NIGHT_NO
                        2 -> AppCompatDelegate.MODE_NIGHT_YES
                        else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
                    }
                )
            }
        }

        content.title(tr("Pengaturan Bahasa", "Language"))
        content.card {
            sectionLabel(tr("Pilih Bahasa", "Choose Language"))
            val codes = listOf(L.SYSTEM, L.EN, L.ID)
            dropdown(
                listOf(tr("Ikuti Sistem", "Follow System"), "English", "Indonesia"),
                codes.indexOf(Prefs.lang).coerceAtLeast(0)
            ) { i ->
                Prefs.lang = codes[i]
                L.reload()
                requireActivity().recreate()
            }
        }

        content.title(tr("Pengaturan Unduhan", "Download Settings"))
        content.card {
            val pal = Pal.of(context)
            sectionLabel(tr("Folder Output", "Output Folder"))
            label(
                if (Prefs.downloadDir.isBlank()) tr("Belum diatur", "Not set") else Prefs.downloadDir,
                14f, dim = Prefs.downloadDir.isBlank(), mono = Prefs.downloadDir.isNotBlank()
            )
            button(tr("Pilih Folder Output", "Choose Output Folder"), true) {
                val start = if (Prefs.downloadDir.isBlank()) File("/storage/emulated/0") else File(Prefs.downloadDir)
                FolderPicker.pick(ctx, start, "Pilih folder output", true) {
                    Prefs.downloadDir = it.absolutePath
                    refresh()
                }
            }
            switchRow(tr("Aktifkan Notifikasi", "Enable Notifications"), Prefs.downloadNotify) { on ->
                val needAsk = on && Build.VERSION.SDK_INT >= 33 &&
                    ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                if (needAsk) notifPerm.launch(Manifest.permission.POST_NOTIFICATIONS) else Prefs.downloadNotify = on
            }
            hint(tr("Menampilkan progres dan hasil unduhan artefak / log, serta pemberitahuan saat run Aksi selesai (ketika aplikasi sedang tidak dibuka).", "Shows download progress and results, and a notice when an Actions run finishes (while the app is not open)."))
            switchRow(tr("Ekstrak Artefak Otomatis", "Auto-extract Artifacts"), Prefs.downloadExtract) { on -> Prefs.downloadExtract = on }
            hint(tr("Artefak .zip diekstrak ke folder bernama artefak di folder output, lalu file .zip dihapus. APK di dalamnya bisa langsung diinstal setelah unduhan selesai.", "Artifact .zip files are extracted into a folder named after the artifact in the output folder, then the .zip is deleted. APKs inside can be installed right after the download."))
        }

        content.title(tr("Pengaturan Data", "Data Settings"))
        content.card {
            hint(tr("Cadangkan pengaturan, daftar repository, folder output, dan akun GitHub ke satu file (dilindungi sandi). Setelah aplikasi dihapus dan dipasang ulang, cukup impor file itu.", "Back up your settings, repository list, output folder and GitHub accounts into one file (password protected). After uninstalling and reinstalling the app, just import that file."))
            button(tr("Ekspor Data Aplikasi", "Export App Data")) { startExport() }
            button(tr("Impor Data Aplikasi", "Import App Data"), true) { startImport() }
        }
    }
}
