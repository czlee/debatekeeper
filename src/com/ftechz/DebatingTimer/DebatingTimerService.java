package com.ftechz.DebatingTimer;

import java.util.Timer;
import java.util.TimerTask;

import android.app.IntentService;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.util.Log;

/**
 * DebatingTimerService class
 * The background service for the application
 * Keeps the debate/timers ticking in the background
 * Uses a broadcast (though not the best way IMO) to update the main UI
 */
public class DebatingTimerService extends IntentService
{
    public static final String BROADCAST_ACTION = "com.ftechz.DebatingTimer.update";
    private Intent intent;

    private Timer tickTimer;
    private final IBinder mBinder = new DebatingTimerServiceBinder();

    private Debate mDebate;

    private AlertManager mAlertManager;

    public DebatingTimerService() {
        super("DebatingTimerService");
    }

    @Override
    public void onCreate() {
        super.onCreate();

        tickTimer = new Timer();

        // Start a new timertask to broadcast a ui update
        intent = new Intent(BROADCAST_ACTION);
        TimerTask mRunnable = new TimerTask() {
            @Override
            public void run() {
                sendBroadcast(DebatingTimerService.this.intent);
            }
        };
        tickTimer.schedule(mRunnable, 0, 200);

        mAlertManager = new AlertManager(this);

    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        if(tickTimer != null)
        {
            // Clean up stuff
            tickTimer.cancel();
            tickTimer = null;
        }

        if(mDebate != null)
        {
            mDebate.release();
            mDebate = null;
        }

        Log.v(this.getClass().getSimpleName(), "The service is shutting down now!");
    }

    public class DebatingTimerServiceBinder extends Binder
    {
        public Debate getDebate()
        {
            return mDebate;
        }

        public Debate createDebate()
        {
            if(mDebate != null)
            {
                mDebate.release();
            }
            mDebate = new Debate(mAlertManager);
            return mDebate;
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    @Override
    protected void onHandleIntent(Intent intent) {

    }
}
