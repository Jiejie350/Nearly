package com.benjamin.nearly; // CHANGE to your app's package

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Re-arms the background checks after a reboot or an app update. */
public class NearlyBootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context c, Intent i) {
        Context app = c.getApplicationContext();
        NearlySync.schedule(app);
        NearlyMsgAlarm.schedule(app, true);
        NearlyWidgets.refreshAll(app);
    }
}
