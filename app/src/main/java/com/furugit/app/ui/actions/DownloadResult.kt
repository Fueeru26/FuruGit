package com.furugit.app.ui.actions

import android.app.Activity
import com.furugit.app.core.common.friendly
import com.furugit.app.core.common.tr
import com.furugit.app.github.artifacts.ApkRef
import com.furugit.app.github.artifacts.DownloadTask
import com.furugit.app.github.artifacts.Downloads
import com.furugit.app.github.releases.Updater
import com.furugit.app.ui.components.DBtn
import com.furugit.app.ui.components.Dialogs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Jendela hasil unduhan (ceklis / silang) dan pemasangan APK hasil unduhan. */
object DownloadResult {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    /** Memasang jendela hasil ke [Downloads]; aman dipanggil berulang. */
    fun attach() {
        Downloads.onFinished = { act, t -> show(act, t) }
    }

    private fun show(act: Activity, t: DownloadTask) {
        when (t.state) {
            DownloadTask.OK -> Dialogs.result(
                act, true, t.message,
                if (t.apks.isEmpty()) null else DBtn(tr("Instal APK", "Install APK"), true) { installApk(act, t.apks) }
            )
            DownloadTask.FAIL -> Dialogs.result(act, false, t.message)
            else -> Dialogs.toast(act, t.message)
        }
    }

    /** Membuka installer sistem untuk APK hasil unduhan; bila ada beberapa, pengguna memilih dulu. */
    fun installApk(act: Activity, apks: List<ApkRef>) {
        if (apks.size == 1) {
            doInstall(act, apks[0])
        } else {
            Dialogs.choose(act, tr("Pilih APK", "Pick an APK"), apks.map { it.label }) { doInstall(act, apks[it]) }
        }
    }

    private fun doInstall(act: Activity, ref: ApkRef) {
        scope.launch {
            try {
                Updater.install(act, Downloads.prepareApk(act.cacheDir, ref))
            } catch (e: Throwable) {
                Dialogs.result(act, false, e.friendly())
            }
        }
    }
}
