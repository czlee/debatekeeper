/*
 * Copyright (C) 2012-2021 Chuan-Zheng Lee
 *
 * This file is part of the Debatekeeper app, which is licensed under the GNU
 * General Public Licence version 3 (GPLv3).  You can redistribute and/or modify
 * it under the terms of the GPLv3, and you must not use this file except in
 * compliance with the GPLv3.
 *
 * This app is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR
 * A PARTICULAR PURPOSE.  See the GNU General Public Licence for more details.
 *
 * You should have received a copy of the GNU General Public Licence along with
 * this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package net.czlee.debatekeeper;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.ClipData;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ListView;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.FileProvider;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.Fragment;
import androidx.navigation.NavDirections;
import androidx.navigation.fragment.NavHostFragment;
import androidx.preference.PreferenceManager;

import com.google.android.material.snackbar.BaseTransientBottomBar;
import com.google.android.material.snackbar.Snackbar;

import net.czlee.debatekeeper.databinding.FragmentFormatChooserBinding;
import net.czlee.debatekeeper.databinding.ViewFormatFullBinding;
import net.czlee.debatekeeper.databinding.ViewFormatShortBinding;
import net.czlee.debatekeeper.debateformat.DebateFormatFieldExtractor;
import net.czlee.debatekeeper.debateformat.DebateFormatInfo;
import net.czlee.debatekeeper.debateformat.DebateFormatInfoForSchema1;
import net.czlee.debatekeeper.debateformat.DebateFormatInfoForSchema2;
import net.czlee.debatekeeper.debateformat.XmlUtilities;
import net.czlee.debatekeeper.debateformat.XmlUtilities.IllegalSchemaVersionException;

import org.xml.sax.SAXException;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;

/**
 * This Activity displays a list of formats for the user to choose from. It
 * returns a file name to the calling activity.
 *
 * @author Chuan-Zheng Lee
 * @since  2012-06-17
 */
public class FormatChooserFragment extends Fragment {

    private static final String TAG = "FormatChooserActivity";
    public static final String FILES_AUTHORITY = "net.czlee.debatekeeper.fileprovider";

    private FragmentFormatChooserBinding mViewBinding;
    private FormatXmlFilesManager mFilesManager;
    private ListView mStylesListView;
    private ActivityResultLauncher<String[]> mImportFileLauncher;

    private DebateFormatEntryArrayAdapter mStylesArrayAdapter;
    private final ArrayList<DebateFormatListEntry> mStylesList = new ArrayList<>();

    private static final String DIALOG_ARGUMENT_FILE_NAME = "filename";
    private static final String DIALOG_ARGUMENT_IMPORT_URI = "import-uri";
    private static final String DIALOG_ARGUMENT_STYLE_NAME = "style-name";
    private static final String DIALOG_TAG_MORE_DETAILS = "details/";
    private static final String DIALOG_TAG_CONFIRM_DELETION = "delete/";
    private static final String DIALOG_TAG_CONFIRM_OVERWRITE = "overwrite/";

    public static final String BUNDLE_KEY_RESULT         = "res";
    public static final String BUNDLE_KEY_XML_FILE_NAME  = "xmlfn";
    public static final String REQUEST_KEY_CHOOSE_FORMAT = "choose-format";

    private static final String PREFERENCE_DOWNLOAD_BANNER_DISMISSED = "dlb-dismiss";

    public static final int RESULT_SUCCESS      = 0;
    public static final int RESULT_UNCHANGED    = 2;
    public static final int RESULT_NO_SELECTION = 3;

    public static final String CURRENT_SCHEMA_VERSION = "2.2";

    //******************************************************************************************
    // Public classes
    //******************************************************************************************

    /**
     * Passive data class storing a filename and a style name.
     */
    public static class DebateFormatListEntry {

        private final String filename;
        private final String styleName;

        DebateFormatListEntry(String filename, String styleName) {
            this.filename = filename;
            this.styleName = styleName;
        }

        String getFilename() {
            return filename;
        }

        String getStyleName() {
            return styleName;
        }

