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

## v3.15
Home screen: the priority stripe now sits at the far right, after the emoji / delete button. Notes tables: "- Row" and "- Column" let you pick which row or column to delete (header row stays), with a confirmation. Opening a link from the alarm screen or the notification's Open link button now marks the task done and stops the ring and vibration. Pomodoro: its end-of-period tune and volume are set in Settings > Pomodoro. Settings > Appearance: new AMOLED theme (pure black). Smart dates: type a date after @ in the quick-add pill ("call mom @sat 2pm", "@tomorrow 7pm", "@next sunday 5am", "@every monday 2pm", "@today 3:40pm", "@in 2 hours", "@dec 25", "@every 3 days", "@every weekday 9am") and it is applied and removed from the title. Settings > Smart dates: on/off, trigger symbol, default time for day-only dates, ring as alarm, your own shortcuts (e.g. gym = every mon wed fri 6pm), and a live "Try it" box. Habits: new icon before Pomodoro. Habit cards (streak, today's progress, emoji badge that fills as you reach the goal, Mon to Sun week row with done / missed / partial / skipped / today states). Tap the badge to add a step, tap a day to tick it, long-press a day to skip it. Tap a card for its page: streak, best, total, today's log and a monthly history calendar. Starts with Drink Water (1000 ml) and Read (30 min); add your own with +. Habits are included in backups.

## v3.16
Habits: weekday letters, dates and ticks now line up in the list, on the strip and on the detail page; the + button is visible in light, dark and AMOLED. Habit set-up has unit chips (ml, L, min, cups, pages, steps, km... plus "+ New"), a daily-goal dial and a one-tap-amount dial (swipe, or double-tap the number to type it) and extra quick amounts. Units and their quick amounts live in Settings > Habit units. Long-press a habit's badge for quick amounts / a custom amount / reset; the habit page shows the quick amounts, Custom and undo. Dials (reminder "in" and "at", habit dials): double-tap the centre number to type it, and a hard swipe now glides fast and slows to a stop like iOS, with soft ticks per row. Plan: day names and dates aligned. Pomodoro: new clock face (disc with 120 hair-line ticks and a comet, gradient glowing arc with halo head, ripples and sparks while running, flip-style rolling digits, "ends at" time, glowing play button). Full-screen alert: five animations to choose in Settings > Full-screen alert (Edge glow, Pulse, Aurora, Sparkles, Waves, or Off) with a live preview. Recurring tasks show a repeat glyph on the home list and in Plan. Habit data gains a column (updates in place).

v3.16 build fix: the starter habits now pass their quick amounts when the habit database is created.

## v3.17
Calendar: new month calendar from the header (and by tapping the month in Plan). Days show dots for their tasks, repeating tasks included; tap a day to see its tasks underneath and add a new one from the bar at the bottom (a typed smart date such as "@fri 6pm" wins over the time dial). Plan and Calendar now project repeating tasks onto every day they land on (daily, every N days, weekdays, weekly, monthly), so a daily task appears on all days; future copies are read-only until their day. Dialogs: Cancel / Save / Delete are now pill buttons (filled accent, tonal, red for destructive). Habits: holding a badge resets that day straight away; the habit editor is a roomier full-screen page with cards, bigger chips and dial panels. Pomodoro: the oversized glow that tinted the page the wrong way is toned down in light and dark; new "Always on" mode: dim, outline clock on black at minimum brightness that drifts slowly on two unrelated cycles to protect OLED screens (tap to leave). Smart dates: years and numeric dates (@12 may 2027, @12/05/2027, @2027-05-12), "the 15th", "day after tomorrow", every month on the 15th; clearer Settings labels and a "Read the guide" page that shows every phrase with what it means right now. Full-screen alert animations: Pulse and Sparkles removed; seven new ones (Mesh, Liquid, Beams, Warp, Grid, Ribbons, Orbit) next to Edge glow, Aurora and Waves. Your earlier choice carries over where possible.

v3.17 Settings: the main screen is now a search box, Appearance, Reminder notifications, Alarms and Pomodoro (all inline), then four tiles (Smart dates, Habit units, Full-screen alert, Backup) that each open their own page. The search box filters cards and tiles as you type.

- Task editor: title now sits on an accent hero banner with a live subtasks progress bar; subtasks are filled rounded cards (accent ring while editing, dashed Add button).

- Repeating tasks: ticking a day (Plan, Calendar, list, notification Done) records only that day; the series stays active everywhere. DB v9 adds occ_done and re-opens repeating tasks that were wrongly marked done.
- Settings More: rows with the detail on the right; Habit editor is a rounded sheet again; bell icon before "Alarm" on task chips.

