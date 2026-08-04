# Verification Matrix

Run checks from the Screen-Remote subrepository root unless the command starts with `make`.

## Verification order

1. Run a named or package-focused JVM test for the changed rule.
2. Run `./gradlew testDebugUnitTest` for production Kotlin changes.
3. Run `./gradlew assembleDebug` for app, resource, manifest, Gradle, native, or cross-layer
   changes.
4. Use a device/manual pass for behavior that depends on USB, wireless debugging, foreground
   services, overlays, codecs, rendering, touch, clipboard, or real scrcpy traffic.

Do not run a release build, install/uninstall, mutate devices, regenerate signing material, update
submodules, or download a new scrcpy server unless the user requests or the task specifically
requires it.

## Commands

Focused test:

```bash
cd Screen-Remote
./gradlew testDebugUnitTest --tests '*TestClassName'
```

Unit suite:

```bash
cd Screen-Remote
./gradlew testDebugUnitTest
```

Debug build:

```bash
cd Screen-Remote
./gradlew assembleDebug
```

The outer `make debug` also renames APK outputs and is broader than a compile/build check. Prefer
direct Gradle tasks during implementation.

## Risk-based selection

- **Pure model/parser/policy change:** focused test, then unit suite.
- **Compose/UI state change:** focused state test where possible, unit suite, debug build, then note
  manual UI verification.
- **Manifest/resource/navigation/service change:** unit suite when relevant, debug build, then
  device lifecycle check.
- **ADB/scrcpy/socket/controller/media/session runtime:** focused regression tests from the affected
  package, socket-order test when channel setup is involved, unit suite, debug build, then
  real-device main-chain verification.
- **Gradle/CMake/native/dependency change:** relevant configuration or native task, debug build;
  verify all supported ABIs only when the change is ABI-sensitive.
- **Documentation-only change:** validate paths/links and compare statements with current
  build/source; do not run Android builds by default.
- **External submodule change:** test inside that submodule first, then verify the app integration.
  Do not update the outer pointer unless requested.

## Main-chain device checklist

For connection-sensitive changes, report which of these were actually observed:

1. device discovery or pairing;
2. ADB verification and server start;
3. socket logs in `video -> optional audio -> control` order;
4. video metadata and first rendered frame;
5. audio negotiation/playback when enabled;
6. control input and clipboard behavior;
7. disconnect, cancellation, and resource cleanup;
8. reconnect or transport fallback when in scope.

Never claim device verification from unit tests alone.

## Failure handling

- Preserve the first relevant failure output.
- Fix the owning cause, not downstream symptoms.
- Re-run the narrowest failed check before broadening.
- Distinguish environment/toolchain failures from code failures.
- If build files and wiki versions disagree, use build files for execution and report the
  documentation drift.
