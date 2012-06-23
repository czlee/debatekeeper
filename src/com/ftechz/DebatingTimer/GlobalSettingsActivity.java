package com.ftechz.DebatingTimer;

import java.util.HashMap;

import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.content.res.Resources;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceManager;

public class GlobalSettingsActivity extends PreferenceActivity {

    private final HashMap<String, Integer> mPreferenceToStringResidMap = new HashMap<String, Integer>();

    private final ChangeSummaryOnSharedPreferenceChangeListener listener = new ChangeSummaryOnSharedPreferenceChangeListener();

    private static final int DEFAULT_COUNT_DIRECTION = 1;

    //******************************************************************************************
    // Private classes
    //******************************************************************************************

    private class ChangeSummaryOnSharedPreferenceChangeListener implements OnSharedPreferenceChangeListener {

        @Override
        public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
            if (mPreferenceToStringResidMap.containsKey(key))
                updateSummaryWithInt(key);
            else if (key.equals("countDirection"))
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

        mPreferenceToStringResidMap.put("firstOvertimeBell", R.string.PrefFirstOvertimeBellSummary);
        mPreferenceToStringResidMap.put("overtimeBellPeriod", R.string.PrefOvertimeBellPeriodSummary);

        updateSummaryWithInt("firstOvertimeBell");
        updateSummaryWithInt("overtimeBellPeriod");
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
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        int value  = prefs.getInt(key, 30);
        Preference pref = findPreference(key);
        int summaryTextResid = mPreferenceToStringResidMap.get(key);
        pref.setSummary(getString(summaryTextResid, value));
    }

    private void updateCountDirectionSummary() {
        String key = "countDirection";
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        Resources resources = this.getResources();
        String[] values = resources.getStringArray(R.array.PrefCountDirectionValues);
        String[] summaries = resources.getStringArray(R.array.PrefCountDirectionSummaries);
        String value = prefs.getString(key, values[DEFAULT_COUNT_DIRECTION]);
        int index = getIndexOfItemInArray(values, value, DEFAULT_COUNT_DIRECTION);
        Preference pref = findPreference(key);
        pref.setSummary(summaries[index]);
    }

    /**
     * Returns the index of an item in an array.
     * @param array the array
     * @param value the item to find in the array
     * @param defaultIndex the index to return if the item isn't found in the array
     * @return
     */
    private static int getIndexOfItemInArray(String[] array, String item, int defaultIndex) {
        for (int i = 0; i < array.length; i++) {
            if (item.equals(array[i]))
                return i;
        }
        return defaultIndex;
    }

}
