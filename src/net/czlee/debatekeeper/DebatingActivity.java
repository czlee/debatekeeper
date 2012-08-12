/*
 * Copyright (C) 2012 Phillip Cao, Chuan-Zheng Lee
 *
 * This file is part of the Debatekeeper app, which is licensed under the
 * GNU General Public Licence version 3 (GPLv3).  You can redistribute
 * and/or modify it under the terms of the GPLv3, and you must not use
 * this file except in compliance with the GPLv3.
 *
 * This app is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public Licence for more details.
 *
 * You should have received a copy of the GNU General Public Licence
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package net.czlee.debatekeeper;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Iterator;

import net.czlee.debatekeeper.SpeechFormat.CountDirection;

import org.xml.sax.SAXException;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.res.Resources;
import android.media.AudioManager;
import android.os.Bundle;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;
import android.view.GestureDetector;
import android.view.GestureDetector.SimpleOnGestureListener;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.AnimationUtils;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ViewFlipper;


/**
 * This is the main activity for the Debatekeeper application.  It is the launcher activity,
 * and the activity in which the user spends the most time.
 *
 * @author Phillip Cao
 * @author Chuan-Zheng Lee
 * @since  2012-04-05
 *
 */
public class DebatingActivity extends Activity {

    private ViewFlipper mDebateTimerViewFlipper;
    private RelativeLayout[] mDebateTimerDisplays;
    private int mCurrentDebateTimerDisplayIndex = 0;

    private Button mLeftControlButton;
    private Button mCentreControlButton;
    private Button mRightControlButton;
    private Button mPlayBellButton;

    private DebateManager mDebateManager;
    private Bundle mLastStateBundle;
    private FormatXmlFilesManager mFilesManager;

    private GestureDetector mGestureDetector;

    private String mFormatXmlFileName = null;
    private UserPreferenceCountDirection mUserCountDirection = UserPreferenceCountDirection.GENERALLY_UP;

    private static final String BUNDLE_SUFFIX_DEBATE_MANAGER           = "dm";
    private static final String PREFERENCE_XML_FILE_NAME               = "xmlfn";
    private static final String DIALOG_BUNDLE_FATAL_MESSAGE            = "fm";
    private static final String DIALOG_BUNDLE_XML_ERROR_LOG            = "xel";

    // These must match the string array R.array.PrefCountDirectionValues in preferences.xml
    private static final String USER_COUNT_DIRECTION_VALUE_ALWAYS_UP      = "alwaysUp";
    private static final String USER_COUNT_DIRECTION_VALUE_GENERALLY_UP   = "generallyUp";
    private static final String USER_COUNT_DIRECTION_VALUE_GENERALLY_DOWN = "generallyDown";
    private static final String USER_COUNT_DIRECTION_VALUE_ALWAYS_DOWN    = "alwaysDown";

    private static final int    CHOOSE_STYLE_REQUEST          = 0;
    private static final int    DIALOG_XML_FILE_FATAL         = 0;
    private static final int    DIALOG_XML_FILE_ERRORS        = 1;

    // Constants for touch gesture sensitivity
    private static final float  SWIPE_MIN_DISTANCE = 80;
    private static final float  SWIPE_MAX_OFF_PATH = 250;
    private static final float  SWIPE_THRESHOLD_VELOCITY = 200;

    private DebatingTimerService.DebatingTimerServiceBinder mBinder;
    private final BroadcastReceiver mGuiUpdateBroadcastReceiver = new GuiUpdateBroadcastReceiver();
    private final ServiceConnection mConnection = new DebatingTimerServiceConnection();

    //******************************************************************************************
    // Private classes
    //******************************************************************************************

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

