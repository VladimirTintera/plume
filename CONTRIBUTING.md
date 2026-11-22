# Contributing to Plume

Thanks for your interest. Plume is a Material 3 Expressive Telegram client built with
Kotlin Multiplatform and shared Compose UI for Android, iOS and desktop.

**Most developer documentation is in Czech.** [`CLAUDE.md`](CLAUDE.md) holds the rules for
the whole project, and [`docs/poznatky/`](docs/poznatky) has the notes for each feature area.
Read the file for the area you are changing before you start.

**Everything visible on GitHub is in English**: issues, pull request titles and descriptions,
commit messages, branch names and review comments. Older history from before the repository
went public is in Czech.

## Building

1. Clone with submodules. On-device AI lives in the `external/ai-engine` submodule, and
   llama.cpp is nested inside it:

   ```bash
   git submodule update --init --recursive
   ```

2. Get your own `api_id` and `api_hash` at [my.telegram.org](https://my.telegram.org) and
   put them in `local.properties`, which git ignores:

   ```
   telegramApiId=<your api_id>
   telegramApiHash=<your api_hash>
   ```

   Without them the project still builds, but login fails with an explanation. Never
   commit these values.

3. Every build needs the Android NDK (with its license accepted) and CMake `3.31.0` from
   the SDK Manager.

```bash
./gradlew :client:androidApp:assembleDebug   # Android APK
./gradlew :client:desktopApp:run             # desktop
./gradlew :client:shared:testAndroidHostTest :client:core:testAndroidHostTest   # tests on the JVM
```

iOS builds from Xcode (`open iosApp/iosApp.xcodeproj`).

## Pull requests

- **One branch per change**, created from current `main` and named after its content in
  English (`fix/…`, `feat/…`, `docs/…`). Nothing is committed straight to `main`.
- **Every PR adds a release note**, `release-notes/<branch>.md` with `/` replaced by `-`,
  otherwise the "Release notes" check fails:

  ```
  type: feat
  cs: Hledání v seznamu chatů
  en: Search in the chat list
  ```

  `type` is `feat`, `fix` or `internal`. For `internal` changes (docs, CI, refactoring),
  leave out the `cs:` and `en:` lines. Write the text for users, not developers, at most
  100 characters per language. If you don't speak Czech, write the `en:` line and say so
  in the PR; the maintainer will add the `cs:` line. The format is described in
  [`.github/scripts/release_notes.py`](.github/scripts/release_notes.py).
- **Build before you push.** CI does not compile pull requests. Run at least
  `./gradlew :client:androidApp:assembleDebug`, and `./gradlew check` for the source
  checks (translations, hardcoded text, shapes, motion specs…).
- **Say how you tested it.** Describe what changed, why, and how to verify it on a
  device. If you didn't try it on a device, say that in the PR.

The pre-push hook checks for the release note before you push. Enable it once per clone:

```bash
git config core.hooksPath "$(git rev-parse --show-toplevel)/.githooks"
```

## Rules worth knowing up front

- **TDLib stays behind `:client:core`.** It is the only module that can import
  `dev.g000sha256.tdl.*`; the rest of the app talks to domain interfaces. Call generated
  TDLib methods with named arguments.
- **No user-facing text in code.** Strings live in `client/resources/…/composeResources`.
  English is the default language and Czech is a translation; every new key needs both.
- **Follow Material 3 Expressive defaults.** Colors come from `MaterialTheme.colorScheme`,
  animations from `MotionScheme`, and the shared components are in `core/ui`. Check the
  "Material 3 / vzhled" section of `CLAUDE.md` before adding UI.
- **Never show an error message from TDLib to the user.** Translate it through
  `userMessage(fallback)`.

## Translations

Translations into other languages will be handled through Weblate once it is set up.
Until then, open an issue if you want to translate the app.

## Name and trademarks

The app name must not contain "Telegram" unless "Unofficial" comes before it, and the
official Telegram logo must not be used ([Telegram API Terms of Service](https://core.telegram.org/api/terms)).

## License

By contributing you agree that your contribution is licensed under the [MIT License](LICENSE).
