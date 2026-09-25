# Aurora Downloader

A free, open-source, ad-free download manager for Android — built to outclass
Advanced Download Manager (ADM) on speed, UX, and honesty.

![Platform](https://img.shields.io/badge/platform-Android-3DDC84?logo=android&logoColor=white)
![License](https://img.shields.io/badge/license-MIT-blue)
![Kotlin](https://img.shields.io/badge/Kotlin-2.1.0-7F52FF?logo=kotlin&logoColor=white)
![Compose](https://img.shields.io/badge/Jetpack%20Compose-BOM-4285F4?logo=jetpackcompose&logoColor=white)
![Min SDK](https://img.shields.io/badge/min%20SDK-24-34A853)

> **Status:** early development. The engine and browser interception work; the
> media/torrent layers are planned (see Roadmap).

## Why another download manager?

ADM has dominated Android for 16 years, but it is ad-cluttered, its UI is stuck
in 2015, it has no torrent support, no desktop counterpart, and no source code
you can read. Aurora is the modern, free alternative.

## Core architecture

```
URL → Resource Probe → Range/ETag detection → Segment Planner
   → Fixed Part Pool (OkHttp) → RandomAccessFile writes
   → Size verification → MediaStore indexing → COMPLETED
```

Everything is persisted (Room) so a hard kill mid-download resumes from the
last written byte on the next start — no restart, no data loss.

| Layer | Choice | Why |
|---|---|---|
| Network | **OkHttp** | Interceptor chain for cookies/retry/auth; no native binary to ship |
| UI | **Jetpack Compose** | Material 3, dynamic color |
| Persistence | **Room** + **DataStore** | Download/part state, settings |
| Background | **UIDT** (Android 14+) / **FGS** fallback | Exempt from job quotas |
| Interception | **WebView DownloadListener + cookies** | The only path that works on 100% of sites |

## Key engineering decisions (and what we deliberately *don't* do)

- **Fixed part count, not adaptive concurrency.** Re-planning mid-download
  discards TCP warm-up and trips per-connection rate limits. Parts are decided
  once from file size and stay fixed.
- **Session cookies are first-class.** Chrome does not hand downloads to
  external apps, so the reliable interception path is the in-app WebView, and
  its cookies are carried into every download request. Without this, protected
  downloads 403.
- **No DRM circumvention.** Widevine-protected streams cannot be downloaded by
  anyone, and no such code lives here.
- **No yt-dlp bundling.** A Python runtime in an Android APK is 20-30 MB of
  maintenance debt; HLS/DASH is handled natively (see Roadmap).

## Roadmap

- [x] Multi-part HTTP downloads with resume
- [x] Crash-recovery persistence
- [x] In-app browser interception with cookies
- [x] Speed limiting (token bucket)
- [x] Background execution (UIDT + FGS)
- [ ] HLS / DASH stream downloads (Media3)
- [ ] Torrent / magnet (FOSS flavor only, jlibtorrent)
- [ ] Scheduling and queues
- [ ] Desktop sync

## Flavors

- `standard` — Play Store / general distribution
- `foss` — F-Droid build; will carry the torrent engine that Google Play
  policy does not allow

## Building

```bash
gradle assembleStandardDebug
```

Requires JDK 17 and Android SDK 35.

## License

MIT © 2026 Aurora Downloader contributors
