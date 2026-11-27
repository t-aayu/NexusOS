# NexusOS

**NexusOS** (`com.nexus.prm`) is a private, offline-first personal and professional contact directory for Android, built with Jetpack Compose and Material 3.

## Features

- **Encrypted local directory**: Stores contacts, groups, posting history, introductions, and contact photos locally using Android Keystore encryption (`nexus_directory.enc` and encrypted `photos/`), with atomic file writes and write-failure recovery.
- **Structured contact profiles**:
  - Multiple labelled phone numbers and email addresses (`Personal`, `Office`, or custom labels) with primary selection for quick Call, WhatsApp, and Email actions.
  - Service, Batch, Cadre, Organisation, Designation, posting & role history, introductions (with searchable profile linking), tags, and reusable groups.
- **Fast capture & import/export**:
  - **Add person** with **Save and add another** and duplicate detection.
  - Home-screen **Quick Capture** widget and **Add to Nexus** capture from shared text, selected text, or single-contact `.vcf` files.
  - Android Contacts import, CSV roster import/export, and privacy-safe `.vcf` contact sharing (keeping private notes, tags, groups, photos, postings, and introductions private).
- **Search & match hints**:
  - Compact single-row search across names, numbers, emails, postings, introductions, tags, and groups with concise match-reason hints on non-name matches.
- **My contact card**:
  - Choose which fields to share and export your card as a live QR code, plain text, `.vcf` vCard, or PNG QR image.
- **Nexus Caller ID**:
  - Optional `CallScreeningService` overlay that shows a draggable, system-themed caller card on incoming calls (including lock-screen tap-to-unlock profile shortcut) without delaying ringing or blocking Phone call controls.
- **Encrypted backups & scheduled sync**:
  - Password-protected `.nxbk` backup archives with byte-counted stream and ZIP entry bounds, **Merge** (default) and **Full replacement** restore modes, and scheduled SAF backups compatible with FolderSync.
- **Themes & accents**:
  - **System**, **Light**, **Dark**, and pure-black **AMOLED** modes with preset accent palettes and Android 12+ Wallpaper colours, paired with a soft frosted glass UI.

## Building & Testing

- **Requirements**: JDK 17, Android SDK (compileSdk 36, minSdk 24).
- **Run unit tests**:
  ```bash
  ./gradlew testDebugUnitTest
  ```
- **Build release APK**:
  ```bash
  ./gradlew assembleRelease
  ```

## Releases

Pushing a `v*` tag triggers [release.yml](.github/workflows/release.yml) on GitHub Actions to run `testDebugUnitTest`, build the signed `app-release.apk`, and publish the GitHub Release.
