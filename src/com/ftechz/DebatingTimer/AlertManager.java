package com.ftechz.DebatingTimer;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.PowerManager;

/**
*
*/
public class AlertManager
{
    public static final int NOTIFICATION_ID = 1;

    private DebatingTimerService mDebatingTimerService;
    private NotificationManager mNotificationManager;
    private Notification mNotification;
    private PendingIntent mPendingIntent;
    private boolean mShowingNotification = false;
    private AlarmChain mStage;
    private PowerManager.WakeLock mWakeLock;

    public AlertManager(DebatingTimerService debatingTimerService)
    {
        mDebatingTimerService = debatingTimerService;
        mNotificationManager = (NotificationManager) debatingTimerService.getSystemService(
                Context.NOTIFICATION_SERVICE);
        Intent mNotificationIntent = new Intent(debatingTimerService,
                DebatingActivity.class);
        mPendingIntent = PendingIntent.getActivity(
                debatingTimerService, 0, mNotificationIntent, 0);

        PowerManager pm = (PowerManager) debatingTimerService.getSystemService(Context.POWER_SERVICE);

        mWakeLock = pm.newWakeLock(
                PowerManager.SCREEN_DIM_WAKE_LOCK | PowerManager.ON_AFTER_RELEASE,
                "DebatingWakeLock");
    }

    public void showNotification(AlarmChain stage)
    {
        mStage = stage;

        if(!mShowingNotification)
        {
            mNotification = new Notification(R.drawable.icon,
                    stage.getNotificationTickerText(),
                    System.currentTimeMillis());

            updateNotification();
            mDebatingTimerService.startForeground(NOTIFICATION_ID, mNotification);

            mShowingNotification = true;
        }

        mWakeLock.acquire();
    }

    public void updateNotification()
    {
        if(mStage != null)
        {
            mNotification.setLatestEventInfo(mDebatingTimerService,
                    mDebatingTimerService.getText(R.string.notification_title),
                    mStage.getNotificationText(), mPendingIntent);
        }
    }

    public void hideNotification()
    {
        if(mShowingNotification)
        {
            mWakeLock.release();
            mDebatingTimerService.stopForeground(true);
            mShowingNotification = false;
        }
    }

    public void triggerAlert(int soundId)
    {
        updateNotification();
        if(mShowingNotification)
        {
            mNotification.sound = Uri.parse("android.resource://com.ftechz.DebatingTimer/" + soundId);
            mNotificationManager.notify(NOTIFICATION_ID, mNotification);
        }
    }
}
