# How it works

## Recording both sides of a call

Normal Android apps can't use the "voice call" audio sources; those need a system-only permission, and on Samsung phones they return silence even when that permission is available. Recording only the microphone usually captures your voice and very little of the other person.

On Samsung Galaxy phones the audio layer has an **in-call recording mode**. When an app turns it on, call audio is fed into the normal capture path. Call Recorder:

1. Notices the call through a `TelephonyCallback` registered from an **accessibility service**. The service is what Android allows to start microphone capture from the background; it doesn't read the screen (`canRetrieveWindowContent="false"`).
2. Starts a **microphone foreground service** with a single low-priority notification.
3. Calls `AudioManager.setParameters("INCALL_RECORDING_MODE=ON")` and `("VOICE_RECORDING_MODE=ON")`.
4. Records with `MediaRecorder` from `AudioSource.VOICE_RECOGNITION` (falls back to `MIC`) as AAC, 48 kHz mono.
5. When the call ends: switches the modes off, then reads the call log (or, for internet calls, the app's call notification) to name the file, applies the user's recording rules, and publishes the file through `MediaStore` into `Recordings/Call/`.

The result is a single mixed track (stereo capture gives two identical channels, so the app records mono). The far side is often quieter than your own voice; the player's **Boost** compensates.

## Internet calls (WhatsApp, Signal, Telegram…)

These calls also go through the phone's audio stack and are recorded the same way. They never appear in the call log, so the name comes from the calling app's ongoing-call notification via an optional `NotificationListenerService`. It only looks at notifications in the *call* category from a short list of calling apps.

## Library

- The user grants **read-only access** to `Recordings/Call` through the Storage Access Framework. The app walks it (including subfolders) and keeps a small Room database as an index.
- File names from this app, Samsung's recorder and other common formats are parsed for the date, number and name; contact names and photos come from the phone's contacts.
- Duplicates (same size and same first 64 KB) are shown once.
- Deleting uses `MediaStore.createTrashRequest`, so Android asks for confirmation and files stay restorable from the Recycle bin for 30 days.

## Opening a recording in Samsung Voice Recorder

Voice Recorder accepts a `voice://recording.id/<MediaStore id>` link and opens that recording in its full player, where Galaxy AI's **Transcribe** and **Summary** live. If the link isn't available, the app falls back to a normal "open with".

## Code layout

```
app/src/main/java/app/callrecorder/
├── recorder/   CallWatchService (accessibility, call state) · RecorderService (capture, naming)
│               CallNotificationWatcher (internet-call names) · RecordSettings · EventLog
├── data/       Room database · Scanner (folder index) · FileNames (parse/format names)
│               RecordRules · Retention (auto-delete) · Tidy (rename old files) · AppSettings · Media
└── ui/         Library, detail/player, settings, theme (Jetpack Compose, Material 3)
```

Unit tests cover file-name parsing and formatting and the recording rules.

## Known limitations

- **Samsung only** for two-way audio. Other brands will likely record just the microphone.
- The in-call recording mode isn't a public API; a One UI update could change it.
- Android only reveals the other number after a call ends, so "who to record" rules are applied then: an unwanted recording is made and deleted immediately.
- Speaker labels and transcripts aren't produced by the app; use Samsung Voice Recorder (Galaxy AI).
- Recordings in folders Android doesn't index (with a `.nomedia` file) can't be moved to the Recycle bin by the app; they're hidden instead.
- Bluetooth headsets and car audio are untested.
