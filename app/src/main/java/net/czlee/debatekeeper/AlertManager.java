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

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.os.PowerManager;
import android.os.Vibrator;
import android.support.v4.app.NotificationCompat;

import net.czlee.debatekeeper.debateformat.BellSoundInfo;

import java.util.Timer;
import java.util.TimerTask;

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
    private final PendingIntent         mIntentForOngoingNotification;
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

        // System services
        mNotificationManager = (NotificationManager) debatingTimerService.getSystemService(
                Context.NOTIFICATION_SERVICE);
        mVibrator = (Vibrator) debatingTimerService.getSystemService(Context.VIBRATOR_SERVICE);
        mPowerManager = (PowerManager) mService.getSystemService(Context.POWER_SERVICE);

        // Create a PendingIntent for the notification we raise while the timer is running.
        Intent intent = new Intent(debatingTimerService, DebatingActivity.class);
        // This flag prevents the activity from having multiple instances on the back stack,
        // so that when the user presses the notification while already in Debatekeeper, pressing
        // back won't make the user go through several instances of Debatekeeper on the back stack.
        intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);

        mIntentForOngoingNotification = PendingIntent.getActivity(debatingTimerService,
                0, intent, 0);

        // Set up defaults
        Resources res = mService.getResources();
        mSilentMode   = res.getBoolean(R.bool.prefDefault_silentMode);
        mVibrateMode  = res.getBoolean(R.bool.prefDefault_vibrateMode);

        createWakeLock();
    }

    //******************************************************************************************
    // Public classes
    //******************************************************************************************
    /**
     * A user of {@link AlertManager} wishing to use the "flash screen" functions of it must
     * pass a class implementing this interface to <code>setFlashScreenListener()</code>.
     * The class must do two things:
     * <ol><li>Implement the graphics side of flashing the screen</li>
     * <li>If necessary, implement a semaphore for shared access to the screen colour</li>
     * </ol>
     * @author Chuan-Zheng Lee
     *
     */
    public interface FlashScreenListener {
        /**
         * This is called by {@link AlertManager} at the beginning of a screen-flash.  (In the
         * case of a strobe flash, it is called just once before the first strobe.)  It should
         * execute any preparation necessary before a screen-flash starts.  This will likely
         * involve a semaphore, as {@link AlertManager} supports screen flashes in multiple
         * situations, which are not guaranteed not to coincide.
         *
         * <p>If this method returns <code>false</code>, then {@link AlertManager} will not continue
         * with the screen-flash.  If it returns <code>true</code>, then it will.  It is acceptable
         * for this method to block until a semaphore permit becomes available.</p>
         *
         * @return <code>true</code> if the flash screen is allowed to continue, <code>false</code>
         * if the flash screen is disallowed
         */
        boolean begin();

        /**
         * This is called by {@link AlertManager} to turn on a screen-flash.  In strobe flashes,
         * it is called once for each strobe (<i>i.e.</i> lots of times).
         * @param colour the colour of the screen-flash
         */

        void flashScreenOn(int colour);

        /**
         * This is called by {@link AlertManager} to turn off a screen flash.  In strobe flashes,
         * it is called once for each strobe (<i>i.e.</i> lots of times).
         */
        void flashScreenOff();

        /**
         * This is called by {@link AlertManager} at the end of a screen-flash.  (In the case of
         * a strobe flash, it is called just once after all the strobes are completed.)  It should
         * execute any clean-up necessary.  This will likely involve releasing a semaphore
         * acquired in <code>begin()</code>.  It might also involve updating the parent GUI.
         */
        void done();
    }

    public enum FlashScreenMode {

        // These must match the values string array in the preference.xml file.
        // (We can pull strings from the resource automatically,
        // but we can't assign them to enums automatically.)
        OFF ("off"),
        STROBE_FLASH ("strobeFlash"),
        SOLID_FLASH ("solidFlash");

        private final String prefValue;

        FlashScreenMode(String prefValue) {
            this.prefValue = prefValue;
        }

        public String toPrefValue() {
            return this.prefValue;
        }

        public static FlashScreenMode toEnum(String key) {
            for (FlashScreenMode value : FlashScreenMode.values())
                if (key.equals(value.prefValue))
                    return value;
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
     * @param speechName the speech name to use in the notification
     */
    public void makeActive(String speechName) {

        if(!mShowingNotification) {

            NotificationCompat.Builder builder = new NotificationCompat.Builder(mService);
            builder.setSmallIcon(R.drawable.ic_stat_name)
                   .setTicker(mService.getText(R.string.notification_tickerText))
                   .setContentTitle(mService.getText(R.string.notification_title))
                   .setContentText(speechName)
                   .setContentIntent(mIntentForOngoingNotification);

            mNotification = builder.build();
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
        BellSoundInfo bellInfo = new BellSoundInfo(1);
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
     * @param bsi the {@link BellSoundInfo} to use to play the bell
     */
    public void triggerAlert(BellSoundInfo bsi) {
        if(mShowingNotification) {
            mNotificationManager.notify(NOTIFICATION_ID, mNotification);
            playBell(bsi);
        }
    }

    public void triggerPoiAlert() {
        // if (mPoiBuzzerEnabled)
            // TODO fill this space
        //    ;

        if (mPoiVibrateEnabled)
            mVibrator.vibrate(POI_VIBRATE_TIME);

        if (mFlashScreenListener != null) {
            switch (mPoiFlashScreenMode) {
            case SOLID_FLASH:
                if (mFlashScreenListener.begin())
                    startSingleFlashScreen(MAX_BELL_SCREEN_FLASH_TIME, POI_FLASH_COLOUR, true);
                break;
            case STROBE_FLASH:
                if (mFlashScreenListener.begin())
                    startSingleStrobeFlashScreen(MAX_BELL_SCREEN_FLASH_TIME, POI_FLASH_COLOUR, true);
                break;
            case OFF:
                // Do nothing
                break;
            }
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

        mWakeLock = mPowerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Debatekeeper");

        // Either we have the lock or we don't, we don't need to count how many times we locked
        // it.  Turning this off makes it okay to acquire or release multiple times.
        mWakeLock.setReferenceCounted(false);
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

        if (mFlashScreenListener == null) return;

        // Try to acquire a semaphore; if we can't, return immediately and don't bother
        // with the flash screen
        if (!mFlashScreenListener.begin())
            return;

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

                final boolean lastFlash = ++timesSoFar >= timesToPlay;
                if (lastFlash) {
                    flashTime = MAX_BELL_SCREEN_FLASH_TIME;
                    this.cancel();
                }

                switch (mFlashScreenMode) {
                case SOLID_FLASH:
                    startSingleFlashScreen(flashTime, colour, lastFlash);
                    break;
                case STROBE_FLASH:
                    startSingleStrobeFlashScreen(flashTime, colour, lastFlash);
                    break;
                case OFF:
                    // Do nothing
                    break;
                }

            }
        }, 0, repeatPeriod);
    }

    /**
     * Flashes the screen once.  The most atomic flash screen action.
     * @param flashTime how long in milliseconds to flash the screen for
     * @param colour colour to flash screen
     * @param lastFlash <code>true</code> if the GUI should be reset after this single flash
     */
    private void startSingleFlashScreen(long flashTime, final int colour, final boolean lastFlash) {
        if (mFlashScreenListener == null) return;

        // Flash the screen white and set a timer to turn it back normal after half a second
        mFlashScreenListener.flashScreenOn(colour);
        Timer offTimer = new Timer();
        offTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                mFlashScreenListener.flashScreenOff();
                if (lastFlash)
                    mFlashScreenListener.done();
            }
        }, flashTime);
    }

    /**
     * Runs a strobe flash
     * @param numberOfStrobes The number of strobes to do.
     */
    /**
     * Starts a single strobe flash, i.e., one rapid period of flashing.  So in a double strobe
     * bell, there are two of these.
     * @param flashTime how long in milliseconds the strobe flash should last
     * @param colour colour to flash screen
     * @param lastFlash <code>true</code> if the GUI should be reset after this strobe flash
     */
    private void startSingleStrobeFlashScreen(long flashTime, final int colour, final boolean lastFlash) {
        Timer strobeTimer = new Timer();

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
                if (++timesSoFar < numStrobes) {
                    startSingleFlashScreen(STROBE_PERIOD * 2 / 3, colour, false);
                } else {
                    // If it's the last flash in this strobe *and* this strobe was the last strobe
                    // flash in the sequence, then pass true to lastFlash of startSingleFlashScreen.
                    startSingleFlashScreen(STROBE_PERIOD * 2 / 3, colour, lastFlash);
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
