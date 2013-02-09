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

import java.util.HashMap;
import java.util.HashSet;

import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.net.Uri;
import android.os.Bundle;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceManager;

/**
 * @author Chuan-Zheng Lee
 * @since  2012-05-14
 */
public class GlobalSettingsActivity extends PreferenceActivity {

    private final HashMap<String, Integer> mPreferenceToSummaryResidMap = new HashMap<String, Integer>();
    private final HashMap<String, Integer> mPreferenceToDefaultResidMap = new HashMap<String, Integer>();

    private final HashSet<String> mIntegerPreferenceKeys       = new HashSet<String>();
    private final HashSet<String> mListPreferenceKeys          = new HashSet<String>();

    private final ChangeSummaryOnSharedPreferenceChangeListener listener = new ChangeSummaryOnSharedPreferenceChangeListener();

    private static String KEY_FIRST_OVERTIME_BELL;
    private static String KEY_OVERTIME_BELL_PERIOD;
    private static String KEY_COUNT_DIRECTION;
    private static String KEY_BACKGROUND_COLOUR_AREA;
    private static String KEY_FLASH_SCREEN_MODE;
    private static String KEY_POI_FLASH_SCREEN_MODE;
    private static String KEY_POI_TIMER_LEARN_MORE;
    private static String KEY_PREP_TIMER_COUNT_DIRECTION;
    private static String KEY_PREP_TIMER_BELLS;

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
    // Protected methods
    //******************************************************************************************
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.global_settings);

        KEY_FIRST_OVERTIME_BELL        = getString(R.string.pref_firstOvertimeBell_key);
        KEY_OVERTIME_BELL_PERIOD       = getString(R.string.pref_overtimeBellPeriod_key);
        KEY_COUNT_DIRECTION            = getString(R.string.pref_countDirection_key);
        KEY_BACKGROUND_COLOUR_AREA     = getString(R.string.pref_backgroundColourArea_key);
        KEY_FLASH_SCREEN_MODE          = getString(R.string.pref_flashScreenMode_key);
        KEY_POI_FLASH_SCREEN_MODE      = getString(R.string.pref_poiTimer_flashScreenMode_key);
        KEY_POI_TIMER_LEARN_MORE       = getString(R.string.pref_poiTimer_learnMore_key);
        KEY_PREP_TIMER_COUNT_DIRECTION = getString(R.string.pref_prepTimer_countDirection_key);
        KEY_PREP_TIMER_BELLS           = getString(R.string.pref_prepTimer_bells_key);

        mIntegerPreferenceKeys.add(KEY_FIRST_OVERTIME_BELL);
        mIntegerPreferenceKeys.add(KEY_OVERTIME_BELL_PERIOD);
        mListPreferenceKeys.add(KEY_COUNT_DIRECTION);
        mListPreferenceKeys.add(KEY_BACKGROUND_COLOUR_AREA);
        mListPreferenceKeys.add(KEY_FLASH_SCREEN_MODE);
        mListPreferenceKeys.add(KEY_POI_FLASH_SCREEN_MODE);
        mListPreferenceKeys.add(KEY_PREP_TIMER_COUNT_DIRECTION);

        mPreferenceToSummaryResidMap.put(KEY_FIRST_OVERTIME_BELL,        R.string.pref_firstOvertimeBell_summary);
        mPreferenceToSummaryResidMap.put(KEY_OVERTIME_BELL_PERIOD,       R.string.pref_overtimeBellPeriod_summary);
        mPreferenceToSummaryResidMap.put(KEY_COUNT_DIRECTION,            R.array.pref_countDirection_summaries);
        mPreferenceToSummaryResidMap.put(KEY_BACKGROUND_COLOUR_AREA,     R.array.pref_backgroundColourArea_summaries);
        mPreferenceToSummaryResidMap.put(KEY_FLASH_SCREEN_MODE,          R.array.pref_flashScreenMode_summaries);
        mPreferenceToSummaryResidMap.put(KEY_POI_FLASH_SCREEN_MODE,      R.array.pref_poiTimer_flashScreenMode_summaries);
        mPreferenceToSummaryResidMap.put(KEY_PREP_TIMER_COUNT_DIRECTION, R.array.pref_prepTimer_countDirection_summaries);

        mPreferenceToDefaultResidMap.put(KEY_FIRST_OVERTIME_BELL,        R.integer.prefDefault_firstOvertimeBell);
        mPreferenceToDefaultResidMap.put(KEY_OVERTIME_BELL_PERIOD,       R.integer.prefDefault_overtimeBellPeriod);
        mPreferenceToDefaultResidMap.put(KEY_COUNT_DIRECTION,            R.string.prefDefault_countDirection);
        mPreferenceToDefaultResidMap.put(KEY_BACKGROUND_COLOUR_AREA,     R.string.prefDefault_backgroundColourArea);
        mPreferenceToDefaultResidMap.put(KEY_FLASH_SCREEN_MODE,          R.string.prefDefault_flashScreenMode);
        mPreferenceToDefaultResidMap.put(KEY_POI_FLASH_SCREEN_MODE,      R.string.prefDefault_poiTimer_flashScreenMode);
        mPreferenceToDefaultResidMap.put(KEY_PREP_TIMER_COUNT_DIRECTION, R.string.prefDefault_prepTimer_countDirection);

        updateIntegerPreferenceSummary(KEY_FIRST_OVERTIME_BELL);
        updateIntegerPreferenceSummary(KEY_OVERTIME_BELL_PERIOD);
        updateListPreferenceSummary(KEY_COUNT_DIRECTION);
        updateListPreferenceSummary(KEY_BACKGROUND_COLOUR_AREA);
        updateListPreferenceSummary(KEY_FLASH_SCREEN_MODE);
        updateListPreferenceSummary(KEY_POI_FLASH_SCREEN_MODE);
        updateListPreferenceSummary(KEY_PREP_TIMER_COUNT_DIRECTION);

        // Set what the "Learn More" option in POIs timer category does
        getPreferenceManager().findPreference(KEY_POI_TIMER_LEARN_MORE)
            .setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                @Override
                public boolean onPreferenceClick(Preference preference) {
                    Uri uri = Uri.parse(getString(R.string.poiTimer_moreInfoUrl));
                    Intent intent = new Intent(Intent.ACTION_VIEW, uri);
                    startActivity(intent);
                    return true;
                }
            });

        // Set what the "Prep timer bells" option does
        Preference prefPrepTimerBells = getPreferenceManager().findPreference(KEY_PREP_TIMER_BELLS);
        prefPrepTimerBells.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                Intent intent = new Intent(GlobalSettingsActivity.this, PrepTimeBellsEditActivity.class);
                startActivity(intent);
                return true;
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        getPreferenceScreen().getSharedPreferences().registerOnSharedPreferenceChangeListener(listener);
    }


    @Override
    protected void onPause() {
        super.onPause();
        getPreferenceScreen().getSharedPreferences().unregisterOnSharedPreferenceChangeListener(listener);
    }

    //******************************************************************************************
    // Protected methods
    //******************************************************************************************
    private void updateIntegerPreferenceSummary(String key) {
        SharedPreferences prefs             = PreferenceManager.getDefaultSharedPreferences(this);
        int               defaultValueResid = mPreferenceToDefaultResidMap.get(key);
        int               value             = prefs.getInt(key, this.getResources().getInteger(defaultValueResid));
        Preference        pref              = findPreference(key);
        int               summaryTextResid  = mPreferenceToSummaryResidMap.get(key);

        pref.setSummary(getString(summaryTextResid, value));
    }

    private void updateListPreferenceSummary(String key) {
        SharedPreferences prefs              = PreferenceManager.getDefaultSharedPreferences(this);
        int               defaultValueResid  = mPreferenceToDefaultResidMap.get(key);
        String            value              = prefs.getString(key, getString(defaultValueResid));
        ListPreference    pref               = (ListPreference) findPreference(key);
        int               index              = pref.findIndexOfValue(value);
        int               summariesTextResid = mPreferenceToSummaryResidMap.get(key);
        String[]          summaries          = this.getResources().getStringArray(summariesTextResid);

        pref.setSummary(summaries[index]);
    }

}
