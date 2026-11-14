# CleanMyTV Enhanced 0.8.0-beta.1 — review candidate

Unofficial community fork of CleanMyTV by wingedpanther. MIT licence and upstream copyright retained.

Bundled launcher: 1.7.4-enhanced.2 / code 24, same Android package and repository signing key as Enhanced.1.

## Features

- TV-side Edit Home Screen: row/folder creation, rename, reorder and confirmed deletion; app moves between Dock, rows, folders and All apps with D-pad insertion-position controls, OK confirmation and Back cancellation.
- Long-press app menu: open, move, remove a custom placement, native App Info/uninstall/Force stop controls and optional verified closing through Shizuku.
- Open Apps: actual Android resumable-task snapshots with optional Shizuku access, launch/resume, eligible individual closing and confirmed Close All with per-app results. No usage-history approximation. System, launcher and protected service apps are excluded.
- On-device appearance: supported layouts, tile/Dock sizing, tile spacing, top spacing, icon style/shape, accent presets, wallpaper dimming, labels and section visibility/order.
- Safe permanent theme save/upload from the desktop: save the current look directly on the TV, or import a .cmtvtheme into the installed library. Add or deliberately update/rename an existing entry. Preserve other themes, variants and order.
- On-device installed-theme management: save, apply, update, rename and per-theme opacity/reset.
- Independent tile-background opacity for every installed theme. Portable exports carry effective opacity and supported appearance without device-wide settings. Logos, labels and focus effects remain visible.
- Shelf opening viewport separates the full Dock and active Now Playing card from lower rows/folders; adapts to measured content and configured sizing. Other section orders/layouts retain their arrangement.
- Fixed-time scheduling survives boot and rechecks time/timezone changes, applies installed theme settings, preserves manual choices until the next boundary, and uses local DST rules. Idle alarms complement Home/resume checks.
- Configuration revision checks and serialised desktop writes prevent stale desktop layout replacement. Device polling refreshes TV-side edits while the desktop is idle.
- Enhanced branding, fork-based stable/beta release checks, bundled enhanced APK, macOS arm64/x64 and Windows x64 installer targets, beta documentation and recovery checklist.

## Compatibility and limitations

This candidate has automated test/build verification but has not been installed on the owner's TV. Physical upgrade/data-preservation, focus/scroll behaviour and OEM compatibility must be tested before public distribution.

Shizuku is optional. Without it, use Android's per-app controls; Enhanced does not pretend it has unrestricted task access. Android 11+ may allow on-device Shizuku startup through Wireless debugging; Android 9/10 and many TV firmwares need a computer to start it after reboot. Task-dump formats can differ by vendor; unrecognised formats report unavailable. Launching a listed app requests normal task reuse and does not guarantee exact task-ID/screen restoration. There are no task thumbnails.

Background alarms are inexact and may be delayed during deep sleep; the current theme is checked on Home resume. Sunrise/sunset scheduling is not enabled. Old theme packs without spacing metadata require explicit non-destructive updates to embed their desired spacing; the software does not guess settings from names. A theme installed as a permanent pack needs a valid wallpaper. Interrupted theme staging expires after ten minutes and requires free space for a library copy.

macOS notarisation and Windows code signing are not configured. Platform installers require platform-specific runtime testing. GitHub refused branch creation with 403 for this connection, so the local review commits could not be pushed, a PR could not be opened, and hosted macOS/Windows workflow builds could not be started. No public release, main merge or physical TV change was performed.

See ENHANCED-VALIDATION.md for exact commands/results and ENHANCED-BETA.md for installation, upgrade, rollback, troubleshooting and the physical-device checklist.
