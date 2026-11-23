package com.benjamin.nearly; // CHANGE to your app's package

import android.app.Activity;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import org.json.JSONObject;

/** Dialog opened from widgets: choose a mood and optionally set a mood message, activity and status. */
public class MoodPickerActivity extends Activity {
    private String selected = "";
    private final TextView[] cells = new TextView[NearlyContent.MOOD_KEYS.length];
    private EditText msg, activity, status;

    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        NearlyData d = new NearlyData(this);
        JSONObject me = d.me();
        selected = me.optString("mood", "");

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(16), dp(20), dp(8));

        TextView title = new TextView(this);
        title.setText("How are you feeling?");
        title.setTextSize(18);
        root.addView(title);

        int per = 5;
        LinearLayout row = null;
        for (int i = 0; i < NearlyContent.MOOD_KEYS.length; i++) {
            if (i % per == 0) {
                row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                root.addView(row);
            }
            final String key = NearlyContent.MOOD_KEYS[i];
            TextView t = new TextView(this);
            t.setText(NearlyContent.moodEmoji(key));
            t.setTextSize(28);
            t.setGravity(Gravity.CENTER);
            t.setContentDescription(NearlyContent.moodLabel(key));
            t.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) { selected = key; paint(); }
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(52), 1f);
            lp.setMargins(dp(3), dp(6), dp(3), dp(2));
            row.addView(t, lp);
            cells[i] = t;
        }
        paint();

        msg = field(root, "Mood message (optional)", me.optString("moodMsg", ""));
        activity = field(root, "What are you up to? (optional)", me.optString("activity", ""));
        status = field(root, "Status message (optional)", me.optString("status", ""));

        LinearLayout btns = new LinearLayout(this);
        btns.setGravity(Gravity.END);
        Button cancel = new Button(this);
        cancel.setText("Cancel");
        cancel.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { finish(); } });
        Button save = new Button(this);
        save.setText("Save");
        save.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { save(); } });
        btns.addView(cancel);
        btns.addView(save);
        root.addView(btns);
        setContentView(root);
    }

    private EditText field(LinearLayout root, String hint, String value) {
        EditText e = new EditText(this);
        e.setHint(hint);
        e.setText(value);
        e.setSingleLine(true);
        root.addView(e, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return e;
    }

    private void paint() {
        for (int i = 0; i < cells.length; i++) {
            boolean on = NearlyContent.MOOD_KEYS[i].equals(selected);
            GradientDrawable g = new GradientDrawable();
            g.setCornerRadius(dp(14));
            g.setColor(on ? 0x33FF7AA8 : Color.TRANSPARENT);
            g.setStroke(on ? dp(2) : 0, 0xFFFF7AA8);
            cells[i].setBackground(g);
        }
    }

    private void save() {
        final android.content.Context c = getApplicationContext();
        final String mood = selected, m = msg.getText().toString().trim(),
                a = activity.getText().toString().trim(), s = status.getText().toString().trim();
        new Thread(new Runnable() {
            public void run() {
                if (!NearlyStore.paired(c)) { NearlyStore.setFeedback(c, "Pair in the app first"); NearlyWidgets.refreshAll(c); return; }
                NearlyStore.setFeedback(c, mood.isEmpty() ? "Status updated" : NearlyContent.moodEmoji(mood) + " Mood updated");
                NearlySync.setMood(c, mood, m, a, s);
            }
        }).start();
        finish();
    }
}
