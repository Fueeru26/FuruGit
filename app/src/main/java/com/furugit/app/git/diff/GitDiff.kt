package com.furugit.app.git.diff

import com.furugit.app.git.status.ChangeKind
import com.furugit.app.git.status.FileChange
import java.io.ByteArrayOutputStream
import java.io.File
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.treewalk.filter.PathFilter

/** Teks diff satu file untuk ditampilkan di tab Perubahan. */
object GitDiff {

    fun diff(g: Git, c: FileChange): String {
        val limit = 40_000
        if (c.kind == ChangeKind.UNTRACKED) {
            val f = File(g.repository.workTree, c.path)
            if (f.isDirectory) return "(folder)"
            if (f.length() > 200_000) return "(file besar, pratinjau dilewati)"
            val t = f.readText()
            return "+++ file baru: ${c.path}\n" + t.take(limit).lines().joinToString("\n") { "+$it" }
        }
        val out = ByteArrayOutputStream()
        try {
            g.diff().setPathFilter(PathFilter.create(c.path)).setCached(c.staged).setOutputStream(out).call()
        } catch (e: Exception) {
            return "(diff tidak tersedia: ${e.message})"
        }
        val s = out.toString("UTF-8")
        return if (s.isBlank()) "(tidak ada perbedaan teks)" else if (s.length > limit) s.take(limit) + "\n… (dipotong)" else s
    }
}
