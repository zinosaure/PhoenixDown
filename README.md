# Retromul

**Retromul** is an open-source all-in-one emulator for Android based on [Libretro](https://www.libretro.com/).
It is an advanced [Lemuroid](https://github.com/Swordfish90/Lemuroid) fork designed to provide a better user experience with integrated game downloads (files hosted on archive.org, with no direct affiliation to this project), cloud/NAS support, and full Android TV compatibility.

---

## Key Differences from Lemuroid

Retromul extends the base feature set with premium-style features that are completely free:

*   **☁️ Integrated Game Downloads:** Native **Archive.org** browser to search for and download legally preserved ROMs without leaving the app.
*   **📂 SMB/NAS Support:** Scan and play directly from your local server or NAS.
*   **📺 Android TV First:** TV-optimized interface and file pickers, including support for older devices without SAF (Storage Access Framework).
*   **🤖 Metadata Editor:** Manually fix incorrectly identified game names and box art.

---

## Features

*   **Automatic Save State:** Automatically saves and restores game state.
*   **ROM Scanning:** Fast recursive indexing for local and network libraries.
*   **Touch Controls:** Optimized and customizable.
*   **Gamepad Support:** Native Bluetooth and USB controller support.
*   **Shaders:** CRT/LCD screen simulation for a nostalgic look.
*   **Ad-Free:** 100% free software with no tracking.

---

## Latest Changes (April 2026)

The following updates were recently implemented in this fork:

*   **Stability fixes (mobile + TV):**
    * Home freeze/crash issues linked to blocking SMB cover checks during UI rendering were fixed.
    * Cover loading now avoids SMB network calls on hot UI paths.

*   **SMB cover behavior improvements:**
    * Cover persistence now attempts direct write to SMB `GameCovers` for SMB libraries.
    * If SMB write fails (RO share, ACLs, etc.), covers fall back to local cache automatically.
    * Settings now show cover location behavior more explicitly (SMB RW path vs local fallback).

*   **Settings reorganization (mobile + TV):**
    * Settings are grouped into blocks in this order:
      `Roms` -> `General` -> `System Interaction` -> `Metadata/API` -> `Consoles`.
    * TV now includes `Metadata/API` key management for TheGamesDB.
    * TV advanced settings include save games import/export and factory reset confirmation.

*   **UX fixes:**
    * Mobile home list items now use clearer card contrast so game entries remain visible against the page background.
    * Cover settings label updated to **"Cover locations"** / **"Emplacements des jaquettes"**.

---

## Remaining Work / TODO

*   Verify end-to-end SMB RW behavior on real NAS setups:
    * Confirm `GameCovers` creation per ROM directory across different SMB servers (Samba, Synology, Unraid, Windows shares).
    * Confirm ACL edge cases where share is writable but subfolder inheritance blocks writes.

*   Add optional diagnostics for cover persistence:
    * Debug log toggle for SMB cover upload path, write result, and fallback reason.

*   Improve cover location status precision in settings:
    * Replace static wording with runtime `SMB RO` / `SMB RW` detection done asynchronously (without UI blocking).

*   Add automated regression tests for:
    * cover persistence fallback logic (SMB -> local),
    * settings block ordering (mobile + TV),
    * non-blocking cover model resolution.

---

## 🏗️ Project Structure (Fork)

The following modules and files make up the main Retromul additions:

```text
lemuroid-app/src/main/java/com/swordfish/lemuroid/app/mobile/feature/
├── catalog/
│   ├── ArchiveOrgClient.kt      # Archive.org REST API client
│   ├── CatalogViewModel.kt      # Search and filtering logic
│   ├── CatalogScreen.kt         # Online catalog Compose UI
│   ├── RomDownloader.kt         # Download manager with notifications
│   ├── SourceManager.kt         # Source orchestrator (local vs SMB)
│   ├── SmbClient.kt             # SMB client (JCIFS-NG) for NAS
│   └── RomMetadataExtractor.kt  # Smart identification by name/region
├── disclaimer/
│   └── DisclaimerScreen.kt      # Mandatory legal notice (Google Play)
└── main/
    └── GameEditDialog.kt        # Manual game metadata editor
```

---

## 🎮 Supported Systems

| System | Core (Engine) |
| :--- | :--- |
| **Nintendo** | NES, SNES, N64, GB, GBC, GBA, DS, 3DS |
| **Sega** | Master System, Genesis, CD, Game Gear |
| **Sony** | PlayStation (PSX), PSP |
| **Arcade** | FinalBurn Neo |
| **Atari** | 2600, 7800, Lynx |
| **Other** | Neo Geo Pocket, WonderSwan, PC Engine |

---

## 🛠️ How to Build

Builds are supported on Linux/macOS with either Docker (recommended) or Gradle.

### Prerequisites

*   Docker + Docker Compose (`docker compose` or `docker-compose`)
*   Or JDK 17 + Android SDK/NDK (for manual Gradle builds)

### Recommended Method (Docker)

From project root:

```bash
./build.sh
```

This uses `docker-compose.yml` and builds the default release target.

Optional arguments:

```bash
./build.sh <variant_label> <build_variant>
```

Example:

```bash
./build.sh v1.17.0 freeBundleRelease
```

Notes:

*   First argument is a **label** used in output naming.
*   Second argument is the Gradle variant passed into container build scripts.

### Manual Method (Gradle)

```bash
# Ensure wrapper is executable
chmod +x src/gradlew

# Configure Java if needed
export JAVA_HOME="/path/to/jdk17"

# Debug APK
(cd src && ./gradlew :lemuroid-app:assembleFreeBundleDebug)

# Release AAB (requires signing setup in local.properties)
(cd src && ./gradlew :lemuroid-app:bundlePlayBundleRelease)
```

### Common Build Issues

*   `Permission denied` on `./gradlew`:
    * run `chmod +x src/gradlew`

*   Stale Gradle configuration cache:
    * remove `src/.gradle-cache/configuration-cache/` and rebuild

*   No system `gradle` command:
    * use the wrapper (`src/gradlew`) or Docker build flow

---

## 📄 License

This project is distributed under the **GNU General Public License v3.0 (GPLv3)**.

*   Retromul Copyright (C) 2026
*   Based on Lemuroid Copyright (C) Filippo Scognamiglio (Swordfish90)
*   Libretro cores have their own individual licenses.

> **Important:** Retromul does not include games or copyrighted BIOS files. Users are responsible for providing their own legally acquired files.
