# Android LLM Service

A small Android service that provides one shared on-device LLM runtime to multiple apps through Binder/AIDL.

Android LLM Service keeps local inference behind one stable Android-facing boundary. Client apps can submit text or image-assisted prompts without embedding their own LiteRT-LM runtime or maintaining another multi-gigabyte model copy.

The current baseline is intentionally focused: one registered `.litertlm` model, local inference on the phone, a Binder/AIDL API, diagnostics, client authorization, and a small management UI. LAN and external API providers are future extensions rather than requirements for the baseline service.

## What it does

- Registers an existing `.litertlm` model through Android's Storage Access Framework.
- Keeps persistent access to that model without copying it into app-private storage.
- Runs local inference through LiteRT-LM, preferring GPU and falling back to CPU when needed.
- Exposes text generation over Binder/AIDL.
- Exposes image-assisted generation over Binder/AIDL using a `ParcelFileDescriptor`.
- Lets multiple client apps share the same service-owned runtime and model registration.
- Automatically trusts same-signed clients and lets the user approve independently signed clients.
- Binds approvals to package name plus SHA-256 signing-certificate fingerprint.
- Provides a small UI for model selection, client access, diagnostics and direct prompt testing.
- Uses German UI copy for German system locales and English for all other locales.

## Architecture

```text
Client app
   │
   │ Binder / AIDL
   ▼
Android LLM Service
   │
   ├── client authorization
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
5. Open **Clients** to review apps that request access to the Binder service.
6. Approve independently signed clients that you trust.

The service intentionally does not duplicate the model into its private storage.

## Client integration

The exported Binder service uses:

```text
Package: de.fgna.androidllmservice
Action:  de.fgna.androidllmservice.BIND
Permission marker: de.fgna.androidllmservice.permission.BIND_LLM_SERVICE
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

See [`docs/CLIENT_INTEGRATION.md`](docs/CLIENT_INTEGRATION.md) for the binding contract, approval flow and compatibility rules.

### Client authorization

The service supports both coordinated and independently distributed apps:

- apps signed with the same certificate as Android LLM Service are trusted automatically
- independently signed apps must be explicitly approved under **Clients** in the service UI

Approval is stored against the package name and current signing-certificate fingerprint. A differently signed replacement therefore does not inherit access automatically.

Client apps should still declare `de.fgna.androidllmservice.permission.BIND_LLM_SERVICE` in their manifest. The declaration acts as an explicit opt-in and allows the service UI to discover candidate clients. Authorization itself is enforced by the service for every Binder operation.

## API compatibility

The Binder/AIDL contract is treated as a public interface once clients depend on it. Existing methods should not be removed, reordered or have their semantics changed without an explicit compatibility plan. New capabilities should preferably be additive.

Client apps should treat service availability as optional at runtime and show a clear user-facing message when the service is not installed, not authorized, not compatible or has no model registered.

## Security and privacy

Inference is local in the current baseline. Prompts and supplied images are processed by the on-device runtime; this repository does not add a remote inference fallback.

The Binder endpoint is exported because other apps need to bind to it. Access is restricted at runtime using the calling UID, package identity and signing certificate, with explicit user approval for independently signed clients. See [`SECURITY.md`](SECURITY.md) for the trust boundary and reporting guidance.

## Development and releases

See [`CONTRIBUTING.md`](CONTRIBUTING.md) for contribution guidance, [`CODE_OF_CONDUCT.md`](CODE_OF_CONDUCT.md) for participation expectations, [`CHANGELOG.md`](CHANGELOG.md) for notable changes, and [`docs/RELEASING.md`](docs/RELEASING.md) for the release process.

Real screenshots belong under [`docs/screenshots/`](docs/screenshots/). They are intentionally kept separate from generated or mock imagery so the README only shows actual app output.

## Project status

The on-device Binder service is the completed baseline version:

- local model registration: complete
- local text inference: complete
- image-assisted Binder requests: complete
- shared Binder/AIDL service: complete
- independently signed client authorization: complete
- management/test UI: complete

Possible later extensions include trusted-LAN model servers, explicit external API providers and richer capability reporting.

## License

Licensed under the Apache License 2.0. See [`LICENSE`](LICENSE).
