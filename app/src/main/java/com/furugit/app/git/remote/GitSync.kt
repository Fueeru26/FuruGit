package com.furugit.app.git.remote

import com.furugit.app.core.common.ActivityLog
import com.furugit.app.core.common.GitUserError
import com.furugit.app.core.common.OpResult
import com.furugit.app.git.branch.GitMerge
import com.furugit.app.git.commit.GitCommit
import com.furugit.app.git.repository.GitRepository
import com.furugit.app.git.status.FileChange
import com.furugit.app.git.status.GitStatus
import com.furugit.app.github.auth.AuthStore
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.api.RebaseCommand
import org.eclipse.jgit.lib.ProgressMonitor
import org.eclipse.jgit.transport.CredentialsProvider
import org.eclipse.jgit.transport.PushResult
import org.eclipse.jgit.transport.RefSpec
import org.eclipse.jgit.transport.RemoteRefUpdate

/** Fetch, Pull, Push (tanpa push paksa), dan Commit & Push. */
object GitSync {

    private fun creds(g: Git, remote: String): CredentialsProvider? {
        val url = g.repository.config.getString("remote", remote, "url") ?: return null
        return AuthStore.providerFor(url)
    }

    fun fetch(g: Git, remote: String, pm: ProgressMonitor?) {
        g.fetch().setRemote(remote).setRemoveDeletedRefs(true)
            .setCredentialsProvider(creds(g, remote)).setProgressMonitor(pm).call()
    }

    fun pull(g: Git, remote: String, rebase: Boolean, pm: ProgressMonitor?): OpResult {
        val repo = g.repository
        val branch = repo.branch
        if (repo.fullBranch?.startsWith("refs/heads/") != true) throw GitUserError("HEAD sedang detached. Pindah ke sebuah branch dulu.")
        if (rebase && g.status().call().hasUncommittedChanges()) {
            throw GitUserError("Rebase butuh working tree bersih. Commit atau stash dulu.")
        }
        val cmd = g.pull().setRemote(remote).setRebase(rebase)
            .setCredentialsProvider(creds(g, remote)).setProgressMonitor(pm)
        if (repo.config.getString("branch", branch, "merge") == null) cmd.setRemoteBranchName(branch)
        val r = cmd.call()
        if (r.isSuccessful) return OpResult(true, "Pull berhasil.")
        if (rebase) {
            try {
                g.rebase().setOperation(RebaseCommand.Operation.ABORT).call()
            } catch (_: Exception) {
            }
            return OpResult(false, "Rebase menemui konflik dan dibatalkan. Coba Pull dengan mode Merge.")
        }
        val mr = r.mergeResult ?: return OpResult(false, "Pull tidak berhasil.")
        return GitMerge.mergeOutcome(mr)
    }

    private fun checkPush(results: Iterable<PushResult>): Boolean {
        var allUpToDate = true
        for (res in results) {
            for (u in res.remoteUpdates) {
                when (u.status) {
                    RemoteRefUpdate.Status.OK -> allUpToDate = false
                    RemoteRefUpdate.Status.UP_TO_DATE -> {}
                    else -> throw GitUserError(
                        "Push ditolak (${u.status}). " + (u.message ?: "") +
                            if (u.status == RemoteRefUpdate.Status.REJECTED_NONFASTFORWARD) " Lakukan Pull dulu." else ""
                    )
                }
            }
        }
        return allUpToDate
    }

    private fun requireBranch(g: Git) {
        if (!GitRepository.hasCommits(g)) throw GitUserError("Belum ada commit untuk dikirim.")
        if (g.repository.fullBranch?.startsWith("refs/heads/") != true) {
            throw GitUserError("HEAD sedang detached. Pindah ke sebuah branch dulu.")
        }
    }

    fun push(g: Git, remote: String, pm: ProgressMonitor?): OpResult {
        requireBranch(g)
        val branch = g.repository.branch
        val results = g.push().setRemote(remote)
            .setRefSpecs(RefSpec("refs/heads/$branch:refs/heads/$branch"))
            .setCredentialsProvider(creds(g, remote)).setProgressMonitor(pm).call()
        val upToDate = checkPush(results)
        val cfg = g.repository.config
        if (cfg.getString("branch", branch, "remote") == null) {
            cfg.setString("branch", branch, "remote", remote)
            cfg.setString("branch", branch, "merge", "refs/heads/$branch")
            cfg.save()
        }
        return OpResult(true, if (upToDate) "Sudah up to date, tidak ada yang dikirim." else "Push ke $remote/$branch berhasil.")
    }

    /** Fetch dulu; jika remote lebih baru, jangan push (tidak pernah push paksa). */
    fun safePush(g: Git, remote: String, pm: ProgressMonitor?): OpResult {
        requireBranch(g)
        fetch(g, remote, pm)
        val tr = GitStatus.tracking(g)
        if (tr != null && tr.second > 0) {
            throw GitUserError("Remote punya ${tr.second} commit baru yang belum ada di lokal. Lakukan Pull dulu, lalu Push.")
        }
        return push(g, remote, pm)
    }

    fun pushTag(g: Git, remote: String, tag: String, pm: ProgressMonitor?) {
        val results = g.push().setRemote(remote).setRefSpecs(RefSpec("refs/tags/$tag"))
            .setCredentialsProvider(creds(g, remote)).setProgressMonitor(pm).call()
        checkPush(results)
    }

    /** Stage → commit → fetch → cek sinkron → push. */
    fun quickSync(g: Git, include: List<FileChange>, message: String, pm: ProgressMonitor?): OpResult {
        if (include.isNotEmpty()) GitCommit.stage(g, include)
        val id = GitCommit.commit(g, message)
        ActivityLog.add("Commit $id dibuat")
        val remote = GitRemote.defaultRemote(g)
            ?: throw GitUserError("Commit $id dibuat, tetapi repository belum punya remote. Tambahkan di tab Kelola › Sinkronisasi.")
        val res = try {
            safePush(g, remote, pm)
        } catch (e: GitUserError) {
            throw GitUserError("Commit $id sudah tersimpan lokal, tetapi push gagal: ${e.message}")
        }
        return res.copy(message = "Commit $id → ${res.message}")
    }
}
