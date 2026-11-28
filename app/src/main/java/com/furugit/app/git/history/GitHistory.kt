package com.furugit.app.git.history

import com.furugit.app.core.common.GitUserError
import com.furugit.app.core.common.L
import com.furugit.app.core.common.OpResult
import kotlin.math.max
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.api.ResetCommand
import org.eclipse.jgit.lib.ObjectId
import org.eclipse.jgit.lib.Repository

/** Riwayat commit, Revert, Cherry-pick, Reset, dan Tag. */
object GitHistory {

    /** Pesan lengkap commit terakhir (HEAD), atau null bila belum ada commit. */
    fun lastMessage(g: Git): String? {
        val head = g.repository.resolve("HEAD") ?: return null
        org.eclipse.jgit.revwalk.RevWalk(g.repository).use { w ->
            return w.parseCommit(head).fullMessage.trim()
        }
    }

    fun log(g: Git, ref: String?, max: Int = 150): List<CommitInfo> {
        val start = g.repository.resolve(ref ?: "HEAD") ?: return emptyList()
        return g.log().add(start).setMaxCount(max).call().map {
            CommitInfo(it.name, it.name.substring(0, 7), it.shortMessage, it.authorIdent.name, it.commitTime * 1000L)
        }
    }

    private fun requireClean(g: Git) {
        if (g.status().call().hasUncommittedChanges()) {
            throw GitUserError("Ada perubahan yang belum di-commit. Commit atau stash dulu.")
        }
    }

    fun revert(g: Git, commitId: String): OpResult {
        requireClean(g)
        val r = g.revert().include(ObjectId.fromString(commitId)).call()
        if (r == null) {
            g.reset().setMode(ResetCommand.ResetType.HARD).call()
            return OpResult(false, "Revert menyebabkan konflik, sehingga dibatalkan. Tidak ada yang berubah.")
        }
        return OpResult(true, "Commit di-revert (commit pembatalan baru dibuat).")
    }

    fun cherryPick(g: Git, commitId: String): OpResult {
        requireClean(g)
        val r = g.cherryPick().include(ObjectId.fromString(commitId)).call()
        return when (r.status) {
            org.eclipse.jgit.api.CherryPickResult.CherryPickStatus.OK -> OpResult(true, "Cherry-pick berhasil.")
            else -> {
                g.reset().setMode(ResetCommand.ResetType.HARD).call()
                OpResult(false, "Cherry-pick gagal/konflik (${r.status}) dan dibatalkan. Tidak ada yang berubah.")
            }
        }
    }

    fun reset(g: Git, commitId: String, mode: ResetCommand.ResetType) {
        g.reset().setMode(mode).setRef(commitId).call()
    }

    fun tags(g: Git): List<String> =
        g.tagList().call().map { Repository.shortenRefName(it.name) }.sorted()

    fun createTag(g: Git, name: String, commitId: String) {
        if (!Repository.isValidRefName("refs/tags/$name")) throw GitUserError("Nama tag tidak valid.")
        val c = g.repository.parseCommit(ObjectId.fromString(commitId))
        g.tag().setName(name).setObjectId(c).setAnnotated(false).call()
    }

    fun deleteTag(g: Git, name: String) {
        g.tagDelete().setTags(name).call()
    }
}
