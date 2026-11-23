package com.benjamin.nearly; // CHANGE to your app's package

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import com.getcapacitor.BridgeActivity;

public class MainActivity extends BridgeActivity {
    private final Handler beat = new Handler();
    private final Runnable tick = new Runnable() {
        public void run() {
            NearlySync.presenceAsync(MainActivity.this, true);   // "online" heartbeat while the app is open
            beat.postDelayed(this, 60000);
        }
    };

    @Override
    public void onCreate(Bundle savedInstanceState) {
        registerPlugin(NearlyWidgetPlugin.class);
        super.onCreate(savedInstanceState);
        rememberPage(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        rememberPage(intent);
    }

    @Override
    public void onResume() {
        super.onResume();
        beat.removeCallbacks(tick);
        beat.post(tick);
        NearlySync.syncIfStale(this, 15000);                     // widgets catch up as soon as you open the app
    }

    @Override
    public void onPause() {
        beat.removeCallbacks(tick);
        NearlySync.presenceAsync(this, false);                   // goes offline; "last active" = now
        super.onPause();
    }

    /** Widgets open the app with a "nearly_page" extra; JS reads it via NearlyWidget.getPendingPage(). */
    private void rememberPage(Intent i) {
        if (i == null || !i.hasExtra("nearly_page")) return;
        getSharedPreferences(NearlyStore.FILE, MODE_PRIVATE).edit().putString("pendingPage", i.getStringExtra("nearly_page")).apply();
    }
}
