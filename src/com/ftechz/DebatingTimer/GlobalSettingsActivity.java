package com.ftechz.DebatingTimer;

import java.util.HashMap;

import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceManager;

public class GlobalSettingsActivity extends PreferenceActivity {

    HashMap<String, Integer> mPreferenceToStringResidMap = new HashMap<String, Integer>();

    ChangeSummaryOnSharedPreferenceChangeListener listener = new ChangeSummaryOnSharedPreferenceChangeListener();

    //******************************************************************************************
    // Private classes
    //******************************************************************************************

    private class ChangeSummaryOnSharedPreferenceChangeListener implements OnSharedPreferenceChangeListener {

        @Override
        public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
            updateSummaryWithInt(key);
        }


    }

    //******************************************************************************************
    // Protected methods
    //******************************************************************************************
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.global_settings);

        mPreferenceToStringResidMap.put("firstOvertimeBell", R.string.PreferenceFirstOvertimeBellSummary);
        mPreferenceToStringResidMap.put("overtimeBellPeriod", R.string.PreferenceOvertimeBellPeriodSummary);

        updateSummaryWithInt("firstOvertimeBell");
        updateSummaryWithInt("overtimeBellPeriod");

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
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        int value  = prefs.getInt(key, 0);
        Preference pref = findPreference(key);
        int summaryTextResid = mPreferenceToStringResidMap.get(key);
        pref.setSummary(getString(summaryTextResid, value));
    }


}
