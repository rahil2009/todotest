# Private Todo

Offline to-do app for Android with real reminders. Add, edit, check off, delete, filter (Active / Done / All).

## Home screen
- Bottom bar: **settings** (left), a round **type-a-task pill** (centre) and **search** (right).
  Type in the pill and press Enter (or the arrow) to add a task. The small clock in the pill opens the
  full editor with what you typed, for a reminder, repeat, subtasks or alarm style.
- Search turns the pill into a live search over task and subtask text. Tap the X to leave search.

## Notebook
- Open it from the **notebook icon** in the header, next to the bell. Searchable list of notes, newest edit first.
- **Editor toolbar:** heading, **bold**, *italic*, underline, ~~strikethrough~~, **highlight colours** (5 + none),
  `inline code`, code blocks and bullet lists. With text selected a style applies to it; with only a cursor it
  applies to what you type next. Enter on a bullet continues the list; Enter on an empty bullet ends it.
- **To-do items inside a note:** the **(✓)** toolbar button turns the line into a to-do item with a round checkbox. Tap the circle to
  tick it (filled, with a tick; text struck through); tap again to untick. Enter adds the next item; Enter on an empty item ends the list.
- Saves automatically when you leave the note. Long-press a note in the list (or use the bin) to delete.
- Stored in the same local database (`notes` table); no sync, no network.

## Settings
- **Appearance:** match the phone, light or dark. Dark uses dark-grey surfaces that get lighter as they rise, softer text and a softer accent.
- **Sound** for reminder notifications and for alarms, separately: pick any sound on the phone
  (system picker), any audio file you choose (no storage permission needed), the phone default, or silent.
- **Volume** for each: a thick One UI style pill slider (drag anywhere on the bar), with a test button. The app plays the sound itself, so the slider is relative
  to the phone's own notification / alarm volume (it can turn a sound down, not above the phone's level).
  Alarm sounds loop until you press Done / Snooze / Dismiss (max 10 min) using a foreground service.

## Per-task sound and volume
In the task editor (when a reminder time is set) choose **Same as Settings** or **Custom for this task**:
its own sound (phone sound, audio file, or silent) and its own volume slider.

## Subtasks and links
- Add subtasks inside any task. Press **Enter** on the keyboard and the next subtask line opens automatically;
  Enter on an empty line (or Backspace on an empty one) finishes the list. Tick subtasks off right from the list.
- Links in a task or subtask (`https://...`, `www...`, `site.com/page`) and email addresses are **tappable** in the list
  and on the lock-screen alarm. Tapping the rest of the text still opens the editor.
- Notifications can't hold inline links, so the first link becomes an **Open link** button on the notification
  (it shows "1/n" when there are more), and the expanded notification lists the subtasks.
- Links open in your browser. The app itself still has no internet permission.

## Reminders
- Any time works: "In" 1 min to 99 weeks, or "At" any day within a year, down to the minute.
  The quick buttons (in 1 hour, tonight 8 pm, tomorrow 9 am) are only shortcuts; **Pick any time...** opens the dials.
- Pick a date and time, or use a quick preset (in 1 hour, tonight 8 pm, tomorrow 9 am).
- **Repeat** once, daily, weekly, or **Custom: every N min / hr / day / week / month** (N from 1 to 99, same swipe dials).
  Examples: every 30 min, every 2 days, every 1 month. Monthly repeats keep their day of the month (31st rolls to the month's last day, then back).
  "Done" on a repeating reminder skips to the next one instead of ending the series; set the task to Once to stop it.
- **Notification** with **Done** and **Snooze 10 min** buttons.
- **Alarm style** (switch in the editor): rings with the alarm sound and vibration until handled, and opens a
  **full-screen screen over the lock screen** (like Samsung Reminder) with Snooze 5 min / 10 min / 30 min / 1 hour, Done and Dismiss.
- Reminders survive reboot, app update and clock/timezone changes.
- The bell icon in the header opens **Reminder setup**: one place to grant notifications, exact alarms,
  full-screen alerts and the battery setting. A red dot means something is still missing.

### Permissions (none of them is network access)
| Permission | Why |
|---|---|
| POST_NOTIFICATIONS | show reminders (Android 13+, asked at runtime) |
| SCHEDULE_EXACT_ALARM | ring exactly on time (Android 12+, toggled in Settings) |
| USE_FULL_SCREEN_INTENT | lock-screen alarm screen (Android 14+, toggled in Settings) |
| RECEIVE_BOOT_COMPLETED | re-arm reminders after reboot |
| VIBRATE | alarm vibration |
| FOREGROUND_SERVICE (+ media playback) | keep an alarm sound looping while the screen is off |

### Samsung tip
Settings > Battery > Background usage limits: make sure Private Todo is not in "Sleeping apps" / "Deep sleeping apps",
and set its battery usage to **Unrestricted**. Otherwise One UI can delay or drop alarms.

## Privacy, by construction
- **No INTERNET permission**, so Android blocks all network access for the app.
- **Local SQLite** database in the app's private storage (`tasks.db`).
- **No analytics, no accounts, no ads, zero third-party libraries.**
- **Backups disabled** (`allowBackup=false` plus data-extraction rules), so tasks never reach cloud backup or device transfer.

## Build the APK
Needs JDK 17 and the Android SDK (platform 34). Android Studio installs both.

```
./gradlew assembleDebug
```

Output: `app/build/outputs/apk/debug/app-debug.apk`. Copy it to your phone and open it
(allow "install unknown apps" for your file manager when asked).

No local Android setup? Push this folder to a GitHub repo and run the **Build APK** workflow
(Actions tab). The APK appears under the run's artifacts.

Requires Android 8.0+ (API 26).
