package com.ftechz.DebatingTimer;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
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

    private final Service               mService;
    private final NotificationManager   mNotificationManager;
    private final PendingIntent         mIntentStartingHostActivity;
    private       PowerManager.WakeLock mWakeLock;
    private final Vibrator              mVibrator;
    private       Notification          mNotification;
    private       BellRepeater          mBellRepeater        = null;
    private       boolean               mShowingNotification = false;
    private       boolean               mSilentMode          = false;
    private       boolean               mVibrateMode         = true;
    private       boolean               mKeepScreenOn        = true;

    /**
     * Constructor.
     * @param debatingTimerService The instance of {@link DebatingTimerService} to which this
     * AlertManager relates
     */
    public AlertManager(Service debatingTimerService) {

        mService = debatingTimerService;

        // Retrieve the notification manager
        mNotificationManager = (NotificationManager) debatingTimerService.getSystemService(
                Context.NOTIFICATION_SERVICE);

        Intent notificationIntent = new Intent(debatingTimerService, DebatingActivity.class);
        mIntentStartingHostActivity = PendingIntent.getActivity(debatingTimerService, 0, notificationIntent, 0);
        mVibrator = (Vibrator) debatingTimerService.getSystemService(Context.VIBRATOR_SERVICE);

        createWakeLock();
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

    public boolean isWakeLockEnabled() {
        return mKeepScreenOn;
    }

    public void setWakeLockEnabled(boolean wakeLockEnabled) {
        this.mKeepScreenOn = wakeLockEnabled;

        // Also, re-create the wake lock and re-acquire if appropriate
        createWakeLock();  // This also resets the wake lock
        if (mShowingNotification)
            mWakeLock.acquire();
    }

    public void makeActive(PeriodInfo currentPeriodInfo) {

        if(!mShowingNotification) {
            mNotification = new Notification(R.drawable.ic_stat_name,
                    mService.getText(R.string.NotificationTickerText),
                    System.currentTimeMillis());

            updateNotification(currentPeriodInfo.getDescription());
            mService.startForeground(NOTIFICATION_ID, mNotification);

            mShowingNotification = true;
        }

        mWakeLock.acquire();
    }

    public void updateNotification(String notificationText) {
            mNotification.setLatestEventInfo(mService,
                    mService.getText(R.string.NotificationTitle),
                    notificationText, mIntentStartingHostActivity);
    }

    public void makeInactive() {
        if(mShowingNotification) {
            mWakeLock.release();
            mService.stopForeground(true);
            if (mBellRepeater != null) mBellRepeater.stop();
            mVibrator.cancel();
            mShowingNotification = false;
        }
    }

    /**
     * Call this when the activity is paused (from onPause())
     */
    public void activityPause() {
        mWakeLock.release();
    }

    public void activityResume() {
        mWakeLock.acquire();
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
    public void playBell(BellSoundInfo bsi) {
        if (mBellRepeater != null) {
            mBellRepeater.stop();
        }

        if (!mSilentMode) {
            mBellRepeater = new BellRepeater(mService.getApplicationContext(), bsi);
            mBellRepeater.play();
        }
        if (mVibrateMode) {
            mVibrator.vibrate(300 * bsi.getTimesToPlay());
        }
    }

    // Plays a single bell.
    // Intended for use directly with a user button.
    public void playBell() {
        // TODO un-hardcode this R.raw.desk_bell
        BellSoundInfo bellInfo = new BellSoundInfo(R.raw.desk_bell, 1);
        playBell(bellInfo);
    }

    /**
     * Creates the appropriate wake lock based on the "keep screen on" setting.
     * If <code>mWakeLock</code> exists, this releases and overwrites it.
     */
    private void createWakeLock() {

        // If there exists a wake lock, release it.
        if (mWakeLock != null) {
            mWakeLock.release();
            mWakeLock = null;
        }

        // First compile the correct flags.
        // If "keep screen on" is enabled, get the dim wake lock, otherwise, partial lock (this
        // just keeps the CPU running) is sufficient.
        int flags = PowerManager.ON_AFTER_RELEASE;
        if (mKeepScreenOn) flags |= PowerManager.SCREEN_DIM_WAKE_LOCK;
        else flags |= PowerManager.PARTIAL_WAKE_LOCK;

        PowerManager pm = (PowerManager) mService.getSystemService(Context.POWER_SERVICE);

        mWakeLock = pm.newWakeLock(flags, "Debatekeeper");

        // Either we have the lock or we don't, we don't need to count how many times we locked
        // it.  Turning this off makes it okay to acquire or release multiple times.
        mWakeLock.setReferenceCounted(false);
    }
}
