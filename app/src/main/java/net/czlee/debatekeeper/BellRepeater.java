/*
 * Copyright (C) 2012 Chuan-Zheng Lee
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
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import net.czlee.debatekeeper.debateformat.BellSoundInfo;
import android.content.Context;
import android.media.MediaPlayer;
import android.util.Log;

/**
 * BellRepeater uses {@link MediaPlayer} to repeat a bell sound.
 *
 * As well as playing the bell sound, it can repeat the sound a given number of times,
 * with a given delay in between. There should be one instance of this for every bell.
 *
 * It is the responsibility of the caller to stop, delete and recreate this class if that
 * is what the caller wishes to do when a bell is started before a previous one is finished.
 *
 * @author Chuan-Zheng Lee
 * @since  2012-05-12
 */
public class BellRepeater {

    private static final String TAG = "BellRepeater";

    private enum BellRepeaterState {
        INITIAL,
        PREPARED, // This means it's ready to play, but either it hasn't started or it is between repetitions
        PLAYING,  // This means a sound is currently actually actively playing
        STOPPED,  // This means it was forcibly stopped, for whatever reason
        FINISHED  // This means it finished all of its repetitions
    }

    private final Context           mContext;
    private final BellSoundInfo     mSoundInfo;
    private       BellRepeaterState mState;
    private       MediaPlayer       mMediaPlayer;
    private       int               mRepetitionsSoFar = 0;
    private       Timer             mTimer            = null;

    private final Semaphore         mSemaphore = new Semaphore(1, true);

    //******************************************************************************************
    // Private classes
    //******************************************************************************************

    private class BellRepeatTask extends TimerTask {

        /* (non-Javadoc)
         * @see java.util.TimerTask#run()
         * This method runs at a fixed rate.
         */
        @Override
        public void run() {

            if (!tryAcquireSemaphore()) return;

            switch (mState) {
            case PREPARED:
                mState = BellRepeaterState.PLAYING;
                mMediaPlayer.start();
                // Log.i("BellRepeater", "Media player starting");
                break;
            case PLAYING:
                // Restart the tone
                // mState remains PLAYING
                mMediaPlayer.seekTo(0);
                // Log.i("BellRepeater", "Media player restarting");
                break;
            case STOPPED:
                // In theory this shouldn't happen, because the timer should be cancelled.
                // But just in case, do nothing.
                releaseSemaphore();
                return;
            default:
                break;
            }

            // If it's not the last repetition, set the completion listener to change the state to
            // PREPARED, so that on the next run() we know to use start() rather than seekTo().
            if (++mRepetitionsSoFar < mSoundInfo.getTimesToPlay()) {
                mMediaPlayer.setOnCompletionListener(mp -> {
                    mState = BellRepeaterState.PREPARED;
                    // Log.i("BellRepeater", "Media player completed");
                });

            // If it's the last repetition, set the completion listener to release the player and
            // change the state to FINISHED, and cancel the timer (i.e. clean everything up).
            } else {
                mMediaPlayer.setOnCompletionListener(mp -> {
                    // The MediaPlayer coming here should be the same one as mMediaPlayer in the BellRepeater class
                    if (mp != mMediaPlayer){
                        Log.e(TAG, "OnCompletionListener mp wasn't the same as mMediaPlayer!");
                    }
                    mMediaPlayer.release();
                    mMediaPlayer = null;
                    mState = BellRepeaterState.FINISHED;
                    // Log.i("BellRepeater", "Over and out");
                });

                mTimer.cancel();
            }

            releaseSemaphore();
        }

    }

    //******************************************************************************************
    // Public methods
    //******************************************************************************************

    /**
     * Constructor.
     * @param context The context that is used for MediaPlayer (probably a Service)
     * @param bellInfo A BellInfo object containing information about the bell to be played
     */
    public BellRepeater(Context context, BellSoundInfo bellInfo) {
        super();
        mContext  = context;
        mSoundInfo = bellInfo;
        mState    = BellRepeaterState.INITIAL;
    }

    /**
     * Starts playing the repeated sound.
     * Has no effect if the sound resid is 0 or the times to play is 0.
     */
    public void play() {
        if (mSoundInfo.getSoundResid() == 0 || mSoundInfo.getTimesToPlay() == 0)
            return;

        if (mState == BellRepeaterState.INITIAL) {

            if (!tryAcquireSemaphore()) return;

            // Initialise the MediaPlayer
            mMediaPlayer = MediaPlayer.create(mContext, mSoundInfo.getSoundResid());
            // Set to maximum volume possible (it's really soft!)
            mMediaPlayer.setVolume(1, 1);
            // On Error, release it and shut it down and put it away.
            // But log a message so that we know...
            mMediaPlayer.setOnErrorListener((mp, what, extra) -> {
                Log.e(TAG, "The media player went into an errored state! Releasing.");
                // The MediaPlayer coming here should be the same one as mMediaPlayer in the BellRepeater class
                if (mp != mMediaPlayer)
                    Log.e(TAG, "OnErrorListener mp wasn't the same as mMediaPlayer!");
                if (!tryAcquireSemaphore()) return false;
                mMediaPlayer.release();
                mMediaPlayer = null;
                releaseSemaphore();
                return false;
            });

            mRepetitionsSoFar = 0;

            mTimer = new Timer();
            mTimer.schedule(new BellRepeatTask(), 0, mSoundInfo.getRepeatPeriod());

            mState = BellRepeaterState.PREPARED;

            releaseSemaphore();
        }
    }

    /**
     * Stops playing the repeated sound.
     * Can be called repeatedly; has no effect if already stopped.
     */
    public void stop() {

        if (!tryAcquireSemaphore()) return;

        mState = BellRepeaterState.STOPPED;
        if (mMediaPlayer != null) {
            mMediaPlayer.stop();
            mMediaPlayer.release();
            mMediaPlayer = null;
            // Log.i("BellRepeater", "Stopped");
        }
        if (mTimer != null) {
            mTimer.cancel();
        }

        releaseSemaphore();
    }

    /**
     * @return True if the BellRepeater can be said to be "busy", false otherwise
     */
    // Implementation: this can be either PREPARED or PLAYING, since in between repetitions
    // does count.
    public boolean isPlaying(){
        return mState == BellRepeaterState.PREPARED || mState == BellRepeaterState.PLAYING;
    }

    //******************************************************************************************
    // Private methods
    //******************************************************************************************

    /**
     * Tries to acquire the semaphore lock, timing out after two seconds.  Methods that call this
     * method must call <code>releaseSemaphore()</code> when done.
     * @return <code>true</code> if the semaphore was acquired, <code>false</code> otherwise.
     */
    private boolean tryAcquireSemaphore() {
        try {
            if (mSemaphore.tryAcquire(2, TimeUnit.SECONDS)) return true;
            else {
                Log.e(TAG, "Could not acquire semaphore");
                return false;
            }
        } catch (InterruptedException e) {
            Log.e(TAG, "Interrupted while acquiring semaphore");
            return false;
        }
    }

    /**
     * Releases the semaphore lock.  Methods that <code>tryAcquireSemaphore()</code> must call this method
     * when done.
     */
    private void releaseSemaphore() {
        mSemaphore.release();
    }

}
