/*
 * Copyright (C) 2012 Phillip Cao, Chuan-Zheng Lee
 *
 * This file is part of the Debatekeeper app, which is licensed under the GNU
 * General Public Licence version 3 (GPLv3).  You can redistribute and/or modify
 * it under the terms of the GPLv3, and you must not use this file except in
 * compliance with the GPLv3.
 *
 * This app is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR
 * A PARTICULAR PURPOSE.  See the GNU General Public Licence for more details.
 *
 * You should have received a copy of the GNU General Public Licence along with
 * this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package net.czlee.debatekeeper

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import net.czlee.debatekeeper.debateformat.BellSoundInfo
import java.util.Timer
import java.util.TimerTask

/**
 * AlertManager manages all alerts for the Debatekeeper application.
 *
 * Only a single instance of AlertManager should exist at any given time.  It receives requests
 * from other parts of the application.
 *
 * @param debatingTimerService The instance of [DebatingTimerService] to which this
 * AlertManager relates
 *
 * @author Phillip Cao
 * @author Chuan-Zheng Lee
 * @since  2012-03-30
 */
class AlertManager(debatingTimerService: Service) {

    private val mService: Service = debatingTimerService

    // System services
    private val mNotificationManager =
            debatingTimerService.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private val mIntentForOngoingNotification: PendingIntent
    private val mPowerManager: PowerManager
    private val mVibrator =
            debatingTimerService.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
    private var mWakeLock: PowerManager.WakeLock? = null

    // Other things
    private var mNotification: Notification? = null
    private var mBellRepeater: BellRepeater? = null
    private var mFlashScreenListener: FlashScreenListener? = null
    private var mShowingNotification = false
    private var mActivityActive = false

    // Preferences for speech bells
    private var mBellsEnabled: Boolean
    private var mVibrateMode: Boolean
    private var mFlashScreenMode = FlashScreenMode.OFF

    // Preferences for POI bells
    @Suppress("unused")
    private var mPoiBuzzerEnabled = false // for future use
    private var mPoiVibrateEnabled = true
    private var mPoiFlashScreenMode = FlashScreenMode.SOLID_FLASH

    init {
        mPowerManager = mService.getSystemService(Context.POWER_SERVICE) as PowerManager

        // Create a PendingIntent for the notification we raise while the timer is running.
        val intent = Intent(debatingTimerService, DebatingActivity::class.java)
        // This flag prevents the activity from having multiple instances on the back stack,
        // so that when the user presses the notification while already in Debatekeeper, pressing
        // back won't make the user go through several instances of Debatekeeper on the back stack.
        intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)

        var flags = PendingIntent.FLAG_UPDATE_CURRENT
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
            flags = flags or PendingIntent.FLAG_IMMUTABLE
        mIntentForOngoingNotification = PendingIntent.getActivity(debatingTimerService, 0,
                intent, flags)

        // Set up defaults
        val res = mService.resources
        mBellsEnabled = res.getBoolean(R.bool.prefDefault_ringBells)
        mVibrateMode = res.getBoolean(R.bool.prefDefault_vibrateMode)

