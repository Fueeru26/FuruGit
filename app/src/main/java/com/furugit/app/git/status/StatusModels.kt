package com.furugit.app.git.status

enum class ChangeKind { ADDED, MODIFIED, DELETED, UNTRACKED }

data class FileChange(val path: String, val kind: ChangeKind, val staged: Boolean)

data class StatusInfo(
    val branch: String,
    val state: String,
    val hasCommits: Boolean,
    val staged: List<FileChange>,
    val unstaged: List<FileChange>,
    val conflicts: List<String>,
    val hasTracking: Boolean,
    val ahead: Int,
    val behind: Int
) {
    val isMerging: Boolean get() = state != "SAFE"
    val hasUncommitted: Boolean get() = staged.isNotEmpty() || unstaged.any { it.kind != ChangeKind.UNTRACKED } || conflicts.isNotEmpty()
}
