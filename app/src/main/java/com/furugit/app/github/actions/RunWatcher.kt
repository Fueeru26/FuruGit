package com.furugit.app.github.actions

import com.furugit.app.App
import com.furugit.app.core.common.L
import com.furugit.app.core.common.Notifier
import com.furugit.app.core.common.tr
import com.furugit.app.data.preferences.Prefs
import com.furugit.app.github.artifacts.Downloads
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Memantau run GitHub Actions yang sedang berjalan dan mengirim notifikasi saat selesai, walau layar Aksi sudah
 * ditinggalkan. Memeriksa tiap 15 detik selama ada run berjalan (maksimal 1 jam). Hanya aktif bila "Aktifkan Notifikasi"
 * menyala; saat aplikasi sedang dibuka cukup toast di tab Aksi, jadi notifikasi tidak dikirim. Berjalan di dalam proses
 * aplikasi, jadi berhenti bila Android mematikan prosesnya.
 */
object RunWatcher {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var loop: Job? = null
    private var gh: GhRepo? = null
    private var lastKick = 0L
    private val known = HashMap<Long, WorkflowRun>()

    /** Dipanggil setelah ada run yang dimulai atau terlihat berjalan. */
    fun kick(g: GhRepo) {
        if (!Prefs.downloadNotify) return
        gh = g
        lastKick = System.currentTimeMillis()
        if (loop?.isActive == true) return
        loop = scope.launch {
            while (true) {
                delay(15_000L)
                val repo = gh ?: break
                val now = System.currentTimeMillis()
                try {
                    val runs = ActionsApi.runs(repo, null, 20)
                    for (r in runs) if (r.status != "completed") known[r.id] = r
                    val byId = runs.associateBy { it.id }
                    val iter = known.entries.iterator()
                    while (iter.hasNext()) {
                        val e = iter.next()
                        val cur = byId[e.key]
                        if (cur == null) {
                            iter.remove()
                        } else if (cur.status == "completed") {
                            iter.remove()
                            done(cur)
                        }
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Throwable) {
                    // jaringan putus sementara: coba lagi di putaran berikutnya
                }
                if (known.isEmpty() && now - lastKick > 90_000L) break
                if (now - lastKick > 3_600_000L) break
            }
            known.clear()
        }
    }

    private fun done(r: WorkflowRun) {
        if (Downloads.host?.get() != null) return
        val res = when (r.conclusion) {
            "success" -> tr("berhasil", "succeeded")
            "cancelled" -> tr("dibatalkan", "cancelled")
            else -> tr("gagal", "failed")
        }
        Notifier.runDone(com.furugit.app.App.instance, r.workflow + " #" + r.number + " " + res, r.title, r.id.toInt())
    }
}
