# Verification log

Every claim here was produced by running the app, not by compiling it.

**Devices**

| Device | Detail | Used for |
|---|---|---|
| Infinix X6853 (`1154137425005666`) | Android phone, gesture nav, 1080×2436 | signed-in flows (token survives `install -r`) |
| Pixel 9 Pro AVD (`emulator-5554`) | Android Studio AVD, 1280×2856 | fresh install → login screen |

**Build under test**

```bash
cd mobile
./gradlew :app:assembleDebug --no-daemon -PGTM_GOOGLE_CLIENT_ID="<from backend/.env>"
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n id.ai.antaratask/.MainActivity
```

`:app:compileDebugKotlin` and `:app:assembleDebug` both succeed. The Google client id is
read from `backend/.env` at build time and never committed.

## Gates run

| Gate | Command | Result |
|---|---|---|
| Token contrast (32 pairs) | `python3 .agents/design/tools/check_contrast.py` | all pass WCAG AA |
| Decision ledger | `python3 .agents/design/tools/jev_judge.py verify` | see `decisions.md` |
| Kotlin compile | `./gradlew :app:compileDebugKotlin` | success |
| Debug APK | `./gradlew :app:assembleDebug` | success, installs on both targets |

## Screens verified

| Screenshot | Screen | State |
|---|---|---|
| `speak_light.png` | Speak | idle, light, status bar clear of the headline |
| `speak_dark.png` | Speak | idle, dark, light status icons |
| `speak_listening` (device run) | Speak | listening: red orb + three expanding rings, live badge |
| `tasks_light.png` | Tasks | list, compact band, chips + toggle on one line |
| `tasks_dark.png` | Tasks | list, dark palette, warm row retained |
| `board_light.png` | Tasks | board, 232dp columns, tinted header chips |
| `detail_light.png` | Task detail | warm KV card, wrapping move chips, close affordance |
| `composer_light.png` | Quick add | stacked cards on brand canvas, disabled submit |
| `system_light.png` | System | real usage/storage/engine data from the backend |
| `login_emulator.png` | Login | split layout, Google button enabled |
| `tasks_fontscale200.png` | Tasks | 200% font scale: no clipping, chips wrap |

## Interactions exercised

- Tab switching on all three tabs (verified through the accessibility tree, not just pixels).
- Mic FAB from the Tasks tab → switches to Speak and starts a real capture (listening state
  observed with the OS microphone indicator).
- Task row → detail sheet; close affordance and scrim dismiss.
- Add FAB → composer; project/priority chip selection; title input.
- Board toggle; column headers and counts.
- System actions render; destructive unpair is behind a confirmation dialog.
- Dark mode switch (`cmd uimode night yes|no`) and font scale (`settings put system font_scale 2.0`).

## Defects found here (all fixed)

The nine defects listed in `design.md` §12.4 were found on these runs. Each was fixed and
re-verified; the screenshots above are from the fixed build except where noted.

## Known limitations (not defects)

- The System screen has no 7-day chart because the API returns aggregates only.
- The composer does not offer estimates, tags or notes because `createTask` has no fields
  for them; adding them is a backend change, not a UI one.
- The signed-in screenshots come from the physical device because a fresh emulator has no
  Google session; the emulator covers the login screen.
