package com.ftechz.DebatingTimer;

import android.app.IntentService;
import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.os.SystemClock;

import java.util.*;

import static java.util.Collections.sort;

/**
 * Created by IntelliJ IDEA.
 * User: Phil
 * Date: 3/24/12
 * Time: 11:47 PM
 * To change this template use File | Settings | File Templates.
 */
public class DebatingTimerService extends IntentService
{
    public static final String BROADCAST_ACTION = "com.ftechz.DebatingTimer.update";
    Intent intent;

    public DebatingTimerService() {
        super("DebatingTimerService");
    }

    private Timer tickTimer;
    private final IBinder mBinder = new DebatingTimerServiceBinder();
    
    private Debate debate;
    private Speaker mSpeaker1;      // Affirmative
    private Speaker mSpeaker2;
    private Speaker mSpeaker3;      // Negative
    private Speaker mSpeaker4;

    private final AlarmChain.AlarmChainAlert substativeSpeechAlerts[] = {
            new SpeakerTimer.WarningAlert(5),
            new SpeakerTimer.FinishAlert(10),
            new SpeakerTimer.OvertimeAlert(15, 2)
    };

    private final AlarmChain.AlarmChainAlert replySpeechAlerts[] = {
            new SpeakerTimer.WarningAlert(2),
            new SpeakerTimer.FinishAlert(3),
            new SpeakerTimer.OvertimeAlert(5, 2)
    };

    @Override
    public void onCreate() {
        super.onCreate();

        tickTimer = new Timer();

        intent = new Intent(BROADCAST_ACTION);
        TimerTask mRunnable = new TimerTask() {
            @Override
            public void run() {
                sendBroadcast(DebatingTimerService.this.intent);
            }
        };
        tickTimer.schedule(mRunnable, 0, 1000);

        debate = new Debate();
        // Set up speakers
        mSpeaker1 = new Speaker("Speaker1");
        mSpeaker2 = new Speaker("Speaker2");
        mSpeaker3 = new Speaker("Speaker3");
        mSpeaker4 = new Speaker("Speaker4");

        debate.addStage(mSpeaker1, substativeSpeechAlerts);
        debate.addStage(mSpeaker3, substativeSpeechAlerts);
        debate.addStage(mSpeaker2, substativeSpeechAlerts);
        debate.addStage(mSpeaker4, substativeSpeechAlerts);
        debate.addStage(mSpeaker3, replySpeechAlerts);
        debate.addStage(mSpeaker1, replySpeechAlerts);
    }

    public class DebatingTimerServiceBinder extends Binder
    {
        public Debate getDebate()
        {
            return debate;
        }
    }

    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    @Override
    protected void onHandleIntent(Intent intent) {

    }
}
