# Retromul - Next Agent Handoff

## 1) Current Snapshot

This repository is a Lemuroid fork with major additions around:
- Archive.org catalog/download integration
- SMB/NAS support
- Android TV support and TV-specific settings flows
- Metadata/API management

Recent work focused on settings restructuring, SMB covers persistence, and UI stability.

## 2) What Was Recently Changed

### SMB Covers
- Cover destination folder was switched to `GameCovers` (instead of hidden dot folders).
- Covers attempt SMB write first for SMB libraries.
- If SMB write fails (RO share, ACL, permission mismatch), a local cache mirror is used.
- UI cover model resolution avoids blocking SMB network checks on critical UI render paths.

Main file:
- `src/lemuroid-app/src/main/java/com/swordfish/lemuroid/app/shared/covers/CoverUtils.kt`

### Settings (Mobile + TV)
- Settings order was reorganized to:
  1. Roms
  2. General
  3. System Interaction
  4. Metadata/API
  5. Consoles
- TV includes Metadata/API key editing support.
- Cover location row is present in settings on mobile and TV.

Main files:
- `src/lemuroid-app/src/main/java/com/swordfish/lemuroid/app/mobile/feature/settings/general/SettingsScreen.kt`
- `src/lemuroid-app/src/main/java/com/swordfish/lemuroid/app/tv/settings/TVSettingsFragment.kt`
- `src/lemuroid-app/src/main/res/xml/tv_settings.xml`
- `src/lemuroid-app/src/main/res/values/strings.xml`
- `src/lemuroid-app/src/main/res/values-fr-rFR/strings.xml`

### Mobile Home Visual Fix
- Home list/thumbnail entries gained better card contrast to avoid blending into background.

Main file:
- `src/lemuroid-app/src/main/java/com/swordfish/lemuroid/app/mobile/feature/home/HomeScreen.kt`

## 3) Known Sensitive Areas

1. SMB write checks can vary by NAS ACLs
- Some shares allow root writes but deny subfolder creation.
- Dot-prefixed folder restrictions are common on certain servers.

2. UI performance in covers loading
- Avoid blocking SMB I/O calls in `getCoverModel`/compose list rendering paths.

3. Settings wording consistency
- Ensure user-facing strings for SMB RO/RW remain accurate and localized.

## 4) Suggested Next Tasks

1. Validate SMB cover writes on multiple NAS implementations
- Samba (Linux), Synology, Unraid, Windows SMB.
- Confirm `GameCovers` creation and write behavior for RW shares.

2. Add diagnostics toggle for covers persistence
- Log server/share/path and write result.
- Log fallback reason when local cache is used.

3. Add tests
- Cover persistence fallback (SMB write fail -> local cache)
- Non-blocking cover model resolution
- Settings layout/order smoke tests (mobile + TV)

4. Improve status UX
- If SMB status cannot be checked, show clear neutral wording (not RW by default).

## 5) Build / Validation

Recommended:
```bash
./build.sh
```

Useful validation command:
```bash
bash build.sh 2>&1 | grep -E "^e: |error:|BUILD (SUCCESSFUL|FAILED)" | head -80
```

If configuration cache is stale:
```bash
rm -rf src/.gradle-cache/configuration-cache/
```

## 6) Quick Functional Test Matrix

1. TV + SMB RW
- Configure SMB share with RW.
- Run scan.
- Confirm covers are visible in UI.
- Confirm `GameCovers` appears on NAS and files are written.

2. Mobile + SMB RO
- Configure SMB share as RO.
- Run scan.
- Confirm games and covers still visible.
- Confirm no writes on NAS, fallback to local cache works.
- Confirm settings do not claim RW when share is RO.

3. Local/SAF
- Confirm covers still persist in `GameCovers` under selected storage location.

## 7) Notes for the Next Agent

- Keep changes minimal and avoid refactoring unrelated modules.
- Do not reintroduce blocking SMB checks in UI rendering code.
- Preserve current settings block ordering unless explicitly requested.
- Prefer explicit fallback behavior over optimistic assumptions for SMB writes.
