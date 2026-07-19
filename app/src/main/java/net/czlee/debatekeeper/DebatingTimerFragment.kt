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

package net.czlee.debatekeeper

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.app.Dialog
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.text.Html
import android.text.Spanned
import android.text.method.LinkMovementMethod
import android.util.Log
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.widget.Toolbar
import androidx.core.content.ContextCompat
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentResultListener
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.navigation.fragment.NavHostFragment
import androidx.preference.PreferenceManager
import androidx.viewpager.widget.PagerAdapter
import androidx.viewpager.widget.ViewPager
import com.google.android.material.snackbar.Snackbar
import net.czlee.debatekeeper.AlertManager.FlashScreenMode
import net.czlee.debatekeeper.databinding.DebateTimerDisplayBinding
import net.czlee.debatekeeper.databinding.DialogWithDontShowBinding
import net.czlee.debatekeeper.databinding.FragmentDebateBinding
import net.czlee.debatekeeper.debateformat.DebateFormat
import net.czlee.debatekeeper.debateformat.DebateFormatBuilderFromXmlForSchema2
import net.czlee.debatekeeper.debateformat.DebateFormatFieldExtractor
import net.czlee.debatekeeper.debateformat.DebatePhaseFormat
import net.czlee.debatekeeper.debateformat.PeriodInfo
import net.czlee.debatekeeper.debateformat.PrepTimeFormat
import net.czlee.debatekeeper.debateformat.SpeechFormat
import net.czlee.debatekeeper.debatemanager.DebateManager
import net.czlee.debatekeeper.debatemanager.DebatePhaseManager.DebateTimerState
import org.xml.sax.SAXException
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit

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
class DebatingTimerFragment : Fragment() {

    private var mServiceBinder: DebatingTimerService.DebatingTimerServiceBinder? = null
    private val mServiceConnection: ServiceConnection = DebatingTimerServiceConnection()

    private var mDebateManager: DebateManager? = null
    private var mDebateLoadError: Spanned? = null
    private var mLastStateBundle: Bundle? = null
    private var mIsEditingTime = false
    private var mIsOpeningFormatChooser = false
    private val mFlashScreenSemaphore = Semaphore(1, true)

    private var mTimerDisplay: DebateTimerDisplayBinding? = null
    private var mViewPager: EnableableViewPager? = null
    private var mIsChangingPages = false

    private var mViewBinding: FragmentDebateBinding? = null

    private var mFormatXmlFileName: String? = null
    private var mCountDirection = CountDirection.COUNT_UP
    private var mPrepTimeCountDirection = CountDirection.COUNT_DOWN
    private var mBackgroundColourArea = BackgroundColourArea.WHOLE_SCREEN
    private var mPoiTimerEnabled = true
    private var mBellsEnabled = true
    private var mSpeechKeepScreenOn = false
    private var mPrepTimeKeepScreenOn = false
    private var mImportIntentHandled = false

    private var mDialogBlockingTag: String? = null
    private val mDialogsInWaiting = ArrayList<Pair<String, QueueableDialogFragment>>()

    private val mGuiUpdateBroadcastReceiver: BroadcastReceiver = GuiUpdateBroadcastReceiver()

