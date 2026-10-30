package app.privatetodo;

import android.app.Activity;
import android.app.KeyguardManager;
import android.content.Context;
import android.content.Intent;
import android.hardware.biometrics.BiometricManager;
import android.hardware.biometrics.BiometricPrompt;
import android.os.Build;
import android.os.Bundle;
import android.os.CancellationSignal;

/**
 * Asks the phone to confirm it is you (fingerprint, face or screen lock). Backing out closes the app instead of
 * leaving the locked screens open. Starts itself only once at a time.
 */
public class UnlockActivity extends Activity {
    private static boolean showing;
    private static final int REQ = 71;
    private CancellationSignal cancel;

    static void show(Context c) {
        if (showing) return;
        showing = true;
        c.startActivity(new Intent(c, UnlockActivity.class).addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION));
    }

    @Override
    protected void onCreate(Bundle b) {
        Pal.init(this);
        setTheme(Pal.themeRes());
        super.onCreate(b);
        showing = true;
        android.view.View blank = new android.view.View(this);
        blank.setBackgroundColor(Pal.bg);
        setContentView(blank);
        getWindow().setFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE,
                android.view.WindowManager.LayoutParams.FLAG_SECURE);
        if (!Lock.deviceSecure(this)) { // no screen lock on the phone: nothing to ask, never lock people out
            Lock.unlocked = true;
            finish();
            return;
        }
        if (b == null) blank.post(this::ask);
    }

    private void ask() {
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
                    done(true);
                }

                @Override
                public void onAuthenticationError(int code, CharSequence msg) {
                    done(false);
                }
            });
        } else {
            KeyguardManager km = getSystemService(KeyguardManager.class);
            Intent i = km.createConfirmDeviceCredentialIntent("Unlock Private Todo", null);
            if (i == null) {
                done(true);
                return;
            }
            startActivityForResult(i, REQ);
        }
    }

    @Override
    protected void onActivityResult(int req, int result, Intent data) {
        super.onActivityResult(req, result, data);
        if (req == REQ) done(result == RESULT_OK);
    }

    private void done(boolean ok) {
        if (isFinishing()) return;
        if (ok) {
            Lock.unlocked = true;
            finish();
        } else {
            finishAffinity(); // cancelled: close the app rather than show what is behind
        }
    }

    @Override
    public void onBackPressed() {
        done(false);
    }

    @Override
    protected void onDestroy() {
        showing = false;
        if (cancel != null) cancel.cancel();
        super.onDestroy();
    }
}
