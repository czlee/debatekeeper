/*
 * Copyright (C) 2012 Chuan-Zheng Lee
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

import android.content.SharedPreferences
import android.content.SharedPreferences.OnSharedPreferenceChangeListener
import android.os.Bundle
import androidx.navigation.fragment.NavHostFragment
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceManager

/**
 * @author Chuan-Zheng Lee
 * @since  2012-05-14
 */
class SettingsSubFragment : PreferenceFragmentCompat() {

    private val mPreferenceToSummaryResIdMap = HashMap<String, Int>()
    private val mPreferenceToDefaultResIdMap = HashMap<String, Int>()

    private val mIntegerPreferenceKeys = HashSet<String>()
    private val mListPreferenceKeys = HashSet<String>()

    private val listener = ChangeSummaryOnSharedPreferenceChangeListener()

    //******************************************************************************************
    // Private classes
    //******************************************************************************************

    private inner class ChangeSummaryOnSharedPreferenceChangeListener :
            OnSharedPreferenceChangeListener {

        override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences,
                                               key: String?) {
            if (mIntegerPreferenceKeys.contains(key))
                updateIntegerPreferenceSummary(key!!)
            else if (mListPreferenceKeys.contains(key))
                updateListPreferenceSummary(key!!)
        }
    }

    //******************************************************************************************
    // Public methods
    //******************************************************************************************

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        addPreferencesFromResource(R.xml.overtime_bell_number_settings)
        addPreferencesFromResource(R.xml.general_settings)

        // *************************************************************************************
        // Set up key-to-parameter maps

        val keyFirstOvertimeBell = getString(R.string.pref_firstOvertimeBell_key)
        val keyOvertimeBellPeriod = getString(R.string.pref_overtimeBellPeriod_key)
        val keyCountDirection = getString(R.string.pref_countDirection_key)
        val keyBackgroundColourArea = getString(R.string.pref_backgroundColourArea_key)
        val keyFlashScreenMode = getString(R.string.pref_flashScreenMode_key)
        val keyPoiFlashScreenMode = getString(R.string.pref_poiTimer_flashScreenMode_key)
        val keyPrepTimerCountDirection = getString(R.string.pref_prepTimer_countDirection_key)
        val keyPrepTimerBells = getString(R.string.pref_prepTimer_bells_key)

        mIntegerPreferenceKeys.add(keyFirstOvertimeBell)
        mIntegerPreferenceKeys.add(keyOvertimeBellPeriod)
        mListPreferenceKeys.add(keyCountDirection)
        mListPreferenceKeys.add(keyBackgroundColourArea)
        mListPreferenceKeys.add(keyFlashScreenMode)
        mListPreferenceKeys.add(keyPoiFlashScreenMode)
        mListPreferenceKeys.add(keyPrepTimerCountDirection)

        mPreferenceToSummaryResIdMap[keyFirstOvertimeBell] = R.string.pref_firstOvertimeBell_summary
        mPreferenceToSummaryResIdMap[keyOvertimeBellPeriod] = R.string.pref_overtimeBellPeriod_summary
        mPreferenceToSummaryResIdMap[keyCountDirection] = R.array.pref_countDirection_summaries
        mPreferenceToSummaryResIdMap[keyBackgroundColourArea] = R.array.pref_backgroundColourArea_summaries
        mPreferenceToSummaryResIdMap[keyFlashScreenMode] = R.array.pref_flashScreenMode_summaries
        mPreferenceToSummaryResIdMap[keyPoiFlashScreenMode] = R.array.pref_poiTimer_flashScreenMode_summaries
        mPreferenceToSummaryResIdMap[keyPrepTimerCountDirection] = R.array.pref_prepTimer_countDirection_summaries

        mPreferenceToDefaultResIdMap[keyFirstOvertimeBell] = R.integer.prefDefault_firstOvertimeBell
        mPreferenceToDefaultResIdMap[keyOvertimeBellPeriod] = R.integer.prefDefault_overtimeBellPeriod
        mPreferenceToDefaultResIdMap[keyCountDirection] = R.string.prefDefault_countDirection
        mPreferenceToDefaultResIdMap[keyBackgroundColourArea] = R.string.prefDefault_backgroundColourArea
        mPreferenceToDefaultResIdMap[keyFlashScreenMode] = R.string.prefDefault_flashScreenMode
        mPreferenceToDefaultResIdMap[keyPoiFlashScreenMode] = R.string.prefDefault_poiTimer_flashScreenMode
        mPreferenceToDefaultResIdMap[keyPrepTimerCountDirection] = R.string.prefDefault_prepTimer_countDirection

        updateIntegerPreferenceSummary(keyFirstOvertimeBell)
        updateIntegerPreferenceSummary(keyOvertimeBellPeriod)
        updateListPreferenceSummary(keyCountDirection)
        updateListPreferenceSummary(keyBackgroundColourArea)
        updateListPreferenceSummary(keyFlashScreenMode)
        updateListPreferenceSummary(keyPoiFlashScreenMode)
        updateListPreferenceSummary(keyPrepTimerCountDirection)

        // Set what the "Prep timer bells" option does
        val prefPrepTimerBells =
                preferenceManager.findPreference<Preference>(keyPrepTimerBells)
        prefPrepTimerBells?.setOnPreferenceClickListener {
            val action = SettingsFragmentDirections.actionEditPrepTimeBells()
            NavHostFragment.findNavController(this@SettingsSubFragment).navigate(action)
            true
        }
    }

    override fun onResume() {
        super.onResume()
        preferenceScreen.sharedPreferences!!.registerOnSharedPreferenceChangeListener(listener)
    }

    override fun onPause() {
        super.onPause()
        preferenceScreen.sharedPreferences!!.unregisterOnSharedPreferenceChangeListener(listener)
    }

    //******************************************************************************************
    // Private methods
    //******************************************************************************************

    private fun updateIntegerPreferenceSummary(key: String) {
        val prefs = PreferenceManager.getDefaultSharedPreferences(requireActivity())
        val defaultValueResId = mPreferenceToDefaultResIdMap[key]!!

        val value = prefs.getInt(key, resources.getInteger(defaultValueResId))
        val summaryTextResId = mPreferenceToSummaryResIdMap[key]!!

        val pref = findPreference<Preference>(key)!!
        pref.summary = getString(summaryTextResId, value)
    }

    private fun updateListPreferenceSummary(key: String) {
        val prefs = PreferenceManager.getDefaultSharedPreferences(requireActivity())
        val defaultValueResId = mPreferenceToDefaultResIdMap[key]!!

        val value = prefs.getString(key, getString(defaultValueResId))
        val pref = findPreference<ListPreference>(key)!!

        val index = pref.findIndexOfValue(value)
        val summariesTextResId = mPreferenceToSummaryResIdMap[key]!!

        val summaries = resources.getStringArray(summariesTextResId)

        pref.summary = summaries[index]
    }
}
