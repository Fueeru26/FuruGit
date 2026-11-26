package com.furugit.app.ui

import android.widget.CheckBox
import android.widget.LinearLayout
import com.furugit.app.data.ActionsApi
import com.furugit.app.data.DispatchInput
import com.furugit.app.data.LocalJob
import com.furugit.app.data.LocalWorkflow
import com.furugit.app.data.OpResult
import com.furugit.app.data.RunWatcher
import com.furugit.app.data.RepoEntry
import com.furugit.app.git.GitEngine
import com.furugit.app.git.friendly
import kotlinx.coroutines.CancellationException

/**
 * Halaman "Jalankan Aksi": memindai file workflow (folder .github/workflows) di repo, menampilkan job-job di dalamnya
 * (mis. debug-apk, unit-tests) dengan centang, lalu menjalankan ulang yang dipilih di GitHub tanpa commit / push baru.
 */
class RunActionsFragment : BaseFragment() {

    private val selected = HashSet<String>()
    private val inputValues = HashMap<String, String>()

    override fun refresh() {
        content.removeAllViews()
        val r = needRepo() ?: return
        val wfs = ActionsApi.scanLocal(r.path)
        if (wfs.isEmpty()) {
            content.label(
                tr(
                    "Tidak ada file workflow di .github/workflows pada repo ini.",
                    "There are no workflow files in .github/workflows in this repo."
                )
            )
            return
        }
        content.hint(
            tr(
                "Centang job yang ingin dijalankan, lalu tekan Jalankan. Job dijalankan ulang dari run terakhir di GitHub, tanpa commit atau push baru.",
                "Tick the jobs to run, then press Run. Jobs are re-run from the latest run on GitHub, with no new commit or push."
            )
        )
        for (w in wfs) {
            content.title(w.name)
            content.card {
                label(w.file + if (w.dispatch) "  ·  workflow_dispatch" else "", 12f, dim = true)
                if (w.jobs.isEmpty()) hint(tr("Tidak ada job terdeteksi.", "No jobs detected."))
                for (j in w.jobs) {
                    val key = w.file + "::" + j.key
                    val cb = CheckBox(ctx)
                    cb.text = if (j.name == j.key) j.name else j.name + "  (" + j.key + ")"
                    cb.textSize = 16f
                    cb.typeface = Fonts.semibold(ctx)
                    cb.isChecked = key in selected
                    cb.setOnCheckedChangeListener { _, on -> if (on) selected.add(key) else selected.remove(key) }
                    addView(cb, LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = gap(6) })
                }
                if (w.dispatch) {
                    sectionLabel(tr("Jalankan baru dengan input (workflow_dispatch)", "Run anew with inputs (workflow_dispatch)"))
                    for (inp in w.inputs) inputControl(this, w, inp)
                    button(tr("Jalankan workflow ini", "Run this workflow"), true) { dispatchWorkflow(r, w) }
                }
            }
        }
        content.button(tr("Jalankan yang dipilih", "Run selected")) { runSelected(r, wfs) }
    }

    /** Kontrol satu input workflow_dispatch: centang (boolean), dropdown (choice), atau kolom teks. */
    private fun inputControl(box: LinearLayout, w: LocalWorkflow, inp: DispatchInput) {
        val key = w.file + "::" + inp.name
        if (key !in inputValues) {
            inputValues[key] = when {
                inp.default.isNotBlank() -> inp.default
                inp.type == "boolean" -> "false"
                inp.type == "choice" && inp.options.isNotEmpty() -> inp.options[0]
                else -> ""
            }
        }
        val title = inp.name + (if (inp.description.isNotBlank()) "  ·  " + inp.description else "")
        when (inp.type) {
            "boolean" -> {
                val cb = CheckBox(ctx)
                cb.text = title
                cb.textSize = 15f
                cb.typeface = Fonts.regular(ctx)
                cb.isChecked = inputValues[key] == "true"
                cb.setOnCheckedChangeListener { _, on -> inputValues[key] = if (on) "true" else "false" }
                box.addView(cb, LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = box.gap(6) })
            }
            "choice" -> {
                box.label(title, 14f, dim = true)
                box.dropdown(inp.options, inp.options.indexOf(inputValues[key]).coerceAtLeast(0)) { i ->
                    inputValues[key] = inp.options[i]
                    refresh()
                }
            }
            else -> {
                val et = box.input(title, inputValues[key] ?: "")
                et.onChange { inputValues[key] = it }
            }
        }
    }

    /** Menjalankan workflow baru lewat workflow_dispatch dengan input yang diisi, pada branch yang sedang aktif. */
    private fun dispatchWorkflow(r: RepoEntry, w: LocalWorkflow) {
        val inputs = LinkedHashMap<String, String>()
        for (inp in w.inputs) {
            val v = inputValues[w.file + "::" + inp.name] ?: inp.default
            if (v.isNotEmpty()) inputs[inp.name] = v
        }
        val summary = w.name + (if (inputs.isEmpty()) "" else "\n" + inputs.entries.joinToString("\n") { it.key + " = " + it.value })
        sure(tr("Jalankan workflow?", "Run the workflow?"), summary, tr("Jalankan", "Run")) {
            git("Jalankan ${w.name}", {
                val gh = ActionsApi.refFor(GitEngine.remoteUrl(r.path))
                val branch = GitEngine.use(r.path) { it.repository.branch }
                ActionsApi.dispatch(gh, w.file, branch, inputs)
                RunWatcher.kick(gh)
                OpResult(true, tr("Workflow ${w.name} dijalankan di branch $branch.", "Workflow ${w.name} started on branch $branch."))
            }) { res -> showResult(res) }
        }
    }

    private fun runSelected(r: RepoEntry, wfs: List<LocalWorkflow>) {
        val picks = wfs.mapNotNull { w ->
            val js = w.jobs.filter { (w.file + "::" + it.key) in selected }
            if (js.isEmpty()) null else w to js
        }
        if (picks.isEmpty()) {
            Dialogs.toast(ctx, tr("Centang minimal satu job.", "Tick at least one job."))
            return
        }
        val summary = picks.joinToString("\n") { (w, js) -> "• " + w.name + ": " + js.joinToString { it.name } }
        sure(tr("Jalankan aksi terpilih?", "Run the selected actions?"), summary, tr("Jalankan", "Run")) {
            git("Jalankan aksi", { execute(r, picks) }) { res -> showResult(res) }
        }
    }

    private suspend fun execute(r: RepoEntry, picks: List<Pair<LocalWorkflow, List<LocalJob>>>): OpResult {
        val gh = ActionsApi.refFor(GitEngine.remoteUrl(r.path))
        val branch = GitEngine.use(r.path) { it.repository.branch }
        val lines = ArrayList<String>()
        var allOk = true
        for ((w, js) in picks) {
            try {
                val latest = ActionsApi.runs(gh, branch, 1, workflow = w.file).firstOrNull()
                    ?: ActionsApi.runs(gh, null, 1, workflow = w.file).firstOrNull()
                if (latest == null) {
                    if (w.dispatch) {
                        ActionsApi.dispatch(gh, w.file, branch)
                        lines.add("✔ " + w.name + ": " + tr("dijalankan baru (workflow_dispatch).", "started (workflow_dispatch)."))
                    } else {
                        allOk = false
                        lines.add("✘ " + w.name + ": " + tr("belum ada run untuk dijalankan ulang dan workflow ini tidak punya workflow_dispatch; run pertama muncul setelah push.", "no run to re-run yet and this workflow has no workflow_dispatch; the first run appears after a push."))
                    }
                    continue
                }
                if (latest.status != "completed") {
                    allOk = false
                    lines.add("✘ " + w.name + ": " + tr("run terakhir (#${latest.number}) masih berjalan, tunggu sampai selesai.", "the latest run (#${latest.number}) is still running, wait until it finishes."))
                    continue
                }
                val remoteJobs = ActionsApi.jobs(gh, latest.id)
                val whole = js.size == w.jobs.size
                if (whole || js.size > 1) {
                    ActionsApi.rerun(gh, latest.id)
                    lines.add(
                        "✔ " + w.name + ": " + if (whole) tr("semua job dijalankan ulang (run #${latest.number}).", "all jobs re-run (run #${latest.number}).")
                        else tr("GitHub hanya bisa menjalankan ulang satu job atau seluruh run, jadi seluruh run #${latest.number} dijalankan ulang.", "GitHub can only re-run one job or the whole run, so the whole run #${latest.number} was re-run.")
                    )
                } else {
                    val j = js[0]
                    val rj = remoteJobs.firstOrNull {
                        it.name == j.name || it.name == j.key || it.name.startsWith(j.name + " (") || it.name.startsWith(j.key + " (")
                    }
                    if (rj == null) {
                        allOk = false
                        lines.add("✘ " + w.name + ": " + tr("job ${j.name} tidak ada di run terakhir (mungkin belum di-push).", "job ${j.name} is not in the latest run (maybe not pushed yet)."))
                    } else {
                        ActionsApi.rerunJob(gh, rj.id)
                        lines.add("✔ " + w.name + ": " + j.name)
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                allOk = false
                lines.add("✘ " + w.name + ": " + e.friendly())
            }
        }
        RunWatcher.kick(gh)
        return OpResult(allOk, lines.joinToString("\n"))
    }
}
