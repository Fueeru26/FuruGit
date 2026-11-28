package com.furugit.app.git.stash

import com.furugit.app.core.common.GitUserError
import com.furugit.app.core.common.L
import com.furugit.app.git.commit.GitCommit
import com.furugit.app.git.repository.GitRepository
import org.eclipse.jgit.api.Git

/** Stash: simpan, daftar, apply, pop, drop, dan hapus semua. */
object GitStash {

    fun stashSave(g: Git, message: String, includeUntracked: Boolean) {
        val who = GitCommit.identity(g)
        val msg = message.ifBlank { "WIP on ${g.repository.branch}" }
        g.stashCreate().setPerson(who).setIncludeUntracked(includeUntracked)
            .setWorkingDirectoryMessage(msg).call()
            ?: throw GitUserError("Tidak ada perubahan untuk di-stash.")
    }

    fun stashList(g: Git): List<StashInfo> {
        if (!GitRepository.hasCommits(g)) return emptyList()
        return g.stashList().call().mapIndexed { i, c -> StashInfo(i, c.shortMessage, c.commitTime * 1000L) }
    }

    fun stashApply(g: Git, index: Int) {
        g.stashApply().setStashRef("stash@{" + index + "}").call()
    }

    fun stashDrop(g: Git, index: Int) {
        g.stashDrop().setStashRef(index).call()
    }

    fun stashPop(g: Git, index: Int) {
        stashApply(g, index)
        stashDrop(g, index)
    }

    fun stashClear(g: Git) {
        g.stashDrop().setAll(true).call()
    }
}
