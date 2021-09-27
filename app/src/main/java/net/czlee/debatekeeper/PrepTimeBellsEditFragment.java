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

import static android.content.Context.MODE_PRIVATE;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.Editable;
import android.util.Log;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.LayoutInflater;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.AdapterContextMenuInfo;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.TimePicker;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;

import net.czlee.debatekeeper.databinding.FragmentPrepTimeBellsEditBinding;

import java.util.ArrayList;
import java.util.ListIterator;
import java.util.Locale;

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
public class PrepTimeBellsEditFragment extends Fragment {

    private FragmentPrepTimeBellsEditBinding mViewBinding;

    private PrepTimeBellsManager mPtbm;

    private static final String DIALOG_TAG_ADD_BELL        = "add";
    private static final String DIALOG_TAG_CLEAR_ALL_BELLS = "clr";
    private static final String DIALOG_TAG_EDIT_BELL       = "edit";

    private static final String KEY_INDEX = "index";

    public static final int DEFAULT_MINUTES = 5;
    public static final int DEFAULT_SECONDS = 0;
    public static final String DEFAULT_PERCENTAGE_TEXT = "50";

    // These must match the strings in R.string.AddPrepTimeBellTypes (in prep_time_bells_edit.xml)
    private static final int ADD_PREP_TIME_BELL_TYPE_START      = 0;
    private static final int ADD_PREP_TIME_BELL_TYPE_FINISH     = 1;
    private static final int ADD_PREP_TIME_BELL_TYPE_PERCENTAGE = 2;

    //******************************************************************************************
    // Public classes
    //******************************************************************************************

    public static class DialogAddOrEditBellFragment extends DialogFragment {

        @NonNull
        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            return getAddOrEditBellDialog();
        }

