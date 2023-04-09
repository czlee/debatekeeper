/*
 * Copyright (C) 2012-2021 Phillip Cao, Chuan-Zheng Lee
 *
 * This file is part of the Debatekeeper app, which is licensed under the GNU
 * General Public Licence version 3 (GPLv3).  You can redistribute and/or modify
 * it under the terms of the GPLv3, and you must not use this file except in
 * compliance with the GPLv3.
 *
 * This app is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR
 * A PARTICULAR PURPOSE.  See the GNU General Public Licence for more details.
 *
 * You should have received a copy of the GNU General Public Licence along with
 * this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package net.czlee.debatekeeper;

import static android.content.Context.INPUT_METHOD_SERVICE;
import static android.content.Context.MODE_PRIVATE;

import android.Manifest;
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
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.res.Resources;
import android.media.AudioManager;
import android.net.Uri;
import android.nfc.NfcAdapter;
import android.nfc.NfcEvent;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.text.Html;
import android.text.Spanned;
import android.text.method.LinkMovementMethod;
import android.util.Log;
import android.util.Pair;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.TextView;
import android.widget.TimePicker;

import androidx.activity.OnBackPressedCallback;
import androidx.activity.OnBackPressedDispatcher;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.fragment.app.FragmentResultListener;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.navigation.NavDirections;
import androidx.navigation.fragment.NavHostFragment;
import androidx.viewbinding.ViewBinding;
import androidx.viewpager.widget.PagerAdapter;
import androidx.viewpager.widget.ViewPager;

import com.google.android.material.snackbar.Snackbar;

import net.czlee.debatekeeper.AlertManager.FlashScreenMode;
import net.czlee.debatekeeper.databinding.DebateLoadErrorBinding;
import net.czlee.debatekeeper.databinding.DebateTimerDisplayBinding;
import net.czlee.debatekeeper.databinding.DialogWithDontShowBinding;
import net.czlee.debatekeeper.databinding.FragmentDebateBinding;
import net.czlee.debatekeeper.debateformat.BellInfo;
import net.czlee.debatekeeper.debateformat.DebateFormat;
import net.czlee.debatekeeper.debateformat.DebateFormatBuilderFromXml;
import net.czlee.debatekeeper.debateformat.DebateFormatBuilderFromXmlForSchema2;
import net.czlee.debatekeeper.debateformat.DebateFormatFieldExtractor;
import net.czlee.debatekeeper.debateformat.DebatePhaseFormat;
import net.czlee.debatekeeper.debateformat.PeriodInfo;
import net.czlee.debatekeeper.debateformat.PrepTimeFormat;
import net.czlee.debatekeeper.debateformat.SpeechFormat;
import net.czlee.debatekeeper.debatemanager.DebateManager;

import org.xml.sax.SAXException;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;


/**
 * This is the main fragment for the Debatekeeper application, showing the actual timer.
 *
 * Most of this code used to be in DebatingActivity, before this app was written into a
 * single-activity structure, moving off the activities into fragments. The authorship information
 * reflects the original DebatingActivity class.
 *
 * @author Phillip Cao
 * @author Chuan-Zheng Lee
 * @since 2012-04-05
 */
public class DebatingTimerFragment extends Fragment {

    private static final String TAG = "DebatingTimerFragment";

    private DebatingTimerService.DebatingTimerServiceBinder mServiceBinder;
    private final ServiceConnection mServiceConnection = new DebatingTimerServiceConnection();

    private DebateManager    mDebateManager;
    private Spanned          mDebateLoadError = null;
    private Bundle           mLastStateBundle;
    private boolean          mIsEditingTime = false;
    private boolean          mIsOpeningFormatChooser = false;
    private final Semaphore  mFlashScreenSemaphore = new Semaphore(1, true);

    private DebateTimerDisplayBinding mTimerDisplay;  // normally but not always a DebateTimerDisplayBinding
    private EnableableViewPager       mViewPager;
    private boolean                   mIsChangingPages;

    private FragmentDebateBinding mViewBinding;

    private String               mFormatXmlFileName      = null;
    private CountDirection       mCountDirection         = CountDirection.COUNT_UP;
    private CountDirection       mPrepTimeCountDirection = CountDirection.COUNT_DOWN;
    private BackgroundColourArea mBackgroundColourArea   = BackgroundColourArea.WHOLE_SCREEN;
    private boolean              mPoiTimerEnabled        = true;
    private boolean              mBellsEnabled           = true;
    private boolean              mSpeechKeepScreenOn;
    private boolean              mPrepTimeKeepScreenOn;
    private boolean              mImportIntentHandled    = false;

    private String mDialogBlockingTag = null;
    private final ArrayList<Pair<String, QueueableDialogFragment>> mDialogsInWaiting = new ArrayList<>();

    private static final String BUNDLE_KEY_DEBATE_MANAGER               = "dm";
    private static final String BUNDLE_KEY_XML_FILE_NAME                = "xmlfn";
    private static final String BUNDLE_KEY_IMPORT_INTENT_HANDLED        = "iih";
    private static final String BUNDLE_KEY_BLOCKING_DIALOG              = "blocking-dialog";
    private static final String PREFERENCE_XML_FILE_NAME                = "xmlfn";
    private static final String LAST_CHANGELOG_VERSION_SHOWN            = "lastChangeLog";
    private static final String NOTIFICATIONS_PERMISSION_DIALOG_SHOWN   = "notifications-dialog";
    private static final String DIALOG_ARGUMENT_SCHEMA_USED             = "used";
    private static final String DIALOG_ARGUMENT_SCHEMA_SUPPORTED        = "supp";
    private static final String DIALOG_ARGUMENT_FILE_NAME               = "fn";
    private static final String DIALOG_ARGUMENT_INCOMING_STYLE_NAME     = "isn";
    private static final String DIALOG_ARGUMENT_EXISTING_STYLE_NAME     = "esn";
    private static final String DIALOG_ARGUMENT_EXISTS                  = "efl";
    private static final String DIALOG_ARGUMENT_SUGGESTED_FILE_NAME     = "sfn";

    // Dialog tags that are attached to particular files must end in "/", as the name of the file
    // they relate to is appended to the tag.
    private static final String DIALOG_TAG_SCHEMA_TOO_NEW               = "toonew/";
    private static final String DIALOG_TAG_CHANGELOG                    = "changelog";
    private static final String DIALOG_TAG_IMPORT_CONFIRM               = "import";
    private static final String DIALOG_TAG_IMPORT_SUGGEST_REPLACEMENT   = "replace";
    private static final String DIALOG_TAG_NOTIFICATIONS_DENIED         = "notifications";

    private static final int SNACKBAR_DURATION_RESET_DEBATE               = 1200;
    private static final int COLOUR_TRANSPARENT                           = 0;

    private final BroadcastReceiver mGuiUpdateBroadcastReceiver = new GuiUpdateBroadcastReceiver();

    private final OnBackPressedCallback mFinishEditingTimeBackPressedCallback = new OnBackPressedCallback(false) {
        @Override
        public void handleOnBackPressed() {
            editCurrentTimeFinish(false);
        }
    };

    private final OnBackPressedCallback mPreviousSpeechBackPressedCallback = new OnBackPressedCallback(false) {
        @Override
        public void handleOnBackPressed() {
            goToPreviousSpeech();
        }
    };

    private final ControlButtonSpec CONTROL_BUTTON_START_TIMER = new ControlButtonSpec(
            R.string.timer_controlButton_startTimer_text,
            (view) -> {
                mDebateManager.startTimer();
                updateGui();
                updateKeepScreenOn();
            });
    private final ControlButtonSpec CONTROL_BUTTON_STOP_TIMER = new ControlButtonSpec(
            R.string.timer_controlButton_stopTimer_text,
            (view) -> {
                mDebateManager.stopTimer();
                updateGui();
                updateKeepScreenOn();
            });
    private final ControlButtonSpec CONTROL_BUTTON_RESET_TIMER = new ControlButtonSpec(
            R.string.timer_controlButton_resetTimer_text,
            (view) -> {
                mDebateManager.resetActivePhase();
                updateGui();
            });
    private final ControlButtonSpec CONTROL_BUTTON_RESUME_TIMER = new ControlButtonSpec(
            R.string.timer_controlButton_resumeTimer_text,
            (view) -> {
                mDebateManager.startTimer();
                updateGui();
                updateKeepScreenOn();
            });
    private final ControlButtonSpec CONTROL_BUTTON_NEXT_PHASE = new ControlButtonSpec(
            R.string.timer_controlButton_nextPhase_text,
            (view) -> {
                goToNextSpeech();
                updateGui();
            });

    private final ActivityResultLauncher<String> mRequestPermissionLauncher = registerForActivityResult(
            new ActivityResultContracts.RequestPermission(),
            isGranted -> {
                if (!isGranted) {
                    Log.e(TAG, "mRequestPermissionLauncher: permission denied");
                    showNotificationsPermissionDeniedDialog();
                }
            }
    );

    //******************************************************************************************
    // Public classes
    //******************************************************************************************

    public static class QueueableDialogFragment extends DialogFragment {
        @Override
        public void onDismiss(@NonNull DialogInterface dialog) {
            super.onDismiss(dialog);
            DebatingTimerFragment parent;
            try {
                parent = (DebatingTimerFragment) getParentFragment();
            } catch (ClassCastException e) {
                Log.e(TAG, "QueueableDialogFragment.onDismiss: class cast exception in QueueableDialogFragment");
                return;
            }
            if (parent != null) {
                parent.showNextQueuedDialog();
            } else {
                Log.w(TAG, "QueueableDialogFragment.onDismiss: parent was null");
            }
        }
    }

