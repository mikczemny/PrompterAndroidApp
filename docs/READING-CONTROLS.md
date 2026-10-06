# Reading controls

This update adapts selected ideas from the user's reference:
[Best Teleprompter App For iPad, iPhone & Android (UPDATED!), Primal Video](https://www.youtube.com/watch?v=Q2asN1dihK4).
The implemented scope is stage layout and physical remote control, plus saved
setups and a clean reading screen. It does not add another speech service.

## Remote / keyboard

Use a keyboard or HID presenter/pedal that Android already recognizes.
Bluetooth pairing remains in Android settings; USB keyboards can be connected
normally. No Bluetooth scan, new permission, account or network service is
introduced by these controls.

| Input | Stage action |
| --- | --- |
| Space, Enter, Media Play/Pause | Start or Stop |
| Up / Left | Previous rendered line |
| Down / Right | Next rendered line |
| Page Up / Page Down | Previous / next page (80% of the viewport) |
| Home | Return to the beginning |
| H | Hide / show controls |
| Volume Up / Down, if enabled | Previous / next line |

Stop ends the microphone session and offers the take for Save/Discard. Line
and page jumps keep the current recording going and reset the recognition
utterance, so previously spoken text does not pull the pointer back.

Only the initial key-down performs an action. Repeated and key-up events for
mapped keys are consumed to prevent a second activation on a focused button.
Ctrl/Alt/Meta combinations are left alone. The settings sheet, permission flow,
recording finalization and Save/Discard dialog block stage shortcuts; the UI
does not automatically accept or discard a recording for the user.

Hardware must send one of the supported key codes. Some shutter remotes use
volume keys: enable Volume buttons in Settings for these. The default leaves
volume buttons under Android's normal control.

## Layout and clean screen

- Font: 24–96 sp. Line spacing: 1–2 times the font size.
- Margin: 0–40% on each side, relative to the measured reading viewport.
  Large margins are reduced when needed to retain a readable column.
- The camera exclusion area is preserved and its logical side is swapped
  under mirror mode. An oversized camera preview may overlap the remaining
  text column; resize/move the preview for the desired composition.
- The reading band can be switched off to leave all text visible. It defaults
  off in SelfiePrompter and on in ExtPrompter. The tracked-word highlight remains.
- The final script line has enough bottom padding to reach the reading band
  even on a tall tablet.
- The fullscreen control, double-tap on text or H toggles stage controls.
  Android Back reveals hidden controls before navigating away. The camera
  remains bound; only its close button is hidden.
- Single-tap still selects a word. Dragging selects a new reading position
  when the gesture/fling ends; voice tracking then resumes from that line.

Settings persist independently for SelfiePrompter and ExtPrompter. A new stage
always starts with its controls visible. Active microphone state and script
position are not persisted by this feature.

## Tracking and recording safeguards

A capture session is reserved before model preparation starts and remains
reserved through cleanup. Stop cancels that specific session; an old download
or audio-focus callback cannot reactivate its microphone or stop a new take.
Vosk resets run on the capture thread. The UI rejects late transcripts and
updates its silence/pause clock independently of new recognition results.

A new Start is blocked until the previous recording has left the live state,
finished video finalization, and received its Save/Discard decision. Ending
CameraX video early still leaves the microphone's Stop control available.

## Verification

Automated verification uses the existing Android build and JVM tests:

```bash
./gradlew assembleDebug testDebugUnitTest
```

New regression tests cover remote mappings and repeat events, settings
round-trips and mode isolation, line/page geometry, narrow layouts and capture
session races. Existing Markdown/Polish tracking tests remain part of the suite.

The following still require a physical Android device and actual hardware:

1. Read a Polish Markdown script, pause, jump by line/page, and resume speaking.
2. Hold Space or Enter: one press must start one countdown; Stop must finalize
   one take. Try another Start while finalization or Save/Discard is visible.
3. Hide controls while the selfie camera records; restore them with double-tap,
   H and Back, and confirm the saved WAV/MP4 are complete.
4. Check portrait/landscape with wide margins, a large font, mirror mode and a
   moved camera preview. The text column and last line must remain reachable.
5. Exit/reopen both modes and confirm each retains its own reading settings.
6. Verify the actual presenter's/pedal's key codes, including opt-in volume keys.

This feature does not provide a cross-app floating overlay, a Wi-Fi remote
console, clean HDMI output, or automatic speed scrolling. Those require
separate integration and device-level validation.
