# Privacy Policy for Phoenix Down

**Last Updated:** December 17, 2024

This Privacy Policy describes how Phoenix Down ("we", "our", or "the App") handles your information.

## 1. Data Collection and Usage

**We do not collect, store, or share any personal identification information (PII).**

Phoenix Down is an open-source emulator designed to function locally on your device. We do not have any external servers that harvest user data.

## 2. Permissions and Features

The App requires specific permissions to function correctly:

*   **Storage (Read/Write):** Required to access your game files (ROMs) and save game states on your device or NAS.
*   **Internet Access:** Required for:
    *   Downloading game covers/metadata (from TheGamesDB).
    *   Browsing public libraries (Archive.org).
    *   Syncing saves (see below).

## 3. Third-Party Services

### Cloud Save Sync (Google Drive / WebDAV)
If you choose to enable Cloud Sync:
*   The App interacts directly with **Google Drive API** or your WebDAV provider to upload/download your game save files (`.srm`, `.state`).
*   **We do not see, store, or process your login credentials.** Authentication is handled directly via Google's secure OAuth consent screen.
*   The App only accesses the specific folder created for Phoenix Down saves, not your entire Drive.

### Game Metadata
*   The App may fetch game information and cover art from public databases (like TheGamesDB). Ideally, this is anonymous and does not track you.

## 4. Children's Privacy
We do not knowingly collect personal data from children under 13.

## 5. Contact
For any questions regarding privacy or to inspect the source code, please visit our repository:
https://github.com/zinosaure/PhoenixDown
