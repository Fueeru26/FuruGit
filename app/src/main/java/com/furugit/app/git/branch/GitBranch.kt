package com.furugit.app.git.branch

import com.furugit.app.core.common.GitUserError
import com.furugit.app.git.repository.GitRepository
import org.eclipse.jgit.api.CreateBranchCommand
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.api.ListBranchCommand
import org.eclipse.jgit.lib.Repository

/** Daftar, buat, pindah, dan hapus branch. */
object GitBranch {

    fun branches(g: Git): List<BranchInfo> {
        val cur = g.repository.branch
        val out = ArrayList<BranchInfo>()
        for (r in g.branchList().call()) {
            val n = Repository.shortenRefName(r.name)
            out.add(BranchInfo(n, n == cur, false))
        }
        for (r in g.branchList().setListMode(ListBranchCommand.ListMode.REMOTE).call()) {
            val n = Repository.shortenRefName(r.name)
            if (!n.endsWith("/HEAD")) out.add(BranchInfo(n, false, true))
        }
        return out
    }

    fun createBranch(g: Git, name: String) {
        if (!Repository.isValidRefName("refs/heads/$name")) throw GitUserError("Nama branch tidak valid.")
        if (!GitRepository.hasCommits(g)) throw GitUserError("Buat commit pertama dulu sebelum membuat branch.")
        g.checkout().setCreateBranch(true).setName(name).call()
    }

    fun switchBranch(g: Git, name: String, isRemote: Boolean) {
        if (g.status().call().hasUncommittedChanges()) {
            throw GitUserError("Ada perubahan yang belum di-commit. Commit atau stash dulu agar tidak tertimpa.")
        }
        if (isRemote) {
            val local = name.substringAfter('/')
            val exists = g.branchList().call().any { Repository.shortenRefName(it.name) == local }
            if (exists) {
                g.checkout().setName(local).call()
            } else {
                g.checkout().setCreateBranch(true).setName(local).setStartPoint(name)
                    .setUpstreamMode(CreateBranchCommand.SetupUpstreamMode.TRACK).call()
            }
        } else {
            g.checkout().setName(name).call()
        }
    }

    fun deleteBranch(g: Git, name: String, force: Boolean) {
        val res = g.branchDelete().setBranchNames(name).setForce(force).call()
        if (res.isEmpty()) {
            throw GitUserError("Branch belum di-merge. Hapus paksa jika kamu yakin.")
        }
    }
}
