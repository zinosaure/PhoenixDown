****************************************************************************************************
20/04/2026 12:00 - Upstream Watch: Lemuroid 1.17.0 announced on stores/blog (non-Google) - v017
****************************************************************************************************
- Description:
  Synced project documentation with the upstream Lemuroid 1.17.0 announcement.
  Note: at the time of writing, GitHub Releases/Tags still publicly show 1.16.2 as latest.

- Upstream headline changes announced:
  Added microphone support for Nintendo DS (MelonDS only)
  Deprecated DeSmuME, replaced by MelonDS
  Completely redesigned touch controls
  Added immersive mode with dynamic background color
  Made autosave more robust
  Slightly improved HD mode
  Added quicksave/quickload gamepad shortcuts
  Added support for 16Kb pages
  Updated all cores and databases
  Various UI/UX improvements

- Notes:
  Mobile UI is now fully Jetpack Compose upstream (TV migration still pending upstream)
  Samsung multitouch/Game Booster issue mitigation was addressed upstream
  Save reliability on app background/kill scenarios was improved upstream
  Google Drive save sync remains Play-flavor only; GitHub/free builds do not include it

****************************************************************************************************
22/12/2025 17:19 - Integrated Bug Report Form - v016
****************************************************************************************************
- Description:
  Added an internal WebView for the bug report form, removing the dependency on an external browser.

- Changes:
  BugReportActivity with a WebView for the bug report form
  Multiple image uploads from the form
  Full mobile and TV support
  Removed the external browser dependency

****************************************************************************************************
22/12/2025 02:23 - Catalog UI Improvements + Storage Checks + v014 Fix - v015
****************************************************************************************************
- Description:
  Fixed scrolling in the package dialog, added space checks before bulk downloads, and correctly applied the v014 changes (LazyColumn, search, counter).

- Changes:
  Fix: Package description now scrolls together with search and ROMs (does not block the UI)
  Fix: v014 correctly applied - LazyColumn+items for files (prevents ANR)
  Fix: v014 correctly applied - Search field inside the package dialog
  Fix: v014 correctly applied - Filtered file counter (X of Y)
  New: Available storage check before Download All
  New: Alert dialog when there is not enough space (shows required, available, and missing space)
  New: getAvailableSpace and checkStorageForDownload functions in RomDownloader
  New: Book icon for the TV manual (replaces the question mark)

****************************************************************************************************
21/12/2025 04:20 - Fix ANR in Catalog + Search in Packages - v014
****************************************************************************************************
- Description:
  Fixed the ANR that blocked the app when opening large packages (347k+ files) in the catalog. Added a search field inside the package dialog.

- Changes:
  Fix: Changed file rendering from verticalScroll+forEach to LazyColumn+items (renders only ~20 visible items)
  New: Search field to filter ROMs by name inside a package
  New: Filtered file counter when search is active

****************************************************************************************************
19/12/2025 01:41 - Mobile Carousel Improvement - v013
****************************************************************************************************
- Description:
  Simplified the game carousel in the mobile interface by removing 3D effects to improve swipe compatibility on all devices.

****************************************************************************************************
18/12/2025 22:01 - Versioning Script Fix - Beta_v012
****************************************************************************************************
- Description:
  Script adjustments for compatibility with this project.

- Changes:
  Fixed regex to detect Beta_vXXX and Alfa_vXXX
  Removed unnecessary version.txt code

****************************************************************************************************
18/12/2025 21:51 - Download UX Optimization (Optimistic UI) - Beta_v011
****************************************************************************************************
- Description:
  Improved immediate visual feedback for Archive.org downloads. When pressing download, the indicator now changes instantly to a progress circle.

- Changes:
  Optimistic UI in the catalog (immediate CircularProgressIndicator)
  Fixed isFileDownloaded to account for in-memory downloads
  Unified mobile/TV through a shared CatalogScreen

****************************************************************************************************
18/12/2025 17:06 - Backup Before Download UI Fixes - v010
****************************************************************************************************
- Description:
  Safety backup requested before fixing download indicators and library refresh.

