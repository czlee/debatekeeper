package com.ftechz.DebatingTimer;

import android.content.Context;
import android.media.MediaPlayer;

/**
 * Alert manager for playing alerts
 *
 */
public class AlertManager
{
    private Context mContext;
    private MediaPlayer mMediaPlayer;

    public AlertManager(Context context)
    {
        mContext = context;
    }

    public void playAlert(int id)
    {
        if(mMediaPlayer != null)
        {
            release();
        }
        mMediaPlayer = MediaPlayer.create(mContext, id);
        mMediaPlayer.start();
    }

    public void release()
    {
        mMediaPlayer.release();
        mMediaPlayer = null;
    }
}