    private class DebatingTimerFlashScreenListener implements FlashScreenListener {
        @Override
        public void flashScreen(boolean invert) {
            final int colour = (invert) ? 0xffffffff : 0x00000000;
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    findViewById(R.id.debateActivityRootView).setBackgroundColor(colour);
                }
            });
        }
    }

    private class DebatingTimerOnGestureListener extends SimpleOnGestureListener {

        @Override
        public boolean onDown(MotionEvent e) {
            // Ignore all touch events if no debate is loaded
            return (mDebateManager != null);
        }

        @Override
        public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {

            // The goToNextSpeech() and goToPreviousSpeech() methods check that the debate manager
            // is in a valid state, so we don't have to here.

            // If we go too far up or down, ignore as it's then not a horizontal swipe
            if (Math.abs(e1.getY() - e2.getY()) > SWIPE_MAX_OFF_PATH)
                return false;

            // If we go left or right far enough, then it's a horizontal swipe.
            // Check for the direction.
            if (Math.abs(e1.getX() - e2.getX()) > SWIPE_MIN_DISTANCE) {
                if (velocityX < -SWIPE_THRESHOLD_VELOCITY) {
                    goToNextSpeech();
                } else if (velocityX > SWIPE_THRESHOLD_VELOCITY) {
                    goToPreviousSpeech();
                } else {
                    return false;
                }
                return true;
            }
            return false;
        }

    }

    private class DebatingTimerOnTouchListener implements View.OnTouchListener {

        @Override
        public boolean onTouch(View v, MotionEvent event) {
            if (mGestureDetector != null)
                return mGestureDetector.onTouchEvent(event);
            return false;
        }

    }

    /**
     * Defines call-backs for service binding, passed to bindService()
     */
    private class DebatingTimerServiceConnection implements ServiceConnection {

        @Override
        public void onServiceConnected(ComponentName className, IBinder service) {
            mBinder = (DebatingTimerService.DebatingTimerServiceBinder) service;
            initialiseDebate();
            restoreBinder();
        }

        @Override
        public void onServiceDisconnected(ComponentName arg0) {
            mDebateManager = null;
        }
    };

    private class FatalXmlError extends Exception {

        private static final long serialVersionUID = -1774973645180296278L;

        public FatalXmlError(String detailMessage, Throwable throwable) {
            super(detailMessage, throwable);
        }
    }

    private final class GuiUpdateBroadcastReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            updateGui();
        }
    }

    private class LeftControlButtonOnClickListener implements View.OnClickListener {
        @Override
        public void onClick(View pV) {
            if (mDebateManager == null) {
                Intent intent = new Intent(DebatingActivity.this, FormatChooserActivity.class);
                startActivityForResult(intent, CHOOSE_STYLE_REQUEST);
                return;
            }
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

    private class PlayBellButtonOnClickListener implements View.OnClickListener {
        @Override
        public void onClick(View v) {
            mBinder.getAlertManager().playSingleBell();
        }
    }

    private class RightControlButtonOnClickListener implements View.OnClickListener {
        @Override
        public void onClick(View pV) {
            if (mDebateManager == null) return;
            switch (mDebateManager.getStatus()) {
            case NOT_STARTED:
            case STOPPED_BY_USER:
                goToNextSpeech();
                break;
            default:
                break;
            }
            updateGui();
        }
    }

    private enum OverallCountDirection {
        COUNT_UP, COUNT_DOWN
    }

    private enum UserPreferenceCountDirection {
        ALWAYS_UP, GENERALLY_UP, GENERALLY_DOWN, ALWAYS_DOWN
    }

    //******************************************************************************************
    // Public and protected methods
    //******************************************************************************************

    @Override
    public void onBackPressed() {

        // If no debate is loaded, exit.
        if (mDebateManager == null) {
            super.onBackPressed();
            return;
        }

        // If the timer is stopped AND it's not the first speaker, go back one speaker.
        // Note: We do not just leave this check to goToPreviousSpeaker(), because we want to do
        // other things if it's not in a state in which it could go to the previous speaker.
        if (!mDebateManager.isFirstSpeech() && !mDebateManager.isRunning()) {
            goToPreviousSpeech();
            return;

        // Otherwise, behave normally (i.e. exit).
        // Note that if the timer is running, the service will remain present in the
        // background, so this doesn't stop a running timer.
        } else {
            super.onBackPressed();
            return;
        }
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
            goToPreviousSpeech();
            return true;
        case R.id.chooseFormat:
            Intent getStyleIntent = new Intent(this, FormatChooserActivity.class);
            getStyleIntent.putExtra(FormatChooserActivity.EXTRA_XML_FILE_NAME, mFormatXmlFileName);
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
            prevSpeakerItem.setEnabled(!mDebateManager.isFirstSpeech() && !mDebateManager.isRunning());
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
            String filename = data.getStringExtra(FormatChooserActivity.EXTRA_XML_FILE_NAME);
            if (filename != null) {
                Log.v(this.getClass().getSimpleName(), String.format("Got file name %s", filename));
                setXmlFileName(filename);
                resetDebateWithoutToast();
            }
            // Do nothing if cancelled or error.
        }

    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.debate_activity);

        mFilesManager = new FormatXmlFilesManager(this);

        mDebateTimerViewFlipper    = (ViewFlipper)    findViewById(R.id.debateTimerDisplayFlipper);
        mDebateTimerDisplays       = new RelativeLayout[2];
        mDebateTimerDisplays[0]    = (RelativeLayout) findViewById(R.id.debateTimerDisplay0);
        mDebateTimerDisplays[1]    = (RelativeLayout) findViewById(R.id.debateTimerDisplay1);

        mDebateTimerViewFlipper.setDisplayedChild(mCurrentDebateTimerDisplayIndex);

        mLeftControlButton   = (Button) findViewById(R.id.leftControlButton);
        mCentreControlButton = (Button) findViewById(R.id.centreControlButton);
        mRightControlButton  = (Button) findViewById(R.id.rightControlButton);
        mPlayBellButton      = (Button) findViewById(R.id.playBellButton);

        //
        // OnClickListeners
        mLeftControlButton  .setOnClickListener(new LeftControlButtonOnClickListener());
        mCentreControlButton.setOnClickListener(new CentreControlButtonOnClickListener());
        mRightControlButton .setOnClickListener(new RightControlButtonOnClickListener());
        mPlayBellButton     .setOnClickListener(new PlayBellButtonOnClickListener());

        mLastStateBundle = savedInstanceState; // This could be null

        //
        // OnTouchListener
        mGestureDetector = new GestureDetector(new DebatingTimerOnGestureListener());
        View displayFlipper = findViewById(R.id.debateTimerDisplayFlipper);
        displayFlipper.setOnTouchListener(new DebatingTimerOnTouchListener());

        //
        // Find the style file name
        String filename = loadXmlFileName();

        // If there doesn't appear to be an existing style selected, then start
        // the Activity to select the style immediately, and don't bother with the
        // rest.
        if (filename == null) {
            Intent getStyleIntent = new Intent(DebatingActivity.this, FormatChooserActivity.class);
            startActivityForResult(getStyleIntent, CHOOSE_STYLE_REQUEST);
        }

        //
        // Start the timer service
        Intent intent = new Intent(this, DebatingTimerService.class);
        startService(intent);
        bindService(intent, mConnection, Context.BIND_AUTO_CREATE);
    }

    @Override
    protected Dialog onCreateDialog(int id, Bundle bundle) {
        switch (id) {
        case DIALOG_XML_FILE_FATAL:
            return getFatalProblemWithXmlFileDialog(bundle);
        case DIALOG_XML_FILE_ERRORS:
            return getErrorsWithXmlFileDialog(bundle);
        }
        return super.onCreateDialog(id);
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

    @Override
    protected void onStart() {
        // TODO Auto-generated method stub
        super.onStart();
        restoreBinder();
        LocalBroadcastManager.getInstance(this).registerReceiver(mGuiUpdateBroadcastReceiver,
                new IntentFilter(DebatingTimerService.UPDATE_GUI_BROADCAST_ACTION));

        applyPreferences();
        updateGui();
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (mBinder != null) {
            AlertManager am = mBinder.getAlertManager();
            if (am != null) {
                am.activityStop();
            }
        }
        LocalBroadcastManager.getInstance(this).unregisterReceiver(mGuiUpdateBroadcastReceiver);
    }

    @Override
    protected void onSaveInstanceState(Bundle bundle) {
        if (mDebateManager != null)
            mDebateManager.saveState(BUNDLE_SUFFIX_DEBATE_MANAGER, bundle);
    }


    //******************************************************************************************
    // Private methods
    //******************************************************************************************

    /**
     * Gets the preferences from the shared preferences file and applies them.
     */
    private void applyPreferences() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        boolean silentMode, vibrateMode, overtimeBellsEnabled, keepScreenOn, flashScreen;
        int firstOvertimeBell, overtimeBellPeriod;
        String userCountDirectionValue;

        try {
            silentMode           = prefs.getBoolean("silentMode", AlertManager.DEFAULT_SILENT_MODE);
            vibrateMode          = prefs.getBoolean("vibrateMode", AlertManager.DEFAULT_VIBRATE_MODE);
            flashScreen          = prefs.getBoolean("flashScreen", false);
            overtimeBellsEnabled = prefs.getBoolean("overtimeBellsEnable", true);
            keepScreenOn         = prefs.getBoolean("keepScreenOn", AlertManager.DEFAULT_KEEP_SCREEN_ON);
            if (overtimeBellsEnabled) {
                firstOvertimeBell  = prefs.getInt("firstOvertimeBell", 30);
                overtimeBellPeriod = prefs.getInt("overtimeBellPeriod", 30);
            } else {
                firstOvertimeBell = 0;
                overtimeBellPeriod = 0;
            }

            userCountDirectionValue = prefs.getString("countDirection", USER_COUNT_DIRECTION_VALUE_GENERALLY_UP);
            // This is like a switch statement (not supported for strings in Java 6)
            if (userCountDirectionValue.equals(USER_COUNT_DIRECTION_VALUE_ALWAYS_DOWN))
                mUserCountDirection = UserPreferenceCountDirection.ALWAYS_DOWN;
            else if (userCountDirectionValue.equals(USER_COUNT_DIRECTION_VALUE_ALWAYS_UP))
                mUserCountDirection = UserPreferenceCountDirection.ALWAYS_UP;
            else if (userCountDirectionValue.equals(USER_COUNT_DIRECTION_VALUE_GENERALLY_DOWN))
                mUserCountDirection = UserPreferenceCountDirection.GENERALLY_DOWN;
            else if (userCountDirectionValue.equals(USER_COUNT_DIRECTION_VALUE_GENERALLY_UP))
                mUserCountDirection = UserPreferenceCountDirection.GENERALLY_UP;

        } catch (ClassCastException e) {
            Log.e(this.getClass().getSimpleName(), "applyPreferences: caught ClassCastException!");
            return;
        }

        if (mDebateManager != null) {
            mDebateManager.setOvertimeBells(firstOvertimeBell, overtimeBellPeriod);
        } else {
            Log.w(this.getClass().getSimpleName(), "applyPreferences: Couldn't restore overtime bells, mDebateManager doesn't yet exist");
        }

        if (mBinder != null) {
            AlertManager am = mBinder.getAlertManager();

            // Volume control stream is linked to silent mode
            am.setSilentMode(silentMode);
            setVolumeControlStream((silentMode) ? AudioManager.STREAM_RING : AudioManager.STREAM_MUSIC);

            am.setVibrateMode(vibrateMode);
            am.setKeepScreenOn(keepScreenOn);
            am.setFlashScreenListener((flashScreen) ? new DebatingTimerFlashScreenListener() : null);
            Log.v(this.getClass().getSimpleName(), "applyPreferences: successfully applied");
        } else {
            Log.w(this.getClass().getSimpleName(), "applyPreferences: Couldn't restore AlertManager preferences; mBinder doesn't yet exist");
        }
    }

    /**
     * Builds a <code>DebateFormat</code> from a specified XML file. Shows a <code>Dialog</code> if
     * the debate format builder logged non-fatal errors.
     * @param filename the file name of the XML file
     * @return the built <code>DebateFormat</code>
     * @throws FatalXmlError if there was any problem, which could include:
     * <ul><li>A problem opening or reading the file</li>
     * <li>A problem parsing the XML file</li>
     * <li>That there were no speeches in this debate format</li>
     * </ul>
     * The message of the exception will be human-readable and can be displayed in a dialogue box.
     */
    private DebateFormat buildDebateFromXml(String filename) throws FatalXmlError {
        DebateFormatBuilderFromXml dfbfx = new DebateFormatBuilderFromXml(this);
        InputStream is = null;
        DebateFormat df;

        try {
            is = mFilesManager.open(filename);
        } catch (IOException e) {
            throw new FatalXmlError(getString(R.string.FatalProblemWithXmlFileMessage_CannotFind, filename), e);
        }

        try {
            df = dfbfx.buildDebateFromXml(is);
        } catch (IOException e) {
            throw new FatalXmlError(getString(R.string.FatalProblemWithXmlFileMessage_CannotRead, filename), e);
        } catch (SAXException e) {
            throw new FatalXmlError(getString(
                    R.string.FatalProblemWithXmlFileMessage_BadXml, filename, e.getMessage()), e);
        } catch (IllegalStateException e) {
            throw new FatalXmlError(getString(
                    R.string.FatalProblemWithXmlFileMessage_NoSpeeches, filename), e);
        }

        if (dfbfx.hasErrors()) {
            Bundle bundle = new Bundle();
            bundle.putStringArrayList(DIALOG_BUNDLE_XML_ERROR_LOG, dfbfx.getErrorLog());
            removeDialog(DIALOG_XML_FILE_ERRORS);
            showDialog(DIALOG_XML_FILE_ERRORS, bundle);
        }

        return df;
    }

    /**
     * Assembles the speech format and user count directions to find the count direction to use
     * currently.
     * @return OverallCountDirection.UP or OverallCountDirection.DOWN
     */
    private OverallCountDirection getCountDirection() {

        // If the user has specified always up or always down, that takes priority.
        if (mUserCountDirection == UserPreferenceCountDirection.ALWAYS_DOWN)
            return OverallCountDirection.COUNT_DOWN;
        if (mUserCountDirection == UserPreferenceCountDirection.ALWAYS_UP)
            return OverallCountDirection.COUNT_UP;

        // If the user hasn't specified, and the speech format has specified a count direction,
        // use the speech format suggestion.
        if (mDebateManager != null) {
            SpeechFormat currentSpeechFormat = mDebateManager.getCurrentSpeechFormat();
            CountDirection sfCountDirection = currentSpeechFormat.getCountDirection();
            if (sfCountDirection == CountDirection.COUNT_DOWN)
                return OverallCountDirection.COUNT_DOWN;
            if (sfCountDirection == CountDirection.COUNT_UP)
                return OverallCountDirection.COUNT_UP;
        }

        // Otherwise, use the user setting.
        if (mUserCountDirection == UserPreferenceCountDirection.GENERALLY_DOWN)
            return OverallCountDirection.COUNT_DOWN;
        if (mUserCountDirection == UserPreferenceCountDirection.GENERALLY_UP)
            return OverallCountDirection.COUNT_UP;

        // We've now covered all possibilities.  But just in case (and to satisfy the compiler)...
        return OverallCountDirection.COUNT_UP;

    }

    private Dialog getErrorsWithXmlFileDialog(Bundle bundle) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);

        String errorMessage = getString(R.string.ErrorsInXmlFileDialogMessagePrefix);

        ArrayList<String> errorLog = bundle.getStringArrayList(DIALOG_BUNDLE_XML_ERROR_LOG);
        Iterator<String> errorIterator = errorLog.iterator();

        while (errorIterator.hasNext()) {
            errorMessage = errorMessage.concat("\n");
            errorMessage = errorMessage.concat(errorIterator.next());
        }

        builder.setTitle(R.string.ErrorsInXmlFileDialogTitle)
               .setMessage(errorMessage)
               .setCancelable(true)
               .setPositiveButton(R.string.ErrorsInXmlFileDialogButton, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                    }
                });

        return builder.create();
    }

    private Dialog getFatalProblemWithXmlFileDialog(Bundle bundle) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);

        String errorMessage = bundle.getString(DIALOG_BUNDLE_FATAL_MESSAGE);
        errorMessage = errorMessage.concat(getString(R.string.FatalProblemWithXmlFileMessageSuffix));

        builder.setTitle(R.string.FatalProblemWithXmlFileDialogTitle)
               .setMessage(errorMessage)
               .setCancelable(true)
               .setPositiveButton(R.string.FatalProblemWithXmlFileDialogButton, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        Intent intent = new Intent(DebatingActivity.this, FormatChooserActivity.class);
                        startActivityForResult(intent, CHOOSE_STYLE_REQUEST);
                    }
                })
                .setOnCancelListener(new DialogInterface.OnCancelListener() {
                    @Override
                    public void onCancel(DialogInterface dialog) {
                        DebatingActivity.this.finish();
                    }
                });

        return builder.create();
    }

    /**
     * Goes to the next speech.
     * Does nothing if there is no debate loaded, if the current speech is the last speech or if
     * the timer is running.
     */
    private void goToNextSpeech() {

        if (mDebateManager == null) return;
        if (mDebateManager.isRunning()) return;
        if (mDebateManager.isLastSpeech()) return;

        // Swap the current display index
        mCurrentDebateTimerDisplayIndex = (mCurrentDebateTimerDisplayIndex == 1) ? 0 : 1;

        mDebateManager.goToNextSpeaker();
        updateDebateTimerDisplay(mCurrentDebateTimerDisplayIndex);
        mDebateTimerViewFlipper.setInAnimation(AnimationUtils.loadAnimation(
                DebatingActivity.this, R.anim.slide_from_right));
        mDebateTimerViewFlipper.setOutAnimation(AnimationUtils.loadAnimation(
                DebatingActivity.this, R.anim.slide_to_left));
        mDebateTimerViewFlipper.setDisplayedChild(mCurrentDebateTimerDisplayIndex);

        updateGui();

    }

    /**
     * Goes to the previous speech.
     * Does nothing if there is no debate loaded, if the current speech is the first speech or if
     * the timer is running.
     */
    private void goToPreviousSpeech() {

        if (mDebateManager == null) return;
        if (mDebateManager.isRunning()) return;
        if (mDebateManager.isFirstSpeech()) return;

        // Swap the current display index
        mCurrentDebateTimerDisplayIndex = (mCurrentDebateTimerDisplayIndex == 1) ? 0 : 1;

        mDebateManager.goToPreviousSpeaker();
        updateDebateTimerDisplay(mCurrentDebateTimerDisplayIndex);
        mDebateTimerViewFlipper.setInAnimation(AnimationUtils.loadAnimation(
                DebatingActivity.this, R.anim.slide_from_left));
        mDebateTimerViewFlipper.setOutAnimation(AnimationUtils.loadAnimation(
                DebatingActivity.this, R.anim.slide_to_right));
        mDebateTimerViewFlipper.setDisplayedChild(mCurrentDebateTimerDisplayIndex);

        updateGui();

    }

    private void initialiseDebate() {
        if (mFormatXmlFileName == null) {
            Log.w(this.getClass().getSimpleName(), "Tried to initialise debate with null file");
            return;
        }

        mDebateManager = mBinder.getDebateManager();
        if (mDebateManager == null) {

            DebateFormat df;
            try {
                df = buildDebateFromXml(mFormatXmlFileName);
            } catch (FatalXmlError e) {
                removeDialog(DIALOG_XML_FILE_FATAL);
                Bundle bundle = new Bundle();
                bundle.putString(DIALOG_BUNDLE_FATAL_MESSAGE, e.getMessage());
                showDialog(DIALOG_XML_FILE_FATAL, bundle);
                return;
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

    private String loadXmlFileName() {
        SharedPreferences sp = getPreferences(MODE_PRIVATE);
        String filename = sp.getString(PREFERENCE_XML_FILE_NAME, null);
        mFormatXmlFileName = filename;
        return filename;
    }

    private void resetDebate() {
        resetDebateWithoutToast();
        Toast.makeText(this, R.string.ResetDebateToastText, Toast.LENGTH_SHORT).show();
    }

    private void resetDebateWithoutToast() {
        if (mBinder == null) return;
        mBinder.releaseDebateManager();
        initialiseDebate();
    }

    private void restoreBinder() {
        if (mBinder != null) {
            AlertManager am = mBinder.getAlertManager();
            if (am != null) {
                am.activityStart();
            }
        }
    }

    // Sets the text and visibility of a single button
    private void setButton(Button button, int resid) {
        button.setText(resid);
        int visibility = (resid == R.string.NullButtonText) ? View.GONE : View.VISIBLE;
        button.setVisibility(visibility);
    }

    // Sets the text, visibility and "weight" of all buttons
    private void setButtons(int leftResid, int centreResid, int rightResid) {
        setButton(mLeftControlButton, leftResid);
        setButton(mCentreControlButton, centreResid);
        setButton(mRightControlButton, rightResid);

        // If there are exactly two buttons, make the weight of the left button double,
        // so that it fills two-thirds of the width of the screen.
        float leftControlButtonWeight = (float) ((centreResid == R.string.NullButtonText && rightResid != R.string.NullButtonText) ? 2.0 : 1.0);
        mLeftControlButton.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, leftControlButtonWeight));
    }

    private void setXmlFileName(String filename) {
        mFormatXmlFileName = filename;
        SharedPreferences sp = getPreferences(MODE_PRIVATE);
        Editor editor = sp.edit();
        editor.putString(PREFERENCE_XML_FILE_NAME, filename);
        editor.commit();
    }

    /**
     *  Updates the buttons according to the current status of the debate
     *  The buttons are allocated as follows:
     *  When at startOfSpeaker: [Start] [Next Speaker]
     *  When running:           [Stop]
     *  When stopped by user:   [Resume] [Restart] [Next Speaker]
     *  When stopped by alarm:  [Resume]
     *  The [Bell] button always is on the right of any of the above three buttons.
     */
    private void updateButtons() {
        if (mDebateManager != null) {

            // If it's the last speaker, don't show a "next speaker" button.
            // Show a "restart debate" button instead.
            switch (mDebateManager.getStatus()) {
            case NOT_STARTED:
                setButtons(R.string.StartTimerButtonText, R.string.NullButtonText, R.string.NextSpeakerButtonText);
                break;
            case RUNNING:
                setButtons(R.string.StopTimerButtonText, R.string.NullButtonText, R.string.NullButtonText);
                break;
            case STOPPED_BY_BELL:
                setButtons(R.string.ResumeTimerAfterAlarmButtonText, R.string.NullButtonText, R.string.NullButtonText);
                break;
            case STOPPED_BY_USER:
                setButtons(R.string.ResumeTimerAfterUserStopButtonText, R.string.ResetTimerButtonText, R.string.NextSpeakerButtonText);
                break;
            default:
                break;
            }

            // Disable the [Next Speaker] button if there are no more speakers
            mLeftControlButton.setEnabled(true);
            mCentreControlButton.setEnabled(true);
            mRightControlButton.setEnabled(!mDebateManager.isLastSpeech());

        } else {
            // If no debate is loaded, disable the control buttons
            // (Keep the play bell button enabled.)
            setButtons(R.string.NoDebateLoadedButtonText, R.string.NullButtonText, R.string.NullButtonText);
            mLeftControlButton.setEnabled(true);
            mCentreControlButton.setEnabled(false);
            mRightControlButton.setEnabled(false);
        }

        // Show or hide the [Bell] button
        updatePlayBellButton();
    }

    /**
     * Updates the debate timer display (including speech name, period name, etc.) in a given view.
     * The view should be the <code>RelativeLayout</code> in debate_timer_display.xml.
     * @param debateTimerDisplayIndex The index of the debate timer display that will be updated.
     */
    private void updateDebateTimerDisplay(int debateTimerDisplayIndex) {

        View v = mDebateTimerDisplays[debateTimerDisplayIndex];

        TextView periodDescriptionText = (TextView) v.findViewById(R.id.periodDescriptionText);
        TextView speechNameText        = (TextView) v.findViewById(R.id.speechNameText);
        TextView currentTimeText       = (TextView) v.findViewById(R.id.currentTime);
        TextView nextTimeText          = (TextView) v.findViewById(R.id.nextTime);
        TextView finalTimeText         = (TextView) v.findViewById(R.id.finalTime);

        if (mDebateManager != null) {

            SpeechFormat currentSpeechFormat = mDebateManager.getCurrentSpeechFormat();
            PeriodInfo   currentPeriodInfo   = mDebateManager.getCurrentPeriodInfo();

            speechNameText.setText(mDebateManager.getCurrentSpeechName());
            speechNameText.setBackgroundColor(currentPeriodInfo.getBackgroundColor());
            periodDescriptionText.setText(currentPeriodInfo.getDescription());
            periodDescriptionText.setBackgroundColor(currentPeriodInfo.getBackgroundColor());

            long currentSpeechTime = mDebateManager.getCurrentSpeechTime();
            Long nextBellTime = mDebateManager.getNextBellTime();
            boolean nextBellIsPause = mDebateManager.isNextBellPause();

            if (getCountDirection() == OverallCountDirection.COUNT_DOWN) {
                currentSpeechTime = currentSpeechFormat.getSpeechLength() - currentSpeechTime;
                if (nextBellTime != null)
                    nextBellTime = currentSpeechFormat.getSpeechLength() - nextBellTime;
            }

            Resources resources = getResources();
            int currentTimeTextColor;
            if (mDebateManager.isOvertime())
                currentTimeTextColor = resources.getColor(R.color.overtime);
            else
                currentTimeTextColor = resources.getColor(android.R.color.primary_text_dark);
            currentTimeText.setText(secsToText(currentSpeechTime));
            currentTimeText.setTextColor(currentTimeTextColor);

            if (nextBellTime != null) {
                if (nextBellIsPause) {
                    nextTimeText.setText(String.format(
                            this.getString(R.string.NextBellWithPauseText),
                            secsToText(nextBellTime)));
                } else {
                    nextTimeText.setText(String.format(this.getString(R.string.NextBellText),
                            secsToText(nextBellTime)));
                }
            } else {
                nextTimeText.setText(this.getString(R.string.NoMoreBellsText));
            }
            finalTimeText.setText(String.format(
                this.getString(R.string.SpeechLengthText),
                secsToText(currentSpeechFormat.getSpeechLength())
            ));

        } else {
            // Blank out all the fields
            periodDescriptionText.setText(R.string.NoDebateLoadedText);
            speechNameText.setText("");
            periodDescriptionText.setBackgroundColor(0);
            speechNameText.setBackgroundColor(0);
            currentTimeText.setText("");
            nextTimeText.setText("");
            finalTimeText.setText("");
        }

    }

    /**
     * Updates the GUI (in the general case).
     */
    private void updateGui() {
        updateDebateTimerDisplay(mCurrentDebateTimerDisplayIndex);
        updateButtons();

        if (mDebateManager != null) {
            this.setTitle(getString(R.string.DebatingActivityTitleBarWithFormatName, mDebateManager.getDebateFormatName()));
        } else {
            setTitle(R.string.DebatingActivityTitleBarWithoutFormatName);
        }

    }

    private void updatePlayBellButton() {
        if (mBinder != null)
            mPlayBellButton.setVisibility((mBinder.getAlertManager().isSilentMode()) ? View.GONE : View.VISIBLE);
    }

    private static String secsToText(long time) {
        if (time >= 0) {
            return String.format("%02d:%02d", time / 60, time % 60);
        } else {
            return String.format("%02d:%02d over", -time / 60, -time % 60);
        }
    }

}
