package com.ftechz.DebatingTimer;

import java.util.Timer;
import java.util.TimerTask;

import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.util.Log;

/**
 * DebatingTimerService class
 * The background service for the application
 * Keeps the debate/timers ticking in the background
 * Uses a broadcast (though not the best way IMO) to update the main UI
 *
 * NOTE NOTE NOTE NOTE NOTE NOTE NOTE
 * We are NOT using a separate thread for this class.  This means that the Service runs
 * in the same process as the Activity that calls it (DebatingActivity), because we
 * haven't specified otherwise.  This means that this service must NOT do intensive work,
 * because if it does, IT WILL BLOCK THE USER INTERFACE!
 */
public class DebatingTimerService extends Service
{
    public static final String BROADCAST_ACTION = "com.ftechz.DebatingTimer.update";
    private Intent intent;

    private Timer updateGuiTimer;
    private final IBinder mBinder = new DebatingTimerServiceBinder();

    private DebateManager mDebateManager;
    private AlertManager mAlertManager;

    @Override
    public void onCreate() {
        super.onCreate();

        updateGuiTimer = new Timer();

        // TODO: Find a better way to do this than a broadcast
        // Start a new timer task to broadcast a UI update
        intent = new Intent(BROADCAST_ACTION);
        TimerTask mRunnable = new TimerTask() {
            @Override
            public void run() {
                sendBroadcast(DebatingTimerService.this.intent);
            }
        };
        updateGuiTimer.schedule(mRunnable, 0, 200);

        mAlertManager = new AlertManager(this);

    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // We don't care if the service is started multiple times.  It only ever
        // makes sense to have one of these at a given time; all activities should bind
        // do this single instance.  In fact, there should never be more than one
        // activity.

        // We don't do anything with intent.  If we ever do, be sure to check
        // for the possibility that intent could be null!

        Log.v(this.getClass().getSimpleName(), String.format("The service is starting: %d", startId));

        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        if(updateGuiTimer != null) {
            // Clean up stuff
            updateGuiTimer.cancel();
            updateGuiTimer = null;
        }

        if(mDebateManager != null) {
            mDebateManager.release();
            mDebateManager = null;
        }

        Log.v(this.getClass().getSimpleName(), "The service is shutting down now!");
    }

    public class DebatingTimerServiceBinder extends Binder
    {
        public DebateManager getDebateManager() {
            return mDebateManager;
        }

        public AlertManager getAlertManager() {
            return mAlertManager;
        }

        public DebateManager createDebateManager(DebateFormat df) {
            if(mDebateManager != null)
                mDebateManager.release();
            mDebateManager = new DebateManager(df, mAlertManager);
            return mDebateManager;
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

}
