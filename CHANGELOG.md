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
  - **Actions tab** (was History): GitHub Actions runs (live from GitHub, so deleted runs / workflows disappear), re-run, open, delete a run, and a **Run Actions** page that scans `.github/workflows` and re-runs ticked jobs without a new commit. The commit history (log, Cherry-pick, Revert, Reset, Tags) moved to a *Commit history* button in this tab.
- **Confirmation window** before every button that runs an action (stage, commit, commit & push, fetch, pull, push, switch branch, stash apply / pop, reset, push / delete tag, resolve conflict, init, save identity, GitHub login, install update, and so on).
- **Settings** (gear icon in the top bar):
  - **GitHub Settings**: storage permission, Git identity, accounts and authentication (Personal Access Token and OAuth Device Flow; tokens are stored encrypted).
  - **App Settings**: theme mode (Follow System / Light / Dark) and language (Follow System / English / Indonesian; a system language other than Indonesian uses English).
  - **Activity Log**: record of the operations that were run, with Copy and Clear.
  - **Guide**: what Git is, the Git commands and their buttons, how the app works, resolving conflicts and troubleshooting.
  - **Check for Updates**: reads the releases of `Fueeru26/FuruGit`, shows the notes, downloads the APK and opens the system installer.
  - **About**: developer, contact, donation, problem report by email and "About the App".
- **Design** identical to FuruArc's Blue Blossom look: blossom background image, glass cards with a light rim, blue glow and corner flowers, gradient buttons and icon badges, field-style inputs and dropdowns, the floating glass navigation bar, section titles with lines, the Qanelas font, and FuruArc's animations (springy press, staggered card entrance, scroll jelly, sliding tab / rising page / sinking back transitions). There is no theme color option; only theme mode and language.
- Status markers ("● Active", the active branch and remote) are capitalised and coloured: white / black while loading, green when connected, red when the connection fails (the active repo then reads "● Failed").
- Dialog buttons are now one row of equal columns instead of stacked lines; the result window has a centred **Close** button, no longer closes by itself, and its check / cross animates more slowly.
- The loading bar sits centred between the tab title and the content.
- **Actions tab**: new run menu entries **Download Artifacts** (tick the artifacts, then Download with a progress window and *Run in Background*) and **View / Download Logs** (a log page where each job is a collapsible card showing the whole log, 50 lines per page with FuruArc-style page navigation and *Go to page*, or download per-job `.log` files). Runs in progress show a spinning yellow dot, the list refreshes by itself every 8 seconds while something is running, and a toast tells when a run finishes. **Bulk delete runs** (failed, older than 7 / 30 days, all finished). **Run Actions** now reads `workflow_dispatch` inputs (checkbox, dropdown, text) and can start a new run with them.
- **Loading once**: the five tabs are created once and kept, so switching tabs no longer reloads. Tabs load when the app opens (in the background), and again only after something changes (a Git operation, a repo added / removed / switched, returning from Settings), when the internet comes back after a drop, when file access is granted, or when you tap the active tab again. Switching back to a loaded tab keeps its scroll position and replays the card entrance animation.
- **Actions tab, more**: **Cancel run** for runs in progress (the run menu now only offers what applies: running runs get Cancel, finished runs get Re-run / Delete). The log page has a **search** across all jobs with next / previous match (jumps to the right job and page and highlights the line), a **First error** button, coloured lines (errors red, warnings yellow, step titles bold, ANSI codes removed), and **Copy page** / **Share log** per job. When *Enable Notifications* is on, a notification tells when a run you started or watched finishes, even after leaving the Actions tab (checked every 15 seconds while a run is active; it stops if Android kills the app process).
- **App Settings › Download Settings**: output folder and *Enable Notifications* (progress and result in the notification shade). Download buttons need the output folder first.
- The navigation bar hides while the keyboard is open. The active repo reads "● Processing" while its status loads. The check / cross of the result window draws faster while the circle keeps its speed.
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
