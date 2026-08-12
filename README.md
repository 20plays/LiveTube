# LiveTube

A minimal Android YouTube player built for podcasts: add channels and playlists to a **Library**, tap any video, and keep listening with the screen off — or switch to **audio-only** to save data and battery.

```
Paste a link  →  Play  →  Close your phone →  keeps playing
```

## Features

- **Library** — save channels and playlists with a `+`; browse them by title + thumbnail
- **Pagination** — infinite scroll through a channel/playlist feed, or **Load all** to cache every page
- **Audio-only mode** — plays just the audio stream (no video) to save bandwidth and battery. Default on.
- **Background playback** — a Media3 foreground service keeps playing with the screen off, with a media notification (play / pause / stop) and lock-screen media controls
- **Auto-play next** — optional toggle to keep playing through a channel/playlist queue
- **Never AI-dubbed** — when a video has multi-language AI dubbing, only the original language audio track is played
- **Live streams** — plays YouTube live streams, with periodic refresh so the URL doesn't expire
- **Direct media URLs** — the Player tab also accepts raw podcast/audio URLs

## Requirements

- Android 8.0+ (API 26+); works best on Android 10+
- A YouTube link to start with :)

## Install

Download the latest `app-release.apk` from the [Releases page](https://github.com/20plays/LiveTube/releases), allow "Install unknown apps" for your browser/download manager, and open the APK.

On first launch, grant the notification permission so the playback notification shows up.

## Build from source

```bash
# JDK 17
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk   # path depends on your system
./gradlew assembleRelease
```

The signed release APK is produced at `app/build/outputs/apk/release/app-release.apk`.

> **Signing:** signing secrets are *not* in this repository (see `.gitignore`). To produce a release build locally you need your own keystore with a `keystore.properties` file at the repo root:
>
> ```properties
> storeFile=keystore/livetube.jks
> storePassword=<password>
> keyAlias=<alias>
> keyPassword=<password>
> ```
>
> Keep that keystore backed up — upgrading an installed app requires signing with the same key.

## Usage

- **Library tab** — tap `+`, paste a channel or playlist URL, it's validated and saved. Tap an item to open its feed; tap a video to play it.
- **Player tab** — paste any YouTube link (or a direct media URL) and press **Play**. Toggle **Audio only** to strip video, and **Auto-play next** to keep a list playing.

## Privacy

- No accounts, no telemetry, no ads. Everything is stored on-device (Room database).
- Requires network access to YouTube to fetch feeds and streams.

## Disclaimer

This app plays YouTube content through unofficial extraction of stream URLs and isn't endorsed by YouTube/Google. Background playback and audio-only streaming of YouTube content may not be permitted by YouTube's Terms of Service and generally require YouTube Premium via the official app — use responsibly, for personal, legal content. The extraction technique can break if YouTube changes its internals.

## License

See the `LICENSE` file, if present. This project's public code is provided as-is for personal use.