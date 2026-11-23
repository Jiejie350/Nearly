package com.benjamin.nearly; // CHANGE to your app's package

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Fires every 10 minutes while a Messages widget exists: pull new messages, repaint, re-arm. */
public class NearlyMsgAlarmReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context ctx, Intent i) {
        final PendingResult pr = goAsync();
        final Context c = ctx.getApplicationContext();
        NearlyMsgAlarm.schedule(c, true);               // re-arm first so a crash can't stop the chain
        new Thread(new Runnable() {
            public void run() {
                try { NearlySync.run(c); } finally { pr.finish(); }
            }
        }).start();
    }
}
