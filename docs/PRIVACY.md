# Privacy

**English** | [Bahasa Indonesia](PRIVACY_ID.md)

FuruGit works on repositories you choose, on your device. It has no FuruGit accounts, and it contains no analytics, crash-reporting or advertising libraries.

## Your files

Repositories are opened directly from your phone's storage through the "All files access" permission, and processed locally by JGit. FuruGit sends repository data only to the Git remote you choose to fetch from, pull from or push to.

## When the app uses the network

Nothing is sent unless you use one of these features:

| Feature | Where it connects | What is sent |
| --- | --- | --- |
| Clone, Fetch, Pull, Push | The remote URL of your repository (for example `github.com`) | The Git protocol traffic, and your access token as credentials for that host. |
| Token check and OAuth login | `api.github.com`, `github.com` | The token you enter (to read your username), or the Client ID and device code for OAuth Device Flow. |
| Check for updates | `api.github.com` (GitHub Releases of `Fueeru26/FuruGit`) | A normal web request, made when the app opens (at most every 6 hours) and when you tap "Check for updates". Nothing about you is sent. |
| Update download | `github.com` release assets | Only after you tap "Download latest version". Installing always asks for your confirmation in the system installer. |

Like any web request, these reveal your IP address to the server you contact.

## What is stored on your device

- Settings (theme, language, Git name and email), the list of repositories you added, and the activity log (kept in memory only) in the app's private storage.
- Access tokens, encrypted with the Android Keystore (`EncryptedSharedPreferences`). Backup is turned off for the app.

Uninstalling the app removes this data. Repositories in your storage are not deleted.

## Permissions

| Permission | Why |
| --- | --- |
| `INTERNET` | The network features above. |
| `MANAGE_EXTERNAL_STORAGE` (All files access) | Opening and changing repository folders by real path, which Git needs. |
| `READ_EXTERNAL_STORAGE` / `WRITE_EXTERNAL_STORAGE` (old Android only) | The same, on Android 10 and older. |
| `REQUEST_INSTALL_PACKAGES` | Opening the system installer for an update APK you downloaded in the app. |

## Changes and contact

If this page changes, the change is listed in the [CHANGELOG](../CHANGELOG.md). Questions: open a [discussion or issue](https://github.com/Fueeru26/FuruGit). For security problems see [SECURITY.md](../SECURITY.md).