        private Dialog getAddOrEditBellDialog() {
            final Activity activity = requireActivity();
            final PrepTimeBellsEditFragment parent = (PrepTimeBellsEditFragment) getParentFragment();

            AlertDialog.Builder builder = new AlertDialog.Builder(activity);

            View content = activity.getLayoutInflater().inflate(R.layout.add_prep_time_bell, null);

            // Take note of the form elements
            final Spinner    typeSpinner = content.findViewById(R.id.addPrepTimeBellDialog_typeSpinner);
            final TimePicker timePicker  = content.findViewById(R.id.addPrepTimeBellDialog_timePicker);
            final EditText   editText    = content.findViewById(R.id.addPrepTimeBellDialog_editText);

            // Format the form elements
            timePicker.setIs24HourView(true);
            ArrayAdapter<CharSequence> typesAdapter = ArrayAdapter.createFromResource(activity,
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

            String title, confirmButtonText;
            DialogInterface.OnClickListener confirmOnClickListener;

            // Prepare the dialog fields
            if (DIALOG_TAG_ADD_BELL.equals(getTag())) {

                prepareAddBellDialogView(content);

                title = getString(R.string.prepTimeBellsEditor_addBellDialog_title);
                confirmButtonText = getString(R.string.prepTimeBellsEditor_addBellDialog_confirmButton);
                confirmOnClickListener = parent.getAddBellDialogOnClickListener();

            } else if (DIALOG_TAG_EDIT_BELL.equals(getTag())) {

                prepareEditBellDialogView(content);
                int index = getArguments().getInt(KEY_INDEX);

                title = getString(R.string.prepTimeBellsEditor_editBellDialog_title,
                        parent.mPtbm.getBellDescription(index));
                confirmButtonText = getString(R.string.prepTimeBellsEditor_editBellDialog_confirmButton);
                confirmOnClickListener = parent.getEditBellDialogOnClickListener(index);

            } else {

                Log.e("DialogAddOrEditBellFrag", "Unrecognised tag: " + getTag());

                title = getString(R.string.prepTimeBellsEditor_addBellDialog_title);
                confirmButtonText = getString(R.string.prepTimeBellsEditor_addBellDialog_confirmButton);
                confirmOnClickListener = (dialog, which) -> dialog.cancel();
            }

            // When the text field gains focus, select all
            builder.setTitle(title)
                   .setView(content)
                   .setCancelable(true)
                   .setPositiveButton(confirmButtonText, confirmOnClickListener)
                   .setNegativeButton(android.R.string.cancel, (dialog, which) -> dialog.cancel());

            return builder.create();
        }

        private void prepareAddBellDialogView(View view) {
            final TimePicker timePicker  = view.findViewById(R.id.addPrepTimeBellDialog_timePicker);
            final EditText   editText    = view.findViewById(R.id.addPrepTimeBellDialog_editText);

            // Defaults
            timePicker.setCurrentHour(DEFAULT_MINUTES);
            timePicker.setCurrentMinute(DEFAULT_SECONDS);
            editText.setText(DEFAULT_PERCENTAGE_TEXT);

        }

        /**
         * @param view the {@link View} to be prepared
         */
        private void prepareEditBellDialogView(View view) {
            final Bundle args = getArguments();

            final Spinner    typeSpinner = view.findViewById(R.id.addPrepTimeBellDialog_typeSpinner);
            final TimePicker timePicker  = view.findViewById(R.id.addPrepTimeBellDialog_timePicker);
            final EditText   editText    = view.findViewById(R.id.addPrepTimeBellDialog_editText);

            // Defaults
            timePicker.setCurrentHour(DEFAULT_MINUTES);
            timePicker.setCurrentMinute(DEFAULT_SECONDS);
            editText.setText(DEFAULT_PERCENTAGE_TEXT);

            // Populate the fields with the current values
            String type = args.getString(PrepTimeBellsManager.KEY_TYPE);
            if (PrepTimeBellsManager.VALUE_TYPE_START.equals(type)) {
                typeSpinner.setSelection(ADD_PREP_TIME_BELL_TYPE_START);
                long time = args.getLong(PrepTimeBellsManager.KEY_TIME);
                timePicker.setCurrentHour((int) (time / 60));
                timePicker.setCurrentMinute((int) (time % 60));
            } else if (PrepTimeBellsManager.VALUE_TYPE_FINISH.equals(type)) {
                typeSpinner.setSelection(ADD_PREP_TIME_BELL_TYPE_FINISH);
                long time = args.getLong(PrepTimeBellsManager.KEY_TIME);
                timePicker.setCurrentHour((int) (time / 60));
                timePicker.setCurrentMinute((int) (time % 60));
            } else if (PrepTimeBellsManager.VALUE_TYPE_PROPORTIONAL.equals(type)) {
                typeSpinner.setSelection(ADD_PREP_TIME_BELL_TYPE_PERCENTAGE);
                double proportion = args.getDouble(PrepTimeBellsManager.KEY_PROPORTION);
                editText.setText(String.valueOf(proportion * 100));
            }

        }

    }

    public static class DialogClearBellsFragment extends DialogFragment {

        @NonNull
        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {

            final Activity activity = requireActivity();
            final PrepTimeBellsEditFragment parent = (PrepTimeBellsEditFragment) getParentFragment();

            AlertDialog.Builder builder = new AlertDialog.Builder(activity);

            builder.setTitle(R.string.prepTimeBellsEditor_clearAllDialog_title)
                   .setMessage("")
                   .setCancelable(true)
                   .setPositiveButton(R.string.prepTimeBellsEditor_clearAllDialog_confirmButton, (dialog, which) -> {
                       boolean spareFinish = parent.mPtbm.hasBellsOtherThanFinish();
                       parent.mPtbm.deleteAllBells(spareFinish);
                       parent.refreshBellsList();
                   })
                   .setNegativeButton(android.R.string.cancel, (dialog, which) -> dialog.cancel());

            AlertDialog dialog = builder.create();

            int messageResId = (parent.mPtbm.hasFinishBell() && parent.mPtbm.hasBellsOtherThanFinish()) ?
                    R.string.prepTimeBellsEditor_clearAllDialog_message_withFinishBell :
                        R.string.prepTimeBellsEditor_clearAllDialog_message_noFinishBell;
            dialog.setMessage(getString(messageResId));

            return dialog;
        }

    }

