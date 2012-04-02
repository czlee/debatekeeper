package com.ftechz.DebatingTimer;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;

/**
*
*/
public class AlertManager
{
    public static final int NOTIFICATION_ID = 1;

    private DebatingTimerService mDebatingTimerService;
    private NotificationManager mNotificationManager;
    private Intent mNotificationIntent;
    private Notification mNotification;
    private PendingIntent mPendingIntent;
    private boolean mShowingNotification = false;

    public AlertManager(DebatingTimerService debatingTimerService)
    {
        mDebatingTimerService = debatingTimerService;
        mNotificationManager = (NotificationManager) debatingTimerService.getSystemService(Context.NOTIFICATION_SERVICE);
        mNotificationIntent = new Intent(debatingTimerService,
                DebatingTimer.class);
        mPendingIntent = PendingIntent.getActivity(
                debatingTimerService, 0, mNotificationIntent, 0);
    }

    public void showNotification(String title, String message)
    {
        if(!mShowingNotification)
        {
            mNotification = new Notification(R.drawable.icon, mDebatingTimerService.getText(R.string.ticker_text),
                    System.currentTimeMillis());

            mNotification.setLatestEventInfo(mDebatingTimerService,
                    title, message, mPendingIntent);
            mDebatingTimerService.startForeground(NOTIFICATION_ID, mNotification);
            mShowingNotification = true;

            //Enable notifications for latter alerts
            mNotification.defaults = Notification.DEFAULT_VIBRATE;
        }
    }

    public void updateNotification()
    {

    }

    public void hideNotification()
    {
        if(mShowingNotification)
        {
            mDebatingTimerService.stopForeground(true);
            mShowingNotification = false;
        }
    }

    public void triggerAlert(int soundId)
    {
        if(mShowingNotification)
        {
            mNotification.sound = Uri.parse("android.resource://com.ftechz.DebatingTimer/" + soundId);
            mNotificationManager.notify(NOTIFICATION_ID, mNotification);
        }
    }
}
