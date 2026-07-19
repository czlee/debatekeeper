/*
 * Copyright (C) 2012 Chuan-Zheng Lee
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

import android.content.Context
import android.media.MediaPlayer
import android.util.Log
import net.czlee.debatekeeper.debateformat.BellSoundInfo
import java.util.Timer
import java.util.TimerTask
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit

/**
 * BellRepeater uses [MediaPlayer] to repeat a bell sound.
 *
 * As well as playing the bell sound, it can repeat the sound a given number of times,
 * with a given delay in between. There should be one instance of this for every bell.
 *
 * It is the responsibility of the caller to stop, delete and recreate this class if that
 * is what the caller wishes to do when a bell is started before a previous one is finished.
 *
 * @param mContext the context that is used for MediaPlayer (probably a Service)
 * @param mSoundInfo a [BellSoundInfo] object containing information about the bell to be played
 *
 * @author Chuan-Zheng Lee
 * @since  2012-05-12
 */
class BellRepeater(private val mContext: Context, private val mSoundInfo: BellSoundInfo) {

    private var mState = BellRepeaterState.INITIAL
    private var mMediaPlayer: MediaPlayer? = null
    private var mRepetitionsSoFar = 0
    private var mTimer: Timer? = null

    private val mSemaphore = Semaphore(1, true)

    private enum class BellRepeaterState {
        INITIAL,
        PREPARED, // This means it's ready to play, but either it hasn't started or it is between repetitions
        PLAYING,  // This means a sound is currently actually actively playing
        STOPPED,  // This means it was forcibly stopped, for whatever reason
        FINISHED  // This means it finished all of its repetitions
    }

    //******************************************************************************************
    // Private classes
    //******************************************************************************************

    private inner class BellRepeatTask : TimerTask() {

        override fun run() {

            if (!tryAcquireSemaphore()) return

            when (mState) {
                BellRepeaterState.PREPARED -> {
                    mState = BellRepeaterState.PLAYING
                    mMediaPlayer!!.start()
                    // Log.i("BellRepeater", "Media player starting");
                }
                BellRepeaterState.PLAYING -> {
                    // Restart the tone
                    // mState remains PLAYING
                    mMediaPlayer!!.seekTo(0)
                    // Log.i("BellRepeater", "Media player restarting");
                }
                BellRepeaterState.STOPPED -> {
                    // In theory this shouldn't happen, because the timer should be cancelled.
                    // But just in case, do nothing.
                    releaseSemaphore()
                    return
                }
                else -> {}
            }

            // If it's not the last repetition, set the completion listener to change the state to
            // PREPARED, so that on the next run() we know to use start() rather than seekTo().
            if (++mRepetitionsSoFar < mSoundInfo.timesToRepeatMedia) {
                mMediaPlayer!!.setOnCompletionListener {
                    mState = BellRepeaterState.PREPARED
                    // Log.i("BellRepeater", "Media player completed");
                }

            // If it's the last repetition, set the completion listener to release the player and
            // change the state to FINISHED, and cancel the timer (i.e. clean everything up).
            } else {
                mMediaPlayer!!.setOnCompletionListener { mp ->
                    // The MediaPlayer coming here should be the same one as mMediaPlayer in the
                    // BellRepeater class
                    if (mp !== mMediaPlayer) {
                        Log.e(TAG, "OnCompletionListener mp wasn't the same as mMediaPlayer!")
                    }
                    mMediaPlayer!!.release()
                    mMediaPlayer = null
                    mState = BellRepeaterState.FINISHED
                    // Log.i("BellRepeater", "Over and out");
                }

                mTimer!!.cancel()
            }

            releaseSemaphore()
        }
    }

    //******************************************************************************************
    // Public methods
    //******************************************************************************************

    /**
     * Starts playing the repeated sound.
     * Has no effect if the sound resource ID is 0 or the times to play is 0.
     */
    fun play() {
        if (mSoundInfo.soundResId == 0 || mSoundInfo.timesToRepeatMedia == 0)
            return

        if (mState == BellRepeaterState.INITIAL) {

            if (!tryAcquireSemaphore()) return

            // Initialise the MediaPlayer
            val mediaPlayer = MediaPlayer.create(mContext, mSoundInfo.soundResId)
            mMediaPlayer = mediaPlayer
            // Set to maximum volume possible (it's really soft!)
            mediaPlayer.setVolume(1f, 1f)
            // On Error, release it and shut it down and put it away.
            // But log a message so that we know...
            mediaPlayer.setOnErrorListener { mp, _, _ ->
                Log.e(TAG, "The media player went into an error state! Releasing.")
                // The MediaPlayer coming here should be the same one as mMediaPlayer in the
                // BellRepeater class
                if (mp !== mMediaPlayer)
                    Log.e(TAG, "OnErrorListener mp wasn't the same as mMediaPlayer!")
                if (!tryAcquireSemaphore()) return@setOnErrorListener false
                mMediaPlayer!!.release()
                mMediaPlayer = null
                releaseSemaphore()
                false
            }

            mRepetitionsSoFar = 0

            val timer = Timer()
            mTimer = timer
            timer.schedule(BellRepeatTask(), 0, mSoundInfo.repeatPeriod)

            mState = BellRepeaterState.PREPARED

            releaseSemaphore()
        }
    }

    /**
     * Stops playing the repeated sound.
     * Can be called repeatedly; has no effect if already stopped.
     */
    fun stop() {

        if (!tryAcquireSemaphore()) return

        mState = BellRepeaterState.STOPPED
        mMediaPlayer?.let {
            it.stop()
            it.release()
            mMediaPlayer = null
            // Log.i("BellRepeater", "Stopped");
        }
        mTimer?.cancel()

        releaseSemaphore()
    }

    /**
     * @return True if the BellRepeater can be said to be "busy", false otherwise
     */
    // Implementation: this can be either PREPARED or PLAYING, since in between repetitions
    // does count.
    val isPlaying: Boolean
        get() = mState == BellRepeaterState.PREPARED || mState == BellRepeaterState.PLAYING

    //******************************************************************************************
    // Private methods
    //******************************************************************************************

    /**
     * Tries to acquire the semaphore lock, timing out after two seconds.  Methods that call this
     * method must call [releaseSemaphore] when done.
     * @return `true` if the semaphore was acquired, `false` otherwise.
     */
    private fun tryAcquireSemaphore(): Boolean {
        return try {
            if (mSemaphore.tryAcquire(2, TimeUnit.SECONDS)) true
            else {
                Log.e(TAG, "Could not acquire semaphore")
                false
            }
        } catch (e: InterruptedException) {
            Log.e(TAG, "Interrupted while acquiring semaphore")
            false
        }
    }

    /**
     * Releases the semaphore lock.  Methods that [tryAcquireSemaphore] must call this method
     * when done.
     */
    private fun releaseSemaphore() {
        mSemaphore.release()
    }

    companion object {
        private const val TAG = "BellRepeater"
    }
}
