# GTM Android client

Native Kotlin/Jetpack Compose client for the local GTM backend.

## Build

```bash
cd mobile
./gradlew :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

The debug build talks to `http://127.0.0.1:8765` over a USB tunnel:

```bash
cd backend && ../.venv/bin/python -m app
adb reverse tcp:8765 tcp:8765
adb shell am start -n dev.antinormies.gtm/.MainActivity
```

Pair from the web Settings page: generate a six-digit code, enter it in the
app. The token is stored in Keystore-backed encrypted preferences.

## What is implemented

- Three tabs: **Speak**, **Tasks**, **System**.
- Speak: tap-to-talk, partial transcript, 10 s silence timeout, 60 s cap,
  capture card, proposal review, optional short TTS.
- Tasks: Board (To do / In progress / Waiting / Done) and List, project
  groups, Today/Overdue filters, detail sheet with checklist and move-to.
- System: usage, storage, engine/health, paired devices, backup, FTS rebuild,
  unpair.
- Device speech recognition by default; only the transcript is sent to the
  backend. Audio is not uploaded.
- Pairing, bearer auth, 401/403 handling, offline/error states.

## Notes

- Android Studio is not required; the committed Gradle wrapper is enough.
- `local.properties` is local and ignored. It contains `sdk.dir`.
- Release builds only permit cleartext for loopback/emulator hosts; use
  Tailscale Serve or another HTTPS endpoint for the phone in daily use.
- The reference UI is `docs/reference.html`; the synthesis is documented in
  `docs/MOBILE.md`.
