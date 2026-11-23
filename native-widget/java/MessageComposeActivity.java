package com.benjamin.nearly; // CHANGE to your app's package

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.os.Bundle;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import org.json.JSONObject;
import java.util.ArrayList;

/**
 * One screen for everything: type a message, draw one, or both. No mode to pick.
 * Whatever you filled in gets sent. Your last text message can be loaded back to edit.
 */
public class MessageComposeActivity extends Activity {
    private static final int[] PENS = {0xFF4A2A3C, 0xFFE53935, 0xFFFF7AA8, 0xFF4FA3FF, 0xFF2DD4BF};

    private EditText input;
    private PadView pad;
    private String editId = "";
    private JSONObject lastText;

    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }

    /** Finger-drawing pad on a cream "paper" background. */
    static final class PadView extends View {
        final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        final ArrayList<Path> paths = new ArrayList<>();
        final ArrayList<Integer> colors = new ArrayList<>();
        Path cur;
        int pen = PENS[0];

        PadView(Context c) {
            super(c);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setStrokeJoin(Paint.Join.ROUND);
            paint.setStrokeWidth(5 * c.getResources().getDisplayMetrics().density);
            setBackgroundColor(0xFFFFF8FB);
        }

        boolean empty() { return paths.isEmpty(); }
        void undo() { if (!paths.isEmpty()) { paths.remove(paths.size() - 1); colors.remove(colors.size() - 1); invalidate(); } }
        void clear() { paths.clear(); colors.clear(); invalidate(); }

        @Override
        public boolean onTouchEvent(MotionEvent ev) {
            getParent().requestDisallowInterceptTouchEvent(true);
            float x = ev.getX(), y = ev.getY();
            switch (ev.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    cur = new Path(); cur.moveTo(x, y); cur.lineTo(x + 0.1f, y + 0.1f);
                    paths.add(cur); colors.add(pen);
                    break;
                case MotionEvent.ACTION_MOVE:
                    if (cur != null) cur.lineTo(x, y);
                    break;
                default:
                    cur = null;
                    break;
            }
            invalidate();
            return true;
        }

        @Override
        protected void onDraw(Canvas cv) {
            super.onDraw(cv);
            for (int i = 0; i < paths.size(); i++) { paint.setColor(colors.get(i)); cv.drawPath(paths.get(i), paint); }
        }

        Bitmap export() {
            int w = getWidth(), h = getHeight();
            float k = Math.min(1f, 720f / Math.max(w, h));
            Bitmap b = Bitmap.createBitmap(Math.max(1, Math.round(w * k)), Math.max(1, Math.round(h * k)), Bitmap.Config.ARGB_8888);
            Canvas cv = new Canvas(b);
            cv.drawColor(0xFFFFF8FB);
            cv.scale(k, k);
            for (int i = 0; i < paths.size(); i++) { paint.setColor(colors.get(i)); cv.drawPath(paths.get(i), paint); }
            return b;
        }
    }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        final NearlyData d = new NearlyData(this);

        ScrollView sv = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(16), dp(20), dp(8));
        sv.addView(root);

        TextView title = new TextView(this);
        title.setTextSize(18);
        title.setText("💌 Message to " + d.pName());
        root.addView(title);

        for (JSONObject n : d.chat()) {                       // newest-first: first text I wrote
            if (n.optString("by").equals(d.slot) && n.optString("type").equals("text")) { lastText = n; break; }
        }
        if (lastText != null) {
            LinearLayout row = new LinearLayout(this);
            row.setGravity(Gravity.CENTER_VERTICAL);
            TextView last = new TextView(this);
            last.setTextSize(12);
            last.setMaxLines(2);
            last.setText("Your last message: " + NearlyUtil.clip(lastText.optString("text"), 70));
            row.addView(last, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
            Button edit = new Button(this);
            edit.setText("Edit");
            edit.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    editId = lastText.optString("id");
                    input.setText(lastText.optString("text"));
                    input.setSelection(input.getText().length());
                }
            });
            row.addView(edit);
            root.addView(row);
        }

        input = new EditText(this);
        input.setHint("Type something sweet…");
        input.setMinLines(2);
        input.setGravity(Gravity.TOP);
        root.addView(input, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView or = new TextView(this);
        or.setText("…and/or draw or write by hand ✍️");
        or.setTextSize(13);
        or.setPadding(0, dp(10), 0, dp(4));
        root.addView(or);

        pad = new PadView(this);
        root.addView(pad, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(190)));

        LinearLayout tools = new LinearLayout(this);
        tools.setGravity(Gravity.CENTER_VERTICAL);
        for (final int col : PENS) {
            View dot = new View(this);
            dot.setBackgroundColor(col);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(28), dp(28));
            lp.setMargins(0, dp(8), dp(10), dp(4));
            dot.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { pad.pen = col; } });
            tools.addView(dot, lp);
        }
        Button undo = new Button(this);
        undo.setText("Undo");
        undo.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { pad.undo(); } });
        Button clear = new Button(this);
        clear.setText("Clear");
        clear.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { pad.clear(); } });
        tools.addView(undo);
        tools.addView(clear);
        root.addView(tools);

        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.END);
        Button cancel = new Button(this);
        cancel.setText("Cancel");
        cancel.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { finish(); } });
        Button send = new Button(this);
        send.setText("Send 💗");
        send.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { send(); } });
        row.addView(cancel);
        row.addView(send);
        root.addView(row);
        setContentView(sv);
    }

    private void send() {
        final String text = input.getText().toString().trim();
        final byte[] png = pad.empty() ? null : NearlyMsgs.png(pad.export());
        if (text.isEmpty() && png == null) { finish(); return; }
        final Context c = getApplicationContext();
        final String eid = editId;
        new Thread(new Runnable() {
            public void run() {
                try {
                    if (!text.isEmpty()) NearlyMsgs.sendText(c, text, eid);
                    if (png != null) NearlyMsgs.sendDrawing(c, png);
                } catch (Throwable ignored) { }
            }
        }).start();
        finish();
    }
}
