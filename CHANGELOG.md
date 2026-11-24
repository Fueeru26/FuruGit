# Changelog

All notable changes to FuruGit are listed here. The version number is only raised when the maintainer decides to release.

## 1.00

First release.

### Added
- **Git with buttons**: run Git operations without typing commands, using JGit on the device (no server in between).
  - **Repo tab**: Clone, Init, open an existing repository folder, choose the active repository. Folders are opened by real path (not copied) with the "All files access" permission.
  - **Changes tab**: status, Stage selected / Stage all, Unstage, Discard selected changes, coloured diff, Commit, **Commit & Push**, conflict resolution (Mine / Theirs / manual Edit), Abort merge.
  - **Branch tab**: local and remote branches, create, switch, merge, delete; Stash (save, apply, pop, drop, delete all).
  - **Sync tab**: Fetch, Pull (merge or rebase), safe Push (always fetches first, never forces), manage remotes.
  - **History tab**: log per branch, Cherry-pick, Revert, Reset soft / mixed / HARD, Tags (create, push, delete).
- **Confirmation window** before every button that runs an action (stage, commit, commit & push, fetch, pull, push, switch branch, stash apply / pop, reset, push / delete tag, resolve conflict, init, save identity, GitHub login, install update, and so on).
- **Settings** (gear icon in the top bar):
  - **GitHub Settings**: storage permission, Git identity, accounts and authentication (Personal Access Token and OAuth Device Flow; tokens are stored encrypted).
  - **App Settings**: theme mode (Follow System / Light / Dark), theme color (9 accent colors, same as FuruArc) and language (Follow System / English / Indonesian; a system language other than Indonesian uses English).
  - **Activity Log**: record of the operations that were run, with Copy and Clear.
  - **Guide**: what Git is, the Git commands and their buttons, how the app works, resolving conflicts and troubleshooting.
  - **Check for Updates**: reads the releases of `Fueeru26/FuruGit`, shows the notes, downloads the APK and opens the system installer.
  - **About**: developer, contact, donation, problem report by email and "About the App".
- **Design** matching FuruArc (Classic style: palette, 10 dp cards and buttons, bottom navigation, Qanelas font) and a new app icon.
- Repository files: README (English and Indonesian), LICENSE (GPL-3.0), SECURITY, CONTRIBUTING, CODE OF CONDUCT, THIRD_PARTY_NOTICES, docs (FAQ, Privacy), issue and pull request templates, GitHub Actions workflows (build, release).

### Fixed
- Buttons whose label needs two lines (for example "Open folder", "Commit & Push", "Stash changes") no longer shift down or get clipped: every button is exactly 44 dp high, uses one line and shrinks long text, so buttons side by side line up exactly.
