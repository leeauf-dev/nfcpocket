# Contributing

Thanks for helping improve NFC Pocket.

## Before opening an issue

- Check existing issues first.
- Include the sending and receiving device models, Android/iOS versions, and manufacturer ROM.
- For NFC failures, note whether other physical NDEF tags are detected and whether another HCE app is installed.
- Do not post private URLs or sensitive APDU data.

## Pull requests

Keep changes focused and explain the user-facing reason for them. NFC Pocket intentionally stays small: avoid network permissions, analytics, dependency injection frameworks, and large dependencies.

The project uses Kotlin, Gradle Kotlin DSL, Jetpack Compose, and Material 3. CI builds the debug APK on every push to `main`. Contributors may run Gradle locally if they have an Android toolchain; maintainers can also rely entirely on GitHub Actions.

By contributing, you agree that your contribution is licensed under Apache-2.0.
