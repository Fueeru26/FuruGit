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
  - **App Settings**: theme mode (Follow System / Light / Dark) and language (Follow System / English / Indonesian; a system language other than Indonesian uses English).
  - **Activity Log**: record of the operations that were run, with Copy and Clear.
  - **Guide**: what Git is, the Git commands and their buttons, how the app works, resolving conflicts and troubleshooting.
  - **Check for Updates**: reads the releases of `Fueeru26/FuruGit`, shows the notes, downloads the APK and opens the system installer.
  - **About**: developer, contact, donation, problem report by email and "About the App".
- **Design** identical to FuruArc's Blue Blossom look: blossom background image, glass cards with a light rim, blue glow and corner flowers, gradient buttons and icon badges, field-style inputs and dropdowns, the floating glass navigation bar, section titles with lines, the Qanelas font, and FuruArc's animations (springy press, staggered card entrance, scroll jelly, sliding tab / rising page / sinking back transitions). There is no theme color option; only theme mode and language.
- **Result window**: after confirming an action, a window shows an animated green check (success) or red cross (failure) with a message below it.
- A new app icon.
- **Crash screen**: an unhandled crash is saved and shown on screen (separate `:crash` process) so the error message can be copied and reported.
- Repository files: README (English and Indonesian), LICENSE (GPL-3.0), SECURITY, CONTRIBUTING, CODE OF CONDUCT, THIRD_PARTY_NOTICES, docs (FAQ, Privacy), issue and pull request templates, GitHub Actions workflows (build, release).

### Fixed
- The active repository marker is now a large green dot with "Active" at the right end of its card.
- More space between the Fetch / Pull / Push buttons and the "Pull with rebase" checkbox, and between the Branch / Tags buttons and the History list.
- Buttons whose label needs two lines (for example "Open folder", "Commit & Push", "Stash changes") no longer shift down or get clipped: every button is exactly 44 dp high, uses one line and shrinks long text, so buttons side by side line up exactly.
- The toolbar title now changes immediately when switching between tabs (it used to show the previously selected tab).

### Added (later in 1.00)
- **Last message** button in the Changes tab: fills the commit message (and description) from the latest commit.
- Tapping the **active repository** card in the Repo tab opens the Changes tab.

### Changed
- Spacing now follows FuruArc: 16 dp between cards, 16 dp card padding, 12 dp before buttons and fields, roomier line spacing, and section titles in the accent color.
- App Settings now uses FuruArc-style dropdowns (Theme Mode, Language) and the scrolling named color circles; the extra description lines were removed.
- About: contact links have icons, donation shows only the platform names as links followed by the support note (same layout as FuruArc).
