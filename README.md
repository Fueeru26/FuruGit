<p align="center">
  <img src="docs/pictures/icon.png" alt="FuruGit" width="128">
</p>

<p align="center">
  <a href="LICENSE"><img alt="License: GPL-3.0" src="https://img.shields.io/badge/license-GPL--3.0-blue.svg"></a>
  <img alt="Platform: Android 8.0+" src="https://img.shields.io/badge/Android-8.0%2B-3DDC84.svg">
  <img alt="Kotlin" src="https://img.shields.io/badge/Kotlin-1.9.22-7F52FF.svg">
  <img alt="JGit" src="https://img.shields.io/badge/JGit-5.13-F05032.svg">
  <a href="https://github.com/Fueeru26/FuruGit/releases"><img alt="Release" src="https://img.shields.io/github/v/release/Fueeru26/FuruGit?include_prereleases"></a>
  <a href="https://github.com/Fueeru26/FuruGit/actions/workflows/build.yml"><img alt="Build" src="https://github.com/Fueeru26/FuruGit/actions/workflows/build.yml/badge.svg"></a>
  <a href="https://github.com/Fueeru26/FuruGit/releases"><img alt="Downloads" src="https://img.shields.io/github/downloads/Fueeru26/FuruGit/total"></a>
</p>

# FuruGit

**English** | [Bahasa Indonesia](README_ID.md)

