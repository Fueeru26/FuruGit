package com.furugit.app.ui

import android.content.Intent
import android.net.Uri
import android.widget.CheckBox
import android.widget.LinearLayout
import androidx.lifecycle.lifecycleScope
import com.furugit.app.R
import com.furugit.app.data.ActionsApi
import com.furugit.app.data.GhRepo
import com.furugit.app.data.OpResult
import com.furugit.app.data.RepoEntry
import com.furugit.app.data.WorkflowRun
import com.furugit.app.git.GitEngine
import com.furugit.app.git.friendly
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.io.File

/**
 * Tab Aksi (sesuai tab Actions di GitHub): daftar run workflow repo aktif langsung dari GitHub, jadi run atau
 * workflow yang dihapus di GitHub ikut hilang dari daftar saat dimuat ulang. Dari sini juga bisa menjalankan
 * ulang, membuka, dan menghapus run / workflow.
 */
class ActionsFragment : BaseFragment() {
    override val tabPage: Boolean get() = true

    private var filter: String? = null
    private var filterIsTag = false

    override fun refresh() {
        content.removeAllViews()
        val r = needRepo() ?: return
        content.label(r.name, 16f, bold = true)
        content.button(tr("Jalankan Aksi", "Run Actions")) {
            (requireActivity() as MainActivity).push(RunActionsFragment(), tr("Jalankan Aksi", "Run Actions"))
        }
        content.hrow {
            button((if (filterIsTag) "Tag: " else "Branch: ") + (filter ?: tr("Semua", "All")), true) { pickBranch(r) }
            button("Tags", true) { pickTag(r) }
        }
        val list = LinearLayout(ctx)
        list.orientation = LinearLayout.VERTICAL
        list.clipChildren = false
        content.addView(list, LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = ctx.dp(16) })
        list.hint(tr("Memuat daftar aksi…", "Loading actions…"))
        load(r, list)
    }

    private fun load(r: RepoEntry, list: LinearLayout) {
        val act = requireActivity() as MainActivity
        viewLifecycleOwner.lifecycleScope.launch {
            act.setBusy(true)
            try {
                val gh = ActionsApi.refFor(GitEngine.remoteUrl(r.path))
                val runs = ActionsApi.runs(gh, filter)
                if (view == null) return@launch
                list.removeAllViews()
                if (runs.isEmpty()) list.hint(tr("Belum ada run aksi di GitHub untuk filter ini.", "No action runs on GitHub for this filter yet."))
                for (run in runs) runCard(r, gh, list, run)
                if (runs.isNotEmpty()) list.hint(tr("Menampilkan maksimal 30 run terbaru. Ketuk run untuk jalankan ulang, buka, atau hapus.", "Showing the 30 latest runs. Tap a run to re-run, open or delete it."))
                list.button(tr("Riwayat commit", "Commit history"), true) {
                    (requireActivity() as MainActivity).push(HistoryFragment(), tr("Riwayat commit", "Commit history"))
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                if (view != null) {
                    list.removeAllViews()
                    list.hint(e.friendly())
                    list.button(tr("Coba lagi", "Retry")) { refresh() }
                    list.button(tr("Riwayat commit", "Commit history"), true) {
                        (requireActivity() as MainActivity).push(HistoryFragment(), tr("Riwayat commit", "Commit history"))
                    }
                }
            } finally {
                act.setBusy(false)
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
            run.status == "in_progress" -> Badge(ctx, tr("Berjalan", "Running"), state = Badge.WARN)
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
        val items = listOf(
            tr("Jalankan ulang seluruh run", "Re-run the whole run"),
            tr("Lihat job / jalankan ulang satu job", "View jobs / re-run one job"),
            tr("Buka di GitHub", "Open on GitHub"),
            tr("Hapus run ini", "Delete this run"),
            tr("Hapus workflow", "Delete workflow")
        )
        Dialogs.choose(ctx, run.workflow + " #" + run.number, items) {
            when (it) {
                0 -> sure(tr("Jalankan ulang?", "Re-run?"), tr("Seluruh job pada run #${run.number} dijalankan ulang di GitHub, tanpa commit baru.", "All jobs of run #${run.number} are re-run on GitHub, with no new commit."), tr("Jalankan ulang", "Re-run")) {
                    git("Jalankan ulang ${run.workflow} #${run.number}", { ActionsApi.rerun(gh, run.id) }) { refresh() }
                }
                1 -> jobsDialog(gh, run)
                2 -> startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(run.url)))
                3 -> sure(tr("Hapus run?", "Delete run?"), tr("Run #${run.number} dihapus dari GitHub (tidak bisa dibatalkan).", "Run #${run.number} is deleted from GitHub (cannot be undone)."), tr("Hapus", "Delete")) {
                    git("Hapus run #${run.number}", { ActionsApi.deleteRun(gh, run.id) }) { refresh() }
                }
                else -> deleteWorkflow(r, gh, run)
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
                        git("Jalankan ulang job ${j.name}", { ActionsApi.rerunJob(gh, j.id) }) { refresh() }
                    }
                }
            }
        }
    }

    /**
     * Hapus workflow = hapus semua run-nya di GitHub (GitHub tidak punya API untuk menghapus workflow itu sendiri).
     * Opsional ikut menghapus file .yml di folder repo lokal; setelah itu commit & push agar workflow hilang dari GitHub.
     */
    private fun deleteWorkflow(r: RepoEntry, gh: GhRepo, run: WorkflowRun) {
        lateinit var cb: CheckBox
        val localFile = if (run.path.isNotBlank()) File(r.path, run.path) else null
        Dialogs.form(ctx, tr("Hapus workflow?", "Delete workflow?"), tr("Hapus", "Delete"), { box ->
            box.label(
                tr(
                    "Semua run workflow \"${run.workflow}\" dihapus dari GitHub (run yang masih berjalan dilewati). Tidak bisa dibatalkan.",
                    "All runs of the workflow \"${run.workflow}\" are deleted from GitHub (runs still in progress are skipped). This cannot be undone."
                )
            )
            cb = CheckBox(ctx)
            cb.text = tr("Hapus juga file ${run.path} di repo lokal", "Also delete the file ${run.path} in the local repo")
            cb.isEnabled = localFile != null && localFile.isFile
            box.addView(cb, LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = ctx.dp(12) })
        }) {
            val alsoFile = cb.isChecked
            git("Hapus workflow ${run.workflow}", {
                val (deleted, skipped) = ActionsApi.deleteWorkflowRuns(gh, run.workflowId)
                val sb = StringBuilder(tr("$deleted run dihapus dari GitHub.", "$deleted runs deleted from GitHub."))
                if (skipped > 0) sb.append("\n").append(tr("$skipped run masih berjalan dan dilewati.", "$skipped runs still in progress were skipped."))
                if (alsoFile && localFile != null && localFile.isFile) {
                    if (localFile.delete()) {
                        sb.append("\n").append(tr("File ${run.path} dihapus dari repo lokal. Commit & push agar workflow hilang dari GitHub.", "File ${run.path} deleted from the local repo. Commit & push so the workflow disappears from GitHub."))
                    }
                }
                OpResult(true, sb.toString())
            }) { res ->
                showResult(res)
                refresh()
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
