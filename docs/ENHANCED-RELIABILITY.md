# Enhanced beta 2 — reliability fixes for Astra review

Base: published enhanced/community-beta commit aa6aefa5208d578717fc82bb227998242e0acbba (the original nine commits). New work is isolated on enhanced/reliability-review and retains that commit as an ancestor. Nothing is merged into main, publicly released or deployed to a physical TV.

Desktop: 0.8.0-beta.2. Android launcher: 1.7.4-enhanced.3, version code 25. Android package and compatibility signing key are unchanged. Earlier beta-1 installers/archives do not contain these fixes and should not be used to evaluate them.

## Placement preservation

A move now has a selected source container. It removes only that source shortcut and upserts the destination shortcut. Reordering in the same container changes only that container. Independent copies in other rows, folders and the Dock remain. Selecting an app from the editor's general chooser adds a destination shortcut without assuming which existing shortcut should be removed. Back cancels without persisting the pending move.

A backward-compatible optional dock_apps list records explicit Dock membership after editing. Null/missing means the original implicit Dock rule, so merely upgrading does not rearrange existing installations. Explicit membership permits a Dock shortcut and folder shortcut for the same app. Desktop parsing, preview, ordering and Dock-size controls understand the list; portable themes exclude it. Hidden explicit Dock entries are preserved. Editing first captures the rendered legacy Dock, respecting Other-app and hidden-locked-app filtering. Stale uninstalled entries cannot displace visible apps in legacy Dock calculations.

Folder/row deletion preserves Dock order and other groups; applications remain installed. Row rename and whole-row ordering use shared tested operations, including boundary and duplicate-name checks. Editor saves compare changed fields against the live settings and use the configuration revision to reject conflicting writes rather than overwrite newer edits.

## Consistent PIN policy

All root editing paths use one policy/guard: app context menus, folder long-press, Quick settings, the Home editor, Open Apps and the corresponding activity-open routes. “Also lock Settings” protects these tools using Lock.PANEL. A selected locked app adds that app's PIN target, including when Settings locking is disabled. Existing unlocked-target and lock-duration rules remain in Lock. Missing PIN / disabled app lock keeps editing available as before.

A PIN challenge queues its required targets; cancelling clears the queue and deferred action without opening the requested tool. The editor and pending challenge close on Activity pause, so they cannot remain on screen as an authorised session over sleep or another app. Native app launching still uses the app's own lock check. Shizuku task closing excludes applications that currently require an app PIN, including Close All; it cannot silently stop them through the bulk path.

This inherits the upstream panel's entry-authorisation model; it is not a new device-owner security boundary. Real-device PIN, accessibility, sleep and lock-duration tests remain mandatory.

## Remote reliability

The Compose remote handler recognises OK, Enter, numeric Enter, gamepad A and Menu. A 550 ms hold opens a menu once; repeating DOWN events do not restart the timer. Releasing a held/interrupted press does not open the application. Focus loss, disposal and pause cancel the hold; a resumed repeat without a fresh DOWN cannot reactivate an interrupted press. Pointer activation remains with the TV Surface.

This independently implements patterns identified in Gathin's launcher; no Java/View implementation or assets were copied and no architecture was replaced. The move interaction remains a destination/position dialog: OK confirms, Back cancels. There is no new direct-grid dragging feature.

Tile focus is registered by section/package, with folder-origin fallback. Dialog dismissal restores the available originating target using at most ten layout-frame retries; the first Home target is the fallback if the old tile is no longer attached. Initial Home focus is no longer requested again simply because the first ordered app changed. Vertical and app-row lazy scroll states remain in place while the editor dialog is open. Exact focus of an off-screen destination is not guaranteed; device tests must verify the fallback and bring-into-view behaviour, including custom pinned-focus layouts.

“Back / Cancel” replaces the misleading universal “Return to Home” label in nested editor sheets.

## Regression coverage

The full desktop suite includes explicit Dock membership validation, legacy/null/empty states, independent folder membership, size limits and exclusion from portable themes.

Android tests cover Dock reordering with duplicate row/folder shortcuts; source-specific row and folder moves; adding independent shortcuts; per-row removal; row/folder deletion; whole-row ordering, rename and boundaries; hidden and stale-package cases; legacy/explicit configuration round-trip; all five editor entry policies with Settings/app locks enabled and disabled; short press, repeated hold, held release, cancellation and selection-identity mismatch. Existing theme storage/recovery, opacity, DST, task-parser and input tests remain in the complete run.

Unit tests exercise the shared policy and input state machine. They do not simulate a physical remote, compose focus tree, native settings activity or an actual PIN entry session.

## Required device acceptance

- Start with Dock A/B and A repeated in multiple rows/folders. Reorder A; confirm all unrelated shortcuts and hidden settings remain. Move one selected copy and remove one copy; verify only the intended source changes. Rename/reorder/delete rows and folders; reconnect desktop and verify identical state.
- Cancel a pending move and PIN challenge with Back. Confirm no write/action occurs. Try wrong/right PINs from app/folder long-press and Settings/editor paths. Test Settings locking off with a separately locked app, plus always/15-minute/until-sleep modes. Sleep or open another app during a challenge/editor, then return.
- Test short OK, 550 ms hold, repeated DOWN/UP variants, Enter/gamepad A/Menu, focus change during a hold and pause/resume while held. Confirm there is no release-triggered app launch or duplicate menu.
- Dismiss menus and folders, cancel/confirm moves, reorder the first Dock app, remove the focused application, and test below-fold rows. Check scroll retention and focus fallback at different tile sizes, section orders and pinned-focus settings.
- Upgrade code 23 or 24 to 25 in place. Verify installed themes/variants, wallpaper, scheduler, three rows, folder and settings. Never uninstall or clear data to resolve an upgrade error.

## Delivery

GitHub branch creation was retried and still returned 403 for this integration. The incremental bundle requires aa6aefa and contains only the new review commits on enhanced/reliability-review. Import it into a clone containing the original nine commits, review the new branch, and fast-forward the intended development branch only if appropriate. No reset, history rewrite or force-push is needed. The source archive is a convenience copy, not a replacement for the Git history.

The prior Repository-Comparison report remains historical review evidence; its Dock-placement and editor-entry findings are addressed by this candidate. Broader direct-grid dragging, global focus coordination, stable group-ID migrations and large-folder presentation redesign remain deferred.

## Automated validation of this candidate

- Full Vitest suite: 188 tests passed in 16 files.
- TypeScript checking and desktop production build: passed.
- Full Android JVM suite: 27 passed (13 reliability, 9 Enhanced, 5 Inputs).
- Signed release APK assembly: passed; package io.github.wingedpanther.cleanmytv.home, version 1.7.4-enhanced.3 / code 25.
- apksigner verification: passed. Certificate SHA-256 cf511447bac0b9fb79767fc8f3499328e5082e0fb41ee764984adaa2c7cc0620 matches the unchanged repository compatibility key.

The Windows candidate is cross-built using the same static NSIS uninstaller extraction method as beta 1; native Windows execution is not tested here. macOS installers and physical TV checks remain outstanding. No on-device upgrade, layout/focus timing, real remote, actual PIN-dialog or sleep behaviour is marked as physically tested.
