# Information Translation

A pure client-side Fabric mod for **Minecraft 26.1** that translates **chat messages, written books and signs** using free AI models. No API key needed, and nothing to install on the server — it works on any server, including vanilla.

- **Chat** — translated copy appears under the original; several messages are batched into one request.
- **Books** — click the translate button in the top-right of the book screen; the whole book is translated with an original/translation toggle.
- **Signs** — all 4 lines are translated automatically when you look at a sign.
- Target language is fully customizable (default: 简体中文), with a settings screen via **Mod Menu** or the **O** key.

## Install

Requires [Fabric Loader](https://fabricmc.net/use/installer/) (≥ 0.20.1) and [Fabric API](https://modrinth.com/mod/fabric-api) (≥ 0.145.1+26.1). Drop the jar from [Releases](../../releases) into `mods/`; [Mod Menu](https://modrinth.com/mod/mod-menu) is optional but recommended.

## Notes

Uses [Pollinations](https://pollinations.ai) free models by default (rate-limited to ~1 request / 5 s, with automatic retry). Any OpenAI-compatible endpoint can be configured instead. Build from source with JDK 25: `./gradlew build`.

License: [MIT](LICENSE)
