# Releasing Android LLM Service

This project currently uses Git tags and GitHub Releases to mark stable versions.

## Before tagging

1. Make sure the intended release commit is on `main`.
2. Confirm CI is green.
3. Run locally if possible:

```bash
./gradlew test
./gradlew assembleDebug
```

4. Verify the management UI starts and the active model can be queried.
5. Verify Binder/AIDL compatibility for existing clients.
6. Review `README.md`, `SECURITY.md`, and `docs/CLIENT_INTEGRATION.md` for release-relevant changes.

## Versioning

Use semantic versioning where practical:

- patch: fixes with no Binder/AIDL compatibility break
- minor: additive features and compatible API additions
- major: intentional compatibility breaks that require client migration

The first public stable release should be tagged `v1.0.0` once the public repository state is confirmed.

## Tag and release

Create an annotated tag from the release commit and push it:

```bash
git checkout main
git pull
git tag -a v1.0.0 -m "Android LLM Service v1.0.0"
git push origin v1.0.0
```

Then create a GitHub Release from that tag with concise release notes covering user-visible changes, compatibility notes, security changes, and known limitations.

Do not publish signing keys, keystores, model files, local configuration, or credentials as release assets.
