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

    private final HashMap<String, String>  mEnablePreferenceToScreenPreferenceMap
        = new HashMap<String, String>();
    private final HashMap<String, Integer[]> mScreenPreferenceToSummaryResidMap
        = new HashMap<String, Integer[]>();

    private final HashSet<String> mIntegerPreferenceKeys       = new HashSet<String>();
    private final HashSet<String> mListPreferenceKeys          = new HashSet<String>();
    private final HashSet<String> mScreenEnablePreferenceKeys  = new HashSet<String>();

    private final ChangeSummaryOnSharedPreferenceChangeListener listener = new ChangeSummaryOnSharedPreferenceChangeListener();

    private static String KEY_FIRST_OVERTIME_BELL;
    private static String KEY_OVERTIME_BELL_PERIOD;
    private static String KEY_COUNT_DIRECTION;
    private static String KEY_FLASH_SCREEN_MODE;
    private static String KEY_POI_FLASH_SCREEN_MODE;
    private static String KEY_POI_TIMER_LEARN_MORE;
    private static String KEY_PREP_TIMER_COUNT_DIRECTION;

    private static String KEY_POI_TIMER_ENABLE;
    private static String KEY_POI_TIMER_SCREEN;
    private static String KEY_PREP_TIMER_ENABLE;
    private static String KEY_PREP_TIMER_SCREEN;

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
            else if (mScreenEnablePreferenceKeys.contains(key))
                updateScreenSummaryFromEnablePreference(key);
        }

    }

    //******************************************************************************************
    // Protected methods
    //******************************************************************************************
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.global_settings);

        KEY_FIRST_OVERTIME_BELL        = getString(R.string.PrefFirstOvertimeBellKey);
        KEY_OVERTIME_BELL_PERIOD       = getString(R.string.PrefOvertimeBellPeriodKey);
        KEY_COUNT_DIRECTION            = getString(R.string.PrefCountDirectionKey);
        KEY_FLASH_SCREEN_MODE          = getString(R.string.PrefFlashScreenModeKey);
        KEY_POI_FLASH_SCREEN_MODE      = getString(R.string.PrefPoiFlashScreenModeKey);
        KEY_POI_TIMER_LEARN_MORE       = getString(R.string.PrefPoiLearnMoreKey);
        KEY_PREP_TIMER_COUNT_DIRECTION = getString(R.string.PrefPrepTimerCountDirectionKey);

        KEY_POI_TIMER_ENABLE  = getString(R.string.PrefPoiTimerEnableKey);
        KEY_POI_TIMER_SCREEN  = getString(R.string.PrefScreenPoiKey);
        KEY_PREP_TIMER_ENABLE = getString(R.string.PrefPrepTimerEnableKey);
        KEY_PREP_TIMER_SCREEN = getString(R.string.PrefScreenPrepTimeKey);

        mIntegerPreferenceKeys.add(KEY_FIRST_OVERTIME_BELL);
        mIntegerPreferenceKeys.add(KEY_OVERTIME_BELL_PERIOD);
        mListPreferenceKeys.add(KEY_COUNT_DIRECTION);
        mListPreferenceKeys.add(KEY_FLASH_SCREEN_MODE);
        mListPreferenceKeys.add(KEY_POI_FLASH_SCREEN_MODE);
        mListPreferenceKeys.add(KEY_PREP_TIMER_COUNT_DIRECTION);
        mScreenEnablePreferenceKeys.add(KEY_POI_TIMER_ENABLE);
        mScreenEnablePreferenceKeys.add(KEY_PREP_TIMER_ENABLE);

        mPreferenceToSummaryResidMap.put(KEY_FIRST_OVERTIME_BELL,        R.string.PrefFirstOvertimeBellSummary);
        mPreferenceToSummaryResidMap.put(KEY_OVERTIME_BELL_PERIOD,       R.string.PrefOvertimeBellPeriodSummary);
        mPreferenceToSummaryResidMap.put(KEY_COUNT_DIRECTION,            R.array.PrefCountDirectionSummaries);
        mPreferenceToSummaryResidMap.put(KEY_FLASH_SCREEN_MODE,          R.array.PrefFlashScreenModeSummaries);
        mPreferenceToSummaryResidMap.put(KEY_POI_FLASH_SCREEN_MODE,      R.array.PrefPoiFlashScreenModeSummaries);
        mPreferenceToSummaryResidMap.put(KEY_PREP_TIMER_COUNT_DIRECTION, R.array.PrefCountDirectionSummaries);

        mPreferenceToDefaultResidMap.put(KEY_FIRST_OVERTIME_BELL,        R.integer.DefaultPrefFirstOvertimeBell);
        mPreferenceToDefaultResidMap.put(KEY_OVERTIME_BELL_PERIOD,       R.integer.DefaultPrefOvertimeBellPeriod);
        mPreferenceToDefaultResidMap.put(KEY_COUNT_DIRECTION,            R.string.DefaultPrefCountDirection);
        mPreferenceToDefaultResidMap.put(KEY_FLASH_SCREEN_MODE,          R.string.DefaultPrefFlashScreenMode);
        mPreferenceToDefaultResidMap.put(KEY_POI_FLASH_SCREEN_MODE,      R.string.DefaultPrefPoiFlashScreenMode);
        mPreferenceToDefaultResidMap.put(KEY_PREP_TIMER_COUNT_DIRECTION, R.string.DefaultPrefPrepTimerCountDirection);
        mPreferenceToDefaultResidMap.put(KEY_POI_TIMER_ENABLE,           R.bool.DefaultPrefPoiTimerEnable);
        mPreferenceToDefaultResidMap.put(KEY_PREP_TIMER_ENABLE,          R.bool.DefaultPrefPrepTimerEnable);

        mEnablePreferenceToScreenPreferenceMap.put(KEY_POI_TIMER_ENABLE,  KEY_POI_TIMER_SCREEN);
        mEnablePreferenceToScreenPreferenceMap.put(KEY_PREP_TIMER_ENABLE, KEY_PREP_TIMER_SCREEN);

        Integer[] prepTimerScreenSummaries = {R.string.PrefPrepTimeScreenSummaryWhenDisabled, R.string.PrefPrepTimeScreenSummaryWhenEnabled};
        mScreenPreferenceToSummaryResidMap.put(KEY_PREP_TIMER_SCREEN, prepTimerScreenSummaries);

        Integer[] poiTimerScreenSummaries = {R.string.PrefPoiScreenSummaryWhenDisabled, R.string.PrefPoiScreenSummaryWhenEnabled};
        mScreenPreferenceToSummaryResidMap.put(KEY_POI_TIMER_SCREEN, poiTimerScreenSummaries);

        updateIntegerPreferenceSummary(KEY_FIRST_OVERTIME_BELL);
        updateIntegerPreferenceSummary(KEY_OVERTIME_BELL_PERIOD);
        updateListPreferenceSummary(KEY_COUNT_DIRECTION);
        updateListPreferenceSummary(KEY_FLASH_SCREEN_MODE);
        updateListPreferenceSummary(KEY_POI_FLASH_SCREEN_MODE);
        updateListPreferenceSummary(KEY_PREP_TIMER_COUNT_DIRECTION);
        updateScreenSummaryFromEnablePreference(KEY_POI_TIMER_ENABLE);
        updateScreenSummaryFromEnablePreference(KEY_PREP_TIMER_ENABLE);

        // Set what the "Learn More" option in POIs timer sub-screen does
        getPreferenceManager().findPreference(KEY_POI_TIMER_LEARN_MORE)
            .setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                @Override
                public boolean onPreferenceClick(Preference preference) {
                    Uri uri = Uri.parse(getString(R.string.PoiTimerMoreInfoUrl));
                    Intent intent = new Intent(Intent.ACTION_VIEW, uri);
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

    private void updateScreenSummaryFromEnablePreference(String enablePreferenceKey) {
        SharedPreferences prefs               = PreferenceManager.getDefaultSharedPreferences(this);
        String            screenPreferenceKey = mEnablePreferenceToScreenPreferenceMap.get(enablePreferenceKey);
        int               defaultValueResid   = mPreferenceToDefaultResidMap.get(enablePreferenceKey);
        boolean           enabled             = prefs.getBoolean(enablePreferenceKey, this.getResources().getBoolean(defaultValueResid));
        final Preference  pref                = findPreference(screenPreferenceKey);
        final Integer[]   summaryTextResids   = mScreenPreferenceToSummaryResidMap.get(screenPreferenceKey);
        final int         summaryTextResid    = summaryTextResids[(enabled) ? 1 : 0];

        pref.setSummary(getString(summaryTextResid));
        this.onContentChanged();
    }

}