    public static class DialogChangelogFragment extends QueueableDialogFragment {

        @NonNull
        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            final Resources res = getResources();
            final Activity activity = requireActivity();
            final DialogWithDontShowBinding binding = DialogWithDontShowBinding.inflate(LayoutInflater.from(activity));
            binding.message.setText(R.string.changelogDialog_message);

            AlertDialog.Builder builder = new AlertDialog.Builder(activity);
            builder.setTitle(res.getString(R.string.changelogDialog_title))
                    .setView(binding.getRoot())
                    .setPositiveButton(res.getString(R.string.changelogDialog_ok), (dialog, which) -> {
                        // Take note of "do not show again" setting
                        if (binding.dontShowAgain.isChecked()) {
                            SharedPreferences prefs = activity.getPreferences(MODE_PRIVATE);
                            SharedPreferences.Editor editor = prefs.edit();
                            int thisChangelogVersionCode = res.getInteger(R.integer.changelogDialog_versionCode);
                            editor.putInt(LAST_CHANGELOG_VERSION_SHOWN, thisChangelogVersionCode);
                            editor.apply();
                        }
                    });

            return builder.create();
        }

    }

    public static class DialogNotificationPermissionDeniedFragment extends QueueableDialogFragment {
        @NonNull
        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            AlertDialog.Builder builder = new AlertDialog.Builder(requireContext());
            builder.setMessage(R.string.notificationsPermissionDenied_message)
                    .setPositiveButton(R.string.notificationsPermissionDenied_button,
                            (dialog, which) -> {
                        SharedPreferences prefs = requireActivity().getPreferences(MODE_PRIVATE);
                        Editor editor = prefs.edit();
                        editor.putBoolean(NOTIFICATIONS_PERMISSION_DIALOG_SHOWN, true);
                        editor.apply();
                    });

            return builder.create();
        }
    }

    public static class DialogImportFileConfirmFragment extends QueueableDialogFragment {

        static DialogImportFileConfirmFragment newInstance(@NonNull String incomingFilename, @NonNull String incomingStyleName,
                                                           boolean exists, @Nullable String existingStyleName) {
            DialogImportFileConfirmFragment fragment = new DialogImportFileConfirmFragment();
            Bundle args = new Bundle();
            args.putString(DIALOG_ARGUMENT_FILE_NAME, incomingFilename);
            args.putString(DIALOG_ARGUMENT_INCOMING_STYLE_NAME, incomingStyleName);
            args.putBoolean(DIALOG_ARGUMENT_EXISTS, exists);
            args.putString(DIALOG_ARGUMENT_EXISTING_STYLE_NAME, existingStyleName);
            fragment.setArguments(args);
            return fragment;
        }

        @NonNull
        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            final DebatingTimerFragment parent = (DebatingTimerFragment) getParentFragment();
            AlertDialog.Builder builder = new AlertDialog.Builder(requireContext());
            Bundle args = getArguments();
            assert parent != null;
            assert args != null;

            final String incomingFilename = args.getString(DIALOG_ARGUMENT_FILE_NAME);
            String incomingStyleName = args.getString(DIALOG_ARGUMENT_INCOMING_STYLE_NAME);
            String existingStyleName = args.getString(DIALOG_ARGUMENT_EXISTING_STYLE_NAME, "<unknown>");
            StringBuilder message = new StringBuilder(getString(R.string.importDebateFormat_dialog_message_question,
                    incomingFilename, incomingStyleName));

            if (args.getBoolean(DIALOG_ARGUMENT_EXISTS)) {
                message.append("\n\n");
                if (incomingStyleName != null && incomingStyleName.equals(existingStyleName))
                    message.append(getString(R.string.importDebateFormat_dialog_addendum_overwriteExistingSameName, existingStyleName));
                else
                    message.append(getString(R.string.importDebateFormat_dialog_addendum_overwriteExistingDifferentName, existingStyleName));
            }

            builder.setTitle(R.string.importDebateFormat_dialog_title)
                    .setMessage(Html.fromHtml(message.toString()))
                    .setPositiveButton(R.string.importDebateFormat_dialog_button_yes,
                            (dialog, which) -> parent.importIncomingFile(incomingFilename))
                    .setNegativeButton(R.string.importDebateFormat_dialog_button_no,
                            (dialog, which) -> parent.mImportIntentHandled = true);

            AlertDialog dialog = builder.create();
            dialog.setCanceledOnTouchOutside(false);
            return dialog;
        }

    }

    public static class DialogSuggestReplacementFragment extends QueueableDialogFragment {

        static DialogSuggestReplacementFragment newInstance(@NonNull String incomingFilename, @NonNull String styleName,
                                                            @NonNull String suggestedFilename) {
            DialogSuggestReplacementFragment fragment = new DialogSuggestReplacementFragment();
            Bundle args = new Bundle();
            args.putString(DIALOG_ARGUMENT_FILE_NAME, incomingFilename);
            args.putString(DIALOG_ARGUMENT_INCOMING_STYLE_NAME, styleName);
            args.putString(DIALOG_ARGUMENT_SUGGESTED_FILE_NAME, suggestedFilename);
            fragment.setArguments(args);
            return fragment;
        }

        @NonNull
        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            final DebatingTimerFragment parent = (DebatingTimerFragment) getParentFragment();
            final Context context = requireContext();
            AlertDialog.Builder builder = new AlertDialog.Builder(context);
            Bundle args = getArguments();
            assert args != null;
            assert parent != null;

            final String incomingFilename = args.getString(DIALOG_ARGUMENT_FILE_NAME);
            final String suggestedFilename = args.getString(DIALOG_ARGUMENT_SUGGESTED_FILE_NAME);

            builder.setTitle(R.string.replaceDebateFormat_dialog_title)
                    .setMessage(Html.fromHtml(getString(R.string.replaceDebateFormat_dialog_message,
                            args.getString(DIALOG_ARGUMENT_INCOMING_STYLE_NAME), suggestedFilename, incomingFilename)))
                    .setPositiveButton(R.string.replaceDebateFormat_dialog_button_replace,
                            (dialog, which) -> parent.importIncomingFile(suggestedFilename))
                    .setNeutralButton(R.string.replaceDebateFormat_dialog_button_addNew,
                            (dialog, which) -> parent.importIncomingFile(incomingFilename))
                    .setNegativeButton(R.string.replaceDebateFormat_dialog_button_cancel,
                            (dialog, which) -> parent.mImportIntentHandled = true);


            AlertDialog dialog = builder.create();
            dialog.setCanceledOnTouchOutside(false);
            return dialog;
        }

    }

    public static class DialogSchemaTooNewFragment extends QueueableDialogFragment {

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
            final DebatingActivity activity = (DebatingActivity) requireActivity();
            Bundle args = getArguments();
            assert args != null;

            String schemaUsed = args.getString(DIALOG_ARGUMENT_SCHEMA_USED);
            String schemaSupported = args.getString(DIALOG_ARGUMENT_SCHEMA_SUPPORTED);

            String appVersion;
            try {
                appVersion = activity.getPackageManager().getPackageInfo(activity.getPackageName(), 0).versionName;
            } catch (PackageManager.NameNotFoundException e) {
                appVersion = "unknown";
            }

            String message = getString(R.string.schemaTooNewDialog_message,
                    schemaUsed, schemaSupported, appVersion, args.getString(DIALOG_ARGUMENT_FILE_NAME));

            AlertDialog.Builder builder = new AlertDialog.Builder(activity);
            builder.setTitle(R.string.schemaTooNewDialog_title)
                    .setMessage(message)
                    .setPositiveButton(R.string.schemaTooNewDialog_button_upgrade, (dialog, which) -> {
                        // Open Google Play to upgrade
                        Uri uri = Uri.parse(getString(R.string.app_marketUri));
                        Intent intent = new Intent(Intent.ACTION_VIEW);
                        intent.setData(uri);
                        startActivity(intent);
                    })
                    .setNegativeButton(R.string.schemaTooNewDialog_button_ignore, null);

            AlertDialog dialog = builder.create();
            dialog.setCanceledOnTouchOutside(false);
            return dialog;
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

    private static class ControlButtonSpec {
        int textResId;
        View.OnClickListener onClickListener;

        private ControlButtonSpec(int textResId, View.OnClickListener onClickListener) {
            this.textResId = textResId;
            this.onClickListener = onClickListener;
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

    private class DebateTimerDisplayOnPageChangeListener extends ViewPager.SimpleOnPageChangeListener {

        @Override
        public void onPageSelected(int position) {
            // Enable the lock that prevents updateGui() from running while pages are changing.
            // This is necessary to prevent updateGui() from updating the wrong view after this
            // method is run (and the active phase index changed) and before
            // DebateTimerDisplayPagerAdapter#setPrimaryItem() is called (and the view pointer
            // updated).
            if (mDebateManager != null) {
                mIsChangingPages = true;
                mDebateManager.setActivePhaseIndex(position);
            }
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

        private static final String TAG = "DebateTDPagerAdapter";

        private final HashMap<DebateManager.DebatePhaseTag, DebateTimerDisplayBinding> mDisplaysMap = new HashMap<>();
        private static final String NO_DEBATE_LOADED = "no_debate_loaded";

        @Override
        public void destroyItem(@NonNull ViewGroup container, int position, @NonNull Object object) {
            DebateManager.DebatePhaseTag dpt = (DebateManager.DebatePhaseTag) object;
            DebateTimerDisplayBinding binding = mDisplaysMap.get(dpt);
            if (binding == null)
                Log.e(TAG, "Nothing found to destroy at position " + position + " - " + object);
            else
                container.removeView(binding.getRoot());
            mDisplaysMap.remove(dpt);
        }

        @Override
        public int getCount() {
            if (mDebateManager == null) return 0;
            else return mDebateManager.getNumberOfPhases();
        }

        @Override
        public int getItemPosition(@NonNull Object object) {

            DebateManager.DebatePhaseTag tag = (DebateManager.DebatePhaseTag) object;
            if ((mDebateManager == null) != (NO_DEBATE_LOADED.equals(tag.specialTag))) {
                // If it was the "no debate loaded" screen and there is now a debate loaded,
                // then the View no longer exists.  Likewise if there is no debate loaded and
                // there was anything but the "no debate loaded" screen.
                Log.e(TAG, "getItemPosition: returning POSITION_NONE");
                return POSITION_NONE;


            } else if (mDebateManager == null) {
                // If it was "no debate loaded" and there is still no debate loaded, it's unchanged.
                // This should never happen, but just in case.
                Log.e(TAG, "getItemPosition: returning POSITION_UNCHANGED");
                return POSITION_UNCHANGED;
            }

            // If there's no messy debate format changing or loading, delegate this function to the
            // DebateManager.
            else return mDebateManager.getPhaseIndexForTag(tag);
        }

        @NonNull
        @Override
        public Object instantiateItem(@NonNull ViewGroup container, int position) {

            Context context = requireContext();

            if (mDebateManager == null) {
                // Return a blank view that should never be seen, since I think we have to add something.
                Log.e(TAG, "Tried to instantiate ViewPager item with no debate loaded");
                container.addView(new View(context));
                DebateManager.DebatePhaseTag tag = new DebateManager.DebatePhaseTag();
                tag.specialTag = NO_DEBATE_LOADED;
                return tag;
            }

            // The View for the position in question is the inflated debate_timer_display for
            // the relevant timer (prep time or speech).
            DebateTimerDisplayBinding vb = DebateTimerDisplayBinding.inflate(LayoutInflater.from(context));

            // OnTouchListeners
            vb.timerRoot.setOnClickListener((view) -> editCurrentTimeFinish(true));
            vb.timerCurrentTime.setOnLongClickListener(
                    (view) -> {
                        editCurrentTimeStart();
                        return true;
                    }
            );

            // Set the time picker to 24-hour time
            vb.timerCurrentTimePicker.setIs24HourView(true);

            // Set the POI timer OnClickListener
            vb.timerPoiTimerButton.setOnClickListener(new PoiButtonOnClickListener());

            // Update the debate timer display
            long time = mDebateManager.getPhaseCurrentTime(position);
            DebatePhaseFormat dpf = mDebateManager.getPhaseFormat(position);
            PeriodInfo pi = dpf.getPeriodInfoForTime(time);

            updateDebateTimerDisplay(vb, dpf, pi,
                    mDebateManager.getPhaseName(position), time,
                    mDebateManager.getPhaseNextOvertimeBellTime(position));

            container.addView(vb.getRoot());

            // Retrieve a tag and take note of it.
            DebateManager.DebatePhaseTag tag = mDebateManager.getPhaseTagForIndex(position);
            mDisplaysMap.put(tag, vb);

            return tag;

        }

        @Override
        public boolean isViewFromObject(@NonNull View view, @NonNull Object object) {
            DebateManager.DebatePhaseTag dpt = (DebateManager.DebatePhaseTag) object;
            if (!mDisplaysMap.containsKey(dpt))
                return false;
            DebateTimerDisplayBinding binding = mDisplaysMap.get(dpt);
            if (binding == null)
                return false;
            return binding.getRoot() == view;
        }

        @Override
        public void setPrimaryItem(@NonNull ViewGroup container, int position, @NonNull Object object) {
            DebateTimerDisplayBinding original = mTimerDisplay;

            DebateManager.DebatePhaseTag dpt = (DebateManager.DebatePhaseTag) object;
            mTimerDisplay = mDisplaysMap.get(dpt);

            // Disable the lock that prevents updateGui() from running while the pages are
            // changing.
            mIsChangingPages = false;

            // This method seems to be called multiple times on each update.
            // To save unnecessary work (i.e. for performance), only run (the relatively-intensive)
            // updateGui if mDebateTimerDisplay has actually changed.
            if (original != mTimerDisplay)
                updateGui();
        }

        /**
         * Refreshes all the background colours known to this {@link PagerAdapter}.
         * This should be called when a background colour user preference is changed, in a way
         * that requires all of the background colours in all {@link View}s known to be refreshed.
         * Before calling this method, <code>DebatingActivity.resetBackgroundColoursToTransparent()</code>
         * should be called to reset all of the other background colours to transparent.
         */
        void refreshBackgroundColours() {
            if (mDebateManager == null) return;
            for (Map.Entry<DebateManager.DebatePhaseTag, DebateTimerDisplayBinding> entry : mDisplaysMap.entrySet()) {
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

    private class DebatingTimerMenuItemClickListener implements Toolbar.OnMenuItemClickListener {

        @Override
        public boolean onMenuItemClick(MenuItem item) {
            editCurrentTimeFinish(false);
            int itemId = item.getItemId();
            if (itemId == R.id.timer_menuItem_chooseFormat) {
                navigateToFormatChooser(mFormatXmlFileName);
                return true;

            } else if (itemId == R.id.timer_menuItem_resetDebate) {
                if (mDebateManager == null) return true;
                resetDebate(false);
                showSnackbar(SNACKBAR_DURATION_RESET_DEBATE, R.string.timer_snackbar_resetDebate);
                return true;

            } else if (itemId == R.id.timer_menuItem_settings) {
                Log.d(TAG, "opening settings");
                @NonNull NavDirections action = DebatingTimerFragmentDirections.actionEditSettings();
                NavHostFragment.findNavController(DebatingTimerFragment.this).navigate(action);
                return true;

            } else if (itemId == R.id.timer_menuItem_ringBells) {
                // Edit the preference, then apply the changes.
                // Don't fetch the current preference - if there is an inconsistency, we want the toggle
                // to reflect what this activity thinks silent mode is.
                SharedPreferences.Editor editor = PreferenceManager.getDefaultSharedPreferences(requireContext()).edit();
                editor.putBoolean(getResources().getString(R.string.pref_ringBells_key), !mBellsEnabled);
                boolean success = editor.commit(); // we want this to block until it returns
                if (success) applyPreferences(); // this will update mBellsEnabled
                return true;

            }
            else return false;
        }
    }

    private class DebatingTimerFlashScreenListener implements AlertManager.FlashScreenListener {

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
            Activity activity = getActivity();
            if (activity == null) return;  // doesn't matter if no activity is current
            activity.runOnUiThread(() -> {
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

                updateDebateTimerDisplayColours(mTimerDisplay, textColour, backgroundColour);

                // Set the background colour of the root view to be black again.
                mViewBinding.timerRootView.setBackgroundColor(resources.getColor(android.R.color.black));
            });
        }

        @Override
        public void flashScreenOn(final int colour) {
            Activity activity = getActivity();
            if (activity == null) return;  // doesn't matter if no activity is current
            activity.runOnUiThread(() -> {

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
                updateDebateTimerDisplayColours(mTimerDisplay, invertedTextColour, COLOUR_TRANSPARENT);

                // Having completed preparations, set the background colour of the root view to
                // flash the screen.
                mViewBinding.timerRootView.setBackgroundColor(colour);
            });
        }
    }

    /**
     * Defines call-backs for service binding, passed to bindService()
     */
    private class DebatingTimerServiceConnection implements ServiceConnection {

        @Override
        public void onServiceConnected(ComponentName className, IBinder service) {
            Log.d(TAG, "service connected");
            mServiceBinder = (DebatingTimerService.DebatingTimerServiceBinder) service;
            initialiseDebate(true);
            restoreBinder();
        }

        @Override
        public void onServiceDisconnected(ComponentName arg0) {
            Log.d(TAG, "service disconnected");
            mDebateManager = null;
            notifyViewPagerDataSetChanged();
        }
    }

    private static class FatalXmlError extends Exception {

        private static final long serialVersionUID = -1774973645180296278L;

        FatalXmlError(String detailMessage) {
            super(detailMessage);
        }

        FatalXmlError(String detailMessage, Throwable throwable) {
            super(detailMessage, throwable);
        }
    }

    private class FormatChooserFragmentResultListener implements FragmentResultListener {

        @Override
        public void onFragmentResult(@NonNull String requestKey, @NonNull Bundle result) {
            int outcome = result.getInt(FormatChooserFragment.BUNDLE_KEY_RESULT);
            String filename = result.getString(FormatChooserFragment.BUNDLE_KEY_XML_FILE_NAME);
            if (outcome == FormatChooserFragment.RESULT_UNCHANGED) {
                Log.v(TAG, "Format was unchanged");
            } else if (filename != null) {
                Log.v(TAG, "Got file name " + filename);
                setXmlFileName(filename);
                resetDebate(true);
            } else {
                Log.e(TAG, "File name returned was null");
                setXmlFileName(null);
                if (mServiceBinder != null) mServiceBinder.releaseDebateManager();
                mDebateManager = null;
                updateGui();
                updateToolbar();
            }
            mIsOpeningFormatChooser = false;  // clear flag
        }
    }

    private class BeamFileUriCallback implements NfcAdapter.CreateBeamUrisCallback {

        @Override
        public Uri[] createBeamUris(NfcEvent event) {
            FormatXmlFilesManager filesManager = new FormatXmlFilesManager(requireActivity());
            if (!filesManager.exists(mFormatXmlFileName)) {
                Log.e(TAG, "createBeamUris: Tried to share non-existent file");
                showSnackbar(Snackbar.LENGTH_LONG, R.string.timer_snackbar_beam_error_existence);
                return new Uri[0];
            }
            File file = filesManager.getFileFromExternalStorage(mFormatXmlFileName);
            Uri fileUri = Uri.fromFile(file);
            if (fileUri != null) {
                Log.i(TAG, "createBeamUris: Sharing URI " + fileUri);
                return new Uri[]{fileUri};
            } else {
                showSnackbar(Snackbar.LENGTH_LONG, R.string.timer_snackbar_beam_error_generic);
                Log.e(TAG, "createBeamUris: file URI was null");
                return new Uri[0];
            }
        }
    }

    private final class GuiUpdateBroadcastReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            updateGui();
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
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        FragmentActivity activity = requireActivity();

        OnBackPressedDispatcher dispatcher = activity.getOnBackPressedDispatcher();
        dispatcher.addCallback(this, mPreviousSpeechBackPressedCallback);
        dispatcher.addCallback(this, mFinishEditingTimeBackPressedCallback);

        getParentFragmentManager().setFragmentResultListener(FormatChooserFragment.REQUEST_KEY_CHOOSE_FORMAT,
                this, new FormatChooserFragmentResultListener());

        // If there's a file name passed in (presumably from FormatChooserFragment), use it,
        // otherwise load from preferences.
        SharedPreferences prefs = activity.getPreferences(MODE_PRIVATE);
        mFormatXmlFileName = prefs.getString(PREFERENCE_XML_FILE_NAME, null);

        // Bind to the timer service
        Intent serviceIntent = new Intent(activity, DebatingTimerService.class);
        activity.bindService(serviceIntent, mServiceConnection, Context.BIND_AUTO_CREATE);
    }

    @Override
    @Nullable
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        mViewBinding = FragmentDebateBinding.inflate(inflater, container, false);
        return mViewBinding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        mViewBinding.toolbarDebatingTimer.setOnMenuItemClickListener(new DebatingTimerMenuItemClickListener());
        mViewBinding.timerPlayBellButton.setOnClickListener(
                (v) -> mServiceBinder.getAlertManager().playSingleBell()
        );
        mViewBinding.timerDebateLoadError.debateLoadErrorChooseStyleButton.setOnClickListener(
                (v) -> navigateToFormatChooser(null)
        );
        mViewBinding.timerNoDebateLoaded.noDebateLoadedChooseStyleButton.setOnClickListener(
                (v) -> navigateToFormatChooser(null)
        );
        mViewBinding.timerDebateLoadError.debateLoadErrorMessage.setMovementMethod(LinkMovementMethod.getInstance());

        // ViewPager
        mViewPager = mViewBinding.timerViewPager;
        mViewPager.setAdapter(new DebateTimerDisplayPagerAdapter());
        mViewPager.addOnPageChangeListener(new DebateTimerDisplayOnPageChangeListener());
        mViewPager.setPageMargin(1);
        mViewPager.setPageMarginDrawable(R.drawable.divider);

        mLastStateBundle = savedInstanceState; // This could be null
        if (savedInstanceState != null)
            mDialogBlockingTag = savedInstanceState.getString(BUNDLE_KEY_BLOCKING_DIALOG);

        // Configure NFC
        Activity activity = requireActivity();
        if (activity.getPackageManager().hasSystemFeature(PackageManager.FEATURE_NFC)) {
            NfcAdapter nfcAdapter = NfcAdapter.getDefaultAdapter(activity);
            if (nfcAdapter != null)
                nfcAdapter.setBeamPushUrisCallback(new BeamFileUriCallback(), activity);
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        Context context = requireContext();
        context.unbindService(mServiceConnection);
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle bundle) {
        super.onSaveInstanceState(bundle);
        bundle.putString(BUNDLE_KEY_XML_FILE_NAME, mFormatXmlFileName);
        bundle.putBoolean(BUNDLE_KEY_IMPORT_INTENT_HANDLED, mImportIntentHandled);
        bundle.putString(BUNDLE_KEY_BLOCKING_DIALOG, mDialogBlockingTag);
        if (mDebateManager != null)
            mDebateManager.saveState(BUNDLE_KEY_DEBATE_MANAGER, bundle);
    }

    @Override
    public void onStart() {
        super.onStart();

        copyAssetsIfEmpty();

        // If there's an incoming style, and it wasn't handled before a screen rotation, ask the
        // user whether they want to import it.
        if (mLastStateBundle != null) {
            mImportIntentHandled = mLastStateBundle.getBoolean(BUNDLE_KEY_IMPORT_INTENT_HANDLED, false);
            Log.d(TAG, "onViewCreated: import intent handled is " + mImportIntentHandled);
        }
        if (Intent.ACTION_VIEW.equals(requireActivity().getIntent().getAction()) && !mImportIntentHandled) {
            showDialogToConfirmImport();
        } else if (mFormatXmlFileName == null) {
            // Otherwise, if there's no style loaded, direct the user to choose one
            if (!mIsOpeningFormatChooser) {
                Log.v(TAG, "no file loaded, redirecting to choose format");
                navigateToFormatChooser(null);
                return;
            } else {
                Log.v(TAG, "no file loaded, but returned from format chooser, so staying put");
                mIsOpeningFormatChooser = false;
            }
        }

        restoreBinder();
        LocalBroadcastManager.getInstance(requireContext()).registerReceiver(mGuiUpdateBroadcastReceiver,
                new IntentFilter(DebatingTimerService.UPDATE_GUI_BROADCAST_ACTION));
        updateGui();

        showChangelogDialog();

        if (mDebateLoadError != null)
            // May as well try again (sometimes the error is cleared, e.g., if the error was because
            // the file didn't exist, and the user just downloaded it.
            initialiseDebate(false);
    }

    @Override
    public void onStop() {
        super.onStop();
        if (mServiceBinder != null) {
            AlertManager am = mServiceBinder.getAlertManager();
            if (am != null) am.activityStop();
        }
        LocalBroadcastManager.getInstance(requireContext()).unregisterReceiver(mGuiUpdateBroadcastReceiver);
    }


    //******************************************************************************************
    // Private methods
    //******************************************************************************************

    /**
     * Gets the preferences from the shared preferences file and applies them. If this fragment is
     * not attached to an activity, it skips the updates that require one.
     */
    private void applyPreferences() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(requireContext());
        boolean vibrateMode, overtimeBellsEnabled;
        boolean poiBuzzerEnabled, poiVibrateEnabled, prepTimerEnabled;
        int firstOvertimeBell, overtimeBellPeriod;
        String userCountDirectionValue, userPrepTimeCountDirectionValue, poiFlashScreenModeValue, backgroundColourAreaValue;
        FlashScreenMode flashScreenMode, poiFlashScreenMode;

        Resources res = getResources();

        final String TAG = "applyPreferences";

        try {

            // The boolean preferences
            mBellsEnabled = prefs.getBoolean(res.getString(R.string.pref_ringBells_key), res.getBoolean(R.bool.prefDefault_ringBells));
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
                DebateTimerDisplayPagerAdapter adapter = (DebateTimerDisplayPagerAdapter) mViewPager.getAdapter();
                if (adapter != null) adapter.refreshBackgroundColours();
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
            notifyViewPagerDataSetChanged();

        } else {
            Log.v(TAG, "Couldn't restore overtime bells, mDebateManager doesn't yet exist");
        }

        if (mServiceBinder != null) {
            AlertManager am = mServiceBinder.getAlertManager();

            // Volume control stream is linked to ring bells mode
            am.setBellsEnabled(mBellsEnabled);

            am.setVibrateMode(vibrateMode);
            am.setFlashScreenMode(flashScreenMode);

            am.setPoiBuzzerEnabled(poiBuzzerEnabled);
            am.setPoiVibrateEnabled(poiVibrateEnabled);
            am.setPoiFlashScreenMode(poiFlashScreenMode);

            Log.v(TAG, "AlertManager preferences applied");
        } else {
            Log.v(TAG, "Couldn't restore AlertManager preferences; service binder doesn't yet exist");
        }

        Activity activity = getActivity();
        if (activity != null)
            activity.setVolumeControlStream((mBellsEnabled) ? AudioManager.STREAM_MUSIC : AudioManager.STREAM_RING);
        updateKeepScreenOn();

        updateToolbar();
        updateGui();
    }

    private void applyPrepTimeBells() {
        Context context = requireContext();
        PrepTimeBellsManager ptbm = new PrepTimeBellsManager(context);
        SharedPreferences prefs = context.getSharedPreferences(PrepTimeBellsManager.PREP_TIME_BELLS_PREFERENCES_NAME, MODE_PRIVATE);
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
    private DebateFormat buildDebateFromXml(String filename, boolean showDialogs) throws FatalXmlError {

        DebateFormatBuilderFromXml dfbfx;
        Context context = requireContext();

        InputStream is;
        DebateFormat df;
        FormatXmlFilesManager filesManager = new FormatXmlFilesManager(context);

        try {
            is = filesManager.open(filename);
        } catch (IOException e) {
            throw new FatalXmlError(getString(R.string.debateLoadError_cannotFind), e);
        }

        dfbfx = new DebateFormatBuilderFromXmlForSchema2(context);

        try {
            df = dfbfx.buildDebateFromXml(is);
        } catch (IOException e) {
            throw new FatalXmlError(getString(R.string.debateLoadError_cannotRead), e);
        } catch (SAXException e) {
            Log.e(TAG, "bad xml");
            throw new FatalXmlError(getString(
                    R.string.debateLoadError_badXml, e.getLocalizedMessage()), e);
        }

        if (dfbfx.isSchemaOutdated())
            throw new FatalXmlError(getString(R.string.debateLoadError_schemaOutdated));

        if (showDialogs && dfbfx.isSchemaTooNew()) {
            QueueableDialogFragment fragment = DialogSchemaTooNewFragment.newInstance(dfbfx.getSchemaVersion(), dfbfx.getSupportedSchemaVersion(), filename);
            queueDialog(fragment, DIALOG_TAG_SCHEMA_TOO_NEW + filename);
        }

        if (df.numberOfSpeeches() == 0)
            throw new FatalXmlError(getString(
                    R.string.debateLoadError_noSpeeches));

        if (dfbfx.hasErrors()) {

            StringBuilder errorLogItems = new StringBuilder();
            List<String> errorLog = dfbfx.getErrorLog();
            if (errorLog != null) {
                for (String error : errorLog) {
                    errorLogItems.append("• ");
                    errorLogItems.append(error);
                    errorLogItems.append("<br />");
                }
            }
            throw new FatalXmlError(getString(R.string.debateLoadError_generalErrors, errorLogItems.toString()));
        }

        return df;
    }

    private void clearDebateLoadError() {
        mDebateLoadError = null;
        updateGui();
    }

    private void copyAssetsIfEmpty() {
        FormatXmlFilesManager manager = new FormatXmlFilesManager(requireContext());
        try {
            if (manager.isEmpty()) manager.copyAssets();
        } catch (IOException e) {
            showSnackbar(Snackbar.LENGTH_LONG, R.string.timer_snackbar_copyAssetsError);
        }
    }

    /**
     * Finishes editing the current time and restores the GUI to its prior state.
     * @param save true if the edited time should become the new current time, false if it should
     * be discarded.
     */
    private void editCurrentTimeFinish(boolean save) {

        if (mTimerDisplay == null) {
            Log.e(TAG, "editCurrentTimeFinish: no debate timer display");
            return;
        }

        TimePicker currentTimePicker = mTimerDisplay.timerCurrentTimePicker;
        currentTimePicker.clearFocus();

        // Hide the keyboard
        InputMethodManager imm = (InputMethodManager) requireContext().getSystemService(INPUT_METHOD_SERVICE);
        imm.hideSoftInputFromWindow(currentTimePicker.getWindowToken(), 0);

        if (save && mDebateManager != null && mIsEditingTime) {
            // We're using this in hours and minutes, not minutes and seconds
            int minutes = currentTimePicker.getCurrentHour();
            int seconds = currentTimePicker.getCurrentMinute();
            long newTime = minutes * 60L + seconds;
            // Invert the time if in count-down mode
            newTime = subtractFromSpeechLengthIfCountingDown(newTime);
            mDebateManager.setActivePhaseCurrentTime(newTime);
        }

        mIsEditingTime = false;
        mFinishEditingTimeBackPressedCallback.setEnabled(false);

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
        mFinishEditingTimeBackPressedCallback.setEnabled(true);

        if (mTimerDisplay == null) {
            Log.e(TAG, "editCurrentTimeStart: no debate timer display");
            return;
        }

        TimePicker currentTimePicker = mTimerDisplay.timerCurrentTimePicker;

        long currentTime = mDebateManager.getActivePhaseCurrentTime();

        // Invert the time if in count-down mode
        currentTime = subtractFromSpeechLengthIfCountingDown(currentTime);

        // Limit to the allowable time range
        if (currentTime < 0) {
            currentTime = 0;
            showSnackbar(Snackbar.LENGTH_LONG, R.string.timer_snackbar_editTextDiscardChangesInfo_limitedBelow);
        } else if (currentTime >= 24 * 60) {
            currentTime = 24 * 60 - 1;
            showSnackbar(Snackbar.LENGTH_LONG, R.string.timer_snackbar_editTextDiscardChangesInfo_limitedAbove);
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
     * Retrieves the file name and an {@link InputStream} from the {@link Intent} that started this
     * activity. If either can't be found, it shows the appropriate snackbar (so there is no need
     * for the caller to show another error message), and returns null.
     * @return a <code>Pair&lt;String, InputStream&gt;</code>, being the file name and input stream,
     * or null if there was an error.
     */
    @Nullable
    private Pair<String, InputStream> getIncomingFilenameAndInputStream() {
        Activity activity = requireActivity();
        Intent intent = activity.getIntent();
        if (!Intent.ACTION_VIEW.equals(intent.getAction())) {
            Log.e(TAG, "importIncomingFile: Intent action was not ACTION_VIEW");
            return null;
        }

        Log.i(TAG, String.format("importIncomingFile: mime type %s, data %s", intent.getType(), intent.getDataString()));

        Uri uri = intent.getData();
        String filename = DebatekeeperUtils.getFilenameFromUri(activity.getContentResolver(), uri);
        Log.i(TAG, "importIncomingFile: file name is " + filename);

        if (filename == null) {
            showSnackbar(Snackbar.LENGTH_LONG, R.string.importDebateFormat_snackbar_error_generic);
            Log.e(TAG, "importIncomingFile: File name was null");
            return null;
        }

        InputStream is;
        try {
            is = activity.getContentResolver().openInputStream(uri);
        } catch (FileNotFoundException e) {
            showSnackbar(Snackbar.LENGTH_LONG, R.string.importDebateFormat_snackbar_error_generic);
            Log.e(TAG, "importIncomingFile: Could not resolve file " + uri);
            return null;
        }
        return Pair.create(filename, is);
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

    /**
     * Imports a file that was passed in the intent that opened this activity.
     * @param filename the file name to be saved
     */
    private void importIncomingFile(String filename) {
        Pair<String, InputStream> incoming = getIncomingFilenameAndInputStream();
        if (incoming == null) return;
        InputStream is = incoming.second;

        FormatXmlFilesManager filesManager = new FormatXmlFilesManager(requireContext());

        try {
            filesManager.copy(is, filename);
        } catch (IOException e) {
            e.printStackTrace();
            showSnackbar(Snackbar.LENGTH_LONG, R.string.importDebateFormat_snackbar_error_generic);
            Log.e(TAG, "importIncomingFile: Could not copy file: " + e.getMessage());
            return;
        }

        showSnackbar(Snackbar.LENGTH_SHORT, R.string.importDebateFormat_snackbar_success, filename);

        // Now, load the debate
        mImportIntentHandled = true;
        setXmlFileName(filename);
        resetDebate(true);
    }

    /**
     * Initialises the debate by parsing the relevant XML file, creating a {@link DebateManager},
     * and applying the user's preferences to it.
     * <p>
     * If a {@link DebateManager} is already active, this skips the debate initialisation. Use
     * {@link #resetDebate(boolean)} to reset the debate.
     *
     * @see #resetDebate(boolean)
     * @param showDialogs whether dialogs should be shown, normally {@code true} but should be
     *                    {@code false} if, for example, this was initiated by a reset.
     */
    private void initialiseDebate(boolean showDialogs) {
        if (mFormatXmlFileName == null) {
            Log.w(TAG, "Tried to initialise debate with null file");
            return;
        }

        mDebateManager = mServiceBinder.getDebateManager();

        if (mDebateManager == null) {
            Log.d(TAG, "initialiseDebate: creating debate manager");

            DebateFormat df;
            try {
                df = buildDebateFromXml(mFormatXmlFileName, showDialogs);
            } catch (FatalXmlError e) {
                String message = e.getLocalizedMessage();
                setDebateLoadError(message);
                notifyViewPagerDataSetChanged();
                return;
            }

            mDebateManager = mServiceBinder.createDebateManager(df);

            // We only restore the state if there wasn't an existing debate, i.e. if the service
            // wasn't already running, and if the debate format stored in the saved instance state
            // matches the debate format we're using now.
            if (mLastStateBundle != null) {
                String xmlFileName = mLastStateBundle.getString(BUNDLE_KEY_XML_FILE_NAME);
                if (xmlFileName != null && xmlFileName.equals(mFormatXmlFileName))
                    mDebateManager.restoreState(BUNDLE_KEY_DEBATE_MANAGER, mLastStateBundle);
            }
        } else {
            Log.d(TAG, "initialiseDebate: debate manager already existed");
        }

        // The bundle should only ever be relevant once per activity cycle
        mLastStateBundle = null;

        Log.d(TAG, "clearing error, notifying view pager");
        clearDebateLoadError();
        if (mViewPager != null) {  // sometimes this is called from the service, so mViewPager might not exist
            notifyViewPagerDataSetChanged();
            mViewPager.setCurrentItem(mDebateManager.getActivePhaseIndex(), false);
        }
        applyPreferences();
        updateToolbar();
        requestNotificationsPermission();
    }

    /**
     * Returns whether or not this is the user's first time opening the app.
     * @return {@code true} if it is the first time, {@code false} otherwise.
     */
    private boolean isFirstInstall() {
        PackageInfo info;
        try {
            info = requireActivity().getPackageManager().getPackageInfo(requireActivity().getPackageName(), 0);
        } catch (NameNotFoundException e) {
            Log.e(TAG, "isFirstInstall: Can't find package info, assuming it is the first install");
            return true;
        }

        Log.v(TAG, String.format("isFirstInstall: %d vs %d", info.firstInstallTime, info.lastUpdateTime));
        return info.firstInstallTime == info.lastUpdateTime;
    }

    /**
     * Navigates to {@link FormatChooserFragment}.
     */
    private void navigateToFormatChooser(@Nullable String xmlFileName) {
        DebatingTimerFragmentDirections.ActionChooseFormat action = DebatingTimerFragmentDirections.actionChooseFormat();
        mIsOpeningFormatChooser = true;
        if (xmlFileName != null)
            action.setXmlFileName(xmlFileName);
        NavHostFragment.findNavController(this).navigate(action);
    }

    /**
     * Convenience function, basically runs <code>mViewPager.getAdapter().notifyDataSetChanged()</code>
     * but also checks that things aren't <code>null</code> (and does nothing if anything is).
     */
    private void notifyViewPagerDataSetChanged() {
        if (mViewPager == null) return;
        PagerAdapter adapter = mViewPager.getAdapter();
        if (adapter == null) return;
        adapter.notifyDataSetChanged();
    }

    /**
     * Queues a dialog to be shown after a currently-shown dialog, or immediately if there is no
     * currently-shown dialog.  This does not happen automatically - dialogs must know whether they
     * are potentially blocking or waiting, and set themselves up accordingly.  Dialogs that could
     * block must set <code>mDialogBlockingTag</code> to their tag when they are shown, and call
     * <code>showQueuedDialog()</code> when they are dismissed.
     * Dialogs that could be queued must call <code>queueDialog()</code> instead of
     * <code>showDialog()</code>.
     *
     * @param fragment the {@link DialogFragment} that would be passed to showDialog()
     * @param tag      the tag that would be passed to showDialog()
     */
    private void queueDialog(QueueableDialogFragment fragment, String tag) {
        if (getChildFragmentManager().findFragmentByTag(tag) != null) {
            Log.w(TAG, "skipping dialog, found in fragment manager: " + tag);
            return;
        }

        if (mDialogBlockingTag == null) {
            Log.d(TAG, "showing dialog immediately: " + tag);
            mDialogBlockingTag = tag;
            fragment.show(getChildFragmentManager(), tag);
        } else {

            // don't queue this again if the same dialog is already queued or showing
            if (mDialogBlockingTag.equals(tag)) {
                Log.w(TAG, "skipping dialog, duplicate of blocked: " + tag);
                return;
            }
            for (Pair<String, QueueableDialogFragment> pair : mDialogsInWaiting) {
                if (pair.first.equals(tag)) {
                    Log.w(TAG, "skipping dialog, already queued: " + tag);
                    return;
                }
            }

            Log.d(TAG, "queueing dialog: " + tag + " (currently blocking: " + mDialogBlockingTag + ")");
            mDialogsInWaiting.add(Pair.create(tag, fragment));
        }
    }

    private void requestNotificationsPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            if (shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS)) {
                Log.w(TAG, "requestNotificationsPermission: permission denied");
                showNotificationsPermissionDeniedDialog();
            } else {
                mRequestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS);
            }
        }
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
            if (v.getId() == R.id.timer_root) {
                v.setBackgroundColor(COLOUR_TRANSPARENT);
                View speechNameText = v.findViewById(R.id.timer_speechNameText);
                View periodDescriptionText = v.findViewById(R.id.timer_periodDescriptionText);
                speechNameText.setBackgroundColor(COLOUR_TRANSPARENT);
                periodDescriptionText.setBackgroundColor(COLOUR_TRANSPARENT);
            }
        }
    }

    /**
     * Releases the current {@link DebateManager} and then initialises the debate (by calling
     * {@link #initialiseDebate(boolean)}.
     * @param showDialogs whether dialogs should be shown, normally {@code true} but should be
     *      *                    {@code false} if, for example, this was initiated by a reset.
     */
    private void resetDebate(boolean showDialogs) {
        if (mServiceBinder == null) return;
        mServiceBinder.releaseDebateManager();
        initialiseDebate(showDialogs);
    }

    private void restoreBinder() {
        if (mServiceBinder != null) {
            AlertManager am = mServiceBinder.getAlertManager();
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
            setButton(mViewBinding.timerLeftCentreControlButton, left);
            setButton(mViewBinding.timerLeftControlButton, null);
            setButton(mViewBinding.timerCentreControlButton, null);

        } else {
            setButton(mViewBinding.timerLeftCentreControlButton, null);
            setButton(mViewBinding.timerLeftControlButton, left);
            setButton(mViewBinding.timerCentreControlButton, centre);
        }

        setButton(mViewBinding.timerRightControlButton, right);
    }

    /**
     * Enables or disables all of the control buttons (except for the "Bell" button).  If
     * <code>mDebateManager</code> is <code>null</code>, this does nothing.
     * @param enable <code>true</code> to enable, <code>false</code> to disable
     */
    private void setButtonsEnable(boolean enable) {
        if (mDebateManager == null) return;
        mViewBinding.timerLeftControlButton.setEnabled(enable);
        mViewBinding.timerLeftCentreControlButton.setEnabled(enable);
        mViewBinding.timerCentreControlButton.setEnabled(enable);
        // Disable the [Next Speaker] button if there are no more speakers
        mViewBinding.timerRightControlButton.setEnabled(enable && !mDebateManager.isInLastPhase());
    }

    private void setDebateLoadError(String message) {
        mDebateLoadError = Html.fromHtml(message);
        if (mServiceBinder != null) mServiceBinder.releaseDebateManager();
        mDebateManager = null;
        mIsChangingPages = false;
        updateGui();
    }

    private void setXmlFileName(String filename) {
        mFormatXmlFileName = filename;
        SharedPreferences sp = requireActivity().getPreferences(MODE_PRIVATE);
        Editor editor = sp.edit();
        if (filename != null)
            editor.putString(PREFERENCE_XML_FILE_NAME, filename);
        else
            editor.remove(PREFERENCE_XML_FILE_NAME);
        editor.apply();
    }

    private void showChangelogDialog() {
        SharedPreferences prefs = requireActivity().getPreferences(MODE_PRIVATE);
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
                queueDialog(new DialogChangelogFragment(), DIALOG_TAG_CHANGELOG);
            }
        }
    }

    private void showDialogToConfirmImport() {
        Pair<String, InputStream> incoming = getIncomingFilenameAndInputStream();
        if (incoming == null) return;
        String incomingFilename = incoming.first;
        InputStream is = incoming.second;

        Context context = requireContext();

        DebateFormatFieldExtractor nameExtractor = new DebateFormatFieldExtractor(context, R.string.xml2elemName_name);
        FormatXmlFilesManager filesManager = new FormatXmlFilesManager(context);
        boolean exists = filesManager.exists(incomingFilename);

        String incomingStyleName = null, existingStyleName = null;

        try {
            incomingStyleName = nameExtractor.getFieldValue(is);
            is.close();
        } catch (IOException e) {
            showSnackbar(Snackbar.LENGTH_LONG, R.string.importDebateFormat_snackbar_error_generic);
            return;
        } catch (SAXException e) {
            Log.e(TAG, "showDialogToConfirmImport: error parsing incoming file");
            // continue with unknown file name
        }
        if (incomingStyleName == null)
            incomingStyleName = getString(R.string.importDebateFormat_placeholder_unknownStyleName);

        if (exists) {
            // If there's an existing file, grab its style name and prompt to replace. (We don't
            // give an option not to replace.
            try {
                InputStream existingIs = filesManager.open(incomingFilename);
                existingStyleName = nameExtractor.getFieldValue(existingIs);
                existingIs.close();
            } catch (IOException | SAXException e) {
                Log.e(TAG, "showDialogToConfirmImport: error parsing existing file");
            }
            if (existingStyleName == null)
                existingStyleName = getString(R.string.importDebateFormat_placeholder_unknownStyleName);

        } else {
            // If it wasn't found, check if the style name happens to match any other file, since we
            // may want to change the file name in order to overwrite the existing file (to avoid
            // duplicates that arise because the system changed the file name during transmission).
            // We'll do this only if there's exactly one existing file -- if there are already
            // duplicates, we can't reliably tell which one to pick.
            String otherStyleName, suggestedFilename = null;
            String[] userFileList = new String[0];
            int numberOfDuplicatesFound = 0;
            try {
                userFileList = filesManager.list();
            } catch (IOException e) {
                Log.e(TAG, "showDialogToConfirmImport: I/O error checking other files");
            }
            for (String otherFilename : userFileList) {
                try {
                    InputStream otherIs = filesManager.open(otherFilename);
                    otherStyleName = nameExtractor.getFieldValue(otherIs);
                    otherIs.close();
                } catch (IOException | SAXException e) {
                    continue;
                }
                if (incomingStyleName.equals(otherStyleName)) {
                    numberOfDuplicatesFound++;
                    suggestedFilename = otherFilename;
                }
            }
            if (numberOfDuplicatesFound == 1) {
                DialogSuggestReplacementFragment fragment = DialogSuggestReplacementFragment.newInstance(incomingFilename, incomingStyleName, suggestedFilename);
                queueDialog(fragment, DIALOG_TAG_IMPORT_SUGGEST_REPLACEMENT);
                return;
            }
        }

        DialogImportFileConfirmFragment fragment = DialogImportFileConfirmFragment.newInstance(incomingFilename, incomingStyleName, exists, existingStyleName);
        queueDialog(fragment, DIALOG_TAG_IMPORT_CONFIRM);
    }

    /**
     * Shows the next queued dialog if there is one, otherwise notes that there are no dialogs
     * blocking.
     */
    private void showNextQueuedDialog() {
        // First, remove now-irrelevant dialogs from list
        Iterator<Pair<String, QueueableDialogFragment>> iterator = mDialogsInWaiting.iterator();
        while (iterator.hasNext()) {
            Pair<String, QueueableDialogFragment> pair = iterator.next();
            String[] tags = pair.first.split("/", 2);
            if (tags.length == 2 && !tags[1].equals(mFormatXmlFileName)) {
                iterator.remove();
                Log.i(TAG, "showNextQueuedDialog: cleared dialog " + pair.first);
            }
        }

        // Then, show the next one
        if (mDialogsInWaiting.size() > 0 && isResumed()) {
            Pair<String, QueueableDialogFragment> pair = mDialogsInWaiting.remove(0);
            pair.second.show(getChildFragmentManager(), pair.first);
            mDialogBlockingTag = pair.first;
        }
        else mDialogBlockingTag = null;
    }

    private void showNotificationsPermissionDeniedDialog() {
        SharedPreferences prefs = requireActivity().getPreferences(MODE_PRIVATE);
        boolean dialogShown = prefs.getBoolean(NOTIFICATIONS_PERMISSION_DIALOG_SHOWN, false);
        if (!dialogShown)
            queueDialog(new DialogNotificationPermissionDeniedFragment(), DIALOG_TAG_NOTIFICATIONS_DENIED);
    }

    /**
     * Convenience function for showing a {@link Snackbar}.
     *
     * @param duration    a {@code Snackbar.LENGTH_*} constant, passed to {@link Snackbar#make(View,
     *                    CharSequence, int)}
     * @param stringResId a resource ID for a string
     * @param formatArgs  arguments to format the string with
     */
    private void showSnackbar(int duration, int stringResId, Object... formatArgs) {
        String string = getString(stringResId, formatArgs);
        View coordinator = mViewBinding.timerCoordinator;
        Snackbar snackbar = Snackbar.make(coordinator, string, duration);
        Resources res = getResources();
        snackbar.setBackgroundTint(res.getColor(R.color.snackbar_background));
        snackbar.setTextColor(res.getColor(R.color.snackbar_text));
        View snackbarText = snackbar.getView();
        TextView textView = snackbarText.findViewById(com.google.android.material.R.id.snackbar_text);
        if (textView != null) textView.setMaxLines(5);
        snackbar.show();
    }

    /**
     * Returns the number of seconds that would be displayed, taking into account the count
     * direction, for the currently active debate phase. If the overall count direction is {@code
     * COUNT_DOWN} and there is a speech  format ready, it returns (speechLength - time). Otherwise,
     * it just returns time.
     *
     * @param time the time that is wished to be formatted (in seconds)
     * @return the time that would be displayed (as an integer, number of seconds)
     * @see #subtractFromSpeechLengthIfCountingDown(long, DebatePhaseFormat)
     */
    private long subtractFromSpeechLengthIfCountingDown(long time) {
        if (mDebateManager != null)
            return subtractFromSpeechLengthIfCountingDown(time, mDebateManager.getActivePhaseFormat());
        return time;
    }

    /**
     * Returns the number of seconds that would be displayed, taking into account the count
     * direction, for the given debate phase.  If the overall count direction is
     * <code>COUNT_DOWN</code> and there is a speech format ready, it returns (speechLength - time).
     * Otherwise, it just returns time.
     *
     * @param time the time that is wished to be formatted (in seconds)
     * @param sf   the relevant {@link DebatePhaseFormat}
     * @return the time that would be displayed (as an integer, number of seconds)
     * @see #subtractFromSpeechLengthIfCountingDown(long, DebatePhaseFormat)
     */
    private long subtractFromSpeechLengthIfCountingDown(long time, DebatePhaseFormat sf) {
        if (getCountDirection(sf) == CountDirection.COUNT_DOWN)
            return sf.getLength() - time;
        return time;
    }

    /**
     *  Updates the buttons according to the current status of the debate
     *  The buttons are allocated as follows:
     *  <ul>
     *  <li>When at start:          [Start] [Next] </li>
     *  <li>When running:           [Stop] </li>
     *  <li>When stopped by user:   [Resume] [Restart] [Next] </li>
     *  <li>When stopped by alarm:  [Resume] </li>
     *  </ul>
     *  The [Bell] button always is on the right of any of the above three buttons.
     */
    private void updateControls() {
        if (mDebateManager != null && mTimerDisplay != null) {

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

            mTimerDisplay.timerCurrentTime.setVisibility((mIsEditingTime) ? View.GONE : View.VISIBLE);
            mTimerDisplay.timerCurrentTimePicker.setVisibility((mIsEditingTime) ? View.VISIBLE : View.GONE);

            setButtonsEnable(!mIsEditingTime);
            mTimerDisplay.timerCurrentTime.setLongClickable(!mDebateManager.isRunning());
            mViewPager.setPagingEnabled(!mIsEditingTime && !mDebateManager.isRunning());

        } else {
            // If no debate is loaded, show only one control button, which leads the user to
            // choose a style. (Keep the play bell button enabled.)
            setButtons(null, null, null);
            mViewBinding.timerLeftControlButton.setEnabled(false);
            mViewBinding.timerCentreControlButton.setEnabled(false);
            mViewBinding.timerRightControlButton.setEnabled(false);

            // This seems counter-intuitive, but we enable paging if there is no debate loaded,
            // as there is only one page anyway, and this way the "scrolled to the limit"
            // indicators appear on the screen.
            mViewPager.setPagingEnabled(true);
        }

        // Show or hide the [Bell] button
        updatePlayBellButton();
    }

    /**
     * Populates the fields in the debate load error display.
     */
    private void updateDebateLoadErrorDisplay() {
        DebateLoadErrorBinding binding = mViewBinding.timerDebateLoadError;
        binding.debateLoadErrorMessage.setText(mDebateLoadError);
        binding.debateLoadErrorTitle.setText(R.string.debateLoadErrorScreen_title);
        binding.debateLoadErrorFileName.setText(getString(R.string.debateLoadErrorScreen_filename, mFormatXmlFileName));
        binding.debateLoadErrorChooseAnother.setText(R.string.debateLoadError_suffix);
        binding.debateLoadErrorChooseStyleButton.setText(R.string.debateLoadErrorScreen_button);
        binding.debateLoadErrorChooseStyleButton.setOnClickListener(
                (v) -> navigateToFormatChooser(null)
        );
    }

    /**
     * Updates the debate timer display with the current active debate phase information.
     */
    private void updateMainDisplay() {

        if (mDebateManager == null && mDebateLoadError != null) {
            Log.w(TAG, "no debate manager, setting error view");
            mViewPager.setVisibility(View.GONE);
            mViewBinding.timerNoDebateLoaded.getRoot().setVisibility(View.GONE);
            mViewBinding.timerDebateLoadError.getRoot().setVisibility(View.VISIBLE);
            updateDebateLoadErrorDisplay();

        } else if (mDebateManager == null) {
            Log.w(TAG, "no debate manager, setting no-debate view");
            mViewPager.setVisibility(View.GONE);
            mViewBinding.timerNoDebateLoaded.getRoot().setVisibility(View.VISIBLE);
            mViewBinding.timerDebateLoadError.getRoot().setVisibility(View.GONE);

        } else {
            mViewPager.setVisibility(View.VISIBLE);
            mViewBinding.timerNoDebateLoaded.getRoot().setVisibility(View.GONE);
            mViewBinding.timerDebateLoadError.getRoot().setVisibility(View.GONE);

            if (mTimerDisplay != null) {
                updateDebateTimerDisplay(mTimerDisplay,
                        mDebateManager.getActivePhaseFormat(),
                        mDebateManager.getActivePhaseCurrentPeriodInfo(),
                        mDebateManager.getActivePhaseName(),
                        mDebateManager.getActivePhaseCurrentTime(),
                        mDebateManager.getActivePhaseNextOvertimeBellTime());
            }
            else Log.w(TAG, "mDebateTimerDisplay is null");
        }
    }

    /**
     * Updates a debate timer display with relevant information.
     * @param binding a {@link DebateTimerDisplayBinding} to populate.
     * @param dpf the {@link DebatePhaseFormat} to be displayed
     * @param pi the {@link PeriodInfo} to be displayed, should be the current one
     * @param phaseName the name of the debate phase
     * @param time the current time in the debate phase
     * @param nextOvertimeBellTime the next overtime bell in the debate phase
     */
    private void updateDebateTimerDisplay(@NonNull DebateTimerDisplayBinding binding, DebatePhaseFormat dpf,
            PeriodInfo pi, String phaseName, long time, Long nextOvertimeBellTime) {

        // If it passed all those checks, populate the timer display
        TextView periodDescriptionText = binding.timerPeriodDescriptionText;
        TextView speechNameText = binding.timerSpeechNameText;
        TextView currentTimeText = binding.timerCurrentTime;
        TextView infoLineText = binding.timerInformationLine;

        // The information at the top of the screen
        speechNameText.setText(phaseName);
        periodDescriptionText.setText(pi.getDescription());

        // Take count direction into account for display
        long timeToShow = subtractFromSpeechLengthIfCountingDown(time, dpf);

        currentTimeText.setText(DebatekeeperUtils.secsToTextSigned(timeToShow));

        boolean overtime = time > dpf.getLength();

        // Colours
        int currentTimeTextColor = getResources().getColor((overtime) ? R.color.overtimeTextColour : android.R.color.primary_text_dark);
        int backgroundColour = getBackgroundColorFromPeriodInfo(dpf, pi);

        // If we're updating the current display (as opposed to an inactive debate phase), then
        // don't update colours if there is a flash screen in progress.
        boolean displayIsActive = binding == mTimerDisplay;
        boolean semaphoreAcquired = displayIsActive && mFlashScreenSemaphore.tryAcquire();

        // If not current display, or we got the semaphore, we're good to go.  If not, don't bother.
        if (!displayIsActive || semaphoreAcquired) {
            updateDebateTimerDisplayColours(binding, currentTimeTextColor, backgroundColour);
            if (semaphoreAcquired) mFlashScreenSemaphore.release();
        }

        // Construct the line that goes at the bottom
        StringBuilder infoLine = new StringBuilder();

        // First, length...
        long length = dpf.getLength();
        String lengthStr;
        if (length % 60 == 0)
            lengthStr = getResources().getQuantityString(R.plurals.timer_timeInMinutes, (int) (length / 60), length / 60);
        else
            lengthStr = DebatekeeperUtils.secsToTextSigned(length);

        int finalTimeTextUnformattedResId = (dpf.isPrep()) ? R.string.timer_prepTimeLength : R.string.timer_speechLength;
        infoLine.append(String.format(this.getString(finalTimeTextUnformattedResId),
                lengthStr));

        if (dpf.isPrep()) {
            PrepTimeFormat ptf = (PrepTimeFormat) dpf;
            if (ptf.isControlled())
                infoLine.append(getString(R.string.timer_prepTimeControlledIndicator));
        }

        // ...then, if applicable, bells
        List<BellInfo> currentSpeechBells = dpf.getBellsSorted();
        Iterator<BellInfo> currentSpeechBellsIter = currentSpeechBells.iterator();

        if (overtime) {
            // show next overtime bell (don't bother with list of bells anymore)
            if (nextOvertimeBellTime == null)
                infoLine.append(getString(R.string.timer_bellsList_noOvertimeBells));
            else {
                long timeToDisplay = subtractFromSpeechLengthIfCountingDown(nextOvertimeBellTime, dpf);
                infoLine.append(getString(R.string.timer_bellsList_nextOvertimeBell,
                        DebatekeeperUtils.secsToTextSigned(timeToDisplay)));
            }

        } else if (currentSpeechBellsIter.hasNext()) {
            // Convert the list of bells into a string.
            StringBuilder bellsStr = new StringBuilder();

            while (currentSpeechBellsIter.hasNext()) {
                BellInfo bi = currentSpeechBellsIter.next();
                long bellTime = subtractFromSpeechLengthIfCountingDown(bi.getBellTime(), dpf);
                bellsStr.append(DebatekeeperUtils.secsToTextSigned(bellTime));
                if (bi.isPauseOnBell())
                    bellsStr.append(getString(R.string.timer_pauseOnBellIndicator));
                if (bi.isSilent())
                    bellsStr.append(getString(R.string.timer_silentBellIndicator));
                if (currentSpeechBellsIter.hasNext())
                    bellsStr.append(", ");
            }

            infoLine.append(getResources().getQuantityString(R.plurals.timer_bellsList_normal, currentSpeechBells.size(), bellsStr));

        } else {
            infoLine.append(getString(R.string.timer_bellsList_noBells));
        }

        infoLineText.setText(infoLine.toString());

        // Update the POI timer button
        updatePoiTimerButton(binding, dpf);

    }

    /**
     * @param binding a {@link ViewBinding}, normally a {@link DebateTimerDisplayBinding},
     * except in cases where no debate is loaded or something like that.
     * @param timeTextColour the text colour to use for the current time
     * @param backgroundColour the colour to use for the background
     */
    private void updateDebateTimerDisplayColours(DebateTimerDisplayBinding binding, int timeTextColour, int backgroundColour) {

        if (binding == null) return;

        switch (mBackgroundColourArea) {
        case TOP_BAR_ONLY:
            // These would only be expected to exist if the view given is the debate timer display
            binding.timerSpeechNameText.setBackgroundColor(backgroundColour);
            binding.timerPeriodDescriptionText.setBackgroundColor(backgroundColour);
            break;
        case WHOLE_SCREEN:
            binding.getRoot().setBackgroundColor(backgroundColour);
            break;
        case DISABLED:
        	// Do nothing
        }

        // This would only be expected to exist if the view given is the debate timer display
        binding.timerCurrentTime.setTextColor(timeTextColour);
    }

    /**
     * Updates the GUI (in the general case).
     */
    private void updateGui() {
        if (mIsChangingPages) {
            Log.d(TAG, "Changing pages, don't update GUI");
            return;
        }
        if (!isVisible()) {
            Log.d(TAG, "Not visible, don't update GUI");
            return;
        }

        mPreviousSpeechBackPressedCallback.setEnabled(
                mDebateManager != null && !mDebateManager.isInFirstPhase() && !mDebateManager.isRunning());

        updateMainDisplay();
        updateControls();
        updateToolbar();
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
     * This is a no-op if this fragment is not attached to an activity.
     */
    private void updateKeepScreenOn() {
        boolean relevantKeepScreenOn;
        Activity activity = getActivity();
        if (activity == null) return;

        if (mDebateManager != null && mDebateManager.getActivePhaseFormat().isPrep())
            relevantKeepScreenOn = mPrepTimeKeepScreenOn;
        else
            relevantKeepScreenOn = mSpeechKeepScreenOn;

        if (relevantKeepScreenOn && mDebateManager != null && mDebateManager.isRunning())
            activity.getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        else
            activity.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    private void updatePlayBellButton() {
        if (mServiceBinder != null)
            mViewBinding.timerPlayBellButton.setVisibility((mServiceBinder.getAlertManager().isBellsEnabled()) ? View.VISIBLE : View.GONE);
    }

    /**
     * @param binding the {@link DebateTimerDisplayBinding} to be updated
     * @param dpf the {@link DebatePhaseFormat} relevant for this <code>debateTimerDisplay</code>
     */
    private void updatePoiTimerButton(@NonNull DebateTimerDisplayBinding binding, DebatePhaseFormat dpf) {
        Button poiButton = binding.timerPoiTimerButton;

        // Display only when user has POI timer enabled, and a debate is loaded and the current
        // speech has POIs in it.
        if (mPoiTimerEnabled && dpf.getClass() == SpeechFormat.class && ((SpeechFormat) dpf).hasPoisAllowedSomewhere()) {
            poiButton.setVisibility(View.VISIBLE);

            // If POIs are currently active, enable the button
            if (mDebateManager != null && mDebateManager.isPoisActive()) {
                poiButton.setEnabled(mDebateManager.isRunning());

                Long poiTime = mDebateManager.getCurrentPoiTime();
                if (poiTime == null)
                    poiButton.setText(R.string.timer_poiTimer_buttonText);
                else
                    //noinspection AndroidLintDefaultLocale
                    poiButton.setText(String.format("%d", poiTime));

            // Otherwise, disable it
            } else {
                poiButton.setText(R.string.timer_poiTimer_buttonText);
                poiButton.setEnabled(false);
            }

        // Otherwise, hide the button
        } else {
            poiButton.setVisibility(View.GONE);
        }
    }

    /**
     * Updates the toolbar according to the options that should be available, given the current
     * debate load status and "ring bells" setting. This includes the title.
     */
    private void updateToolbar() {
        if (mViewBinding == null) {
            Log.d(TAG, "No view binding, skip update toolbar");
            return;
        }

        Toolbar toolbar = mViewBinding.toolbarDebatingTimer;

        // update the title
        if (mDebateManager != null) {
            String shortName = mDebateManager.getDebateFormatShortName();
            if (shortName != null)
                toolbar.setTitle(shortName);
            else
                toolbar.setTitle(mDebateManager.getDebateFormatName());
        }
        else toolbar.setTitle(R.string.fragmentName_Debating_withoutFormat);

        Menu menu = toolbar.getMenu();

        // show or hide the debate menu button
        MenuItem resetDebateItem = menu.findItem(R.id.timer_menuItem_resetDebate);
        resetDebateItem.setVisible(mDebateManager != null);

        // display the appropriate bells icon
        MenuItem ringBellsItem = menu.findItem(R.id.timer_menuItem_ringBells);
        ringBellsItem.setChecked(mBellsEnabled);
        ringBellsItem.setIcon((mBellsEnabled) ? R.drawable.ic_baseline_notifications_active_24 : R.drawable.ic_baseline_notifications_off_24);
    }

}
