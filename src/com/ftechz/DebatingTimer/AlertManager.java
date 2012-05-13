package com.ftechz.DebatingTimer;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.PowerManager;
import android.media.MediaPlayer;

/**
* AlertManager class
* Manages all alerts for the application
* Only a single instance of this should exist
*/
// TODO: Reduce notifications to a single ongoing one that exists when and only when a timer
// is running.
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
    private MediaPlayer mMediaPlayer;

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

    public void triggerAlert(AlarmChain.AlarmChainAlert alert)
    {
        updateNotification();
        if(mShowingNotification)
        {
            // TODO: Make this use timesToPlay, the number of times the sound is to be repeated
            
            //mNotification.sound = Uri.parse("android.resource://com.ftechz.DebatingTimer/" + soundId);
            mNotificationManager.notify(NOTIFICATION_ID, mNotification);
            
            if (mMediaPlayer != null) {
                mMediaPlayer.release();
                mMediaPlayer = null;
            }
            // This could be inefficient -- MediaPlayer.create() blocks until the file is loaded, which
            // supposedly can take a while.  But it seems to be working fine, so we'll just leave it
            // here until it becomes a problem...
            mMediaPlayer = MediaPlayer.create(mDebatingTimerService.getApplicationContext(), alert.getSoundResid());
            mMediaPlayer.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
                
                @Override
                public void onCompletion(MediaPlayer mp) {
                    mp.release();
                    mp = null;
                }
            });
            // Set to maximum volume possible (!)
            mMediaPlayer.setVolume((float) 1, (float) 1);
            mMediaPlayer.start();
            
        }
    }
}
