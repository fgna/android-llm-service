# Changelog

All notable changes to Android LLM Service will be documented here.

The project follows semantic versioning where practical.

## 1.0.0

First public stable release.

- Shared on-device LiteRT-LM runtime exposed through Binder/AIDL.
- Persistent registration of an existing `.litertlm` model without duplicating the model into app-private storage.
- GPU-first inference with CPU fallback.
- Text generation and image-assisted generation through the shared service contract.
- Permission-based access for independently signed client apps via `de.fgna.androidllmservice.permission.BIND_LLM_SERVICE`.
- PocketDev-style management and test UI with model selection, diagnostics and direct prompt execution.
- German UI with English fallback based on system language.
- Public documentation, security guidance, contribution files, repository templates and release instructions.
- Dedicated launcher icon and real English UI screenshot in the public documentation.

### Known limitations

- One active on-device model at a time.
- No Binder cancellation API yet.
- No LAN or remote-provider backend in the baseline release.
- Binder/AIDL compatibility should be treated as stable for existing clients; future changes should be additive where possible.
