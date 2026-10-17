package app.privatetodo;

import android.app.Activity;
import android.appwidget.AppWidgetManager;
import android.content.Intent;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/** Shown when a Tasks widget is added: pick which list it shows (the same filters as the home screen). */
public class WidgetConfigActivity extends Activity {
    private int widgetId = AppWidgetManager.INVALID_APPWIDGET_ID;
    private float density;

    @Override
    protected void onCreate(Bundle b) {
        Pal.init(this);
        setTheme(Pal.dark ? R.style.AppThemeDark : R.style.AppTheme);
        super.onCreate(b);
        setResult(RESULT_CANCELED);
        density = getResources().getDisplayMetrics().density;
        Intent i = getIntent();
        if (i.getExtras() != null) {
            widgetId = i.getExtras().getInt(AppWidgetManager.EXTRA_APPWIDGET_ID,
                    AppWidgetManager.INVALID_APPWIDGET_ID);
        }
        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish();
            return;
        }
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Pal.bg);
        root.setPadding(dp(20), dp(40), dp(20), dp(20));
        TextView h = text("Show on this widget", 24, Pal.ink, true);
        root.addView(h);
        TextView sub = text("Tap the widget title any time to switch.", 14, Pal.muted, false);
        sub.setPadding(0, dp(4), 0, dp(16));
        root.addView(sub);
        for (int k = 0; k < TaskWidget.NAMES.length; k++) {
            final int idx = k;
            TextView row = text(TaskWidget.NAMES[k], 17, Pal.ink, true);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(20), 0, dp(20), 0);
            GradientDrawable g = new GradientDrawable();
            g.setColor(Pal.card);
            g.setCornerRadius(dp(18));
            row.setBackground(g);
            row.setOnClickListener(v -> {
                Hap.tick(v);
                TaskWidget.setFilterIndex(this, widgetId, idx);
                AppWidgetManager.getInstance(this).updateAppWidget(widgetId, TaskWidget.build(this, widgetId));
                setResult(RESULT_OK, new Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId));
                finish();
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(56));
            lp.bottomMargin = dp(8);
            root.addView(row, lp);
        }
        ScrollView sv = new ScrollView(this);
        sv.setBackgroundColor(Pal.bg);
        sv.addView(root);
        setContentView(sv);
        Fonts.apply(getWindow().getDecorView());
    }

    private TextView text(String s, int sp, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    private int dp(int v) {
        return Math.round(v * density);
    }
}
