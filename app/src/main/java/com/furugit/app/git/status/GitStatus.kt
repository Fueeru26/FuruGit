package com.furugit.app.git.status

import com.furugit.app.git.repository.GitRepository
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.lib.BranchTrackingStatus

/** Status working tree: file yang berubah, branch aktif, dan posisi terhadap remote. */
object GitStatus {

    internal fun tracking(g: Git): Pair<Int, Int>? = try {
        val t = BranchTrackingStatus.of(g.repository, g.repository.fullBranch)
        if (t == null) null else Pair(t.aheadCount, t.behindCount)
    } catch (e: Exception) {
        null
    }

    fun status(g: Git): StatusInfo {
        val repo = g.repository
        val st = g.status().call()
        val staged = ArrayList<FileChange>()
        for (p in st.added.sorted()) staged.add(FileChange(p, ChangeKind.ADDED, true))
        for (p in st.changed.sorted()) staged.add(FileChange(p, ChangeKind.MODIFIED, true))
        for (p in st.removed.sorted()) staged.add(FileChange(p, ChangeKind.DELETED, true))
        val unstaged = ArrayList<FileChange>()
        for (p in st.modified.sorted()) unstaged.add(FileChange(p, ChangeKind.MODIFIED, false))
        for (p in st.missing.sorted()) unstaged.add(FileChange(p, ChangeKind.DELETED, false))
        for (p in st.untracked.sorted()) unstaged.add(FileChange(p, ChangeKind.UNTRACKED, false))
        val tr = tracking(g)
        return StatusInfo(
            branch = repo.branch ?: "?",
            state = repo.repositoryState.name,
            hasCommits = GitRepository.hasCommits(g),
            staged = staged,
            unstaged = unstaged,
            conflicts = st.conflicting.sorted(),
            hasTracking = tr != null,
            ahead = tr?.first ?: 0,
            behind = tr?.second ?: 0
        )
    }
}
