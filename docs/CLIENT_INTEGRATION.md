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

Clients should declare the service permission and package visibility:

```xml
<uses-permission android:name="de.fgna.androidllmservice.permission.BIND_LLM_SERVICE" />

<queries>
    <package android:name="de.fgna.androidllmservice" />
</queries>
```

The permission declaration is also used by Android LLM Service to discover candidate client apps in its management UI.

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

Binding can succeed before the client is authorized. Treat Binder calls as the authorization boundary and handle `SecurityException`.

## Authorization flow

Android LLM Service supports two trust paths:

1. **Same signing certificate:** access is automatic.
2. **Independent signing certificate:** the installed client appears under **Clients** in the service UI and the user explicitly approves it.

Approval is tied to both package name and the current SHA-256 signing-certificate fingerprint. If the app is later replaced by a build signed with a different certificate, the old approval no longer applies.

Recommended first-run flow for an independently signed client:

1. Check whether Android LLM Service is installed.
2. Bind to the service.
3. Call `isModelReady()` inside a `try/catch` for `SecurityException`.
4. If denied, tell the user to open Android LLM Service and approve this client under **Clients**.
5. Retry when the user returns.

Example:

```kotlin
val ready = try {
    service.isModelReady
} catch (_: SecurityException) {
    showMessage("Open Android LLM Service and approve this app under Clients.")
    false
}
```

Do not treat a successful `bindService()` call as proof that the client is authorized.

## Runtime behavior

Clients should expect these states and handle them explicitly:

1. Service package is not installed.
2. Service exists but this client has not been approved yet.
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
- This app is not yet approved. Open Android LLM Service → Clients and approve it.
- Open Android LLM Service and select a model first.
- Local inference failed; open the service diagnostics for details.

This keeps the dependency understandable without coupling the client UI to service internals.
