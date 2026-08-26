# Android LLM Service

Central on-device LiteRT-LM runtime for Android. The long-term goal is to let multiple local apps share one model file and one inference runtime instead of keeping separate multi-GB model copies.

## M0

Issue #1 proves the storage/runtime foundation:

- Select an existing `.litertlm` file with Android's Storage Access Framework.
- Persist read access to the selected document URI.
- Do not copy the model into app-private storage.
- Keep the selected `ParcelFileDescriptor` open while LiteRT-LM owns the engine.
- Pass `/proc/self/fd/<fd>` to LiteRT-LM, which currently expects a filesystem path.
- Try GPU first and fall back to CPU if initialization fails.
- Run a local text prompt from the test UI.

The `/proc/self/fd` bridge is intentionally part of M0 validation. It must be proven on the target Android device and storage provider before issue #1 is considered complete.

## Build

The repository includes a self-bootstrapping `gradlew` shell launcher, so a system Gradle installation is not required.

```bash
chmod +x gradlew
./gradlew test
./gradlew assembleDebug
```

APK:

```text
app/build/outputs/apk/debug/app-debug.apk
```

## Device validation

Install without uninstalling an existing build:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell monkey -p de.fgna.androidllmservice -c android.intent.category.LAUNCHER 1
```

Then:

1. Choose an existing `.litertlm` file already stored on the phone.
2. Confirm the UI reports that the model was registered without creating a copy.
3. Run the default prompt and verify a local response.
4. Force-stop and reopen the app. The same model should remain registered without selecting it again.
5. Run a second prompt and record whether GPU or CPU succeeds and the reported initialization/generation times.
6. Optionally compare free storage before/after registration to verify there is no multi-GB duplicate.

## Current dependency

`com.google.ai.edge.litertlm:litertlm-android:0.16.1`

## Next milestone

After M0 is device-validated, issue #2 moves the runtime behind a Binder/AIDL service so trusted apps such as FreyaOS and Personal Library can submit requests without owning or copying the model.
