# CleanMyTV Enhanced community beta

CleanMyTV Enhanced is an unofficial community fork of [CleanMyTV](https://github.com/wingedpanther/cleanmytv), by wingedpanther. The upstream MIT licence and copyright notices remain in LICENSE. Enhanced is maintained in [JonahTweed/cleanmytv](https://github.com/JonahTweed/cleanmytv). It is not an official upstream release.

Desktop candidate: **0.8.0-beta.2**. Launcher candidate: **1.7.4-enhanced.3**, Android version code **25**. This is a review candidate, not a declaration of physical-device compatibility. Do not distribute publicly until the device checklist has passed.

## Installation and upgrading

Use the installer matching your computer: Apple Silicon DMG, Intel x64 DMG or Windows x64 setup executable. The installer includes the launcher APK; users do not need Node, Java, Gradle, Android Studio, Terminal or manually entered ADB commands. Platform installer builds run in the repository's Build workflow; only successful workflow artifacts should be offered for testing.

On macOS, drag CleanMyTV Enhanced to Applications. The current packaging uses ad hoc signing rather than Apple notarisation: Gatekeeper behaviour must be tested on both processor architectures. On Windows, run setup and select an installation directory; SmartScreen may warn about an unsigned community build. Verify the source and checksums before proceeding.

Connect your TV or streaming device through the desktop application's Setup page. Use the platform-specific debugging/pairing instructions there and approve the connection on your TV. Install/update Home from its Overview page. A normal update must retain the package identity and signing certificate and upgrade in place. **Do not uninstall Home or clear its storage to fix an upgrade error.** Stop if Android reports an incompatible signing certificate.

Before a beta upgrade, keep exported theme files and record your rows, folders and appearance settings. Desktop configuration backups are useful but are not a complete backup of installed theme-pack variants. Do not assume a single theme export includes your whole device library.

## Editing Home with the remote

Open **Quick settings → Edit Home Screen**. The Quick settings shortcut remains accessible in the top bar/rail. Change layout, icon style/shape, tile and Dock sizes, tile spacing, top spacing, accent presets, wallpaper dimming, labels and section visibility. Left/right changes numeric values. Section controls use OK to show/hide and left/right to change order; the actual order is displayed below.

Create rows and folders, rename them, move them earlier/later, or delete them with confirmation. Deleting a row/folder does not uninstall applications. Applications released from folders become available in All apps; if that section is hidden, show it in Edit Home Screen.

Hold OK on an app for Open, Move, section removal and applicable Android management actions. Move opens a destination/position editor: focus the destination control, use up/down to choose Dock, All apps, a row or a folder; left/right changes insertion position; OK commits; Back cancels without changing the saved layout. The editor also provides an app list for moving applications into empty groups. This is a D-pad destination/position editor, not a drag animation on the Home tiles.

Moves relocate only the selected source shortcut; independent shortcuts in other groups remain. The general app chooser adds a destination shortcut without removing another placement; the app can still appear in the general All apps library. Dock holds at most eight apps; a custom row/folder holds at most sixty. Existing hidden/locked applications retain their lock policy. Native uninstall uses Android confirmation. App Info/Force stop opens the device's own settings where required.

The desktop reads current configuration when connecting and periodically while idle. Revision checks reject stale desktop writes, and slider writes are serialised. If settings changed on the TV, refresh and repeat your edit rather than overwriting the newer layout.

## Open Apps and closing

Android does not expose the complete cross-app task list to ordinary launchers. Enhanced therefore does **not** substitute launch history or UsageStats for an Open Apps list. Without optional elevated access, the screen explains the limitation and offers Android's own per-app settings.

For compatible devices, optional [Shizuku](https://shizuku.rikka.app/guide/setup/) access enables task snapshots and force-stop requests without a permanently connected computer. Install Shizuku from its official distribution, start it using its supported setup, open Enhanced's Open Apps screen and authorise it. On Android 11+ with Wireless debugging, startup may be possible entirely on-device. Android 9/10 usually needs a computer to restart Shizuku after each reboot. Fire OS and vendor TV firmwares can lack the required setup screens. Root is not required or requested.

Press Refresh after granting permission or starting Shizuku. The list shows actual Android resumable task records, not a promise that the application's process is currently executing. Switching opens the application's launcher activity with a request to reuse its task; individual apps decide whether to restore the exact previous screen. Task-stack thumbnails and exact task-ID restoration are not provided in this candidate.

Close App reports whether Android's per-user stopped flag could be verified. Unsupported commands, timeouts and lost permission are errors, never silent successes. Close All requires confirmation, attempts only eligible applications represented in the task snapshot, and reports results per application. It excludes Home, Shizuku, system applications, other Home handlers, accessibility apps and applications declaring protected Android service bindings. This intentionally excludes some user applications that are technically stoppable. The list is a snapshot; refresh to update it.

Force-stopping interrupts playback and may discard unsaved work. No claim is made that bulk closing improves device performance; Android normally manages cached processes itself.

## Themes

In **Look & wallpaper → Themes**, enter a name and choose **Save Current Theme to TV**. It captures the active wallpaper and supported appearance on the device without downloading/reuploading that wallpaper. Choose **Add a new theme**, or select an existing installed theme to update/rename it. New names that collide with another theme are rejected; select that existing entry deliberately to update it.

**Upload theme file to TV** adds a .cmtvtheme file permanently rather than just applying it. Files must contain a valid wallpaper to become installed packs. For a look-only theme, load it, choose a wallpaper, then save the current look. **Save this look…** still exports a portable file; **Load a theme file…** still applies a file temporarily.

Theme installation stages a copy of the library and merges the changed entries. Existing theme order, unrelated metadata and wallpaper variants are retained. A failed transfer does not commit the staged library. An interrupted upload reserves staging for up to ten minutes; retry after that interval. The previous directory is recovered if the process stops during the final swap. Staging requires free storage for a second copy of the installed library. Old launchers without safe-merge capability are blocked from this upload path until updated.

Each installed theme has its own 0–100% tile-background opacity slider and Reset. Changes are stored by theme ID. The current-theme control and theme switching use the same effective value. Reset removes the personal override and restores the embedded default (100% for old packs without one). Logos, labels and focus treatments remain visible; an app's native opaque banner artwork cannot have its background removed.

Portable theme exports carry the effective opacity and supported appearance, including spacing; they omit device-wide override maps, rows, folders and personal settings. Theme defaults remain in the installed pack. To change its spacing, apply it, adjust top spacing and update that theme from the current look. Existing packs missing spacing keep the current spacing rather than guessing settings from their names. Check each older pack's intended spacing on-device and explicitly update packs that lack it.

On the TV, Edit Home Screen → Manage installed themes supports applying, saving the current look, renaming, updating and per-theme opacity/reset. It preserves all other themes.

## Shelf layout and automatic themes

With Now Playing/Dock ordered before lower sections, the opening Shelf page contains those sections completely; lower rows/folders start at the next viewport. Inactive Now Playing has no reserved card. The opening area's size is measured and top spacing is reduced when needed to fit the Dock. Custom section orders and Split/Rail layouts keep their own arrangement. Scrolling, focus zoom, oversized tiles and very small displays still require device validation.

Fixed-time day/night switching uses local device time and installed theme IDs. Manual selections remain until the next schedule boundary. The schedule checks on Home resume and while Home is visible, and arms an inexact idle-capable alarm for the next boundary. Boot, time and timezone changes re-evaluate it. Android may defer background alarms in deep sleep; the correct theme is applied when Home resumes. No exact-alarm permission is requested. java.time resolves DST gaps forward and overlaps using the earlier occurrence. Identical day/night times do not switch. Sunrise/sunset was investigated but is not enabled: location, polar-day behaviour and privacy need a separate design and validation.

## Recovery and rollback

If an upgrade fails, leave the installed launcher and its data in place. Check free storage, pairing/authorisation and the error in the desktop activity log. Restore the stock launcher through the desktop application's existing recovery controls if Home cannot be used; switching Home does not require deleting Enhanced's data.

Android normally blocks installing a lower version code. For rollback, prepare the known-good source as a new, higher-version-code APK signed with the same certificate, then upgrade in place. Do not advise uninstall/reinstall or clearing data as a normal rollback. A theme-directory recovery copy is internal crash recovery, not a substitute for user backups.

## Supported platforms and beta limits

The project declares Android 9+ and macOS 13+; Windows target is x64 Windows 10/11. These are build targets, not a tested device matrix. Existing Enhanced.1 was tested by the owner on a Chromecast; this new candidate has not yet been installed there. Android TV, Google TV and Fire TV settings, task commands, focus and debugging support differ. No universal compatibility claim is made.

Apple notarisation and Windows code signing are not configured. The desktop application retains its existing application ID and explicitly keeps its existing CleanMyTV configuration directory, including device keys, saved TVs and backups; avoid running upstream and Enhanced desktop companions simultaneously. The Android package remains io.github.wingedpanther.cleanmytv.home. The build uses the repository's existing compatibility key; it must match the installed certificate before device testing.

## Physical-device checklist before public beta

- Upgrade Enhanced.1/code 23 in place; confirm certificate, wallpaper, all six packs and variants, three rows, folder and settings remain. Never uninstall/clear data.
- Exercise long-press handling with different remotes; confirm/cancel moves across all containers; create empty groups; rename/reorder/delete; reconnect desktop and verify both-direction updates and stale-write rejection.
- Test Shelf active/inactive media, 720p/1080p/4K scaling, tile sizes, other section orders, Back/Up focus, theme transitions and Split/Rail.
- Save current look, upload/export/import, update/rename deliberately, interrupt transfers and test low storage. Verify the existing library's order and variants remain byte-for-byte intact.
- Set distinct opacity/default/reset values for six themes, switch by remote/scheduler, reboot and export. Check opaque logos/focus and the intended spacing of every theme.
- Test schedule edits, manual overrides, missed boundaries, reboot, deep sleep, timezone changes and DST.
- Test Shizuku unavailable/denied/restarted, OEM task dump parsing, resumable switching, verified individual closing, Close All confirmation/results and protected-service exclusions. Stop testing if a protected component is affected.
- Install and launch both macOS architectures and Windows x64; verify pairing, included APK version/certificate, upgrades, recovery and fork beta update checks.

Report bugs through the fork's Issues page with desktop/launcher versions, device model, Android version, reproduction steps and a redacted activity log. Do not post pairing codes, keys or personal configuration.
