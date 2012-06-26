/**
 *
 */
package com.ftechz.DebatingTimer;

import java.util.Timer;
import java.util.TimerTask;

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

    private enum BellRepeaterState {
        INITIAL,
        PREPARED, // This means it's ready to play, but either it hasn't started or it is between repetitions
        PLAYING,  // This means a sound is currently actually actively playing
        STOPPED,  // This means it was forcibly stopped, for whatever reason
        FINISHED  // This means it finished all of its repetitions
    }

    private final Context           mContext;
    private final BellSoundInfo     mSoundInfo;
    private       BellRepeaterState mState            = BellRepeaterState.INITIAL;
    private       MediaPlayer       mMediaPlayer;
    private       int               mRepetitionsSoFar = 0;
    private       Timer             mTimer            = null;

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
            switch (mState) {
            case PREPARED:
                mState = BellRepeaterState.PLAYING;
                mMediaPlayer.start();
                Log.i("BellRepeater", "Media player starting");
                break;
            case PLAYING:
                // Restart the tone
                // mState remains PLAYING
                mMediaPlayer.seekTo(0);
                Log.i("BellRepeater", "Media player restarting");
                break;
            case STOPPED:
                // In theory this shouldn't happen, because the timer should be cancelled.
                // But just in case, do nothing.
                return;
            default:
                break;
            }

            // If it's not the last repetition, set the completion listener to change the state to
            // PREPARED, so that on the next run() we know to use start() rather than seekTo().
            if (++mRepetitionsSoFar < mSoundInfo.getTimesToPlay()) {
                mMediaPlayer.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {

                    @Override
                    public void onCompletion(MediaPlayer mp) {
                        mState = BellRepeaterState.PREPARED;
                        Log.i("BellRepeater", "Media player completed");
                    }
                });

            // If it's the last repetition, set the completion listener to release the player and
            // change the state to FINISHED, and cancel the timer (i.e. clean everything up).
            } else {
                mMediaPlayer.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {

                    @Override
                    public void onCompletion(MediaPlayer mp) {
                        // The MediaPlayer coming here should be the same one as mMediaPlayer in the BellRepeater class
                        if (mp != mMediaPlayer){
                            Log.e(this.getClass().getSimpleName(), "OnCompletionListener mp wasn't the same as mMediaPlayer!");
                        }
                        mMediaPlayer.release();
                        mMediaPlayer = null;
                        mState = BellRepeaterState.FINISHED;
                        Log.i("BellRepeater", "Over and out");
                    }
                });

                mTimer.cancel();
            }
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
     * Has no effect if the sound resid is 0.
     */
    public void play() {
        if (mSoundInfo.getSoundResid() == 0)
            return;

        if (mState == BellRepeaterState.INITIAL) {

            // Initialise the MediaPlayer
            mMediaPlayer = MediaPlayer.create(mContext, mSoundInfo.getSoundResid());
            // Set to maximum volume possible (it's really soft!)
            mMediaPlayer.setVolume(1, 1);
            // On Error, release it and shut it down and put it away.
            // But log a message so that we know...
            mMediaPlayer.setOnErrorListener(new MediaPlayer.OnErrorListener() {

                @Override
                public boolean onError(MediaPlayer mp, int what, int extra) {
                    Log.e("BellRepeater", "The media player went into an errored state! Releasing.");
                    // The MediaPlayer coming here should be the same one as mMediaPlayer in the BellRepeater class
                    if (mp != mMediaPlayer){
                        Log.e(this.getClass().getSimpleName(), "OnErrorListener mp wasn't the same as mMediaPlayer!");
                    }
                    mMediaPlayer.release();
                    mMediaPlayer = null;
                    return false;
                }
            });

            mRepetitionsSoFar = 0;

            mTimer = new Timer();
            mTimer.schedule(new BellRepeatTask(), 0, mSoundInfo.getRepeatPeriod());

            mState = BellRepeaterState.PREPARED;
        }
    }

    /**
     * Stops playing the repeated sound.
     * Can be called repeatedly; has no effect if already stopped.
     */
    public void stop() {
        mState = BellRepeaterState.STOPPED;
        if (mMediaPlayer != null) {
            mMediaPlayer.stop();
            mMediaPlayer.release();
            mMediaPlayer = null;
            Log.i("BellRepeater", "Stopped");
        }
        if (mTimer != null) {
            mTimer.cancel();
        }
    }

    /**
     * @return True if the BellRepeater can be said to be "busy", false otherwise
     */
    // Implementation: this can be either PREPARED or PLAYING, since in between repetitions
    // does count.
    public boolean isPlaying(){
        return mState == BellRepeaterState.PREPARED || mState == BellRepeaterState.PLAYING;
    }

}