    private val mFinishEditingTimeBackPressedCallback = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() {
            editCurrentTimeFinish(false)
        }
    }

    private val mPreviousSpeechBackPressedCallback = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() {
            goToPreviousSpeech()
        }
    }

    private val CONTROL_BUTTON_START_TIMER = ControlButtonSpec(
            R.string.timer_controlButton_startTimer_text) {
        mDebateManager!!.startTimer()
        updateGui()
        updateKeepScreenOn()
    }
    private val CONTROL_BUTTON_STOP_TIMER = ControlButtonSpec(
            R.string.timer_controlButton_stopTimer_text) {
        mDebateManager!!.stopTimer()
        updateGui()
        updateKeepScreenOn()
    }
    private val CONTROL_BUTTON_RESET_TIMER = ControlButtonSpec(
            R.string.timer_controlButton_resetTimer_text) {
        mDebateManager!!.resetActivePhase()
        updateGui()
    }
    private val CONTROL_BUTTON_RESUME_TIMER = ControlButtonSpec(
            R.string.timer_controlButton_resumeTimer_text) {
        mDebateManager!!.startTimer()
        updateGui()
        updateKeepScreenOn()
    }
    private val CONTROL_BUTTON_NEXT_PHASE = ControlButtonSpec(
            R.string.timer_controlButton_nextPhase_text) {
        goToNextSpeech()
        updateGui()
    }

    private val mRequestPermissionLauncher = registerForActivityResult(
            ActivityResultContracts.RequestPermission()) { isGranted ->
        if (!isGranted) {
            Log.e(TAG, "mRequestPermissionLauncher: permission denied")
            showNotificationsPermissionDeniedDialog()
        }
    }

    //******************************************************************************************
    // Public classes
    //******************************************************************************************

    open class QueueableDialogFragment : DialogFragment() {

        override fun onDismiss(dialog: android.content.DialogInterface) {
            super.onDismiss(dialog)
            val parent: DebatingTimerFragment?
            try {
                parent = parentFragment as DebatingTimerFragment?
            } catch (e: ClassCastException) {
                Log.e(TAG, "QueueableDialogFragment.onDismiss: class cast exception in QueueableDialogFragment")
                return
            }
            if (parent != null) {
                parent.showNextQueuedDialog()
            } else {
                Log.w(TAG, "QueueableDialogFragment.onDismiss: parent was null")
            }
        }
    }

    class DialogChangelogFragment : QueueableDialogFragment() {

        override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
            val res = resources
            val activity: Activity = requireActivity()
            val binding = DialogWithDontShowBinding.inflate(LayoutInflater.from(activity))
            binding.message.setText(R.string.changelogDialog_message)

            val builder = AlertDialog.Builder(activity)
            builder.setTitle(res.getString(R.string.changelogDialog_title))
                    .setView(binding.root)
                    .setPositiveButton(res.getString(R.string.changelogDialog_ok)) { _, _ ->
                        // Take note of "do not show again" setting
                        if (binding.dontShowAgain.isChecked) {
                            val prefs = activity.getPreferences(Context.MODE_PRIVATE)
                            val thisChangelogVersionCode =
                                    res.getInteger(R.integer.changelogDialog_versionCode)
                            prefs.edit().putInt(LAST_CHANGELOG_VERSION_SHOWN,
                                    thisChangelogVersionCode).apply()
                        }
                    }

            return builder.create()
        }
    }

    class DialogNotificationPermissionDeniedFragment : QueueableDialogFragment() {

        override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
            val builder = AlertDialog.Builder(requireContext())
            builder.setMessage(R.string.notificationsPermissionDenied_message)
                    .setPositiveButton(R.string.notificationsPermissionDenied_button) { _, _ ->
                        val prefs = requireActivity().getPreferences(Context.MODE_PRIVATE)
                        prefs.edit().putBoolean(NOTIFICATIONS_PERMISSION_DIALOG_SHOWN, true)
                                .apply()
                    }

            return builder.create()
        }
    }

    class DialogImportFileConfirmFragment : QueueableDialogFragment() {

        override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
            val parent = parentFragment as DebatingTimerFragment
            val builder = AlertDialog.Builder(requireContext())
            val args = requireArguments()

            val incomingFilename = args.getString(DIALOG_ARGUMENT_FILE_NAME)
            val incomingStyleName = args.getString(DIALOG_ARGUMENT_INCOMING_STYLE_NAME)
            val existingStyleName =
                    args.getString(DIALOG_ARGUMENT_EXISTING_STYLE_NAME, "<unknown>")
            val message = StringBuilder(getString(
                    R.string.importDebateFormat_dialog_message_question,
                    incomingFilename, incomingStyleName))

            if (args.getBoolean(DIALOG_ARGUMENT_EXISTS)) {
                message.append("\n\n")
                if (incomingStyleName != null && incomingStyleName == existingStyleName)
                    message.append(getString(
                            R.string.importDebateFormat_dialog_addendum_overwriteExistingSameName,
                            existingStyleName))
                else
                    message.append(getString(
                            R.string.importDebateFormat_dialog_addendum_overwriteExistingDifferentName,
                            existingStyleName))
            }

            @Suppress("deprecation")
            builder.setTitle(R.string.importDebateFormat_dialog_title)
                    .setMessage(Html.fromHtml(message.toString()))
                    .setPositiveButton(R.string.importDebateFormat_dialog_button_yes) { _, _ ->
                        parent.importIncomingFile(incomingFilename!!)
                    }
                    .setNegativeButton(R.string.importDebateFormat_dialog_button_no) { _, _ ->
                        parent.mImportIntentHandled = true
                    }

            val dialog = builder.create()
            dialog.setCanceledOnTouchOutside(false)
            return dialog
        }

        companion object {
            internal fun newInstance(incomingFilename: String, incomingStyleName: String,
                                     exists: Boolean, existingStyleName: String?):
                    DialogImportFileConfirmFragment {
                val fragment = DialogImportFileConfirmFragment()
                val args = Bundle()
                args.putString(DIALOG_ARGUMENT_FILE_NAME, incomingFilename)
                args.putString(DIALOG_ARGUMENT_INCOMING_STYLE_NAME, incomingStyleName)
                args.putBoolean(DIALOG_ARGUMENT_EXISTS, exists)
                args.putString(DIALOG_ARGUMENT_EXISTING_STYLE_NAME, existingStyleName)
                fragment.arguments = args
                return fragment
            }
        }
    }

    class DialogSuggestReplacementFragment : QueueableDialogFragment() {

        override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
            val parent = parentFragment as DebatingTimerFragment
            val context = requireContext()
            val builder = AlertDialog.Builder(context)
            val args = requireArguments()

            val incomingFilename = args.getString(DIALOG_ARGUMENT_FILE_NAME)
            val suggestedFilename = args.getString(DIALOG_ARGUMENT_SUGGESTED_FILE_NAME)

            @Suppress("deprecation")
            builder.setTitle(R.string.replaceDebateFormat_dialog_title)
                    .setMessage(Html.fromHtml(getString(R.string.replaceDebateFormat_dialog_message,
                            args.getString(DIALOG_ARGUMENT_INCOMING_STYLE_NAME),
                            suggestedFilename, incomingFilename)))
                    .setPositiveButton(R.string.replaceDebateFormat_dialog_button_replace) { _, _ ->
                        parent.importIncomingFile(suggestedFilename!!)
                    }
                    .setNeutralButton(R.string.replaceDebateFormat_dialog_button_addNew) { _, _ ->
                        parent.importIncomingFile(incomingFilename!!)
                    }
                    .setNegativeButton(R.string.replaceDebateFormat_dialog_button_cancel) { _, _ ->
                        parent.mImportIntentHandled = true
                    }

            val dialog = builder.create()
            dialog.setCanceledOnTouchOutside(false)
            return dialog
        }

        companion object {
            internal fun newInstance(incomingFilename: String, styleName: String,
                                     suggestedFilename: String): DialogSuggestReplacementFragment {
                val fragment = DialogSuggestReplacementFragment()
                val args = Bundle()
                args.putString(DIALOG_ARGUMENT_FILE_NAME, incomingFilename)
                args.putString(DIALOG_ARGUMENT_INCOMING_STYLE_NAME, styleName)
                args.putString(DIALOG_ARGUMENT_SUGGESTED_FILE_NAME, suggestedFilename)
                fragment.arguments = args
                return fragment
            }
        }
    }

    class DialogSchemaTooNewFragment : QueueableDialogFragment() {

        override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
            val activity = requireActivity() as DebatingActivity
            val args = requireArguments()

            val schemaUsed = args.getString(DIALOG_ARGUMENT_SCHEMA_USED)
            val schemaSupported = args.getString(DIALOG_ARGUMENT_SCHEMA_SUPPORTED)

            val appVersion: String = try {
                activity.packageManager.getPackageInfo(activity.packageName, 0).versionName
                        ?: "unknown"
            } catch (e: PackageManager.NameNotFoundException) {
                "unknown"
            }

            val message = getString(R.string.schemaTooNewDialog_message,
                    schemaUsed, schemaSupported, appVersion,
                    args.getString(DIALOG_ARGUMENT_FILE_NAME))

            val builder = AlertDialog.Builder(activity)
            builder.setTitle(R.string.schemaTooNewDialog_title)
                    .setMessage(message)
                    .setPositiveButton(R.string.schemaTooNewDialog_button_upgrade) { _, _ ->
                        // Open Google Play to upgrade
                        val uri = Uri.parse(getString(R.string.app_marketUri))
                        val intent = Intent(Intent.ACTION_VIEW)
                        intent.data = uri
                        startActivity(intent)
                    }
                    .setNegativeButton(R.string.schemaTooNewDialog_button_ignore, null)

            val dialog = builder.create()
            dialog.setCanceledOnTouchOutside(false)
            return dialog
        }

        companion object {
            internal fun newInstance(schemaUsed: String?, schemaSupported: String?,
                                     filename: String?): DialogSchemaTooNewFragment {
                val fragment = DialogSchemaTooNewFragment()
                val args = Bundle()
                args.putString(DIALOG_ARGUMENT_SCHEMA_USED, schemaUsed)
                args.putString(DIALOG_ARGUMENT_SCHEMA_SUPPORTED, schemaSupported)
                args.putString(DIALOG_ARGUMENT_FILE_NAME, filename)
                fragment.arguments = args
                return fragment
            }
        }
    }

    //******************************************************************************************
    // Private classes
    //******************************************************************************************

    private enum class BackgroundColourArea(private val key: String) {
        // These must match the values string array in the preference.xml file.
        // (We can pull strings from the resource automatically,
        // but we can't assign them to enums automatically.)
        DISABLED("disabled"),
        TOP_BAR_ONLY("topBarOnly"),
        WHOLE_SCREEN("wholeScreen");

        companion object {
            fun toEnum(key: String): BackgroundColourArea {
                for (value in entries)
                    if (key == value.key)
                        return value
                throw IllegalArgumentException(
                        String.format("There is no enumerated constant '%s'", key))
            }
        }
    }

    private class ControlButtonSpec(val textResId: Int, val onClickListener: View.OnClickListener)

    private enum class CountDirection(private val key: String) {
        // These must match the values string array in the preference.xml file.
        // (We can pull strings from the resource automatically,
        // but we can't assign them to enums automatically.)
        COUNT_UP("alwaysUp"),
        COUNT_DOWN("alwaysDown");

        companion object {
            fun toEnum(key: String): CountDirection {
                for (value in entries)
                    if (key == value.key)
                        return value
                throw IllegalArgumentException(
                        String.format("There is no enumerated constant '%s'", key))
            }
        }
    }

    private inner class DebateTimerDisplayOnPageChangeListener :
            ViewPager.SimpleOnPageChangeListener() {

        override fun onPageSelected(position: Int) {
            // Enable the lock that prevents updateGui() from running while pages are changing.
            // This is necessary to prevent updateGui() from updating the wrong view after this
            // method is run (and the active phase index changed) and before
            // DebateTimerDisplayPagerAdapter#setPrimaryItem() is called (and the view pointer
            // updated).
            val debateManager = mDebateManager
            if (debateManager != null) {
                mIsChangingPages = true
                debateManager.activePhaseIndex = position
            }
            updateControls()
        }
    }

    /**
     * Implementation of [PagerAdapter] that pages through the various speeches of a debate
     * managed by a [DebateManager].
     *
     * @author Chuan-Zheng Lee
     * @since 2013-06-10
     */
    private inner class DebateTimerDisplayPagerAdapter : PagerAdapter() {

        private val mDisplaysMap = HashMap<DebateManager.DebatePhaseTag, DebateTimerDisplayBinding>()

        override fun destroyItem(container: ViewGroup, position: Int, `object`: Any) {
            val dpt = `object` as DebateManager.DebatePhaseTag
            val binding = mDisplaysMap[dpt]
            if (binding == null)
                Log.e(PAGER_TAG, "Nothing found to destroy at position $position - $`object`")
            else
                container.removeView(binding.root)
            mDisplaysMap.remove(dpt)
        }

        override fun getCount(): Int {
            return mDebateManager?.numberOfPhases ?: 0
        }

        override fun getItemPosition(`object`: Any): Int {
            val tag = `object` as DebateManager.DebatePhaseTag
            val debateManager = mDebateManager
            return if ((debateManager == null) != (NO_DEBATE_LOADED == tag.specialTag)) {
                // If it was the "no debate loaded" screen and there is now a debate loaded,
                // then the View no longer exists.  Likewise if there is no debate loaded and
                // there was anything but the "no debate loaded" screen.
                Log.e(PAGER_TAG, "getItemPosition: returning POSITION_NONE")
                POSITION_NONE

            } else if (debateManager == null) {
                // If it was "no debate loaded" and there is still no debate loaded, it's
                // unchanged. This should never happen, but just in case.
                Log.e(PAGER_TAG, "getItemPosition: returning POSITION_UNCHANGED")
                POSITION_UNCHANGED
            }

            // If there's no messy debate format changing or loading, delegate this function to
            // the DebateManager.
            else debateManager.getPhaseIndexForTag(tag)
        }

        override fun instantiateItem(container: ViewGroup, position: Int): Any {
            val context = requireContext()

            val debateManager = mDebateManager
            if (debateManager == null) {
                // Return a blank view that should never be seen, since I think we have to add
                // something.
                Log.e(PAGER_TAG, "Tried to instantiate ViewPager item with no debate loaded")
                container.addView(View(context))
                val tag = DebateManager.DebatePhaseTag()
                tag.specialTag = NO_DEBATE_LOADED
                return tag
            }

            // The View for the position in question is the inflated debate_timer_display for
            // the relevant timer (prep time or speech).
            val vb = DebateTimerDisplayBinding.inflate(LayoutInflater.from(context))

            // OnTouchListeners
            vb.timerRoot.setOnClickListener { editCurrentTimeFinish(true) }
            vb.timerCurrentTime.setOnLongClickListener {
                editCurrentTimeStart()
                true
            }

            // Set the time picker to 24-hour time
            vb.timerCurrentTimePicker.setIs24HourView(true)

            // Set the POI timer OnClickListener
            vb.timerPoiTimerButton.setOnClickListener(PoiButtonOnClickListener())

            // Update the debate timer display
            val time = debateManager.getPhaseCurrentTime(position)
            val dpf = debateManager.getPhaseFormat(position)
            val pi = dpf.getPeriodInfoForTime(time)

            updateDebateTimerDisplay(vb, dpf, pi,
                    debateManager.getPhaseName(position), time,
                    debateManager.getPhaseNextOvertimeBellTime(position))

            container.addView(vb.root)

            // Retrieve a tag and take note of it.
            val tag = debateManager.getPhaseTagForIndex(position)
            mDisplaysMap[tag] = vb

            return tag
        }

        override fun isViewFromObject(view: View, `object`: Any): Boolean {
            val dpt = `object` as DebateManager.DebatePhaseTag
            if (!mDisplaysMap.containsKey(dpt))
                return false
            val binding = mDisplaysMap[dpt] ?: return false
            return binding.root === view
        }

        override fun setPrimaryItem(container: ViewGroup, position: Int, `object`: Any) {
            val original = mTimerDisplay

            val dpt = `object` as DebateManager.DebatePhaseTag
            mTimerDisplay = mDisplaysMap[dpt]

            // Disable the lock that prevents updateGui() from running while the pages are
            // changing.
            mIsChangingPages = false

            // This method seems to be called multiple times on each update.
            // To save unnecessary work (i.e. for performance), only run (the
            // relatively-intensive) updateGui if mDebateTimerDisplay has actually changed.
            if (original !== mTimerDisplay)
                updateGui()
        }

        /**
         * Refreshes all the background colours known to this [PagerAdapter].
         * This should be called when a background colour user preference is changed, in a way
         * that requires all of the background colours in all [View]s known to be refreshed.
         * Before calling this method, [resetBackgroundColoursToTransparent]
         * should be called to reset all of the other background colours to transparent.
         */
        internal fun refreshBackgroundColours() {
            val debateManager = mDebateManager ?: return
            for ((key, value) in mDisplaysMap) {
                val phaseIndex = debateManager.getPhaseIndexForTag(key)
                val dpf = debateManager.getPhaseFormat(phaseIndex)
                val time = debateManager.getPhaseCurrentTime(phaseIndex)
                val pi = dpf.getPeriodInfoForTime(time)
                val backgroundColour = getBackgroundColorFromPeriodInfo(dpf, pi)
                val overtime = time > dpf.length
                @Suppress("deprecation")
                val timeTextColour = resources.getColor(
                        if (overtime) R.color.overtimeTextColour
                        else android.R.color.primary_text_dark)
                updateDebateTimerDisplayColours(value, timeTextColour, backgroundColour)
            }
        }
    }

    private inner class DebatingTimerMenuItemClickListener : Toolbar.OnMenuItemClickListener {

        override fun onMenuItemClick(item: MenuItem): Boolean {
            editCurrentTimeFinish(false)
            when (item.itemId) {
                R.id.timer_menuItem_chooseFormat -> {
                    navigateToFormatChooser(mFormatXmlFileName)
                    return true
                }
                R.id.timer_menuItem_resetDebate -> {
                    if (mDebateManager == null) return true
                    resetDebate(false)
                    showSnackbar(SNACKBAR_DURATION_RESET_DEBATE, R.string.timer_snackbar_resetDebate)
                    return true
                }
                R.id.timer_menuItem_settings -> {
                    Log.d(TAG, "opening settings")
                    val action = DebatingTimerFragmentDirections.actionEditSettings()
                    NavHostFragment.findNavController(this@DebatingTimerFragment).navigate(action)
                    return true
                }
                R.id.timer_menuItem_ringBells -> {
                    // Edit the preference, then apply the changes.
                    // Don't fetch the current preference - if there is an inconsistency, we want
                    // the toggle to reflect what this activity thinks silent mode is.
                    val editor =
                            PreferenceManager.getDefaultSharedPreferences(requireContext()).edit()
                    editor.putBoolean(resources.getString(R.string.pref_ringBells_key),
                            !mBellsEnabled)
                    val success = editor.commit() // we want this to block until it returns
                    if (success) applyPreferences() // this will update mBellsEnabled
                    return true
                }
                else -> return false
            }
        }
    }

    private inner class DebatingTimerFlashScreenListener : AlertManager.FlashScreenListener {

        override fun begin(): Boolean {
            return try {
                mFlashScreenSemaphore.tryAcquire(2, TimeUnit.SECONDS)
            } catch (e: InterruptedException) {
                false // Don't bother with the flash screen any more
            }
        }

        override fun done() {
            mFlashScreenSemaphore.release()
        }

        override fun flashScreenOff() {
            val activity = activity ?: return // doesn't matter if no activity is current
            activity.runOnUiThread {
                // Restore the original colours
                // It takes a bit of brain-work to figure out what they should be.  We actually
                // do this brain-work because the correct colours should be considered volatile
                // - this timer can happen at any time so there is no guarantee they haven't
                // changed since the last time we checked.
                val textColour: Int
                val backgroundColour: Int
                val resources = resources
                val debateManager = mDebateManager
                if (debateManager != null) {
                    val dpf = debateManager.activePhaseFormat
                    val overtime = debateManager.activePhaseCurrentTime > dpf.length
                    @Suppress("deprecation")
                    textColour = resources.getColor(
                            if (overtime) R.color.overtimeTextColour
                            else android.R.color.primary_text_dark)
                    backgroundColour = getBackgroundColorFromPeriodInfo(dpf,
                            debateManager.activePhaseCurrentPeriodInfo)
                } else {
                    @Suppress("deprecation")
                    textColour = resources.getColor(android.R.color.primary_text_dark)
                    backgroundColour = COLOUR_TRANSPARENT
                }

                updateDebateTimerDisplayColours(mTimerDisplay, textColour, backgroundColour)

                // Set the background colour of the root view to be black again.
                @Suppress("deprecation")
                mViewBinding!!.timerRootView.setBackgroundColor(
                        resources.getColor(android.R.color.black))
            }
        }

        override fun flashScreenOn(colour: Int) {
            val activity = activity ?: return // doesn't matter if no activity is current
            activity.runOnUiThread {

                // We need to figure out how to colour the text.
                // Basically we want to colour the text to whatever the background colour is now.
                // So the whole screen is coloured, it'll be the current background colour for
                // the current period.  If not, it'll be black (make sure we don't make the
                // text transparent though!).
                val debateManager = mDebateManager
                val invertedTextColour: Int =
                        if (mBackgroundColourArea == BackgroundColourArea.WHOLE_SCREEN
                                && debateManager != null)
                            getBackgroundColorFromPeriodInfo(debateManager.activePhaseFormat,
                                    debateManager.activePhaseCurrentPeriodInfo)
                        else {
                            @Suppress("deprecation")
                            resources.getColor(android.R.color.black)
                        }

                // So we invert the text colour and set all background colours to transparent.
                // Everything will be restored by flashScreenOff().
                updateDebateTimerDisplayColours(mTimerDisplay, invertedTextColour,
                        COLOUR_TRANSPARENT)

                // Having completed preparations, set the background colour of the root view to
                // flash the screen.
                mViewBinding!!.timerRootView.setBackgroundColor(colour)
            }
        }
    }

    /**
     * Defines call-backs for service binding, passed to bindService()
     */
    private inner class DebatingTimerServiceConnection : ServiceConnection {

        override fun onServiceConnected(className: ComponentName, service: IBinder) {
            Log.d(TAG, "service connected")
            mServiceBinder = service as DebatingTimerService.DebatingTimerServiceBinder
            initialiseDebate(true)
            restoreBinder()
        }

        override fun onServiceDisconnected(arg0: ComponentName) {
            Log.d(TAG, "service disconnected")
            mDebateManager = null
            notifyViewPagerDataSetChanged()
        }
    }

    private class FatalXmlError : Exception {
        constructor(detailMessage: String) : super(detailMessage)
        constructor(detailMessage: String, throwable: Throwable) : super(detailMessage, throwable)
    }

    private inner class FormatChooserFragmentResultListener : FragmentResultListener {

        override fun onFragmentResult(requestKey: String, result: Bundle) {
            val outcome = result.getInt(FormatChooserFragment.BUNDLE_KEY_RESULT)
            val filename = result.getString(FormatChooserFragment.BUNDLE_KEY_XML_FILE_NAME)
            if (outcome == FormatChooserFragment.RESULT_UNCHANGED) {
                Log.v(TAG, "Format was unchanged")
            } else if (filename != null) {
                Log.v(TAG, "Got file name $filename")
                setXmlFileName(filename)
                resetDebate(true)
            } else {
                Log.e(TAG, "File name returned was null")
                setXmlFileName(null)
                mServiceBinder?.releaseDebateManager()
                mDebateManager = null
                updateGui()
                updateToolbar()
            }
            mIsOpeningFormatChooser = false // clear flag
        }
    }

    private inner class GuiUpdateBroadcastReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            updateGui()
        }
    }

    private inner class PoiButtonOnClickListener : View.OnClickListener {
        override fun onClick(v: View) {
            val debateManager = mDebateManager ?: return
            if (debateManager.isPoiRunning)
                debateManager.stopPoiTimer()
            else
                debateManager.startPoiTimer()
        }
    }

    //******************************************************************************************
    // Public methods
    //******************************************************************************************

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val activity = requireActivity()

        val dispatcher = activity.onBackPressedDispatcher
        dispatcher.addCallback(this, mPreviousSpeechBackPressedCallback)
        dispatcher.addCallback(this, mFinishEditingTimeBackPressedCallback)

        parentFragmentManager.setFragmentResultListener(
                FormatChooserFragment.REQUEST_KEY_CHOOSE_FORMAT,
                this, FormatChooserFragmentResultListener())

        // If there's a file name passed in (presumably from FormatChooserFragment), use it,
        // otherwise load from preferences.
        val prefs = activity.getPreferences(Context.MODE_PRIVATE)
        mFormatXmlFileName = prefs.getString(PREFERENCE_XML_FILE_NAME, null)

        // Bind to the timer service
        val serviceIntent = Intent(activity, DebatingTimerService::class.java)
        activity.bindService(serviceIntent, mServiceConnection, Context.BIND_AUTO_CREATE)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View {
        val viewBinding = FragmentDebateBinding.inflate(inflater, container, false)
        mViewBinding = viewBinding
        return viewBinding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val viewBinding = mViewBinding!!

        viewBinding.toolbarDebatingTimer.setOnMenuItemClickListener(
                DebatingTimerMenuItemClickListener())
        viewBinding.timerPlayBellButton.setOnClickListener {
            mServiceBinder!!.alertManager!!.playSingleBell()
        }
        viewBinding.timerDebateLoadError.debateLoadErrorChooseStyleButton.setOnClickListener {
            navigateToFormatChooser(null)
        }
        viewBinding.timerNoDebateLoaded.noDebateLoadedChooseStyleButton.setOnClickListener {
            navigateToFormatChooser(null)
        }
        viewBinding.timerDebateLoadError.debateLoadErrorMessage.movementMethod =
                LinkMovementMethod.getInstance()

        // ViewPager
        val viewPager = viewBinding.timerViewPager
        mViewPager = viewPager
        viewPager.adapter = DebateTimerDisplayPagerAdapter()
        viewPager.addOnPageChangeListener(DebateTimerDisplayOnPageChangeListener())
        viewPager.pageMargin = 1
        viewPager.setPageMarginDrawable(R.drawable.divider)

        mLastStateBundle = savedInstanceState // This could be null
        if (savedInstanceState != null)
            mDialogBlockingTag = savedInstanceState.getString(BUNDLE_KEY_BLOCKING_DIALOG)
    }

    override fun onDestroy() {
        super.onDestroy()

        val context = requireContext()
        context.unbindService(mServiceConnection)
    }

    override fun onSaveInstanceState(bundle: Bundle) {
        super.onSaveInstanceState(bundle)
        bundle.putString(BUNDLE_KEY_XML_FILE_NAME, mFormatXmlFileName)
        bundle.putBoolean(BUNDLE_KEY_IMPORT_INTENT_HANDLED, mImportIntentHandled)
        bundle.putString(BUNDLE_KEY_BLOCKING_DIALOG, mDialogBlockingTag)
        mDebateManager?.saveState(BUNDLE_KEY_DEBATE_MANAGER, bundle)
    }

    override fun onStart() {
        super.onStart()

        copyAssetsIfEmpty()

        // If there's an incoming style, and it wasn't handled before a screen rotation, ask the
        // user whether they want to import it.
        val lastStateBundle = mLastStateBundle
        if (lastStateBundle != null) {
            mImportIntentHandled =
                    lastStateBundle.getBoolean(BUNDLE_KEY_IMPORT_INTENT_HANDLED, false)
            Log.d(TAG, "onViewCreated: import intent handled is $mImportIntentHandled")
        }
        if (Intent.ACTION_VIEW == requireActivity().intent.action && !mImportIntentHandled) {
            showDialogToConfirmImport()
        } else if (mFormatXmlFileName == null) {
            // Otherwise, if there's no style loaded, direct the user to choose one
            if (!mIsOpeningFormatChooser) {
                Log.v(TAG, "no file loaded, redirecting to choose format")
                navigateToFormatChooser(null)
                return
            } else {
                Log.v(TAG, "no file loaded, but returned from format chooser, so staying put")
                mIsOpeningFormatChooser = false
            }
        }

        restoreBinder()
        LocalBroadcastManager.getInstance(requireContext()).registerReceiver(
                mGuiUpdateBroadcastReceiver,
                IntentFilter(DebatingTimerService.UPDATE_GUI_BROADCAST_ACTION))
        updateGui()

        showChangelogDialog()

        if (mDebateLoadError != null)
            // May as well try again (sometimes the error is cleared, e.g., if the error was
            // because the file didn't exist, and the user just downloaded it.
            initialiseDebate(false)
    }

    override fun onStop() {
        super.onStop()
        mServiceBinder?.alertManager?.activityStop()
        LocalBroadcastManager.getInstance(requireContext())
                .unregisterReceiver(mGuiUpdateBroadcastReceiver)
    }

    //******************************************************************************************
    // Private methods
    //******************************************************************************************

    /**
     * Gets the preferences from the shared preferences file and applies them. If this fragment is
     * not attached to an activity, it skips the updates that require one.
     */
    private fun applyPreferences() {
        val prefs = PreferenceManager.getDefaultSharedPreferences(requireContext())
        var vibrateMode = false
        val overtimeBellsEnabled: Boolean
        var poiBuzzerEnabled = false
        var poiVibrateEnabled = false
        var prepTimerEnabled = false
        var firstOvertimeBell = 0
        var overtimeBellPeriod = 0
        var flashScreenMode = FlashScreenMode.OFF
        var poiFlashScreenMode = FlashScreenMode.OFF

        val res = resources

        val tag = "applyPreferences"

        try {

            // The boolean preferences
            mBellsEnabled = prefs.getBoolean(res.getString(R.string.pref_ringBells_key),
                    res.getBoolean(R.bool.prefDefault_ringBells))
            vibrateMode = prefs.getBoolean(res.getString(R.string.pref_vibrateMode_key),
                    res.getBoolean(R.bool.prefDefault_vibrateMode))
            overtimeBellsEnabled =
                    prefs.getBoolean(res.getString(R.string.pref_overtimeBellsEnable_key),
                            res.getBoolean(R.bool.prefDefault_overtimeBellsEnable))

            mSpeechKeepScreenOn = prefs.getBoolean(res.getString(R.string.pref_keepScreenOn_key),
                    res.getBoolean(R.bool.prefDefault_keepScreenOn))
            mPrepTimeKeepScreenOn =
                    prefs.getBoolean(res.getString(R.string.pref_prepTimer_keepScreenOn_key),
                            res.getBoolean(R.bool.prefDefault_prepTimer_keepScreenOn))

            mPoiTimerEnabled = prefs.getBoolean(res.getString(R.string.pref_poiTimer_enable_key),
                    res.getBoolean(R.bool.prefDefault_poiTimer_enable))
            poiBuzzerEnabled =
                    prefs.getBoolean(res.getString(R.string.pref_poiTimer_buzzerEnable_key),
                            res.getBoolean(R.bool.prefDefault_poiTimer_buzzerEnable))
            poiVibrateEnabled =
                    prefs.getBoolean(res.getString(R.string.pref_poiTimer_vibrateEnable_key),
                            res.getBoolean(R.bool.prefDefault_poiTimer_vibrateEnable))

            prepTimerEnabled = prefs.getBoolean(res.getString(R.string.pref_prepTimer_enable_key),
                    res.getBoolean(R.bool.prefDefault_prepTimer_enable))

            // Overtime bell integers
            if (overtimeBellsEnabled) {
                firstOvertimeBell =
                        prefs.getInt(res.getString(R.string.pref_firstOvertimeBell_key),
                                res.getInteger(R.integer.prefDefault_firstOvertimeBell))
                overtimeBellPeriod =
                        prefs.getInt(res.getString(R.string.pref_overtimeBellPeriod_key),
                                res.getInteger(R.integer.prefDefault_overtimeBellPeriod))
            } else {
                firstOvertimeBell = 0
                overtimeBellPeriod = 0
            }

            // List preference: POI flash screen mode
            val poiFlashScreenModeValue =
                    prefs.getString(res.getString(R.string.pref_poiTimer_flashScreenMode_key),
                            res.getString(R.string.prefDefault_poiTimer_flashScreenMode))!!
            poiFlashScreenMode = FlashScreenMode.toEnum(poiFlashScreenModeValue)

            // List preference: Count direction
            val userCountDirectionValue =
                    prefs.getString(res.getString(R.string.pref_countDirection_key),
                            res.getString(R.string.prefDefault_countDirection))!!
            mCountDirection = CountDirection.toEnum(userCountDirectionValue)

            // List preference: Count direction for prep time
            val userPrepTimeCountDirectionValue =
                    prefs.getString(res.getString(R.string.pref_prepTimer_countDirection_key),
                            res.getString(R.string.prefDefault_prepTimer_countDirection))!!
            mPrepTimeCountDirection = CountDirection.toEnum(userPrepTimeCountDirectionValue)

            // List preference: Background colour area
            val oldBackgroundColourArea = mBackgroundColourArea
            val backgroundColourAreaValue =
                    prefs.getString(res.getString(R.string.pref_backgroundColourArea_key),
                            res.getString(R.string.prefDefault_backgroundColourArea))!!
            mBackgroundColourArea = BackgroundColourArea.toEnum(backgroundColourAreaValue)
            if (oldBackgroundColourArea != mBackgroundColourArea) {
                Log.v(tag, "background colour preference changed - refreshing")
                resetBackgroundColoursToTransparent()
                val adapter = mViewPager?.adapter as DebateTimerDisplayPagerAdapter?
                adapter?.refreshBackgroundColours()
            }

            // List preference: Flash screen mode
            val flashScreenModeValue =
                    prefs.getString(res.getString(R.string.pref_flashScreenMode_key),
                            res.getString(R.string.prefDefault_flashScreenMode))!!
            flashScreenMode = FlashScreenMode.toEnum(flashScreenModeValue)

        } catch (e: ClassCastException) {
            Log.e(tag, "caught ClassCastException!")
            return
        }

        val debateManager = mDebateManager
        if (debateManager != null) {
            debateManager.setOvertimeBells(firstOvertimeBell.toLong(),
                    overtimeBellPeriod.toLong())
            debateManager.setPrepTimeEnabled(prepTimerEnabled)
            applyPrepTimeBells()

            // This is necessary if the debate structure has changed, i.e. if prep time has been
            // enabled or disabled.
            notifyViewPagerDataSetChanged()

        } else {
            Log.v(tag, "Couldn't restore overtime bells, mDebateManager doesn't yet exist")
        }

        val serviceBinder = mServiceBinder
        if (serviceBinder != null) {
            val am = serviceBinder.alertManager!!

            // Volume control stream is linked to ring bells mode
            am.setBellsEnabled(mBellsEnabled)

            am.setVibrateMode(vibrateMode)
            am.setFlashScreenMode(flashScreenMode)

            am.setPoiBuzzerEnabled(poiBuzzerEnabled)
            am.setPoiVibrateEnabled(poiVibrateEnabled)
            am.setPoiFlashScreenMode(poiFlashScreenMode)

            Log.v(tag, "AlertManager preferences applied")
        } else {
            Log.v(tag, "Couldn't restore AlertManager preferences; service binder doesn't yet exist")
        }

        activity?.volumeControlStream =
                if (mBellsEnabled) AudioManager.STREAM_MUSIC else AudioManager.STREAM_RING
        updateKeepScreenOn()

        updateToolbar()
        updateGui()
    }

    private fun applyPrepTimeBells() {
        val context = requireContext()
        val ptbm = PrepTimeBellsManager(context)
        val prefs = context.getSharedPreferences(
                PrepTimeBellsManager.PREP_TIME_BELLS_PREFERENCES_NAME, Context.MODE_PRIVATE)
        ptbm.loadFromPreferences(prefs)
        mDebateManager!!.setPrepTimeBellsManager(ptbm)
    }

    /**
     * Builds a `DebateFormat` from a specified XML file. Shows a `Dialog` if
     * the debate format builder logged non-fatal errors.
     * @param filename the file name of the XML file
     * @return the built `DebateFormat`
     * @throws FatalXmlError if there was any problem, which could include:
     *
     *  - A problem opening or reading the file
     *  - A problem parsing the XML file
     *  - That there were no speeches in this debate format
     *
     * The message of the exception will be human-readable and can be displayed in a dialogue box.
     */
    @Throws(FatalXmlError::class)
    private fun buildDebateFromXml(filename: String, showDialogs: Boolean): DebateFormat {

        val context = requireContext()

        val filesManager = FormatXmlFilesManager(context)

        val inputStream: InputStream = try {
            filesManager.open(filename)
        } catch (e: IOException) {
            throw FatalXmlError(getString(R.string.debateLoadError_cannotFind), e)
        }

        val dfbfx = DebateFormatBuilderFromXmlForSchema2(context)

        val df: DebateFormat = try {
            dfbfx.buildDebateFromXml(inputStream)
        } catch (e: IOException) {
            throw FatalXmlError(getString(R.string.debateLoadError_cannotRead), e)
        } catch (e: SAXException) {
            Log.e(TAG, "bad xml")
            throw FatalXmlError(getString(
                    R.string.debateLoadError_badXml, e.localizedMessage), e)
        }

        if (dfbfx.isSchemaOutdated())
            throw FatalXmlError(getString(R.string.debateLoadError_schemaOutdated))

        if (showDialogs && dfbfx.isSchemaTooNew()) {
            val fragment = DialogSchemaTooNewFragment.newInstance(dfbfx.schemaVersion,
                    dfbfx.supportedSchemaVersion, filename)
            queueDialog(fragment, DIALOG_TAG_SCHEMA_TOO_NEW + filename)
        }

        if (df.numberOfSpeeches() == 0)
            throw FatalXmlError(getString(R.string.debateLoadError_noSpeeches))

        if (dfbfx.hasErrors()) {
            val errorLogItems = StringBuilder()
            for (error in dfbfx.errorLog) {
                errorLogItems.append("• ")
                errorLogItems.append(error)
                errorLogItems.append("<br />")
            }
            throw FatalXmlError(
                    getString(R.string.debateLoadError_generalErrors, errorLogItems.toString()))
        }

        return df
    }

    private fun clearDebateLoadError() {
        mDebateLoadError = null
        updateGui()
    }

    private fun copyAssetsIfEmpty() {
        val manager = FormatXmlFilesManager(requireContext())
        try {
            if (manager.isEmpty()) manager.copyAssets()
        } catch (e: IOException) {
            showSnackbar(Snackbar.LENGTH_LONG, R.string.timer_snackbar_copyAssetsError)
        }
    }

    /**
     * Finishes editing the current time and restores the GUI to its prior state.
     * @param save true if the edited time should become the new current time, false if it should
     * be discarded.
     */
    private fun editCurrentTimeFinish(save: Boolean) {

        val timerDisplay = mTimerDisplay
        if (timerDisplay == null) {
            Log.e(TAG, "editCurrentTimeFinish: no debate timer display")
            return
        }

        val currentTimePicker = timerDisplay.timerCurrentTimePicker
        currentTimePicker.clearFocus()

        // Hide the keyboard
        val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE)
                as InputMethodManager
        imm.hideSoftInputFromWindow(currentTimePicker.windowToken, 0)

        val debateManager = mDebateManager
        if (save && debateManager != null && mIsEditingTime) {
            // We're using this in hours and minutes, not minutes and seconds
            @Suppress("deprecation")
            val minutes = currentTimePicker.currentHour
            @Suppress("deprecation")
            val seconds = currentTimePicker.currentMinute
            var newTime = minutes * 60L + seconds
            // Invert the time if in count-down mode
            newTime = subtractFromSpeechLengthIfCountingDown(newTime)
            debateManager.setActivePhaseCurrentTime(newTime)
        }

        mIsEditingTime = false
        mFinishEditingTimeBackPressedCallback.isEnabled = false

        updateGui()
    }

    /**
     * Displays the time picker to edit the current time.
     * Does nothing if there is no debate loaded or if the timer is running.
     */
    private fun editCurrentTimeStart() {

        // Check that things are in a valid state to enter edit time mode
        // If they aren't, return straight away
        val debateManager = mDebateManager ?: return
        if (debateManager.isRunning) return

        // Only if things were in a valid state do we enter edit time mode
        mIsEditingTime = true
        mFinishEditingTimeBackPressedCallback.isEnabled = true

        val timerDisplay = mTimerDisplay
        if (timerDisplay == null) {
            Log.e(TAG, "editCurrentTimeStart: no debate timer display")
            return
        }

        val currentTimePicker = timerDisplay.timerCurrentTimePicker

        var currentTime = debateManager.activePhaseCurrentTime

        // Invert the time if in count-down mode
        currentTime = subtractFromSpeechLengthIfCountingDown(currentTime)

        // Limit to the allowable time range
        if (currentTime < 0) {
            currentTime = 0
            showSnackbar(Snackbar.LENGTH_LONG,
                    R.string.timer_snackbar_editTextDiscardChangesInfo_limitedBelow)
        } else if (currentTime >= 24 * 60) {
            currentTime = 24 * 60 - 1
            showSnackbar(Snackbar.LENGTH_LONG,
                    R.string.timer_snackbar_editTextDiscardChangesInfo_limitedAbove)
        }

        // We're using this in hours and minutes, not minutes and seconds
        @Suppress("deprecation")
        currentTimePicker.currentHour = (currentTime / 60).toInt()
        @Suppress("deprecation")
        currentTimePicker.currentMinute = (currentTime % 60).toInt()

        updateGui()

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
    private fun getCountDirection(dpf: DebatePhaseFormat): CountDirection {
        return if (dpf.isPrep)
            mPrepTimeCountDirection
        else
            mCountDirection
    }

    /**
     * @param dpf the [DebatePhaseFormat]
     * @param pi the current [PeriodInfo]
     * @return the appropriate background colour
     */
    private fun getBackgroundColorFromPeriodInfo(dpf: DebatePhaseFormat, pi: PeriodInfo): Int {
        @Suppress("deprecation")
        return pi.backgroundColor
                ?: resources.getColor(
                        if (dpf.isPrep) R.color.prepTimeBackgroundColour
                        else android.R.color.background_dark)
    }

    /**
     * Retrieves the file name and an [InputStream] from the [Intent] that started this
     * activity. If either can't be found, it shows the appropriate snackbar (so there is no need
     * for the caller to show another error message), and returns null.
     * @return a [Pair], being the file name and input stream, or null if there was an error.
     */
    private fun getIncomingFilenameAndInputStream(): Pair<String, InputStream>? {
        val activity = requireActivity()
        val intent = activity.intent
        if (Intent.ACTION_VIEW != intent.action) {
            Log.e(TAG, "importIncomingFile: Intent action was not ACTION_VIEW")
            return null
        }

        Log.i(TAG, String.format("importIncomingFile: mime type %s, data %s",
                intent.type, intent.dataString))

        val uri = intent.data
        val filename = DebatekeeperUtils.getFilenameFromUri(activity.contentResolver, uri!!)
        Log.i(TAG, "importIncomingFile: file name is $filename")

        if (filename == null) {
            showSnackbar(Snackbar.LENGTH_LONG, R.string.importDebateFormat_snackbar_error_generic)
            Log.e(TAG, "importIncomingFile: File name was null")
            return null
        }

        val inputStream: InputStream = try {
            activity.contentResolver.openInputStream(uri)!!
        } catch (e: FileNotFoundException) {
            showSnackbar(Snackbar.LENGTH_LONG, R.string.importDebateFormat_snackbar_error_generic)
            Log.e(TAG, "importIncomingFile: Could not resolve file $uri")
            return null
        }
        return Pair(filename, inputStream)
    }

    /**
     * Goes to the next speech.
     * Does nothing if there is no debate loaded, if the current speech is the last speech, if
     * the timer is running, or if the current time is being edited.
     */
    private fun goToNextSpeech() {

        val debateManager = mDebateManager ?: return
        if (debateManager.isRunning) return
        if (debateManager.isInLastPhase) return
        if (mIsEditingTime) return

        debateManager.goToNextPhase()
        mViewPager!!.currentItem = debateManager.activePhaseIndex

        updateGui()
    }

    /**
     * Goes to the previous speech.
     * Does nothing if there is no debate loaded, if the current speech is the first speech, if
     * the timer is running, or if the current time is being edited.
     */
    private fun goToPreviousSpeech() {

        val debateManager = mDebateManager ?: return
        if (debateManager.isRunning) return
        if (debateManager.isInFirstPhase) return
        if (mIsEditingTime) return

        debateManager.goToPreviousPhase()
        mViewPager!!.currentItem = debateManager.activePhaseIndex

        updateGui()
    }

    /**
     * Imports a file that was passed in the intent that opened this activity.
     * @param filename the file name to be saved
     */
    private fun importIncomingFile(filename: String) {
        val incoming = getIncomingFilenameAndInputStream() ?: return
        val inputStream = incoming.second

        val filesManager = FormatXmlFilesManager(requireContext())

        try {
            filesManager.copy(inputStream, filename)
        } catch (e: IOException) {
            e.printStackTrace()
            showSnackbar(Snackbar.LENGTH_LONG, R.string.importDebateFormat_snackbar_error_generic)
            Log.e(TAG, "importIncomingFile: Could not copy file: " + e.message)
            return
        }

        showSnackbar(Snackbar.LENGTH_SHORT, R.string.importDebateFormat_snackbar_success, filename)

        // Now, load the debate
        mImportIntentHandled = true
        setXmlFileName(filename)
        resetDebate(true)
    }

    /**
     * Initialises the debate by parsing the relevant XML file, creating a [DebateManager],
     * and applying the user's preferences to it.
     *
     * If a [DebateManager] is already active, this skips the debate initialisation. Use
     * [resetDebate] to reset the debate.
     *
     * @see resetDebate
     * @param showDialogs whether dialogs should be shown, normally `true` but should be
     *                    `false` if, for example, this was initiated by a reset.
     */
    private fun initialiseDebate(showDialogs: Boolean) {
        val formatXmlFileName = mFormatXmlFileName
        if (formatXmlFileName == null) {
            Log.w(TAG, "Tried to initialise debate with null file")
            return
        }

        var debateManager = mServiceBinder!!.debateManager
        mDebateManager = debateManager

        if (debateManager == null) {
            Log.d(TAG, "initialiseDebate: creating debate manager")

            val df: DebateFormat = try {
                buildDebateFromXml(formatXmlFileName, showDialogs)
            } catch (e: FatalXmlError) {
                setDebateLoadError(e.localizedMessage!!)
                notifyViewPagerDataSetChanged()
                return
            }

            debateManager = mServiceBinder!!.createDebateManager(df)
            mDebateManager = debateManager

            // We only restore the state if there wasn't an existing debate, i.e. if the service
            // wasn't already running, and if the debate format stored in the saved instance state
            // matches the debate format we're using now.
            val lastStateBundle = mLastStateBundle
            if (lastStateBundle != null) {
                val xmlFileName = lastStateBundle.getString(BUNDLE_KEY_XML_FILE_NAME)
                if (xmlFileName != null && xmlFileName == formatXmlFileName)
                    debateManager.restoreState(BUNDLE_KEY_DEBATE_MANAGER, lastStateBundle)
            }
        } else {
            Log.d(TAG, "initialiseDebate: debate manager already existed")
        }

        // The bundle should only ever be relevant once per activity cycle
        mLastStateBundle = null

        Log.d(TAG, "clearing error, notifying view pager")
        clearDebateLoadError()
        val viewPager = mViewPager
        if (viewPager != null) {
            // sometimes this is called from the service, so mViewPager might not exist
            notifyViewPagerDataSetChanged()
            viewPager.setCurrentItem(debateManager.activePhaseIndex, false)
        }
        applyPreferences()
        updateToolbar()
        requestNotificationsPermission()
    }

    /**
     * Returns whether or not this is the user's first time opening the app.
     * @return `true` if it is the first time, `false` otherwise.
     */
    private fun isFirstInstall(): Boolean {
        val info = try {
            requireActivity().packageManager.getPackageInfo(requireActivity().packageName, 0)
        } catch (e: PackageManager.NameNotFoundException) {
            Log.e(TAG, "isFirstInstall: Can't find package info, assuming it is the first install")
            return true
        }

        Log.v(TAG, String.format("isFirstInstall: %d vs %d",
                info.firstInstallTime, info.lastUpdateTime))
        return info.firstInstallTime == info.lastUpdateTime
    }

    /**
     * Navigates to [FormatChooserFragment].
     */
    private fun navigateToFormatChooser(xmlFileName: String?) {
        mIsOpeningFormatChooser = true
        val action =
                if (xmlFileName != null)
                    DebatingTimerFragmentDirections.actionChooseFormat(xmlFileName)
                else
                    DebatingTimerFragmentDirections.actionChooseFormat()
        NavHostFragment.findNavController(this).navigate(action)
    }

    /**
     * Convenience function, basically runs
     * `mViewPager.getAdapter().notifyDataSetChanged()` but also checks that things aren't
     * `null` (and does nothing if anything is).
     */
    private fun notifyViewPagerDataSetChanged() {
        mViewPager?.adapter?.notifyDataSetChanged()
    }

    /**
     * Queues a dialog to be shown after a currently-shown dialog, or immediately if there is no
     * currently-shown dialog.  This does not happen automatically - dialogs must know whether
     * they are potentially blocking or waiting, and set themselves up accordingly.  Dialogs that
     * could block must set `mDialogBlockingTag` to their tag when they are shown, and call
     * `showQueuedDialog()` when they are dismissed.
     * Dialogs that could be queued must call `queueDialog()` instead of `showDialog()`.
     *
     * @param fragment the [DialogFragment] that would be passed to showDialog()
     * @param tag      the tag that would be passed to showDialog()
     */
    private fun queueDialog(fragment: QueueableDialogFragment, tag: String) {
        if (childFragmentManager.findFragmentByTag(tag) != null) {
            Log.w(TAG, "skipping dialog, found in fragment manager: $tag")
            return
        }

        val dialogBlockingTag = mDialogBlockingTag
        if (dialogBlockingTag == null) {
            Log.d(TAG, "showing dialog immediately: $tag")
            mDialogBlockingTag = tag
            fragment.show(childFragmentManager, tag)
        } else {

            // don't queue this again if the same dialog is already queued or showing
            if (dialogBlockingTag == tag) {
                Log.w(TAG, "skipping dialog, duplicate of blocked: $tag")
                return
            }
            for (pair in mDialogsInWaiting) {
                if (pair.first == tag) {
                    Log.w(TAG, "skipping dialog, already queued: $tag")
                    return
                }
            }

            Log.d(TAG, "queueing dialog: $tag (currently blocking: $dialogBlockingTag)")
            mDialogsInWaiting.add(Pair(tag, fragment))
        }
    }

    private fun requestNotificationsPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(requireContext(),
                        Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            if (shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS)) {
                Log.w(TAG, "requestNotificationsPermission: permission denied")
                showNotificationsPermissionDeniedDialog()
            } else {
                mRequestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    /**
     * Resets the background colour to the default.
     * This should be called whenever the background colour preference is changed, as
     * `updateGui()` doesn't automatically do this (for efficiency). You should call
     * `updateGui()` as immediately as practicable after calling this.
     */
    private fun resetBackgroundColoursToTransparent() {
        val viewPager = mViewPager!!
        for (i in 0 until viewPager.childCount) {
            val v = viewPager.getChildAt(i)
            if (v.id == R.id.timer_root) {
                v.setBackgroundColor(COLOUR_TRANSPARENT)
                val speechNameText = v.findViewById<View>(R.id.timer_speechNameText)
                val periodDescriptionText = v.findViewById<View>(R.id.timer_periodDescriptionText)
                speechNameText.setBackgroundColor(COLOUR_TRANSPARENT)
                periodDescriptionText.setBackgroundColor(COLOUR_TRANSPARENT)
            }
        }
    }

    /**
     * Releases the current [DebateManager] and then initialises the debate (by calling
     * [initialiseDebate]).
     * @param showDialogs whether dialogs should be shown, normally `true` but should be
     *                    `false` if, for example, this was initiated by a reset.
     */
    private fun resetDebate(showDialogs: Boolean) {
        val serviceBinder = mServiceBinder ?: return
        serviceBinder.releaseDebateManager()
        initialiseDebate(showDialogs)
    }

    private fun restoreBinder() {
        val am = mServiceBinder?.alertManager
        if (am != null) {
            am.setFlashScreenListener(DebatingTimerFlashScreenListener())
            am.activityStart()
        }

        // Always apply preferences after restoring the binder, as some of the preferences
        // apply to the binder.
        applyPreferences()
    }

    /**
     *  Sets up a single button
     */
    private fun setButton(button: Button, spec: ControlButtonSpec?) {
        if (spec == null)
            button.visibility = View.GONE
        else {
            button.setText(spec.textResId)
            button.setOnClickListener(spec.onClickListener)
            button.visibility = View.VISIBLE
        }
    }

    /**
     * Sets up the buttons according to the [ControlButtonSpec]s specified.  If the centre
     * button is blank and the left and right are not, a "left-centre" button is used in place
     * of the left button; *i.e.* the left button has "double weight" of sorts.
     * @param left the [ControlButtonSpec] for the left button
     * @param centre the [ControlButtonSpec] for the centre button
     * @param right the [ControlButtonSpec] for the right button
     */
    private fun setButtons(left: ControlButtonSpec?, centre: ControlButtonSpec?,
                           right: ControlButtonSpec?) {
        val viewBinding = mViewBinding!!

        if (left != null && centre == null && right != null) {
            setButton(viewBinding.timerLeftCentreControlButton, left)
            setButton(viewBinding.timerLeftControlButton, null)
            setButton(viewBinding.timerCentreControlButton, null)

        } else {
            setButton(viewBinding.timerLeftCentreControlButton, null)
            setButton(viewBinding.timerLeftControlButton, left)
            setButton(viewBinding.timerCentreControlButton, centre)
        }

        setButton(viewBinding.timerRightControlButton, right)
    }

    /**
     * Enables or disables all of the control buttons (except for the "Bell" button).  If
     * `mDebateManager` is `null`, this does nothing.
     * @param enable `true` to enable, `false` to disable
     */
    private fun setButtonsEnable(enable: Boolean) {
        val debateManager = mDebateManager ?: return
        val viewBinding = mViewBinding!!
        viewBinding.timerLeftControlButton.isEnabled = enable
        viewBinding.timerLeftCentreControlButton.isEnabled = enable
        viewBinding.timerCentreControlButton.isEnabled = enable
        // Disable the [Next Speaker] button if there are no more speakers
        viewBinding.timerRightControlButton.isEnabled = enable && !debateManager.isInLastPhase
    }

    private fun setDebateLoadError(message: String) {
        @Suppress("deprecation")
        mDebateLoadError = Html.fromHtml(message)
        mServiceBinder?.releaseDebateManager()
        mDebateManager = null
        mIsChangingPages = false
        updateGui()
    }

    private fun setXmlFileName(filename: String?) {
        mFormatXmlFileName = filename
        val sp = requireActivity().getPreferences(Context.MODE_PRIVATE)
        val editor = sp.edit()
        if (filename != null)
            editor.putString(PREFERENCE_XML_FILE_NAME, filename)
        else
            editor.remove(PREFERENCE_XML_FILE_NAME)
        editor.apply()
    }

    private fun showChangelogDialog() {
        val prefs = requireActivity().getPreferences(Context.MODE_PRIVATE)
        val res = resources
        val thisChangelogVersion = res.getInteger(R.integer.changelogDialog_versionCode)
        val lastChangelogVersionShown = prefs.getInt(LAST_CHANGELOG_VERSION_SHOWN, 0)
        if (lastChangelogVersionShown < thisChangelogVersion) {
            if (isFirstInstall()) {
                // Don't show on the dialog on first install, but take note of the version.
                prefs.edit().putInt(LAST_CHANGELOG_VERSION_SHOWN, thisChangelogVersion).apply()
            } else {
                // The dialog will update the preference to the new version code.
                queueDialog(DialogChangelogFragment(), DIALOG_TAG_CHANGELOG)
            }
        }
    }

    private fun showDialogToConfirmImport() {
        val incoming = getIncomingFilenameAndInputStream() ?: return
        val incomingFilename = incoming.first
        val inputStream = incoming.second

        val context = requireContext()

        val nameExtractor = DebateFormatFieldExtractor(context, R.string.xml2elemName_name)
        val filesManager = FormatXmlFilesManager(context)
        val exists = filesManager.exists(incomingFilename)

        var incomingStyleName: String? = null
        var existingStyleName: String? = null

        try {
            incomingStyleName = nameExtractor.getFieldValue(inputStream)
            inputStream.close()
        } catch (e: IOException) {
            showSnackbar(Snackbar.LENGTH_LONG, R.string.importDebateFormat_snackbar_error_generic)
            return
        } catch (e: SAXException) {
            Log.e(TAG, "showDialogToConfirmImport: error parsing incoming file")
            // continue with unknown file name
        }
        if (incomingStyleName == null)
            incomingStyleName = getString(R.string.importDebateFormat_placeholder_unknownStyleName)

        if (exists) {
            // If there's an existing file, grab its style name and prompt to replace. (We don't
            // give an option not to replace.
            try {
                val existingIs = filesManager.open(incomingFilename)
                existingStyleName = nameExtractor.getFieldValue(existingIs)
                existingIs.close()
            } catch (e: IOException) {
                Log.e(TAG, "showDialogToConfirmImport: error parsing existing file")
            } catch (e: SAXException) {
                Log.e(TAG, "showDialogToConfirmImport: error parsing existing file")
            }
            if (existingStyleName == null)
                existingStyleName =
                        getString(R.string.importDebateFormat_placeholder_unknownStyleName)

        } else {
            // If it wasn't found, check if the style name happens to match any other file, since
            // we may want to change the file name in order to overwrite the existing file (to
            // avoid duplicates that arise because the system changed the file name during
            // transmission). We'll do this only if there's exactly one existing file -- if there
            // are already duplicates, we can't reliably tell which one to pick.
            var suggestedFilename: String? = null
            var userFileList = arrayOf<String>()
            var numberOfDuplicatesFound = 0
            try {
                userFileList = filesManager.list()
            } catch (e: IOException) {
                Log.e(TAG, "showDialogToConfirmImport: I/O error checking other files")
            }
            for (otherFilename in userFileList) {
                val otherStyleName: String?
                try {
                    val otherIs = filesManager.open(otherFilename)
                    otherStyleName = nameExtractor.getFieldValue(otherIs)
                    otherIs.close()
                } catch (e: IOException) {
                    continue
                } catch (e: SAXException) {
                    continue
                }
                if (incomingStyleName == otherStyleName) {
                    numberOfDuplicatesFound++
                    suggestedFilename = otherFilename
                }
            }
            if (numberOfDuplicatesFound == 1) {
                val fragment = DialogSuggestReplacementFragment.newInstance(incomingFilename,
                        incomingStyleName, suggestedFilename!!)
                queueDialog(fragment, DIALOG_TAG_IMPORT_SUGGEST_REPLACEMENT)
                return
            }
        }

        val fragment = DialogImportFileConfirmFragment.newInstance(incomingFilename,
                incomingStyleName, exists, existingStyleName)
        queueDialog(fragment, DIALOG_TAG_IMPORT_CONFIRM)
    }

    /**
     * Shows the next queued dialog if there is one, otherwise notes that there are no dialogs
     * blocking.
     */
    private fun showNextQueuedDialog() {
        // First, remove now-irrelevant dialogs from list
        val iterator = mDialogsInWaiting.iterator()
        while (iterator.hasNext()) {
            val pair = iterator.next()
            val tags = pair.first.split("/".toRegex(), limit = 2).toTypedArray()
            if (tags.size == 2 && tags[1] != mFormatXmlFileName) {
                iterator.remove()
                Log.i(TAG, "showNextQueuedDialog: cleared dialog " + pair.first)
            }
        }

        // Then, show the next one
        if (mDialogsInWaiting.size > 0 && isResumed) {
            val pair = mDialogsInWaiting.removeAt(0)
            pair.second.show(childFragmentManager, pair.first)
            mDialogBlockingTag = pair.first
        } else mDialogBlockingTag = null
    }

    private fun showNotificationsPermissionDeniedDialog() {
        val prefs = requireActivity().getPreferences(Context.MODE_PRIVATE)
        val dialogShown = prefs.getBoolean(NOTIFICATIONS_PERMISSION_DIALOG_SHOWN, false)
        if (!dialogShown)
            queueDialog(DialogNotificationPermissionDeniedFragment(),
                    DIALOG_TAG_NOTIFICATIONS_DENIED)
    }

    /**
     * Convenience function for showing a [Snackbar].
     *
     * @param duration    a `Snackbar.LENGTH_*` constant, passed to [Snackbar.make]
     * @param stringResId a resource ID for a string
     * @param formatArgs  arguments to format the string with
     */
    private fun showSnackbar(duration: Int, stringResId: Int, vararg formatArgs: Any) {
        val string = getString(stringResId, *formatArgs)
        val coordinator = mViewBinding!!.timerCoordinator
        val snackbar = Snackbar.make(coordinator, string, duration)
        val res = resources
        @Suppress("deprecation")
        snackbar.setBackgroundTint(res.getColor(R.color.snackbar_background))
        @Suppress("deprecation")
        snackbar.setTextColor(res.getColor(R.color.snackbar_text))
        val snackbarText = snackbar.view
        val textView = snackbarText.findViewById<TextView>(
                com.google.android.material.R.id.snackbar_text)
        textView?.maxLines = 5
        snackbar.show()
    }

    /**
     * Returns the number of seconds that would be displayed, taking into account the count
     * direction, for the currently active debate phase. If the overall count direction is
     * `COUNT_DOWN` and there is a speech format ready, it returns (speechLength - time).
     * Otherwise, it just returns time.
     *
     * @param time the time that is wished to be formatted (in seconds)
     * @return the time that would be displayed (as an integer, number of seconds)
     */
    private fun subtractFromSpeechLengthIfCountingDown(time: Long): Long {
        val debateManager = mDebateManager
        if (debateManager != null)
            return subtractFromSpeechLengthIfCountingDown(time,
                    debateManager.activePhaseFormat)
        return time
    }

    /**
     * Returns the number of seconds that would be displayed, taking into account the count
     * direction, for the given debate phase.  If the overall count direction is
     * `COUNT_DOWN` and there is a speech format ready, it returns (speechLength - time).
     * Otherwise, it just returns time.
     *
     * @param time the time that is wished to be formatted (in seconds)
     * @param sf   the relevant [DebatePhaseFormat]
     * @return the time that would be displayed (as an integer, number of seconds)
     */
    private fun subtractFromSpeechLengthIfCountingDown(time: Long, sf: DebatePhaseFormat): Long {
        if (getCountDirection(sf) == CountDirection.COUNT_DOWN)
            return sf.length - time
        return time
    }

    /**
     *  Updates the buttons according to the current status of the debate
     *  The buttons are allocated as follows:
     *
     *  - When at start:          [Start] [Next]
     *  - When running:           [Stop]
     *  - When stopped by user:   [Resume] [Restart] [Next]
     *  - When stopped by alarm:  [Resume]
     *
     *  The [Bell] button always is on the right of any of the above three buttons.
     */
    private fun updateControls() {
        val viewBinding = mViewBinding ?: return
        val debateManager = mDebateManager
        val viewPager = mViewPager!!
        if (debateManager != null && mTimerDisplay != null) {

            // If it's the last speaker, don't show a "next speaker" button.
            // Show a "restart debate" button instead.
            when (debateManager.timerStatus) {
                DebateTimerState.NOT_STARTED ->
                    setButtons(CONTROL_BUTTON_START_TIMER, null, CONTROL_BUTTON_NEXT_PHASE)
                DebateTimerState.RUNNING ->
                    setButtons(CONTROL_BUTTON_STOP_TIMER, null, null)
                DebateTimerState.STOPPED_BY_BELL ->
                    setButtons(CONTROL_BUTTON_RESUME_TIMER, null, null)
                DebateTimerState.STOPPED_BY_USER ->
                    setButtons(CONTROL_BUTTON_RESUME_TIMER, CONTROL_BUTTON_RESET_TIMER,
                            CONTROL_BUTTON_NEXT_PHASE)
            }

            val timerDisplay = mTimerDisplay!!
            timerDisplay.timerCurrentTime.visibility =
                    if (mIsEditingTime) View.GONE else View.VISIBLE
            timerDisplay.timerCurrentTimePicker.visibility =
                    if (mIsEditingTime) View.VISIBLE else View.GONE

            setButtonsEnable(!mIsEditingTime)
            timerDisplay.timerCurrentTime.isLongClickable = !debateManager.isRunning
            viewPager.setPagingEnabled(!mIsEditingTime && !debateManager.isRunning)

        } else {
            // If no debate is loaded, show only one control button, which leads the user to
            // choose a style. (Keep the play bell button enabled.)
            setButtons(null, null, null)
            viewBinding.timerLeftControlButton.isEnabled = false
            viewBinding.timerCentreControlButton.isEnabled = false
            viewBinding.timerRightControlButton.isEnabled = false

            // This seems counter-intuitive, but we enable paging if there is no debate loaded,
            // as there is only one page anyway, and this way the "scrolled to the limit"
            // indicators appear on the screen.
            viewPager.setPagingEnabled(true)
        }

        // Show or hide the [Bell] button
        updatePlayBellButton()
    }

    /**
     * Populates the fields in the debate load error display.
     */
    private fun updateDebateLoadErrorDisplay() {
        val binding = mViewBinding!!.timerDebateLoadError
        binding.debateLoadErrorMessage.text = mDebateLoadError
        binding.debateLoadErrorTitle.setText(R.string.debateLoadErrorScreen_title)
        binding.debateLoadErrorFileName.text =
                getString(R.string.debateLoadErrorScreen_filename, mFormatXmlFileName)
        binding.debateLoadErrorChooseAnother.setText(R.string.debateLoadError_suffix)
        binding.debateLoadErrorChooseStyleButton.setText(R.string.debateLoadErrorScreen_button)
        binding.debateLoadErrorChooseStyleButton.setOnClickListener {
            navigateToFormatChooser(null)
        }
    }

    /**
     * Updates the debate timer display with the current active debate phase information.
     */
    private fun updateMainDisplay() {
        val viewBinding = mViewBinding!!
        val viewPager = mViewPager!!
        val debateManager = mDebateManager

        if (debateManager == null && mDebateLoadError != null) {
            Log.w(TAG, "no debate manager, setting error view")
            viewPager.visibility = View.GONE
            viewBinding.timerNoDebateLoaded.root.visibility = View.GONE
            viewBinding.timerDebateLoadError.root.visibility = View.VISIBLE
            updateDebateLoadErrorDisplay()

        } else if (debateManager == null) {
            Log.w(TAG, "no debate manager, setting no-debate view")
            viewPager.visibility = View.GONE
            viewBinding.timerNoDebateLoaded.root.visibility = View.VISIBLE
            viewBinding.timerDebateLoadError.root.visibility = View.GONE

        } else {
            viewPager.visibility = View.VISIBLE
            viewBinding.timerNoDebateLoaded.root.visibility = View.GONE
            viewBinding.timerDebateLoadError.root.visibility = View.GONE

            val timerDisplay = mTimerDisplay
            if (timerDisplay != null) {
                updateDebateTimerDisplay(timerDisplay,
                        debateManager.activePhaseFormat,
                        debateManager.activePhaseCurrentPeriodInfo,
                        debateManager.activePhaseName,
                        debateManager.activePhaseCurrentTime,
                        debateManager.activePhaseNextOvertimeBellTime)
            } else Log.w(TAG, "mDebateTimerDisplay is null")
        }
    }

    /**
     * Updates a debate timer display with relevant information.
     * @param binding a [DebateTimerDisplayBinding] to populate.
     * @param dpf the [DebatePhaseFormat] to be displayed
     * @param pi the [PeriodInfo] to be displayed, should be the current one
     * @param phaseName the name of the debate phase
     * @param time the current time in the debate phase
     * @param nextOvertimeBellTime the next overtime bell in the debate phase
     */
    private fun updateDebateTimerDisplay(binding: DebateTimerDisplayBinding,
                                         dpf: DebatePhaseFormat, pi: PeriodInfo,
                                         phaseName: String, time: Long,
                                         nextOvertimeBellTime: Long?) {

        // If it passed all those checks, populate the timer display
        val periodDescriptionText = binding.timerPeriodDescriptionText
        val speechNameText = binding.timerSpeechNameText
        val currentTimeText = binding.timerCurrentTime
        val infoLineText = binding.timerInformationLine

        // The information at the top of the screen
        speechNameText.text = phaseName
        periodDescriptionText.text = pi.description

        // Take count direction into account for display
        val timeToShow = subtractFromSpeechLengthIfCountingDown(time, dpf)

        currentTimeText.text = DebatekeeperUtils.secsToTextSigned(timeToShow)

        val overtime = time > dpf.length

        // Colours
        @Suppress("deprecation")
        val currentTimeTextColor = resources.getColor(
                if (overtime) R.color.overtimeTextColour else android.R.color.primary_text_dark)
        val backgroundColour = getBackgroundColorFromPeriodInfo(dpf, pi)

        // If we're updating the current display (as opposed to an inactive debate phase), then
        // don't update colours if there is a flash screen in progress.
        val displayIsActive = binding === mTimerDisplay
        val semaphoreAcquired = displayIsActive && mFlashScreenSemaphore.tryAcquire()

        // If not current display, or we got the semaphore, we're good to go.  If not, don't
        // bother.
        if (!displayIsActive || semaphoreAcquired) {
            updateDebateTimerDisplayColours(binding, currentTimeTextColor, backgroundColour)
            if (semaphoreAcquired) mFlashScreenSemaphore.release()
        }

        // Construct the line that goes at the bottom
        val infoLine = StringBuilder()

        // First, length...
        val length = dpf.length
        val lengthStr: String = if (length % 60 == 0L)
            resources.getQuantityString(R.plurals.timer_timeInMinutes, (length / 60).toInt(),
                    length / 60)
        else
            DebatekeeperUtils.secsToTextSigned(length)

        val finalTimeTextUnformattedResId =
                if (dpf.isPrep) R.string.timer_prepTimeLength else R.string.timer_speechLength
        infoLine.append(String.format(getString(finalTimeTextUnformattedResId), lengthStr))

        if (dpf.isPrep) {
            val ptf = dpf as PrepTimeFormat
            if (ptf.isControlled)
                infoLine.append(getString(R.string.timer_prepTimeControlledIndicator))
        }

        // ...then, if applicable, bells
        val currentSpeechBells = dpf.bellsSorted
        val currentSpeechBellsIter = currentSpeechBells.iterator()

        if (overtime) {
            // show next overtime bell (don't bother with list of bells anymore)
            if (nextOvertimeBellTime == null)
                infoLine.append(getString(R.string.timer_bellsList_noOvertimeBells))
            else {
                val timeToDisplay =
                        subtractFromSpeechLengthIfCountingDown(nextOvertimeBellTime, dpf)
                infoLine.append(getString(R.string.timer_bellsList_nextOvertimeBell,
                        DebatekeeperUtils.secsToTextSigned(timeToDisplay)))
            }

        } else if (currentSpeechBellsIter.hasNext()) {
            // Convert the list of bells into a string.
            val bellsStr = StringBuilder()

            while (currentSpeechBellsIter.hasNext()) {
                val bi = currentSpeechBellsIter.next()
                val bellTime = subtractFromSpeechLengthIfCountingDown(bi.bellTime, dpf)
                bellsStr.append(DebatekeeperUtils.secsToTextSigned(bellTime))
                if (bi.isPauseOnBell)
                    bellsStr.append(getString(R.string.timer_pauseOnBellIndicator))
                if (bi.isSilent)
                    bellsStr.append(getString(R.string.timer_silentBellIndicator))
                if (currentSpeechBellsIter.hasNext())
                    bellsStr.append(", ")
            }

            infoLine.append(resources.getQuantityString(R.plurals.timer_bellsList_normal,
                    currentSpeechBells.size, bellsStr))

        } else {
            infoLine.append(getString(R.string.timer_bellsList_noBells))
        }

        infoLineText.text = infoLine.toString()

        // Update the POI timer button
        updatePoiTimerButton(binding, dpf)
    }

    /**
     * @param binding a [DebateTimerDisplayBinding], except in cases where no debate is loaded
     * or something like that.
     * @param timeTextColour the text colour to use for the current time
     * @param backgroundColour the colour to use for the background
     */
    private fun updateDebateTimerDisplayColours(binding: DebateTimerDisplayBinding?,
                                                timeTextColour: Int, backgroundColour: Int) {

        if (binding == null) return

        when (mBackgroundColourArea) {
            BackgroundColourArea.TOP_BAR_ONLY -> {
                // These would only be expected to exist if the view given is the debate timer
                // display
                binding.timerSpeechNameText.setBackgroundColor(backgroundColour)
                binding.timerPeriodDescriptionText.setBackgroundColor(backgroundColour)
            }
            BackgroundColourArea.WHOLE_SCREEN ->
                binding.root.setBackgroundColor(backgroundColour)
            BackgroundColourArea.DISABLED -> {
                // Do nothing
            }
        }

        // This would only be expected to exist if the view given is the debate timer display
        binding.timerCurrentTime.setTextColor(timeTextColour)
    }

    /**
     * Updates the GUI (in the general case).
     */
    private fun updateGui() {
        if (mIsChangingPages) {
            Log.d(TAG, "Changing pages, don't update GUI")
            return
        }
        if (!isVisible) {
            Log.d(TAG, "Not visible, don't update GUI")
            return
        }

        val debateManager = mDebateManager
        mPreviousSpeechBackPressedCallback.isEnabled =
                debateManager != null && !debateManager.isInFirstPhase && !debateManager.isRunning

        updateMainDisplay()
        updateControls()
        updateToolbar()
    }

    /**
     * Update the "keep screen on" flag according to
     *
     *  - whether it is prep time or a speech, and
     *  - whether the timer is currently running.
     *
     * This method should be called whenever
     *
     *  - the user preference is applied, and
     *  - the timer starts or stops, or might start or stop.
     *
     * This is a no-op if this fragment is not attached to an activity.
     */
    private fun updateKeepScreenOn() {
        val activity = activity ?: return
        val debateManager = mDebateManager

        val relevantKeepScreenOn: Boolean =
                if (debateManager != null && debateManager.activePhaseFormat.isPrep)
                    mPrepTimeKeepScreenOn
                else
                    mSpeechKeepScreenOn

        if (relevantKeepScreenOn && debateManager != null && debateManager.isRunning)
            activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        else
            activity.window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    private fun updatePlayBellButton() {
        val serviceBinder = mServiceBinder ?: return
        mViewBinding!!.timerPlayBellButton.visibility =
                if (serviceBinder.alertManager!!.isBellsEnabled) View.VISIBLE else View.GONE
    }

    /**
     * @param binding the [DebateTimerDisplayBinding] to be updated
     * @param dpf the [DebatePhaseFormat] relevant for this `debateTimerDisplay`
     */
    private fun updatePoiTimerButton(binding: DebateTimerDisplayBinding, dpf: DebatePhaseFormat) {
        val poiButton = binding.timerPoiTimerButton

        // Display only when user has POI timer enabled, and a debate is loaded and the current
        // speech has POIs in it.
        if (mPoiTimerEnabled && dpf.javaClass == SpeechFormat::class.java
                && (dpf as SpeechFormat).hasPoisAllowedSomewhere()) {
            poiButton.visibility = View.VISIBLE

            // If POIs are currently active, enable the button
            val debateManager = mDebateManager
            if (debateManager != null && debateManager.isPoisActive) {
                poiButton.isEnabled = debateManager.isRunning

                val poiTime = debateManager.currentPoiTime
                if (poiTime == null)
                    poiButton.setText(R.string.timer_poiTimer_buttonText)
                else
                    @Suppress("AndroidLintDefaultLocale")
                    poiButton.text = String.format("%d", poiTime)

            // Otherwise, disable it
            } else {
                poiButton.setText(R.string.timer_poiTimer_buttonText)
                poiButton.isEnabled = false
            }

        // Otherwise, hide the button
        } else {
            poiButton.visibility = View.GONE
        }
    }

    /**
     * Updates the toolbar according to the options that should be available, given the current
     * debate load status and "ring bells" setting. This includes the title.
     */
    private fun updateToolbar() {
        val viewBinding = mViewBinding
        if (viewBinding == null) {
            Log.d(TAG, "No view binding, skip update toolbar")
            return
        }

        val toolbar = viewBinding.toolbarDebatingTimer

        // update the title
        val debateManager = mDebateManager
        if (debateManager != null) {
            val shortName = debateManager.debateFormatShortName
            if (shortName != null)
                toolbar.title = shortName
            else
                toolbar.title = debateManager.debateFormatName
        } else toolbar.setTitle(R.string.fragmentName_Debating_withoutFormat)

        val menu = toolbar.menu

        // show or hide the debate menu button
        val resetDebateItem = menu.findItem(R.id.timer_menuItem_resetDebate)
        resetDebateItem.isVisible = debateManager != null

        // display the appropriate bells icon
        val ringBellsItem = menu.findItem(R.id.timer_menuItem_ringBells)
        ringBellsItem.isChecked = mBellsEnabled
        ringBellsItem.setIcon(if (mBellsEnabled) R.drawable.ic_baseline_notifications_active_24
                              else R.drawable.ic_baseline_notifications_off_24)
    }

    companion object {
        private const val TAG = "DebatingTimerFragment"
        private const val PAGER_TAG = "DebateTDPagerAdapter"

        private const val NO_DEBATE_LOADED = "no_debate_loaded"

        private const val BUNDLE_KEY_DEBATE_MANAGER = "dm"
        private const val BUNDLE_KEY_XML_FILE_NAME = "xmlfn"
        private const val BUNDLE_KEY_IMPORT_INTENT_HANDLED = "iih"
        private const val BUNDLE_KEY_BLOCKING_DIALOG = "blocking-dialog"
        private const val PREFERENCE_XML_FILE_NAME = "xmlfn"
        private const val LAST_CHANGELOG_VERSION_SHOWN = "lastChangeLog"
        private const val NOTIFICATIONS_PERMISSION_DIALOG_SHOWN = "notifications-dialog"
        private const val DIALOG_ARGUMENT_SCHEMA_USED = "used"
        private const val DIALOG_ARGUMENT_SCHEMA_SUPPORTED = "supp"
        private const val DIALOG_ARGUMENT_FILE_NAME = "fn"
        private const val DIALOG_ARGUMENT_INCOMING_STYLE_NAME = "isn"
        private const val DIALOG_ARGUMENT_EXISTING_STYLE_NAME = "esn"
        private const val DIALOG_ARGUMENT_EXISTS = "efl"
        private const val DIALOG_ARGUMENT_SUGGESTED_FILE_NAME = "sfn"

        // Dialog tags that are attached to particular files must end in "/", as the name of the
        // file they relate to is appended to the tag.
        private const val DIALOG_TAG_SCHEMA_TOO_NEW = "toonew/"
        private const val DIALOG_TAG_CHANGELOG = "changelog"
        private const val DIALOG_TAG_IMPORT_CONFIRM = "import"
        private const val DIALOG_TAG_IMPORT_SUGGEST_REPLACEMENT = "replace"
        private const val DIALOG_TAG_NOTIFICATIONS_DENIED = "notifications"

        private const val SNACKBAR_DURATION_RESET_DEBATE = 1200
        private const val COLOUR_TRANSPARENT = 0
    }
}
