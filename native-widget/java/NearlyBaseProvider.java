package com.benjamin.nearly; // CHANGE to your app's package

import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.os.Bundle;

/** Shared lifecycle for all Nearly widget types. Each instance is rendered independently. */
public abstract class NearlyBaseProvider extends AppWidgetProvider {
    abstract String type();

    @Override
    public void onUpdate(Context c, AppWidgetManager m, int[] ids) {
        for (int id : ids) NearlyRenderer.update(c, m, id, type());      // paint cached data right away
        NearlySync.schedule(c);
        final PendingResult pr = goAsync();
        final Context app = c.getApplicationContext();
        new Thread(new Runnable() {
            public void run() {
                try { NearlySync.run(app); } finally { pr.finish(); }
            }
        }).start();
    }

    @Override
    public void onAppWidgetOptionsChanged(Context c, AppWidgetManager m, int id, Bundle opts) {
        NearlyRenderer.update(c, m, id, type());                          // resized: re-layout
    }

    @Override
    public void onDeleted(Context c, int[] ids) {
        for (int id : ids) NearlyStore.clearW(c, id);                     // forget that instance's settings
        NearlySync.schedule(c);
    }

    @Override
    public void onEnabled(Context c) { NearlySync.schedule(c); }

    @Override
    public void onDisabled(Context c) {
        if (NearlyWidgets.count(c) == 0) NearlySync.schedule(c);          // cancels the job when none are left
    }
}
