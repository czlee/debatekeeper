package com.ftechz.DebatingTimer;

import java.util.Timer;
import java.util.TimerTask;

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
* @since  2012-03-30
*/
public class AlertManager
{
    public  static final int NOTIFICATION_ID = 1;
    private static final int BELL_SCREEN_FLASH_TIME = 500;

    private final Service               mService;
    private final NotificationManager   mNotificationManager;
    private final PendingIntent         mIntentStartingHostActivity;
    private final PowerManager          mPowerManager;
    private final Vibrator              mVibrator;
    private       PowerManager.WakeLock mWakeLock;
    private       Notification          mNotification;
    private       BellRepeater          mBellRepeater         = null;
    private       FlashScreenListener   mFlashScreenListener  = null;
    private       boolean               mShowingNotification  = false;
    private       boolean               mSilentMode           = false;
    private       boolean               mVibrateMode          = true;
    private       boolean               mKeepScreenOn         = true;
    private       boolean               mActivityActive       = false;


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

        mIntentStartingHostActivity = PendingIntent.getActivity(debatingTimerService,
                0, new Intent(debatingTimerService, DebatingActivity.class), 0);
        mVibrator = (Vibrator) debatingTimerService.getSystemService(Context.VIBRATOR_SERVICE);
        mPowerManager = (PowerManager) mService.getSystemService(Context.POWER_SERVICE);

        createWakeLock();
    }

    //******************************************************************************************
    // Public methods
    //******************************************************************************************

    /**
     * Call this when the activity is stopped (from onStop())
     */
    public void activityStop() {
        mActivityActive = false;
        mWakeLock.release();
    }

    /**
     * Call this when the activity is started (from onStart())
     */
    public void activityStart() {
        // Note: Write this method so that it can be called multiple times with no bad effect.
        mActivityActive = true;
        if (mShowingNotification)
            mWakeLock.acquire();
    }

    public boolean isSilentMode() {
        return mSilentMode;
    }

    /**
     * Shows the notification.  Call this when the timer is started.
     * @param pi the {@link PeriodInfo} to use in the notification
     */
    public void makeActive(PeriodInfo pi) {

        if(!mShowingNotification) {
            mNotification = new Notification(R.drawable.ic_stat_name,
                    mService.getText(R.string.NotificationTickerText),
                    System.currentTimeMillis());

            updateNotification(pi.getDescription());
            mService.startForeground(NOTIFICATION_ID, mNotification);

            mShowingNotification = true;
        }

        mWakeLock.acquire();
    }

    /**
     * Hides the notification.  Call this when the timer is stopped.
     */
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
     * Plays a single bell.
     * Intended for use directly with a user button.
     */
    public void playSingleBell() {
        // TODO un-hardcode this R.raw.desk_bell
        BellSoundInfo bellInfo = new BellSoundInfo(R.raw.desk_bell, 1);
        playBell(bellInfo);
    }

    /**
     * Plays a bell according to a given {@link BellSoundInfo}.
     * Does not play if in silent mode.
     * @param bsi the <code>BellSoundInfo</code> to play
     */
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

        if (mFlashScreenListener != null) {
            // Flash the screen white and set a timer to turn it back normal after half a second
            mFlashScreenListener.flashScreen(true);
            wakeUpScreenForBell();
            Timer timer = new Timer();
            timer.schedule(new TimerTask() {
                @Override
                public void run() {
                    mFlashScreenListener.flashScreen(false);
                }
            }, BELL_SCREEN_FLASH_TIME);
        }
    }

    /**
     * @param flashScreenListener the {@link FlashScreenListener} to set
     */
    public void setFlashScreenListener(FlashScreenListener flashScreenListener) {
        this.mFlashScreenListener = flashScreenListener;
    }

    public void setSilentMode(boolean silentMode) {
        this.mSilentMode = silentMode;
    }

    public void setVibrateMode(boolean vibrateMode) {
        this.mVibrateMode = vibrateMode;
    }

    public void setKeepScreenOn(boolean keepScreenOn) {
        this.mKeepScreenOn = keepScreenOn;

        // Also, re-create the wake lock and re-acquire if appropriate
        createWakeLock();  // This also resets the wake lock
        if (mShowingNotification)
            mWakeLock.acquire();
    }

    /**
     * Triggers an alert.  Play this to activate a bell.
     * @param bi the {@link BellInfo} to use to play the bell
     * @param pi the {@link PeriodInfo} to use in the notification
     */
    public void triggerAlert(BellInfo bi, PeriodInfo pi) {
        updateNotification(pi.getDescription());
        if(mShowingNotification) {

            mNotificationManager.notify(NOTIFICATION_ID, mNotification);

            playBell(bi.getBellSoundInfo());

        }
    }

    /**
     * Wakes up the screen to attract user attention
     */
    public void wakeUpScreenForPause() {
        int flags = PowerManager.ACQUIRE_CAUSES_WAKEUP | PowerManager.FULL_WAKE_LOCK | PowerManager.ON_AFTER_RELEASE;
        PowerManager.WakeLock temporaryWakeLock = mPowerManager.newWakeLock(flags, "Debatekeeper-pause");
        temporaryWakeLock.acquire(3000);
    }

    //******************************************************************************************
    // Private methods
    //******************************************************************************************

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

        mWakeLock = mPowerManager.newWakeLock(flags, "Debatekeeper");

        // Either we have the lock or we don't, we don't need to count how many times we locked
        // it.  Turning this off makes it okay to acquire or release multiple times.
        mWakeLock.setReferenceCounted(false);
    }

    private void updateNotification(String notificationText) {
            mNotification.setLatestEventInfo(mService,
                    mService.getText(R.string.NotificationTitle),
                    notificationText, mIntentStartingHostActivity);
    }

    /**
     * Wakes up the screen to attract user attention
     */
    private void wakeUpScreenForBell() {
        if (mActivityActive) {
            int flags = PowerManager.ACQUIRE_CAUSES_WAKEUP | PowerManager.FULL_WAKE_LOCK;
            PowerManager.WakeLock temporaryWakeLock = mPowerManager.newWakeLock(flags, "Debatekeeper-bell");
            temporaryWakeLock.acquire(BELL_SCREEN_FLASH_TIME);
        }
    }


}