    //******************************************************************************************
    // Private classes
    //******************************************************************************************

    private class AddButtonOnClickListener implements OnClickListener {
        @Override
        public void onClick(View v) {
            // Generate the "add bell" dialog
            DialogFragment fragment = new DialogAddOrEditBellFragment();
            fragment.show(getChildFragmentManager(), DIALOG_TAG_ADD_BELL);
        }
    }

    private class ClearButtonOnClickListener implements OnClickListener {
        @Override
        public void onClick(View v) {
            // Generate the "are you sure?" dialog
            DialogFragment fragment = new DialogClearBellsFragment();
            fragment.show(getChildFragmentManager(), DIALOG_TAG_CLEAR_ALL_BELLS);
        }
    }

    //******************************************************************************************
    // Public methods
    //******************************************************************************************
    @Override
    public void onCreateContextMenu(@NonNull ContextMenu menu, @NonNull View v, ContextMenuInfo menuInfo) {
        super.onCreateContextMenu(menu, v, menuInfo);
        AdapterContextMenuInfo info = (AdapterContextMenuInfo) menuInfo;
        String title = getString(R.string.prepTimeBellsEditor_contextMenu_header,
                mPtbm.getBellDescription(info.position));
        menu.setHeaderTitle(title);
        MenuInflater inflater = requireActivity().getMenuInflater();
        inflater.inflate(R.menu.prep_time_bells_list_context, menu);
    }

    //******************************************************************************************
    // Public methods
    //******************************************************************************************


    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        mViewBinding = FragmentPrepTimeBellsEditBinding.inflate(inflater, container, false);
        return mViewBinding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        Context context = requireContext();

        // Open the preferences file and instantiate the PrepTimeBellsManager
        SharedPreferences prefs = context.getSharedPreferences(PrepTimeBellsManager.PREP_TIME_BELLS_PREFERENCES_NAME, MODE_PRIVATE);
        mPtbm = new PrepTimeBellsManager(context);
        mPtbm.loadFromPreferences(prefs);

        // Populate the list
        refreshBellsList();

        // Set the OnClickListeners
        mViewBinding.prepTimeBellsEditorAddBellButton.setOnClickListener(new AddButtonOnClickListener());
        mViewBinding.prepTimeBellsEditorClearAllButton.setOnClickListener(new ClearButtonOnClickListener());

        // Register a context menu for the bells list items
        registerForContextMenu(mViewBinding.prepTimeBellsEditorBellsList);

        // Register for the list to show a toast on non-long click
        mViewBinding.prepTimeBellsEditorBellsList.setOnItemClickListener(
                (parent, v, pos, id) -> Toast.makeText(context,
                        R.string.prepTimeBellsEditor_contextMenu_tip, Toast.LENGTH_SHORT).show());

