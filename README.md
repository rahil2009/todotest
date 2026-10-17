# Private Todo

Offline to-do app for Android with real reminders. Add, edit, check off, delete, filter (Active / Done / All).

## Home screen
- Bottom bar: **settings** (left), a round **type-a-task pill** (centre) and **search** (right).
  Type in the pill and press Enter (or the arrow) to add a task. The small clock in the pill opens the
  full editor with what you typed, for a reminder, repeat, subtasks or alarm style.
- Search turns the pill into a live search over task and subtask text. Tap the X to leave search.

## Task list gestures and extras
- **Swipe a task right** to mark it done (or not done); **swipe left** to delete it (always asks first).
- **Priority** (None / Low / Medium / High) is set in the New / Edit task popup. Higher priority sorts to the top, with a coloured bar on the card (blue, amber, red).
- **Links** in a task show as small tinted pills. Tap the pill to open the link; tap anywhere else on the task to edit it.
- The bin icon in the header clears all completed tasks (after asking).
- Popups sit at the bottom of the screen for one-handed use and ride up with the keyboard.
- Font: Figtree (bundled, SIL Open Font License; see FONT-LICENSE-Figtree-OFL.txt).

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


## Updating without losing data
Every build is signed with the fixed key in `app/debug.keystore`, so a new APK installs over the old app and keeps your tasks.
If you ever install a build signed with a different key, Android forces an uninstall first: use Settings > Backup > Export backup before that, then Import backup afterwards.

## Repeat on chosen days
In the task editor, Repeat > Choose days lets you tick Mon to Sun (Mon to Fri by default; Sat and Sun stay off).

## Show even when unlocked
Alarm style has a second toggle, "Show even when unlocked". It makes the alarm screen open over whatever you are doing (it asks for "Display over other apps" once). The notification's Open link button now silences the ring and vibration as it opens the link.

## v3.8 additions
Undo pill (4 s, draining bar) after delete / clear / complete; Duplicate in the edit dialog; ticking the last subtask completes the task; haptic taps; Today / Tomorrow / Upcoming / Priority filters; a placeholder Plan icon; roomier code blocks; the unlocked full-screen alarm no longer also slides in as a heads-up notification.

## v3.9
Plan mode (week strip, day agenda, progress ring, one-tap scheduling, "Plan my day"), custom quick "Remind me" buttons (add with + Add, long-press to remove), a slider-style Undo pill, and ticking the last subtask now moves the task to Done with its subtasks left ticked.

## v3.10
Code blocks: fixed the huge padding (small gap above the first and below the last line only). Notes: pinch with two fingers to zoom text, and an "Aa" button for Small / Normal / Large / Huge (remembered). Plan mode: hatched, dashed "free" blocks for empty stretches of an hour or more; tap one to put an untimed task there or add a new task at that time. Dials: the centred value is bold. Home-screen widgets: "Today" (tick tasks off, + to add) and "Quick add". Widgets read only the local database.

## v3.11
Dials: new own Wheel view (replaces NumberPicker) so the centred value is always bold on every dial. Notes: tables (the table button inserts rows x columns; the next-cell button moves between cells and adds a row at the end; tapping the table button inside a table adds a row or column or removes it). Widgets: Tasks (switch Today / Tomorrow / Upcoming / Active / Priority / All by tapping the title or on adding), Notebook, Plan (with free stretches) and Quick add. Plan screen: free blocks now line up with task cards (time on the left).

## v3.12
Emoji per task (editor > Emoji; shows on the list, Plan and widgets). Date/time now shows as a squarish tile (red outline overdue, solid accent today, neutral later) so it never looks like a link pill. Plan: filling a free gap or an untimed task lets you pick the exact hour and minute (5 min steps), and tapping a time on the agenda moves that task. Tables rebuilt: column widths follow the content, padded cells, bold tinted header row (new tables start with "Column 1.." selected), and tapping anywhere in a cell puts the cursor in that cell. Database version 8 (adds the emoji column; updates in place).

## v3.13
Tables rebuilt a third time, properly: a table is one block in the note (a single placeholder character); tapping it opens a grid of real text fields where the first row is the tinted, bold header row, with + Row / + Column / - Row / - Column / Remove table. Cells are saved with the note. (Tables made with v3.11/3.12 stay as plain tab-separated text.) Emoji: shown as a soft round badge at the far right of the task in place of the X (tap it to get the delete confirmation); in the task editor it sits on the title line at the far right.

## v3.14
Notes tables: removing a row or column now asks first (and shows what is in it); any body cell can carry a tick circle (the small circle in the cell editor cycles none, empty, ticked) and tapping the circle right in the note flips it. Settings > Full-screen alert: an Edge glow switch (a soft colour light travelling round the screen edge while an alarm rings, on by default), "Test full-screen alert" (rings now with sample data) and "Test on the lock screen" (rings in 10 seconds so you can lock the phone first); the sample task deletes itself afterwards. Pomodoro: new tomato icon before Plan on the home screen. Ring of 60 ticks and a breathing head, Focus / Short / Long break, 4-session dots, adjustable lengths, a task to focus on (mark it done from there), confetti when a focus session ends, today and 7-day focus stats. Keeps running with the screen off (countdown notification and an end-of-period alert). All stored on the phone.
