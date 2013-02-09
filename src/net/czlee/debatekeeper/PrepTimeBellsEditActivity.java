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
import java.util.ListIterator;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.Editable;
import android.util.Log;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.AdapterView;
import android.widget.AdapterView.AdapterContextMenuInfo;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.TimePicker;
import android.widget.Toast;

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

    private static final int DIALOG_ADD_BELL        = 0;
    private static final int DIALOG_CLEAR_ALL_BELLS = 1;
    private static final int DIALOG_EDIT_BELL       = 2;

    private static final String KEY_INDEX = "index";

    // These must match the strings in R.string.AddPrepTimeBellTypes (in prep_time_bells_edit.xml)
    private static final int ADD_PREP_TIME_BELL_TYPE_START      = 0;
    private static final int ADD_PREP_TIME_BELL_TYPE_FINISH     = 1;
    private static final int ADD_PREP_TIME_BELL_TYPE_PERCENTAGE = 2;

    //******************************************************************************************
    // Private classes
    //******************************************************************************************

    private class AddButtonOnClickListener implements OnClickListener {
        @Override
        public void onClick(View v) {
            // Generate the "add bell" dialog
            showDialog(DIALOG_ADD_BELL);
        }
    }

    private class ClearButtonOnClickListener implements OnClickListener {
        @Override
        public void onClick(View v) {
            // Generate the "are you sure?" dialog
            showDialog(DIALOG_CLEAR_ALL_BELLS);
        }
    }

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
        refreshBellsList();

        // Set the OnClickListeners
        ((Button) findViewById(R.id.prepTimeBellsEditor_addBellButton))
                .setOnClickListener(new AddButtonOnClickListener());
        ((Button) findViewById(R.id.prepTimeBellsEditor_clearAllButton))
                .setOnClickListener(new ClearButtonOnClickListener());

        // Register a context menu for the bells list items
        registerForContextMenu(findViewById(R.id.prepTimeBellsEditor_bellsList));

        // Register for the list to show a toast on non-long click
        ((ListView) findViewById(R.id.prepTimeBellsEditor_bellsList)).setOnItemClickListener(new OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int pos, long id) {
                Toast.makeText(PrepTimeBellsEditActivity.this,
                        R.string.prepTimeBellsEditor_contextMenu_tip, Toast.LENGTH_SHORT).show();
            }
        });

    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo) {
        super.onCreateContextMenu(menu, v, menuInfo);
        AdapterContextMenuInfo info = (AdapterContextMenuInfo) menuInfo;
        String title = getString(R.string.prepTimeBellsEditor_contextMenu_header,
                mPtbm.getBellDescription(info.position));
        menu.setHeaderTitle(title);
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.prep_time_bells_list_context, menu);
    }

    @Override
    protected Dialog onCreateDialog(int id, Bundle args) {
        switch (id) {
        case DIALOG_ADD_BELL:
        case DIALOG_EDIT_BELL:
            return getAddOrEditBellDialog();
        case DIALOG_CLEAR_ALL_BELLS:
            return getClearBellsDialog();
        }
        return super.onCreateDialog(id, args);
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        AdapterContextMenuInfo info = (AdapterContextMenuInfo) item.getMenuInfo();
        switch (item.getItemId()) {
        case R.id.prepTimeBellsEditor_contextMenu_edit:
            Bundle args = mPtbm.getBellBundle(info.position);
            args.putInt(KEY_INDEX, info.position);
            showDialog(DIALOG_EDIT_BELL, args);
            return true;
        case R.id.prepTimeBellsEditor_contextMenu_delete:
            mPtbm.deleteBell(info.position);
            refreshBellsList();
            return true;
        default:
            return super.onContextItemSelected(item);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        SharedPreferences prefs = getSharedPreferences(PrepTimeBellsManager.PREP_TIME_BELLS_PREFERENCES_NAME, MODE_PRIVATE);
        mPtbm.saveToPreferences(prefs);
    }

    @Override
    protected void onPrepareDialog(int id, Dialog dialog, Bundle args) {
        super.onPrepareDialog(id, dialog, args);
        switch (id) {
        case DIALOG_ADD_BELL:
            prepareAddBellDialog(dialog);
            break;
        case DIALOG_EDIT_BELL:
            prepareEditBellDialog(dialog, args);
            break;
        case DIALOG_CLEAR_ALL_BELLS:
            prepareClearBellsDialog(dialog);
            break;
        }
    }

    //******************************************************************************************
    // Private methods
    //******************************************************************************************

    private Dialog getAddOrEditBellDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);

        View content = getLayoutInflater().inflate(R.layout.add_prep_time_bell, null);

        // Take note of the form elements
        final Spinner    typeSpinner = (Spinner)    content.findViewById(R.id.addBellTypeSpinner);
        final TimePicker timePicker  = (TimePicker) content.findViewById(R.id.addBellTimePicker);
        final EditText   editText    = (EditText)   content.findViewById(R.id.addBellEditText);

        // Format the form elements
        timePicker.setIs24HourView(true);
        ArrayAdapter<CharSequence> typesAdapter = ArrayAdapter.createFromResource(this,
                R.array.prepTimeBellsEditor_editBellDialog_types, android.R.layout.simple_spinner_item);
        typesAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        typeSpinner.setAdapter(typesAdapter);

        typeSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int pos, long id) {
                // Show either the EditText or TimePicker, whichever is appropriate
                if (pos == ADD_PREP_TIME_BELL_TYPE_PERCENTAGE) { // This is "percentage through prep time"
                    editText.setVisibility(View.VISIBLE);
                    timePicker.setVisibility(View.GONE);
                } else {
                    editText.setVisibility(View.GONE);
                    timePicker.setVisibility(View.VISIBLE);
                }
            }
            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                // Do nothing.
            }
        });

        // When the text field gains focus, select all
        builder.setTitle(R.string.prepTimeBellsEditor_addBellDialog_title)
               .setView(content)
               .setCancelable(true)
               .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        Log.e(this.getClass().getSimpleName(), "Positive button not overridden");
                        dialog.cancel();
                    }
                })
               .setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.cancel();
                    }
                });

        return builder.create();
    }

    private void prepareAddBellDialog(final Dialog dialog) {

        final TimePicker timePicker  = (TimePicker) dialog.findViewById(R.id.addBellTimePicker);
        final EditText   editText    = (EditText)   dialog.findViewById(R.id.addBellEditText);

        // Defaults
        timePicker.setCurrentHour(5);
        timePicker.setCurrentMinute(0);
        editText.setText("50");

        AlertDialog alert = (AlertDialog) dialog;

        alert.setTitle(R.string.prepTimeBellsEditor_addBellDialog_title);
        // The text argument of setButton doesn't seem to work, so use setText on the button itself.
        alert.getButton(AlertDialog.BUTTON_POSITIVE).setText(R.string.prepTimeBellsEditor_addBellDialog_confirmButton);
        alert.setButton(AlertDialog.BUTTON_POSITIVE, getString(R.string.prepTimeBellsEditor_addBellDialog_confirmButton),
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int which) {
                        Bundle newBundle = createBellBundleFromAddOrEditDialog(dialog);
                        mPtbm.addFromBundle(newBundle);
                        refreshBellsList();
                    }
                }
        );

    }

    /**
     * @param dialog the {@link Dialog} to be prepared
     * @param args a {@link Bundle} representing the bell being edited, must <b>not</b> be <code>null</code>
     */
    private void prepareEditBellDialog(final Dialog dialog, final Bundle args) {

        final Spinner    typeSpinner = (Spinner)    dialog.findViewById(R.id.addBellTypeSpinner);
        final TimePicker timePicker  = (TimePicker) dialog.findViewById(R.id.addBellTimePicker);
        final EditText   editText    = (EditText)   dialog.findViewById(R.id.addBellEditText);

        // Defaults
        timePicker.setCurrentHour(5);
        timePicker.setCurrentMinute(0);
        editText.setText("50");

        // Populate the fields with the current values
        String type = args.getString(PrepTimeBellsManager.KEY_TYPE);
        if (type == PrepTimeBellsManager.VALUE_TYPE_START) {
            typeSpinner.setSelection(ADD_PREP_TIME_BELL_TYPE_START);
            long time = args.getLong(PrepTimeBellsManager.KEY_TIME);
            timePicker.setCurrentHour((int) (time / 60));
            timePicker.setCurrentMinute((int) (time % 60));
        } else if (type == PrepTimeBellsManager.VALUE_TYPE_FINISH) {
            typeSpinner.setSelection(ADD_PREP_TIME_BELL_TYPE_FINISH);
            long time = args.getLong(PrepTimeBellsManager.KEY_TIME);
            timePicker.setCurrentHour((int) (time / 60));
            timePicker.setCurrentMinute((int) (time % 60));
        } else if (type == PrepTimeBellsManager.VALUE_TYPE_PROPORTIONAL) {
            typeSpinner.setSelection(ADD_PREP_TIME_BELL_TYPE_PERCENTAGE);
            double proportion = args.getDouble(PrepTimeBellsManager.KEY_PROPORTION);
            editText.setText(String.valueOf(proportion * 100));
        }

        final int index = args.getInt(KEY_INDEX);

        AlertDialog alert = (AlertDialog) dialog;

        alert.setTitle(getString(R.string.prepTimeBellsEditor_editBellDialog_title, mPtbm.getBellDescription(index)));
        // The text argument of setButton doesn't seem to work, so use setText on the button itself.
        alert.getButton(AlertDialog.BUTTON_POSITIVE).setText(R.string.prepTimeBellsEditor_editBellDialog_confirmButton);
        alert.setButton(AlertDialog.BUTTON_POSITIVE, getString(R.string.prepTimeBellsEditor_editBellDialog_confirmButton),
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int which) {
                        Bundle newBundle = createBellBundleFromAddOrEditDialog(dialog);
                        mPtbm.replaceFromBundle(index, newBundle);
                        refreshBellsList();
                    }
                }
        );

    }

    private Bundle createBellBundleFromAddOrEditDialog(final Dialog dialog) {
        final Spinner    typeSpinner = (Spinner)    dialog.findViewById(R.id.addBellTypeSpinner);
        final TimePicker timePicker  = (TimePicker) dialog.findViewById(R.id.addBellTimePicker);
        final EditText   editText    = (EditText)   dialog.findViewById(R.id.addBellEditText);

        int typeSelected = typeSpinner.getSelectedItemPosition();
        Bundle bundle = new Bundle();
        switch (typeSelected) {
        case ADD_PREP_TIME_BELL_TYPE_START:
        case ADD_PREP_TIME_BELL_TYPE_FINISH:
            // We're using this in hours and minutes, not minutes and seconds
            int minutes = timePicker.getCurrentHour();
            int seconds = timePicker.getCurrentMinute();
            long time = minutes * 60 + seconds;
            if (typeSelected == ADD_PREP_TIME_BELL_TYPE_START)
                bundle.putString(PrepTimeBellsManager.KEY_TYPE, PrepTimeBellsManager.VALUE_TYPE_START);
            else
                bundle.putString(PrepTimeBellsManager.KEY_TYPE, PrepTimeBellsManager.VALUE_TYPE_FINISH);
            bundle.putLong(PrepTimeBellsManager.KEY_TIME, time);
            break;
        case ADD_PREP_TIME_BELL_TYPE_PERCENTAGE:
            Editable text = editText.getText();
            bundle.putString(PrepTimeBellsManager.KEY_TYPE, PrepTimeBellsManager.VALUE_TYPE_PROPORTIONAL);
            Double percentage = Double.parseDouble(text.toString());
            double value = percentage / 100;
            bundle.putDouble(PrepTimeBellsManager.KEY_PROPORTION, value);
            break;
        }

        return bundle;
    }

    private Dialog getClearBellsDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);

        builder.setTitle(R.string.prepTimeBellsEditor_clearAllDialog_title)
               .setMessage("")
               .setCancelable(true)
               .setPositiveButton(R.string.prepTimeBellsEditor_clearAllDialog_confirmButton, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        boolean spareFinish = mPtbm.hasBellsOtherThanFinish();
                        mPtbm.deleteAllBells(spareFinish);
                        refreshBellsList();
                    }
                })
                .setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.cancel();
                    }
                });

        return builder.create();
    }

    private void prepareClearBellsDialog(final Dialog dialog) {
        int messageResId = (mPtbm.hasFinishBell() && mPtbm.hasBellsOtherThanFinish()) ? R.string.prepTimeBellsEditor_clearAllDialog_message_withFinishBell : R.string.prepTimeBellsEditor_clearAllDialog_message_noFinishBell;
        AlertDialog alert = (AlertDialog) dialog;
        alert.setMessage(getString(messageResId));
    }

    private void refreshBellsList() {
        ArrayList<String> descriptions = mPtbm.getBellDescriptions();

        // Convert descriptions to sentence case
        ListIterator<String> descriptionsIterator = descriptions.listIterator();
        while (descriptionsIterator.hasNext()) {
            String description = descriptionsIterator.next();
            descriptionsIterator.set(description.substring(0, 1).toUpperCase() + description.substring(1));
        }

        ListView view = (ListView) findViewById(R.id.prepTimeBellsEditor_bellsList);
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, descriptions);
        view.setAdapter(adapter);

        // Disable the "clear" button if there is nothing to clear
        ((Button) findViewById(R.id.prepTimeBellsEditor_clearAllButton)).setEnabled(mPtbm.hasBells());
    }

}
