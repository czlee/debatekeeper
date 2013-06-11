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
import java.util.HashMap;
import java.util.Iterator;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import net.czlee.debatekeeper.AlertManager.FlashScreenListener;
import net.czlee.debatekeeper.AlertManager.FlashScreenMode;
import net.czlee.debatekeeper.EnableableViewPager.PagingEnabledIndicator;
import net.czlee.debatekeeper.debateformat.BellInfo;
import net.czlee.debatekeeper.debateformat.DebateFormat;
import net.czlee.debatekeeper.debateformat.DebateFormatBuilderFromXml;
import net.czlee.debatekeeper.debateformat.DebateFormatBuilderFromXmlForSchema1;
import net.czlee.debatekeeper.debateformat.DebateFormatBuilderFromXmlForSchema2;
import net.czlee.debatekeeper.debateformat.DebatePhaseFormat;
import net.czlee.debatekeeper.debateformat.PeriodInfo;
import net.czlee.debatekeeper.debateformat.PrepTimeFormat;
import net.czlee.debatekeeper.debateformat.SpeechFormat;
import net.czlee.debatekeeper.debatemanager.DebateManager;
import net.czlee.debatekeeper.debatemanager.DebateManager.DebatePhaseTag;

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
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.res.Resources;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v4.view.PagerAdapter;
import android.support.v4.view.ViewPager.OnPageChangeListener;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnLongClickListener;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.TimePicker;
import android.widget.Toast;


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

    private View             mDebateTimerDisplay;
    private boolean          mIsEditingTime = false;
    private final Semaphore  mFlashScreenSemaphore = new Semaphore(1, true);
    private final int        mNormalBackgroundColour = 0;

    private EnableableViewPager mViewPager;

    private Button    mLeftControlButton;
    private Button    mCentreControlButton;
    private Button    mRightControlButton;
    private Button    mPlayBellButton;

    private DebateManager         mDebateManager;
    private Bundle                mLastStateBundle;
    private FormatXmlFilesManager mFilesManager;

    private String               mFormatXmlFileName      = null;
    private CountDirection       mCountDirection         = CountDirection.COUNT_UP;
    private CountDirection       mPrepTimeCountDirection = CountDirection.COUNT_DOWN;
    private BackgroundColourArea mBackgroundColourArea   = BackgroundColourArea.WHOLE_SCREEN;
    private boolean              mPoiTimerEnabled        = true;
    private boolean              mKeepScreenOn;
    private boolean              mPrepTimeKeepScreenOn;

    private boolean              mDialogBlocking        = false;
    private boolean              mDialogWaiting         = false;
    private int                  mDialogIdWaiting       = -1;
    private Bundle               mDialogBundleInWaiting = null;

    private static final String BUNDLE_SUFFIX_DEBATE_MANAGER     = "dm";
    private static final String PREFERENCE_XML_FILE_NAME         = "xmlfn";
    private static final String DO_NOT_SHOW_POI_TIMER_DIALOG     = "dnspoi";
    private static final String DIALOG_BUNDLE_FATAL_MESSAGE      = "fm";
    private static final String DIALOG_BUNDLE_XML_ERROR_LOG      = "xel";
    private static final String DIALOG_BUNDLE_SCHEMA_USED        = "used";
    private static final String DIALOG_BUNDLE_SCHEMA_SUPPORTED   = "supp";

    private static final int    CHOOSE_STYLE_REQUEST      = 0;
    private static final int    DIALOG_XML_FILE_FATAL     = 0;
    private static final int    DIALOG_XML_FILE_ERRORS    = 1;
    private static final int    DIALOG_POI_TIMERS_INFO    = 2;
    private static final int    DIALOG_XML_SCHEMA_TOO_NEW = 3;

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
            switch (mDebateManager.getTimerStatus()) {
            case STOPPED_BY_USER:
                mDebateManager.resetActivePhase();
                break;
            default:
                break;
            }
            updateGui();
        }
    }

    private class CurrentTimeOnLongClickListener implements OnLongClickListener {

        @Override
        public boolean onLongClick(View v) {
            editCurrentTimeStart();
            return true;
        }

    }

    private class DebatingTimerFlashScreenListener implements FlashScreenListener {

        private void flashScreen(final int colour) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    findViewById(R.id.mainScreen_rootView).setBackgroundColor(colour);
                }
            });
        }

        @Override
        public boolean begin() {
            try {
                return mFlashScreenSemaphore.tryAcquire(2, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                return false; // Don't bother with the flash screen any more
            }
        }

        @Override
        public void flashScreenOn(int colour) {

            // First, if the whole screen is coloured, remove the colouring.
            // It will be restored by updateGui() in done().
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    if (mBackgroundColourArea == BackgroundColourArea.WHOLE_SCREEN) {
                        // Log.v(this.getClass().getSimpleName(), "removing background colour on " + Thread.currentThread().toString());
                        mDebateTimerDisplay.setBackgroundColor(0);
                    }
                }
            });
            flashScreen(colour);
        }

        @Override
        public void flashScreenOff() {
            flashScreen(mNormalBackgroundColour);
        }

        @Override
        public void done() {
            mFlashScreenSemaphore.release();
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    updateGui();
                }
            });
        }
    }

    private class DebateTimerDisplayOnClickListener implements OnClickListener {

        @Override
        public void onClick(View v) {
            editCurrentTimeFinish(true);
        }

    }

    private class DebateTimerDisplayOnPageChangeListener implements OnPageChangeListener {

        @Override
        public void onPageScrollStateChanged(int state) {
        }

        @Override
        public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {
        }

        @Override
        public void onPageSelected(int position) {
            mDebateManager.setCurrentPhaseIndex(position);
        }

    }

    /**
     * Implementation of {@link PagerAdapter} that pages through the various speeches of a debate
     * managed by a {@link DebateManager}.
     *
     * @author Chuan-Zheng Lee
     * @since 2013-06-10
     *
     */
    private class DebateTimerDisplayPagerAdapter extends PagerAdapter {

        private final HashMap<DebatePhaseTag, View> mViewsMap = new HashMap<DebatePhaseTag, View>();
        private static final String NO_DEBATE_LOADED = "no_debate_loaded";

        @Override
        public void destroyItem(ViewGroup container, int position, Object object) {
            View view = mViewsMap.get(object);
            if (view == null) {
                Log.e(getClass().getSimpleName(), String.format("Nothing found to destroy at position %d, %s", position, object.toString()));
                return;
            }
            Log.i(getClass().getSimpleName(), String.format("Destroying position %d, %s, %s", position, object.toString(), view.toString()));
            container.removeView(view);
            mViewsMap.remove(object);
        }

        @Override
        public int getItemPosition(Object object) {

            // If it was the "no debate loaded" screen and there is now a debate loaded,
            // then the View no longer exists.  Likewise if there is no debate loaded and
            // there was anything but the "no debate loaded" screen.
            DebatePhaseTag tag = (DebatePhaseTag) object;
            if ((mDebateManager == null) != (tag.specialTag == NO_DEBATE_LOADED))
                return POSITION_NONE;

            // If it was "no debate loaded" and there is still no debate loaded, it's unchanged.
            if (mDebateManager == null && tag.specialTag == NO_DEBATE_LOADED)
                return POSITION_UNCHANGED;

            // That covers all situations in which mDebateManager could be null. Just to be safe:
            assert mDebateManager != null;

            // If there's no messy debate format changing or loading, delegate this function to the
            // DebateManager.
            int position = mDebateManager.getPhaseIndexForTag((DebatePhaseTag) object);

            return position;
        }

        @Override
        public int getCount() {
            if (mDebateManager == null) return 1;
            else return mDebateManager.getNumberOfPhases();
        }

        @Override
        public Object instantiateItem(ViewGroup container, int position) {
            // TODO generalise this code so it doesn't duplicate updateDebateTimerDisplay()

            // Note - the Object returned by this method must return the inflated View, because
            // setPrimaryItem() relies on this correlation in order to set mDebateTimerDisplay.

            if (mDebateManager == null) {
                Log.i(getClass().getSimpleName(), "No debate loaded");
                View v = View.inflate(DebatingActivity.this, R.layout.no_debate_loaded, null);
                container.addView(v);
                DebatePhaseTag tag = new DebatePhaseTag();
                tag.specialTag = NO_DEBATE_LOADED;
                mViewsMap.put(tag, v);
                return tag;
            }

            // The View for the position in question is the inflated debate_timer_display for
            // the relevant timer (prep time or speech).
            View v = View.inflate(DebatingActivity.this, R.layout.debate_timer_display, null);

            // Set the time picker to 24-hour time
            TimePicker currentTimePicker = (TimePicker) v.findViewById(R.id.debateTimer_currentTimePicker);
            currentTimePicker.setIs24HourView(true);

            // Set the POI timer OnClickListener
            Button poiTimerButton = (Button) v.findViewById(R.id.debateTimer_poiTimerButton);
            poiTimerButton.setOnClickListener(new PoiButtonOnClickListener());

            // Populate the text fields
            TextView periodDescriptionText = (TextView) v.findViewById(R.id.debateTimer_periodDescriptionText);
            TextView speechNameText        = (TextView) v.findViewById(R.id.debateTimer_speechNameText);
            TextView currentTimeText       = (TextView) v.findViewById(R.id.debateTimer_currentTime);
            TextView infoLineText          = (TextView) v.findViewById(R.id.debateTimer_informationLine);

            // OnTouchListeners
            v.setOnClickListener(new DebateTimerDisplayOnClickListener());
            currentTimeText.setOnLongClickListener(new CurrentTimeOnLongClickListener());

            long               time = mDebateManager.getPhaseCurrentTime(position);
            DebatePhaseFormat  dpf  = mDebateManager.getPhaseFormat(position);
            PeriodInfo         pi   = dpf.getPeriodInfoForTime(time);

            boolean overtime = time > dpf.getLength();

            // The information at the top of the screen
            speechNameText.setText(mDebateManager.getPhaseName(position));
            periodDescriptionText.setText(pi.getDescription());

            // Background colour, this is user-preference dependent
            Integer backgroundColour = pi.getBackgroundColor();
            switch (mBackgroundColourArea) {
            case TOP_BAR_ONLY:
                speechNameText.setBackgroundColor(backgroundColour);
                periodDescriptionText.setBackgroundColor(backgroundColour);
                break;
            case WHOLE_SCREEN:
                v.setBackgroundColor(backgroundColour);
            }

            // Take count direction into account for display
            long timeToShow = subtractFromSpeechLengthIfCountingDown(time, dpf);

            Resources resources = getResources();
            int timeTextColor;
            if (overtime)
                timeTextColor = resources.getColor(R.color.overtime);
            else
                timeTextColor = resources.getColor(android.R.color.primary_text_dark);
            currentTimeText.setText(secsToText(timeToShow));
            currentTimeText.setTextColor(timeTextColor);

            // Construct the line that goes at the bottom
            StringBuilder infoLine = new StringBuilder();

            // First, length...
            long length = dpf.getLength();
            String lengthStr;
            if (length % 60 == 0)
                lengthStr = String.format(getResources().
                        getQuantityString(R.plurals.timeInMinutes, (int) (length / 60), length / 60));
            else
                lengthStr = secsToText(length);

            int finalTimeTextUnformattedResid = (dpf.isPrep()) ? R.string.prepTimeLength: R.string.speechLength;
            infoLine.append(String.format(getString(finalTimeTextUnformattedResid), lengthStr));

            if (dpf.isPrep()) {
                PrepTimeFormat pt = (PrepTimeFormat) dpf;
                if (pt.isControlled())
                    infoLine.append(getString(R.string.prepTimeControlledIndicator));
            }

            // ...then, if applicable, bells
            ArrayList<BellInfo> currentSpeechBells = dpf.getBellsSorted();
            Iterator<BellInfo> currentSpeechBellsIter = currentSpeechBells.iterator();

            if (overtime) {
                // show next overtime bell (don't bother with list of bells anymore)
                Long nextOvertimeBellTime = mDebateManager.getPhaseNextOvertimeBellTime(position);
                if (nextOvertimeBellTime == null)
                    infoLine.append(getString(R.string.mainScreen_bellsList_noOvertimeBells));
                else {
                    long timeToDisplay = subtractFromSpeechLengthIfCountingDown(nextOvertimeBellTime, dpf);
                    infoLine.append(getString(R.string.mainScreen_bellsList_nextOvertimeBell,
                            secsToText(timeToDisplay)));
                }

            } else if (currentSpeechBellsIter.hasNext()) {
                // Convert the list of bells into a string.
                StringBuilder bellsStr = new StringBuilder();

                while (currentSpeechBellsIter.hasNext()) {
                    BellInfo bi = currentSpeechBellsIter.next();
                    long bellTime = subtractFromSpeechLengthIfCountingDown(bi.getBellTime(), dpf);
                    bellsStr.append(secsToText(bellTime));
                    if (bi.isPauseOnBell())
                        bellsStr.append(getString(R.string.pauseOnBellIndicator));
                    if (bi.isSilent())
                        bellsStr.append(getString(R.string.silentBellIndicator));
                    if (currentSpeechBellsIter.hasNext())
                        bellsStr.append(", ");
                }

                infoLine.append(getResources().getQuantityString(R.plurals.mainScreen_bellsList_normal, currentSpeechBells.size(), bellsStr));

            } else {
                infoLine.append(getString(R.string.mainScreen_bellsList_noBells));
            }

            infoLineText.setText(infoLine.toString());

            // Determine whether or not we display the POI timer button
            // Display only when user has POI timer enabled, and a debate is loaded and the current
            // speech has POIs in it.
            boolean displayPoiTimerButton = false;
            if (mPoiTimerEnabled)
                if (dpf.getClass() == SpeechFormat.class)
                    if (((SpeechFormat) dpf).hasPoisAllowedSomewhere())
                        displayPoiTimerButton = true;

            // If it's appropriate to display the button, do so
            // It'll always be disabled as the timer is never running when switching pages
            if (displayPoiTimerButton) {
                poiTimerButton.setVisibility(View.VISIBLE);
                poiTimerButton.setText(R.string.mainScreen_poiTimer_buttonText);
                poiTimerButton.setEnabled(false);

                // Otherwise, hide the button
            } else {
                poiTimerButton.setVisibility(View.GONE);
            }

            container.addView(v);

            DebatePhaseTag tag = mDebateManager.getPhaseTagForIndex(position);
            mViewsMap.put(tag, v);

            Log.i(getClass().getSimpleName(), String.format("Instantiated position %d, %s, %s", position, tag.toString(), v.toString()));

            return tag;

        }

        @Override
        public void setPrimaryItem(ViewGroup container, int position, Object object) {
            mDebateTimerDisplay = mViewsMap.get(object);
            updateGui();
        }

        @Override
        public boolean isViewFromObject(View view, Object object) {
            if (!mViewsMap.containsKey(object)) return false;
            else return (mViewsMap.get(object) == view);
        }

    }

    private class DebateTimerPagingEnabledIndicator implements PagingEnabledIndicator {

        @Override
        public boolean isPagingEnabled() {

            // This seems counter-intuitive, but we enable paging if there is no debate loaded,
            // as there is only one page anyway, and this way the "scrolled to the limit"
            // indicators appear on the screen.
            if (mDebateManager == null) return true;

            // Otherwise paging is enabled if and only if the timer is not running.
            else return !mDebateManager.isRunning();
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

        public FatalXmlError(String detailMessage) {
            super(detailMessage);
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
            switch (mDebateManager.getTimerStatus()) {
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

    private class PoiButtonOnClickListener implements View.OnClickListener {
        @Override
        public void onClick(View v) {
            if (mDebateManager != null) {
                if (mDebateManager.isPoiRunning())
                    mDebateManager.stopPoiTimer();
                else
                    mDebateManager.startPoiTimer();
            }
        }
    }

    private class RightControlButtonOnClickListener implements View.OnClickListener {
        @Override
        public void onClick(View pV) {
            if (mDebateManager == null) return;
            switch (mDebateManager.getTimerStatus()) {
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

    private enum BackgroundColourArea {
        // These must match the values string array in the preference.xml file.
        // (We can pull strings from the resource automatically,
        // but we can't assign them to enums automatically.)
        DISABLED     ("disabled"),
        TOP_BAR_ONLY ("topBarOnly"),
        WHOLE_SCREEN ("wholeScreen");

        private final String key;

        private BackgroundColourArea(String key) {
            this.key = key;
        }

        public static BackgroundColourArea toEnum(String key) {
            BackgroundColourArea[] values = BackgroundColourArea.values();
            for (int i = 0; i < values.length; i++)
                if (key.equals(values[i].key))
                    return values[i];
            throw new IllegalArgumentException(String.format("There is no enumerated constant '%s'", key));
        }
    }

    private enum CountDirection {

        // These must match the values string array in the preference.xml file.
        // (We can pull strings from the resource automatically,
        // but we can't assign them to enums automatically.)
        COUNT_UP   ("alwaysUp"),
        COUNT_DOWN ("alwaysDown");

        private final String key;

        private CountDirection(String key) {
            this.key = key;
        }

        public static CountDirection toEnum(String key) {
            CountDirection[] values = CountDirection.values();
            for (int i = 0; i < values.length; i++)
                if (key.equals(values[i].key))
                    return values[i];
            throw new IllegalArgumentException(String.format("There is no enumerated constant '%s'", key));
        }

    }

    //******************************************************************************************
    // Public methods
    //******************************************************************************************

    @Override
    public void onBackPressed() {

        // If no debate is loaded, exit.
        if (mDebateManager == null) {
            super.onBackPressed();
            return;
        }

        // If we're in editing mode, exit editing mode
        if (mIsEditingTime) {
            editCurrentTimeFinish(false);
            return;
        }

        // If the timer is stopped AND it's not the first speaker, go back one speaker.
        // Note: We do not just leave this check to goToPreviousSpeaker(), because we want to do
        // other things if it's not in a state in which it could go to the previous speaker.
        if (!mDebateManager.isInFirstPhase() && !mDebateManager.isRunning()) {
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
        editCurrentTimeFinish(false);
        switch (item.getItemId()) {
        case R.id.mainScreen_menuItem_prevSpeaker:
            goToPreviousSpeech();
            return true;
        case R.id.mainScreen_menuItem_chooseFormat:
            Intent getStyleIntent = new Intent(this, FormatChooserActivity.class);
            getStyleIntent.putExtra(FormatChooserActivity.EXTRA_XML_FILE_NAME, mFormatXmlFileName);
            startActivityForResult(getStyleIntent, CHOOSE_STYLE_REQUEST);
            return true;
        case R.id.mainScreen_menuItem_resetDebate:
            if (mDebateManager == null) return true;
            resetDebate();
            updateGui();
            return true;
        case R.id.mainScreen_menuItem_settings:
            startActivity(new Intent(this, GlobalSettingsActivity.class));
            return true;
        default:
            return super.onOptionsItemSelected(item);
        }
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {

        MenuItem prevSpeakerItem = menu.findItem(R.id.mainScreen_menuItem_prevSpeaker);
        MenuItem resetDebateItem = menu.findItem(R.id.mainScreen_menuItem_resetDebate);

        if (mDebateManager != null) {
            prevSpeakerItem.setEnabled(!mDebateManager.isInFirstPhase() && !mDebateManager.isRunning() && !mIsEditingTime);
            resetDebateItem.setEnabled(true);
        } else {
            prevSpeakerItem.setEnabled(false);
            resetDebateItem.setEnabled(false);
        }

        return super.onPrepareOptionsMenu(menu);
    }

    //******************************************************************************************
    // Protected methods
    //******************************************************************************************

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
        setContentView(R.layout.activity_debate);

        mFilesManager = new FormatXmlFilesManager(this);

        mLeftControlButton   = (Button) findViewById(R.id.mainScreen_leftControlButton);
        mCentreControlButton = (Button) findViewById(R.id.mainScreen_centreControlButton);
        mRightControlButton  = (Button) findViewById(R.id.mainScreen_rightControlButton);
        mPlayBellButton      = (Button) findViewById(R.id.mainScreen_playBellButton);

        //
        // ViewPager
        mViewPager = (EnableableViewPager) findViewById(R.id.mainScreen_debateTimerViewPager);
        mViewPager.setAdapter(new DebateTimerDisplayPagerAdapter());
        mViewPager.setOnPageChangeListener(new DebateTimerDisplayOnPageChangeListener());
        mViewPager.setPagingEnabledIndicator(new DebateTimerPagingEnabledIndicator());

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
    protected Dialog onCreateDialog(int id, Bundle args) {
        switch (id) {
        case DIALOG_XML_FILE_FATAL:
            return getDialogFatalProblemWithXmlFile(args);
        case DIALOG_XML_FILE_ERRORS:
            return getDialogErrorsWithXmlFile(args);
        case DIALOG_POI_TIMERS_INFO:
            return getDialogPoiTimerInfo();
        case DIALOG_XML_SCHEMA_TOO_NEW:
            return getDialogSchemaTooNew(args);
        }
        return super.onCreateDialog(id, args);
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
        boolean silentMode, vibrateMode, overtimeBellsEnabled;
        boolean poiBuzzerEnabled, poiVibrateEnabled, prepTimerEnabled;
        int firstOvertimeBell, overtimeBellPeriod;
        String userCountDirectionValue, userPrepTimeCountDirectionValue, poiFlashScreenModeValue, backgroundColourAreaValue;
        FlashScreenMode flashScreenMode, poiFlashScreenMode;

        Resources res = getResources();

        try {

            // The boolean preferences
            silentMode = prefs.getBoolean(res.getString(R.string.pref_silentMode_key),
                    res.getBoolean(R.bool.prefDefault_silentMode));
            vibrateMode = prefs.getBoolean(res.getString(R.string.pref_vibrateMode_key),
                    res.getBoolean(R.bool.prefDefault_vibrateMode));
            overtimeBellsEnabled = prefs.getBoolean(res.getString(R.string.pref_overtimeBellsEnable_key),
                    res.getBoolean(R.bool.prefDefault_overtimeBellsEnable));

            mKeepScreenOn = prefs.getBoolean(res.getString(R.string.pref_keepScreenOn_key),
                    res.getBoolean(R.bool.prefDefault_keepScreenOn));
            mPrepTimeKeepScreenOn = prefs.getBoolean(res.getString(R.string.pref_prepTimer_keepScreenOn_key),
                    res.getBoolean(R.bool.prefDefault_prepTimer_keepScreenOn));

            mPoiTimerEnabled = prefs.getBoolean(res.getString(R.string.pref_poiTimer_enable_key),
                    res.getBoolean(R.bool.prefDefault_poiTimer_enable));
            poiBuzzerEnabled = prefs.getBoolean(res.getString(R.string.pref_poiTimer_buzzerEnable_key),
                    res.getBoolean(R.bool.prefDefault_poiTimer_buzzerEnable));
            poiVibrateEnabled = prefs.getBoolean(res.getString(R.string.pref_poiTimer_vibrateEnable_key),
                    res.getBoolean(R.bool.prefDefault_poiTimer_vibrateEnable));

            prepTimerEnabled = prefs.getBoolean(res.getString(R.string.pref_prepTimer_enable_key),
                    res.getBoolean(R.bool.prefDefault_prepTimer_enable));

            // Overtime bell integers
            if (overtimeBellsEnabled) {
                firstOvertimeBell  = prefs.getInt(res.getString(R.string.pref_firstOvertimeBell_key),
                        res.getInteger(R.integer.prefDefault_firstOvertimeBell));
                overtimeBellPeriod = prefs.getInt(res.getString(R.string.pref_overtimeBellPeriod_key),
                        res.getInteger(R.integer.prefDefault_overtimeBellPeriod));
            } else {
                firstOvertimeBell = 0;
                overtimeBellPeriod = 0;
            }

            // List preference: POI flash screen mode
            poiFlashScreenModeValue = prefs.getString(res.getString(R.string.pref_poiTimer_flashScreenMode_key),
                    res.getString(R.string.prefDefault_poiTimer_flashScreenMode));
            poiFlashScreenMode = FlashScreenMode.toEnum(poiFlashScreenModeValue);

            // List preference: Count direction
            //  - Backwards compatibility measure
            // This changed in version 0.9, to remove the generallyUp and generallyDown options.
            // Therefore, if we find either of those, we need to replace it with alwaysUp or
            // alwaysDown, respectively.
            userCountDirectionValue = prefs.getString(res.getString(R.string.pref_countDirection_key),
                    res.getString(R.string.prefDefault_countDirection));
            if (userCountDirectionValue.equals("generallyUp") || userCountDirectionValue.equals("generallyDown")) {
                // Replace the preference with alwaysUp or alwaysDown, respectively.
                SharedPreferences.Editor editor = prefs.edit();
                String newValue = (userCountDirectionValue.equals("generallyUp")) ? "alwaysUp" : "alwaysDown";
                editor.putString(res.getString(R.string.pref_countDirection_key), newValue);
                editor.commit();
                Log.w(this.getClass().getSimpleName(),
                        String.format("countDirection: replaced %s with %s", userCountDirectionValue, newValue));
                userCountDirectionValue = newValue;
            }
            mCountDirection = CountDirection.toEnum(userCountDirectionValue);

            // List preference: Count direction for prep time
            userPrepTimeCountDirectionValue = prefs.getString(res.getString(R.string.pref_prepTimer_countDirection_key),
                    res.getString(R.string.prefDefault_prepTimer_countDirection));
            mPrepTimeCountDirection = CountDirection.toEnum(userPrepTimeCountDirectionValue);

            // List preference: Background colour area
            backgroundColourAreaValue = prefs.getString(res.getString(R.string.pref_backgroundColourArea_key),
                    res.getString(R.string.prefDefault_backgroundColourArea));
            mBackgroundColourArea = BackgroundColourArea.toEnum(backgroundColourAreaValue);
            resetBackgroundColour();

            // List preference: Flash screen mode
            //  - Backwards compatibility measure
            // This changed from a boolean to a list preference in version 0.6, so there is
            // backwards compatibility to take care of.  Backwards compatibility applies if
            // (a) the list preference is NOT present AND (b) the boolean preference IS present.
            // In this case, retrieve the boolean preference, delete it and write the corresponding
            // list preference.  In all other cases, just take the list preference (using the
            // normal default mechanism if it isn't present, i.e. neither are present).

            if (!prefs.contains(res.getString(R.string.pref_flashScreenMode_key)) &&
                    prefs.contains(res.getString(R.string.pref_flashScreenBool_key))) {
                // Boolean preference.
                // First, get the string and convert it to an enum.
                boolean flashScreenModeBool = prefs.getBoolean(
                        res.getString(R.string.pref_flashScreenBool_key), false);
                flashScreenMode = (flashScreenModeBool) ? FlashScreenMode.SOLID_FLASH : FlashScreenMode.OFF;

                // Then, convert that enum to the list preference value (a string) and write that
                // back to the preferences.  Also, remove the old boolean preference.
                String flashStringModePrefValue = flashScreenMode.toPrefValue();
                SharedPreferences.Editor editor = prefs.edit();
                editor.putString(res.getString(R.string.pref_flashScreenMode_key), flashStringModePrefValue);
                editor.remove(res.getString(R.string.pref_flashScreenBool_key));
                editor.commit();
                Log.w(this.getClass().getSimpleName(),
                        String.format("flashScreenMode: replaced boolean preference with list preference: %s", flashStringModePrefValue));

            } else {
                // List preference.
                // Get the string and convert it to an enum.
                String flashScreenModeValue;
                flashScreenModeValue = prefs.getString(res.getString(R.string.pref_flashScreenMode_key),
                        res.getString(R.string.prefDefault_flashScreenMode));
                flashScreenMode = FlashScreenMode.toEnum(flashScreenModeValue);
            }

        } catch (ClassCastException e) {
            Log.e(this.getClass().getSimpleName(), "applyPreferences: caught ClassCastException!");
            return;
        }

        if (mDebateManager != null) {
            mDebateManager.setOvertimeBells(firstOvertimeBell, overtimeBellPeriod);
            mDebateManager.setPrepTimeEnabled(prepTimerEnabled);
            applyPrepTimeBells();
        } else {
            Log.i(this.getClass().getSimpleName(), "applyPreferences: Couldn't restore overtime bells, mDebateManager doesn't yet exist");
        }

        if (mBinder != null) {
            AlertManager am = mBinder.getAlertManager();

            // Volume control stream is linked to silent mode
            am.setSilentMode(silentMode);
            setVolumeControlStream((silentMode) ? AudioManager.STREAM_RING : AudioManager.STREAM_MUSIC);

            am.setVibrateMode(vibrateMode);
            am.setFlashScreenMode(flashScreenMode);

            am.setPoiBuzzerEnabled(poiBuzzerEnabled);
            am.setPoiVibrateEnabled(poiVibrateEnabled);
            am.setPoiFlashScreenMode(poiFlashScreenMode);

            this.updateKeepScreenOn();

            Log.v(this.getClass().getSimpleName(), "applyPreferences: successfully applied");
        } else {
            Log.i(this.getClass().getSimpleName(), "applyPreferences: Couldn't restore AlertManager preferences; mBinder doesn't yet exist");
        }

    }

    private void applyPrepTimeBells() {
        PrepTimeBellsManager ptbm = new PrepTimeBellsManager(this);
        SharedPreferences prefs = getSharedPreferences(PrepTimeBellsManager.PREP_TIME_BELLS_PREFERENCES_NAME, MODE_PRIVATE);
        ptbm.loadFromPreferences(prefs);
        mDebateManager.setPrepTimeBellsManager(ptbm);
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

        DebateFormatBuilderFromXml dfbfx;

        InputStream is = null;
        DebateFormat df;

        try {
            is = mFilesManager.open(filename);
        } catch (IOException e) {
            throw new FatalXmlError(getString(R.string.fatalProblemWithXmlFileDialog_message_cannotFind, filename), e);
        }

        dfbfx = new DebateFormatBuilderFromXmlForSchema2(this);

        // First try schema 2.0
        try {
            df = dfbfx.buildDebateFromXml(is);
        } catch (IOException e) {
            throw new FatalXmlError(getString(R.string.fatalProblemWithXmlFileDialog_message_cannotRead), e);
        } catch (SAXException e) {
            throw new FatalXmlError(getString(
                    R.string.fatalProblemWithXmlFileDialog_message_badXml, e.getMessage()), e);
        }

        // If the schema wasn't supported, try schema 1.0 to see if it works
        if (!dfbfx.isSchemaSupported()) {

            DebateFormat df1;
            DebateFormatBuilderFromXml dfbfx1 = new DebateFormatBuilderFromXmlForSchema1(this);

            try {
                is.close();
                is = mFilesManager.open(filename);
            } catch (IOException e) {
                throw new FatalXmlError(getString(R.string.fatalProblemWithXmlFileDialog_message_cannotFind), e);
            }

            try {
                df1 = dfbfx1.buildDebateFromXml(is);
            } catch (IOException e) {
                throw new FatalXmlError(getString(R.string.fatalProblemWithXmlFileDialog_message_cannotRead), e);
            } catch (SAXException e) {
                throw new FatalXmlError(getString(
                        R.string.fatalProblemWithXmlFileDialog_message_badXml, e.getMessage()), e);
            }

            // If it's looking good, replace.
            // (Otherwise, pretend this schema 1.0 attempt never happened.)
            if (dfbfx1.isSchemaSupported()) {
                df    = df1;
                dfbfx = dfbfx1;
            }
        }

        // If the schema still isn't supported (even after possibly having been replaced by
        // schema 1.0), prompt the user to upgrade the app.
        if (dfbfx.isSchemaTooNew()) {
            Bundle bundle = new Bundle();
            bundle.putString(DIALOG_BUNDLE_SCHEMA_SUPPORTED, dfbfx.getSupportedSchemaVersion());
            bundle.putString(DIALOG_BUNDLE_SCHEMA_USED, dfbfx.getSchemaVersion());
            removeDialog(DIALOG_XML_SCHEMA_TOO_NEW);
            showDialog(DIALOG_XML_SCHEMA_TOO_NEW, bundle);
        }

        if (df.numberOfSpeeches() == 0)
            throw new FatalXmlError(getString(
                    R.string.fatalProblemWithXmlFileDialog_message_noSpeeches));

        if (dfbfx.hasErrors()) {
            Bundle bundle = new Bundle();
            bundle.putStringArrayList(DIALOG_BUNDLE_XML_ERROR_LOG, dfbfx.getErrorLog());
            removeDialog(DIALOG_XML_FILE_ERRORS);
            queueDialog(DIALOG_XML_FILE_ERRORS, bundle);
        }

        return df;
    }

    /**
     * Displays the time picker to edit the current time.
     * Does nothing if there is no debate loaded or if the timer is running.
     */
    private void editCurrentTimeStart() {

        // Check that things are in a valid state to enter edit time mode
        // If they aren't, return straight away
        if (mDebateManager == null) return;
        if (mDebateManager.isRunning()) return;

        // Only if things were in a valid state do we enter edit time mode
        mIsEditingTime = true;

        TimePicker currentTimePicker = (TimePicker) mDebateTimerDisplay.findViewById(R.id.debateTimer_currentTimePicker);

        if (currentTimePicker == null) {
            Log.e("editCurrentTimeStart", "currentTimePicker was null");
            return;
        }

        long currentTime = mDebateManager.getActivePhaseCurrentTime();

        // Invert the time if in count-down mode
        currentTime = subtractFromSpeechLengthIfCountingDown(currentTime);

        // Limit to the allowable time range
        if (currentTime < 0) {
            currentTime = 0;
            Toast.makeText(this, R.string.mainScreen_toast_editTextDiscardChangesInfo_limitedBelow, Toast.LENGTH_LONG).show();
        }
        if (currentTime >= 24 * 60) {
            currentTime = 24 * 60 - 1;
            Toast.makeText(this, R.string.mainScreen_toast_editTextDiscardChangesInfo_limitedAbove, Toast.LENGTH_LONG).show();
        }

        // We're using this in hours and minutes, not minutes and seconds
        currentTimePicker.setCurrentHour((int) (currentTime / 60));
        currentTimePicker.setCurrentMinute((int) (currentTime % 60));

        updateGui();

        // If we had to limit the time, display a helpful/apologetic message informing the user
        // of how to discard their changes, since they can't recover the time.

    }

    /**
     * Finishes editing the current time and restores the GUI to its prior state.
     * @param save true if the edited time should become the new current time, false if it should
     * be discarded.
     */
    private void editCurrentTimeFinish(boolean save) {

        TimePicker currentTimePicker = (TimePicker) mDebateTimerDisplay.findViewById(R.id.debateTimer_currentTimePicker);

        if (currentTimePicker == null) {
            Log.e("editCurrentTimeFinish", "currentTimePicker was null");
            return;
        }

        currentTimePicker.clearFocus();

        // Hide the keyboard
        InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        imm.hideSoftInputFromWindow(currentTimePicker.getWindowToken(), 0);

        if (save && mDebateManager != null && mIsEditingTime) {
            // We're using this in hours and minutes, not minutes and seconds
            int minutes = currentTimePicker.getCurrentHour();
            int seconds = currentTimePicker.getCurrentMinute();
            long newTime = minutes * 60 + seconds;
            // Invert the time if in count-down mode
            newTime = subtractFromSpeechLengthIfCountingDown(newTime);
            mDebateManager.setActivePhaseCurrentTime(newTime);
        }

        mIsEditingTime = false;

        updateGui();

    }

    /**
     * Returns the count direction that should currently be used.
     * This method used to assemble the speech format and user count directions to find the
     * count direction to use.  In version 0.9, the speech format count direction was made
     * obsolete, so the only thing it has to take into account now is the user count direction.
     * However, because of the addition of a separate prep time count direction, there is still
     * some brain-work to do.
     * @return CountDirection.COUNT_UP or CountDirection.COUNT_DOWN
     */
    private CountDirection getCountDirection(DebatePhaseFormat spf) {
        if (spf.isPrep())
            return mPrepTimeCountDirection;
        else
            return mCountDirection;
    }

    private Dialog getDialogErrorsWithXmlFile(Bundle bundle) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);

        StringBuilder errorMessage = new StringBuilder(getString(R.string.errorsinXmlFileDialog_message_prefix));

        ArrayList<String> errorLog = bundle.getStringArrayList(DIALOG_BUNDLE_XML_ERROR_LOG);
        Iterator<String> errorIterator = errorLog.iterator();

        while (errorIterator.hasNext()) {
            errorMessage.append("\n");
            errorMessage.append(errorIterator.next());
        }

        errorMessage.append(getString(R.string.dialogs_fileName_suffix, mFormatXmlFileName));

        builder.setTitle(R.string.errorsinXmlFileDialog_title)
               .setMessage(errorMessage)
               .setCancelable(true)
               .setPositiveButton(R.string.errorsinXmlFileDialog_button, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                    }
                });

        return builder.create();
    }

    private Dialog getDialogFatalProblemWithXmlFile(Bundle bundle) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);

        StringBuilder errorMessage = new StringBuilder(bundle.getString(DIALOG_BUNDLE_FATAL_MESSAGE));
        errorMessage.append(getString(R.string.fatalProblemWithXmlFileDialog_message_suffix));
        errorMessage.append(getString(R.string.dialogs_fileName_suffix, mFormatXmlFileName));

        builder.setTitle(R.string.fatalProblemWithXmlFileDialog_title)
               .setMessage(errorMessage)
               .setCancelable(true)
               .setPositiveButton(R.string.fatalProblemWithXmlFileDialog_button, new DialogInterface.OnClickListener() {
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

    private Dialog getDialogPoiTimerInfo() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);

        View content = getLayoutInflater().inflate(R.layout.poi_timer_dialog, null);
        final CheckBox doNotShowAgain = (CheckBox) content.findViewById(R.id.poiTimerInfoDialog_dontShow);

        builder.setTitle(R.string.poiTimerInfoDialog_title)
               .setView(content)
               .setCancelable(true)
               .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        // Take note of "do not show again" setting
                        SharedPreferences prefs = getPreferences(MODE_PRIVATE);
                        Editor editor = prefs.edit();
                        editor.putBoolean(DO_NOT_SHOW_POI_TIMER_DIALOG, doNotShowAgain.isChecked());
                        editor.commit();
                        dialog.dismiss();
                    }
                })
               .setNeutralButton(R.string.poiTimerInfoDialog_button_learnMore, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        Uri uri = Uri.parse(getString(R.string.poiTimer_moreInfoUrl));
                        Intent intent = new Intent(Intent.ACTION_VIEW, uri);
                        startActivity(intent);
                    }
                })
                .setOnCancelListener(new DialogInterface.OnCancelListener() {
                    @Override
                    public void onCancel(DialogInterface dialog) {
                        dialog.dismiss();
                    }
                });

        return builder.create();

    }

    private Dialog getDialogSchemaTooNew(Bundle bundle) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);

        mDialogBlocking = true;

        String schemaUsed      = bundle.getString(DIALOG_BUNDLE_SCHEMA_USED);
        String schemaSupported = bundle.getString(DIALOG_BUNDLE_SCHEMA_SUPPORTED);

        String appVersion;
        try {
            appVersion = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (NameNotFoundException e) {
            appVersion = "unknown";
        }

        StringBuilder message = new StringBuilder(getString(R.string.schemaTooNewDialog_message, schemaUsed, schemaSupported, appVersion));

        message.append(getString(R.string.dialogs_fileName_suffix, mFormatXmlFileName));

        builder.setTitle(R.string.schemaTooNewDialog_title)
               .setMessage(message)
               .setCancelable(false)
               .setPositiveButton(R.string.schemaTooNewDialog_button_upgrade, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        // Open Google Play to upgrade
                        Uri uri = Uri.parse(getString(R.string.app_marketUri));
                        Intent intent = new Intent(Intent.ACTION_VIEW);
                        intent.setData(uri);
                        startActivity(intent);
                    }
                })
            .setNegativeButton(R.string.schemaTooNewDialog_button_ignore, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    // To ignore, just dismiss the dialog and return to whatever was happening before
                    dialog.dismiss();
                }
            });

        AlertDialog dialog = builder.create();

        // This method is only supported for AlertDialog.Builder from API level 17 onwards,
        // so we have to call it on the AlertDialog directly.
        dialog.setOnDismissListener(new DialogInterface.OnDismissListener() {
            @Override
            public void onDismiss(DialogInterface dialog) {
                showQueuedDialog();
            }
        });

        return dialog;
    }

    /**
     * Goes to the next speech.
     * Does nothing if there is no debate loaded, if the current speech is the last speech, if
     * the timer is running, or if the current time is being edited.
     */
    private void goToNextSpeech() {

        if (mDebateManager == null) return;
        if (mDebateManager.isRunning()) return;
        if (mDebateManager.isInLastPhase()) return;
        if (mIsEditingTime) return;

        mDebateManager.goToNextPhase();
        mViewPager.setCurrentItem(mDebateManager.getActivePhaseIndex());

        updateGui();
        updateKeepScreenOn();

    }

    /**
     * Goes to the previous speech.
     * Does nothing if there is no debate loaded, if the current speech is the first speech, if
     * the timer is running, or if the current time is being edited.
     */
    private void goToPreviousSpeech() {

        if (mDebateManager == null) return;
        if (mDebateManager.isRunning()) return;
        if (mDebateManager.isInFirstPhase()) return;
        if (mIsEditingTime) return;

        mDebateManager.goToPreviousPhase();
        mViewPager.setCurrentItem(mDebateManager.getActivePhaseIndex());

        updateGui();
        updateKeepScreenOn();

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
                queueDialog(DIALOG_XML_FILE_FATAL, bundle);
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

            SharedPreferences prefs = getPreferences(MODE_PRIVATE);
            if (!prefs.getBoolean(DO_NOT_SHOW_POI_TIMER_DIALOG, false))
                if (df.hasPoisAllowedSomewhere())
                    if (mPoiTimerEnabled)
                        showDialog(DIALOG_POI_TIMERS_INFO);
        }

        mViewPager.getAdapter().notifyDataSetChanged();
        mViewPager.setCurrentItem(mDebateManager.getActivePhaseIndex());
        applyPreferences();
        updateGui();
    }

    private String loadXmlFileName() {
        SharedPreferences sp = getPreferences(MODE_PRIVATE);
        String filename = sp.getString(PREFERENCE_XML_FILE_NAME, null);
        mFormatXmlFileName = filename;
        return filename;
    }

    /**
     * Queues a dialog to be shown after a currently-shown dialog, or immediately if there is
     * no currently-shown dialog.  This does not happen automatically - dialogs must know whether
     * they are potentially blocking or waiting, and set themselves up accordingly.  Dialogs
     * that could block must set <code>mDialogBlocking</code> to true when they are shown, and call
     * <code>showQueuedDialog()</code> when they are dismissed.
     * Dialogs that could be queued must call <code>queueDialog()</code> instead of <code>showDialog()</code>.
     * Only one dialog may be queued at a time.  If more than one dialog is queued, only the last
     * one is kept in the queue; all others are discarded.
     * @param id the dialog ID that would be passed to showDialog()
     * @param args the {@link Bundle} that would be passed to showDialog()
     */
    private void queueDialog(int id, Bundle args) {
        if (!mDialogBlocking) {
            showDialog(id, args);
            return;
        }

        mDialogWaiting = true;
        mDialogIdWaiting = id;
        mDialogBundleInWaiting = args;
    }

    /**
     * Shows the currently-queued dialog if there is one; does nothing otherwise.  Dialogs that
     * could block other dialogs must call this method on dismissal.
     */
    private void showQueuedDialog() {
        if (mDialogWaiting) {
            showDialog(mDialogIdWaiting, mDialogBundleInWaiting);
            mDialogBlocking = false;
            mDialogWaiting = false;
            mDialogIdWaiting = -1;
            mDialogBundleInWaiting = null;
        }
    }

    /**
     * Resets the background colour to the default.
     * This should be called whenever the background colour preference is changed, as <code>updateGui()</code>
     * doesn't automatically do this (for efficiency). You should call <code>updateGui()</code> as immediately as
     * practicable after calling this.
     */
    private void resetBackgroundColour() {
        if (mDebateTimerDisplay == null || mDebateManager == null) return;
        if (mDebateTimerDisplay.getId() != R.id.debateTimer_root) return;
        View v = mDebateTimerDisplay;
        v.setBackgroundColor(mNormalBackgroundColour);
        View speechNameText = v.findViewById(R.id.debateTimer_speechNameText);
        View periodDescriptionText = v.findViewById(R.id.debateTimer_periodDescriptionText);
        speechNameText.setBackgroundColor(mNormalBackgroundColour);
        periodDescriptionText.setBackgroundColor(mNormalBackgroundColour);
    }

    private void resetDebate() {
        resetDebateWithoutToast();
        Toast.makeText(this, R.string.mainScreen_toast_resetDebate, Toast.LENGTH_SHORT).show();
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
                am.setFlashScreenListener(new DebatingTimerFlashScreenListener());
                am.activityStart();
            }
        }
    }

    /**
     *  Sets the text and visibility of a single button
     */
    private void setButton(Button button, int resid) {
        button.setText(resid);
        int visibility = (resid == R.string.mainScreen_null_buttonText) ? View.GONE : View.VISIBLE;
        button.setVisibility(visibility);
    }

    /**
     *  Sets the text, visibility and "weight" of all buttons
     * @param leftResid
     * @param centreResid
     * @param rightResid
     */
    private void setButtons(int leftResid, int centreResid, int rightResid) {
        setButton(mLeftControlButton, leftResid);
        setButton(mCentreControlButton, centreResid);
        setButton(mRightControlButton, rightResid);

        // If there are exactly two buttons, make the weight of the left button double,
        // so that it fills two-thirds of the width of the screen.
        float leftControlButtonWeight = (float) ((centreResid == R.string.mainScreen_null_buttonText && rightResid != R.string.mainScreen_null_buttonText) ? 2.0 : 1.0);
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
    private void updateControls() {
        if (mDebateTimerDisplay == null) return;
        if (mDebateTimerDisplay.getId() != R.id.debateTimer_root) return;

        View currentTimeText   = mDebateTimerDisplay.findViewById(R.id.debateTimer_currentTime);
        View currentTimePicker = mDebateTimerDisplay.findViewById(R.id.debateTimer_currentTimePicker);

        if (mDebateManager != null) {

            // If it's the last speaker, don't show a "next speaker" button.
            // Show a "restart debate" button instead.
            switch (mDebateManager.getTimerStatus()) {
            case NOT_STARTED:
                setButtons(R.string.mainScreen_startTimer_buttonText, R.string.mainScreen_null_buttonText, R.string.mainScreen_nextSpeaker_buttonText);
                break;
            case RUNNING:
                setButtons(R.string.mainScreen_stopTimer_buttonText, R.string.mainScreen_null_buttonText, R.string.mainScreen_null_buttonText);
                break;
            case STOPPED_BY_BELL:
                setButtons(R.string.mainScreen_resumeTimerAfterAlarm_buttonText, R.string.mainScreen_null_buttonText, R.string.mainScreen_null_buttonText);
                break;
            case STOPPED_BY_USER:
                setButtons(R.string.mainScreen_resumeTimerAfterUserStop_buttonText, R.string.mainScreen_resetTimer_buttonText, R.string.mainScreen_nextSpeaker_buttonText);
                break;
            default:
                break;
            }

            if (mIsEditingTime) {
                // Show the time picker, not the text
                currentTimeText.setVisibility(View.GONE);
                currentTimePicker.setVisibility(View.VISIBLE);

                // Disable all control buttons
                mLeftControlButton.setEnabled(false);
                mCentreControlButton.setEnabled(false);
                mRightControlButton.setEnabled(false);
            } else {
                // Show the time as text, not the picker
                currentTimeText.setVisibility(View.VISIBLE);
                currentTimePicker.setVisibility(View.GONE);

                // Disable the [Next Speaker] button if there are no more speakers
                mLeftControlButton.setEnabled(true);
                mCentreControlButton.setEnabled(true);
                mRightControlButton.setEnabled(!mDebateManager.isInLastPhase());
            }

        } else {
            // If no debate is loaded, show only one control button, which leads the user to
            // choose a style.
            // (Keep the play bell button enabled.)
            setButtons(R.string.mainScreen_noDebateLoaded_buttonText, R.string.mainScreen_null_buttonText, R.string.mainScreen_null_buttonText);
            mLeftControlButton.setEnabled(true);
            mCentreControlButton.setEnabled(false);
            mRightControlButton.setEnabled(false);
        }

        // Show or hide the [Bell] button
        updatePlayBellButton();
    }

    /**
     * Updates the debate timer display (including speech name, period name, etc.
     * The view should be the <code>RelativeLayout</code> in debate_timer_display.xml.
     */
    private void updateDebateTimerDisplay() {

        if (mDebateTimerDisplay == null) {
            Log.e(this.getClass().getSimpleName(), "mDebateTimerDisplay was null");
            return;
        }
        if (mDebateTimerDisplay.getId() != R.id.debateTimer_root) {
            Log.e(this.getClass().getSimpleName(), "mDebateTimerDisplay was not the debate timer display");
            return;
        }

        TextView periodDescriptionText = (TextView) mDebateTimerDisplay.findViewById(R.id.debateTimer_periodDescriptionText);
        TextView speechNameText        = (TextView) mDebateTimerDisplay.findViewById(R.id.debateTimer_speechNameText);
        TextView currentTimeText       = (TextView) mDebateTimerDisplay.findViewById(R.id.debateTimer_currentTime);
        TextView infoLineText          = (TextView) mDebateTimerDisplay.findViewById(R.id.debateTimer_informationLine);

        if (mDebateManager != null) {

            DebatePhaseFormat currentSpeechFormat = mDebateManager.getActivePhaseFormat();
            PeriodInfo         currentPeriodInfo   = mDebateManager.getActivePhaseCurrentPeriodInfo();

            // The information at the top of the screen
            speechNameText.setText(mDebateManager.getActivePhaseName());
            periodDescriptionText.setText(currentPeriodInfo.getDescription());

            // Background colour, this is user-preference dependent
            Integer backgroundColour = currentPeriodInfo.getBackgroundColor();
            switch (mBackgroundColourArea) {
            case TOP_BAR_ONLY:
                speechNameText.setBackgroundColor(backgroundColour);
                periodDescriptionText.setBackgroundColor(backgroundColour);
                break;
            case WHOLE_SCREEN:
                // Don't do the whole screen if there is a flash screen in progress
                if (mFlashScreenSemaphore.tryAcquire()) {
                    mDebateTimerDisplay.setBackgroundColor(backgroundColour);
                    mFlashScreenSemaphore.release();
                }
            }

            long currentSpeechTime = mDebateManager.getActivePhaseCurrentTime();

            // Take count direction into account for display
            currentSpeechTime = subtractFromSpeechLengthIfCountingDown(currentSpeechTime);

            Resources resources = getResources();
            int currentTimeTextColor;
            if (mDebateManager.isOvertime())
                currentTimeTextColor = resources.getColor(R.color.overtime);
            else
                currentTimeTextColor = resources.getColor(android.R.color.primary_text_dark);
            currentTimeText.setText(secsToText(currentSpeechTime));
            currentTimeText.setTextColor(currentTimeTextColor);

            // Construct the line that goes at the bottom
            StringBuilder infoLine = new StringBuilder();

            // First, length...
            long length = currentSpeechFormat.getLength();
            String lengthStr;
            if (length % 60 == 0)
                lengthStr = String.format(getResources().
                        getQuantityString(R.plurals.timeInMinutes, (int) (length / 60), length / 60));
            else
                lengthStr = secsToText(length);

            int finalTimeTextUnformattedResid = (mDebateManager.isInPrepTime()) ? R.string.prepTimeLength: R.string.speechLength;
            infoLine.append(String.format(this.getString(finalTimeTextUnformattedResid),
                    lengthStr));

            if (mDebateManager.isInPrepTime() && mDebateManager.isPrepTimeControlled())
                infoLine.append(getString(R.string.prepTimeControlledIndicator));

            // ...then, if applicable, bells
            ArrayList<BellInfo> currentSpeechBells = currentSpeechFormat.getBellsSorted();
            Iterator<BellInfo> currentSpeechBellsIter = currentSpeechBells.iterator();

            if (mDebateManager.isOvertime()) {
                // show next overtime bell (don't bother with list of bells anymore)
                Long nextOvertimeBellTime = mDebateManager.getActivePhaseNextOvertimeBellTime();
                if (nextOvertimeBellTime == null)
                    infoLine.append(getString(R.string.mainScreen_bellsList_noOvertimeBells));
                else {
                    long timeToDisplay = subtractFromSpeechLengthIfCountingDown(nextOvertimeBellTime);
                    infoLine.append(getString(R.string.mainScreen_bellsList_nextOvertimeBell,
                            secsToText(timeToDisplay)));
                }

            } else if (currentSpeechBellsIter.hasNext()) {
                // Convert the list of bells into a string.
                StringBuilder bellsStr = new StringBuilder();

                while (currentSpeechBellsIter.hasNext()) {
                    BellInfo bi = currentSpeechBellsIter.next();
                    long bellTime = subtractFromSpeechLengthIfCountingDown(bi.getBellTime());
                    bellsStr.append(secsToText(bellTime));
                    if (bi.isPauseOnBell())
                        bellsStr.append(getString(R.string.pauseOnBellIndicator));
                    if (bi.isSilent())
                        bellsStr.append(getString(R.string.silentBellIndicator));
                    if (currentSpeechBellsIter.hasNext())
                        bellsStr.append(", ");
                }

                infoLine.append(getResources().getQuantityString(R.plurals.mainScreen_bellsList_normal, currentSpeechBells.size(), bellsStr));

            } else {
                infoLine.append(getString(R.string.mainScreen_bellsList_noBells));
            }

            infoLineText.setText(infoLine.toString());

        } else {
            // Blank out all the fields
            periodDescriptionText.setText(R.string.mainScreen_noDebateLoaded_text);
            speechNameText.setText("");
            periodDescriptionText.setBackgroundColor(0);
            speechNameText.setBackgroundColor(0);
            currentTimeText.setText("");
            infoLineText.setText("");
        }

        // Update the POI timer button
        updatePoiTimerButton();

    }

    /**
     * Sets the "keep screen on" setting according to whether it is prep time or a speech.
     * This method should be called whenever (a) we switch between speeches and prep time,
     * and (b) the user preference is applied again.
     */
    private void updateKeepScreenOn() {
        if (mBinder == null)
            return;

        AlertManager am = mBinder.getAlertManager();

        if (mDebateManager != null) {
            if (mDebateManager.isInPrepTime()) {
                am.setKeepScreenOn(mPrepTimeKeepScreenOn);
                return;
            }
        }

        am.setKeepScreenOn(mKeepScreenOn);
    }

    /**
     * Updates the GUI (in the general case).
     */
    private void updateGui() {
        updateDebateTimerDisplay();
        updateControls();

        if (mDebateManager != null) {
            this.setTitle(getString(R.string.activityName_Debating_withFormat, mDebateManager.getDebateFormatName()));
        } else {
            setTitle(R.string.activityName_Debating_withoutFormat);
        }

    }

    private void updatePlayBellButton() {
        if (mBinder != null)
            mPlayBellButton.setVisibility((mBinder.getAlertManager().isSilentMode()) ? View.GONE : View.VISIBLE);
    }

    private void updatePoiTimerButton() {
        Button poiButton = (Button) mDebateTimerDisplay.findViewById(R.id.debateTimer_poiTimerButton);

        // Determine whether or not we display the POI timer button
        // Display only when user has POI timer enabled, and a debate is loaded and the current
        // speech has POIs in it.
        boolean displayPoiTimerButton = false;
        if (mPoiTimerEnabled)
            if (mDebateManager != null)
                if (mDebateManager.hasPoisInActivePhase())
                    displayPoiTimerButton = true;

        // If it's appropriate to display the button, do so
        if (displayPoiTimerButton) {
            poiButton.setVisibility(View.VISIBLE);

            if (mDebateManager.isPoisActive()) {
                poiButton.setEnabled(mDebateManager.isRunning());

                Long poiTime = mDebateManager.getCurrentPoiTime();
                if (poiTime == null)
                    poiButton.setText(R.string.mainScreen_poiTimer_buttonText);
                else
                    poiButton.setText(poiTime.toString());
            } else {
                poiButton.setText(R.string.mainScreen_poiTimer_buttonText);
                poiButton.setEnabled(false);
            }

        // Otherwise, hide the button
        } else {
            poiButton.setVisibility(View.GONE);
        }
    }

    private static String secsToText(long time) {
        if (time >= 0) {
            return String.format("%02d:%02d", time / 60, time % 60);
        } else {
            return String.format("+%02d:%02d", -time / 60, -time % 60);
        }
    }

    /**
     * Returns the number of seconds that would be displayed, taking into account the count
     * direction.  If the overall count direction is <code>COUNT_DOWN</code> and there is a speech
     * format ready, it returns (speechLength - time).  Otherwise, it just returns time.
     * @param time the time that is wished to be formatted (in seconds)
     * @return the time that would be displayed (as an integer, number of seconds)
     */
    private long subtractFromSpeechLengthIfCountingDown(long time) {
        if (mDebateManager != null)
            return subtractFromSpeechLengthIfCountingDown(time, mDebateManager.getActivePhaseFormat());
        return time;
    }

    private long subtractFromSpeechLengthIfCountingDown(long time, DebatePhaseFormat sf) {
        if (getCountDirection(sf) == CountDirection.COUNT_DOWN)
            return sf.getLength() - time;
        return time;
    }
}
