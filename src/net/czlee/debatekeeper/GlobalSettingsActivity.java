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

import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.content.res.Resources;
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

    private final ChangeSummaryOnSharedPreferenceChangeListener listener = new ChangeSummaryOnSharedPreferenceChangeListener();

    private static String KEY_FIRST_OVERTIME_BELL;
    private static String KEY_OVERTIME_BELL_PERIOD;
    private static String KEY_COUNT_DIRECTION;

    //******************************************************************************************
    // Private classes
    //******************************************************************************************

    private class ChangeSummaryOnSharedPreferenceChangeListener implements OnSharedPreferenceChangeListener {

        @Override
        public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
            if (mPreferenceToSummaryResidMap.containsKey(key))
                updateSummaryWithInt(key);
            else if (key.equals(KEY_COUNT_DIRECTION))
                updateCountDirectionSummary();
        }


    }

    //******************************************************************************************
    // Protected methods
    //******************************************************************************************
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.global_settings);

        KEY_FIRST_OVERTIME_BELL  = getString(R.string.PrefFirstOvertimeBellKey);
        KEY_OVERTIME_BELL_PERIOD = getString(R.string.PrefOvertimeBellPeriodKey);
        KEY_COUNT_DIRECTION      = getString(R.string.PrefCountDirectionKey);

        mPreferenceToSummaryResidMap.put(KEY_FIRST_OVERTIME_BELL,  R.string.PrefFirstOvertimeBellSummary);
        mPreferenceToSummaryResidMap.put(KEY_OVERTIME_BELL_PERIOD, R.string.PrefOvertimeBellPeriodSummary);

        mPreferenceToDefaultResidMap.put(KEY_FIRST_OVERTIME_BELL,  R.integer.DefaultPrefFirstOvertimeBell);
        mPreferenceToDefaultResidMap.put(KEY_OVERTIME_BELL_PERIOD, R.integer.DefaultPrefOvertimeBellPeriod);

        updateSummaryWithInt(KEY_FIRST_OVERTIME_BELL);
        updateSummaryWithInt(KEY_OVERTIME_BELL_PERIOD);
        updateCountDirectionSummary();

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
    private void updateSummaryWithInt(String key) {
        SharedPreferences prefs             = PreferenceManager.getDefaultSharedPreferences(this);
        int               defaultValueResid = mPreferenceToDefaultResidMap.get(key);
        int               value             = prefs.getInt(key, this.getResources().getInteger(defaultValueResid));
        Preference        pref              = findPreference(key);
        int               summaryTextResid  = mPreferenceToSummaryResidMap.get(key);

        pref.setSummary(getString(summaryTextResid, value));
    }

    private void updateCountDirectionSummary() {
        String            key       = KEY_COUNT_DIRECTION;
        SharedPreferences prefs     = PreferenceManager.getDefaultSharedPreferences(this);
        Resources         resources = this.getResources();
        String[]          summaries = resources.getStringArray(R.array.PrefCountDirectionSummaries);
        String            value     = prefs.getString(key, getString(R.string.DefaultPrefCountDirection));
        ListPreference    pref      = (ListPreference) findPreference(key);
        int               index     = pref.findIndexOfValue(value);

        pref.setSummary(summaries[index]);
    }

}