****************************************************************************************************
18/12/2025 15:00 - Fix Archive.org Parsing (R8) - Beta_v012
****************************************************************************************************
- Description:
  Critical fix for the Archive.org parsing error in release builds. [FIX] Added @Keep annotations to Archive.org data models to prevent R8 obfuscation issues.

****************************************************************************************************
18/12/2025 05:43 - Final Polish & Music Persistence - Beta_v011
****************************************************************************************************
- Description:
  Final UI and music persistence fixes for the release candidate.

- Changes:
  [FIX] Music state persistence (does not turn itself back on)
  [FIX] Save button layout in the SMB dialog (mobile)
  [FIX] Scroll and version in the About dialog

****************************************************************************************************
18/12/2025 04:56 - Fix Critical Mobile Permissions - Beta_v010
****************************************************************************************************
- Description:
  Critical fix for folder selection on Android 10 (Legacy Storage) and catalog error handling.

- Changes:
  [FIX] Implemented legacy permission fallback (Android 10) for phones with broken SAF (system picker bypass)
  [FIX] Explicit network error handling in ArchiveOrgClient (prevents silent empty lists)
  [FIX] Automatic EmulAI_Roms folder configuration when legacy permission is granted

****************************************************************************************************
17/12/2025 20:53 - Production Release - v009
****************************************************************************************************
- Description:
  Full preparation for Google Play and source code publication.

- Changes:
  Unified build script
  GitHub Sync automation
  Signed AAB generation
  R8/ProGuard rule fixes
  README.md update

****************************************************************************************************
17/12/2025 17:34 - V8.9: SMB Deletion Support - Beta_v009
****************************************************************************************************
- Description:
  Implementation of ROM deletion for SMB shares, ensuring functionality on both TV and mobile interfaces.

- Changes:
  - Implemented delete() in StorageProvider for SMB, local, and SAF
  - Added deleteFile() to SmbClient
  - Centralized deletion logic in LemuroidLibrary
  - Refactored GameInteractor to support remote file deletion
  - Fixed compilation errors in DI modules

****************************************************************************************************
17/12/2025 13:26 - V8.8: Fix SMB Library Mobile + UI Improvements - Beta_v009
****************************************************************************************************
- Description:
  Fixed the SMB library system on mobile and added visual improvements.

- Changes:
  [FIX] V8.8: Mobile now stores KEY_SMB_LIBRARY_SHARE correctly (aligned with TV)
  [FIX] SMB path parsing to extract share and subpath in SettingsScreen.kt
  [UI] Changed 'Cloud/Nube' to 'Archive.org' in catalog filters for Google Play transparency
  [FIX] V8.7: Regenerated coverFrontUrl for existing games during rescan
  [FIX] V8.6: Automatic thumbnail post-processing when metadata exists but thumbnail=null

****************************************************************************************************
17/12/2025 12:55 - V8.8: Fix SMB Library Mobile + UI Improvements - Beta_v009
****************************************************************************************************
- Description:
  Fixed the SMB library system on mobile and added visual improvements.

- Changes:
  [FIX] V8.8: Mobile now stores KEY_SMB_LIBRARY_SHARE correctly (aligned with TV)
  [FIX] SMB path parsing to extract share and subpath in SettingsScreen.kt
  [UI] Changed 'Cloud/Nube' to 'Archive.org' in catalog filters for Google Play transparency
  [FIX] V8.7: Regenerated coverFrontUrl for existing games during rescan
  [FIX] V8.6: Automatic thumbnail post-processing when metadata exists but thumbnail=null

****************************************************************************************************
17/12/2025 05:48 - V8.6-V8.7: Fix Thumbnails and Metadata - Beta_v009
****************************************************************************************************
- Description:
  Complete fix for the thumbnail and metadata system for cloud/SMB ROMs.

