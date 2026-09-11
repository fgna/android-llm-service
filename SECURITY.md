# Security

Android LLM Service exposes an Android Binder service so other apps can request local inference. Because prompts and images can contain private data and inference consumes significant device resources, access to that endpoint is part of the security boundary.

## Client trust model

The Binder service is exported so separately installed apps can bind to it. Access is controlled by the custom Android permission:

```text
de.fgna.androidllmservice.permission.BIND_LLM_SERVICE
```

The permission uses `protectionLevel="normal"` so independently signed client apps can request it. The exported Binder service declares that permission directly via `android:permission`, therefore apps that do not request the permission cannot bind to the service.

There is no additional package allowlist, certificate matching or per-client approval state. Declaring the permission is the explicit opt-in and is sufficient for access.

This is intentionally a lower-friction trust model for a user-controlled local app ecosystem. It prevents accidental access from apps that do not declare the permission, but it is not intended to defend against a deliberately malicious app that chooses to request the same normal permission.

## Data handling

The current baseline performs inference on-device through LiteRT-LM. It does not silently send prompts, images or model data to a remote provider.

The selected model remains in its original storage location. Android LLM Service persists access through the Storage Access Framework rather than copying the model into app-private storage.

## Reporting a vulnerability

Please open a GitHub issue for ordinary bugs. For a security-sensitive report that would expose a practical exploit or private information, avoid publishing exploit details until a private reporting channel is available; contact the repository owner directly through an available GitHub contact method.

When reporting, include the Android version, service version/commit, client package, signing/distribution context and the minimum steps needed to reproduce the problem.
