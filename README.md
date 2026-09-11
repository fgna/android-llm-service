# Android LLM Service

A small Android service that provides one shared on-device LLM runtime to multiple apps through Binder/AIDL.

[![Android](https://github.com/fgna/android-llm-service/actions/workflows/android.yml/badge.svg)](https://github.com/fgna/android-llm-service/actions/workflows/android.yml)

Android LLM Service keeps local inference behind one stable Android-facing boundary. Client apps can submit text or image-assisted prompts without embedding their own LiteRT-LM runtime or maintaining another multi-gigabyte model copy.

The current baseline is intentionally focused: one registered `.litertlm` model, local inference on the phone, a Binder/AIDL API, diagnostics, and a small management UI. LAN and external API providers are future extensions rather than requirements for the baseline service.

## What it does

- Registers an existing `.litertlm` model through Android's Storage Access Framework.
- Keeps persistent access to that model without copying it into app-private storage.
- Runs local inference through LiteRT-LM, preferring GPU and falling back to CPU when needed.
- Exposes text generation over Binder/AIDL.
- Exposes image-assisted generation over Binder/AIDL using a `ParcelFileDescriptor`.
- Provides a small UI for model selection, diagnostics and direct prompt testing.
- Lets multiple trusted client apps share the same service-owned runtime and model registration.

## Architecture

```text
Client app
   │
   │ Binder / AIDL
   ▼
Android LLM Service
   │
   │ LiteRT-LM
   ▼
Registered .litertlm model
```

Typical clients include apps such as Personal Library or TaskOS. They keep their product-specific logic while delegating local inference to this service.

## Requirements

- Android 8.0 / API 26 or newer
- JDK 17 for local builds
- Android SDK with compile SDK 36 available
- A compatible `.litertlm` model already present on the Android device

Current LiteRT-LM dependency:

```text
com.google.ai.edge.litertlm:litertlm-android:0.16.1
```

## Build

The repository includes a self-bootstrapping `gradlew` launcher, so a system-wide Gradle installation is not required.

```bash
chmod +x gradlew
./gradlew test
./gradlew assembleDebug
```

The debug APK is written to:

```text
app/build/outputs/apk/debug/app-debug.apk
```

Install or update it with:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## First run

1. Open **Android LLM Service**.
2. Select a `.litertlm` model that is already stored on the phone.
3. Run diagnostics if you want to verify model access and runtime initialization.
4. Send a prompt from the built-in test UI.
5. Reopen the app later; the registered model should still be available without selecting it again.

The service intentionally does not duplicate the model into its private storage.

## Client integration

The exported Binder service uses:

```text
Package: de.fgna.androidllmservice
Action:  de.fgna.androidllmservice.BIND
Permission: de.fgna.androidllmservice.permission.BIND_LLM_SERVICE
```

The AIDL contract lives under:

```text
app/src/main/aidl/de/fgna/androidllmservice/
```

The current interface supports:

- checking whether a model is ready
- reading the active model name
- text generation
- image-assisted generation

See [`docs/CLIENT_INTEGRATION.md`](docs/CLIENT_INTEGRATION.md) for the binding contract and compatibility rules.

### Important signing constraint

The current Binder permission uses `protectionLevel="signature"`. That is deliberate: it prevents arbitrary apps from using the local inference service.

It also means that a client currently needs to be signed with the same signing key as Android LLM Service. This works for coordinated builds, but it is not yet suitable for independently signed public distribution channels. Tracking issue #10 covers a safer authorization model for independently signed public clients.

## API compatibility

The Binder/AIDL contract is treated as a public interface once clients depend on it. Existing methods should not be removed, reordered or have their semantics changed without an explicit compatibility plan. New capabilities should preferably be additive.

Client apps should treat service availability as optional at runtime and show a clear user-facing message when the service is not installed, not compatible or has no model registered.

## Security and privacy

Inference is local in the current baseline. Prompts and supplied images are processed by the on-device runtime; this repository does not add a remote inference fallback.

The Binder endpoint is exported because other apps need to bind to it, but access is currently restricted by a signature-level permission. See [`SECURITY.md`](SECURITY.md) for the current trust boundary and reporting guidance.

## Project status

The on-device Binder service is the completed baseline version:

- local model registration: complete
- local text inference: complete
- image-assisted Binder requests: complete
- shared Binder/AIDL service: complete
- management/test UI: complete

Possible later extensions include trusted-LAN model servers, explicit external API providers, richer capability reporting and independently signed client authorization.

## License

Licensed under the Apache License 2.0. See [`LICENSE`](LICENSE).
