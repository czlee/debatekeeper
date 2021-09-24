/*
 * Copyright (C) 2012 Chuan-Zheng Lee
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

import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.net.Uri;
import android.os.Bundle;

import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceManager;

import java.util.HashMap;
import java.util.HashSet;

/**
 * @author Chuan-Zheng Lee
 * @since  2012-05-14
 */
public class GlobalSettingsSubFragment extends PreferenceFragmentCompat {

    private final HashMap<String, Integer> mPreferenceToSummaryResIdMap = new HashMap<>();
    private final HashMap<String, Integer> mPreferenceToDefaultResIdMap = new HashMap<>();

    private final HashSet<String> mIntegerPreferenceKeys       = new HashSet<>();
    private final HashSet<String> mListPreferenceKeys          = new HashSet<>();

    private final ChangeSummaryOnSharedPreferenceChangeListener listener = new ChangeSummaryOnSharedPreferenceChangeListener();

    //******************************************************************************************
    // Private classes
    //******************************************************************************************

    private class ChangeSummaryOnSharedPreferenceChangeListener implements OnSharedPreferenceChangeListener {

        @Override
        public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
            if (mIntegerPreferenceKeys.contains(key))
                updateIntegerPreferenceSummary(key);
            else if (mListPreferenceKeys.contains(key))
                updateListPreferenceSummary(key);
        }

    }

    //******************************************************************************************
    // Public methods
    //******************************************************************************************
    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        addPreferencesFromResource(R.xml.overtime_bell_number_settings);
        addPreferencesFromResource(R.xml.general_settings);

        // *************************************************************************************
        // Set up key-to-parameter maps

        String KEY_FIRST_OVERTIME_BELL        = getString(R.string.pref_firstOvertimeBell_key);
        String KEY_OVERTIME_BELL_PERIOD       = getString(R.string.pref_overtimeBellPeriod_key);
        String KEY_COUNT_DIRECTION            = getString(R.string.pref_countDirection_key);
        String KEY_BACKGROUND_COLOUR_AREA     = getString(R.string.pref_backgroundColourArea_key);
        String KEY_FLASH_SCREEN_MODE          = getString(R.string.pref_flashScreenMode_key);
        String KEY_POI_FLASH_SCREEN_MODE      = getString(R.string.pref_poiTimer_flashScreenMode_key);
        String KEY_POI_TIMER_LEARN_MORE       = getString(R.string.pref_poiTimer_learnMore_key);
        String KEY_PREP_TIMER_COUNT_DIRECTION = getString(R.string.pref_prepTimer_countDirection_key);
        String KEY_PREP_TIMER_BELLS           = getString(R.string.pref_prepTimer_bells_key);

        mIntegerPreferenceKeys.add(KEY_FIRST_OVERTIME_BELL);
        mIntegerPreferenceKeys.add(KEY_OVERTIME_BELL_PERIOD);
        mListPreferenceKeys.add(KEY_COUNT_DIRECTION);
        mListPreferenceKeys.add(KEY_BACKGROUND_COLOUR_AREA);
        mListPreferenceKeys.add(KEY_FLASH_SCREEN_MODE);
        mListPreferenceKeys.add(KEY_POI_FLASH_SCREEN_MODE);
        mListPreferenceKeys.add(KEY_PREP_TIMER_COUNT_DIRECTION);

        mPreferenceToSummaryResIdMap.put(KEY_FIRST_OVERTIME_BELL,        R.string.pref_firstOvertimeBell_summary);
        mPreferenceToSummaryResIdMap.put(KEY_OVERTIME_BELL_PERIOD,       R.string.pref_overtimeBellPeriod_summary);
        mPreferenceToSummaryResIdMap.put(KEY_COUNT_DIRECTION,            R.array.pref_countDirection_summaries);
        mPreferenceToSummaryResIdMap.put(KEY_BACKGROUND_COLOUR_AREA,     R.array.pref_backgroundColourArea_summaries);
        mPreferenceToSummaryResIdMap.put(KEY_FLASH_SCREEN_MODE,          R.array.pref_flashScreenMode_summaries);
        mPreferenceToSummaryResIdMap.put(KEY_POI_FLASH_SCREEN_MODE,      R.array.pref_poiTimer_flashScreenMode_summaries);
        mPreferenceToSummaryResIdMap.put(KEY_PREP_TIMER_COUNT_DIRECTION, R.array.pref_prepTimer_countDirection_summaries);

        mPreferenceToDefaultResIdMap.put(KEY_FIRST_OVERTIME_BELL,        R.integer.prefDefault_firstOvertimeBell);
        mPreferenceToDefaultResIdMap.put(KEY_OVERTIME_BELL_PERIOD,       R.integer.prefDefault_overtimeBellPeriod);
        mPreferenceToDefaultResIdMap.put(KEY_COUNT_DIRECTION,            R.string.prefDefault_countDirection);
        mPreferenceToDefaultResIdMap.put(KEY_BACKGROUND_COLOUR_AREA,     R.string.prefDefault_backgroundColourArea);
        mPreferenceToDefaultResIdMap.put(KEY_FLASH_SCREEN_MODE,          R.string.prefDefault_flashScreenMode);
        mPreferenceToDefaultResIdMap.put(KEY_POI_FLASH_SCREEN_MODE,      R.string.prefDefault_poiTimer_flashScreenMode);
        mPreferenceToDefaultResIdMap.put(KEY_PREP_TIMER_COUNT_DIRECTION, R.string.prefDefault_prepTimer_countDirection);

        updateIntegerPreferenceSummary(KEY_FIRST_OVERTIME_BELL);
        updateIntegerPreferenceSummary(KEY_OVERTIME_BELL_PERIOD);
        updateListPreferenceSummary(KEY_COUNT_DIRECTION);
        updateListPreferenceSummary(KEY_BACKGROUND_COLOUR_AREA);
        updateListPreferenceSummary(KEY_FLASH_SCREEN_MODE);
        updateListPreferenceSummary(KEY_POI_FLASH_SCREEN_MODE);
        updateListPreferenceSummary(KEY_PREP_TIMER_COUNT_DIRECTION);

        // *************************************************************************************
        // Customise a few preferences

        // Set what the "Learn More" option in POIs timer category does
        getPreferenceManager().findPreference(KEY_POI_TIMER_LEARN_MORE)
            .setOnPreferenceClickListener(preference -> {
                Uri uri = Uri.parse(getString(R.string.poiTimer_moreInfoUrl));
                Intent intent = new Intent(Intent.ACTION_VIEW, uri);
                startActivity(intent);
                return true;
            });

        // Set what the "Prep timer bells" option does
        Preference prefPrepTimerBells = getPreferenceManager().findPreference(KEY_PREP_TIMER_BELLS);
        prefPrepTimerBells.setOnPreferenceClickListener(preference -> {
            Intent intent = new Intent(getActivity(), PrepTimeBellsEditActivity.class);
            startActivity(intent);
            return true;
        });

    }

    @Override
    public void onResume() {
        super.onResume();
        getPreferenceScreen().getSharedPreferences().registerOnSharedPreferenceChangeListener(listener);
    }

    @Override
    public void onPause() {
        super.onPause();
        getPreferenceScreen().getSharedPreferences().unregisterOnSharedPreferenceChangeListener(listener);
    }

    //******************************************************************************************
    // Private methods
    //******************************************************************************************
    private void updateIntegerPreferenceSummary(String key) {
        SharedPreferences prefs             = PreferenceManager.getDefaultSharedPreferences(getActivity());
        int               defaultValueResId = mPreferenceToDefaultResIdMap.get(key);
        int               value             = prefs.getInt(key, this.getResources().getInteger(defaultValueResId));
        Preference        pref              = findPreference(key);
        int               summaryTextResId  = mPreferenceToSummaryResIdMap.get(key);

        pref.setSummary(getString(summaryTextResId, value));
    }

    private void updateListPreferenceSummary(String key) {
        SharedPreferences prefs              = PreferenceManager.getDefaultSharedPreferences(getActivity());
        int               defaultValueResId  = mPreferenceToDefaultResIdMap.get(key);
        String            value              = prefs.getString(key, getString(defaultValueResId));
        ListPreference    pref               = (ListPreference) findPreference(key);
        int               index              = pref.findIndexOfValue(value);
        int               summariesTextResId = mPreferenceToSummaryResIdMap.get(key);
        String[]          summaries          = this.getResources().getStringArray(summariesTextResId);

        pref.setSummary(summaries[index]);
    }

}
