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
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.res.Resources;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.design.widget.Snackbar;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.FragmentTransaction;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v4.view.PagerAdapter;
import android.support.v4.view.ViewPager.SimpleOnPageChangeListener;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.text.format.DateUtils;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnLongClickListener;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.TextView;
import android.widget.TimePicker;

import net.czlee.debatekeeper.AlertManager.FlashScreenListener;
import net.czlee.debatekeeper.AlertManager.FlashScreenMode;
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

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map.Entry;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;


/**
 * This is the main activity for the Debatekeeper application.  It is the launcher activity,
 * and the activity in which the user spends the most time.
 *
 * @author Phillip Cao
 * @author Chuan-Zheng Lee
 * @since  2012-04-05
 *
 */
public class DebatingActivity extends AppCompatActivity {

    private static final String TAG = "DebatingActivity";

    private View             mDebateTimerDisplay;
    private boolean          mIsEditingTime = false;
    private final Semaphore  mFlashScreenSemaphore = new Semaphore(1, true);
    private static final int COLOUR_TRANSPARENT = 0;

    private EnableableViewPager mViewPager;
    private boolean             mChangingPages;

    private Button    mLeftControlButton;
    private Button    mLeftCentreControlButton;
    private Button    mCentreControlButton;
    private Button    mRightControlButton;
    private Button    mPlayBellButton;

    private final ControlButtonSpec CONTROL_BUTTON_START_TIMER  = new ControlButtonSpec(R.string.mainScreen_controlButton_startTimer_text,  new ControlButtonStartTimerOnClickListener());
    private final ControlButtonSpec CONTROL_BUTTON_STOP_TIMER   = new ControlButtonSpec(R.string.mainScreen_controlButton_stopTimer_text,   new ControlButtonStopTimerOnClickListener());
    private final ControlButtonSpec CONTROL_BUTTON_CHOOSE_STYLE = new ControlButtonSpec(R.string.mainScreen_controlButton_chooseStyle_text, new ControlButtonChooseStyleOnClickListener());
    private final ControlButtonSpec CONTROL_BUTTON_RESET_TIMER  = new ControlButtonSpec(R.string.mainScreen_controlButton_resetTimer_text,  new ControlButtonResetActiveDebatePhaseOnClickListener());
    private final ControlButtonSpec CONTROL_BUTTON_RESUME_TIMER = new ControlButtonSpec(R.string.mainScreen_controlButton_resumeTimer_text, new ControlButtonStartTimerOnClickListener());
    private final ControlButtonSpec CONTROL_BUTTON_NEXT_PHASE   = new ControlButtonSpec(R.string.mainScreen_controlButton_nextPhase_text,   new ControlButtonNextDebatePhaseOnClickListener());

    private DebateManager         mDebateManager;
    private Bundle                mLastStateBundle;

    private String               mFormatXmlFileName      = null;
    private CountDirection       mCountDirection         = CountDirection.COUNT_UP;
    private CountDirection       mPrepTimeCountDirection = CountDirection.COUNT_DOWN;
    private BackgroundColourArea mBackgroundColourArea   = BackgroundColourArea.WHOLE_SCREEN;
    private boolean              mPoiTimerEnabled        = true;
    private boolean              mBellsEnabled           = true;
    private boolean              mSpeechKeepScreenOn;
    private boolean              mPrepTimeKeepScreenOn;

    private boolean              mDialogBlocking          = false;
    private boolean              mDialogWaiting           = false;
    private DialogFragment       mDialogFragmentInWaiting = null;
    private String               mDialogTagInWaiting      = null;

    private static final String BUNDLE_KEY_DEBATE_MANAGER        = "dm";
    private static final String BUNDLE_KEY_XML_FILE_NAME         = "xmlfn";
    private static final String PREFERENCE_XML_FILE_NAME         = "xmlfn";
    private static final String LAST_CHANGELOG_VERSION_SHOWN     = "lastChangeLog";
    private static final String DIALOG_ARGUMENT_FATAL_MESSAGE    = "fm";
    private static final String DIALOG_ARGUMENT_XML_ERROR_LOG    = "xel";
    private static final String DIALOG_ARGUMENT_SCHEMA_USED      = "used";
    private static final String DIALOG_ARGUMENT_SCHEMA_SUPPORTED = "supp";
    private static final String DIALOG_ARGUMENT_FILE_NAME        = "fn";
    private static final String DIALOG_TAG_SCHEMA_TOO_NEW        = "toonew";
    private static final String DIALOG_TAG_ERRORS_WITH_XML       = "errors";
    private static final String DIALOG_TAG_FATAL_PROBLEM         = "fatal";
    private static final String DIALOG_TAG_CHANGELOG             = "changelog";

    private static final int    CHOOSE_STYLE_REQUEST           = 0;
    private static final int    SNACKBAR_DURATION_RESET_DEBATE = 500;

    private DebatingTimerService.DebatingTimerServiceBinder mBinder;
    private final BroadcastReceiver mGuiUpdateBroadcastReceiver = new GuiUpdateBroadcastReceiver();
    private final ServiceConnection mConnection = new DebatingTimerServiceConnection();

    //******************************************************************************************
    // Public classes
    //******************************************************************************************

    public static class DialogChangelogFragment extends DialogFragment {

        @NonNull
        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            final Activity activity = getActivity();
            AlertDialog.Builder builder = new AlertDialog.Builder(activity);
            View content = activity.getLayoutInflater().inflate(R.layout.changelog_dialog, null);
            final CheckBox doNotShowAgain = (CheckBox) content.findViewById(R.id.changelogDialog_dontShow);
            final Resources res = getResources();

            builder.setTitle(res.getString(R.string.changelogDialog_title, BuildConfig.VERSION_NAME))
                    .setView(content)
                    .setCancelable(true)
                    .setPositiveButton(res.getString(R.string.changelogDialog_ok), new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            // Take note of "do not show again" setting
                            if (doNotShowAgain.isChecked()) {
                                SharedPreferences prefs = activity.getPreferences(MODE_PRIVATE);
                                Editor editor = prefs.edit();
                                int thisChangelogVersionCode = res.getInteger(R.integer.changelogDialog_versionCode);
                                editor.putInt(LAST_CHANGELOG_VERSION_SHOWN, thisChangelogVersionCode);
                                editor.apply();
                            }
                            dialog.dismiss();
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

    }

    public static class DialogErrorsWithXmlFileFragment extends DialogFragment {

        static DialogErrorsWithXmlFileFragment newInstance(ArrayList<String> errorLog, String filename) {
            DialogErrorsWithXmlFileFragment fragment = new DialogErrorsWithXmlFileFragment();
            Bundle args = new Bundle();
            args.putStringArrayList(DIALOG_ARGUMENT_XML_ERROR_LOG, errorLog);
            args.putString(DIALOG_ARGUMENT_FILE_NAME, filename);
            fragment.setArguments(args);
            return fragment;
        }

        @NonNull
        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());

            StringBuilder errorMessage = new StringBuilder(getString(R.string.errorsinXmlFileDialog_message_prefix));

            Bundle args = getArguments();

            ArrayList<String> errorLog = args.getStringArrayList(DIALOG_ARGUMENT_XML_ERROR_LOG);

            if (errorLog != null) {
                for (String error : errorLog) {
                    errorMessage.append("\n");
                    errorMessage.append(error);
                }
            }

            errorMessage.append(getString(R.string.dialogs_fileName_suffix, args.getString(DIALOG_ARGUMENT_FILE_NAME)));

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

    }

    public static class DialogFatalProblemWithXmlFileFragment extends DialogFragment {

        static DialogFatalProblemWithXmlFileFragment newInstance(String message, String filename) {
            DialogFatalProblemWithXmlFileFragment fragment = new DialogFatalProblemWithXmlFileFragment();
            Bundle args = new Bundle();
            args.putString(DIALOG_ARGUMENT_FATAL_MESSAGE, message);
            args.putString(DIALOG_ARGUMENT_FILE_NAME, filename);
            fragment.setArguments(args);
            return fragment;
        }

