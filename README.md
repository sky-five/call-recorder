# Call Recorder

A small, private, automatic call recorder for Samsung Galaxy phones, with a clean library to listen back.

- **Records both sides** of regular phone calls automatically, and WhatsApp / internet calls too.
- **Stays on your phone.** The app has no internet permission at all, so Android blocks it from sending anything anywhere.
- **Tiny:** about 3.5 MB, no ads, no analytics, no accounts.
- **Readable files:** recordings are saved as `Jane Doe – 2026-11-11 8.21 PM.m4a` in `Recordings/Call/`, so they're easy to find in any file manager.

> ⚠️ **Recording laws differ by country and state.** In many places you must tell the other person, or get their consent, before recording. You are responsible for using this app lawfully.

## Features

**Recording**
- Starts and stops automatically with each call; one quiet notification while recording.
- Names files from your contacts and call log; WhatsApp/Signal/Telegram calls get the caller's name too (optional, see *Permissions*).
- Who to record: everyone, contacts only, or unknown numbers only; a "never record" list; skip very short calls; optional start delay.
- Quality: Standard (64 kbps), High (128 kbps) or Best (192 kbps) AAC, 48 kHz mono.

**Library**
- All call recordings in one list, newest first, grouped by day or by person, with contact names and photos.
- Search by name or number; filter by today / 7 days / 30 days.
- Unknown numbers and spam kept in their own section (or shown in the main list, your choice).
- Picks up recordings from Samsung's own call recorder and other apps in the same folder, read-only; duplicates are shown once.
- Long-press to select several recordings and move them to the phone's Recycle bin.
- Star recordings; auto-delete after 30 / 90 / 180 / 365 days (starred ones are kept).

**Playback**
- Speed 0.75×–2×, skip silence, volume boost for quiet calls, ±10 s, resumes where you left off.
- Plays on the speaker; hold the phone to your ear and it switches to the earpiece (screen off), or force it with a button.
- **Transcript** button opens the recording straight in Samsung Voice Recorder, where Galaxy AI can transcribe and summarise it.
- Call back or share a recording.

## Requirements

- A Samsung Galaxy phone. Two-way recording relies on Samsung's in-call recording mode; on other brands the app will likely record only your side.
- Android 12 or newer. Tested on a Galaxy S24 running Android 17 / One UI 9.

## Install

There are no prebuilt releases yet. Build it yourself (below) and install with:

```bash
adb install app/build/outputs/apk/release/app-release.apk
```

Then open the app and:
1. Tap **Choose folder** and pick `Recordings › Call` (read-only access).
2. **Settings → Grant permissions.**
3. **Settings → Open Accessibility settings → Installed apps → Call Recorder → On.**
   If Android says it's a *restricted setting*: Settings → Apps → Call Recorder → ⋮ → *Allow restricted settings*, then try again. Android does this for apps installed outside the Play Store.
4. Optional: **Allow notification access** to name WhatsApp and other internet calls.

Make a short test call and check the recording.

## Build

Requirements: JDK 17+ and the Android SDK (platform 36). No NDK needed.

```bash
./gradlew testDebugUnitTest assembleRelease
```

The release build is signed with your local debug key so it installs over development builds. Use your own signing key if you distribute it.

## Permissions and privacy

| Permission | Why |
|---|---|
| Microphone | Recording calls |
| Phone state, Call log | Knowing when a call starts and ends, and who it was with |
| Contacts | Showing names and photos |
| Read audio | Listing recordings made by other apps, opening them in Voice Recorder |
| Notifications | The recording notice and "call saved" note |
| Foreground service (microphone) | Android requires it to record in the background |
| Accessibility service | Lets the app start recording when a call begins. It only listens for call state; it doesn't read your screen |
| Notification access (optional) | Reads the caller's name from WhatsApp-style call notifications. Every other notification is ignored and nothing is stored |

There is **no `INTERNET` permission**. Recordings, settings and the small index database never leave the device.

## How it works

See [docs/how-it-works.md](docs/how-it-works.md) for the recording approach, the code layout and known limitations.

## License

[MIT](LICENSE)
