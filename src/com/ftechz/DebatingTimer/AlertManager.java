package com.ftechz.DebatingTimer;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.PowerManager;
import android.os.Vibrator;

import com.ftechz.DebatingTimer.AlarmChain.Event.BellInfo;

/**
* AlertManager class
* Manages all alerts for the application
* Only a single instance of this should exist
*/
public class AlertManager
{
    public static final int NOTIFICATION_ID = 1;

    private final DebatingTimerService mDebatingTimerService;
    private final NotificationManager mNotificationManager;
    private Notification mNotification;
    private final PendingIntent mPendingIntent;
    private boolean mShowingNotification = false;
    private AlarmChain mStage;
    private final PowerManager.WakeLock mWakeLock;
    private BellRepeater mBellRepeater = null;
    private final Vibrator mVibrator;

    private boolean mSilentMode = false;
    private boolean mVibrateMode = true;

    public AlertManager(DebatingTimerService debatingTimerService)
    {
        mDebatingTimerService = debatingTimerService;
        mNotificationManager = (NotificationManager) debatingTimerService.getSystemService(
                Context.NOTIFICATION_SERVICE);
        Intent notificationIntent = new Intent(debatingTimerService, DebatingActivity.class);
        mPendingIntent = PendingIntent.getActivity(debatingTimerService, 0, notificationIntent, 0);

        PowerManager pm = (PowerManager) debatingTimerService.getSystemService(Context.POWER_SERVICE);
        mWakeLock = pm.newWakeLock(
                PowerManager.SCREEN_DIM_WAKE_LOCK | PowerManager.ON_AFTER_RELEASE,
                "DebatingWakeLock");

        mVibrator = (Vibrator) debatingTimerService.getSystemService(Context.VIBRATOR_SERVICE);
    }

    public boolean isSilentMode() {
        return mSilentMode;
    }

    public void setSilentMode(boolean silentMode) {
        this.mSilentMode = silentMode;
    }

    public boolean isVibrateMode() {
        return mVibrateMode;
    }

    public void setVibrateMode(boolean vibrateMode) {
        this.mVibrateMode = vibrateMode;
    }

    public void makeActive(AlarmChain stage)
    {
        mStage = stage;

        if(!mShowingNotification)
        {
            mNotification = new Notification(R.drawable.ic_stat_name,
                    mDebatingTimerService.getText(R.string.notificationTicker),
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

    public void makeInactive()
    {
        if(mShowingNotification)
        {
            mWakeLock.release();
            mDebatingTimerService.stopForeground(true);
            if (mBellRepeater != null){
                mBellRepeater.stop();
            }
            mVibrator.cancel();
            mShowingNotification = false;
        }
    }

    public void triggerAlert(AlarmChain.Event alert)
    {
        updateNotification();
        if(mShowingNotification)
        {

            mNotificationManager.notify(NOTIFICATION_ID, mNotification);

            playBell(alert.getBellInfo());

        }
    }

    // Plays a bell according to a given bellInfo.
    // Does not play if in silent mode.
    public void playBell(BellInfo bellInfo) {
        if (mBellRepeater != null) {
            mBellRepeater.stop();
        }

        if (!mSilentMode) {
            mBellRepeater = new BellRepeater(mDebatingTimerService.getApplicationContext(), bellInfo);
            mBellRepeater.play();
        }
        if (mVibrateMode) {
            mVibrator.vibrate(300 * bellInfo.getTimesToPlay());
        }
    }

    // Plays a single bell.
    // Intended for use directly with a user button.
    public void playBell() {
        // TODO un-hardcode this R.raw.desk_bell
        BellInfo bellInfo = new BellInfo(R.raw.desk_bell, 1);
        playBell(bellInfo);
    }
}
