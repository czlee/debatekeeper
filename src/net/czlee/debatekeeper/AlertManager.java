/*
 * Copyright (C) 2012 Phillip Cao, Chuan-Zheng Lee
 *
 * This file is part of the Debatekeeper app, which is licensed under the
 * GNU General Public Licence version 3 (GPLv3).  You can redistribute
 * and/or modify it under the terms of the GPLv3, and you must not use
 * this file except in compliance with the GPLv3.
 *
 * This app is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public Licence for more details.
 *
 * You should have received a copy of the GNU General Public Licence
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package net.czlee.debatekeeper;

import java.util.Timer;
import java.util.TimerTask;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.os.PowerManager;
import android.os.Vibrator;

/**
 * AlertManager manages all alerts for the Debatekeeper application.
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
    private static final long MAX_BELL_SCREEN_FLASH_TIME = 500;
    private static final int  POI_VIBRATE_TIME           = 350;
    public  static final int  NOTIFICATION_ID            = 1;
    private static final int  STROBE_PERIOD              = 100;
    private static final int  BELL_FLASH_COLOUR          = 0xffffffff;
    private static final int  POI_FLASH_COLOUR           = 0xffadd6ff;

    private final Service               mService;

    // System services
    private final NotificationManager   mNotificationManager;
    private final PendingIntent         mIntentStartingHostActivity;
    private final PowerManager          mPowerManager;
    private final Vibrator              mVibrator;
    private       PowerManager.WakeLock mWakeLock;

    // Other things
    private       Notification          mNotification;
    private       BellRepeater          mBellRepeater        = null;
    private       FlashScreenListener   mFlashScreenListener = null;
    private       boolean               mShowingNotification = false;
    private       boolean               mActivityActive      = false;

    // Preferences for speech bells
    private       boolean               mSilentMode;
    private       boolean               mVibrateMode;
    private       boolean               mKeepScreenOn;
    private       FlashScreenMode       mFlashScreenMode     = FlashScreenMode.OFF;

    // Preferences for POI bells
    private       boolean               mPoiBuzzerEnabled;
    private       boolean               mPoiVibrateEnabled = true;
    private       FlashScreenMode       mPoiFlashScreenMode  = FlashScreenMode.SOLID_FLASH;


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

        // Set up defaults
        Resources res = mService.getResources();
        mSilentMode   = res.getBoolean(R.bool.DefaultPrefSilentMode);
        mVibrateMode  = res.getBoolean(R.bool.DefaultPrefVibrateMode);
        mKeepScreenOn = res.getBoolean(R.bool.DefaultPrefKeepScreenOn);

        createWakeLock();
    }

    //******************************************************************************************
    // Public classes
    //******************************************************************************************
    public enum FlashScreenMode {

        // These must match the values string array in the preference.xml file.
        // (We can pull strings from the resource automatically,
        // but we can't assign them to enums automatically.)
        OFF ("off"),
        STROBE_FLASH ("strobeFlash"),
        SOLID_FLASH ("solidFlash");

        private final String prefValue;

        private FlashScreenMode(String prefValue) {
            this.prefValue = prefValue;
        }

        public String toPrefValue() {
            return this.prefValue;
        }

        public static FlashScreenMode toEnum(String key) {
            FlashScreenMode[] values = FlashScreenMode.values();
            for (int i = 0; i < values.length; i++)
                if (key.equals(values[i].prefValue))
                    return values[i];
            throw new IllegalArgumentException(String.format("There is no enumerated constant '%s'", key));
        }
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
     * Takes preferences like silent mode, vibrate mode, flash screen mode into account.
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
            final long[] vibratePattern = getVibratePattern(bsi);
            if (vibratePattern != null)
                mVibrator.vibrate(vibratePattern, -1);
        }

        if (mFlashScreenMode != FlashScreenMode.OFF) {
            flashScreen(bsi, BELL_FLASH_COLOUR);
        }
    }

    /**
     * @param flashScreenListener the {@link FlashScreenListener} to set
     */
    public void setFlashScreenListener(FlashScreenListener flashScreenListener) {
        this.mFlashScreenListener = flashScreenListener;
    }

    public void setFlashScreenMode(FlashScreenMode flashScreenMode) {
        this.mFlashScreenMode = flashScreenMode;
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

    public void setPoiBuzzerEnabled(boolean poiBuzzerEnabled) {
        this.mPoiBuzzerEnabled = poiBuzzerEnabled;
    }

    public void setPoiVibrateEnabled(boolean poiVibrateEnabled) {
        this.mPoiVibrateEnabled = poiVibrateEnabled;
    }

    public void setPoiFlashScreenMode(FlashScreenMode poiFlashScreenMode) {
        this.mPoiFlashScreenMode = poiFlashScreenMode;
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

    public void triggerPoiAlert() {
        if (mPoiBuzzerEnabled)
            // TODO fill this space
            ;

        if (mPoiVibrateEnabled)
            mVibrator.vibrate(POI_VIBRATE_TIME);

        switch (mPoiFlashScreenMode) {
        case SOLID_FLASH:
            startSingleFlashScreen(MAX_BELL_SCREEN_FLASH_TIME, POI_FLASH_COLOUR);
            break;
        case STROBE_FLASH:
            startSingleStrobeFlashScreen(MAX_BELL_SCREEN_FLASH_TIME, POI_FLASH_COLOUR);
            break;
        case OFF:
            // Do nothing
            break;
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
     * Flashes the screen according to the specifications of a bell.
     * @param bsi the {@link BellSoundInfo} for this bell
     */
    private void flashScreen(BellSoundInfo bsi, final int colour) {
        Timer       repeatTimer  = new Timer();
        final long  repeatPeriod = bsi.getRepeatPeriod();
        final int   timesToPlay  = bsi.getTimesToPlay();
        if (timesToPlay == 0) return; // Do nothing if the number of bells is zero

        wakeUpScreenForBell(repeatPeriod * timesToPlay);

        /* Note: To avoid race conditions, we do NOT have a single TimerTask to toggle the
         * screen flash at a fixed rate.  We have one timer to govern turning the screen on
         * at a fixed repeat period.  Each time the screen starts a flash, a *separate* timer
         * is started to turn the screen off.  This guarantees (hopefully) that the last timer
         * task that affects the screen is always one that turns it off.
         */

        repeatTimer.scheduleAtFixedRate(new TimerTask() {
            int timesSoFar = 0;
            @Override
            public void run() {
                long flashTime = repeatPeriod / 2;

                // If half the repeat period is more than the maximum flash time, or if this is
                // the last repetition, make the flash time equal to the maximum
                if (flashTime > MAX_BELL_SCREEN_FLASH_TIME)
                    flashTime = MAX_BELL_SCREEN_FLASH_TIME;

                if (++timesSoFar >= timesToPlay) {
                    flashTime = MAX_BELL_SCREEN_FLASH_TIME;
                    this.cancel();
                }

                switch (mFlashScreenMode) {
                case SOLID_FLASH:
                    startSingleFlashScreen(flashTime, colour);
                    break;
                case STROBE_FLASH:
                    startSingleStrobeFlashScreen(flashTime, colour);
                    break;
                case OFF:
                    // Do nothing
                    break;
                }

            }
        }, 0, repeatPeriod);
    }

    /**
     * Flashes the screen once.
     * @param flashTime how long in milliseconds to flash the screen for
     */
    private void startSingleFlashScreen(long flashTime, final int colour) {
        if (mFlashScreenListener == null) return;

        // Flash the screen white and set a timer to turn it back normal after half a second
        mFlashScreenListener.flashScreenOn(colour);
        Timer offTimer = new Timer();
        offTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                mFlashScreenListener.flashScreenOff();
            }
        }, flashTime);
    }

    /**
     * Runs a strobe flash
     * @param numberOfStrobes The number of strobes to do.
     */
    private void startSingleStrobeFlashScreen(long flashTime, final int colour) {
        Timer     strobeTimer = new Timer();

        int numberOfStrobes = (int) (flashTime / STROBE_PERIOD);
        if (flashTime % STROBE_PERIOD > STROBE_PERIOD / 2) numberOfStrobes++;
        final int numStrobes = numberOfStrobes;

        if (numStrobes == 0) return; // Do nothing if the number of bells is zero

        /* Note: To avoid race conditions, we do NOT have a single TimerTask to toggle the
         * screen flash at a fixed rate.  We have one timer to govern turning the screen on
         * at a fixed repeat period.  Each time the screen starts a flash, a *separate* timer
         * is started to turn the screen off.  This guarantees (hopefully) that the last timer
         * task that affects the screen is always one that turns it off.
         */

        strobeTimer.scheduleAtFixedRate(new TimerTask() {
            int timesSoFar = 0;
            @Override
            public void run() {
                startSingleFlashScreen(STROBE_PERIOD * 2 / 3, colour);
                if (++timesSoFar >= numStrobes) {
                    this.cancel();
                }
            }
        }, 0, STROBE_PERIOD);
    }

    /**
     * Wakes up the screen to attract user attention
     */
    private void wakeUpScreenForBell(long wakeTime) {
        if (mActivityActive) {
            int flags = PowerManager.ACQUIRE_CAUSES_WAKEUP | PowerManager.FULL_WAKE_LOCK;
            PowerManager.WakeLock temporaryWakeLock = mPowerManager.newWakeLock(flags, "Debatekeeper-bell");
            temporaryWakeLock.acquire(wakeTime);
        }
    }

    /**
     * @param bsi the {@link BellSoundInfo} for this bell
     * @return a long array that can be passed to Vibrator.vibrate(), or <code>null</code> if it
     * should not vibrate.
     */
    private long[] getVibratePattern(BellSoundInfo bsi) {
        long repeatPeriod = bsi.getRepeatPeriod();
        int  timesToPlay  = bsi.getTimesToPlay();

        // Don't vibrate on a bell that is rung zero times
        if (timesToPlay == 0) return null;

        // Generally, we want the total period to be the same as the bell sound period,
        // and we want the gap between vibrations to be 100ms.  But if that would cause
        // the vibration on time to be less than 80% of the total time, then reduce the
        // gap so that it is equal to 20%.  The threshold here is 100 * 5 = 500 ms.
        long vibrateOffTime = (repeatPeriod < 500) ? repeatPeriod / 5 : 100;
        long vibrateOnTime = repeatPeriod - vibrateOffTime;

        // We guaranteed that timesToPlay is not zero at the beginning of this method.
        long[] pattern = new long[timesToPlay * 2];

        // The pattern is {0, ON, OFF, ON, OFF, ..., OFF, ON}
        pattern[0] = 0;
        for (int i = 1; i < pattern.length-1; i = i + 2) {
            pattern[i]   = vibrateOnTime;
            pattern[i+1] = vibrateOffTime;
        }
        pattern[pattern.length - 1] = vibrateOnTime;

        return pattern;
    }

}