## Repeating tasks: the Repeat tab (v3.18)
- A repeating task always sits on its *current round*: the earliest unticked round that is upcoming or today. An unticked round from earlier today stays red until midnight; rounds from earlier days that were never ticked are skipped.
- Ticking (main list, Plan, Calendar or the notification's Done) ticks the current round only. The task then moves to the **Repeat** tab (struck through, with its next time) and leaves Active. At midnight it returns to Active by itself.
- Tapping the check in the Repeat tab un-ticks today's round and brings it back to Active (red if its time has passed).
- Rounds that start on a later day cannot be ticked early; a message tells you when they start.
- The Repeat tab keeps no history. Plan, Calendar and the lists read the same per-day tick record, so they always agree.
- Database version 9 (adds the per-day tick table).
- Fix: Plan, Calendar and the Plan widget read tasks with `TaskDb.RAW` (exactly as stored), so a repeating task ticked today keeps showing on all future days.

## Edit / New task dialog (v3.19)
- New layout: title banner, subtasks, then WHEN (quick buttons, the chosen one is a black pill with a tick; Clear on the right), REPEAT (Once / Daily / Weekly / Days / Custom as one segmented bar, teal weekday circles), PRIORITY (segmented bar with coloured dots), "Ring like an alarm" and "Show even when unlocked" switches, Sound & volume, a quiet "Duplicate task" link, and a full-width Cancel / Save task (or Add task) pair.
- Quick buttons scroll sideways; "Pick time…" opens the dials; a hand-picked time shows as the first (ticked) button.

## Main screen layout selector (v3.20)
- Settings > More > **Main screen** lets you try five layouts of the Active tab and switch whenever you like. Nothing in your tasks changes; only how the list is drawn. The other tabs, Plan and Calendar are unaffected.
- **Current**: one list, as before.
- **Two sections**: "One-off" first, then "Repeating" in tinted rows. Tap a section header to collapse it.
- **Switch**: All / One-off / Repeating bar above the list (starts on One-off). A line at the bottom says how many repeating tasks are hidden; tap it to show all.
- **Routine strip**: repeating tasks become tiles across the top (tap to tick, long-press to edit); the list is one-off tasks only.
- **Routine dock**: a bar above the add bar summarises routines; tap it to open a list where you can tick them. The list is one-off tasks only.
- Ticking a routine from the strip, dock or Switch follows the usual rules (moves to the Repeat tab; future rounds can't be ticked early).
- Stored in settings as `main_layout` (0, 1, 2, 4, 5) and `main_kind`. No database change.

## Notes: undo / redo (v3.21)
- The note editor header has Undo and Redo arrows (before the bin). They undo typing, formatting, bullet/to-do changes, ticks and table edits, up to 100 steps per visit to a note.
- Steps are taken after a short pause in typing (about 0.7 s) and right before a toolbar action, so one undo reverses a burst of typing or one formatting change. A new edit after undoing clears the redo steps.
- History lives only while the note is open; the cursor returns to where it was at that step. The title field keeps the keyboard's own undo.
- Pinch-zoom and the Aa size picker now change only the note text. The title stays at a fixed size so it can no longer grow and get clipped.

## Notes: custom text size (v3.22)
- The **Aa** button now sets the size of just the selected text (or of what you type next if nothing is selected). Type any size from 8 to 72, use the + / − steppers, or tap a quick size (12, 14, 17, 20, 25, 32, 40, 56). "Normal" removes the custom size.
- Sizes are saved with the note (a new range type, SIZE) and follow pinch-zoom, so zooming keeps proportions. Pinch still zooms the whole note's body text; the title never zooms.
- Undo/redo covers size changes too.

## Notes: pinning (v3.23)
- Every note card has a thumbtack in its top-right corner. Tap it to pin; tap again to unpin. Pinned notes sit at the top under "PINNED n of N" on a lightly tinted card, newest pin first; the rest follow under "NOTES". The home-screen notes widget lists pinned notes first too.
- Limit: 5 pinned notes by default. Settings > More > **Notebook** sets it anywhere from 1 to 20. Lowering it never unpins existing pins; it only stops new ones. At the limit, tapping an unpinned note's thumbtack shows a message pointing to the setting.
- Database version 10: adds a `pinned` column to notes (0 = not pinned, otherwise the pin time). Editing a note never changes its pin or its place among the pinned.
- Fix in the design: the Smart dates row on the Settings board had lost its card background; it is repaired.

## Notebook: preview styles (v3.24)
- The Notebook screen has a button at the top right (four small tiles) that opens "Note previews". Pick a style, try it for a while, and tell me which to keep; the styles are numbered as on the design board.
- 1 Current style, 2 Expanded (up to six lines), 3 Grid (two notes per row), 4 Big title cards (coloured cards, large title), 5 Compact rows (title and time on one line), 6 Titles only, 7 Grouped by date (Today / Yesterday / This week / Earlier), 8 Checklist previews (shows up to three to-do lines), 10 Pinned strip + list (pinned notes as tiles across the top, a compact list below). Style 9 (colour bar + tag) was dropped.
- Pinning works in every style: thumbtack on the card, or long-press a note for Pin / Delete. Compact rows show a small filled thumbtack on pinned notes; use long-press to pin there.
- Stored as `note_look` in settings; no database change.

### Sort button (with v3.24)
The Notebook header has a sort button (next to the preview-style button). Menu: Date edited (newest / oldest), Date created, Title A–Z / Z–A. Pinned notes always stay on top. The "grouped by date" look only groups when sorted by edit date.

### Table editor fix (v3.25)
With many rows the table grid used to fill the whole dialog and push + Row / + Column / − Row / − Column off screen. The grid is now capped at half the screen height and scrolls, so the buttons always stay visible.

### Pinned note colour (v3.26)
Settings > Notebook > "Pinned note colour": fills the whole preview of pinned notes. Ten styles: Soft (default faint tint), Green, Amber, Rose, Sky, Violet, Sticky, Ink, Mixed (each note gets its own colour), None. Works in every preview style (cards, grid, big titles, compact rows, pinned strip). Text and pin icon switch to a readable colour on solid fills.

### Gradient pinned colours (v3.27)
Five more "Pinned note colour" styles with a diagonal gradient fill: Sunset, Ocean, Aurora, Berry, Dusk (15 styles in all; the earlier ten are unchanged).

### Colour per note (v3.28)
Hold the pin on a note (or long-press a note and choose "Pinned colour…") to pick that note's own colour from the 15 styles, or "Follow Settings". Holding the pin on an unpinned note pins it first. Stored in the database (`notes.pincolor`, DB v11, -1 = follow Settings) and included in backups.

### 10 more gradients (v3.29)
Ten more gradient pinned colours (25 styles in all): Peach, Mint, Lagoon, Candy, Citrus, Forest, Midnight, Ember, Lilac, Slate. Same places as before: Settings > Notebook, and hold a pin for a single note.

### Backup now covers everything (v3.30)
Export/import now also includes the repeating tasks' per-day ticks (`occ_done`) and every setting (theme, main-screen layout, note look and sort, pin limit and colours, smart dates, alert and sound choices, habit units, Pomodoro stats). Habits were already included. Old backups still import; they just have no settings in them, so settings stay as they are. Not included: sound files you picked from outside the app (the backup stores the choice, which may need re-picking on a new phone) and home-screen widget placements.

### Brought in line with the design boards (v3.31)
Everything the in-page design edits had changed, now in the app:
- **Notebook folders.** Folder chips under the title (colour, name, count; long-press to rename, recolour or delete), "+ Folder", and a folder icon in the header. Tap a chip to open a folder: its notes, "Notebook › Health · 3 notes", a ⋮ menu, and "New note in Health". The main notebook lists notes that are in no folder; search looks through every note. Move a note with long-press > "Move to folder…". Deleting a folder never deletes notes. DB v12 (`folders` table, `notes.folder`), both included in backups.
- **Search** is now a round button next to "New note" at the bottom (it opens the search box at the top); the permanent search bar is gone. "New note" stays on one line.
- **Sort** opens as a card at the bottom of the screen (one-hand reach).
- **Checklist previews** use round ticks (ring, or filled circle with a tick and struck-through text); the other previews show ○ / ● instead of square ☐ / ☑.
- **Hold-the-pin colour picker** is a sheet at the bottom; "Follow Settings" and "Cancel" share one line.
- **New task / reminder dialog:** Alert type is one control (Full alert = rings like an alarm and shows over the lock screen and while unlocked, Normal alert, Silence) instead of two toggles; "Pick time…" is its own button just above Sound & volume (the time row stays one scrollable line); the chosen priority fills its whole pill with the priority colour, no dots.

## v3.32 fixes
- Theme: Light / Dark / AMOLED now ignore the phone's automatic force-dark (`forceDarkAllowed=false` in all three themes), so picking Light on a dark phone really is light.
- Subtasks: one tap opens the keyboard, and "+ Add subtask" now raises it too.
- Duplicate: bottom-left of the Edit task button row, on one line with Cancel and Save task.
- Pick time: more air between the WHEN buttons and the In / At tabs.
- Notes: New folder colours are one line: seven ready colours plus a rainbow button that opens a hue + shade picker for your own colour (stored in the folder, included in backups); the search button has an accent ring only (no shadow); tapping a to-do circle ticks it without focusing the note or raising the keyboard.

## v3.33 notes
- Text colour: new coloured "A" in the note toolbar (9 colours + back to default). Works on a selection, or on what you type next.
- Numbered lists: the "1." button, or just type "1. " yourself. Enter starts the next number; later items renumber; Enter on an empty item ends the list.
- Nested lists: the ← / → buttons move a bullet, number or to-do item one level in or out (up to 5 levels; an item nests one level under the line above). Enter keeps the level; Enter on an empty nested item steps back out. Stored with the note, so it survives backup / restore.
- "Tx" button: back to standard text (clears bold, italic, underline, strike, colour, highlight, code, size on the selection).
- Finger mode: the pointing-hand button floats at the bottom right of a note. Turn it on, pick a style in the toolbar (bold, italic, underline, strike, code, highlight, text colour, or Tx for standard), then swipe sideways over the text and it applies at once. Swiping over text that already has the style removes it. A tap paints one word; swiping up / down still scrolls. Tap the hand again to switch it off.
- Settings: "Main screen" now has a phone icon instead of the calendar.
