package com.benjamin.nearly; // CHANGE to your app's package

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;

/**
 * Fixed 10-minute message check for the Messages widget. Not user-changeable.
 * Uses a self-re-arming inexact alarm (JobScheduler cannot go below 15 min). Android may delay it
 * a little while the phone is in deep sleep; the 15-min job in NearlySync is the backup.
 */
final class NearlyMsgAlarm {
    static final long EVERY_MS = 10 * 60 * 1000L;

    private static PendingIntent pi(Context c) {
        Intent i = new Intent(c, NearlyMsgAlarmReceiver.class);
        return PendingIntent.getBroadcast(c, 7310, i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    /** force = true re-arms from now (used right after a check); otherwise keeps an alarm that is already due soon. */
    static void schedule(Context c, boolean force) {
        try {
            AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
            if (am == null) return;
            if (NearlyWidgets.ids(c, "msg").length == 0) {
                am.cancel(pi(c));
                NearlyStore.sp(c).edit().remove("msgAlarmNext").apply();
                return;
            }
            long now = NearlyUtil.now();
            long next = NearlyStore.sp(c).getLong("msgAlarmNext", 0);
            if (!force && next > now && next - now <= EVERY_MS) return;
            long at = now + EVERY_MS;
            NearlyStore.sp(c).edit().putLong("msgAlarmNext", at).apply();
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi(c));
        } catch (Throwable ignored) { }
    }
}
