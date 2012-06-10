package com.ftechz.DebatingTimer;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.PowerManager;
import android.os.Vibrator;


/**
* AlertManager manages all alerts for the Debating Timer application.
*
* Only a single instance of AlertManager should exist at any given time.  It receives requests from
* other parts of the application.
*
* @author Phillip Cao
* @author Chuan-Zheng Lee
*/
public class AlertManager
{
    public static final int NOTIFICATION_ID = 1;

    private final DebatingTimerService  mDebatingTimerService;
    private final NotificationManager   mNotificationManager;
    private final PendingIntent         mIntentStartingHostActivity;
    private final PowerManager.WakeLock mWakeLock;
    private final Vibrator              mVibrator;
    private       Notification          mNotification;
    private       AlarmChain            mStage;
    private       BellRepeater          mBellRepeater        = null;
    private       boolean               mShowingNotification = false;
    private       boolean               mSilentMode          = false;
    private       boolean               mVibrateMode         = true;

    /**
     * Constructor.
     * @param debatingTimerService The instance of {@link DebatingTimerService} to which this
     * AlertManager relates
     */
    public AlertManager(DebatingTimerService debatingTimerService) {

        mDebatingTimerService = debatingTimerService;

        // Retrieve the notification manager
        mNotificationManager = (NotificationManager) debatingTimerService.getSystemService(
                Context.NOTIFICATION_SERVICE);
        Intent notificationIntent = new Intent(debatingTimerService, DebatingActivity.class);
        mIntentStartingHostActivity = PendingIntent.getActivity(debatingTimerService, 0, notificationIntent, 0);

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

    public void makeActive(PeriodInfo currentPeriodInfo) {

        if(!mShowingNotification) {
            mNotification = new Notification(R.drawable.ic_stat_name,
                    mDebatingTimerService.getText(R.string.notificationTicker),
                    System.currentTimeMillis());

            updateNotification(currentPeriodInfo.getDescription());
            mDebatingTimerService.startForeground(NOTIFICATION_ID, mNotification);

            mShowingNotification = true;
        }

        mWakeLock.acquire();
    }

    public void updateNotification(String notificationText) {
            mNotification.setLatestEventInfo(mDebatingTimerService,
                    mDebatingTimerService.getText(R.string.notification_title),
                    notificationText, mIntentStartingHostActivity);
    }

    public void makeInactive() {
        if(mShowingNotification) {
            mWakeLock.release();
            mDebatingTimerService.stopForeground(true);
            if (mBellRepeater != null) mBellRepeater.stop();
            mVibrator.cancel();
            mShowingNotification = false;
        }
    }

    public void triggerAlert(BellInfo alert, PeriodInfo currentPeriodInfo) {
        updateNotification(currentPeriodInfo.getDescription());
        if(mShowingNotification) {

            mNotificationManager.notify(NOTIFICATION_ID, mNotification);

            playBell(alert.getBellSoundInfo());

        }
    }

    // Plays a bell according to a given bellInfo.
    // Does not play if in silent mode.
    public void playBell(BellSoundInfo bellInfo) {
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
        BellSoundInfo bellInfo = new BellSoundInfo(R.raw.desk_bell, 1);
        playBell(bellInfo);
    }
}