- Changes:
  [FIX] V8.6: Thumbnail post-processing - automatically generates the URL when metadata exists but thumbnail=null
  [FIX] V8.7: Regenerates coverFrontUrl for existing games during library rescan
  [FIX] TheGamesDB API key hardcoded by default (fallback if the user does not configure it)
  [FIX] Name-based thumbnail fallback in LibretroDBMetadataProvider when CRC is unavailable
  [FIX] V8.5 SMB library cache for downloaded-file checkmarks

****************************************************************************************************
17/12/2025 05:11 - V8.3-V8.5 - Critical SMB Download Fix - Beta_v009
****************************************************************************************************
- Description:
  Critical fix for SMB downloads that were failing with JobCancellationException. RomDownloader is now a Singleton with an internal SmbClient and an SMB cache for checking existing files.

- Changes:
  [FIX] V8.3: Promoted RomDownloader to Singleton (@PerApp)
  [FIX] V8.4: SmbClient is now internal to RomDownloader (not provided from ViewModel)
  [FIX] V8.5: SMB library cache for checking existing files
  [FIX] Resolved JobCancellationException in large SMB uploads

****************************************************************************************************
17/12/2025 03:38 - V8.2 - Fix SMB Downloads & UI Ghosting - Beta_v022
****************************************************************************************************
- Description:
  Implemented a centralized RomDownloader architecture and fixed the 'Split Brain' issue that caused the UI to not recognize SMB downloads.

- Changes:
  - Fixed the visual bug (Ghost Checks) on the SMB tab.
  - Fixed the destination bug (downloads were landing in /Documents instead of the NAS).
  - Unified the RomDownloader instance between ViewModel and UI.

****************************************************************************************************
16/12/2025 10:09 - beta - Beta_v021
****************************************************************************************************
- Description:
  New version

- Changes:
  Catalog fix for TV mode, making it navigable

****************************************************************************************************
16/12/2025 09:41 - v020 - Beta_v021
****************************************************************************************************
- Description:
  New version

- Changes:
  Catalog fix for TV mode, making it navigable

****************************************************************************************************
16/12/2025 09:39 - v020 - Beta_v020
****************************************************************************************************
- Description:
  New version

- Changes:
  Changes pending specification

****************************************************************************************************
15/12/2025 18:58 - v019 - Beta_v019
****************************************************************************************************
- Description:
  Fix for cursor getting stuck in the TV catalog search field

- Changes:
  D-Pad DOWN now exits the search field
  Added onPreviewKeyEvent to OutlinedTextField

****************************************************************************************************
15/12/2025 16:41 - Critical TV Fix: Permissions and ROM Visibility - Beta_v018
****************************************************************************************************
- Description:
  Definitive fix for ROM visibility on Android TV (Scoped Storage). Implemented MANAGE_EXTERNAL_STORAGE and an extension-based identification fallback for games without metadata.

- Changes:
  - [FIX] Restored visibility of older ROMs on Android 11+ (TV).
  - [FIX] Implemented MANAGE_EXTERNAL_STORAGE permission in the TV flow.
  - [FIX] Added fallback: if metadata fails, identify the system by extension (.sfc, .nes, etc).
  - [FIX] Fixed crashes caused by missing configuration intents.

****************************************************************************************************
15/12/2025 14:06 - Critical TV Fix: Permissions and ROM Visibility - Beta_v017
****************************************************************************************************
- Description:
  Definitive fix for ROM visibility on Android TV (Scoped Storage). Implemented MANAGE_EXTERNAL_STORAGE and an extension-based identification fallback for games without metadata.

- Changes:
  - [FIX] Restored visibility of older ROMs on Android 11+ (TV).
  - [FIX] Implemented MANAGE_EXTERNAL_STORAGE permission in the TV flow.
  - [FIX] Added fallback: if metadata fails, identify the system by extension (.sfc, .nes, etc).
  - [FIX] Fixed crashes caused by missing configuration intents.

****************************************************************************************************
14/12/2025 16:43 - SAF Persistence & Library Fix - Beta_v016
****************************************************************************************************
- Description:
  Critical fix for SAF persistence on phones/tablets and library rendering.

