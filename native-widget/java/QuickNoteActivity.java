package com.benjamin.nearly; // CHANGE to your app's package

import android.app.Activity;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import org.json.JSONObject;
import java.util.LinkedHashMap;
import java.util.Random;

/** Dialog opened from widgets: write a note (love / sticky / private / reminder) or answer the daily question. */
public class QuickNoteActivity extends Activity {
    private static final String[] KINDS = {"love", "sticky", "private", "reminder"};
    private static final String[] LABELS = {"💌 Love note", "📝 Sticky note", "🔒 Private message", "⏰ Shared reminder"};
    private static final int[] PASTELS = {0xFFFFF1A8, 0xFFFFD6E7, 0xFFD6F5E3, 0xFFD9E8FF, 0xFFEBDDFF, 0xFFFFE2C6};

    private Spinner kind;
    private EditText input;
    private CheckBox ping;
    private boolean answer;
    private String question = "";

    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        answer = "answer".equals(getIntent().getStringExtra("mode"));
        question = getIntent().getStringExtra("q") == null ? "" : getIntent().getStringExtra("q");
        String preset = getIntent().getStringExtra("kind") == null ? "love" : getIntent().getStringExtra("kind");

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(16), dp(20), dp(8));

        TextView title = new TextView(this);
        title.setTextSize(18);
        title.setText(answer ? "💬 Your answer" : "✏️ Quick note");
        root.addView(title);

        if (answer) {
            TextView q = new TextView(this);
            q.setText(question);
            q.setTextSize(14);
            q.setPadding(0, dp(6), 0, dp(6));
            root.addView(q);
        } else {
            kind = new Spinner(this);
            kind.setAdapter(new ArrayAdapter<String>(this, android.R.layout.simple_spinner_dropdown_item, LABELS));
            for (int i = 0; i < KINDS.length; i++) if (KINDS[i].equals(preset)) kind.setSelection(i);
            root.addView(kind);
        }

        input = new EditText(this);
        input.setHint(answer ? "Type your answer…" : "Write something sweet…");
        input.setMinLines(3);
        input.setGravity(Gravity.TOP);
        root.addView(input, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        if (!answer) {
            ping = new CheckBox(this);
            ping.setText("Also notify my partner");
            ping.setChecked(true);
            root.addView(ping);
        }

        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.END);
        Button cancel = new Button(this);
        cancel.setText("Cancel");
        cancel.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { finish(); } });
        Button send = new Button(this);
        send.setText(answer ? "Save" : "Send 💗");
        send.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { send(); } });
        row.addView(cancel);
        row.addView(send);
        root.addView(row);
        setContentView(root);
    }

    private void send() {
        final String text = input.getText().toString().trim();
        if (text.isEmpty()) { finish(); return; }
        final android.content.Context c = getApplicationContext();
        final String k = answer ? "" : KINDS[kind.getSelectedItemPosition()];
        final boolean notify = !answer && ping.isChecked();
        final boolean isAnswer = answer;
        new Thread(new Runnable() {
            public void run() {
                try {
                    if (!NearlyStore.paired(c)) { NearlyStore.setFeedback(c, "Pair in the app first"); NearlyWidgets.refreshAll(c); return; }
                    NearlyData d = new NearlyData(c);
                    LinkedHashMap<String, Object> m = new LinkedHashMap<>();
                    if (isAnswer) {
                        m.put("game/answers/" + NearlyUtil.todayKey() + "/" + d.slot, text);
                        NearlyStore.setFeedback(c, "💬 Answer saved");
                    } else {
                        long ts = NearlyUtil.now();
                        JSONObject n = new JSONObject();
                        NearlyUtil.put(n, "text", text);
                        NearlyUtil.put(n, "by", d.slot);
                        NearlyUtil.put(n, "ts", ts);
                        NearlyUtil.put(n, "kind", k);
                        if (k.equals("private")) NearlyUtil.put(n, "to", d.pslot);
                        if (k.equals("sticky")) NearlyUtil.put(n, "color", String.format("#%06X", PASTELS[new Random().nextInt(PASTELS.length)] & 0xFFFFFF));
                        m.put("notes/" + Long.toString(ts, 36), n);
                        NearlyStore.setFeedback(c, "💌 Note sent");
                    }
                    NearlySync.writeAll(c, m);
                    if (notify) NearlySync.notifyPartner(c, "💌 " + d.myName(), k.equals("private") ? "sent you a private message" : NearlyUtil.clip(text, 100));
                } catch (Throwable ignored) { }
            }
        }).start();
        finish();
    }
}
