# Security Policy

## Supported versions

Only the latest release receives fixes.

## Reporting a vulnerability

Please do not open a public issue for security problems. Use GitHub's private vulnerability reporting ("Security" tab > "Report a vulnerability") for this repository, and include the affected version and steps to reproduce.

FuruGit works on repositories you choose and does not upload them anywhere except to the Git remote you push to yourself. Network access is used only for Git operations you start (clone, fetch, pull, push), GitHub sign-in (PAT validation and OAuth Device Flow), and the update check against the GitHub Releases API. The update APK is downloaded only after you confirm, and installing it always requires your confirmation in the system installer.

Access tokens are stored encrypted with the Android Keystore. Signing keys and passwords are never stored in this repository; if you find a secret committed here, please report it privately.

For what the app stores and when it connects to the internet, see [docs/PRIVACY.md](docs/PRIVACY.md).
