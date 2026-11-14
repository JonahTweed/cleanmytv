# Enhanced beta validation and review status

Baseline: enhanced/development at abb05340a482fc13a991abef15024b854bffda36. Work is isolated on the local enhanced/community-beta branch; baseline history and main are unchanged.

## Automated checks

- `npm ci --ignore-scripts`: completed; Electron downloaded separately for packaging.
- `npm run sync`: restored the pinned upstream resources; 371 bundled lists verified by the existing tests.
- `npm run typecheck`: passed.
- `npm test`: **184 passed, 15 test files**. Existing suite plus new theme validation/export/transport and beta update-channel tests. An adapter-enumeration error discovered in the restricted test environment is now handled without masking the original connection error.
- `npm run build`: passed; main/preload/renderer production output generated.
- `npm run home`: **signed release build and all 14 Android unit tests passed**. The final launcher includes regressions for Dock removal and group deletion, duplicate-name guards, and native uninstall permission.
- Android package/version metadata verified with aapt; APK signature verified with apksigner against the repository's existing compatibility identity.

The local Android build used the configured SDK 37 platform, JDK 17, Gradle 9.8.2 and an explicit local Gradle executable through CMTV_GRADLE_BIN. Network requests were routed through the environment's required proxy. The standard build script still uses the wrapper by default. The SDK downloader now requests platform/build-tools matching the project's compile SDK. No physical device was contacted for installation or configuration.

## APK identity

- Package: `io.github.wingedpanther.cleanmytv.home`
- Version name: `1.7.4-enhanced.2`
- Version code: `24`
- minSdk: 28; targetSdk: 36; compileSdk: 37.
- Certificate SHA-256: `cf511447bac0b9fb79767fc8f3499328e5082e0fb41ee764984adaa2c7cc0620`.

The signing key was not changed. Matching the owner's already installed certificate remains part of physical upgrade testing; no on-device signature/upgrade result is claimed for this candidate.

## Installer and UI validation

The normal Windows x64 packaging command reached NSIS generation but could not complete: Linux has no system Wine. Retrying with the supported Wine toolset downloaded Wine successfully but its server could not create sockets in this environment. **The final local Windows x64 NSIS cross-build completed successfully**, using electron-builder's own static NSIS uninstaller reader (already used by its macOS fallback) instead of executing that intermediate Windows binary. The installer is 115,230,295 bytes, has a valid PE header and contains an x64 application. The packaged APK matches the final signed APK byte-for-byte (SHA-256 `e1b61db1d5e827bd8eebdb92a70b23c3d03caf3b284ad86a49247b076a608e54`). This cross-built installer has not been run on Windows. The standard installer workflow remains intended for native Windows/macOS hosts.

macOS arm64 and x64 DMG targets are configured but were not built: this host is Linux and no macOS runner can be started through the read-only GitHub connection. Apple notarisation and Windows code signing are not configured.

A local desktop browser smoke-test attempt was blocked by the environment's denied Unix sockets. No interactive desktop UI, remote D-pad behaviour, TV rendering, latency, Shizuku/OEM task operation or physical upgrade is marked as tested. See ENHANCED-BETA.md for the required device/platform checklist.

## GitHub delivery blocker

The connected GitHub integration read the repository and confirmed the baseline. Creating enhanced/community-beta was rejected with `403 Resource not accessible by integration`. Therefore these are **local commits**, not GitHub-hosted commits; no PR was opened and no hosted build was started. The review bundle contains the complete source archive and Git bundle, including commit history. A single coordinated push/PR can be prepared once the connection has repository write access. Do not merge or publish without the owner's explicit approval.

No public release, main merge, physical installation, launcher uninstall, data clear or theme-library deployment was performed.