        @NonNull
        @Override
        public String toString() {
            return styleName;
        }

    }

    /**
     * Interface to {@link DebateFormatEntryArrayAdapter}. Provides a method for
     * the <code>DebateFormatEntryArrayAdapter</code> to request the selected
     * position and whether "more details" mode is on.
     *
     * @author Chuan-Zheng Lee
     *
     */
    public class FormatChooserFragmentBinder {
        DetailsButtonOnClickListener getDetailsButtonOnClickListener(String filename) {
            return new DetailsButtonOnClickListener(filename);
        }

        /**
         * @return the position of the currently checked item.
         */
        int getSelectedPosition() {
            return mStylesListView.getCheckedItemPosition();
        }

        void populateBasicInfo(ViewFormatShortBinding vb, String filename) throws IOException, SAXException {
            DebateFormatInfo dfi = getDebateFormatInfo(filename);
            vb.viewFormatRegionValue.setText(concatenate(dfi.getRegions()));
            vb.viewFormatLevelValue.setText(concatenate(dfi.getLevels()));
            vb.viewFormatUsedAtValue.setText(concatenate(dfi.getUsedAts()));
            vb.viewFormatDescValue.setText(dfi.getDescription());
        }
    }

    //******************************************************************************************
    // Private classes
    //******************************************************************************************

    public static class ConfirmDeleteDialogFragment extends DialogFragment {

        static ConfirmDeleteDialogFragment newInstance(String filename) {
            ConfirmDeleteDialogFragment fragment = new ConfirmDeleteDialogFragment();
            Bundle args = new Bundle();
            args.putString(DIALOG_ARGUMENT_FILE_NAME, filename);
            fragment.setArguments(args);
            return fragment;
        }

        @NonNull
        @Override
        public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
            assert getArguments() != null;
            String filename = getArguments().getString(DIALOG_ARGUMENT_FILE_NAME);

            Activity activity = requireActivity();
            FormatChooserFragment parent = (FormatChooserFragment) getParentFragment();
            assert parent != null;
            AlertDialog.Builder builder = new AlertDialog.Builder(activity);

            builder.setMessage(getString(R.string.formatChooser_dialog_confirmDelete_message, filename))
                    .setPositiveButton(R.string.formatChooser_dialog_confirmDelete_yes,
                            (dialog, which) -> parent.deleteFile(filename))
                    .setNegativeButton(R.string.formatChooser_dialog_confirmDelete_no, null);