        @NonNull
        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            final FragmentActivity activity = getActivity();
            AlertDialog.Builder builder = new AlertDialog.Builder(activity);
            Bundle args = getArguments();

            StringBuilder errorMessage = new StringBuilder(args.getString(DIALOG_ARGUMENT_FATAL_MESSAGE));
            errorMessage.append(getString(R.string.fatalProblemWithXmlFileDialog_message_suffix));
            errorMessage.append(getString(R.string.dialogs_fileName_suffix, args.getString(DIALOG_ARGUMENT_FILE_NAME)));

            builder.setTitle(R.string.fatalProblemWithXmlFileDialog_title)
                   .setMessage(errorMessage)
                   .setCancelable(true)
                   .setPositiveButton(R.string.fatalProblemWithXmlFileDialog_button, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            Intent intent = new Intent(activity, FormatChooserActivity.class);

                            // We want to start this from the Activity, not from this Fragment,
                            // as the Fragment won't be active when it comes back.  See:
                            // http://stackoverflow.com/questions/10564474/wrong-requestcode-in-onactivityresult
                            activity.startActivityForResult(intent, CHOOSE_STYLE_REQUEST);
                        }
                    })
                    .setOnCancelListener(new DialogInterface.OnCancelListener() {
                        @Override
                        public void onCancel(DialogInterface dialog) {
                            activity.finish();
                        }
                    });

            return builder.create();
        }

    }

    public static class DialogSchemaTooNewFragment extends DialogFragment {

        static DialogSchemaTooNewFragment newInstance(String schemaUsed, String schemaSupported, String filename) {
            DialogSchemaTooNewFragment fragment = new DialogSchemaTooNewFragment();
            Bundle args = new Bundle();
            args.putString(DIALOG_ARGUMENT_SCHEMA_USED, schemaUsed);
            args.putString(DIALOG_ARGUMENT_SCHEMA_SUPPORTED, schemaSupported);
            args.putString(DIALOG_ARGUMENT_FILE_NAME, filename);
            fragment.setArguments(args);
            return fragment;
        }

        @NonNull
        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            final DebatingActivity activity = (DebatingActivity) getActivity();
            AlertDialog.Builder builder = new AlertDialog.Builder(activity);
            Bundle args = getArguments();

            String schemaUsed      = args.getString(DIALOG_ARGUMENT_SCHEMA_USED);
            String schemaSupported = args.getString(DIALOG_ARGUMENT_SCHEMA_SUPPORTED);

            String appVersion;
            try {
                appVersion = activity.getPackageManager().getPackageInfo(activity.getPackageName(), 0).versionName;
            } catch (NameNotFoundException e) {
                appVersion = "unknown";
            }

            StringBuilder message = new StringBuilder(getString(R.string.schemaTooNewDialog_message, schemaUsed, schemaSupported, appVersion));

            message.append(getString(R.string.dialogs_fileName_suffix, args.getString(DIALOG_ARGUMENT_FILE_NAME)));

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

            return builder.create();
        }

        @Override
        public void onDismiss(DialogInterface dialog) {
            super.onDismiss(dialog);
            ((DebatingActivity) getActivity()).showQueuedDialog();
        }
    }

    //******************************************************************************************
    // Private classes
    //******************************************************************************************

    private enum BackgroundColourArea {
        // These must match the values string array in the preference.xml file.
        // (We can pull strings from the resource automatically,
        // but we can't assign them to enums automatically.)
        DISABLED     ("disabled"),
        TOP_BAR_ONLY ("topBarOnly"),
        WHOLE_SCREEN ("wholeScreen");

        private final String key;

        BackgroundColourArea(String key) {
            this.key = key;
        }

        public static BackgroundColourArea toEnum(String key) {
            for (BackgroundColourArea value : BackgroundColourArea.values())
                if (key.equals(value.key))
                    return value;
            throw new IllegalArgumentException(String.format("There is no enumerated constant '%s'", key));
        }
    }

    private class ControlButtonSpec {
        int textResId;
        View.OnClickListener onClickListener;

        private ControlButtonSpec(int textResId, View.OnClickListener onClickListener) {
            this.textResId = textResId;
            this.onClickListener = onClickListener;
        }
    }

    private class ControlButtonChooseStyleOnClickListener implements View.OnClickListener {
        @Override
        public void onClick(View v) {
            Intent intent = new Intent(DebatingActivity.this, FormatChooserActivity.class);
            startActivityForResult(intent, CHOOSE_STYLE_REQUEST);
        }
    }

    private class ControlButtonNextDebatePhaseOnClickListener implements View.OnClickListener {
        @Override
        public void onClick(View v) {
            goToNextSpeech();
            updateGui();
        }
    }

    private class ControlButtonResetActiveDebatePhaseOnClickListener implements View.OnClickListener {
        @Override
        public void onClick(View v) {
            mDebateManager.resetActivePhase();
            updateGui();
        }
    }

    private class ControlButtonStartTimerOnClickListener implements View.OnClickListener {
        @Override
        public void onClick(View v) {
            mDebateManager.startTimer();
            updateGui();
            updateKeepScreenOn();
        }
    }

    private class ControlButtonStopTimerOnClickListener implements View.OnClickListener {
        @Override
        public void onClick(View v) {
            mDebateManager.stopTimer();
            updateGui();
            updateKeepScreenOn();
        }
    }

    private enum CountDirection {

        // These must match the values string array in the preference.xml file.
        // (We can pull strings from the resource automatically,
        // but we can't assign them to enums automatically.)
        COUNT_UP   ("alwaysUp"),
        COUNT_DOWN ("alwaysDown");

        private final String key;

        CountDirection(String key) {
            this.key = key;
        }

        public static CountDirection toEnum(String key) {
            for (CountDirection value : CountDirection.values())
                if (key.equals(value.key))
                    return value;
            throw new IllegalArgumentException(String.format("There is no enumerated constant '%s'", key));
        }
    }

    private class CurrentTimeOnLongClickListener implements OnLongClickListener {

        @Override
        public boolean onLongClick(View v) {
            editCurrentTimeStart();
            return true;
        }

    }

    private class DebateTimerDisplayOnClickListener implements OnClickListener {

        @Override
        public void onClick(View v) {
            editCurrentTimeFinish(true);
        }

    }

    private class DebateTimerDisplayOnPageChangeListener extends SimpleOnPageChangeListener {

        @Override
        public void onPageSelected(int position) {
            // Log.d(TAG, "onPageSelected for position " + position);

            // Enable the lock that prevents updateGui() from running while pages are changing.
            // This is necessary to prevent updateGui() from updating the wrong view after this
            // method is run (and the active phase index changed) and before
            // DebateTimerDisplayPagerAdapter#setPrimaryItem() is called (and the view pointer
            // updated).
            mChangingPages = true;

            if (mDebateManager != null)
                mDebateManager.setActivePhaseIndex(position);
            updateControls();
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

        private static final String TAG = "DebateTmrDispPagAdapt";

        private final HashMap<DebatePhaseTag, View> mViewsMap = new HashMap<>();
        private static final String NO_DEBATE_LOADED = "no_debate_loaded";

        @Override
        public void destroyItem(ViewGroup container, int position, Object object) {
            DebatePhaseTag dpt = (DebatePhaseTag) object;
            View view = mViewsMap.get(dpt);
            if (view == null) {
                Log.e(TAG, "Nothing found to destroy at position " + position + " - " + object.toString());
                return;
            }
            container.removeView(view);
            mViewsMap.remove(dpt);
        }

        @Override
        public int getCount() {
            if (mDebateManager == null) return 1;
            else return mDebateManager.getNumberOfPhases();
        }

        @Override
        public int getItemPosition(Object object) {

            // If it was the "no debate loaded" screen and there is now a debate loaded,
            // then the View no longer exists.  Likewise if there is no debate loaded and
            // there was anything but the "no debate loaded" screen.
            DebatePhaseTag tag = (DebatePhaseTag) object;
            if ((mDebateManager == null) != (NO_DEBATE_LOADED.equals(tag.specialTag)))
                return POSITION_NONE;

            // If it was "no debate loaded" and there is still no debate loaded, it's unchanged.
            if (mDebateManager == null && NO_DEBATE_LOADED.equals(tag.specialTag))
                return POSITION_UNCHANGED;

            // That covers all situations in which mDebateManager could be null. Just to be safe:
            assert mDebateManager != null;

            // If there's no messy debate format changing or loading, delegate this function to the
            // DebateManager.
            return mDebateManager.getPhaseIndexForTag((DebatePhaseTag) object);
        }

        @Override
        public Object instantiateItem(ViewGroup container, int position) {

            if (mDebateManager == null) {
                // Load the "no debate loaded" screen.
                Log.i(TAG, "No debate loaded");
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

            // OnTouchListeners
            v.setOnClickListener(new DebateTimerDisplayOnClickListener());
            v.findViewById(R.id.debateTimer_currentTime).setOnLongClickListener(new CurrentTimeOnLongClickListener());

            // Set the time picker to 24-hour time
            TimePicker currentTimePicker = (TimePicker) v.findViewById(R.id.debateTimer_currentTimePicker);
            currentTimePicker.setIs24HourView(true);

            // Set the POI timer OnClickListener
            Button poiTimerButton = (Button) v.findViewById(R.id.debateTimer_poiTimerButton);
            poiTimerButton.setOnClickListener(new PoiButtonOnClickListener());

            // Update the debate timer display
            long               time = mDebateManager.getPhaseCurrentTime(position);
            DebatePhaseFormat  dpf  = mDebateManager.getPhaseFormat(position);
            PeriodInfo         pi   = dpf.getPeriodInfoForTime(time);

            updateDebateTimerDisplay(v, dpf, pi,
                    mDebateManager.getPhaseName(position), time,
                    mDebateManager.getPhaseNextOvertimeBellTime(position));

            container.addView(v);

            // Retrieve a tag and take note of it.
            DebatePhaseTag tag = mDebateManager.getPhaseTagForIndex(position);
            mViewsMap.put(tag, v);

            return tag;

        }

        @Override
        public boolean isViewFromObject(View view, Object object) {
            DebatePhaseTag dpt = (DebatePhaseTag) object;
            return mViewsMap.containsKey(dpt) && (mViewsMap.get(dpt) == view);
        }

        @Override
        public void setPrimaryItem(ViewGroup container, int position, Object object) {

            // Log.d(TAG, "setPrimaryItem for position " + position);
            View original = mDebateTimerDisplay;

            // Note: There is no guarantee that mDebateTimerDisplay will in fact be a debate
            // timer display - it is just whatever view is currently being displayed.  Therefore,
            // other methods should check that mDebateTimerDisplay is in fact a debate timer
            // display (by comparing its ID to R.id.debateTimer_root) before working on it.
            DebatePhaseTag dpt = (DebatePhaseTag) object;
            mDebateTimerDisplay = mViewsMap.get(dpt);

            // Disable the lock that prevents updateGui() from running while the pages are
            // changing.
            mChangingPages = false;

            // This method seems to be called multiple times on each update.
            // To save unnecessary work (i.e. for performance), only run (the relatively-intensive)
            // updateGui if mDebateTimerDisplay has actually changed.
            if (original != mDebateTimerDisplay)
                updateGui();
        }

        /**
         * Refreshes all the background colours known to this {@link PagerAdapter}.
         * This should be called when a background colour user preference is changed, in a way
         * that requires all of the background colours in all {@link View}s known to be refreshed.
         * Before calling this method, <code>DebatingActivity.resetBackgroundColoursToTransparent()</code>
         * should be called to reset all of the other background colours to transparent.
         */
        public void refreshBackgroundColours() {
            if (mDebateManager == null) return;
            for (Entry<DebatePhaseTag, View> entry : mViewsMap.entrySet()) {
                int phaseIndex = mDebateManager.getPhaseIndexForTag(entry.getKey());
                DebatePhaseFormat dpf = mDebateManager.getPhaseFormat(phaseIndex);
                long time = mDebateManager.getPhaseCurrentTime(phaseIndex);
                PeriodInfo pi = dpf.getPeriodInfoForTime(time);
                int backgroundColour = getBackgroundColorFromPeriodInfo(dpf, pi);
                boolean overtime = time > dpf.getLength();
                int timeTextColour = getResources().getColor((overtime) ? R.color.overtimeTextColour : android.R.color.primary_text_dark);
                updateDebateTimerDisplayColours(entry.getValue(), timeTextColour, backgroundColour);
            }
        }

    }

    private class DebatingTimerFlashScreenListener implements FlashScreenListener {

        @Override
        public boolean begin() {
            boolean result;
            try {
                result = mFlashScreenSemaphore.tryAcquire(2, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                return false; // Don't bother with the flash screen any more
            }
            return result;
        }

        @Override
        public void done() {
            mFlashScreenSemaphore.release();
        }

        @Override
        public void flashScreenOff() {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    // Restore the original colours
                    // It takes a bit of brain-work to figure out what they should be.  We actually
                    // do this brain-work because the correct colours should be considered volatile
                    // - this timer can happen at any time so there is no guarantee they haven't
                    // changed since the last time we checked.
                    int textColour, backgroundColour;
                    Resources resources = getResources();
                    if (mDebateManager != null) {
                        DebatePhaseFormat dpf = mDebateManager.getActivePhaseFormat();
                        boolean overtime = mDebateManager.getActivePhaseCurrentTime() > dpf.getLength();
                        textColour = resources.getColor((overtime) ? R.color.overtimeTextColour : android.R.color.primary_text_dark);
                        backgroundColour = getBackgroundColorFromPeriodInfo(dpf, mDebateManager.getActivePhaseCurrentPeriodInfo());
                    } else {
                        textColour = resources.getColor(android.R.color.primary_text_dark);
                        backgroundColour = COLOUR_TRANSPARENT;
                    }

                    updateDebateTimerDisplayColours(mDebateTimerDisplay, textColour, backgroundColour);

                    // Set the background colour of the root view to be black again.
                    findViewById(R.id.mainScreen_rootView).setBackgroundColor(resources.getColor(android.R.color.black));
                }
            });
        }

        @Override
        public void flashScreenOn(final int colour) {

            runOnUiThread(new Runnable() {
                @Override
                public void run() {

                    // We need to figure out how to colour the text.
                    // Basically we want to colour the text to whatever the background colour is now.
                    // So the whole screen is coloured, it'll be the current background colour for
                    // the current period.  If not, it'll be black (make sure we don't make the
                    // text transparent though!).
                    int invertedTextColour;
                    if (mBackgroundColourArea == BackgroundColourArea.WHOLE_SCREEN && mDebateManager != null)
                        invertedTextColour = getBackgroundColorFromPeriodInfo(mDebateManager.getActivePhaseFormat(), mDebateManager.getActivePhaseCurrentPeriodInfo());
                    else
                        invertedTextColour = getResources().getColor(android.R.color.black);

                    // So we invert the text colour and set all background colours to transparent.
                    // Everything will be restored by flashScreenOff().
                    updateDebateTimerDisplayColours(mDebateTimerDisplay, invertedTextColour, COLOUR_TRANSPARENT);;

                    // Having completed preparations, set the background colour of the root view to
                    // flash the screen.
                    findViewById(R.id.mainScreen_rootView).setBackgroundColor(colour);
                }
            });
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
            mViewPager.getAdapter().notifyDataSetChanged();
        }
    }

    private class FatalXmlError extends Exception {

        private static final long serialVersionUID = -1774973645180296278L;

        public FatalXmlError(String detailMessage) {
            super(detailMessage);
        }

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

    //******************************************************************************************
    // Public methods
    //******************************************************************************************

    @Override
    public void onBackPressed() {

        // If no debate is loaded, exit.
        if (mDebateManager == null)
            super.onBackPressed();

        // If we're in editing mode, exit editing mode
        else if (mIsEditingTime)
            editCurrentTimeFinish(false);

        // If the timer is stopped AND it's not the first speaker, go back one speaker.
        // Note: We do not just leave this check to goToPreviousSpeaker(), because we want to do
        // other things if it's not in a state in which it could go to the previous speaker.
        else if (!mDebateManager.isInFirstPhase() && !mDebateManager.isRunning())
            goToPreviousSpeech();

        // Otherwise, behave normally (i.e. exit).
        // Note that if the timer is running, the service will remain present in the
        // background, so this doesn't stop a running timer.
        else super.onBackPressed();
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
        case R.id.mainScreen_menuItem_chooseFormat:
            Intent getStyleIntent = new Intent(this, FormatChooserActivity.class);
            getStyleIntent.putExtra(FormatChooserActivity.EXTRA_XML_FILE_NAME, mFormatXmlFileName);
            startActivityForResult(getStyleIntent, CHOOSE_STYLE_REQUEST);
            return true;
        case R.id.mainScreen_menuItem_resetDebate:
            if (mDebateManager == null) return true;
            resetDebate();
            View coordinator = findViewById(R.id.mainScreen_coordinator);
            if (coordinator != null)
                //noinspection WrongConstant
                Snackbar.make(coordinator, R.string.mainScreen_snackbar_resetDebate, SNACKBAR_DURATION_RESET_DEBATE).show();
            return true;
        case R.id.mainScreen_menuItem_settings:
            startActivity(new Intent(this, GlobalSettingsActivity.class));
            return true;
        case R.id.mainScreen_menuItem_ringBells:
            // Edit the preference, then apply the changes.
            // Don't fetch the current preference - if there is an inconsistency, we want the toggle
            // to reflect what this activity thinks silent mode is.
            Editor editor = PreferenceManager.getDefaultSharedPreferences(this).edit();
            editor.putBoolean(getResources().getString(R.string.pref_ringBells_key), !mBellsEnabled);
            boolean success = editor.commit(); // we want this to block until it returns
            if (success) applyPreferences(); // this will update mBellsEnabled
            return true;
        default:
            return super.onOptionsItemSelected(item);
        }
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        // show or hide the debate menu button
        MenuItem resetDebateItem = menu.findItem(R.id.mainScreen_menuItem_resetDebate);
        resetDebateItem.setVisible(mDebateManager != null);

        // display the appropriate bells icon
        MenuItem ringBellsItem = menu.findItem(R.id.mainScreen_menuItem_ringBells);
        ringBellsItem.setChecked(mBellsEnabled);
        ringBellsItem.setIcon((mBellsEnabled) ? R.drawable.ic_notifications_active_white_24dp : R.drawable.ic_notifications_off_white_24dp);

        return super.onPrepareOptionsMenu(menu);
    }

    //******************************************************************************************
    // Protected methods
    //******************************************************************************************

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == CHOOSE_STYLE_REQUEST) {

            switch (resultCode) {
                case RESULT_OK:
                    String filename = data.getStringExtra(FormatChooserActivity.EXTRA_XML_FILE_NAME);
                    if (filename != null) {
                        Log.v(TAG, "Got file name " + filename);
                        setXmlFileName(filename);
                        resetDebate();
                    }
                    break;

                case FormatChooserActivity.RESULT_UNCHANGED:
                    View coordinator = findViewById(R.id.mainScreen_coordinator);
                    if (coordinator != null)
                        Snackbar.make(coordinator, R.string.mainScreen_snackbar_formatUnchanged, Snackbar.LENGTH_SHORT);
                    break;

                case FormatChooserActivity.RESULT_ERROR:
                    Log.w(TAG, "Got error from FormatChooserActivity");
                    setXmlFileName(null);
                    if (mBinder != null) mBinder.releaseDebateManager();
                    mDebateManager = null;
                    updateGui();
                    invalidateOptionsMenu();
            }
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_debate);
        setSupportActionBar((Toolbar) findViewById(R.id.mainScreen_toolbar));

        mLeftControlButton       = (Button) findViewById(R.id.mainScreen_leftControlButton);
        mLeftCentreControlButton = (Button) findViewById(R.id.mainScreen_leftCentreControlButton);
        mCentreControlButton     = (Button) findViewById(R.id.mainScreen_centreControlButton);
        mRightControlButton      = (Button) findViewById(R.id.mainScreen_rightControlButton);
        mPlayBellButton          = (Button) findViewById(R.id.mainScreen_playBellButton);

        //
        // ViewPager
        mViewPager = (EnableableViewPager) findViewById(R.id.mainScreen_debateTimerViewPager);
        mViewPager.setAdapter(new DebateTimerDisplayPagerAdapter());
        mViewPager.addOnPageChangeListener(new DebateTimerDisplayOnPageChangeListener());
        mViewPager.setPageMargin(1);
        mViewPager.setPageMarginDrawable(R.drawable.divider);

        //
        // OnClickListeners
        mPlayBellButton.setOnClickListener(new PlayBellButtonOnClickListener());

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

        //
        // If there's been an update, show the changelog.
        SharedPreferences prefs = getPreferences(MODE_PRIVATE);
        Resources res = getResources();
        int thisChangelogVersion = res.getInteger(R.integer.changelogDialog_versionCode);
        int lastChangelogVersionShown = prefs.getInt(LAST_CHANGELOG_VERSION_SHOWN, 0);
        if (lastChangelogVersionShown < thisChangelogVersion) {
            if (isFirstInstall()) {
                // Don't show on the dialog on first install, but take note of the version.
                Editor editor = prefs.edit();
                editor.putInt(LAST_CHANGELOG_VERSION_SHOWN, thisChangelogVersion);
                editor.apply();
            } else {
                // The dialog will update the preference to the new version code.
                showDialog(new DialogChangelogFragment(), DIALOG_TAG_CHANGELOG);
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        unbindService(mConnection);

        if (mDebateManager == null || !mDebateManager.isRunning()) {
            Intent intent = new Intent(this, DebatingTimerService.class);
            stopService(intent);
            Log.i(TAG, "Timer is not running, stopped service");
        } else {
            Log.i(TAG, "Timer is running, keeping service alive");
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle bundle) {
        bundle.putString(BUNDLE_KEY_XML_FILE_NAME, mFormatXmlFileName);
        if (mDebateManager != null)
            mDebateManager.saveState(BUNDLE_KEY_DEBATE_MANAGER, bundle);
    }

    @Override
    protected void onStart() {
        super.onStart();
        restoreBinder();
        LocalBroadcastManager.getInstance(this).registerReceiver(mGuiUpdateBroadcastReceiver,
                new IntentFilter(DebatingTimerService.UPDATE_GUI_BROADCAST_ACTION));

        updateGui();
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (mBinder != null) {
            AlertManager am = mBinder.getAlertManager();
            if (am != null) am.activityStop();
        }
        LocalBroadcastManager.getInstance(this).unregisterReceiver(mGuiUpdateBroadcastReceiver);
    }


    //******************************************************************************************
    // Private methods
    //******************************************************************************************

    /**
     * Gets the preferences from the shared preferences file and applies them.
     */
    private void applyPreferences() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        boolean vibrateMode, overtimeBellsEnabled;
        boolean poiBuzzerEnabled, poiVibrateEnabled, prepTimerEnabled;
        int firstOvertimeBell, overtimeBellPeriod;
        String userCountDirectionValue, userPrepTimeCountDirectionValue, poiFlashScreenModeValue, backgroundColourAreaValue;
        FlashScreenMode flashScreenMode, poiFlashScreenMode;

        Resources res = getResources();

        final String TAG = "applyPreferences";

        try {

            // Boolean preference: Ring bells
            //  - Backwards compatibility measure
            // This changed from "silent mode" (true meaning no bells) to "ring bells" (true
            // meaning ring bells), i.e. it was inverted, in version 1.2, so there is backwards
            // compatibility to take care of.  Backward compatibility applies if (a) ringBells is
            // NOT present AND (b) silentMode IS present. In this case, retrieve the old silentMode
            // preference, delete it and write the opposite into the new ringBells preference. In
            // all other cases, just use the normal mechanism (i.e. retrieve if present, use
            // default if not).

            if (!prefs.contains(res.getString(R.string.pref_ringBells_key)) &&
                    prefs.contains(res.getString(R.string.pref_silentMode_key))) {
                boolean oldSilentMode = prefs.getBoolean(res.getString(R.string.pref_silentMode_key), false);
                mBellsEnabled = !oldSilentMode;
                SharedPreferences.Editor editor = prefs.edit();
                editor.putBoolean(res.getString(R.string.pref_ringBells_key), mBellsEnabled);
                editor.remove(res.getString(R.string.pref_silentMode_key));
                editor.apply();
                Log.i(TAG, String.format("applyPreferences: replaced silentMode (%b) with ringBells (%b)", oldSilentMode, mBellsEnabled));
            } else {
                // Normal mechanism
                mBellsEnabled = prefs.getBoolean(res.getString(R.string.pref_ringBells_key), res.getBoolean(R.bool.prefDefault_ringBells));
            }


            // The boolean preferences
            vibrateMode = prefs.getBoolean(res.getString(R.string.pref_vibrateMode_key), res.getBoolean(R.bool.prefDefault_vibrateMode));
            overtimeBellsEnabled = prefs.getBoolean(res.getString(R.string.pref_overtimeBellsEnable_key), res.getBoolean(R.bool.prefDefault_overtimeBellsEnable));

            mSpeechKeepScreenOn = prefs.getBoolean(res.getString(R.string.pref_keepScreenOn_key), res.getBoolean(R.bool.prefDefault_keepScreenOn));
            mPrepTimeKeepScreenOn = prefs.getBoolean(res.getString(R.string.pref_prepTimer_keepScreenOn_key), res.getBoolean(R.bool.prefDefault_prepTimer_keepScreenOn));

            mPoiTimerEnabled = prefs.getBoolean(res.getString(R.string.pref_poiTimer_enable_key), res.getBoolean(R.bool.prefDefault_poiTimer_enable));
            poiBuzzerEnabled = prefs.getBoolean(res.getString(R.string.pref_poiTimer_buzzerEnable_key), res.getBoolean(R.bool.prefDefault_poiTimer_buzzerEnable));
            poiVibrateEnabled = prefs.getBoolean(res.getString(R.string.pref_poiTimer_vibrateEnable_key), res.getBoolean(R.bool.prefDefault_poiTimer_vibrateEnable));

            prepTimerEnabled = prefs.getBoolean(res.getString(R.string.pref_prepTimer_enable_key), res.getBoolean(R.bool.prefDefault_prepTimer_enable));

            // Overtime bell integers
            if (overtimeBellsEnabled) {
                firstOvertimeBell  = prefs.getInt(res.getString(R.string.pref_firstOvertimeBell_key), res.getInteger(R.integer.prefDefault_firstOvertimeBell));
                overtimeBellPeriod = prefs.getInt(res.getString(R.string.pref_overtimeBellPeriod_key), res.getInteger(R.integer.prefDefault_overtimeBellPeriod));
            } else {
                firstOvertimeBell = 0;
                overtimeBellPeriod = 0;
            }

            // List preference: POI flash screen mode
            poiFlashScreenModeValue = prefs.getString(res.getString(R.string.pref_poiTimer_flashScreenMode_key), res.getString(R.string.prefDefault_poiTimer_flashScreenMode));
            poiFlashScreenMode = FlashScreenMode.toEnum(poiFlashScreenModeValue);

            // List preference: Count direction
            userCountDirectionValue = prefs.getString(res.getString(R.string.pref_countDirection_key), res.getString(R.string.prefDefault_countDirection));
            mCountDirection = CountDirection.toEnum(userCountDirectionValue);

            // List preference: Count direction for prep time
            userPrepTimeCountDirectionValue = prefs.getString(res.getString(R.string.pref_prepTimer_countDirection_key), res.getString(R.string.prefDefault_prepTimer_countDirection));
            mPrepTimeCountDirection = CountDirection.toEnum(userPrepTimeCountDirectionValue);

            // List preference: Background colour area
            BackgroundColourArea oldBackgroundColourArea = mBackgroundColourArea;
            backgroundColourAreaValue = prefs.getString(res.getString(R.string.pref_backgroundColourArea_key), res.getString(R.string.prefDefault_backgroundColourArea));
            mBackgroundColourArea = BackgroundColourArea.toEnum(backgroundColourAreaValue);
            if (oldBackgroundColourArea != mBackgroundColourArea) {
                Log.v(TAG, "background colour preference changed - refreshing");
                resetBackgroundColoursToTransparent();
                ((DebateTimerDisplayPagerAdapter) mViewPager.getAdapter()).refreshBackgroundColours();
            }

            // List preference: Flash screen mode
            String flashScreenModeValue;
            flashScreenModeValue = prefs.getString(res.getString(R.string.pref_flashScreenMode_key), res.getString(R.string.prefDefault_flashScreenMode));
            flashScreenMode = FlashScreenMode.toEnum(flashScreenModeValue);

        } catch (ClassCastException e) {
            Log.e(TAG, "caught ClassCastException!");
            return;
        }

        if (mDebateManager != null) {
            mDebateManager.setOvertimeBells(firstOvertimeBell, overtimeBellPeriod);
            mDebateManager.setPrepTimeEnabled(prepTimerEnabled);
            applyPrepTimeBells();

            // This is necessary if the debate structure has changed, i.e. if prep time has been
            // enabled or disabled.
            mViewPager.getAdapter().notifyDataSetChanged();

        } else {
            Log.v(TAG, "Couldn't restore overtime bells, mDebateManager doesn't yet exist");
        }

        if (mBinder != null) {
            AlertManager am = mBinder.getAlertManager();

            // Volume control stream is linked to ring bells mode
            am.setBellsEnabled(mBellsEnabled);
            setVolumeControlStream((mBellsEnabled) ? AudioManager.STREAM_MUSIC : AudioManager.STREAM_RING);

            am.setVibrateMode(vibrateMode);
            am.setFlashScreenMode(flashScreenMode);

            am.setPoiBuzzerEnabled(poiBuzzerEnabled);
            am.setPoiVibrateEnabled(poiVibrateEnabled);
            am.setPoiFlashScreenMode(poiFlashScreenMode);

            this.updateKeepScreenOn();

            Log.v(TAG, "successfully applied");
        } else {
            Log.v(TAG, "Couldn't restore AlertManager preferences; mBinder doesn't yet exist");
        }

        invalidateOptionsMenu();
        updateGui();
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

        InputStream is;
        DebateFormat df;
        FormatXmlFilesManager filesManager = new FormatXmlFilesManager(this);

        try {
            is = filesManager.open(filename);
        } catch (IOException e) {
            throw new FatalXmlError(getString(R.string.fatalProblemWithXmlFileDialog_message_cannotFind), e);
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
                is = filesManager.open(filename);
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
            DialogFragment fragment = DialogSchemaTooNewFragment.newInstance(dfbfx.getSchemaVersion(), dfbfx.getSupportedSchemaVersion(), filename);
            showBlockingDialog(fragment, DIALOG_TAG_SCHEMA_TOO_NEW);
        }

        if (df.numberOfSpeeches() == 0)
            throw new FatalXmlError(getString(
                    R.string.fatalProblemWithXmlFileDialog_message_noSpeeches));

        if (dfbfx.hasErrors()) {
            DialogFragment fragment = DialogErrorsWithXmlFileFragment.newInstance(dfbfx.getErrorLog(), mFormatXmlFileName);
            queueDialog(fragment, DIALOG_TAG_ERRORS_WITH_XML);
        }

        return df;
    }

    /**
     * Finishes editing the current time and restores the GUI to its prior state.
     * @param save true if the edited time should become the new current time, false if it should
     * be discarded.
     */
    private void editCurrentTimeFinish(boolean save) {

        TimePicker currentTimePicker = (TimePicker) mDebateTimerDisplay.findViewById(R.id.debateTimer_currentTimePicker);

        if (currentTimePicker == null) {
            Log.e(TAG, "editCurrentTimeFinish: currentTimePicker was null");
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
            Log.e(TAG, "editCurrentTimeFinish: currentTimePicker was null");
            return;
        }

        long currentTime = mDebateManager.getActivePhaseCurrentTime();

        // Invert the time if in count-down mode
        currentTime = subtractFromSpeechLengthIfCountingDown(currentTime);

        // Limit to the allowable time range
        View coordinator = findViewById(R.id.mainScreen_coordinator);
        if (currentTime < 0) {
            currentTime = 0;
            if (coordinator != null)
                Snackbar.make(coordinator, R.string.mainScreen_snackbar_editTextDiscardChangesInfo_limitedBelow, Snackbar.LENGTH_LONG).show();
        }
        if (currentTime >= 24 * 60) {
            currentTime = 24 * 60 - 1;
            if (coordinator != null)
                Snackbar.make(coordinator, R.string.mainScreen_snackbar_editTextDiscardChangesInfo_limitedAbove, Snackbar.LENGTH_LONG).show();
        }

        // We're using this in hours and minutes, not minutes and seconds
        currentTimePicker.setCurrentHour((int) (currentTime / 60));
        currentTimePicker.setCurrentMinute((int) (currentTime % 60));

        updateGui();

        // If we had to limit the time, display a helpful/apologetic message informing the user
        // of how to discard their changes, since they can't recover the time.

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
    private CountDirection getCountDirection(DebatePhaseFormat dpf) {
        if (dpf.isPrep())
            return mPrepTimeCountDirection;
        else
            return mCountDirection;
    }

    /**
     * @param dpf the {@link DebatePhaseFormat}
     * @param pi the current {@link PeriodInfo}
     * @return the appropriate background colour
     */
    private int getBackgroundColorFromPeriodInfo(DebatePhaseFormat dpf, PeriodInfo pi) {
        Integer backgroundColour = pi.getBackgroundColor();

        if (backgroundColour == null)
            backgroundColour = getResources().getColor((dpf.isPrep()) ? R.color.prepTimeBackgroundColour : android.R.color.background_dark);

        return backgroundColour;
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
    }

    private void initialiseDebate() {
        if (mFormatXmlFileName == null) {
            Log.w(TAG, "Tried to initialise debate with null file");
            return;
        }

        mDebateManager = mBinder.getDebateManager();
        if (mDebateManager == null) {

            DebateFormat df;
            try {
                df = buildDebateFromXml(mFormatXmlFileName);
            } catch (FatalXmlError e) {
                DialogFragment fragment = DialogFatalProblemWithXmlFileFragment.newInstance(e.getMessage(), mFormatXmlFileName);
                queueDialog(fragment, DIALOG_TAG_FATAL_PROBLEM);

                // We still need to notify of a data set change when there ends up being no
                // debate format
                mViewPager.getAdapter().notifyDataSetChanged();
                return;
            }

            mDebateManager = mBinder.createDebateManager(df);

            // We only restore the state if there wasn't an existing debate, i.e. if the service
            // wasn't already running, and if the debate format stored in the saved instance state
            // matches the debate format we're using now.  Also, only do this once (so set it to
            // null once restored).
            if (mLastStateBundle != null) {
                String xmlFileName = mLastStateBundle.getString(BUNDLE_KEY_XML_FILE_NAME);
                if (xmlFileName != null && xmlFileName.equals(mFormatXmlFileName))
                    mDebateManager.restoreState(BUNDLE_KEY_DEBATE_MANAGER, mLastStateBundle);
                mLastStateBundle = null;
            }
        }

        mViewPager.getAdapter().notifyDataSetChanged();
        mViewPager.setCurrentItem(mDebateManager.getActivePhaseIndex(), false);
        applyPreferences();
    }

    /**
     * Returns whether or not this is the user's first time opening the app.
     * @return true if it is the first time, false otherwise.
     */
    private boolean isFirstInstall() {
        PackageInfo info;
        try {
            info = getPackageManager().getPackageInfo(getPackageName(), 0);
        } catch (NameNotFoundException e) {
            Log.e(TAG, "isFirstInstall: Can't find package info, assuming it is the first install");
            return true;
        }

        Log.v(TAG, String.format("isFirstInstall: %d vs %d", info.firstInstallTime, info.lastUpdateTime));
        return info.firstInstallTime == info.lastUpdateTime;
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
     * @param fragment the {@link DialogFragment} that would be passed to showDialog()
     * @param tag the tag that would be passed to showDialog()
     */
    private void queueDialog(DialogFragment fragment, String tag) {
        if (!mDialogBlocking) {
            showDialog(fragment, tag);
            return;
        }

        mDialogWaiting           = true;
        mDialogFragmentInWaiting = fragment;
        mDialogTagInWaiting      = tag;
    }

    /**
     * Resets the background colour to the default.
     * This should be called whenever the background colour preference is changed, as <code>updateGui()</code>
     * doesn't automatically do this (for efficiency). You should call <code>updateGui()</code> as immediately as
     * practicable after calling this.
     */
    private void resetBackgroundColoursToTransparent() {
        for (int i = 0; i < mViewPager.getChildCount(); i++) {
            View v = mViewPager.getChildAt(i);
            if (v.getId() == R.id.debateTimer_root) {
                v.setBackgroundColor(COLOUR_TRANSPARENT);
                View speechNameText = v.findViewById(R.id.debateTimer_speechNameText);
                View periodDescriptionText = v.findViewById(R.id.debateTimer_periodDescriptionText);
                speechNameText.setBackgroundColor(COLOUR_TRANSPARENT);
                periodDescriptionText.setBackgroundColor(COLOUR_TRANSPARENT);
            }
        }
    }

    private void resetDebate() {
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

        // Always apply preferences after restoring the binder, as some of the preferences
        // apply to the binder.
        applyPreferences();
    }

    /**
     *  Sets up a single button
     */
    private void setButton(Button button, ControlButtonSpec spec) {
        if (spec == null)
            button.setVisibility(View.GONE);
        else {
            button.setText(spec.textResId);
            button.setOnClickListener(spec.onClickListener);
            button.setVisibility(View.VISIBLE);
        }
    }

    /**
     * Sets up the buttons according to the {@link ControlButtonSpec}s specified.  If the centre
     * button is blank and the left and right are not, a "left-centre" button is used in place
     * of the left button; <i>i.e.</i> the left button has "double weight" of sorts.
     * @param left the {@link ControlButtonSpec} for the left button
     * @param centre the {@link ControlButtonSpec} for the centre button
     * @param right the {@link ControlButtonSpec} for the right button
     */
    private void setButtons(ControlButtonSpec left, ControlButtonSpec centre, ControlButtonSpec right) {


        if (left != null && centre == null && right != null) {
            setButton(mLeftCentreControlButton, left);
            setButton(mLeftControlButton, null);
            setButton(mCentreControlButton, null);
        } else {
            setButton(mLeftCentreControlButton, null);
            setButton(mLeftControlButton, left);
            setButton(mCentreControlButton, centre);
        }

        setButton(mRightControlButton, right);
    }

    /**
     * Enables or disables all of the control buttons (except for the "Bell" button).  If
     * <code>mDebateManager</code> is <code>null</code>, this does nothing.
     * @param enable <code>true</code> to enable, <code>false</code> to disable
     */
    private void setButtonsEnable(boolean enable) {
        if (mDebateManager == null) return;
        mLeftControlButton.setEnabled(enable);
        mLeftCentreControlButton.setEnabled(enable);
        mCentreControlButton.setEnabled(enable);
        // Disable the [Next Speaker] button if there are no more speakers
        mRightControlButton.setEnabled(enable && !mDebateManager.isInLastPhase());
    }

    private void setXmlFileName(String filename) {
        mFormatXmlFileName = filename;
        SharedPreferences sp = getPreferences(MODE_PRIVATE);
        Editor editor = sp.edit();
        if (filename != null)
            editor.putString(PREFERENCE_XML_FILE_NAME, filename);
        else
            editor.remove(PREFERENCE_XML_FILE_NAME);
        editor.apply();
    }


    private void showBlockingDialog(DialogFragment fragment, String tag) {
        mDialogBlocking = true;
        showDialog(fragment, tag);
    }

    /**
     * Shows the dialog given, allowing state loss as a workaround for a bug in Android
     * (possibly issue #7132432)
     *
     * <p>For more information see:</p>
     * <ul>
     * <li>https://github.com/android/platform_frameworks_support/commit/4ccc001f3f883190ac8d900c4f69d71fda94690e</li>
     * <li>http://stackoverflow.com/questions/12105064/actions-in-onactivityresult-and-error-can-not-perform-this-action-after-onsavei</li>
     * <li>http://stackoverflow.com/questions/14262312/java-lang-illegalstateexception-can-not-perform-this-action-after-onsaveinstanc</li>
     * </ul>
     *
     * @param fragment a {@link DialogFragment} instance
     * @param tag the tag for the fragment
     */
    private void showDialog(DialogFragment fragment, String tag) {
        FragmentTransaction ft = getSupportFragmentManager().beginTransaction();
        ft.add(fragment, tag);
        ft.commitAllowingStateLoss();
    }

    /**
     * Shows the currently-queued dialog if there is one; does nothing otherwise.  Dialogs that
     * could block other dialogs must call this method on dismissal.
     */
    private void showQueuedDialog() {
        if (mDialogWaiting) {
            showDialog(mDialogFragmentInWaiting, mDialogTagInWaiting);
            mDialogBlocking = false;
            mDialogWaiting = false;
            mDialogFragmentInWaiting = null;
            mDialogTagInWaiting = null;
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

    /**
     *  Updates the buttons according to the current status of the debate
     *  The buttons are allocated as follows:
     *  When at start:          [Start] [Next]
     *  When running:           [Stop]
     *  When stopped by user:   [Resume] [Restart] [Next]
     *  When stopped by alarm:  [Resume]
     *  The [Bell] button always is on the right of any of the above three buttons.
     */
    private void updateControls() {
        if (mDebateTimerDisplay == null) return;

        if (mDebateManager != null && mDebateTimerDisplay.getId() == R.id.debateTimer_root) {

            View currentTimeText   = mDebateTimerDisplay.findViewById(R.id.debateTimer_currentTime);
            View currentTimePicker = mDebateTimerDisplay.findViewById(R.id.debateTimer_currentTimePicker);

            // If it's the last speaker, don't show a "next speaker" button.
            // Show a "restart debate" button instead.
            switch (mDebateManager.getTimerStatus()) {
            case NOT_STARTED:
                setButtons(CONTROL_BUTTON_START_TIMER, null, CONTROL_BUTTON_NEXT_PHASE);
                break;
            case RUNNING:
                setButtons(CONTROL_BUTTON_STOP_TIMER, null, null);
                break;
            case STOPPED_BY_BELL:
                setButtons(CONTROL_BUTTON_RESUME_TIMER, null, null);
                break;
            case STOPPED_BY_USER:
                setButtons(CONTROL_BUTTON_RESUME_TIMER, CONTROL_BUTTON_RESET_TIMER, CONTROL_BUTTON_NEXT_PHASE);
                break;
            }

            currentTimeText  .setVisibility((mIsEditingTime) ? View.GONE : View.VISIBLE);
            currentTimePicker.setVisibility((mIsEditingTime) ? View.VISIBLE : View.GONE);

            setButtonsEnable(!mIsEditingTime);
            currentTimeText.setLongClickable(!mDebateManager.isRunning());
            mViewPager.setPagingEnabled(!mIsEditingTime && !mDebateManager.isRunning());

        } else {
            // If no debate is loaded, show only one control button, which leads the user to
            // choose a style. (Keep the play bell button enabled.)
            setButtons(CONTROL_BUTTON_CHOOSE_STYLE, null, null);
            mLeftControlButton.setEnabled(true);
            mCentreControlButton.setEnabled(false);
            mRightControlButton.setEnabled(false);

            // This seems counter-intuitive, but we enable paging if there is no debate loaded,
            // as there is only one page anyway, and this way the "scrolled to the limit"
            // indicators appear on the screen.
            mViewPager.setPagingEnabled(true);
        }

        // Show or hide the [Bell] button
        updatePlayBellButton();
    }

    /**
     * Updates the debate timer display with the current active debate phase information.
     */
    private void updateDebateTimerDisplay() {
        if (mDebateManager == null) {
            Log.w("updateDebateTmrDisplay", "mDebateManager was null");
            mViewPager.getAdapter().notifyDataSetChanged();
            return;
        }

        updateDebateTimerDisplay(mDebateTimerDisplay,
                mDebateManager.getActivePhaseFormat(),
                mDebateManager.getActivePhaseCurrentPeriodInfo(),
                mDebateManager.getActivePhaseName(),
                mDebateManager.getActivePhaseCurrentTime(),
                mDebateManager.getActivePhaseNextOvertimeBellTime());
    }

    /**
     * Updates a debate timer display with relevant information.
     * @param debateTimerDisplay a {@link View} which should normally be the <code>RelativeLayout</code> in debate_timer_display.xml.
     * @param dpf the {@link DebatePhaseFormat} to be displayed
     * @param pi the {@link PeriodInfo} to be displayed, should be the current one
     * @param phaseName the name of the debate phase
     * @param time the current time in the debate phase
     * @param nextOvertimeBellTime the next overtime bell in the debate phase
     */
    private void updateDebateTimerDisplay(View debateTimerDisplay, DebatePhaseFormat dpf,
            PeriodInfo pi, String phaseName, long time, Long nextOvertimeBellTime) {

        // Make sure it makes sense to run this method now

        if (debateTimerDisplay == null) {
            Log.w("updateDebateTmrDisplay", "debateTimerDisplay was null");
            return;
        }
        if (debateTimerDisplay.getId() != R.id.debateTimer_root) {
            Log.w("updateDebateTmrDisplay", "debateTimerDisplay was not the debate timer display");
            return;
        }

        // If it passed all those checks, populate the timer display

        TextView periodDescriptionText = (TextView) debateTimerDisplay.findViewById(R.id.debateTimer_periodDescriptionText);
        TextView speechNameText = (TextView) debateTimerDisplay.findViewById(R.id.debateTimer_speechNameText);
        TextView currentTimeText = (TextView) debateTimerDisplay.findViewById(R.id.debateTimer_currentTime);
        TextView infoLineText = (TextView) debateTimerDisplay.findViewById(R.id.debateTimer_informationLine);

        // The information at the top of the screen
        speechNameText.setText(phaseName);
        periodDescriptionText.setText(pi.getDescription());

        // Take count direction into account for display
        long timeToShow = subtractFromSpeechLengthIfCountingDown(time, dpf);

        currentTimeText.setText(secsToTextSigned(timeToShow));

        boolean overtime = time > dpf.getLength();

        // Colours
        int currentTimeTextColor = getResources().getColor((overtime) ? R.color.overtimeTextColour : android.R.color.primary_text_dark);
        int backgroundColour = getBackgroundColorFromPeriodInfo(dpf, pi);

        // If we're updating the current display (as opposed to an inactive debate phase), then
        // don't update colours if there is a flash screen in progress.
        boolean displayIsActive = debateTimerDisplay == mDebateTimerDisplay;
        boolean semaphoreAcquired = displayIsActive && mFlashScreenSemaphore.tryAcquire();

        // If not current display, or we got the semaphore, we're good to go.  If not, don't bother.
        if (!displayIsActive || semaphoreAcquired) {
            updateDebateTimerDisplayColours(debateTimerDisplay, currentTimeTextColor, backgroundColour);
            if (semaphoreAcquired) mFlashScreenSemaphore.release();
        }

        // Construct the line that goes at the bottom
        StringBuilder infoLine = new StringBuilder();

        // First, length...
        long length = dpf.getLength();
        String lengthStr;
        if (length % 60 == 0)
            lengthStr = getResources().getQuantityString(R.plurals.mainScreen_timeInMinutes, (int) (length / 60), length / 60);
        else
            lengthStr = DateUtils.formatElapsedTime(length);

        int finalTimeTextUnformattedResid = (dpf.isPrep()) ? R.string.mainScreen_prepTimeLength : R.string.mainScreen_speechLength;
        infoLine.append(String.format(this.getString(finalTimeTextUnformattedResid),
                lengthStr));

        if (dpf.isPrep()) {
            PrepTimeFormat ptf = (PrepTimeFormat) dpf;
            if (ptf.isControlled())
                infoLine.append(getString(R.string.mainScreen_prepTimeControlledIndicator));
        }

        // ...then, if applicable, bells
        ArrayList<BellInfo> currentSpeechBells = dpf.getBellsSorted();
        Iterator<BellInfo> currentSpeechBellsIter = currentSpeechBells.iterator();

        if (overtime) {
            // show next overtime bell (don't bother with list of bells anymore)
            if (nextOvertimeBellTime == null)
                infoLine.append(getString(R.string.mainScreen_bellsList_noOvertimeBells));
            else {
                long timeToDisplay = subtractFromSpeechLengthIfCountingDown(nextOvertimeBellTime, dpf);
                infoLine.append(getString(R.string.mainScreen_bellsList_nextOvertimeBell,
                        secsToTextSigned(timeToDisplay)));
            }

        } else if (currentSpeechBellsIter.hasNext()) {
            // Convert the list of bells into a string.
            StringBuilder bellsStr = new StringBuilder();

            while (currentSpeechBellsIter.hasNext()) {
                BellInfo bi = currentSpeechBellsIter.next();
                long bellTime = subtractFromSpeechLengthIfCountingDown(bi.getBellTime(), dpf);
                bellsStr.append(DateUtils.formatElapsedTime(bellTime));
                if (bi.isPauseOnBell())
                    bellsStr.append(getString(R.string.mainScreen_pauseOnBellIndicator));
                if (bi.isSilent())
                    bellsStr.append(getString(R.string.mainScreen_silentBellIndicator));
                if (currentSpeechBellsIter.hasNext())
                    bellsStr.append(", ");
            }

            infoLine.append(getResources().getQuantityString(R.plurals.mainScreen_bellsList_normal, currentSpeechBells.size(), bellsStr));

        } else {
            infoLine.append(getString(R.string.mainScreen_bellsList_noBells));
        }

        infoLineText.setText(infoLine.toString());

        // Update the POI timer button
        updatePoiTimerButton(debateTimerDisplay, dpf);

    }

    /**
     * @param view a {@link View} which should often be the <code>RelativeLayout</code> in debate_timer_display.xml,
     * except in cases where no debate is loaded or something like that.
     * @param timeTextColour the text colour to use for the current time
     * @param backgroundColour the colour to use for the background
     */
    private void updateDebateTimerDisplayColours(View view, int timeTextColour, int backgroundColour) {

        boolean viewIsDebateTimerDisplay = view.getId() == R.id.debateTimer_root;

        switch (mBackgroundColourArea) {
        case TOP_BAR_ONLY:
            if (viewIsDebateTimerDisplay) {
                // These would only be expected to exist if the view given is the debate timer display
                view.findViewById(R.id.debateTimer_speechNameText).setBackgroundColor(backgroundColour);
                view.findViewById(R.id.debateTimer_periodDescriptionText).setBackgroundColor(backgroundColour);
            }
            break;
        case WHOLE_SCREEN:
            view.setBackgroundColor(backgroundColour);
            break;
        case DISABLED:
        	// Do nothing
        }

        // This would only be expected to exist if the view given is the debate timer display
        if (viewIsDebateTimerDisplay)
            ((TextView) view.findViewById(R.id.debateTimer_currentTime)).setTextColor(timeTextColour);
    }

    /**
     * Updates the GUI (in the general case).
     */
    private void updateGui() {
        if (mChangingPages) {
            Log.d(TAG, "Changing pages, don't do updateGui");
            return;
        }

        updateDebateTimerDisplay();
        updateControls();

        if (mDebateManager != null)
            setTitle(getString(R.string.activityName_Debating_withFormat, mDebateManager.getDebateFormatName()));
        else
            setTitle(R.string.activityName_Debating_withoutFormat);

    }

    /**
     * Update the "keep screen on" flag according to
     * <ul>
     *    <li>whether it is prep time or a speech, and</li>
     *    <li>whether the timer is currently running.</li>
     * </ul>
     * This method should be called whenever
     * <ul>
     *     <li>the user preference is applied, and</li>
     *     <li>the timer starts or stops, or might start or stop.</li>
     * </ul>
     */
    private void updateKeepScreenOn() {
        boolean relevantKeepScreenOn;

        if (mDebateManager != null && mDebateManager.getActivePhaseFormat().isPrep())
            relevantKeepScreenOn = mPrepTimeKeepScreenOn;
        else
            relevantKeepScreenOn = mSpeechKeepScreenOn;

        if (relevantKeepScreenOn && mDebateManager != null && mDebateManager.isRunning())
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        else
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    private void updatePlayBellButton() {
        if (mBinder != null)
            mPlayBellButton.setVisibility((mBinder.getAlertManager().isBellsEnabled()) ? View.VISIBLE : View.GONE);
    }

    /**
     * @param debateTimerDisplay the {@link View} to be updated
     * @param dpf the {@link DebatePhaseFormat} relevant for this <code>debateTimerDisplay</code>
     */
    private void updatePoiTimerButton(View debateTimerDisplay, DebatePhaseFormat dpf) {
        Button poiButton = (Button) debateTimerDisplay.findViewById(R.id.debateTimer_poiTimerButton);

        // Display only when user has POI timer enabled, and a debate is loaded and the current
        // speech has POIs in it.
        if (mPoiTimerEnabled && dpf.getClass() == SpeechFormat.class && ((SpeechFormat) dpf).hasPoisAllowedSomewhere()) {
            poiButton.setVisibility(View.VISIBLE);

            // If POIs are currently active, enable the button
            if (mDebateManager != null && mDebateManager.isPoisActive()) {
                poiButton.setEnabled(mDebateManager.isRunning());

                Long poiTime = mDebateManager.getCurrentPoiTime();
                if (poiTime == null)
                    poiButton.setText(R.string.mainScreen_poiTimer_buttonText);
                else
                    //noinspection AndroidLintDefaultLocale
                    poiButton.setText(String.format("%d", poiTime));

            // Otherwise, disable it
            } else {
                poiButton.setText(R.string.mainScreen_poiTimer_buttonText);
                poiButton.setEnabled(false);
            }

        // Otherwise, hide the button
        } else {
            poiButton.setVisibility(View.GONE);
        }
    }

    /**
     * Converts a number of seconds to a String in the format 00:00, or +00:00 if the time
     * given is negative.  (Note: A <i>plus</i> sign is used for <i>negative</i> numbers; this
     * indicates overtime.)
     * @param time a time in seconds
     * @return the String
     */
    private String secsToTextSigned(long time) {
        if (time >= 0)
            return DateUtils.formatElapsedTime(time);
        else
            return getResources().getString(R.string.mainScreen_overtimeFormat, DateUtils.formatElapsedTime(-time));
    }
}
