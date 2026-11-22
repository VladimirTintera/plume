# Security policy

Plume is an unofficial Telegram client. Its source code is public so that anyone can check
what the app does with their account, messages and device.

## Reporting a vulnerability

**Please do not open a public issue for security problems.**

Report them privately through GitHub: open the **Security** tab of this repository and
choose **Report a vulnerability**. Only the maintainer can see the report.

Please include:

- what the problem is and what an attacker could do with it,
- steps to reproduce, or a proof of concept,
- the app version and platform (Android, Wear OS, iOS, desktop).

Plume is maintained by one person in their spare time. You can expect a reply within
about a week. Fixes go out in the next release, and you will be credited in the release
notes unless you prefer otherwise.

## Supported versions

Only the latest release gets security fixes. Releases come from Google Play and from
[GitHub Releases](../../releases).

## Scope

In scope: the code in this repository, meaning the Plume apps, their build and their
release workflows.

Report these elsewhere:

- **TDLib, MTProto or the Telegram servers**: report them to Telegram through its
  official channels. Plume uses TDLib as it is and does not change the protocol.
- **On-device AI runtime**: open a report in
  [ai-engine](https://github.com/VladimirTintera/ai-engine), the library Plume uses for
  on-device AI.