            return builder.create();
        }
    }

    public static class ConfirmOverwriteDialogFragment extends DialogFragment {

        static ConfirmOverwriteDialogFragment newInstance(Uri uri, String filename, String existingStyleName) {
            ConfirmOverwriteDialogFragment fragment = new ConfirmOverwriteDialogFragment();
            Bundle args = new Bundle();
            args.putParcelable(DIALOG_ARGUMENT_IMPORT_URI, uri);
            args.putString(DIALOG_ARGUMENT_FILE_NAME, filename);
            args.putString(DIALOG_ARGUMENT_STYLE_NAME, existingStyleName);
            fragment.setArguments(args);
            return fragment;
        }

        @NonNull
        @Override
        public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
            FormatChooserFragment parent = (FormatChooserFragment) getParentFragment();
            Bundle args = getArguments();
            Activity activity = requireActivity();

            assert parent != null;
            assert args != null;

            Uri uri = args.getParcelable(DIALOG_ARGUMENT_IMPORT_URI);
            String filename = args.getString(DIALOG_ARGUMENT_FILE_NAME);
            String styleName = args.getString(DIALOG_ARGUMENT_STYLE_NAME);

            AlertDialog.Builder builder = new AlertDialog.Builder(activity);

            builder.setMessage(getString(R.string.formatChooser_dialog_confirmOverwrite_message, filename, styleName))
                    .setPositiveButton(R.string.formatChooser_dialog_confirmOverwrite_yes,
                            (dialog, which) -> parent.importIncomingFile(uri, filename))
                    .setNegativeButton(R.string.formatChooser_dialog_confirmOverwrite_no, null);

            return builder.create();
        }
    }

    public static class MoreDetailsDialogFragment extends DialogFragment {

        static MoreDetailsDialogFragment newInstance(String filename) {
            MoreDetailsDialogFragment fragment = new MoreDetailsDialogFragment();
            Bundle args = new Bundle();
            args.putString(DIALOG_ARGUMENT_FILE_NAME, filename);
            fragment.setArguments(args);
            return fragment;
        }

        @NonNull
        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            assert getArguments() != null;
            String filename = getArguments().getString(DIALOG_ARGUMENT_FILE_NAME);

            Activity activity = requireActivity();
            FormatChooserFragment parent = (FormatChooserFragment) getParentFragment();
            assert parent != null;
            AlertDialog.Builder builder = new AlertDialog.Builder(activity);

            ViewFormatFullBinding binding = ViewFormatFullBinding.inflate(LayoutInflater.from(activity));

            DebateFormatInfo dfi = null;
            try {
                dfi = parent.getDebateFormatInfo(filename);
            } catch (IOException|SAXException e) {
                String message = (e instanceof FileNotFoundException)
                        ? getString(R.string.viewFormat_error_fileNotFound, e.getLocalizedMessage())
                        : e.getLocalizedMessage();

                populateFileInfo(binding, filename, null);
                binding.viewFormatDetailsGroup.setVisibility(View.GONE);
                binding.viewFormatLanguagesGroup.setVisibility(View.GONE);
                binding.viewFormatErrorLabel.setVisibility(View.VISIBLE);
                binding.viewFormatErrorValue.setVisibility(View.VISIBLE);
                binding.viewFormatErrorValue.setText(message);
            }

            if (dfi != null) {
                String schemaVersion = dfi.getSchemaVersion();
                populateFileInfo(binding, filename, schemaVersion);
                populateBasicInfo(binding, dfi);
                populatePrepTimeInfo(binding, dfi);
                List<String[]> speechDescriptions = dfi.getSpeechFormatDescriptions();
                populateTwoColumnTable(binding.viewFormatTableSpeechTypes, R.layout.speech_type_row, dfi.getSpeechFormatDescriptions());
                populateTwoColumnTable(binding.viewFormatTableSpeeches, R.layout.speech_row, dfi.getSpeeches(speechDescriptions));
                binding.viewFormatTitle.setText(dfi.getName());
            }
            builder.setCancelable(true);

            // Configure the buttons
            binding.viewFormatShareButton.setOnClickListener((v) -> parent.shareDebateFormatFile(filename));
            binding.viewFormatDeleteButton.setOnClickListener((v) -> {
                DialogFragment fragment = ConfirmDeleteDialogFragment.newInstance(filename);
                fragment.show(getParentFragmentManager(), DIALOG_TAG_CONFIRM_DELETION + filename);
            });

            AlertDialog dialog = builder.create();
            dialog.setView(binding.getRoot(), 0, 10, 10, 15);
            return dialog;

        }

        /**
         * Populates a View with information about a given file
         * @param vb the {@link ViewFormatFullBinding} to populate
         * @param filename the file name
         */
        private void populateFileInfo(ViewFormatFullBinding vb, String filename, String schemaVersion) {

            FormatChooserFragment parent = (FormatChooserFragment) getParentFragment();
            assert parent != null;

            // Display its schema version if it's not the current version
            if (schemaVersion != null) {
                int comparison = 0;
                String schemaVersionTextValue = null;
                try {
                    comparison = XmlUtilities.compareSchemaVersions(schemaVersion, CURRENT_SCHEMA_VERSION);
                } catch (IllegalSchemaVersionException e) {
                    schemaVersionTextValue = getString(R.string.viewFormat_invalidSchemaVersion, schemaVersion);
                }
                if (schemaVersionTextValue == null) {
                    if (comparison > 0)
                        schemaVersionTextValue = getString(R.string.viewFormat_futureSchemaVersion, schemaVersion);
                    else if (comparison < 0)
                        schemaVersionTextValue = getString(R.string.viewFormat_outdatedSchemaVersion, schemaVersion);
                }
                if (schemaVersionTextValue != null) {
                    TextView schemaVersionText = vb.viewFormatSchemaVersionValue;
                    schemaVersionText.setText(schemaVersionTextValue);
                    schemaVersionText.setVisibility(View.VISIBLE);
                }
            }

            vb.viewFormatFileNameValue.setText(filename);
        }

        private void populateBasicInfo(ViewFormatFullBinding vb, DebateFormatInfo dfi) {
            vb.viewFormatRegionValue.setText(concatenate(dfi.getRegions()));
            vb.viewFormatLevelValue.setText(concatenate(dfi.getLevels()));
            vb.viewFormatUsedAtValue.setText(concatenate(dfi.getUsedAts()));
            vb.viewFormatDescValue.setText(dfi.getDescription());

            List<String> languages = dfi.getDisplayLanguages();
            if (languages.isEmpty()) {
                vb.viewFormatLanguagesGroup.setVisibility(View.GONE);
            } else {
                vb.viewFormatLanguagesGroup.setVisibility(View.VISIBLE);
                vb.viewFormatLanguagesValue.setText(concatenate(languages));
            }
        }

        /**
         * Populates a table from an ArrayList of String arrays.
         * @param table A <code>TableLayout</code>
         * @param rowResId A resource ID pointing to a <code>TableRow</code> <b>layout file</b>.
         * (Not the <code>TableRow</code> itself.)
         * TableRow must have at least two TextView elements, which must have IDs "text1" and "text2".
         * @param list the list of String arrays.  Each array must have two elements.
         */
        private void populateTwoColumnTable(TableLayout table, int rowResId, List<String[]> list) {
            for (String[] rowText : list) {
                TableRow row = (TableRow) View.inflate(getActivity(), rowResId, null);
                ((TextView) row.findViewById(R.id.text1)).setText(rowText[0].concat(" "));
                ((TextView) row.findViewById(R.id.text2)).setText(rowText[1].concat(" "));
                table.addView(row);
            }

        }

        private void populatePrepTimeInfo(ViewFormatFullBinding vb, DebateFormatInfo dfi) {
            String prepTimeDescription = dfi.getPrepTimeDescription();

            // If there is prep time, populate the view.
            if (prepTimeDescription != null)
                vb.viewFormatPrepTimeValue.setText(prepTimeDescription);

            // Otherwise, hide the whole row.
            else {
                vb.viewFormatPrepTimeLabel.setVisibility(View.GONE);
                vb.viewFormatPrepTimeValue.setVisibility(View.GONE);
            }
        }
    }

    // ******************************************************************************************
    // Private classes
    // ******************************************************************************************

    private class DetailsButtonOnClickListener implements OnClickListener {

        private final String filename;

        DetailsButtonOnClickListener(String filename) {
            this.filename = filename;
        }

        @Override
        public void onClick(View v) {
            DialogFragment fragment = MoreDetailsDialogFragment.newInstance(filename);
            fragment.show(getChildFragmentManager(), DIALOG_TAG_MORE_DETAILS + filename);
        }

    }

    private class FormatChooserMenuItemClickListener implements Toolbar.OnMenuItemClickListener {
        @Override
        public boolean onMenuItemClick(MenuItem item) {
            final int itemId = item.getItemId();
            if (itemId == R.id.formatChooser_actionBar_ok) {
                confirmSelectionAndReturn();
                return true;
            } else if (itemId == R.id.formatChooser_actionBar_searchOnline) {
                @NonNull NavDirections action = FormatChooserFragmentDirections.actionGoToDownloads();
                NavHostFragment.findNavController(FormatChooserFragment.this).navigate(action);
                return true;
            } else if (itemId == R.id.formatChooser_actionBar_importFile) {
                mImportFileLauncher.launch(new String[]{"text/xml", "application/xml"});
                return true;
            } else if (itemId == R.id.formatChooser_actionBar_learnMore) {
                Uri uri = Uri.parse(getString(R.string.formats_learnMoreUrl));
                Intent intent = new Intent(Intent.ACTION_VIEW, uri);
                startActivity(intent);
                return true;
            } return false;
        }
    }

    /**
     * A comparator for DebateFormatListEntries, which sorts the debate formats alphabetically
     * by style name.
     */
    private static class StyleEntryComparatorByStyleName implements
            Comparator<DebateFormatListEntry> {

        @Override
        public int compare(DebateFormatListEntry lhs, DebateFormatListEntry rhs) {
            return lhs.getStyleName().compareToIgnoreCase(rhs.getStyleName());
        }

    }

    private class StylesListViewOnItemClickListener implements OnItemClickListener {
        @Override
        public void onItemClick(AdapterView<?> parent, View view, int position,
                long id) {
            mStylesArrayAdapter.notifyDataSetChanged();
        }
    }

    //******************************************************************************************
    // Protected methods
    //******************************************************************************************


    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mImportFileLauncher = registerForActivityResult(new ActivityResultContracts.OpenDocument(),
                this::importIncomingFilePrompt);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        mViewBinding = FragmentFormatChooserBinding.inflate(inflater, container, false);
        return mViewBinding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {

        super.onViewCreated(view, savedInstanceState);

        mViewBinding.toolbarFormatChooser.setOnMenuItemClickListener(new FormatChooserMenuItemClickListener());
        mViewBinding.toolbarFormatChooser.setNavigationOnClickListener(
                (v) -> NavHostFragment.findNavController(this).navigateUp());

        Context context = requireContext();
        mFilesManager = new FormatXmlFilesManager(context);
        mStylesArrayAdapter = new DebateFormatEntryArrayAdapter(context, mStylesList,
                new FormatChooserFragmentBinder());

        // Configure the ListView
        mStylesListView = mViewBinding.formatChooserStylesList;
        mStylesListView.setAdapter(mStylesArrayAdapter);
        mStylesListView.setOnItemClickListener(new StylesListViewOnItemClickListener());

        // Populate the styles list
        populateStylesList();

        // Show the download banner if it hasn't been dismissed
        showDownloadHelpBanner(context);

        // Select and scroll to the incoming selection (if existent)
        String incomingFilename = FormatChooserFragmentArgs.fromBundle(getArguments()).getXmlFileName();
        setSelectionAndScroll(incomingFilename);
    }

    //******************************************************************************************
    // Private methods
    //******************************************************************************************

    /**
     * Confirms and handles the selection appropriately, and ends the Activity.
     */
    private void confirmSelectionAndReturn() {
        String selectedFilename = getSelectedFilename();
        String incomingFilename = FormatChooserFragmentArgs.fromBundle(getArguments()).getXmlFileName();

        Bundle result = new Bundle();

        if (selectedFilename != null && selectedFilename.equals(incomingFilename)) {
            result.putInt(BUNDLE_KEY_RESULT, RESULT_UNCHANGED);
            result.putString(BUNDLE_KEY_XML_FILE_NAME, null);
            Log.v(TAG, "Returning no file, selection wasn't changed");

        } else if (selectedFilename == null) {
            result.putInt(BUNDLE_KEY_RESULT, RESULT_NO_SELECTION);
            result.putString(BUNDLE_KEY_XML_FILE_NAME, null);
            Log.e(TAG, "Returning no file, no selection found");

        } else {
            result.putInt(BUNDLE_KEY_RESULT, RESULT_SUCCESS);
            result.putString(BUNDLE_KEY_XML_FILE_NAME, selectedFilename);
            Log.v(TAG, "File name is " + selectedFilename);
        }

        getParentFragmentManager().setFragmentResult(REQUEST_KEY_CHOOSE_FORMAT, result);
        NavHostFragment.findNavController(this).navigateUp();
    }

    /**
     * Given a filename, returns the index in the styles list where the entry is.
     * @param filename the file name of the style
     * @return integer between 0 and <code>mStylesList.length - 1</code>, or
     * {@link ListView#INVALID_POSITION} if the item could not be found.
     */
    private int convertFilenameToIndex(String filename) {
        if (filename != null) {
            int i = 0;
            for (DebateFormatListEntry entry : mStylesList) {
                if (filename.equals(entry.getFilename())) return i;
                i++;
            }
        }
        return ListView.INVALID_POSITION;
    }

    /**
     * Given an index in the styles list, returns the filename.
     * @param index the index in the styles list
     * @return filename, or null if the index was invalid.
     */
    private String convertIndexToFilename(int index) {
        if (index < 0 || index >= mStylesList.size())
            return null;
        return mStylesList.get(index).getFilename();
    }

    /**
     * Carry out the required work to delete the given file. Called after the user has confirmed
     * the deletion in {@link ConfirmDeleteDialogFragment}.
     * @param filename file to delete
     */
    private void deleteFile(String filename) {
        // Dismiss the details fragment (whether or not this works, we'll need to show the Snackbar)
        Fragment detailsFragment = getChildFragmentManager().findFragmentByTag(DIALOG_TAG_MORE_DETAILS + filename);
        if (detailsFragment instanceof DialogFragment)
            ((DialogFragment) detailsFragment).dismiss();
        else
            Log.e(TAG, "Couldn't find the details fragment");

        boolean success = mFilesManager.delete(filename);

        if (success) {
            // Show a confirmation message
            showSnackbar(R.string.formatChooser_dialog_deleted_success, filename);

            // Remove the entry from the styles list
            int index = convertFilenameToIndex(filename);
            if (index >= 0) {
                mStylesList.remove(index);
                mStylesArrayAdapter.notifyDataSetChanged();
            }
        }
        else showSnackbar(R.string.formatChooser_dialog_deleted_failure, filename);
    }


    /**
     * Parses an XML file to get the {@link DebateFormatInfo} object
     * @param filename the filename for the debate format XML file
     * @return a <code>DebateFormatInfo</code> object, or <code>null</code>
     * @throws IOException if there was an IO problem with the XML file
     * @throws SAXException if thrown by the XML parser
     */
    private DebateFormatInfo getDebateFormatInfo(String filename) throws IOException, SAXException {
        InputStream is;
        is = mFilesManager.open(filename);

        // Assume it's a 2.0 schema first.
        DebateFormatInfoForSchema2 dfi2 = new DebateFormatInfoForSchema2(requireContext(), is);

        // If it's not 2.0, check to see if it is 1.0 or 1.1
        if (!dfi2.isSchemaSupported()) {
            is.close();
            is = mFilesManager.open(filename); // open again to try schema 1.0
            DebateFormatInfo dfi1 = new DebateFormatInfoForSchema1(requireContext(), is);
            if (dfi1.isSchemaSupported()) return dfi1;
        }

        // If it isn't, keep pretending it was 2.0.
        return dfi2;

    }

    /**
     * Returns the currently selected file name.
     *
     * @return The currently selected file name, or <code>null</code> if nothing is selected.
     */
    @Nullable
    private String getSelectedFilename() {
        int selectedPosition = mStylesListView.getCheckedItemPosition();
        return convertIndexToFilename(selectedPosition);
    }

    /**
     * Checks if the incoming file would overwrite an existing one. If so, prompt the user with
     * a dialog; if not, just import it.
     * @param uri a {@link Uri} to the incoming content
     */
    private void importIncomingFilePrompt(@Nullable Uri uri) {
        if (uri == null) {
            showSnackbar(R.string.formatChooser_import_error_noFileChosen);
            return;
        }

        Context context = requireContext();
        String filename = FormatXmlFilesManager.getFilenameFromUri(context.getContentResolver(), uri);

        Log.i(TAG, "saving to file: " + filename);
        if (filename == null) {
            try {
                filename = mFilesManager.getFreeFileName();
            } catch (FileNotFoundException e) {
                e.printStackTrace();
                showSnackbar(R.string.formatChooser_import_error_noFilename);
                return;
            } catch (IOException e) {
                e.printStackTrace();
                showSnackbar(R.string.formatChooser_ioError);
                return;
            }
        }
        if (!filename.endsWith(".xml"))
            filename = filename + ".xml";

        String existingStyleName;

        if (mFilesManager.exists(filename)) {
            DebateFormatFieldExtractor nameExtractor = new DebateFormatFieldExtractor(context, R.string.xml2elemName_name);
            try {
                InputStream in = mFilesManager.open(filename);
                existingStyleName = nameExtractor.getFieldValue(in);
            } catch (IOException | SAXException e) {
                Log.e(TAG, "Couldn't open existing file, even though one exists");
                e.printStackTrace();
                existingStyleName = "???";
            }
            DialogFragment fragment = ConfirmOverwriteDialogFragment.newInstance(uri, filename, existingStyleName);
            fragment.show(getChildFragmentManager(), DIALOG_TAG_CONFIRM_OVERWRITE + filename);

        } else {
            importIncomingFile(uri, filename);
        }
    }

    /**
     * Imports the selected file, refreshes the list and selects the new file.
     * @param uri a {@link Uri} to import
     */
    private void importIncomingFile(@NonNull Uri uri, @NonNull String filename) {
        ContentResolver contentResolver = requireContext().getContentResolver();
        InputStream in;
        try {
            in = contentResolver.openInputStream(uri);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
            showSnackbar(R.string.formatChooser_import_error_opening);
            return;
        }

        if (in == null) {
            showSnackbar(R.string.formatChooser_import_error_opening);
            return;
        }

        try {
            mFilesManager.copy(in, filename);
        } catch (IOException e) {
            e.printStackTrace();
            showSnackbar(R.string.formatChooser_import_error_reading);
            return;
        }

        showSnackbar(R.string.formatChooser_import_success, filename);
        populateStylesList();
        setSelectionAndScroll(filename);
    }

    /**
     * Populates the master styles list, <code>mStylesList</code>.  Should be called when this
     * Activity is created, or whenever we want to refresh the styles list. If there is an error so
     * serious that it can't even get the list, we show a dialog to that effect, and leave the list
     * empty.
     */
    private void populateStylesList() {
        String[] fileList;
        DebateFormatFieldExtractor nameExtractor = new DebateFormatFieldExtractor(requireContext(), R.string.xml2elemName_name);

        try {
            fileList = mFilesManager.list();
        } catch (IOException e) {
            Log.e(TAG, "IO error loading formats list!");
            e.printStackTrace();
            mViewBinding.formatChooserError.setVisibility(View.VISIBLE);
            mViewBinding.formatChooserStylesList.setVisibility(View.GONE);
            return;
        }

        mStylesList.clear();

        for (String filename : fileList) {
            if (!filename.endsWith(".xml")) continue;

            InputStream is;

            try {
                is = mFilesManager.open(filename);
            } catch (IOException e) {
                Log.e(TAG, "populateStylesList: Couldn't find file " + filename);
                continue;
            }

            String styleName;
            try {
                styleName = nameExtractor.getFieldValue(is);
            } catch (SAXException|IOException e) {
                Log.e(TAG, "populateStylesList: Couldn't get name from " + filename);
                continue;
            }

            try {
                is.close();
            } catch (IOException e) {
                Log.e(TAG, "populateStylesList: error closing file " + filename);
            }

            if (styleName != null)
                mStylesList.add(new DebateFormatListEntry(filename, styleName));

        }

        // Sort alphabetically by style name and tell observers
        mStylesArrayAdapter.sort(new StyleEntryComparatorByStyleName());
        mStylesArrayAdapter.notifyDataSetChanged();
    }

    /**
     * Sets the selection to the given file name and scrolls so that the selection is visible.
     * If the file name isn't in the list, it deselects everything.
     * @param filename name of file to select
     */
    private void setSelectionAndScroll(String filename) {
        int index = convertFilenameToIndex(filename);
        mStylesListView.setItemChecked(index, true);
        if (index != ListView.INVALID_POSITION)
            mStylesListView.smoothScrollToPosition(index);
    }

    /**
     * Shares the current selection. If there is no current selection, it shows a {@link Snackbar}
     * with an error message.
     */
    private void shareDebateFormatFile(String filename) {

        // Check for error conditions
        if (filename == null) {
            showSnackbar(R.string.formatChooser_share_error_noFileSelected);
            return;
        }

        if (!mFilesManager.exists(filename)) {
            Log.e(TAG, "shareSelection: file does not exist");
            showSnackbar(R.string.formatChooser_share_error_notFound, filename);
            return;
        }

        File file = mFilesManager.getFileFromExternalStorage(filename);
        if (file == null) {
            Log.e(TAG, String.format("shareSelection: getFileFromExternalStorage returned null on file %s", filename));
            showSnackbar(R.string.formatChooser_share_error_generic);
            return;
        }

        Uri fileUri;
        try {
            fileUri = FileProvider.getUriForFile(requireContext(), FILES_AUTHORITY, file);
        } catch (IllegalArgumentException e) {
            Log.e(TAG, "shareSelection: tried to get file from outside allowable paths");
            Log.e(TAG, "path was: " + file.getAbsolutePath());
            showSnackbar(R.string.formatChooser_share_error_generic);
            return;
        }

        Intent shareIntent = new Intent();
        shareIntent.setAction(Intent.ACTION_SEND);
        shareIntent.setType("text/xml");
        shareIntent.putExtra(Intent.EXTRA_STREAM, fileUri);

        // I'm pretty sure this part doesn't do anything useful. It's part of the spec that
        // setClipData is like putExtra with the ability to grant URI permissions using flags, but
        // apps like Gmail don't seem to honour it, and there's almost no third-party posts on the
        // topic. It also doesn't seem to be harmful, though.
        shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        ClipData clipData = new ClipData(filename, new String[]{"text/xml"}, new ClipData.Item(fileUri));
        shareIntent.setClipData(clipData);

        Intent chooserIntent = Intent.createChooser(shareIntent, getString(R.string.formatChooser_share_chooserTitle));
        startActivity(chooserIntent);
    }

    private void showDownloadHelpBanner(Context context) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        boolean bannerDismissed = prefs.getBoolean(PREFERENCE_DOWNLOAD_BANNER_DISMISSED, false);
        if (!bannerDismissed) {
            mViewBinding.downloadBannerGroup.setVisibility(View.VISIBLE);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP)
                mViewBinding.toolbarFormatChooser.setElevation(0);
            mViewBinding.downloadBannerDismiss.setOnClickListener((v) -> {
                SharedPreferences.Editor editor = prefs.edit();
                editor.putBoolean(PREFERENCE_DOWNLOAD_BANNER_DISMISSED, true);
                editor.apply();
                mViewBinding.downloadBannerGroup.setVisibility(View.GONE);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    final float scale = context.getResources().getDisplayMetrics().density;
                    mViewBinding.toolbarFormatChooser.setElevation(4 * scale + 0.5f);
                }
            });
        }
    }

    private void showSnackbar(int stringResId, Object... formatArgs) {
        String string = getString(stringResId, formatArgs);
        View coordinator = mViewBinding.formatChooserCoordinator;
        Snackbar snackbar = Snackbar.make(coordinator, string, BaseTransientBottomBar.LENGTH_SHORT);
        View snackbarText = snackbar.getView();
        TextView textView = snackbarText.findViewById(com.google.android.material.R.id.snackbar_text);
        if (textView != null) textView.setMaxLines(5);
        snackbar.show();
    }

    /**
     * Concatenates a list of <code>String</code>s with line breaks delimiting.
     *
     * @param list An <code>ArrayList</code> of <code>String</code>s.
     * @return the result, a single <code>String</code>
     */
    private static String concatenate(List<String> list) {
        String str = "";
        Iterator<String> iterator = list.iterator();

        // Start with the first item (if it exists)
        if (iterator.hasNext()) str = iterator.next();

        // Add the second and further items, putting a line break in between.
        while (iterator.hasNext()) {
            str = str.concat("\n");
            str = str.concat(iterator.next());
        }
        return str;
    }

}
