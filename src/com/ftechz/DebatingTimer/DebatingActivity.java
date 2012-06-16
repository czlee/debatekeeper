package com.ftechz.DebatingTimer;

import java.io.IOException;
import java.io.InputStream;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.ftechz.DebatingTimer.DebateFormatBuilder.DebateFormatBuilderException;
import com.ftechz.DebatingTimer.SpeechFormat.CountDirection;


/**
 * This is the main activity for the Debating Timer application.  It is the launcher activity,
 * and the activity in which the user spends the most time.
 *
 * @author Phillip Cao
 * @author Chuan-Zheng Lee
 * @since  2012-03
 *
 */
public class DebatingActivity extends Activity {

	private TextView mStateText;
	private TextView mStageText;
	private TextView mCurrentTimeText;
	private TextView mNextTimeText;
	private TextView mFinalTimeText;

	// The buttons are allocated as follows:
	// When at startOfSpeaker: [Start] [Next Speaker]
	// When running:           [Stop]
	// When stopped by user:   [Resume] [Restart] [Next Speaker]
	// When stopped by alarm:  [Resume]
	// The [Bell] button always is on the right of any of the above three buttons.
	private Button mLeftControlButton;
	private Button mCentreControlButton;
	private Button mRightControlButton;
	private Button mPlayBellButton;

	private DebateManager mDebateManager;
	private Bundle mLastStateBundle;

	// TODO This is a temporary mechanism to switch between real-world and test modes
	// (It just changes the speech times.)
	private int mTestMode = 0;
	private final int NUM_TEST_MODES = 8;

    private final String BUNDLE_SUFFIX_DEBATE_MANAGER = "dm";

