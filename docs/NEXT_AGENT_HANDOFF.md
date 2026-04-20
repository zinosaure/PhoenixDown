# Phoenix Down - Next Agent Handoff

## 1) Current Snapshot

This repository is a Lemuroid fork branded as Phoenix Down with major additions around:
- Archive.org catalog/download integration
- SMB/NAS support
- Android TV support and TV-specific settings flows
- Metadata/API management

Recent work focused on settings restructuring, branding migration to Phoenix Down, and UI stability.

## 2) What Was Recently Changed

### Covers
- Cover persistence experiments were reverted.
- Current behavior matches the pre-feature state again.
- The cover location row was removed from both mobile and TV settings.

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
- Cover location row is no longer present in settings on mobile and TV.

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

1. SMB behavior can vary by NAS ACLs
- Some shares allow root writes but deny subfolder creation.
- If remote storage support is extended again, validate behavior on real NAS devices.

2. UI performance in covers loading
- Avoid blocking SMB I/O calls in hot UI rendering paths.

3. Branding consistency
- Prefer Phoenix Down naming and the new repository URL in user-facing surfaces and docs.

## 4) Suggested Next Tasks

1. Plan next storage protocols
- WebDAV first, then SFTP, while keeping SMB support intact.

2. Add tests
- Settings layout/order smoke tests (mobile + TV)
- Branding regression checks for exported filenames and launcher identity.

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