        // Set the action bar
        mViewBinding.prepTimeBellsEditorToolbar.setNavigationIcon(R.drawable.ic_baseline_arrow_back_24);
        mViewBinding.prepTimeBellsEditorToolbar.setNavigationOnClickListener(
                (v) -> NavHostFragment.findNavController(this).navigateUp());
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        AdapterContextMenuInfo info = (AdapterContextMenuInfo) item.getMenuInfo();
        final int itemId = item.getItemId();
        if (itemId == R.id.prepTimeBellsEditor_contextMenu_edit) {
            Bundle args = mPtbm.getBellBundle(info.position);
            args.putInt(KEY_INDEX, info.position);
            DialogFragment fragment = new DialogAddOrEditBellFragment();
            fragment.setArguments(args);
            fragment.show(getChildFragmentManager(), DIALOG_TAG_EDIT_BELL);
            return true;
        } else if (itemId == R.id.prepTimeBellsEditor_contextMenu_delete) {
            mPtbm.deleteBell(info.position);
            refreshBellsList();
            return true;
        }
        return super.onContextItemSelected(item);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        SharedPreferences prefs = requireContext().getSharedPreferences(PrepTimeBellsManager.PREP_TIME_BELLS_PREFERENCES_NAME, MODE_PRIVATE);
        mPtbm.saveToPreferences(prefs);
    }

    //******************************************************************************************
    // Private methods
    //******************************************************************************************

    @SuppressLint("DefaultLocale")
    private void refreshBellsList() {
        ArrayList<String> descriptions = mPtbm.getBellDescriptions();

        // Convert descriptions to sentence case
        ListIterator<String> descriptionsIterator = descriptions.listIterator();
        while (descriptionsIterator.hasNext()) {
            String description = descriptionsIterator.next();
            descriptionsIterator.set(description.substring(0, 1).toUpperCase(Locale.getDefault()) + description.substring(1));
        }

        ListView view = mViewBinding.prepTimeBellsEditorBellsList;
        ArrayAdapter<String> adapter = new ArrayAdapter<>(requireContext(), android.R.layout.simple_list_item_1, descriptions);
        view.setAdapter(adapter);

        // Disable the "clear" button if there is nothing to clear
        mViewBinding.prepTimeBellsEditorClearAllButton.setEnabled(mPtbm.hasBells());
    }

    private static Bundle createBellBundleFromAddOrEditDialog(final Dialog dialog) {
        final Spinner    typeSpinner = dialog.findViewById(R.id.addPrepTimeBellDialog_typeSpinner);
        final TimePicker timePicker  = dialog.findViewById(R.id.addPrepTimeBellDialog_timePicker);
        final EditText   editText    = dialog.findViewById(R.id.addPrepTimeBellDialog_editText);

        int typeSelected = typeSpinner.getSelectedItemPosition();
        Bundle bundle = new Bundle();
        switch (typeSelected) {
        case ADD_PREP_TIME_BELL_TYPE_START:
        case ADD_PREP_TIME_BELL_TYPE_FINISH:
            // We're using this in hours and minutes, not minutes and seconds
            int minutes = timePicker.getCurrentHour();
            int seconds = timePicker.getCurrentMinute();
            long time = minutes * 60L + seconds;
            if (typeSelected == ADD_PREP_TIME_BELL_TYPE_START)
                bundle.putString(PrepTimeBellsManager.KEY_TYPE, PrepTimeBellsManager.VALUE_TYPE_START);
            else
                bundle.putString(PrepTimeBellsManager.KEY_TYPE, PrepTimeBellsManager.VALUE_TYPE_FINISH);
            bundle.putLong(PrepTimeBellsManager.KEY_TIME, time);
            break;
        case ADD_PREP_TIME_BELL_TYPE_PERCENTAGE:
            Editable text = editText.getText();
            bundle.putString(PrepTimeBellsManager.KEY_TYPE, PrepTimeBellsManager.VALUE_TYPE_PROPORTIONAL);
            double percentage = Double.parseDouble(text.toString());
            double value = percentage / 100;
            bundle.putDouble(PrepTimeBellsManager.KEY_PROPORTION, value);
            break;
        }

        return bundle;
    }

    private DialogInterface.OnClickListener getAddBellDialogOnClickListener() {
        return (dialogInterface, which) -> {
            Dialog dialog = (Dialog) dialogInterface;
            Bundle newBundle = createBellBundleFromAddOrEditDialog(dialog);
            mPtbm.addFromBundle(newBundle);
            refreshBellsList();
        };
    }

    private DialogInterface.OnClickListener getEditBellDialogOnClickListener(final int index) {
        return (dialogInterface, which) -> {
            Dialog dialog = (Dialog) dialogInterface;
            Bundle newBundle = createBellBundleFromAddOrEditDialog(dialog);
            mPtbm.replaceFromBundle(index, newBundle);
            refreshBellsList();
        };
    }

}