        createWakeLock()
    }

    //******************************************************************************************
    // Public classes
    //******************************************************************************************

    /**
     * A user of [AlertManager] wishing to use the "flash screen" functions of it must
     * pass a class implementing this interface to [setFlashScreenListener].
     * The class must do two things:
     *
     *  1. Implement the graphics side of flashing the screen
     *  2. If necessary, implement a semaphore for shared access to the screen colour
     *
     * @author Chuan-Zheng Lee
     */
    interface FlashScreenListener {
        /**
         * This is called by [AlertManager] at the beginning of a screen-flash.  (In the
         * case of a strobe flash, it is called just once before the first strobe.)  It should
         * execute any preparation necessary before a screen-flash starts.  This will likely
         * involve a semaphore, as [AlertManager] supports screen flashes in multiple
         * situations, which are not guaranteed not to coincide.
         *
         * If this method returns `false`, then [AlertManager] will not continue
         * with the screen-flash.  If it returns `true`, then it will.  It is acceptable
         * for this method to block until a semaphore permit becomes available.
         *
         * @return `true` if the flash screen is allowed to continue, `false`
         * if the flash screen is disallowed
         */
        fun begin(): Boolean

        /**
         * This is called by [AlertManager] to turn on a screen-flash.  In strobe flashes,
         * it is called once for each strobe (*i.e.* lots of times).
         * @param colour the colour of the screen-flash
         */
        fun flashScreenOn(colour: Int)

        /**
         * This is called by [AlertManager] to turn off a screen flash.  In strobe flashes,
         * it is called once for each strobe (*i.e.* lots of times).
         */
        fun flashScreenOff()

        /**
         * This is called by [AlertManager] at the end of a screen-flash.  (In the case of
         * a strobe flash, it is called just once after all the strobes are completed.)  It should
         * execute any clean-up necessary.  This will likely involve releasing a semaphore
         * acquired in [begin].  It might also involve updating the parent GUI.
         */
        fun done()
    }

    enum class FlashScreenMode(private val prefValue: String) {

        // These must match the values string array in the preference.xml file.
        // (We can pull strings from the resource automatically,
        // but we can't assign them to enums automatically.)
        OFF("off"),
        STROBE_FLASH("strobeFlash"),
        SOLID_FLASH("solidFlash");

        companion object {
            @JvmStatic
            fun toEnum(key: String): FlashScreenMode {
                for (value in entries)
                    if (key == value.prefValue)
                        return value
                throw IllegalArgumentException(
                        String.format("There is no enumerated constant '%s'", key))
            }
        }
    }

    //******************************************************************************************
    // Public methods
    //******************************************************************************************

    /**
     * Call this when the activity is stopped (from onStop())
     */
    fun activityStop() {
        mActivityActive = false
        mWakeLock?.release()
    }

    /**
     * Call this when the activity is started (from onStart())
     */
    fun activityStart() {
        // Note: Write this method so that it can be called multiple times with no bad effect.
        mActivityActive = true
        if (mShowingNotification)
            mWakeLock?.acquire(15 * 60 * 1000L /*15 minutes*/)
    }

    val isBellsEnabled: Boolean
        get() = mBellsEnabled

    /**
     * Shows the notification.  Call this when the timer is started.
     * @param speechName the speech name to use in the notification
     */
    fun makeActive(speechName: String?) {

        if (!mShowingNotification) {

            val builder = NotificationCompat.Builder(mService, DebatingTimerService.CHANNEL_ID)
            builder.setSmallIcon(R.drawable.ic_stat_debatekeeper)
                    .setContentTitle(mService.getString(R.string.notification_title))
                    .setContentText(mService.getString(R.string.notification_text, speechName))
                    .setContentIntent(mIntentForOngoingNotification)
                    .setCategory(NotificationCompat.CATEGORY_ALARM)
                    .setPriority(NotificationCompat.PRIORITY_LOW)

            mNotification = builder.build()
            ServiceCompat.startForeground(mService, NOTIFICATION_ID, mNotification!!,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
            mShowingNotification = true
        }

        mWakeLock?.acquire(15 * 60 * 1000L /*15 minutes*/)
    }

    /**
     * Hides the notification.  Call this when the timer is stopped.
     */
    fun makeInactive() {
        if (mShowingNotification) {
            mWakeLock?.release()
            ServiceCompat.stopForeground(mService, ServiceCompat.STOP_FOREGROUND_REMOVE)
            mBellRepeater?.stop()
            mVibrator.cancel()
            mShowingNotification = false
        }
    }

    /**
     * Plays a single bell.
     * Intended for use directly with a user button.
     */
    fun playSingleBell() {
        val bellInfo = BellSoundInfo(1)
        playBell(bellInfo)
    }

    /**
     * Plays a bell according to a given [BellSoundInfo].
     * Takes preferences like ring bells mode, vibrate mode, flash screen mode into account.
     * @param bsi the `BellSoundInfo` to play
     */
    fun playBell(bsi: BellSoundInfo) {
        mBellRepeater?.stop()

        if (mBellsEnabled) {
            val bellRepeater = BellRepeater(mService.applicationContext, bsi)
            mBellRepeater = bellRepeater
            bellRepeater.play()
        }

        if (mVibrateMode) {
            val vibratePattern = getVibratePattern(bsi)
            if (vibratePattern != null)
                vibrate(vibratePattern)
        }

        if (mFlashScreenMode != FlashScreenMode.OFF) {
            flashScreen(bsi)
        }
    }

    /**
     * @param flashScreenListener the [FlashScreenListener] to set
     */
    fun setFlashScreenListener(flashScreenListener: FlashScreenListener?) {
        mFlashScreenListener = flashScreenListener
    }

    fun setFlashScreenMode(flashScreenMode: FlashScreenMode) {
        mFlashScreenMode = flashScreenMode
    }

    fun setBellsEnabled(bellsEnabled: Boolean) {
        mBellsEnabled = bellsEnabled
        val bellRepeater = mBellRepeater
        if (bellRepeater != null && bellRepeater.isPlaying)
            bellRepeater.stop()
    }

    fun setVibrateMode(vibrateMode: Boolean) {
        mVibrateMode = vibrateMode
    }

    // for future use
    fun setPoiBuzzerEnabled(poiBuzzerEnabled: Boolean) {
        mPoiBuzzerEnabled = poiBuzzerEnabled
    }

    fun setPoiVibrateEnabled(poiVibrateEnabled: Boolean) {
        mPoiVibrateEnabled = poiVibrateEnabled
    }

    fun setPoiFlashScreenMode(poiFlashScreenMode: FlashScreenMode) {
        mPoiFlashScreenMode = poiFlashScreenMode
    }

    /**
     * Triggers an alert.  Play this to activate a bell.
     * @param bsi the [BellSoundInfo] to use to play the bell
     */
    fun triggerAlert(bsi: BellSoundInfo) {
        if (mShowingNotification) {
            mNotificationManager.notify(NOTIFICATION_ID, mNotification!!)
            playBell(bsi)
        }
    }

    fun triggerPoiAlert() {
        // if (mPoiBuzzerEnabled)
            // TODO fill this space
        //    ;

        if (mPoiVibrateEnabled)
            vibrate(POI_VIBRATE_TIME.toLong())

        val flashScreenListener = mFlashScreenListener
        if (flashScreenListener != null) {
            when (mPoiFlashScreenMode) {
                FlashScreenMode.SOLID_FLASH ->
                    if (flashScreenListener.begin())
                        startSingleFlashScreen(MAX_BELL_SCREEN_FLASH_TIME, POI_FLASH_COLOUR, true)
                FlashScreenMode.STROBE_FLASH ->
                    if (flashScreenListener.begin())
                        startSingleStrobeFlashScreen(MAX_BELL_SCREEN_FLASH_TIME, POI_FLASH_COLOUR,
                                true)
                FlashScreenMode.OFF -> {
                    // Do nothing
                }
            }
        }
    }

    /**
     * Wakes up the screen to attract user attention
     */
    fun wakeUpScreenForPause() {
        @Suppress("deprecation")
        val flags = PowerManager.ACQUIRE_CAUSES_WAKEUP or PowerManager.FULL_WAKE_LOCK or
                PowerManager.ON_AFTER_RELEASE
        val temporaryWakeLock = mPowerManager.newWakeLock(flags, "debatekeeper:pause")
        temporaryWakeLock.acquire(3000)
    }

    //******************************************************************************************
    // Private methods
    //******************************************************************************************

    /**
     * Creates the appropriate wake lock based on the "keep screen on" setting.
     * If `mWakeLock` exists, this releases and overwrites it.
     */
    private fun createWakeLock() {

        // If there exists a wake lock, release it.
        mWakeLock?.release()

        val wakeLock = mPowerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,
                "debatekeeper:main")

        // Either we have the lock or we don't, we don't need to count how many times we locked
        // it.  Turning this off makes it okay to acquire or release multiple times.
        wakeLock.setReferenceCounted(false)
        mWakeLock = wakeLock
    }

    /**
     * Flashes the screen according to the specifications of a bell.
     * @param bsi the [BellSoundInfo] for this bell
     */
    private fun flashScreen(bsi: BellSoundInfo) {
        val repeatTimer = Timer()
        val repeatPeriod = bsi.repeatPeriod
        val numberOfBells = bsi.numberOfBells
        if (numberOfBells == 0) return // Do nothing if the number of bells is zero

        val flashScreenListener = mFlashScreenListener ?: return

        // Try to acquire a semaphore; if we can't, return immediately and don't bother
        // with the flash screen
        if (!flashScreenListener.begin())
            return

        wakeUpScreenForBell(repeatPeriod * numberOfBells)

        /* Note: To avoid race conditions, we do NOT have a single TimerTask to toggle the
         * screen flash at a fixed rate.  We have one timer to govern turning the screen on
         * at a fixed repeat period.  Each time the screen starts a flash, a *separate* timer
         * is started to turn the screen off.  This guarantees (hopefully) that the last timer
         * task that affects the screen is always one that turns it off.
         */

        repeatTimer.scheduleAtFixedRate(object : TimerTask() {
            var timesSoFar = 0
            override fun run() {
                var flashTime = repeatPeriod / 2

                // If half the repeat period is more than the maximum flash time, or if this is
                // the last repetition, make the flash time equal to the maximum
                if (flashTime > MAX_BELL_SCREEN_FLASH_TIME)
                    flashTime = MAX_BELL_SCREEN_FLASH_TIME

                val lastFlash = ++timesSoFar >= numberOfBells
                if (lastFlash) {
                    flashTime = MAX_BELL_SCREEN_FLASH_TIME
                    cancel()
                }

                when (mFlashScreenMode) {
                    FlashScreenMode.SOLID_FLASH ->
                        startSingleFlashScreen(flashTime, BELL_FLASH_COLOUR, lastFlash)
                    FlashScreenMode.STROBE_FLASH ->
                        startSingleStrobeFlashScreen(flashTime, BELL_FLASH_COLOUR, lastFlash)
                    FlashScreenMode.OFF -> {
                        // Do nothing
                    }
                }
            }
        }, 0, repeatPeriod)
    }

    /**
     * Flashes the screen once.  The most atomic flash screen action.
     * @param flashTime how long in milliseconds to flash the screen for
     * @param colour colour to flash screen
     * @param lastFlash `true` if the GUI should be reset after this single flash
     */
    private fun startSingleFlashScreen(flashTime: Long, colour: Int, lastFlash: Boolean) {
        val flashScreenListener = mFlashScreenListener ?: return

        // Flash the screen white and set a timer to turn it back normal after half a second
        flashScreenListener.flashScreenOn(colour)
        val offTimer = Timer()
        offTimer.schedule(object : TimerTask() {
            override fun run() {
                flashScreenListener.flashScreenOff()
                if (lastFlash)
                    flashScreenListener.done()
            }
        }, flashTime)
    }

    /**
     * Starts a single strobe flash, i.e., one rapid period of flashing.  So in a double strobe
     * bell, there are two of these.
     * @param flashTime how long in milliseconds the strobe flash should last
     * @param colour colour to flash screen
     * @param lastFlash `true` if the GUI should be reset after this strobe flash
     */
    private fun startSingleStrobeFlashScreen(flashTime: Long, colour: Int, lastFlash: Boolean) {
        val strobeTimer = Timer()

        var numberOfStrobes = (flashTime / STROBE_PERIOD).toInt()
        if (flashTime % STROBE_PERIOD > STROBE_PERIOD / 2) numberOfStrobes++
        val numStrobes = numberOfStrobes

        if (numStrobes == 0) return // Do nothing if the number of bells is zero

        /* Note: To avoid race conditions, we do NOT have a single TimerTask to toggle the
         * screen flash at a fixed rate.  We have one timer to govern turning the screen on
         * at a fixed repeat period.  Each time the screen starts a flash, a *separate* timer
         * is started to turn the screen off.  This guarantees (hopefully) that the last timer
         * task that affects the screen is always one that turns it off.
         */

        strobeTimer.scheduleAtFixedRate(object : TimerTask() {
            var timesSoFar = 0
            override fun run() {
                if (++timesSoFar < numStrobes) {
                    startSingleFlashScreen(STROBE_PERIOD * 2 / 3, colour, false)
                } else {
                    // If it's the last flash in this strobe *and* this strobe was the last strobe
                    // flash in the sequence, then pass true to lastFlash of
                    // startSingleFlashScreen.
                    startSingleFlashScreen(STROBE_PERIOD * 2 / 3, colour, lastFlash)
                    cancel()
                }
            }
        }, 0, STROBE_PERIOD)
    }

    /**
     * Wakes up the screen to attract user attention
     */
    private fun wakeUpScreenForBell(wakeTime: Long) {
        if (mActivityActive) {
            @Suppress("deprecation")
            val flags = PowerManager.ACQUIRE_CAUSES_WAKEUP or PowerManager.FULL_WAKE_LOCK
            val temporaryWakeLock = mPowerManager.newWakeLock(flags, "debatekeeper:bell")
            temporaryWakeLock.acquire(wakeTime)
        }
    }

    /**
     * @param bsi the [BellSoundInfo] for this bell
     * @return a long array that can be passed to Vibrator.vibrate(), or `null` if it
     * should not vibrate.
     */
    private fun getVibratePattern(bsi: BellSoundInfo): LongArray? {
        val repeatPeriod = bsi.repeatPeriod
        val numberOfBells = bsi.numberOfBells

        // Don't vibrate on a bell that is rung zero times
        if (numberOfBells == 0) return null

        // Generally, we want the total period to be the same as the bell sound period,
        // and we want the gap between vibrations to be 100ms.  But if that would cause
        // the vibration on time to be less than 80% of the total time, then reduce the
        // gap so that it is equal to 20%.  The threshold here is 100 * 5 = 500 ms.
        val vibrateOffTime = if (repeatPeriod < 500) repeatPeriod / 5 else 100
        val vibrateOnTime = repeatPeriod - vibrateOffTime

        // We guaranteed that numberOfBells is not zero at the beginning of this method.
        val pattern = LongArray(numberOfBells * 2)

        // The pattern is {0, ON, OFF, ON, OFF, ..., OFF, ON}
        pattern[0] = 0
        var i = 1
        while (i < pattern.size - 1) {
            pattern[i] = vibrateOnTime
            pattern[i + 1] = vibrateOffTime
            i += 2
        }
        pattern[pattern.size - 1] = vibrateOnTime

        return pattern
    }

    /**
     * Vibrates once for the given duration, using [VibrationEffect] on API 26+ where the
     * old `vibrate(long)` is deprecated.
     * @param milliseconds duration of the vibration
     */
    @Suppress("deprecation")
    private fun vibrate(milliseconds: Long) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            mVibrator.vibrate(VibrationEffect.createOneShot(milliseconds,
                    VibrationEffect.DEFAULT_AMPLITUDE))
        else
            mVibrator.vibrate(milliseconds)
    }

    /**
     * Vibrates with the given pattern (not repeating), using [VibrationEffect] on API 26+
     * where the old `vibrate(long[], int)` is deprecated.
     * @param pattern a pattern as accepted by [VibrationEffect.createWaveform]
     */
    @Suppress("deprecation")
    private fun vibrate(pattern: LongArray) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            mVibrator.vibrate(VibrationEffect.createWaveform(pattern, -1))
        else
            mVibrator.vibrate(pattern, -1)
    }

    companion object {
        private const val MAX_BELL_SCREEN_FLASH_TIME: Long = 500
        private const val POI_VIBRATE_TIME = 350
        private const val NOTIFICATION_ID = 1
        private const val STROBE_PERIOD: Long = 100
        private const val BELL_FLASH_COLOUR = -0x1        // 0xffffffff
        private const val POI_FLASH_COLOUR = -0x522901    // 0xffadd6ff
    }
}
