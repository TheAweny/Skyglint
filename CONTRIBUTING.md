# Contributing

Bug reports, source-backed detector fixtures and focused pull requests are welcome.
Describe the player-visible change and run `./gradlew build` on Java 25.
Native UI/API changes also require the isolated smoke client described in
[development notes](docs/DEVELOPMENT.md).

Keep gameplay input observable: unknown is not zero; a snapshot is not a gain,
kill or level-up. Preserve unrelated HUD elements and every unknown boss UUID.
Do not include real player chat, private diagnostics, tokens or account data.

Contributions are provided under the project's LGPL-3.0-only license.
Third-party source and assets must retain their original notices and provenance.
