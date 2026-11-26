package com.furugit.app.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.LinearLayout
import androidx.lifecycle.lifecycleScope
import com.furugit.app.R
import com.furugit.app.data.ActionsApi
import com.furugit.app.data.DlItem
import com.furugit.app.data.GhRepo
import com.furugit.app.data.OpResult
import com.furugit.app.data.RunWatcher
import com.furugit.app.data.fmtBytes
import com.furugit.app.data.safeFileName
import com.furugit.app.data.RepoEntry
import com.furugit.app.data.WorkflowRun
import com.furugit.app.git.GitEngine
import com.furugit.app.git.friendly
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Tab Aksi (sesuai tab Actions di GitHub): daftar run workflow repo aktif langsung dari GitHub, jadi run atau
 * workflow yang dihapus di GitHub ikut hilang dari daftar saat dimuat ulang. Dari sini juga bisa menjalankan
 * ulang, membuka, dan menghapus run.
 */
class ActionsFragment : BaseFragment() {
    override val tabPage: Boolean get() = true

    private var filter: String? = null
    private var filterIsTag = false
    private var statusFilter: String? = null
    private var pollJob: Job? = null
    private val wasActive = HashMap<Long, Boolean>()

    override fun refresh() {
        content.removeAllViews()
        pollJob?.cancel()
        val r = needRepo() ?: return
        content.label(r.name, 16f, bold = true)
        content.button(tr("Jalankan Aksi", "Run Actions")) {
            (requireActivity() as MainActivity).push(RunActionsFragment(), tr("Jalankan Aksi", "Run Actions"))
        }
        content.hrow {
            button((if (filterIsTag) "Tag: " else "Branch: ") + (filter ?: tr("Semua", "All")), true) { pickBranch(r) }
            button("Tags", true) { pickTag(r) }
        }
        val statusCodes = listOf<String?>(null, "in_progress", "success", "failure", "cancelled")
        val statusNames = listOf(
            tr("Status: Semua", "Status: All"), tr("Status: Berjalan", "Status: Running"), tr("Status: Berhasil", "Status: Success"),
            tr("Status: Gagal", "Status: Failed"), tr("Status: Dibatalkan", "Status: Cancelled")
        )
        content.dropdown(statusNames, statusCodes.indexOf(statusFilter).coerceAtLeast(0)) { i ->
            statusFilter = statusCodes[i]
            refresh()
        }
        val list = LinearLayout(ctx)
        list.orientation = LinearLayout.VERTICAL
        list.clipChildren = false
        content.addView(list, LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = ctx.dp(16) })
        list.hint(tr("Memuat daftar aksi…", "Loading actions…"))
        load(r, list)
    }

    private fun load(r: RepoEntry, list: LinearLayout, quiet: Boolean = false) {
        val act = requireActivity() as MainActivity
        viewLifecycleOwner.lifecycleScope.launch {
            if (!quiet) act.setBusy(true)
            try {
                val gh = ActionsApi.refFor(GitEngine.remoteUrl(r.path))
                val runs = ActionsApi.runs(gh, filter, status = statusFilter)
                if (view == null) return@launch
                // beri tahu bila run yang tadi berjalan sekarang sudah selesai
                for (run in runs) {
                    if (wasActive[run.id] == true && run.status == "completed") {
                        val res = when (run.conclusion) {
                            "success" -> tr("berhasil", "succeeded")
                            "cancelled" -> tr("dibatalkan", "cancelled")
                            else -> tr("gagal", "failed")
                        }
                        Dialogs.toast(ctx, run.workflow + " #" + run.number + " " + res)
                    }
                    wasActive[run.id] = run.status != "completed"
                }
                list.removeAllViews()
                if (runs.isEmpty()) list.hint(tr("Belum ada run aksi di GitHub untuk filter ini.", "No action runs on GitHub for this filter yet."))
                for (run in runs) runCard(r, gh, list, run)
                if (runs.isNotEmpty()) {
                    list.hint(tr("Menampilkan maksimal 30 run terbaru. Ketuk run untuk jalankan ulang, unduh, buka, atau hapus.", "Showing the 30 latest runs. Tap a run to re-run, download, open or delete it."))
                    list.button(tr("Hapus massal run", "Bulk delete runs"), true) { bulkDelete(gh) }
                }
                list.button(tr("Riwayat commit", "Commit history"), true) {
                    (requireActivity() as MainActivity).push(HistoryFragment(), tr("Riwayat commit", "Commit history"))
                }
                // run yang sedang berjalan: muat ulang otomatis tiap 8 detik, dan dipantau untuk notifikasi
                pollJob?.cancel()
                if (runs.any { it.status != "completed" }) {
                    RunWatcher.kick(gh)
                    pollJob = viewLifecycleOwner.lifecycleScope.launch {
                        while (view != null) {
                            delay(8000L)
                            if (isResumed) {
                                load(r, list, true)
                                break
                            }
                        }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                if (view != null && !quiet) {
                    list.removeAllViews()
                    list.hint(e.friendly())
                    list.button(tr("Coba lagi", "Retry")) { refresh() }
                    list.button(tr("Riwayat commit", "Commit history"), true) {
                        (requireActivity() as MainActivity).push(HistoryFragment(), tr("Riwayat commit", "Commit history"))
                    }
                }
            } finally {
                if (!quiet) act.setBusy(false)
            }
        }
    }

    /** Hapus massal: pilih kriteria, tampilkan jumlah yang cocok, lalu hapus dari GitHub (hanya run yang sudah selesai). */
    private fun bulkDelete(gh: GhRepo) {
        val items = listOf(
            tr("Hapus semua run yang gagal", "Delete all failed runs"),
            tr("Hapus run lebih lama dari 7 hari", "Delete runs older than 7 days"),
            tr("Hapus run lebih lama dari 30 hari", "Delete runs older than 30 days"),
            tr("Hapus semua run yang sudah selesai", "Delete all finished runs")
        )
        Dialogs.choose(ctx, tr("Hapus massal run", "Bulk delete runs"), items) { which ->
            val day = 24L * 60 * 60 * 1000
            val now = System.currentTimeMillis()
            val pred: (WorkflowRun) -> Boolean = { run ->
                run.status == "completed" && when (which) {
                    0 -> run.conclusion == "failure" || run.conclusion == "timed_out" || run.conclusion == "startup_failure"
                    1 -> now - run.time > 7 * day
                    2 -> now - run.time > 30 * day
                    else -> true
                }
            }
            gitQuiet("Memeriksa run", { ActionsApi.runsAll(gh, filter).filter(pred) }) { found ->
                if (found.isEmpty()) {
                    Dialogs.info(ctx, tr("Hapus massal run", "Bulk delete runs"), tr("Tidak ada run yang cocok.", "No matching runs."))
                } else {
                    val scopeId = if (filter != null) " pada filter ini" else ""
                    val scopeEn = if (filter != null) " in this filter" else ""
                    sure(
                        tr("Hapus ${found.size} run?", "Delete ${found.size} runs?"),
                        tr(found.size.toString() + " run" + scopeId + " dihapus dari GitHub (tidak bisa dibatalkan).", found.size.toString() + " runs" + scopeEn + " are deleted from GitHub (cannot be undone)."),
                        tr("Hapus", "Delete")
                    ) {
                        git("Hapus ${found.size} run", {
                            var n = 0
                            for (run in found) {
                                ActionsApi.deleteRun(gh, run.id)
                                n++
                            }
                            OpResult(true, tr("$n run dihapus dari GitHub.", "$n runs deleted from GitHub."))
                        }) { res ->
                            showResult(res)
                            refresh()
                        }
                    }
                }
            }
        }
    }

    private fun runCard(r: RepoEntry, gh: GhRepo, list: LinearLayout, run: WorkflowRun) {
        val badge = when {
            run.status == "completed" -> when (run.conclusion) {
                "success" -> Badge(ctx, tr("Berhasil", "Success"), state = Badge.OK)
                "failure", "timed_out", "startup_failure" -> Badge(ctx, tr("Gagal", "Failed"), state = Badge.FAIL)
                "cancelled" -> Badge(ctx, tr("Dibatalkan", "Cancelled"), state = Badge.MUTED)
                else -> Badge(ctx, tr("Selesai", "Done"), state = Badge.MUTED)
            }
            run.status == "in_progress" -> Badge(ctx, tr("Berjalan", "Running"), state = Badge.RUN)
            else -> Badge(ctx, tr("Antre", "Queued"), state = Badge.WARN)
        }
        list.rowCard(
            R.drawable.ic_actions, run.title,
            run.workflow + " #" + run.number + " · " + run.branch + " · " + fmtTime(run.time),
            onClick = { runActions(r, gh, run) },
            badge = badge
        )
    }

    private fun runActions(r: RepoEntry, gh: GhRepo, run: WorkflowRun) {
        val active = run.status != "completed"
        val entries = ArrayList<Pair<String, () -> Unit>>()
        if (active) {
            entries.add(tr("Batalkan run", "Cancel run") to {
                sure(tr("Batalkan run?", "Cancel run?"), tr("Run #${run.number} dihentikan di GitHub.", "Run #${run.number} is stopped on GitHub."), tr("Batalkan run", "Cancel run")) {
                    git("Batalkan run #${run.number}", { ActionsApi.cancel(gh, run.id) }) { refresh() }
                }
            })
        } else {
            entries.add(tr("Jalankan ulang seluruh run", "Re-run the whole run") to {
                sure(tr("Jalankan ulang?", "Re-run?"), tr("Seluruh job pada run #${run.number} dijalankan ulang di GitHub, tanpa commit baru.", "All jobs of run #${run.number} are re-run on GitHub, with no new commit."), tr("Jalankan ulang", "Re-run")) {
                    git("Jalankan ulang ${run.workflow} #${run.number}", {
                        ActionsApi.rerun(gh, run.id)
                        RunWatcher.kick(gh)
                    }) { refresh() }
                }
            })
        }
        entries.add(tr("Lihat job / jalankan ulang satu job", "View jobs / re-run one job") to { jobsDialog(gh, run) })
        entries.add(tr("Unduh Artefak", "Download Artifacts") to { artifactsDialog(gh, run) })
        entries.add(tr("Lihat / Unduh Log", "View / Download Logs") to { logsDialog(gh, run) })
        entries.add(tr("Buka di GitHub", "Open on GitHub") to { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(run.url))) })
        if (!active) {
            entries.add(tr("Hapus run ini", "Delete this run") to {
                sure(tr("Hapus run?", "Delete run?"), tr("Run #${run.number} dihapus dari GitHub (tidak bisa dibatalkan).", "Run #${run.number} is deleted from GitHub (cannot be undone)."), tr("Hapus", "Delete")) {
                    git("Hapus run #${run.number}", { ActionsApi.deleteRun(gh, run.id) }) { refresh() }
                }
            })
        }
        Dialogs.choose(ctx, run.workflow + " #" + run.number, entries.map { it.first }) { entries[it].second() }
    }

    private fun artifactsDialog(gh: GhRepo, run: WorkflowRun) {
        gitQuiet("Artefak ${run.workflow} #${run.number}", { ActionsApi.artifacts(gh, run.id).filter { !it.expired } }) { list ->
            if (list.isEmpty()) {
                Dialogs.info(ctx, tr("Unduh Artefak", "Download Artifacts"), tr("Run ini tidak punya artefak (atau sudah kedaluwarsa).", "This run has no artifacts (or they have expired)."))
            } else {
                DownloadUi.choose(
                    ctx, gh, tr("Unduh Artefak", "Download Artifacts"),
                    list.map { DownloadUi.Entry(it.name, fmtBytes(it.size), DlItem(it.name + ".zip", gh.base + "/actions/artifacts/" + it.id + "/zip", it.size)) }
                )
            }
        }
    }

    private fun logsDialog(gh: GhRepo, run: WorkflowRun) {
        val items = listOf(tr("Lihat log run", "View run log"), tr("Unduh log run", "Download run log"))
        Dialogs.choose(ctx, tr("Lihat / Unduh Log", "View / Download Logs"), items) { which ->
            if (which == 0) {
                val f = RunLogFragment()
                f.arguments = Bundle().apply { putLong("run", run.id) }
                (requireActivity() as MainActivity).push(f, tr("Log", "Log") + " " + run.workflow + " #" + run.number)
            } else {
                gitQuiet("Log ${run.workflow} #${run.number}", { ActionsApi.jobs(gh, run.id) }) { jobs ->
                    if (jobs.isEmpty()) {
                        Dialogs.info(ctx, tr("Unduh log run", "Download run log"), tr("Run ini tidak punya job.", "This run has no jobs."))
                    } else {
                        DownloadUi.choose(
                            ctx, gh, tr("Unduh log run", "Download run log"),
                            jobs.map { DownloadUi.Entry(safeFileName(it.name) + ".log", it.conclusion ?: it.status, DlItem(safeFileName(it.name) + ".log", gh.base + "/actions/jobs/" + it.id + "/logs", 0L)) }
                        )
                    }
                }
            }
        }
    }

    private fun jobsDialog(gh: GhRepo, run: WorkflowRun) {
        gitQuiet("Job ${run.workflow} #${run.number}", { ActionsApi.jobs(gh, run.id) }) { jobs ->
            if (jobs.isEmpty()) {
                Dialogs.info(ctx, "Job", tr("Run ini tidak punya job.", "This run has no jobs."))
            } else {
                val names = jobs.map { j -> j.name + " — " + (j.conclusion ?: j.status) }
                Dialogs.choose(ctx, tr("Pilih job untuk dijalankan ulang", "Pick a job to re-run"), names) { i ->
                    val j = jobs[i]
                    sure(tr("Jalankan ulang job?", "Re-run job?"), j.name, tr("Jalankan ulang", "Re-run")) {
                        git("Jalankan ulang job ${j.name}", {
                            ActionsApi.rerunJob(gh, j.id)
                            RunWatcher.kick(gh)
                        }) { refresh() }
                    }
                }
            }
        }
    }

    private fun pickBranch(r: RepoEntry) {
        gitQuiet("Daftar branch", { GitEngine.use(r.path) { g -> GitEngine.branches(g).filter { !it.isRemote }.map { it.name } } }) { bs ->
            val names = listOf(tr("Semua", "All")) + bs
            Dialogs.choose(ctx, tr("Filter branch", "Branch filter"), names) {
                filter = if (it == 0) null else names[it]
                filterIsTag = false
                refresh()
            }
        }
    }

    private fun pickTag(r: RepoEntry) {
        gitQuiet("Tags", { GitEngine.use(r.path) { GitEngine.tags(it) } }) { tags ->
            if (tags.isEmpty()) {
                Dialogs.info(ctx, "Tags", tr("Belum ada tag di repo ini.", "There are no tags in this repo yet."))
            } else {
                Dialogs.choose(ctx, tr("Filter tag", "Tag filter"), tags) {
                    filter = tags[it]
                    filterIsTag = true
                    refresh()
                }
            }
        }
    }
}
