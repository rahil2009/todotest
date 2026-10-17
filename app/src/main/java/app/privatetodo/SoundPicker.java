package app.privatetodo;

import android.app.Activity;
import android.content.Intent;
import android.media.RingtoneManager;
import android.net.Uri;
import android.widget.Toast;

/** Opens the phone's own sound picker or file picker and reads the answer back. */
final class SoundPicker {
    static final int REQ_PHONE = 21;
    static final int REQ_FILE = 22;

    private SoundPicker() {
    }

    /** The phone's system sound list (ringtone picker). {@code current} is "", SILENT or a URI. */
    static void phone(Activity a, int kind, String current) {
        Intent i = new Intent(RingtoneManager.ACTION_RINGTONE_PICKER)
                .putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE,
                        kind == Prefs.ALARM ? RingtoneManager.TYPE_ALARM
                                : RingtoneManager.TYPE_NOTIFICATION)
                .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
                .putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE,
                        kind == Prefs.ALARM ? "Alarm sound" : kind == Prefs.POMO ? "Pomodoro tune" : "Notification sound");
        if (current != null && !current.isEmpty() && !Prefs.SILENT.equals(current)) {
            i.putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, Uri.parse(current));
        }
        try {
            a.startActivityForResult(i, REQ_PHONE);
        } catch (RuntimeException e) {
            Toast.makeText(a, "No sound picker on this phone. Try \"Audio file\".",
                    Toast.LENGTH_LONG).show();
        }
    }

    /** Any audio file on the phone. No storage permission needed. */
    static void file(Activity a) {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT)
                .addCategory(Intent.CATEGORY_OPENABLE)
                .setType("audio/*")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                        | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        try {
            a.startActivityForResult(i, REQ_FILE);
        } catch (RuntimeException e) {
            Toast.makeText(a, "No file picker found on this phone", Toast.LENGTH_LONG).show();
        }
    }

    /**
     * Call from onActivityResult. Returns the chosen sound ("" = phone default, a URI string, or
     * Prefs.SILENT), or null when this result is not ours or was cancelled.
     */
    static String result(Activity a, int req, int result, Intent data) {
        if (result != Activity.RESULT_OK || data == null) return null;
        if (req == REQ_PHONE) {
            Uri u = data.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI);
            return u == null ? Prefs.SILENT : u.toString();
        }
        if (req == REQ_FILE && data.getData() != null) {
            Uri u = data.getData();
            try { // keep access to the file after a restart
                a.getContentResolver().takePersistableUriPermission(u,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION);
            } catch (RuntimeException ignored) {
                // some providers do not offer persistable grants
            }
            return u.toString();
        }
        return null;
    }
}
