package com.benjamin.nearly; // CHANGE to your app's package

import android.app.Activity;
import android.appwidget.AppWidgetManager;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.RemoteViews;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;
import org.json.JSONObject;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Iterator;

/**
 * Per-widget settings (also the "configure" screen shown when you add a widget).
 * Every change is saved immediately for THIS widget instance only and the preview updates live.
 */
public class NearlyConfigActivity extends Activity {
    private static final int REQ_BG = 4201;

    private int id = AppWidgetManager.INVALID_APPWIDGET_ID;
    private String type = "love";
    private String previewSz = "auto";
    private FrameLayout previewBox;
    private LinearLayout form;

    // ------------------------------------------------------------ per-type mode lists  {value, label}
    private static String[][] modes(String type) {
        switch (type) {
            case "photo": return new String[][]{{"gallery", "Rotating shared gallery"}, {"partner", "Partner photo"}, {"couple", "Couple photo"},
                    {"favorites", "Favorite memories"}, {"recent", "Recently uploaded"}, {"pick", "A specific photo"}};
            case "love": return new String[][]{{"iloveyou", "“I love you” + heart"}, {"random", "Random love message"}, {"daily", "Daily romantic message"},
                    {"partner", "Message from my partner"}, {"kisses", "Virtual kiss counter"}, {"hugs", "Virtual hug button"}, {"affection", "Send affection (heart · kiss · hug)"}};
            case "relationship": return new String[][]{{"days", "Days together"}, {"total", "Total time together"}, {"anniv", "Next anniversary countdown"},
                    {"monthly", "Monthly milestones"}, {"timeline", "Relationship timeline"}, {"reminders", "Special date reminders"}};
            case "status": return new String[][]{{"full", "Full status card"}, {"online", "Online status"}, {"last", "Last active"}, {"mood", "Current mood"},
                    {"activity", "Current activity"}, {"battery", "Battery"}, {"message", "Status message"}};
            case "location": return new String[][]{{"partner", "Partner's shared location"}, {"distance", "Distance between us"}, {"travel", "Estimated travel distance"},
                    {"updated", "Last location update"}, {"sharing", "Location-sharing status"}};
            case "mood": return new String[][]{{"current", "Partner's current mood"}, {"select", "Daily mood selector"}, {"history", "Mood history"},
                    {"shared", "Shared mood status"}, {"calendar", "Mood calendar"}, {"message", "Mood message"}};
            case "countdown": return new String[][]{{"next", "Next date"}, {"anniv", "Anniversary"}, {"birthday", "Birthday"}, {"meetup", "Upcoming meetup"},
                    {"vacation", "Vacation"}, {"special", "Special occasion"}, {"custom", "Custom event"}};
            case "game": return new String[][]{{"ttt", "Tic-Tac-Toe"}, {"daily", "Daily question"}, {"nhie", "Never have I ever"}, {"challenge", "Daily couple challenge"},
                    {"random", "Random question"}, {"invite", "Game invitations"}};
            default: return null;
        }
    }

    private static String defaultMode(String type) {
        String[][] m = modes(type);
        return m == null ? "" : m[0][0];
    }

