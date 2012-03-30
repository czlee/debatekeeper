package com.ftechz.DebatingTimer;

import android.media.*;
import android.net.Uri;
import android.os.Handler;
import android.util.Log;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.TimerTask;

import static java.util.Collections.sort;

/**
 * Created by IntelliJ IDEA.
 * User: Phil
 * Date: 3/26/12
 * Time: 12:57 AM
 * To change this template use File | Settings | File Templates.
 */
public abstract class AlarmChain extends TimerTask
{
    //
    // Classes
    public static abstract class AlarmChainAlert
    {
        public long time;
        public AlarmChainAlert(long seconds)
        {
            time = seconds;
        }
        public abstract void alert();

        public void reset()
        {
            return;
        }
    }

    public static class IntermediateAlert extends AlarmChain.AlarmChainAlert {
        public IntermediateAlert(long seconds)
        {
            super(seconds);
        }

        @Override
        public void alert()
        {
            Log.i(this.getClass().getSimpleName(), "Intermediate Alert.");
        }
    }

    public static class WarningAlert extends AlarmChain.AlarmChainAlert {
        private final double duration = 0.5; // seconds
        private final int sampleRate = 8000;
        private final int numSamples = (int)(duration * sampleRate);
        private final double sample[] = new double[numSamples];
        private final double freqOfTone = 1300; // hz
        private final byte generatedSnd[] = new byte[2 * numSamples];

        public WarningAlert(long seconds)
        {
            super(seconds);
            genTone();
        }
        void genTone(){
// fill out the array
            for (int i = 0; i < numSamples; ++i) {
                sample[i] = Math.sin(2 * Math.PI * i / (sampleRate/freqOfTone));
            }

// convert to 16 bit pcm sound array
// assumes the sample buffer is normalised.
            int idx = 0;
            for (double dVal : sample) {
                short val = (short) (dVal * 32767);
                generatedSnd[idx++] = (byte) (val & 0x00ff);
                generatedSnd[idx++] = (byte) ((val & 0xff00) >>> 8);
            }
        }

        void playSound(){
            AudioTrack audioTrack = new AudioTrack(AudioManager.STREAM_MUSIC,
                    8000, AudioFormat.CHANNEL_CONFIGURATION_MONO,
                    AudioFormat.ENCODING_PCM_16BIT, numSamples,
                    AudioTrack.MODE_STATIC);
            audioTrack.write(generatedSnd, 0, numSamples);
            audioTrack.play();
        }

        Handler handler = new Handler();
        @Override
        public void alert()
        {
            Log.i(this.getClass().getSimpleName(), "Warning Alert.");

            Thread thread = new Thread(new Runnable() {
                public void run() {
                    handler.post(new Runnable() {

                        public void run() {
                            playSound();
                        }
                    });
                }
            });
            thread.start();
            // Do an do alert
        }
    }

    public static class FinishAlert extends AlarmChain.AlarmChainAlert {
        public FinishAlert(long seconds)
        {
            super(seconds);
        }

        @Override
        public void alert()
        {
            Log.i(this.getClass().getSimpleName(), "Finish.");
            // Do an do-do alert

        }
    }

    public static class OvertimeAlert extends AlarmChain.AlarmChainAlert {
        private long mRepeatPeriod = 0;
        private long initTime;

        public OvertimeAlert(long seconds, long repeatPeriod)
        {
            super(seconds);
            initTime = seconds;
            mRepeatPeriod = repeatPeriod;
        }

        @Override
        public void alert()
        {
            time += mRepeatPeriod;
            Log.i(this.getClass().getSimpleName(), "OVERTIME!");
            // Do an do-do-do alert
        }

        @Override
        public void reset() {
            time = initTime;
        }
    }

    public class AlarmChainAlertCompare implements Comparator<AlarmChainAlert> {
        @Override
        public int compare(AlarmChainAlert alert1, AlarmChainAlert alert2) {
            return (int)(alert1.time - alert2.time);
        }
    }

    //
    // Members
    private long mSecondCounter;
    private ArrayList<AlarmChainAlert> mAlerts;

    private int state;
    private AlarmChainAlertCompare alertComparator = new AlarmChainAlertCompare();

    //
    // Methods
    private void init() {
        mAlerts = new ArrayList<AlarmChainAlert>();
        state = 0;
        mSecondCounter = 0;
    }

    public AlarmChain() {
        super();
        init();
    }

    public AlarmChain(AlarmChainAlert[] alerts)
    {
        super();
        init();
        for(AlarmChainAlert alert : alerts){
            mAlerts.add(alert);
        }
    }

    // Assumed to execute every second
    // Increments counter and checks for alert times
    @Override
    public void run() {
        mSecondCounter++;
        if(state < mAlerts.size())
        {
            if(mSecondCounter == mAlerts.get(state).time)
            {
                do {
                    handleAlert(mAlerts.get(state));
                    if(state < mAlerts.size() - 1)
                    {
                        state++;
                    }
                    else
                    {
                        break;
                    }
                } while(mSecondCounter == mAlerts.get(state).time); // Handle multiple with the same time
            }
            else if(mSecondCounter > mAlerts.get(state).time)
            {
                if(state < mAlerts.size() - 1)
                {
                    state++;
                }
            }
        }
    }

    public void addTime(AlarmChainAlert alert) {
        mAlerts.add(alert);
        sort(mAlerts, alertComparator);
    }

    public long getSeconds() {
        return mSecondCounter;
    }

    public long getNextTime()
    {
        if(state < mAlerts.size()) {
            return mAlerts.get(state).time;
        } else {
            return 0;
        }
    }

    public long getFinalTime() {
        if(mAlerts.size() > 0) {
            return mAlerts.get(mAlerts.size()-1).time;
        } else {
            return 0;
        }
    }

    protected abstract void handleAlert(AlarmChainAlert alert);

    public abstract String getStateText();
    
    public void resetState()
    {
        state = 0;
        for(AlarmChainAlert alert : mAlerts){
            alert.reset();
        }
    }
}