**FuruGit** is an Android Git client that runs Git commands with **buttons instead of typed commands**. Clone, stage, commit, branch, merge, stash, pull, push, browse history and resolve conflicts, all on your phone, directly on the repositories in your storage. It uses [JGit](https://www.eclipse.org/jgit/), so everything runs on the device with no server in between.

The interface is available in **Indonesian and English** (it follows the system language by default), with light and dark themes in the same Blue Blossom look as FuruArc.

> **Note**
> FuruGit changes files in your repositories. Every button that runs an action asks for confirmation first, but actions such as *Discard changes* and *Reset HARD* cannot be undone.

## Contents

- [Features](#features)
- [Settings](#settings)
- [Signing in to GitHub](#signing-in-to-github)
- [Installation](#installation)
- [Building from source](#building-from-source)
- [Project structure](#project-structure)
- [Documentation](#documentation)
- [Contributing](#contributing)
- [License](#license)
- [Credits](#credits)

## Features

| Tab | What it does |
| --- | --- |
| **Repo** | Clone from GitHub, Init a new repository, open an existing repository folder (by real path, for example `/storage/emulated/0/FuruArc/Github/FuruArc`), choose the active repository. |
| **Changes** | Status, Stage selected / Stage all, Unstage, Discard selected changes, coloured diff, Commit (with a **Last message** button), **Commit & Push** (stage, commit, check the remote, push), conflict resolution (Mine / Theirs / manual Edit), Abort merge. |
| **Branch** | Local and remote branches; create, switch, merge and delete; Stash (save, apply, pop, drop, delete all). |
| **Sync** | Fetch, Pull (merge or rebase), safe Push (always fetches first, never forces), manage remotes. |
| **Actions** | GitHub Actions runs of the active repo (filter by branch / tag), re-run, open and delete runs (also in bulk), download artifacts and logs, view logs, and **Run Actions** to scan `.github/workflows` and re-run ticked jobs (e.g. debug-apk, unit-tests) without a new commit. *Commit history* (log, Cherry-pick, Revert, Reset soft / mixed / HARD, Tags, copy a hash) is a button at the bottom of this tab. |

Git commands and their buttons:

| Command | In FuruGit |
| --- | --- |
| `git init` / `git clone` | Repo > Init / Clone |
| `git status` / `git diff` | Changes tab, tap a file for its diff |
| `git add` / `git restore --staged` | Changes > Stage selected, Stage all / long press a staged file |
| `git commit` | Changes > Commit |
| `git add` + `commit` + `push` | Changes > Commit & Push |
| `git branch` / `git switch` / `git merge` | Branch tab |
| `git stash` | Branch > Stash changes |
| `git fetch` / `pull` / `push` / `remote` | Sync tab |
| `git log` / `reset` / `revert` / `cherry-pick` / `tag` | Actions tab › Commit history |

**Confirmation before every action.** Buttons that execute something (stage, commit, commit & push, fetch, pull, push, switch branch, stash apply / pop, reset, push or delete a tag, resolve a conflict, init, save identity, GitHub login, install an update) open a confirmation window first.

**Safe by design.** Push always fetches first and refuses when the remote is newer; there is no force push. On shared storage `core.filemode` and `core.symlinks` are turned off automatically so files do not all look modified.

## Settings

Tap the gear icon in the top bar.

- **GitHub Settings**: storage permission, Git identity (name and email for commits), accounts and authentication.
- **App Settings**: theme mode (Follow System, Light, Dark) and language (Follow System, English, Indonesian). *Follow System* uses Indonesian when the device language is Indonesian, otherwise English.
- **Activity Log**: the operations that were run; copy or clear them.
- **Guide**: what Git is, its commands, how the app works, resolving conflicts and troubleshooting.
- **Check for Updates**: looks for a newer release of this repository, shows the notes, downloads the APK and opens the system installer.
- **About**: developer, contact, donation, report a problem by email, and About the App.

## Signing in to GitHub

- **Personal Access Token (PAT)**: Settings > GitHub Settings > Add Personal Access Token. For a fine-grained token give the repository *Contents: Read and write* and *Metadata: Read*. The token is stored encrypted with the Android Keystore.
- **OAuth Device Flow**: create your own GitHub App or OAuth App, enable **Device Flow**, enter its *Client ID* in the app and log in with the code shown. Never put a client secret in the app.

Public repositories can be fetched and pulled without an account; pushing and private repositories need one.

## Installation

Download the latest APK from the [Releases](https://github.com/Fueeru26/FuruGit/releases) page and install it. Requires **Android 8.0 (API 26)** or newer. Every release lists the SHA-256 checksum of the APK (and attaches `SHA256SUMS.txt`) so you can verify the download. On first start, grant the **All files access** permission so FuruGit can open repository folders.

## Building from source

Requirements: JDK 17 and the Android SDK (platform 34). The Gradle wrapper is included.

```bash
git clone https://github.com/Fueeru26/FuruGit.git
cd FuruGit
./gradlew assembleDebug
```

The debug APK is written to `app/build/outputs/apk/debug/`. You can also open the folder in AndroidIDE or Android Studio.

| Component | Version |
| --- | --- |
| Android Gradle Plugin | 8.2.2 |
| Gradle | 8.2 |
| Kotlin | 1.9.22 |
| Git engine | JGit 5.13.3 |
| UI | Views + Material Components 1.11 |
| compileSdk / targetSdk / minSdk | 34 / 34 / 26 |

> **Signing**
> No keystore or password is stored in this repository. Debug builds need no signing. For a signed release, provide your own keystore through the environment variables `ANDROID_KEYSTORE_FILE`, `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS` and `ANDROID_KEY_PASSWORD`, or through an untracked `keystore.properties` in the project root (`storeFile`, `storePassword`, `keyAlias`, `keyPassword`). Without them `assembleRelease` produces an unsigned APK.

## Project structure

```
FuruGit/
├── app/
│   ├── build.gradle
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── res/                          Themes (one per accent color), fonts (Qanelas), icons, layouts
│       └── java/com/furugit/app/
│           ├── App.kt                    Application: language, theme mode, JGit setup
│           ├── data/                     Models, preferences, encrypted accounts, GitHub auth, updater
│           ├── git/GitEngine.kt          All Git operations (JGit)
│           └── ui/                       Screens, UI helpers, palette (Pal.kt), translations (I18n.kt)
├── docs/                                 FAQ and Privacy (English and Indonesian), pictures
├── .github/                              Issue and PR templates, workflows (build, release), Dependabot, funding
├── README_ID.md                          README in Indonesian
├── CHANGELOG.md                          Release notes
├── THIRD_PARTY_NOTICES.md
├── CONTRIBUTING.md
├── CODE_OF_CONDUCT.md
├── SECURITY.md
└── LICENSE
```

## Documentation

- [docs/FAQ.md](docs/FAQ.md): common questions and troubleshooting.
- [docs/PRIVACY.md](docs/PRIVACY.md): what is stored on your device and when the app connects to the internet.
- [CHANGELOG.md](CHANGELOG.md): release notes.

## Contributing

Bug reports and pull requests are welcome. Please read [CONTRIBUTING.md](CONTRIBUTING.md) first and follow the [Code of Conduct](CODE_OF_CONDUCT.md). For security issues see [SECURITY.md](SECURITY.md).

## License

FuruGit is licensed under the **GNU General Public License v3.0**. See [LICENSE](LICENSE). Third-party libraries keep their own licenses, see [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).

## Credits

- Created by **Fueeru** ([@Fueeru26](https://github.com/Fueeru26)).
- [JGit](https://www.eclipse.org/jgit/) (Eclipse Foundation): the Git engine.
- Design follows the author's other app, [FuruArc](https://github.com/Fueeru26/FuruArc).
