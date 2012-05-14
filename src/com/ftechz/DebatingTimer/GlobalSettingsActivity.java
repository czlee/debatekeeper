package com.ftechz.DebatingTimer;

import android.os.Bundle;
import android.preference.PreferenceActivity;

public class GlobalSettingsActivity extends PreferenceActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.global_settings);
    }

}
