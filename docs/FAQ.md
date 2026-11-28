# FAQ and troubleshooting

**English** | [Bahasa Indonesia](FAQ_ID.md)

## General

**Which Android versions are supported?**
Android 8.0 (API 26) and newer.

**Why does FuruGit need "All files access"?**
Git works on real folders. FuruGit opens repositories by their real path (for example `/storage/emulated/0/MyRepo`) instead of copying them, which needs this permission. Without it the app cannot open or change repository folders.

**Are my files uploaded anywhere?**
No. Repositories are processed on your device. Data leaves it only when you Clone, Fetch, Pull or Push to a remote you chose. See [PRIVACY.md](PRIVACY.md).

**Does FuruGit run a command line?**
No. It uses JGit, a Git implementation in Java, so there is no `git` binary and no terminal.

## Sign-in and sync

**"Authentication failed".**
Check your token in Settings > GitHub Settings. A fine-grained Personal Access Token needs *Contents: Read and write* and *Metadata: Read* for that repository. Private repositories and pushing always need an account.

**"Push rejected" or "the remote has new commits".**
Someone (or another device) pushed first. Open the Sync tab, Pull, then Push again. FuruGit never force-pushes.

**GitHub login (OAuth) does not start.**
Enter the Client ID of your own GitHub App / OAuth App with **Device Flow** enabled. Never enter a client secret.

**"Git name & email are not set".**
Fill in Settings > GitHub Settings > Git Identity.

## Files and repositories

**Every file shows as changed.**
FuruGit turns off `core.filemode` and `core.symlinks` when it opens a repository, because shared storage does not support Linux file modes. If the repository was opened by another tool, close and reopen it in FuruGit's Repo tab. Line-ending differences (CRLF / LF) can also make files look changed.

**"Repository is locked" (`.git/index.lock`).**
A previous Git operation was interrupted. If no other Git app is running, delete the `.git/index.lock` file.

**I cannot open a repository folder.**
Make sure "All files access" is granted (Settings > GitHub Settings > Storage permission).

## Merging and undoing

**How do I resolve a conflict?**
Changes tab > Conflicts: choose Mine, Theirs, or Edit for each file, then Commit to finish the merge. Abort merge returns to the last commit.

**Which actions cannot be undone?**
*Discard selected changes* and *Reset HARD* remove uncommitted work permanently. *Reset soft / mixed* keep your changes. Committed work can usually be recovered with the history (a Reset only moves the branch).

## Updates

**How do updates work?**
FuruGit checks the releases of `Fueeru26/FuruGit` when it opens (silently, at most every 6 hours). If a newer version exists, an update window appears with the release notes and a red dot marks the gear icon and Settings > Check for Updates. You can download the APK in its own window and then open the system installer. Android asks you to allow installs from FuruGit the first time.