- Changes:
  Unified persistence through Harmony SharedPreferences (MainActivity and StorageFrameworkPickerLauncher)
  Added write permission (FLAG_GRANT_WRITE_URI_PERMISSION) for SAF
  Fixed StorageAccessFrameworkProvider to read the correct URI (fixes empty library)
  Validated full download and deletion support in SAF mode

****************************************************************************************************
13/12/2025 12:48 - TheGamesDB Implementation and Smart SMB - Beta_v015
****************************************************************************************************
- Description:
  Replaced ScreenScraper and improved downloads.

- Changes:
  Replaced ScreenScraper (user/pass) with TheGamesDB (API Key BYOK)
  New metadata configuration section with API key
  Implemented Smart Organization for SMB downloads (Temporary Folder -> Scan -> Destination)
  Fixed text color in the catalog search bar
  Automated Versioning: APK version now syncs with Changelog

****************************************************************************************************
13/12/2025 12:44 - TheGamesDB Implementation and Smart SMB - Beta_v014
****************************************************************************************************
- Description:
  Replaced ScreenScraper and improved downloads.

- Changes:
  Replaced ScreenScraper (user/pass) with TheGamesDB (API Key BYOK)
  New metadata configuration section with API key
  Implemented Smart Organization for SMB downloads (Temporary Folder -> Scan -> Destination)
  Fixed text color in the catalog search bar

****************************************************************************************************
13/12/2025 02:07 - Smart SMB Organization & Deletion Fixes - Beta_v013
****************************************************************************************************
- Description:
  Implemented intelligent SMB downloading (Temp->Scan->Move) using GameMetadataProvider to ensure ROMs are placed in the correct system subfolders. Fixed the persistent 'Ghost Games' issue by enforcing physical file deletion through SAF in GameInteractor. Resolved compilation issues in CatalogScreen and RomDownloader.

****************************************************************************************************
12/12/2025 17:32 - Premium Carousel and Audio Finalized - Beta_v012
****************************************************************************************************
- Description:
  Full implementation of the 3D Coverflow carousel, 3 view modes (Carousel, List, Grid), and ambient audio system.

- Changes:
  Tuned and centered 3D Coverflow carousel
  3 implemented view modes
  Ambient audio with intro and normalization (22kHz, no initial fadeout)
  Visual fixes in the list view (text colors)

****************************************************************************************************
12/12/2025 15:03 - Premium Carousel and Audio - Beta_v011
****************************************************************************************************
- Description:
  Implemented the 3D carousel, 3 view modes, and a music system with intro.

- Changes:
  3D Coverflow carousel
  3 view modes: Carousel/Grid/List
  Auto-start music with intro
  Audio normalized to 22kHz

****************************************************************************************************
12/12/2025 12:06 - Session 12-Dec-2024 - Beta_v010
****************************************************************************************************
- Description:
  Icon and bulk deletion

- Changes:
  Phoenix Down icon (logo_simple.png) with correct adaptive icon
  Background with dark gradient (biblioteca.jpg)
  Trash FAB in HomeScreen for multi-select mode
  Checkboxes on games for multiselect
  Bulk deletion confirmation dialog
  Individual deletion from the context menu
  EN/ES deletion strings

****************************************************************************************************
12/12/2025 11:21 - Session 12-Dec-2024 - Alfa_v009
****************************************************************************************************
- Description:
  Icon and bulk deletion

- Changes:
  Phoenix Down icon (logo_simple.png) with correct adaptive icon
  Background with dark gradient (biblioteca.jpg)
  Trash FAB in HomeScreen for multi-select mode
  Checkboxes on games for multiselect
  Bulk deletion confirmation dialog
  Individual deletion from the context menu
  EN/ES deletion strings

****************************************************************************************************
12/12/2025 11:02 - Session 11-Dec-2024 - Beta_v008
****************************************************************************************************
- Description:
  Full internationalization and initial branding

