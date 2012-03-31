package com.ftechz.DebatingTimer;

import android.app.IntentService;
import android.app.Notification;
import android.app.PendingIntent;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;

import java.util.Timer;
import java.util.TimerTask;

/**
 * DebatingTimerService class
 * The background service for the application
 * Keeps the debate/timers ticking in the background
 * Uses a broadcast (though not the best way IMO) to update the main UI
 */
public class DebatingTimerService extends IntentService
{
    public static final String BROADCAST_ACTION = "com.ftechz.DebatingTimer.update";
    public static final int ONGOING_NOTIFICATION = 1;
    Intent intent;

    private Timer tickTimer;
    private final IBinder mBinder = new DebatingTimerServiceBinder();

    private Debate mDebate;
    private Speaker mSpeaker1;      // Affirmative
    private Speaker mSpeaker2;
    private Speaker mSpeaker3;      // Negative
    private Speaker mSpeaker4;

    private AlarmChain.AlarmChainAlert substativeSpeechAlerts[];
    private AlarmChain.AlarmChainAlert replySpeechAlerts[];

    private AlertManager mAlertManager;


    private NotificationControl mNofiNotificationControl;

    public DebatingTimerService() {
        super("DebatingTimerService");
    }

    @Override
    public void onCreate() {
        super.onCreate();

        tickTimer = new Timer();

        intent = new Intent(BROADCAST_ACTION);
        TimerTask mRunnable = new TimerTask() {
            @Override
            public void run() {
                sendBroadcast(DebatingTimerService.this.intent);
            }
        };
        tickTimer.schedule(mRunnable, 0, 200);

        mAlertManager = new AlertManager(this);

        substativeSpeechAlerts = new AlarmChain.AlarmChainAlert[] {
            new SpeakerTimer.WarningAlert(5, mAlertManager),
            new SpeakerTimer.FinishAlert(10, mAlertManager),
            new SpeakerTimer.OvertimeAlert(15, 2, mAlertManager)
        };

        replySpeechAlerts = new AlarmChain.AlarmChainAlert[] {
            new SpeakerTimer.WarningAlert(2, mAlertManager),
            new SpeakerTimer.FinishAlert(3, mAlertManager),
            new SpeakerTimer.OvertimeAlert(5, 2, mAlertManager)
        };

        mNofiNotificationControl = new NotificationControl();

        mDebate = new Debate(mNofiNotificationControl);
        // Set up speakers
        mSpeaker1 = new Speaker("Speaker1");
        mSpeaker2 = new Speaker("Speaker2");
        mSpeaker3 = new Speaker("Speaker3");
        mSpeaker4 = new Speaker("Speaker4");

        mDebate.addStage(mSpeaker1, substativeSpeechAlerts);
        mDebate.addStage(mSpeaker3, substativeSpeechAlerts);
        mDebate.addStage(mSpeaker2, substativeSpeechAlerts);
        mDebate.addStage(mSpeaker4, substativeSpeechAlerts);
        mDebate.addStage(mSpeaker3, replySpeechAlerts);
        mDebate.addStage(mSpeaker1, replySpeechAlerts);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        // Clean up stuff
        tickTimer.cancel();
        tickTimer = null;

        mAlertManager.release();
        mAlertManager = null;

        mDebate.release();
        mDebate = null;
    }

    public class DebatingTimerServiceBinder extends Binder
    {
        public Debate getDebate()
        {
            return mDebate;
        }
    }

    public class NotificationControl
    {
        private Intent notificationIntent;
        private Notification notification;
        private PendingIntent pendingIntent;
        private boolean mShowingNotification = false;

        public NotificationControl()
        {
            notificationIntent = new Intent(DebatingTimerService.this,
                    DebatingTimer.class);
            pendingIntent = PendingIntent.getActivity(
                    DebatingTimerService.this, 0, notificationIntent, 0);
        }

        public void showNotification(String title, String message)
        {
            if(!mShowingNotification)
            {
                notification = new Notification(R.drawable.icon, getText(R.string.ticker_text),
                        System.currentTimeMillis());

                notification.setLatestEventInfo(DebatingTimerService.this,
                        title, message, pendingIntent);
                startForeground(ONGOING_NOTIFICATION, notification);
                mShowingNotification = true;
            }
        }

        public void setNotificationText()
        {

        }

        public void hideNotification()
        {
            if(mShowingNotification)
            {
                stopForeground(true);
                mShowingNotification = false;
            }
        }
    }


    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    @Override
    protected void onHandleIntent(Intent intent) {

    }
}
