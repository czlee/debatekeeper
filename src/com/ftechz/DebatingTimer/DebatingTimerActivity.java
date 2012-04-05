package com.ftechz.DebatingTimer;

import android.app.Activity;
import android.content.*;
import android.os.Bundle;
import android.os.IBinder;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

/**
 * DebatingTimerActivity
 * The first Activity shown when application is started... for now
 *
 */
public class DebatingTimerActivity extends Activity
{
    private TextView mStateText;
    private TextView mSpeakerNameText;
    private TextView mCurrentTimeText;
    private TextView mNextTimeText;
    private TextView mFinalTimeText;
    private Button startTimerButton;
    private Button resetDebateButton;

    private Debate mDebate;

    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.debate);

        mStateText = (TextView) findViewById(R.id.stateText);
        mSpeakerNameText = (TextView) findViewById(R.id.titleText);
        mCurrentTimeText = (TextView) findViewById(R.id.currentTime);
        mNextTimeText = (TextView) findViewById(R.id.nextTime);
        mFinalTimeText = (TextView) findViewById(R.id.finalTime);
        startTimerButton = (Button) findViewById(R.id.startTimerButton);
        resetDebateButton = (Button) findViewById(R.id.resetDebateButton);

        startTimerButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View pV) {
                switch (mDebate.getDebateStatus())
                {
                    case setup:
                    case transitioning:
                        if(mDebate.start())
                        {
                            startTimerButton.setText(R.string.stopTimer);
                        }
                        break;
                    case speaking:
                        mDebate.stop();
                        // Update button depending on the status
                        if(mDebate.getDebateStatus() == Debate.DebateStatus.setup)
                        {
                            startTimerButton.setText(R.string.startTimer);
                        }
                        else
                        {
                            startTimerButton.setText(R.string.startNext);
                        }
                        break;
                    default:
                        break;

                }
                updateGui();
            }
        });

        resetDebateButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View pV) {
                mDebate.reset();
            }
        });

        Intent intent = new Intent(this, DebatingTimerService.class);
        startService(intent);

        bindService(intent, mConnection, Context.BIND_AUTO_CREATE);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        unbindService(mConnection);
        Intent intent = new Intent(this, DebatingTimerService.class);
        stopService(intent);
    }

    private String secsToMinuteSecText(long time) {
        return String.format("%02d:%02d", time/60, time % 60);
    }

    public void updateGui() {
        if(mDebate != null)
        {
            mStateText.setText(mDebate.getStageStateText());
            mSpeakerNameText.setText(mDebate.getTitleText());
            mCurrentTimeText.setText(secsToMinuteSecText(mDebate.getStageCurrentTime()));
            mNextTimeText.setText(secsToMinuteSecText(mDebate.getStageNextTime()));
            mFinalTimeText.setText(secsToMinuteSecText(mDebate.getStageFinalTime()));
        }
    }

    // Second tick broadcast
    private BroadcastReceiver broadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            updateGui();
        }
    };

    @Override
    public void onResume() {
        super.onResume();
        registerReceiver(broadcastReceiver,
                new IntentFilter(DebatingTimerService.BROADCAST_ACTION));
    }

    @Override
    public void onPause() {
        super.onPause();
        unregisterReceiver(broadcastReceiver);
    }

    DebatingTimerService.DebatingTimerServiceBinder mBinder;

    /** Defines callbacks for service binding, passed to bindService() */
    private ServiceConnection mConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName className,
                                       IBinder service) {

            mBinder = (DebatingTimerService.DebatingTimerServiceBinder) service;
            mDebate = mBinder.getDebate();
        }

        @Override
        public void onServiceDisconnected(ComponentName arg0) {
            mDebate = null;
        }
    };

}
