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
*   **Cloud Save Sync:** Saved-game synchronization (experimental).
*   **Ad-Free:** 100% free software with no tracking.

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

## 🛠️ Build

Builds are supported on Linux/macOS with either Docker or Gradle.

### Prerequisites
*   Android Studio Ladybug (or newer)
*   JDK 17 (recommended: the JetBrains Runtime bundled with Android Studio)

### Recommended Method (Docker)

Run from the project root:

```bash
./build.sh
```

To build a specific variant:

```bash
./build.sh freeBundleRelease
```

### Manual Method (Gradle)

```bash
# Configure Java if needed
export JAVA_HOME="/path/to/jdk17"

# Generate a debug APK
(cd src && ./gradlew :lemuroid-app:assembleFreeBundleDebug)

# Generate a production AAB (requires keys in local.properties)
(cd src && ./gradlew :lemuroid-app:bundlePlayBundleRelease)
```

---

## 📄 License

This project is distributed under the **GNU General Public License v3.0 (GPLv3)**.

*   Retromul Copyright (C) 2026
*   Based on Lemuroid Copyright (C) Filippo Scognamiglio (Swordfish90)
*   Libretro cores have their own individual licenses.

> **Important:** Retromul does not include games or copyrighted BIOS files. Users are responsible for providing their own legally acquired files.
