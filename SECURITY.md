# Security

Android LLM Service exposes an Android Binder service so other apps can request local inference. Because prompts and images can contain private data and inference consumes significant device resources, access to that endpoint is part of the security boundary.

## Current trust model

The Binder service is exported but protected by:

```text
de.fgna.androidllmservice.permission.BIND_LLM_SERVICE
```

The permission currently uses Android's `signature` protection level. Only apps signed with the same certificate as Android LLM Service can receive the permission and bind successfully.

Do not remove or downgrade this permission merely to make integration easier. Independently signed public clients need an authorization design that preserves an explicit trust decision; this is tracked in issue #10.

## Data handling

The current baseline performs inference on-device through LiteRT-LM. It does not silently send prompts, images or model data to a remote provider.

The selected model remains in its original storage location. Android LLM Service persists access through the Storage Access Framework rather than copying the model into app-private storage.

## Reporting a vulnerability

Please open a GitHub issue for ordinary bugs. For a security-sensitive report that would expose a practical exploit or private information, avoid publishing exploit details until a private reporting channel is available; contact the repository owner directly through an available GitHub contact method.

When reporting, include the Android version, service version/commit, client package, signing/distribution context and the minimum steps needed to reproduce the problem.
