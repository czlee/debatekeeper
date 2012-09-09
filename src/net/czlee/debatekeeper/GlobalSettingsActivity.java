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

import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
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

    private final HashSet<String> mIntegerPreferenceKeys = new HashSet<String>();
    private final HashSet<String> mListPreferenceKeys    = new HashSet<String>();

    private final ChangeSummaryOnSharedPreferenceChangeListener listener = new ChangeSummaryOnSharedPreferenceChangeListener();

    private static String KEY_FIRST_OVERTIME_BELL;
    private static String KEY_OVERTIME_BELL_PERIOD;
    private static String KEY_COUNT_DIRECTION;
    private static String KEY_FLASH_SCREEN_MODE;
    private static String KEY_POI_FLASH_SCREEN_MODE;

    private static String KEY_POI_TIMER_ENABLE;
    private static String KEY_POI_SCREEN;

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
            else if (key.equals(KEY_POI_TIMER_ENABLE))
                updatePoiScreenSummary();
        }

    }

    //******************************************************************************************
    // Protected methods
    //******************************************************************************************
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.global_settings);

        KEY_FIRST_OVERTIME_BELL   = getString(R.string.PrefFirstOvertimeBellKey);
        KEY_OVERTIME_BELL_PERIOD  = getString(R.string.PrefOvertimeBellPeriodKey);
        KEY_COUNT_DIRECTION       = getString(R.string.PrefCountDirectionKey);
        KEY_FLASH_SCREEN_MODE     = getString(R.string.PrefFlashScreenModeKey);
        KEY_POI_FLASH_SCREEN_MODE = getString(R.string.PrefPoiFlashScreenModeKey);

        KEY_POI_TIMER_ENABLE      = getString(R.string.PrefPoiTimerEnableKey);
        KEY_POI_SCREEN            = getString(R.string.PrefScreenPoiKey);


        mIntegerPreferenceKeys.add(KEY_FIRST_OVERTIME_BELL);
        mIntegerPreferenceKeys.add(KEY_OVERTIME_BELL_PERIOD);
        mListPreferenceKeys.add(KEY_COUNT_DIRECTION);
        mListPreferenceKeys.add(KEY_FLASH_SCREEN_MODE);
        mListPreferenceKeys.add(KEY_POI_FLASH_SCREEN_MODE);

        mPreferenceToSummaryResidMap.put(KEY_FIRST_OVERTIME_BELL,   R.string.PrefFirstOvertimeBellSummary);
        mPreferenceToSummaryResidMap.put(KEY_OVERTIME_BELL_PERIOD,  R.string.PrefOvertimeBellPeriodSummary);
        mPreferenceToSummaryResidMap.put(KEY_COUNT_DIRECTION,       R.array.PrefCountDirectionSummaries);
        mPreferenceToSummaryResidMap.put(KEY_FLASH_SCREEN_MODE,     R.array.PrefFlashScreenModeSummaries);
        mPreferenceToSummaryResidMap.put(KEY_POI_FLASH_SCREEN_MODE, R.array.PrefPoiFlashScreenModeSummaries);

        mPreferenceToDefaultResidMap.put(KEY_FIRST_OVERTIME_BELL,   R.integer.DefaultPrefFirstOvertimeBell);
        mPreferenceToDefaultResidMap.put(KEY_OVERTIME_BELL_PERIOD,  R.integer.DefaultPrefOvertimeBellPeriod);
        mPreferenceToDefaultResidMap.put(KEY_COUNT_DIRECTION,       R.string.DefaultPrefCountDirection);
        mPreferenceToDefaultResidMap.put(KEY_FLASH_SCREEN_MODE,     R.string.DefaultPrefFlashScreenMode);
        mPreferenceToDefaultResidMap.put(KEY_POI_FLASH_SCREEN_MODE, R.string.DefaultPrefPoiFlashScreenMode);

        updateIntegerPreferenceSummary(KEY_FIRST_OVERTIME_BELL);
        updateIntegerPreferenceSummary(KEY_OVERTIME_BELL_PERIOD);
        updateListPreferenceSummary(KEY_COUNT_DIRECTION);
        updateListPreferenceSummary(KEY_FLASH_SCREEN_MODE);
        updateListPreferenceSummary(KEY_POI_FLASH_SCREEN_MODE);
        updatePoiScreenSummary();

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

    private void updatePoiScreenSummary() {
        SharedPreferences prefs              = PreferenceManager.getDefaultSharedPreferences(this);
        int               defaultValueResId  = R.bool.DefaultPrefPoiTimerEnable;
        boolean           enabled            = prefs.getBoolean(KEY_POI_TIMER_ENABLE, this.getResources().getBoolean(defaultValueResId));
        final Preference        pref               = findPreference(KEY_POI_SCREEN);
        final int               summaryTextResid   = (enabled) ? R.string.PrefPoiScreenSummaryWhenEnabled : R.string.PrefPoiScreenSummaryWhenDisabled;

        pref.setSummary(getString(summaryTextResid));
        this.onContentChanged();
    }

}
