package com.furugit.app.git.branch

import com.furugit.app.core.common.GitUserError
import com.furugit.app.core.common.OpResult
import java.io.File
import org.eclipse.jgit.api.CheckoutCommand
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.api.MergeResult
import org.eclipse.jgit.api.ResetCommand

/** Merge dan penyelesaian konflik. */
object GitMerge {

    internal fun mergeOutcome(r: MergeResult): OpResult {
        val status = r.mergeStatus
        return when (status) {
            MergeResult.MergeStatus.CONFLICTING -> {
                val files = r.conflicts?.keys?.toList() ?: emptyList()
                OpResult(false, "Merge menghasilkan konflik pada ${files.size} file.", files)
            }
            MergeResult.MergeStatus.FAILED -> {
                val files = r.failingPaths?.keys?.toList() ?: emptyList()
                OpResult(false, "Merge gagal: file lokal akan tertimpa. Commit/stash dulu.", files)
            }
            else -> if (status.isSuccessful) OpResult(true, "Merge berhasil ($status).")
            else OpResult(false, "Merge tidak berhasil: $status")
        }
    }

    fun merge(g: Git, source: String): OpResult {
        if (g.status().call().hasUncommittedChanges()) {
            throw GitUserError("Ada perubahan yang belum di-commit. Commit atau stash dulu sebelum merge.")
        }
        val id = g.repository.resolve(source) ?: throw GitUserError("Branch '$source' tidak ditemukan.")
        val r = g.merge().include(id).setCommit(true)
            .setMessage("Merge $source into ${g.repository.branch}").call()
        return mergeOutcome(r)
    }

    fun resolveKeep(g: Git, path: String, mine: Boolean) {
        val stage = if (mine) CheckoutCommand.Stage.OURS else CheckoutCommand.Stage.THEIRS
        g.checkout().addPath(path).setStage(stage).call()
        g.add().addFilepattern(path).call()
    }

    fun readWorkFile(g: Git, path: String): String = File(g.repository.workTree, path).readText()

    fun writeResolved(g: Git, path: String, text: String) {
        File(g.repository.workTree, path).writeText(text)
        g.add().addFilepattern(path).call()
    }

    fun abortMerge(g: Git) {
        g.reset().setMode(ResetCommand.ResetType.HARD).call()
    }
}
