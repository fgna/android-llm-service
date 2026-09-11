# Client integration

Android LLM Service is designed to be consumed as a separately installed Android app. Client projects should depend on the Binder/AIDL contract, not on the service source tree.

## Service identity

```text
Package:    de.fgna.androidllmservice
Action:     de.fgna.androidllmservice.BIND
Permission: de.fgna.androidllmservice.permission.BIND_LLM_SERVICE
```

A client should query for the package before binding and handle the service being missing or unavailable.

## AIDL contract

Clients currently need matching copies of these AIDL files:

```text
app/src/main/aidl/de/fgna/androidllmservice/ILlmService.aidl
app/src/main/aidl/de/fgna/androidllmservice/ILlmCallback.aidl
```

The service currently exposes:

```aidl
boolean isModelReady();
String getActiveModelName();
void generate(String prompt, ILlmCallback callback);
void generateWithImage(String prompt, in ParcelFileDescriptor image, ILlmCallback callback);
```

`generateWithImage` transfers the image through a file descriptor. Clients should close their local descriptor when ownership/lifecycle permits and should not assume the service keeps access after the request completes.

## Client manifest

Clients must declare the service permission and package visibility:

```xml
<uses-permission android:name="de.fgna.androidllmservice.permission.BIND_LLM_SERVICE" />

<queries>
    <package android:name="de.fgna.androidllmservice" />
</queries>
```

The permission uses Android's `normal` protection level so independently signed clients can request it. The Binder service requires the permission directly on the service component; no extra approval flow is used.

## Binding example

Bind explicitly to the service package and action:

```kotlin
val intent = Intent("de.fgna.androidllmservice.BIND").apply {
    setPackage("de.fgna.androidllmservice")
}

val bound = context.bindService(
    intent,
    serviceConnection,
    Context.BIND_AUTO_CREATE,
)
```

If the client does not declare `de.fgna.androidllmservice.permission.BIND_LLM_SERVICE`, Android rejects access to the exported service. A client that declares the permission can bind without any package-specific or certificate-specific approval step.

## Runtime behavior

Clients should expect these states and handle them explicitly:

1. Service package is not installed.
2. Service is installed but cannot be bound, for example because the permission declaration is missing.
3. Service is available but no model is registered.
4. Model is ready and generation can be requested.
5. Runtime initialization or generation fails and the callback returns an error.

A client should never silently fall back to a remote provider because the local service is unavailable. Any remote inference path should be an explicit product decision and user-visible configuration.

## Compatibility rules

The AIDL contract is a public API boundary. Changes should follow these rules:

- Do not reorder existing methods.
- Do not remove existing methods without a migration path.
- Do not change parameter or callback semantics in place.
- Prefer additive methods for new capabilities.
- Keep existing client behavior valid across compatible service updates.
- Treat model/provider metadata additions as optional for older clients.

If a future breaking change is unavoidable, introduce an explicit versioned contract rather than changing the existing interface silently.

## Recommended client UX

When local inference is required, show actionable states rather than generic errors. Useful messages include:

- Android LLM Service is not installed.
- Android LLM Service cannot be accessed; verify the client declares the Binder permission.
- Open Android LLM Service and select a model first.
- Local inference failed; open the service diagnostics for details.

This keeps the dependency understandable without coupling the client UI to service internals.
