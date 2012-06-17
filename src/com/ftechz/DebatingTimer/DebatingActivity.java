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
import android.content.SharedPreferences.Editor;
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

	private String mFormatXmlFileName = null;

    private static final String BUNDLE_SUFFIX_DEBATE_MANAGER = "dm";
    private static final String PREFERENCE_XML_FILE_NAME = "xmlfn";
    private static final int    CHOOSE_STYLE_REQUEST = 0;

    private final BroadcastReceiver mGuiUpdateBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            updateGui();
        }
    };

    private DebatingTimerService.DebatingTimerServiceBinder mBinder;

    private final ServiceConnection mConnection = new DebatingTimerServiceConnection();

    //******************************************************************************************
    // Private classes
    //******************************************************************************************

    /**
     * Defines call-backs for service binding, passed to bindService()
     */
    private class DebatingTimerServiceConnection implements ServiceConnection {

        @Override
        public void onServiceConnected(ComponentName className, IBinder service) {
            mBinder = (DebatingTimerService.DebatingTimerServiceBinder) service;
            initialiseDebate();
        }

        @Override
        public void onServiceDisconnected(ComponentName arg0) {
            mDebateManager = null;
        }
    };

    private class LeftControlButtonOnClickListener implements View.OnClickListener {
        @Override
        public void onClick(View pV) {
            if (mDebateManager == null) return;
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
            if (mDebateManager == null) return;
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
            if (mDebateManager == null) return;
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
    // Public and protected methods
    //******************************************************************************************

    @Override
    protected void onCreate(Bundle savedInstanceState) {
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

        //
        // Find the style file name
        String filename = loadXmlFileName();

        // If there doesn't appear to be an existing style selected, then start
        // the Activity to select the style immediately, and don't bother with the
        // rest.
        if (filename == null) {
            Intent getStyleIntent = new Intent(DebatingActivity.this, StylesChooserActivity.class);
            startActivityForResult(getStyleIntent, CHOOSE_STYLE_REQUEST);
        }

        //
        // Start the timer service
        Intent intent = new Intent(this, DebatingTimerService.class);
        startService(intent);
        bindService(intent, mConnection, Context.BIND_AUTO_CREATE);

    }

    @Override
    protected void onResume() {
        super.onResume();

        LocalBroadcastManager.getInstance(this).registerReceiver(mGuiUpdateBroadcastReceiver,
                new IntentFilter(DebatingTimerService.UPDATE_GUI_BROADCAST_ACTION));

        if (!applyPreferences())
            Log.w(this.getClass().getSimpleName(), "onResume: Couldn't restore preferences; mDebateManager doesn't yet exist");

        updateGui();
    }

    @Override
    protected void onPause() {
        super.onPause();
        LocalBroadcastManager.getInstance(this).unregisterReceiver(mGuiUpdateBroadcastReceiver);
    }

    @Override
    protected void onSaveInstanceState(Bundle bundle) {
        if (mDebateManager != null)
            mDebateManager.saveState(BUNDLE_SUFFIX_DEBATE_MANAGER, bundle);
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
            if (mDebateManager == null) return true;
	        mDebateManager.previousSpeaker();
            updateGui();
	        return true;
	    case R.id.chooseFormat:
	        Intent getStyleIntent = new Intent(this, StylesChooserActivity.class);
	        startActivityForResult(getStyleIntent, CHOOSE_STYLE_REQUEST);
	        return true;
	    case R.id.resetDebate:
            if (mDebateManager == null) return true;
	        resetDebate();
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
	    MenuItem resetDebateItem = menu.findItem(R.id.resetDebate);

	    if (mDebateManager != null) {
	        prevSpeakerItem.setEnabled(!mDebateManager.isFirstSpeaker());
            resetDebateItem.setEnabled(true);
	    } else {
	        prevSpeakerItem.setEnabled(false);
	        resetDebateItem.setEnabled(false);
	    }

        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == CHOOSE_STYLE_REQUEST && resultCode == RESULT_OK) {
            String filename = data.getStringExtra(StylesChooserActivity.EXTRA_XML_FILE_NAME);
            if (filename != null) {
                Log.v(this.getClass().getSimpleName(), String.format("Got file name %s", filename));
                setXmlFileName(filename);
                resetDebate();
            }
            // Do nothing if cancelled or error.
        }

    }

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

    private void initialiseDebate() {
        if (mFormatXmlFileName == null) {
            Log.w(this.getClass().getSimpleName(), "Tried to initialise debate with null file");
            return;
        }

        mDebateManager = mBinder.getDebateManager();
        if (mDebateManager == null) {
            DebateFormat df = buildDebateFromXml(mFormatXmlFileName);
            if (df == null) {
                // TODO Handle this error properly with a dialog
                Log.e(this.getClass().getSimpleName(), "DebateFormat was null");
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

    private void resetDebate() {
        mBinder.releaseDebateManager();
        initialiseDebate();
    }

    private String loadXmlFileName() {
        SharedPreferences sp = getPreferences(MODE_PRIVATE);
        String filename = sp.getString(PREFERENCE_XML_FILE_NAME, null);
        mFormatXmlFileName = filename;
        return filename;
    }

    private void setXmlFileName(String filename) {
        mFormatXmlFileName = filename;
        SharedPreferences sp = getPreferences(MODE_PRIVATE);
        Editor editor = sp.edit();
        editor.putString(PREFERENCE_XML_FILE_NAME, filename);
        editor.commit();
    }

    private void updateGui() {
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
                    this.getString(R.string.nextBellText),
                    secsToText(nextBellTime)
                ));
            } else {
                mNextTimeText.setText(this.getString(R.string.noMoreBells));
            }
            mFinalTimeText.setText(String.format(
                this.getString(R.string.speechLengthText),
                secsToText(currentSpeechFormat.getSpeechLength())
            ));

            updateButtons();

            this.setTitle(getString(R.string.titleBarWithFormatName, mDebateManager.getDebateFormatName()));
        } else {
            // If no debate is loaded, disable the control buttons
            // (Keep the play bell button enabled.)
            mLeftControlButton.setEnabled(false);
            mCentreControlButton.setEnabled(false);
            mRightControlButton.setEnabled(false);
        }
    }

    private boolean applyPreferences() {
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

    // Updates the buttons according to the current status of the debate
    private void updateButtons() {
        // If it's the last speaker, don't show a "next speaker" button.
        // Show a "restart debate" button instead.
        switch (mDebateManager.getStatus()) {
        case NOT_STARTED:
            setButtons(R.string.startTimerButton, R.string.nullButtonText, R.string.nextSpeakerButton);
            break;
        case RUNNING:
            setButtons(R.string.stopTimerButton, R.string.nullButtonText, R.string.nullButtonText);
            break;
        case STOPPED_BY_BELL:
            setButtons(R.string.resumeTimerAfterAlarmButton, R.string.nullButtonText, R.string.nullButtonText);
            break;
        case STOPPED_BY_USER:
            setButtons(R.string.resumeTimerAfterUserStopButton, R.string.resetTimerButton, R.string.nextSpeakerButton);
            break;
        default:
            break;
        }

        // Disable the [Next Speaker] button if there are no more speakers
        mLeftControlButton.setEnabled(true);
        mCentreControlButton.setEnabled(true);
        mRightControlButton.setEnabled(!mDebateManager.isLastSpeaker());


        // Show or hide the [Bell] button
        mPlayBellButton.setVisibility((mBinder.getAlertManager().isSilentMode()) ? View.GONE : View.VISIBLE);
    }

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

	private DebateFormat buildDebateFromXml(String filename) {
        DebateFormatBuilderFromXml dfbfx = new DebateFormatBuilderFromXml(this);
        InputStream is = null;

	    try {
            is = getAssets().open(filename);
        } catch (IOException e) {
            Log.e(this.getClass().getSimpleName(),
                    String.format("Could not find file %s", filename));
            e.printStackTrace();
            // TODO show a dialog
            return null;
        }

	    return dfbfx.buildDebateFromXml(is);
	}

}
