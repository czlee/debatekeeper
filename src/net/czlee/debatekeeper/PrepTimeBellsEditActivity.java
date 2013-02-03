/*
 * Copyright (C) 2013 Chuan-Zheng Lee
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

import java.util.ArrayList;

import net.czlee.debatekeeper.PrepTimeBellsManager.PrepTimeBellSpec;
import android.app.Activity;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.ListView;

/**
 * This activity allows the user to edit the preparation time bells.
 *
 * The activity does all its editing through an instance of {@link PrepTimeBellsManager}.
 * The implementation for loading and saving the edits to the {@link SharedPreferences} is
 * left to the {@link PrepTimeBellsManager}.
 *
 * @author Chuan-Zheng Lee
 * @since  2013-02-02
 *
 */
public class PrepTimeBellsEditActivity extends Activity {

    private PrepTimeBellsManager mPtbm;

    //******************************************************************************************
    // Protected methods
    //******************************************************************************************

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_prep_time_bells_edit);

        // Open the preferences file and instantiate the PrepTimeBellsManager
        SharedPreferences prefs = getSharedPreferences(PrepTimeBellsManager.PREP_TIME_BELLS_PREFERENCES_NAME, MODE_PRIVATE);
        mPtbm = new PrepTimeBellsManager(this);
        mPtbm.loadFromPreferences(prefs);

        // Populate the list
        populateList();
    }


    //******************************************************************************************
    // Private methods
    //******************************************************************************************
    private void populateList() {

        ArrayList<PrepTimeBellSpec> bellSpecs = mPtbm.getBellSpecs();
        ListView view = (ListView) findViewById(R.id.prepTimeBellsList);
        ArrayAdapter<PrepTimeBellSpec> adapter = new ArrayAdapter<PrepTimeBellSpec>(this, android.R.layout.simple_list_item_1, bellSpecs);
        view.setAdapter(adapter);

    }

}
