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
 * @author Chuan-Zheng Lee
 * BellPlayer manages a MediaPlayer to ring a bell.
 * As well as playing the bell sound, it can repeat the sound a given number of times,
 * with a given delay in between.
 * There should be one instance of this for every bell.
 */
public class BellRepeater extends TimerTask {

    private enum BellRepeaterState {
        INITIAL,
        PREPARED, // This means it's ready to play, but either it hasn't started or it is between repetitions
        PLAYING,  // This means a sound is currently actually actively playing
        STOPPED,
        FINISHED
    }

    private BellRepeaterState mState = BellRepeaterState.INITIAL;
    private MediaPlayer mMediaPlayer;
    private final Context mContext;
    private final AlarmChain.Event.BellInfo mBellInfo;
    private int mRepetitionsSoFar = 0;
    private Timer mTimer = null;
    /**
     *
     */
    public BellRepeater(Context context, AlarmChain.Event.BellInfo bellInfo) {
        mContext  = context;
        mBellInfo = bellInfo;
        mState    = BellRepeaterState.INITIAL;
    }

    /**
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
        if (++mRepetitionsSoFar < mBellInfo.getTimesToPlay()) {
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
                    mp.release();
                    mp = null;
                    mState = BellRepeaterState.FINISHED;
                    Log.i("BellRepeater", "Over and out");
                }
            });

            mTimer.cancel();
        }
    }

    // Starts playing the repeated sound
    public void play() {
        if (mState == BellRepeaterState.INITIAL) {

            // Initialise the MediaPlayer
            mMediaPlayer = MediaPlayer.create(mContext, mBellInfo.getSoundResid());
            // Set to maximum volume possible (it's really soft!)
            mMediaPlayer.setVolume(1, 1);

            mRepetitionsSoFar = 0;

            mTimer = new Timer();
            mTimer.schedule(this, 0, mBellInfo.getRepeatPeriod());

            mState = BellRepeaterState.PREPARED;
        }
    }

    // Stops playing the repeated sound
    // Can be called repeatedly; has no effect if already stopped.
    public void stop() {
        mState = BellRepeaterState.STOPPED;
        if (mMediaPlayer != null) {
            // TODO: This line sometimes runs into an IllegalStateException with mMediaPlayer
            if (mMediaPlayer.isPlaying()) mMediaPlayer.stop();
            mMediaPlayer.release();
            mMediaPlayer = null;
            Log.i("BellRepeater", "Stopped");
        }
        mTimer.cancel();
    }

    // Returns True if the BellRepeater can be said to be "busy".
    // Implementation: this can be either PREPARED or PLAYING, since in between repetitions
    // does count.
    public boolean isPlaying(){
        return mState == BellRepeaterState.PREPARED || mState == BellRepeaterState.PLAYING;
    }

}
