package app.privatetodo;

import android.app.Activity;
import android.app.KeyguardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Typeface;
import android.hardware.biometrics.BiometricManager;
import android.hardware.biometrics.BiometricPrompt;
import android.os.Build;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Asks the phone to confirm it is you (fingerprint, face or screen lock).
 * Normal mode: guards the locked screens. Test mode (Settings -> App lock -> Test unlock): runs the same check,
 * says what happened and never closes the app, so a problem can be seen and fixed without being locked out.
 * It never traps anyone: if the phone cannot do the check at all, it opens.
 */
public class UnlockActivity extends Activity {
    private static boolean showing;
    private static final int REQ = 71;
    private static final int ERR_LOCKOUT = 7;
    private static final int ERR_LOCKOUT_PERMANENT = 9;
    private static final int ERR_HW_UNAVAILABLE = 1;
    private static final int ERR_NO_BIOMETRICS = 11;
    private static final int ERR_HW_NOT_PRESENT = 12;
    private static final int ERR_NO_DEVICE_CREDENTIAL = 14;

    private CancellationSignal cancel;
    private boolean test;
    private boolean asked;
    private boolean running;
    private TextView message;
    private TextView action;
    private TextView close;

    static void show(Context c) {
        if (showing) return;
        showing = true;
        c.startActivity(new Intent(c, UnlockActivity.class).addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION));
    }

    static void test(Context c) {
        showing = true;
        c.startActivity(new Intent(c, UnlockActivity.class).putExtra("test", true));
    }

    private int dp(float v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    @Override
    protected void onCreate(Bundle b) {
        Pal.init(this);
        setTheme(Pal.themeRes());
        super.onCreate(b);
        showing = true;
        test = getIntent().getBooleanExtra("test", false);
        asked = b != null;
        if (!test) {
            getWindow().setFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE,
                    android.view.WindowManager.LayoutParams.FLAG_SECURE);
        }

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER);
        box.setBackgroundColor(Pal.bg);
        box.setPadding(dp(32), dp(32), dp(32), dp(32));
        TextView ic = new TextView(this);
        ic.setText("🔒");
        ic.setTextSize(44);
        ic.setGravity(Gravity.CENTER);
        box.addView(ic);
        TextView title = new TextView(this);
        title.setText(test ? "Test unlock" : "Locked");
        title.setTextSize(24);
        title.setTextColor(Pal.ink);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setGravity(Gravity.CENTER);
        title.setPadding(0, dp(8), 0, dp(8));
        box.addView(title);
        message = new TextView(this);
        message.setTextSize(15);
        message.setTextColor(Pal.muted);
        message.setGravity(Gravity.CENTER);
        message.setPadding(0, 0, 0, dp(20));
        message.setText(test ? "Your phone will ask for your fingerprint, face or screen lock." : "");
        box.addView(message);
        action = pill(test ? "Start test" : "Unlock", Pal.accent, Pal.onAccent);
        action.setOnClickListener(v -> ask());
        box.addView(action);
        close = pill(test ? "Close" : "Close app", Pal.chip, Pal.ink);
        close.setOnClickListener(v -> leave());
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(-2, -2);
        clp.topMargin = dp(10);
        box.addView(close, clp);
        setContentView(box);
        Fonts.apply(box);

        if (!Lock.deviceSecure(this)) {
            if (test) {
                message.setText("This phone has no screen lock set. Set a PIN, pattern or password in the phone's "
                        + "settings first, then run the test again.");
                action.setVisibility(View.GONE);
            } else { // nothing to ask: never lock anyone out
                Lock.unlocked = true;
                finish();
            }
        }
    }

    private TextView pill(String label, int fill, int ink) {
        TextView t = new TextView(this);
        t.setText(label);
        t.setTextSize(16);
        t.setTextColor(ink);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setGravity(Gravity.CENTER);
        t.setPadding(dp(30), dp(13), dp(30), dp(13));
        android.graphics.drawable.GradientDrawable g = new android.graphics.drawable.GradientDrawable();
        g.setColor(fill);
        g.setCornerRadius(dp(100));
        t.setBackground(g);
        return t;
    }

    @Override
    protected void onResume() {
        super.onResume();
        // ask once the screen is really in front: asking earlier gets cancelled by the system
        if (!test && !asked && Lock.deviceSecure(this)) {
            asked = true;
            action.post(this::ask);
        }
    }

    private void ask() {
        if (running || isFinishing()) return;
        running = true;
        message.setText("");
        try {
            if (Build.VERSION.SDK_INT >= 30) {
                BiometricPrompt prompt = new BiometricPrompt.Builder(this)
                        .setTitle("Unlock Private Todo")
                        .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_WEAK
                                | BiometricManager.Authenticators.DEVICE_CREDENTIAL)
                        .build();
                cancel = new CancellationSignal();
                prompt.authenticate(cancel, getMainExecutor(), new BiometricPrompt.AuthenticationCallback() {
                    @Override
                    public void onAuthenticationSucceeded(BiometricPrompt.AuthenticationResult result) {
                        success();
                    }

                    @Override
                    public void onAuthenticationError(int code, CharSequence msg) {
                        error(code, msg == null ? "" : msg.toString());
                    }
                });
            } else {
                KeyguardManager km = getSystemService(KeyguardManager.class);
                Intent i = km.createConfirmDeviceCredentialIntent("Unlock Private Todo", null);
                if (i == null) {
                    success();
                    return;
                }
                startActivityForResult(i, REQ);
            }
        } catch (Throwable t) { // whatever went wrong, say it and never crash
            running = false;
            String why = t.getClass().getSimpleName() + (t.getMessage() == null ? "" : ": " + t.getMessage());
            Lock.setError(this, why);
            if (test) {
                fail("The check could not start.\n" + why);
            } else {
                Lock.unlocked = true; // fail open rather than lock the person out of their own app
                finish();
            }
        }
    }

    @Override
    protected void onActivityResult(int req, int result, Intent data) {
        super.onActivityResult(req, result, data);
        if (req != REQ) return;
        running = false;
        if (result == RESULT_OK) success();
        else fail("Not confirmed.");
    }

    private void success() {
        running = false;
        if (isFinishing()) return;
        Lock.setError(this, "");
        if (test) {
            Lock.setTested(this, true);
            message.setText("✓  It works on this phone. You can switch the lock on now.");
            action.setVisibility(View.GONE);
        } else {
            Lock.unlocked = true;
            finish();
        }
    }

    private void error(int code, String msg) {
        running = false;
        if (isFinishing()) return;
        Lock.setError(this, code + ": " + msg);
        boolean cannot = code == ERR_HW_NOT_PRESENT || code == ERR_NO_BIOMETRICS || code == ERR_NO_DEVICE_CREDENTIAL
                || code == ERR_HW_UNAVAILABLE;
        if (cannot && !test) { // the phone cannot do the check: open instead of locking anyone out
            Lock.unlocked = true;
            finish();
            return;
        }
        if (code == ERR_LOCKOUT || code == ERR_LOCKOUT_PERMANENT) {
            fail("Too many tries. " + msg);
        } else {
            fail(msg.isEmpty() ? "Not confirmed (code " + code + ")." : msg + " (code " + code + ")");
        }
    }

    private void fail(String why) {
        message.setText(why);
        action.setText(test ? "Try again" : "Unlock");
        action.setVisibility(View.VISIBLE);
    }

    private void leave() {
        if (test) finish();
        else finishAffinity();
    }

    @Override
    public void onBackPressed() {
        leave();
    }

    @Override
    protected void onDestroy() {
        showing = false;
        if (cancel != null) cancel.cancel();
        super.onDestroy();
    }
}
