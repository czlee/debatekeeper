package com.ftechz.DebatingTimer;

import android.app.IntentService;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;

import java.util.Timer;
import java.util.TimerTask;

/**
 * DebatingTimerService class
 * The background service for the application
 * Keeps the debate/timers ticking in the background
 * Uses a broadcast (though not the best way IMO) to update the main UI
 */
public class DebatingTimerService extends IntentService
{
    public static final String BROADCAST_ACTION = "com.ftechz.DebatingTimer.update";
    Intent intent;

    private Timer tickTimer;
    private final IBinder mBinder = new DebatingTimerServiceBinder();

    private Debate mDebate;
    private Speaker mSpeaker1;      // Affirmative
    private Speaker mSpeaker2;
    private Speaker mSpeaker3;      // Negative
    private Speaker mSpeaker4;

    private AlarmChain.AlarmChainAlert prepAlerts[];
    private AlarmChain.AlarmChainAlert substativeSpeechAlerts[];
    private AlarmChain.AlarmChainAlert replySpeechAlerts[];

    private AlertManager mAlertManager;

    public DebatingTimerService() {
        super("DebatingTimerService");
    }

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
        tickTimer.schedule(mRunnable, 0, 200);

        mAlertManager = new AlertManager(this);

        prepAlerts = new AlarmChain.AlarmChainAlert[] {
                new SpeakerTimer.WarningAlert(2, mAlertManager),
                new SpeakerTimer.WarningAlert(4, mAlertManager),
                new SpeakerTimer.FinishAlert(7, mAlertManager)
        };

        substativeSpeechAlerts = new AlarmChain.AlarmChainAlert[] {
            new SpeakerTimer.WarningAlert(5, mAlertManager),
            new SpeakerTimer.FinishAlert(10, mAlertManager),
            new SpeakerTimer.OvertimeAlert(15, 2, mAlertManager)
        };

        replySpeechAlerts = new AlarmChain.AlarmChainAlert[] {
            new SpeakerTimer.WarningAlert(2, mAlertManager),
            new SpeakerTimer.FinishAlert(3, mAlertManager),
            new SpeakerTimer.OvertimeAlert(5, 2, mAlertManager)
        };

        mDebate = new Debate(mAlertManager);
        // Set up speakers
        mSpeaker1 = new Speaker("Speaker1");
        mSpeaker2 = new Speaker("Speaker2");
        mSpeaker3 = new Speaker("Speaker3");
        mSpeaker4 = new Speaker("Speaker4");

        mDebate.addPrep(prepAlerts);
        mDebate.addStage(mSpeaker1, substativeSpeechAlerts);
        mDebate.addStage(mSpeaker3, substativeSpeechAlerts);
        mDebate.addStage(mSpeaker2, substativeSpeechAlerts);
        mDebate.addStage(mSpeaker4, substativeSpeechAlerts);
        mDebate.addStage(mSpeaker3, replySpeechAlerts);
        mDebate.addStage(mSpeaker1, replySpeechAlerts);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        // Clean up stuff
        tickTimer.cancel();
        tickTimer = null;

        mDebate.release();
        mDebate = null;
    }

    public class DebatingTimerServiceBinder extends Binder
    {
        public Debate getDebate()
        {
            return mDebate;
        }
    }

    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    @Override
    protected void onHandleIntent(Intent intent) {

    }

}
