package com.furugit.app.github.workflows

import java.io.File

/** Memindai dan membaca file workflow di folder .github/workflows repo lokal. */
object WorkflowScanner {

    /** Memindai file workflow di working tree repo lokal: nama, ada tidaknya workflow_dispatch, dan daftar job-nya. */
    fun scanLocal(repoPath: String): List<LocalWorkflow> {
        val dir = File(repoPath, ".github/workflows")
        val files = dir.listFiles { f -> f.isFile && (f.name.endsWith(".yml") || f.name.endsWith(".yaml")) }
            ?.sortedBy { it.name.lowercase() } ?: return emptyList()
        return files.map { parseWorkflow(it) }
    }

    private fun unquote(s: String): String = s.trim().trim('"', '\'')

    internal fun parseWorkflow(f: File): LocalWorkflow {
        val lines = try {
            f.readLines()
        } catch (e: Exception) {
            emptyList()
        }
        var name = f.name
        var dispatch = false
        val jobs = ArrayList<LocalJob>()
        var inJobs = false
        var curKey: String? = null
        var curName: String? = null
        fun flush() {
            curKey?.let { jobs.add(LocalJob(it, curName ?: it)) }
            curKey = null
            curName = null
        }
        for (raw in lines) {
            val line = raw.substringBefore(" #").trimEnd()
            if (line.isBlank() || line.trimStart().startsWith("#")) continue
            val indent = line.length - line.trimStart().length
            if (indent == 0) {
                if (inJobs) flush()
                inJobs = line.startsWith("jobs:")
                if (line.startsWith("name:")) name = unquote(line.removePrefix("name:")).ifBlank { f.name }
                continue
            }
            if (!inJobs) {
                if (line.contains("workflow_dispatch")) dispatch = true
                continue
            }
            if (indent == 2 && line.trim().endsWith(":") && !line.trim().startsWith("-")) {
                flush()
                curKey = unquote(line.trim().removeSuffix(":"))
            } else if (indent == 4 && line.trim().startsWith("name:") && curKey != null && curName == null) {
                curName = unquote(line.trim().removePrefix("name:"))
            }
        }
        if (inJobs) flush()
        return LocalWorkflow(f.name, name, dispatch, jobs, parseInputs(lines))
    }

    private fun indentOf(s: String) = s.length - s.trimStart().length

    /** Membaca input di blok workflow_dispatch > inputs (nama, type, default, options, description). */
    private fun parseInputs(rawLines: List<String>): List<DispatchInput> {
        val lines = rawLines.map { it.substringBefore(" #").trimEnd() }
        val idx = lines.indexOfFirst { it.trim().startsWith("workflow_dispatch:") }
        if (idx < 0) return emptyList()
        val wdIndent = indentOf(lines[idx])
        var i = idx + 1
        var inputsIdx = -1
        var inputsIndent = 0
        while (i < lines.size) {
            val l = lines[i]
            if (l.isBlank() || l.trim().startsWith("#")) {
                i++
                continue
            }
            if (indentOf(l) <= wdIndent) break
            if (l.trim() == "inputs:") {
                inputsIdx = i
                inputsIndent = indentOf(l)
                break
            }
            i++
        }
        if (inputsIdx < 0) return emptyList()
        val res = ArrayList<DispatchInput>()
        var name: String? = null
        var type = "string"
        var def = ""
        var desc = ""
        val opts = ArrayList<String>()
        var inOptions = false
        var itemIndent = -1
        fun flush() {
            val n = name
            if (n != null) res.add(DispatchInput(n, type, def, opts.toList(), desc))
            name = null
            type = "string"
            def = ""
            desc = ""
            opts.clear()
            inOptions = false
        }
        var j = inputsIdx + 1
        while (j < lines.size) {
            val raw = lines[j]
            j++
            if (raw.isBlank() || raw.trim().startsWith("#")) continue
            val ind = indentOf(raw)
            if (ind <= inputsIndent) break
            val t = raw.trim()
            if (itemIndent < 0) itemIndent = ind
            if (ind == itemIndent && t.endsWith(":")) {
                flush()
                name = unquote(t.removeSuffix(":"))
                continue
            }
            if (name == null) continue
            if (inOptions && t.startsWith("- ")) {
                opts.add(unquote(t.removePrefix("- ")))
                continue
            }
            inOptions = false
            when {
                t.startsWith("type:") -> type = unquote(t.removePrefix("type:"))
                t.startsWith("default:") -> def = unquote(t.removePrefix("default:"))
                t.startsWith("description:") -> desc = unquote(t.removePrefix("description:"))
                t.startsWith("options:") -> inOptions = true
            }
        }
        flush()
        return res
    }
}
