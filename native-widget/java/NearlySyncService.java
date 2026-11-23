package com.benjamin.nearly; // CHANGE to your app's package

import android.app.job.JobParameters;
import android.app.job.JobService;

/** Periodic background refresh: pulls the couple snapshot, publishes battery/location, repaints widgets. */
public class NearlySyncService extends JobService {
    @Override
    public boolean onStartJob(final JobParameters params) {
        new Thread(new Runnable() {
            public void run() {
                NearlySync.run(getApplicationContext());
                jobFinished(params, false);
            }
        }).start();
        return true;
    }

    @Override
    public boolean onStopJob(JobParameters params) { return true; }
}
