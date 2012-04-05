package com.ftechz.DebatingTimer;

import android.app.Activity;
import android.content.*;
import android.os.Bundle;
import android.os.IBinder;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

/**
 * DebatingTimer
 * The first Activity shown when application is started... for now
 *
 */
public class DebatingTimer extends Activity
{
    private TextView mStateText;
    private TextView mSpeakerNameText;
    private TextView mCurrentTimeText;
    private TextView mNextTimeText;
    private TextView mFinalTimeText;
    private Button startTimerButton;
    private Button resetDebateButton;

    private Debate debate;
    
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
                switch (debate.getDebateStatus())
                {
                    case setup:
                    case transitioning:
                        if(debate.start())
                        {
                            startTimerButton.setText(R.string.stopTimer);
                        }
                        break;
                    case speaking:
                        debate.stop();
                        // Update button depending on the status
                        if(debate.getDebateStatus() == Debate.DebateStatus.setup)
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
                debate.reset();
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
        if(debate != null)
        {
            mStateText.setText(debate.getStageStateText());
            mSpeakerNameText.setText(debate.getTitleText());
            mCurrentTimeText.setText(secsToMinuteSecText(debate.getStageCurrentTime()));
            mNextTimeText.setText(secsToMinuteSecText(debate.getStageNextTime()));
            mFinalTimeText.setText(secsToMinuteSecText(debate.getStageFinalTime()));
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
            debate = mBinder.createDebate();
            setupDebate();
        }

        @Override
        public void onServiceDisconnected(ComponentName arg0) {
            debate = null;
        }
    };

    private Speaker mSpeaker1;      // Affirmative
    private Speaker mSpeaker2;
    private Speaker mSpeaker3;      // Negative
    private Speaker mSpeaker4;

    private AlarmChain.AlarmChainAlert prepAlerts[];
    private AlarmChain.AlarmChainAlert substativeSpeechAlerts[];
    private AlarmChain.AlarmChainAlert replySpeechAlerts[];

    public void setupDebate()
    {
        prepAlerts = new AlarmChain.AlarmChainAlert[] {
                new SpeakerTimer.WarningAlert(2),
                new SpeakerTimer.WarningAlert(4),
                new SpeakerTimer.FinishAlert(7)
        };

        substativeSpeechAlerts = new AlarmChain.AlarmChainAlert[] {
                new SpeakerTimer.WarningAlert(5),
                new SpeakerTimer.FinishAlert(10),
                new SpeakerTimer.OvertimeAlert(15, 2)
        };

        replySpeechAlerts = new AlarmChain.AlarmChainAlert[] {
                new SpeakerTimer.WarningAlert(2),
                new SpeakerTimer.FinishAlert(3),
                new SpeakerTimer.OvertimeAlert(5, 2)
        };

        // Set up speakers
        mSpeaker1 = new Speaker("Speaker1");
        mSpeaker2 = new Speaker("Speaker2");
        mSpeaker3 = new Speaker("Speaker3");
        mSpeaker4 = new Speaker("Speaker4");

        //Add in the alarm sets
        debate.addAlarmSet("prep", prepAlerts);
        debate.addAlarmSet("substantiveSpeech", substativeSpeechAlerts);
        debate.addAlarmSet("replySpeech", replySpeechAlerts);

        // Add in the stages
        debate.addPrep("prep");
        debate.addStage(mSpeaker1, "substantiveSpeech");
        debate.addStage(mSpeaker3, "substantiveSpeech");
        debate.addStage(mSpeaker2, "substantiveSpeech");
        debate.addStage(mSpeaker4, "substantiveSpeech");
        debate.addStage(mSpeaker3, "replySpeech");
        debate.addStage(mSpeaker1, "replySpeech");
    }
}