    // Second tick broadcast
    private final BroadcastReceiver mGuiUpdateBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            updateGui();
        }
    };

    private DebatingTimerService.DebatingTimerServiceBinder mBinder;

    private final ServiceConnection mConnection = new DebatingTimerServiceConnection();

    //******************************************************************************************
    // Public methods
    //******************************************************************************************

    /**
     * Defines call-backs for service binding, passed to bindService()
     */
    private class DebatingTimerServiceConnection implements ServiceConnection {

        @Override
        public void onServiceConnected(ComponentName className, IBinder service) {

            mBinder = (DebatingTimerService.DebatingTimerServiceBinder) service;

            mDebateManager = mBinder.getDebateManager();
            if (mDebateManager == null) {
                // DebateFormat df = buildDefaultDebate(mTestMode);
                DebateFormat df = buildDebateFromXml(mTestMode);
                if (df == null) {
                    DebatingActivity.this.finish();
                }
                mDebateManager = mBinder.createDebateManager(df);

                // We only restore the state if there wasn't an existing debate, i.e.
                // if the service wasn't already running.  Also, only do this once (so set it
                // to null once restored).
                if (mLastStateBundle != null) {
                    mDebateManager.restoreState(BUNDLE_SUFFIX_DEBATE_MANAGER, mLastStateBundle);
                    mLastStateBundle = null;
                }
            }

            applyPreferences();

            updateGui();

        }

        @Override
        public void onServiceDisconnected(ComponentName arg0) {
            mDebateManager = null;
        }
    };

    private class LeftControlButtonOnClickListener implements View.OnClickListener {
        @Override
        public void onClick(View pV) {
            switch (mDebateManager.getStatus()) {
            case RUNNING:
                mDebateManager.stopTimer();
                break;
            case NOT_STARTED:
            case STOPPED_BY_BELL:
            case STOPPED_BY_USER:
                mDebateManager.startTimer();
                break;
            default:
                break;
            }
            updateGui();
        }
    }

    private class CentreControlButtonOnClickListener implements View.OnClickListener {
        @Override
        public void onClick(View pV) {
            switch (mDebateManager.getStatus()) {
            case STOPPED_BY_USER:
                mDebateManager.resetSpeaker();
                break;
            default:
                break;
            }
            updateGui();
        }
    }

    private class RightControlButtonOnClickListener implements View.OnClickListener {
        @Override
        public void onClick(View pV) {
            switch (mDebateManager.getStatus()) {
            case NOT_STARTED:
            case STOPPED_BY_USER:
                if (!mDebateManager.isLastSpeaker())
                    mDebateManager.nextSpeaker();
                break;
            default:
                break;
            }
            updateGui();
        }
    }

    private class PlayBellButtonOnClickListener implements View.OnClickListener {
        @Override
        public void onClick(View v) {
            mBinder.getAlertManager().playBell();
        }
    }

    //******************************************************************************************
    // Public methods
    //******************************************************************************************

    /* (non-Javadoc)
     * @see android.app.Activity#onCreate(android.os.Bundle)
     */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.debate_activity);

        mStateText           = (TextView) findViewById(R.id.stateText);
        mStageText           = (TextView) findViewById(R.id.titleText);
        mCurrentTimeText     = (TextView) findViewById(R.id.currentTime);
        mNextTimeText        = (TextView) findViewById(R.id.nextTime);
        mFinalTimeText       = (TextView) findViewById(R.id.finalTime);
        mLeftControlButton   = (Button)   findViewById(R.id.leftControlButton);
        mCentreControlButton = (Button)   findViewById(R.id.centreControlButton);
        mRightControlButton  = (Button)   findViewById(R.id.rightControlButton);
        mPlayBellButton      = (Button)   findViewById(R.id.playBellButton);

        //
        // OnClickListeners
        mLeftControlButton  .setOnClickListener(new LeftControlButtonOnClickListener());
        mCentreControlButton.setOnClickListener(new CentreControlButtonOnClickListener());
        mRightControlButton .setOnClickListener(new RightControlButtonOnClickListener());
        mPlayBellButton     .setOnClickListener(new PlayBellButtonOnClickListener());

        mLastStateBundle = savedInstanceState; // This could be null

        if (savedInstanceState != null)
            mTestMode = savedInstanceState.getInt("testMode", 0);

        Intent intent = new Intent(this, DebatingTimerService.class);
        startService(intent);
        bindService(intent, mConnection, Context.BIND_AUTO_CREATE);
    }

    @Override
    public void onResume() {
        super.onResume();

        LocalBroadcastManager.getInstance(this).registerReceiver(mGuiUpdateBroadcastReceiver,
                new IntentFilter(DebatingTimerService.UPDATE_GUI_BROADCAST_ACTION));

        if (!applyPreferences())
            Log.w(this.getClass().getSimpleName(), "onResume: Couldn't restore preferences; mDebateManager doesn't yet exist");
    }

    @Override
    public void onPause() {
        super.onPause();
        LocalBroadcastManager.getInstance(this).unregisterReceiver(mGuiUpdateBroadcastReceiver);
    }

    @Override
    public void onSaveInstanceState(Bundle bundle) {
        if (mDebateManager != null)
            mDebateManager.saveState(BUNDLE_SUFFIX_DEBATE_MANAGER, bundle);
        bundle.putInt("testMode", mTestMode);
    }

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
	    MenuInflater inflater = getMenuInflater();
	    inflater.inflate(R.menu.debating_activity_menu, menu);
	    return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
	    switch (item.getItemId()) {
	    case R.id.prevSpeaker:
	        mDebateManager.previousSpeaker();
	        return true;
	    case R.id.switchMode:
            mTestMode = mTestMode + 1;
            if (mTestMode == NUM_TEST_MODES) mTestMode = 0;
            // keep going
	    case R.id.restartDebate:
	        mDebateManager.release();
	        mDebateManager = null;
	        mDebateManager = mBinder.createDebateManager(buildDebateFromXml(mTestMode));
	        updateGui();
	        return true;
	    case R.id.settings:
	        startActivity(new Intent(this, GlobalSettingsActivity.class));
	        return true;
        default:
            return super.onOptionsItemSelected(item);
	    }
	}

	@Override
    public boolean onPrepareOptionsMenu(Menu menu) {

	    MenuItem prevSpeakerItem = menu.findItem(R.id.prevSpeaker);
	    prevSpeakerItem.setEnabled(!mDebateManager.isFirstSpeaker());

        return super.onPrepareOptionsMenu(menu);
    }

    // Updates the buttons according to the current status of the debate
	private void updateButtons() {
	    // If it's the last speaker, don't show a "next speaker" button.
	    // Show a "restart debate" button instead.
	    switch (mDebateManager.getStatus()) {
		case NOT_STARTED:
		    setButtons(R.string.startTimer, R.string.nullButtonText, R.string.nextSpeaker);
			break;
		case RUNNING:
		    setButtons(R.string.stopTimer, R.string.nullButtonText, R.string.nullButtonText);
			break;
		case STOPPED_BY_BELL:
		    setButtons(R.string.resumeTimerAfterAlarm, R.string.nullButtonText, R.string.nullButtonText);
			break;
		case STOPPED_BY_USER:
		    setButtons(R.string.resumeTimerAfterUserStop, R.string.resetTimer, R.string.nextSpeaker);
			break;
		default:
			break;
		}

        // Hide the [Next Speaker] button if there are no more speakers
        mRightControlButton.setEnabled(!mDebateManager.isLastSpeaker());


	    // Show or hide the [Bell] button
	    mPlayBellButton.setVisibility((mBinder.getAlertManager().isSilentMode()) ? View.GONE : View.VISIBLE);
	}

    public void updateGui() {
        if (mDebateManager != null) {
            SpeechFormat currentSpeechFormat = mDebateManager.getCurrentSpeechFormat();
            PeriodInfo currentPeriodInfo = mDebateManager.getCurrentPeriodInfo();

            mStateText.setText(currentPeriodInfo.getDescription());
            mStageText.setText(mDebateManager.getCurrentSpeechName());
            mStateText.setBackgroundColor(currentPeriodInfo.getBackgroundColor());
            mStageText.setBackgroundColor(currentPeriodInfo.getBackgroundColor());

            long currentSpeechTime = mDebateManager.getCurrentSpeechTime();
            Long nextBellTime = mDebateManager.getNextBellTime();

            if (currentSpeechFormat.getCountDirection() == CountDirection.COUNT_DOWN) {
                currentSpeechTime = currentSpeechFormat.getSpeechLength() - currentSpeechTime;
                if (nextBellTime != null)
                    nextBellTime = currentSpeechFormat.getSpeechLength() - nextBellTime;
            }

            mCurrentTimeText.setText(secsToText(currentSpeechTime));

            if (nextBellTime != null) {
                mNextTimeText.setText(String.format(
                    this.getString(R.string.nextBell),
                    secsToText(nextBellTime)
                ));
            } else {
                mNextTimeText.setText(this.getString(R.string.noMoreBells));
            }
            mFinalTimeText.setText(String.format(
                this.getString(R.string.speechLength),
                secsToText(currentSpeechFormat.getSpeechLength())
            ));

            updateButtons();

            this.setTitle(getString(R.string.titleBarWithFormatName, mDebateManager.getDebateFormatName()));
        }
    }

    public boolean applyPreferences() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        if (mDebateManager != null) {
            try {
                mBinder.getAlertManager().setSilentMode(prefs.getBoolean("silentMode", false));
                mBinder.getAlertManager().setVibrateMode(prefs.getBoolean("vibrateMode", false));
                mDebateManager.setOvertimeBells(
                        prefs.getInt("firstOvertimeBell", 0),
                        prefs.getInt("overtimeBellPeriod", 0));
            } catch (ClassCastException e) {
                Log.e(this.getClass().getSimpleName(), "applyPreferences: caught ClassCastException!");
                return false;
            }
            Log.v(this.getClass().getSimpleName(), "applyPreferences: successfully applied");
            return true;
        }
        else return false;
    }

    //******************************************************************************************
    // Protected methods
    //******************************************************************************************
    @Override
    protected void onDestroy() {
        super.onDestroy();

        unbindService(mConnection);

        boolean keepRunning = false;
        if (mDebateManager != null) {
            if (mDebateManager.isRunning()) {
                keepRunning = true;
            }
        }
        if (!keepRunning) {
            Intent intent = new Intent(this, DebatingTimerService.class);
            stopService(intent);
            Log.i(this.getClass().getSimpleName(), "Timer is not running, stopped service");
        } else {
            Log.i(this.getClass().getSimpleName(), "Timer is running, keeping service alive");
        }
    }


    //******************************************************************************************
    // Private methods
    //******************************************************************************************

	// Sets the text, visibility and "weight" of all buttons
	private void setButtons(int leftResid, int centreResid, int rightResid) {
	    setButton(mLeftControlButton, leftResid);
	    setButton(mCentreControlButton, centreResid);
	    setButton(mRightControlButton, rightResid);

	    // If there are exactly two buttons, make the weight of the left button double,
	    // so that it fills two-thirds of the width of the screen.
	    float leftControlButtonWeight = (float) ((centreResid == R.string.nullButtonText && rightResid != R.string.nullButtonText) ? 2.0 : 1.0);
	    mLeftControlButton.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, leftControlButtonWeight));
	}

	// Sets the text and visibility of a single button
	private void setButton(Button button, int resid) {
	    button.setText(resid);
	    int visibility = (resid == R.string.nullButtonText) ? View.GONE : View.VISIBLE;
	    button.setVisibility(visibility);
	}

	private static String secsToText(long time) {
	    if (time >= 0) {
	        return String.format("%02d:%02d", time / 60, time % 60);
	    } else {
	        return String.format("%02d:%02d over", -time / 60, -time % 60);
	    }
	}

	public DebateFormat buildDebateFromXml(int testMode) {
	    DebateFormatBuilderFromXml dfbfx = new DebateFormatBuilderFromXml(this);
	    String filename;
	    InputStream is = null;

	    switch (testMode) {
	    case 1: filename = "test.xml"; break;
        case 2: filename = "test2.xml"; break;
        case 3: filename = "bp.xml"; break;
        case 4: filename = "nzeasters.xml"; break;
        case 5: filename = "joyntscroll.xml"; break;
        case 6: filename = "thropy.xml"; break;
        case 7: filename = "thropybreak.xml"; break;
       default: filename = "australs.xml";
	    }

	    Log.v(this.getClass().getSimpleName(), String.format("Using file %s", filename));

	    try {
            is = getAssets().open(filename);
        } catch (IOException e) {
            Log.e(this.getClass().getSimpleName(),
                    String.format("Could not find file %s", filename));
            e.printStackTrace();
            return null;
        }

	    return dfbfx.buildDebateFromXml(is);
	}

	public DebateFormat buildDefaultDebate(int testMode) {
	    DebateFormatBuilder dfb = new DebateFormatBuilder(this);

	    try {

            dfb.addNewResource("#all");
            dfb.addPeriodInfoToResource("#all", "initial", new PeriodInfo("Initial", null));
            dfb.addPeriodInfoToResource("#all", "pois-allowed", new PeriodInfo("POIs allowed", 0x7700ff00));
            dfb.addPeriodInfoToResource("#all", "warning", new PeriodInfo("Warning bell rung", 0x77ffcc00));
            dfb.addPeriodInfoToResource("#all", "overtime", new PeriodInfo("Overtime", 0x77ff0000));

            switch (testMode) {
            case 4: // Short Australs
                dfb.addNewSpeechFormat("substantive", 6*60);
                dfb.addBellInfoToSpeechFormat("substantive", new BellInfo(4*60, 1), "warning");
                dfb.addBellInfoToSpeechFormat("substantive", new BellInfo(6*60, 2), "overtime");

                dfb.addNewSpeechFormat("reply", 3*60);
                dfb.addBellInfoToSpeechFormat("reply", new BellInfo(2*60, 1), "warning");
                dfb.addBellInfoToSpeechFormat("reply", new BellInfo(3*60, 2), "overtime");
                break;
            case 3: // Thropy
                dfb.addNewSpeechFormat("substantive", 8*60);
                dfb.addBellInfoToSpeechFormat("substantive", new BellInfo(6*60, 1), "warning");
                dfb.addBellInfoToSpeechFormat("substantive", new BellInfo(8*60, 2), "overtime");

                dfb.addNewSpeechFormat("reply", 4*60);
                dfb.addBellInfoToSpeechFormat("reply", new BellInfo(3*60, 1), "warning");
                dfb.addBellInfoToSpeechFormat("reply", new BellInfo(4*60, 2), "overtime");
                break;
            case 2: // Premier B
                dfb.addNewSpeechFormat("substantive", 6*60);
                dfb.addBellInfoToSpeechFormat("substantive", new BellInfo(1*60, 1), "pois-allowed");
                dfb.addBellInfoToSpeechFormat("substantive", new BellInfo(5*60, 1), "warning");
                dfb.addBellInfoToSpeechFormat("substantive", new BellInfo(6*60, 2), "overtime");

                dfb.addNewSpeechFormat("reply", 3*60);
                dfb.addBellInfoToSpeechFormat("reply", new BellInfo(2*60, 1), "warning");
                dfb.addBellInfoToSpeechFormat("reply", new BellInfo(3*60, 2), "overtime");
                break;
            case 1: // Australs
                dfb.addNewSpeechFormat("substantive", 8*60);
                dfb.addBellInfoToSpeechFormat("substantive", new BellInfo(1*60, 1), "pois-allowed");
                dfb.addBellInfoToSpeechFormat("substantive", new BellInfo(7*60, 1), "warning");
                dfb.addBellInfoToSpeechFormat("substantive", new BellInfo(8*60, 2), "overtime");

                dfb.addNewSpeechFormat("reply", 4*60);
                dfb.addBellInfoToSpeechFormat("reply", new BellInfo(3*60, 1), "warning");
                dfb.addBellInfoToSpeechFormat("reply", new BellInfo(4*60, 2), "overtime");
                break;
            case 0: // Test mode
            default:
                dfb.addNewSpeechFormat("substantive", 20);
                dfb.addBellInfoToSpeechFormat("substantive", new BellInfo(5, 1), "pois-allowed");
                dfb.addBellInfoToSpeechFormat("substantive", new BellInfo(15, 1), "warning");
                dfb.addBellInfoToSpeechFormat("substantive", new BellInfo(20, 2), "overtime");

                dfb.addNewSpeechFormat("reply", 10);
                dfb.addBellInfoToSpeechFormat("reply", new BellInfo(5, 1), "warning");
                dfb.addBellInfoToSpeechFormat("reply", new BellInfo(10, 2), "overtime");
                break;
            }

            dfb.addSpeech("1st Affirmative", "substantive");
            dfb.addSpeech("1st Negative",    "substantive");
            dfb.addSpeech("2nd Affirmative", "substantive");
            dfb.addSpeech("2nd Negative",    "substantive");
            dfb.addSpeech("3rd Affirmative", "substantive");
            dfb.addSpeech("3rd Negative",    "substantive");
            dfb.addSpeech("Negative Leader's Reply",    "reply");
            dfb.addSpeech("Affirmative Leader's Reply", "reply");

	    } catch (DebateFormatBuilderException e) {
            Log.e(this.getClass().getSimpleName(), "Problem building debate", e);
            return null;
        }

	    return dfb.getDebateFormat();
	}
}
