# Contributing

Thanks for taking an interest in Android LLM Service.

## Development setup

Requirements:

- JDK 17
- Android SDK with compile SDK 36
- an Android device for runtime validation of LiteRT-LM behavior

The repository includes its own Gradle launcher, so a system Gradle installation is not required.

```bash
./gradlew test
./gradlew assembleDebug
```

## Pull requests

Keep changes focused and describe the user-visible or API-visible effect. For Binder/AIDL changes, call out compatibility implications explicitly.

Before opening a pull request:

```bash
./gradlew test
./gradlew assembleDebug
```

For changes that affect model loading, Binder access, GPU/CPU runtime behavior or image generation, device validation is strongly recommended.

## Binder compatibility

Treat the existing AIDL methods as a public contract:

- do not reorder existing methods;
- prefer additive changes;
- do not silently change parameter or callback semantics;
- document breaking changes and provide a migration path.

See `docs/CLIENT_INTEGRATION.md` for the current client contract.

## Security-sensitive changes

Changes to the exported service, permissions, client authorization, model access or remote inference paths need an explicit security review. In particular, do not remove the Binder permission simply to make independently signed clients work.

See `SECURITY.md` and issue #10 for the current trust model.
