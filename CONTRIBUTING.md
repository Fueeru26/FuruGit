# Contributing to FuruGit

Thanks for your interest in FuruGit! Bug reports, ideas, translations and pull requests are all welcome. Please follow the [Code of Conduct](CODE_OF_CONDUCT.md).

## Reporting a bug

Open an [issue](https://github.com/Fueeru26/FuruGit/issues/new/choose) and include:

- App version (Settings > About) and Android version / device.
- The screen and the exact steps (for example Changes tab > Commit & Push).
- The error message shown in the app, and the activity log (Settings > Activity Log > Copy log). Remove anything private from the log first; never post a token or password.

## Suggesting a feature

Open a "Feature request" issue with the Git command or workflow you need and how you would expect it to work with buttons.

## Development setup

1. Install JDK 17 and the Android SDK (platform 34).
2. Clone the repository and run `./gradlew assembleDebug`, or open the folder in Android Studio / AndroidIDE.
3. Release signing is not needed for debug builds (see the Signing note in the README). Never commit keystores or passwords (`*.jks`, `*.keystore` and `keystore.properties` are in `.gitignore`).

Toolchain: AGP 8.2.2, Gradle 8.2, Kotlin 1.9.22, JGit 5.13.3 (the last JGit line that supports Java 8 / Android), minSdk 26. Please do not bump these in a feature pull request.

## Code guidelines

- Kotlin package: `com.furugit.app`. All Git operations live in `git/GitEngine.kt`; screens call them through the helpers in `ui/BaseFragment.kt` (`git`, `gitQuiet`, `sure`).
- The UI is built in code with the helpers in `ui/Ui.kt` (`button`, `card`, `rowCard`, `input`, `Dialogs`). Use them so screens look consistent. Buttons in one row must stay the same height.
- Colors come from `Context.color(R.color.furu_*)`, which resolves to the palette of the chosen accent color and theme mode (`ui/Pal.kt`). Do not hard-code colors.
- Every button that runs an action must ask for confirmation with `sure(title, message, okLabel)` before it executes.
- Developer comments and commit messages are in English.

## Strings and translations

Source strings in the code are Indonesian. English text comes from the dictionary in `ui/I18n.kt` (exact strings, or patterns with `{}` for dynamic parts); for new screens you can also write both languages in place with `tr("Indonesia", "English")`. When you add or change a string, add or update its English entry.

## Documentation

Keep `README.md` and `README_ID.md` (and the docs in `docs/`) in step with each other. Record every change in `CHANGELOG.md`.

## Pull requests

1. Fork the repository and create a branch from `main`.
2. Keep each pull request focused on one change.
3. Make sure `./gradlew assembleDebug` succeeds.
4. Describe what changed and how you tested it (device, Android version).
5. Do not change the app version; the maintainer decides when to release.

By contributing you agree that your contribution is licensed under the [GPL-3.0](LICENSE) like the rest of the project.
