# Security

Android LLM Service exposes an Android Binder service so other apps can request local inference. Because prompts and images can contain private data and inference consumes significant device resources, access to that endpoint is part of the security boundary.

## Client trust model

The Binder service is exported so separately installed apps can bind to it. Authorization is enforced inside the service on every Binder operation.

Client apps opt in by declaring:

```text
de.fgna.androidllmservice.permission.BIND_LLM_SERVICE
```

That declaration is used as a discovery marker. It is not sufficient by itself to access inference.

A caller is authorized when either:

1. it is signed with the same certificate as Android LLM Service, or
2. the user explicitly approves the installed client in the Android LLM Service UI.

For user-approved clients, the approval is stored against both the package name and the current SHA-256 signing-certificate fingerprint. A different package cannot reuse the approval, and replacing the approved app with a build signed by a different certificate invalidates the approval automatically.

Unauthorized callers can bind to the exported service but Binder API calls fail with `SecurityException` before model state or inference is accessed.

## Why the system permission is not the authorization boundary

The custom permission remains declared with `protectionLevel="signature"` for compatibility and as an explicit client opt-in marker. The service component itself no longer relies on Android's manifest-level signature check, because that would make independently signed public clients impossible to authorize.

Do not remove the runtime package-and-certificate verification or replace it with a package-name-only allowlist. Package names alone are not a sufficient trust signal for public builds.

## Data handling

The current baseline performs inference on-device through LiteRT-LM. It does not silently send prompts, images or model data to a remote provider.

The selected model remains in its original storage location. Android LLM Service persists access through the Storage Access Framework rather than copying the model into app-private storage.

## Reporting a vulnerability

Please open a GitHub issue for ordinary bugs. For a security-sensitive report that would expose a practical exploit or private information, avoid publishing exploit details until a private reporting channel is available; contact the repository owner directly through an available GitHub contact method.

When reporting, include the Android version, service version/commit, client package, signing/distribution context and the minimum steps needed to reproduce the problem.
