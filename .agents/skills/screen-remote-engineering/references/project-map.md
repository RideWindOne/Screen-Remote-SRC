# Project Map

Use this reference to orient or place code in the Screen-Remote subrepository. Read targeted source after this map; do not treat the map as a substitute for current code.

## Repository shape

- The outer repository orchestrates documentation, builds, and Git submodules.
- The current repository is the nested Android application repository.
- `../external/dadb` is an included Gradle build and supplies ADB protocol plus Android transport support.
- Other directories under `../external/` are upstream/reference submodules. Do not scan or edit them by default.
- Long-lived engineering documentation lives in the outer `../external/wiki/` repository.

Always inspect both statuses:

```bash
git status --short
git -C .. status --short
```

An app edit changes the nested repository first; the outer repository sees only the submodule pointer or dirty marker.

## Current build facts

The current build files are authoritative:

- Kotlin + Jetpack Compose application
- one Gradle application module: `app`
- Java/Kotlin target 21
- Android `minSdk 23`, `targetSdk 37`, `compileSdk 37`
- C/C++17 through CMake for native pairing support
- DADB resolved through `includeBuild("../external/dadb")`
- scrcpy server asset version and SHA-256 pinned in `app/build.gradle.kts`

Some wiki environment versions may lag behind build files. Use the build configuration for exact tool versions and update documentation separately when requested.

## Package boundaries

Production code is under:

`app/src/main/java/com/screen/remote/android`

Use these ownership rules:

- `app`: application entry, top-level assembly, navigation, top-level lifecycle
- `service`: foreground service, keepalive, Android system lifecycle coordination
- `feature`: user-facing behavior, Compose UI, ViewModels, feature-local presentation/data coordination
- `infrastructure`: ADB, scrcpy, media, protocol, transport, codec, decoder, and runtime implementations
- `core`: stable shared models, data/storage foundations, design system, i18n, constants, and utilities

The practical dependency direction is lower-level capability toward higher-level orchestration:

`core -> infrastructure -> feature -> service -> app`

Do not force every feature through a mechanical `ViewModel -> UseCase -> Repository -> Manager` chain. Introduce a boundary only when it owns policy, isolates technology, or has more than one real consumer.

## Main runtime path

Trace remote-control behavior in this order:

1. `feature/session` owns saved session configuration and selection.
2. `feature/remote` starts and presents a remote session.
3. `infrastructure/adb` discovers, pairs, verifies, and opens device transport.
4. `infrastructure/scrcpy/connection/ConnectionLifecycle.kt` orchestrates ADB, server, sockets, metadata, and cleanup.
5. `ConnectionSocketManager.kt` opens protocol channels.
6. `ConnectionMetadataReader.kt` creates video/audio streams from negotiated headers.
7. `infrastructure/media` decodes and renders media.
8. `infrastructure/scrcpy/controller` sends control messages.
9. `infrastructure/scrcpy/session` owns active runtime state and events.
10. `service/ScrcpyForegroundService.kt` coordinates Android foreground lifetime.

For this path, load `connection-safety.md` before making conclusions or edits.

## UI and state conventions

- Reuse components from `core/designsystem`; do not silently fall back to unrelated default Material patterns.
- Put user-visible text in the appropriate i18n object such as `SessionTexts`, `RemoteTexts`, `SettingsTexts`, or `ManagementTexts`.
- Keep configuration state distinct from negotiated/runtime state.
- Keep device capabilities distinct from saved user preferences.
- Avoid new global accessors. Existing globals are not permission to add more.

## Known complexity zones

The codebase contains several UI/support files above the documented 800-line ceiling, including session management, remote display/layout inspection, settings/about, and ADB connection code. A large file is a signal to inspect responsibility, not an instruction to split mechanically.

When simplifying:

- target one responsibility or state transition at a time;
- preserve nearby call sites and observable ordering;
- avoid many tiny files that make the main flow harder to follow;
- prefer deletion and de-duplication over new indirection.

## Tests

Local JVM tests live under `app/src/test`. They cover domain policies, parsing, ADB behavior, media formats, socket ordering, session transitions, controller behavior, and service helpers.

Prefer focused tests adjacent in package and concept. Particularly important regression anchors include:

- `ConnectionSocketOrderTest`
- `ConnectionFailureClassifierTest`
- `ScrcpyStreamProtocolTest`
- codec/parser tests under `infrastructure/media`
- session and connection-candidate policy tests

There is no broad instrumentation suite in the current tree. Compose/device behavior may require build verification and explicit manual/device follow-up.

## Documentation routing

Read only what matches the task:

- orientation and placement: `../external/wiki/Module-Map-and-Boundaries.md`
- boundaries: `../external/wiki/Module-Map-and-Boundaries.md`
- runtime flow: `../external/wiki/Runtime-Main-Path.md`
- engineering and verification: `../external/wiki/Engineering-and-Verification-Rules.md`
- sockets/control: `../external/wiki/Session-Configuration-and-Connection-Lifecycle.md`
- diagnosis: `../external/wiki/Layered-Troubleshooting-Method.md`

Do not load all wiki files for a normal code task.