- Changes:
  Complete Spanish/English internationalization (Disclaimer, GameEdit, SourceDialogs, CatalogScreen)
  Integrated Phoenix Down icon (Logo.png in 5 densities)
  Copied biblioteca.jpg and banner.jpg to drawable
  Updated AndroidManifest with the new icon

****************************************************************************************************
11/12/2025 21:28 - Session 11-Dec-2024 - Beta_v007
****************************************************************************************************
- Description:
  Full internationalization and initial branding

- Changes:
  Complete Spanish/English internationalization (Disclaimer, GameEdit, SourceDialogs, CatalogScreen)
  Integrated Phoenix Down icon (Logo.png in 5 densities)
  Copied biblioteca.jpg and banner.jpg to drawable
  Updated AndroidManifest with the new icon

****************************************************************************************************
11/12/2025 18:31 - SMB/Local Sources Integration - Beta_v006
****************************************************************************************************
- Description:
  Full implementation of SMB and local sources with downloading and automatic rescan.

- Changes:
  Simplified SMB form (removed redundant Share Name)
  Smart ROM detection (system by folder, region by name)
  Recursive search up to 10 levels
  Download ROMs from SMB to the local library
  Automatic rescan after download
  Status-based icons (downloading/downloaded/available)
  Functional SMB source editing

****************************************************************************************************
11/12/2025 13:55 - Fix Libretro Core Loading - Beta_v005
****************************************************************************************************
- Description:
  Fixed a libretro core loading issue.

- Changes:
  Removed incompatible symlinks in bundled-cores
  Configured jniLibs.srcDirs to load cores from the original directories
  APK now includes all libretro cores (220MB)
  Games now launch correctly

****************************************************************************************************
10/12/2025 21:49 - Archive.org Catalog - Beta_v004
****************************************************************************************************
- Description:
  Full Archive.org catalog integration for searching and downloading ROMs.

- Changes:
  Added Archive.org API client (ArchiveOrgClient.kt)
  Added multiple-download system (RomDownloader.kt)
  Added the catalog Compose UI (CatalogScreen.kt)
  Filters by system, region, and language
  Sorting by downloads, name, and size
  Infinite pagination
  Automatic rescan after download
  Detection of already downloaded files

****************************************************************************************************
10/12/2025 13:10 - SMB and External Library - Beta_v003
****************************************************************************************************
- Description:
  Added support for importing ROM libraries from external paths including SMB. Background scanning with smbj. Fixed the N64 core to mupen64plus_next_gles3.

- Changes:
  - Import external libraries (local, SAF, SMB)
  - SMB credentials dialog (username/password)
  - SMB scanning with the smbj library
  - IO coroutine scanning to avoid blocking the UI
  - Fixed N64 core to mupen64plus_next_gles3
  - Support for manual paths and folder picker

****************************************************************************************************
09/12/2025 21:44 - SMB and External Library - Beta_v002
****************************************************************************************************
- Description:
  Added support for importing ROM libraries from external paths including SMB. Background scanning with smbj. Fixed the N64 core to mupen64plus_next_gles3.

- Changes:
  - Import external libraries (local, SAF, SMB)
  - SMB credentials dialog (username/password)
  - SMB scanning with the smbj library
  - IO coroutine scanning to avoid blocking the UI
  - Fixed N64 core to mupen64plus_next_gles3
  - Support for manual paths and folder picker

****************************************************************************************************
09/12/2025 20:45 - RetroArch Direct Launch - Alfa_v001
****************************************************************************************************
- Description:
  Implemented direct game launching through RetroArch using an Intent with ROM and LIBRETRO extras. The core path is obtained dynamically through packageManager.getPackageInfo().applicationInfo.dataDir for compatibility across Android devices.

- Changes:
  - Directly launch games from the library into RetroArch
  - Dynamic core path through packageInfo.dataDir
  - Updated documentation with the correct solution
  - Working Intent with ROM and LIBRETRO extras

