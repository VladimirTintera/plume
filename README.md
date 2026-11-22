# Plume

An unofficial Telegram client built around Material 3 Expressive. Kotlin Multiplatform with
shared Compose UI for Android, Wear OS, desktop (macOS, Windows, Linux) and iOS.

> Plume uses the [Telegram API](https://core.telegram.org/api) through
> [TDLib](https://core.telegram.org/tdlib). It is not an official Telegram product and is not
> affiliated with Telegram.

## Why the source is public

A messaging client sees everything: your account, your messages, your contacts. You should not
have to take that on trust. The full source of the app is here, and you can check what it does
and build it yourself.

Official builds are made by the GitHub Actions workflows in
[`.github/workflows`](.github/workflows), from the tagged commit.

## What it does

- **Chats**: chat list with folders, archive, pinned and unread groups, drafts, multiple
  accounts, search across chats, public chats and messages.
- **Conversations**: history, live messages, replies, forwarding, editing, reactions, pinned
  messages, polls and quizzes, forum topics, scheduled and silent messages, link previews and
  formatting.
- **Media**: photos, videos and albums, files, voice messages and video messages, stickers,
  GIFs and custom emoji, a media viewer with video and picture-in-picture, music playback.
- **Stories**: viewing, reactions, replies and posting.
- **Bots and mini apps**: inline buttons, keyboards, commands, inline bots and mini apps.
- **Calls**: voice and video calls on Android and desktop.
- **Privacy and security**: secret chats, app lock with PIN and biometrics, an encrypted local
  database, privacy settings, two-step verification, passkeys, active sessions.
- **On-device AI (optional)**: summaries, reply suggestions, scam warnings and photo
  descriptions, running locally on Gemini Nano, Gemma, Qwen, EuroLLM or Aya. Models are
  downloaded only when you ask, and nothing is sent to an AI service.
- **Settings**: theme with wallpaper colors, notifications by chat type with exceptions and
  quiet hours, message text size, app language and more.

The app is in English and Czech. The roadmaps list what is still missing:
[`docs/roadmap.md`](docs/roadmap.md) for the whole app and
[`docs/roadmap-konverzace.md`](docs/roadmap-konverzace.md) for conversations (both in Czech).

## Platforms

| Platform | Status |
|---|---|
| Android | Main platform, released on Google Play |
| Wear OS | Chat list, conversations, tiles and complications; released on Google Play |
| Desktop | macOS (DMG) and Windows (MSI) on [GitHub Releases](../../releases); Linux runs from source |
| iOS | Builds and runs from Xcode, not released yet; push notifications are still waiting for an APNs certificate |

## Privacy

Plume has no backend of its own. Besides Telegram itself, the app talks only to:

- **Firebase Cloud Messaging**, which delivers push notifications on Android while the app is
  not running. The notification content comes encrypted from Telegram.
- **Firebase Crashlytics**, in Android release builds only. It receives crash reports and
  recent log lines. Message content is never written to the log.
- **Hugging Face**, only when you download an on-device AI model.
- **Google Play**, for app updates and for optional components downloaded on demand.

Builds without the Firebase configuration file work normally, just without push notifications
and crash reports.

## Building

```bash
git submodule update --init --recursive   # ai-engine and llama.cpp inside it
```

Get your own `api_id` and `api_hash` at [my.telegram.org](https://my.telegram.org)
(API development tools) and put them in `local.properties`, which git ignores:

```properties
sdk.dir=/path/to/Android/sdk
telegramApiId=1234567
telegramApiHash=abcdef…
```

Without them the project still builds, but login fails with an explanation. Every build needs
the Android NDK (with its license accepted) and CMake `3.31.0` from the SDK Manager.

```bash
./gradlew :client:androidApp:installDebug    # Android
./gradlew :client:desktopApp:run             # desktop
./gradlew :client:shared:testAndroidHostTest :client:core:testAndroidHostTest   # tests on the JVM
```

For iOS, open `iosApp/iosApp.xcodeproj` in Xcode and run it. The Kotlin framework is built
automatically. To run on a device, set `TEAM_ID` in `iosApp/Configuration/Config.xcconfig`.

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md). Security issues go through private reporting, see
[SECURITY.md](SECURITY.md).

Developer documentation is in Czech. [`CLAUDE.md`](CLAUDE.md) covers the architecture,
conventions and pitfalls of the whole project. [`docs/poznatky/`](docs/poznatky) has notes for
each feature area, including releases ([`release.md`](docs/poznatky/release.md)).

## Telegram API terms

Plume follows the [Telegram API Terms of Service](https://core.telegram.org/api/terms). Forks
must follow them too:

- The name must not contain "Telegram" unless "Unofficial" comes before it, and the official
  logo must not be used.
- The store listing must say clearly that the app uses the Telegram API.
- Sponsored messages in channels must be shown unchanged. Plume shows them and reports views
  and clicks.
- Data obtained through the API must not be used to train AI or ML models.

## License

Plume is licensed under the [MIT License](LICENSE). Third-party components keep their own
licenses:

- **TDLib**: [Boost Software License 1.0](https://github.com/tdlib/td/blob/master/LICENSE_1_0.txt).
- **ntgcalls** (calls): [LGPL-3.0](https://github.com/pytgcalls/ntgcalls/blob/master/LICENSE),
  dynamically linked.
- **Figtree** and **Bricolage Grotesque** fonts: SIL Open Font License, see
  [`docs/licenses`](docs/licenses).
- **Material Symbols** (icons): [Apache License 2.0](https://github.com/google/material-design-icons/blob/master/LICENSE).
- **On-device AI models** are not bundled. The app downloads them from Hugging Face only when
  you ask, and whoever downloads a model accepts its license: Gemma (terms on the model card),
  Qwen, EuroLLM and EuroMoE (Apache 2.0), and **Aya Expanse 8B by Cohere Labs
  ([CC BY-NC 4.0](https://creativecommons.org/licenses/by-nc/4.0/) and the Cohere Labs
  acceptable use policy: non-commercial use only, with attribution)**. The app shows the Aya
  license before the download, so a commercial fork must not offer it this way.
- **The Telegram API** is governed by its own
  [Terms of Service](https://core.telegram.org/api/terms), independently of this license.