    private static final String[] COLORS = {"FF7AA8", "E53935", "9C6BFF", "4FA3FF", "2DD4BF", "6BCB77", "FFB74D", "FFFFFF", "1E1E1E", "FFF4E5"};

    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }

    // ------------------------------------------------------------ lifecycle
    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        Intent in = getIntent();
        id = in.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID);
        if (id == AppWidgetManager.INVALID_APPWIDGET_ID) { finish(); return; }
        setResult(RESULT_CANCELED, new Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id));
        String t = NearlyWidgets.typeOf(this, id);
        if (t != null) type = t;

        ScrollView sv = new ScrollView(this);
        form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(16), dp(12), dp(16), dp(24));
        sv.addView(form);
        setContentView(sv);

        TextView title = new TextView(this);
        title.setText("Widget settings");
        title.setTextSize(22);
        title.setPadding(0, 0, 0, dp(8));
        form.addView(title);

        // preview
        previewBox = new FrameLayout(this);
        form.addView(previewBox);
        addSpinner(null, "Preview size", new String[]{"auto", "small", "medium", "large"},
                new String[]{"As placed", "Small", "Medium", "Large"}, "auto");

        buildForm();
        paintPreview();
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req == REQ_BG && res == RESULT_OK && data != null && data.getData() != null) {
            try {
                Uri u = data.getData();
                BitmapFactory.Options o = new BitmapFactory.Options();
                o.inJustDecodeBounds = true;
                InputStream s1 = getContentResolver().openInputStream(u);
                BitmapFactory.decodeStream(s1, null, o);
                s1.close();
                int sample = 1;
                while (o.outWidth / (sample * 2) >= 900 && o.outHeight / (sample * 2) >= 900) sample *= 2;
                o = new BitmapFactory.Options();
                o.inSampleSize = sample;
                InputStream s2 = getContentResolver().openInputStream(u);
                Bitmap bmp = BitmapFactory.decodeStream(s2, null, o);
                s2.close();
                if (bmp != null) {
                    FileOutputStream f = new FileOutputStream(NearlyStore.bgFile(this, id));
                    bmp.compress(Bitmap.CompressFormat.JPEG, 85, f);
                    f.close();
                    NearlyStore.setW(this, id, "bg", "photo");
                    paintPreview();
                }
            } catch (Exception ignored) { }
        }
    }

    // ------------------------------------------------------------ form
    private void buildForm() {
        // ---- content
        String[][] md = modes(type);
        if (md != null) {
            section("What to show");
            String[] v = new String[md.length], l = new String[md.length];
            for (int i = 0; i < md.length; i++) { v[i] = md[i][0]; l[i] = md[i][1]; }
            addSpinner("mode", "Display", v, l, defaultMode(type));
        }
        typeSpecific();

        // ---- look
        section("Appearance");
        addSpinner("size", "Widget layout size", new String[]{"auto", "small", "medium", "large"},
                new String[]{"Automatic (from widget size)", "Small", "Medium", "Large"}, "auto");
        addSpinner("theme", "Theme", new String[]{"auto", "light", "dark"}, new String[]{"Follow system", "Light", "Dark"}, "auto");
        addSpinner("bg", "Background", new String[]{"gradient", "solid", "photo", "transparent"},
                new String[]{"Gradient", "Solid colour", "Photo", "None (transparent)"}, "gradient");
        addButton("Choose photo background…", new Runnable() {
            public void run() {
                Intent i = new Intent(Intent.ACTION_GET_CONTENT).setType("image/*").addCategory(Intent.CATEGORY_OPENABLE);
                startActivityForResult(Intent.createChooser(i, "Background photo"), REQ_BG);
            }
        });
        addSeek("dim", "Photo dimming", 0, 80, 30, "%");
        addColor("bg1", "Background colour 1");
        addColor("bg2", "Background colour 2 (gradient)");
        addColor("txt", "Text colour");
        addColor("acc", "Accent colour");
        addSeek("radius", "Rounded corners", 0, 48, 28, " dp");
        addSeek("opacity", "Transparency (background opacity)", 0, 100, 100, "%");
        addSpinner("font", "Font", new String[]{"sans-serif", "sans-serif-medium", "serif", "monospace", "cursive", "casual", "sans-serif-condensed"},
                new String[]{"Default", "Medium", "Serif", "Monospace", "Handwriting", "Casual", "Condensed"}, "sans-serif");
        addSeek("scale", "Text size", 70, 150, 100, "%");
        addSpinner("align", "Layout", new String[]{"left", "center"}, new String[]{"Left aligned", "Centered"}, "left");
        addCheck("showLabel", "Show title", true);
        addCheck("showExtra", "Show details", true);
        addCheck("showActions", "Show buttons", true);

        // ---- sync
        section("Refresh & sync");
        if (type.equals("msg")) {
            TextView fixed = new TextView(this);
            fixed.setText("Checks for new messages every 10 minutes, automatically. (Fixed)");
            fixed.setTextSize(13);
            fixed.setPadding(0, dp(4), 0, dp(4));
            form.addView(fixed);
        } else {
            addSpinner("refresh", "Background refresh", new String[]{"15", "30", "60", "180", "0"},
                    new String[]{"Every 15 min", "Every 30 min", "Every hour", "Every 3 hours", "Manual only"}, "30");
        }
        addButton("Sync now", new Runnable() {
            public void run() { NearlySync.syncAsync(NearlyConfigActivity.this); }
        });
        TextView st = new TextView(this);
        long ok = NearlyStore.sp(this).getLong("syncOk", 0);
        st.setText((NearlyStore.paired(this) ? "Last sync: " + NearlyUtil.ago(ok) : "Not paired yet — open the app and pair with your partner.")
                + (NearlyStore.queue(this).length() > 0 ? "\n" + NearlyStore.queue(this).length() + " change(s) waiting to upload" : ""));
        st.setTextSize(12);
        st.setPadding(0, dp(4), 0, 0);
        form.addView(st);

        // ---- privacy (shared by all widgets)
        section("Privacy & sharing (applies to all widgets)");
        addPrivCheck("sharePresence", "Share online / last-active status", true);
        addPrivCheck("shareStatus", "Share mood, activity & status", true);
        addPrivCheck("shareBattery", "Share my battery percentage", false);
        addPrivCheck("shareLocation", "Share my location", false);
        addPrivSpinner("locationPrecision", "Location precision", new String[]{"exact", "approx", "city"},
                new String[]{"Exact", "Approximate (~1 km)", "City-level (~10 km)"}, "approx");
        addPrivCheck("maskPrivate", "Hide private messages on the home screen", true);
        addPrivCheck("notifications", "Notify me about my partner's hearts, messages & invites", true);

        // ---- manage
        section("Manage");
        addButton("Reset this widget to defaults", new Runnable() {
            public void run() {
                NearlyStore.resetW(NearlyConfigActivity.this, id);
                form.removeAllViews();
                recreate();
            }
        });
        Button save = new Button(this);
        save.setText("Save");
        save.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                AppWidgetManager m = AppWidgetManager.getInstance(NearlyConfigActivity.this);
                NearlyRenderer.update(NearlyConfigActivity.this, m, id, type);
                NearlySync.schedule(NearlyConfigActivity.this);
                NearlySync.syncAsync(NearlyConfigActivity.this);
                setResult(RESULT_OK, new Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id));
                finish();
            }
        });
        form.addView(save);
    }

    private void typeSpecific() {
        switch (type) {
            case "photo": {
                section("Photo options");
                addSpinner("frame", "Frame", new String[]{"rounded", "none", "polaroid", "circle", "gold", "pink", "film"},
                        new String[]{"Rounded", "None (full bleed)", "Polaroid", "Circle", "Gold", "Pink", "Film strip"}, "rounded");
                addCheck("showCap", "Show caption", true);
                addCheck("showDate", "Show date stamp", false);
                addSpinner("rot", "Rotate gallery every", new String[]{"15", "60", "180", "720", "1440", "0"},
                        new String[]{"15 min", "1 hour", "3 hours", "12 hours", "1 day", "Only when I tap ›"}, "60");
                NearlyData d = new NearlyData(this);
                JSONObject idx = d.o("photoIndex");
                ArrayList<String> ids = new ArrayList<>(), labels = new ArrayList<>();
                Iterator<String> it = idx.keys();
                while (it.hasNext()) {
                    String k = it.next();
                    JSONObject j = idx.optJSONObject(k);
                    if (j == null || "hw".equals(j.optString("kind"))) continue;
                    ids.add(k);
                    String cap = j.optString("cap", "");
                    labels.add(cap.isEmpty() ? j.optString("kind", "photo") + " · " + NearlyUtil.fmtTs(j.optLong("ts"), "MMM d") : cap);
                }
                if (!ids.isEmpty()) addSpinner("photoSel", "Photo for “A specific photo”", ids.toArray(new String[0]), labels.toArray(new String[0]), ids.get(0));
                break;
            }
            case "status": {
                section("Partner info to display");
                addMulti("show", new String[]{"online", "last", "mood", "activity", "battery", "msg"},
                        new String[]{"Online status", "Last active", "Mood", "Activity", "Battery", "Status message"},
                        "online,last,mood,activity,battery,msg");
                break;
            }
            case "location":
                section("Location options");
                addSpinner("units", "Distance units", new String[]{"km", "mi"}, new String[]{"Kilometres", "Miles"}, "km");
                break;
            case "mood":
                section("Mood options");
                addSpinner("calWho", "Mood calendar shows", new String[]{"me", "partner"}, new String[]{"My moods", "Partner's moods"}, "me");
                break;
            case "countdown": {
                section("Countdown options");
                NearlyData d = new NearlyData(this);
                JSONObject evs = d.o("events");
                ArrayList<String> ids = new ArrayList<>(), labels = new ArrayList<>();
                ids.add("");
                labels.add("Next upcoming of this type");
                Iterator<String> it = evs.keys();
                while (it.hasNext()) {
                    String k = it.next();
                    JSONObject j = evs.optJSONObject(k);
                    if (j == null) continue;
                    ids.add(k);
                    labels.add(j.optString("title", "Event") + " · " + j.optString("date", ""));
                }
                addSpinner("evSel", "Event for “Custom event”", ids.toArray(new String[0]), labels.toArray(new String[0]), "");
                break;
            }
            case "interactive":
                section("Buttons (first four checked are shown)");
                addMulti("acts", new String[]{"heart", "kiss", "hug", "mood", "note", "task", "notify", "game"},
                        new String[]{"💗 Send a heart", "💋 Send a kiss", "🤗 Send a hug", "😊 Update mood", "✏️ Write a quick note",
                                "✅ Mark next shared task done", "🔔 Send notification", "🎮 Launch couple games"},
                        "heart,kiss,hug,mood");
                break;
            default:
                break;
        }
    }

    // ------------------------------------------------------------ preview
    private void paintPreview() {
        try {
            AppWidgetManager m = AppWidgetManager.getInstance(this);
            RemoteViews rv = NearlyRenderer.build(this, m, id, type, previewSz.equals("auto") ? null : previewSz);
            previewBox.removeAllViews();
            View v = rv.apply(getApplicationContext(), previewBox);
            int w, h;
            switch (previewSz) {
                case "small": w = 140; h = 140; break;
                case "medium": w = 270; h = 130; break;
                case "large": w = 290; h = 300; break;
                default: w = 270; h = 130; break;
            }
            if (previewSz.equals("auto")) {
                Bundle o = m.getAppWidgetOptions(id);
                w = o.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 270);
                h = o.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 130);
                if (w <= 0) w = 270;
                if (h <= 0) h = 130;
                w = Math.min(w, 330);
                h = Math.min(h, 340);
            }
            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(dp(w), dp(h));
            lp.gravity = Gravity.CENTER_HORIZONTAL;
            previewBox.addView(v, lp);
        } catch (Throwable t) {
            previewBox.removeAllViews();
            TextView e = new TextView(this);
            e.setText("Preview unavailable");
            previewBox.addView(e);
        }
    }

    // ------------------------------------------------------------ form helpers
    private void section(String t) {
        TextView v = new TextView(this);
        v.setText(t);
        v.setTextSize(15);
        v.setTypeface(null, android.graphics.Typeface.BOLD);
        v.setTextColor(0xFFE5648F);
        v.setPadding(0, dp(18), 0, dp(4));
        form.addView(v);
    }

    private TextView label(String t) {
        TextView v = new TextView(this);
        v.setText(t);
        v.setTextSize(13);
        v.setPadding(0, dp(8), 0, 0);
        form.addView(v);
        return v;
    }

    /** key == null: the preview-size picker (not persisted). */
    private void addSpinner(final String key, String title, final String[] values, String[] labels, String def) {
        label(title);
        final Spinner sp = new Spinner(this);
        sp.setAdapter(new ArrayAdapter<String>(this, android.R.layout.simple_spinner_dropdown_item, labels));
        String cur = key == null ? previewSz : NearlyStore.w(this, id, key, def);
        for (int i = 0; i < values.length; i++) if (values[i].equals(cur)) sp.setSelection(i);
        sp.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            boolean first = true;
            public void onItemSelected(AdapterView<?> p, View v, int pos, long l) {
                if (first) { first = false; return; }
                if (key == null) previewSz = values[pos]; else NearlyStore.setW(NearlyConfigActivity.this, id, key, values[pos]);
                paintPreview();
            }
            public void onNothingSelected(AdapterView<?> p) { }
        });
        form.addView(sp);
    }

    private void addSeek(final String key, String title, final int min, int max, int def, final String suffix) {
        final TextView t = label("");
        int cur = NearlyStore.wi(this, id, key, def);
        t.setText(title + ": " + cur + suffix);
        final String ttl = title;
        SeekBar sb = new SeekBar(this);
        sb.setMax(max - min);
        sb.setProgress(cur - min);
        sb.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar s, int p, boolean user) {
                t.setText(ttl + ": " + (p + min) + suffix);
                if (user) { NearlyStore.setW(NearlyConfigActivity.this, id, key, String.valueOf(p + min)); }
            }
            public void onStartTrackingTouch(SeekBar s) { }
            public void onStopTrackingTouch(SeekBar s) { paintPreview(); }
        });
        form.addView(sb);
    }

    private void addCheck(final String key, String title, boolean def) {
        CheckBox cb = new CheckBox(this);
        cb.setText(title);
        cb.setChecked(NearlyStore.wb(this, id, key, def));
        cb.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton b, boolean on) {
                NearlyStore.setW(NearlyConfigActivity.this, id, key, on ? "1" : "0");
                paintPreview();
            }
        });
        form.addView(cb);
    }

    private void addPrivCheck(final String key, String title, boolean def) {
        CheckBox cb = new CheckBox(this);
        cb.setText(title);
        cb.setChecked(NearlyStore.priv(this, key, def));
        cb.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton b, boolean on) {
                NearlyStore.setPriv(NearlyConfigActivity.this, key, on);
                if (key.equals("shareLocation") || key.equals("shareBattery")) NearlySync.syncAsync(NearlyConfigActivity.this);
                paintPreview();
            }
        });
        form.addView(cb);
    }

    private void addPrivSpinner(final String key, String title, final String[] values, String[] labels, String def) {
        label(title);
        Spinner sp = new Spinner(this);
        sp.setAdapter(new ArrayAdapter<String>(this, android.R.layout.simple_spinner_dropdown_item, labels));
        String cur = NearlyStore.privStr(this, key, def);
        for (int i = 0; i < values.length; i++) if (values[i].equals(cur)) sp.setSelection(i);
        sp.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            boolean first = true;
            public void onItemSelected(AdapterView<?> p, View v, int pos, long l) {
                if (first) { first = false; return; }
                NearlyStore.setPrivStr(NearlyConfigActivity.this, key, values[pos]);
            }
            public void onNothingSelected(AdapterView<?> p) { }
        });
        form.addView(sp);
    }

    private void addText(final String key, String title, String def) {
        label(title);
        EditText e = new EditText(this);
        e.setText(NearlyStore.w(this, id, key, def));
        e.setMinLines(2);
        e.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
            public void onTextChanged(CharSequence s, int a, int b, int c) { }
            public void afterTextChanged(Editable s) {
                NearlyStore.setW(NearlyConfigActivity.this, id, key, s.toString());
                paintPreview();
            }
        });
        form.addView(e);
    }

    /** CSV multi-select. */
    private void addMulti(final String key, final String[] values, String[] labels, String defCsv) {
        final java.util.HashSet<String> on = new java.util.HashSet<>();
        for (String s : NearlyStore.w(this, id, key, defCsv).split(",")) if (!s.trim().isEmpty()) on.add(s.trim());
        for (int i = 0; i < values.length; i++) {
            final String v = values[i];
            CheckBox cb = new CheckBox(this);
            cb.setText(labels[i]);
            cb.setChecked(on.contains(v));
            cb.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                public void onCheckedChanged(CompoundButton b, boolean checked) {
                    if (checked) on.add(v); else on.remove(v);
                    StringBuilder csv = new StringBuilder();
                    for (String x : values) if (on.contains(x)) csv.append(csv.length() > 0 ? "," : "").append(x);   // keep list order
                    NearlyStore.setW(NearlyConfigActivity.this, id, key, csv.toString());
                    paintPreview();
                }
            });
            form.addView(cb);
        }
    }

    private void addButton(String text, final Runnable r) {
        Button b = new Button(this);
        b.setText(text);
        b.setAllCaps(false);
        b.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { r.run(); } });
        form.addView(b);
    }

    /** Swatches + hex field. "Auto" clears the override and falls back to the theme. */
    private void addColor(final String key, String title) {
        label(title);
        HorizontalScrollView hs = new HorizontalScrollView(this);
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        hs.addView(row);

        final EditText hex = new EditText(this);
        hex.setHint("#RRGGBB (empty = automatic)");
        hex.setSingleLine(true);
        hex.setText(NearlyStore.w(this, id, key, ""));

        TextView auto = swatch(0, "Auto");
        auto.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { hex.setText(""); NearlyStore.setW(NearlyConfigActivity.this, id, key, null); paintPreview(); }
        });
        row.addView(auto, swatchLp());
        for (final String c : COLORS) {
            TextView s = swatch(Color.parseColor("#" + c), "");
            s.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) { hex.setText("#" + c); }
            });
            row.addView(s, swatchLp());
        }
        form.addView(hs);

        hex.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
            public void onTextChanged(CharSequence s, int a, int b, int c) { }
            public void afterTextChanged(Editable s) {
                String t = s.toString().trim();
                String h = t.startsWith("#") ? t.substring(1) : t;
                if (t.isEmpty()) { NearlyStore.setW(NearlyConfigActivity.this, id, key, null); paintPreview(); return; }
                if (h.length() == 6 || h.length() == 8) {
                    try { Color.parseColor("#" + h); } catch (Exception e) { return; }
                    NearlyStore.setW(NearlyConfigActivity.this, id, key, "#" + h);
                    paintPreview();
                }
            }
        });
        form.addView(hex);
    }

    private LinearLayout.LayoutParams swatchLp() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(40), dp(40));
        lp.setMargins(dp(3), dp(4), dp(3), dp(4));
        return lp;
    }

    private TextView swatch(int color, String text) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextSize(10);
        t.setGravity(Gravity.CENTER);
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.OVAL);
        g.setColor(text.isEmpty() ? color : 0xFFEEEEEE);
        g.setStroke(dp(1), 0x55000000);
        t.setBackground(g);
        t.setTextColor(0xFF333333);
        return t;
    }
}
