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

import android.Manifest;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.ClipData;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.design.widget.Snackbar;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.DialogFragment;
import android.support.v4.content.ContextCompat;
import android.support.v4.content.FileProvider;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.text.method.LinkMovementMethod;
import android.util.Log;
import android.util.Xml;
import android.util.Xml.Encoding;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.CheckBox;
import android.widget.ListView;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;

import net.czlee.debatekeeper.debateformat.DebateFormatInfo;
import net.czlee.debatekeeper.debateformat.DebateFormatInfoExtractorForSchema1;
import net.czlee.debatekeeper.debateformat.DebateFormatInfoForSchema2;
import net.czlee.debatekeeper.debateformat.XmlUtilities;
import net.czlee.debatekeeper.debateformat.XmlUtilities.IllegalSchemaVersionException;

import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;

import static android.support.design.widget.Snackbar.LENGTH_LONG;
import static android.support.design.widget.Snackbar.LENGTH_SHORT;
import static android.util.Log.e;

/**
 * This Activity displays a list of formats for the user to choose from. It
 * returns a file name to the calling activity.
 *
 * @author Chuan-Zheng Lee
 * @since  2012-06-17
 */
/**
 * @author Chuan-Zheng Lee
 *
 */
public class FormatChooserActivity extends AppCompatActivity {

    private static final String TAG = "FormatChooserActivity";
    public static final String FILES_AUTHORITY = "net.czlee.debatekeeper.fileprovider";
    public static final String ACTION_CHOOSE_DEBATE_STYLE = "net.czlee.debatekeeper.CHOOSE_DEBATE_STYLE";

    private FormatXmlFilesManager mFilesManager;
    private ListView mStylesListView;

    private String   mCurrentStyleName = null;
    private boolean  mInitialLookForCustomFormats = false;

    private DebateFormatEntryArrayAdapter mStylesArrayAdapter;
    private final ArrayList<DebateFormatListEntry> mStylesList = new ArrayList<>();

    private String DEBATING_TIMER_URI;

    private static final int REQUEST_TO_READ_EXTERNAL_STORAGE = 17;
    private static final int REQUEST_TO_WRITE_EXTERNAL_STORAGE_FOR_IMPORT = 21;
    private static final String DIALOG_ARGUMENT_FILE_NAME = "fn";
    private static final String DIALOG_TAG_MORE_DETAILS = "md";
    private static final String DIALOG_TAG_LIST_IO_ERROR = "io";

    public  static final int RESULT_ERROR = RESULT_FIRST_USER;
    public  static final int RESULT_UNCHANGED = RESULT_FIRST_USER + 1;

    public static final String CURRENT_SCHEMA_VERSION = "2.1";

    public static final String EXTRA_XML_FILE_NAME = "xmlfn";

    //******************************************************************************************
    // Public classes
    //******************************************************************************************

    /**
     * Passive data class storing a filename and a style name.
     */
    public class DebateFormatListEntry {

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
    public class FormatChooserActivityBinder {
        DetailsButtonOnClickListener getDetailsButtonOnClickListener(String filename) {
            return new DetailsButtonOnClickListener(filename);
        }

        /**
         * @return the position of the currently checked item.
         */
        int getSelectedPosition() {
            return mStylesListView.getCheckedItemPosition();
        }

        void populateBasicInfo(View view, String filename) throws IOException, SAXException {
            FormatChooserActivity.this.populateBasicInfo(view, filename);
        }

    }

    /**
     * An {@link AlertDialog} alerting the user to a fatal problem retrieving the styles list,
     * which then exits this Activity upon dismissal.
     * @author Chuan-Zheng Lee
     *
     */
    public static class ListIOErrorDialogFragment extends DialogFragment {

        @NonNull
        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            final FormatChooserActivity activity = (FormatChooserActivity) getActivity();

            AlertDialog.Builder builder = new AlertDialog.Builder(activity);
            builder.setTitle(R.string.ioErrorDialog_title)
                   .setMessage(R.string.ioErrorDialog_message)
                   .setCancelable(false)
                   .setPositiveButton(R.string.ioErrorDialog_button,
                            new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    activity.finish();
                                }
                            });
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
            String filename = getArguments().getString(DIALOG_ARGUMENT_FILE_NAME);
            return getMoreDetailsDialog(filename);
        }

        /**
         * Returns an {@link AlertDialog} with an error message explaining why the "more details" Dialog
         * for a given debate format couldn't be populated.
         * @param filename the file name of the debate format XML file to which this Dialog should
         * relate
         * @param e the exception leading to this error
         * @return the {@link AlertDialog}
         */
        private AlertDialog getBlankDetailsDialog(String filename, Exception e) {
            FormatChooserActivity activity = (FormatChooserActivity) getActivity();
            AlertDialog.Builder builder = new AlertDialog.Builder(activity);
            builder.setTitle(R.string.blankDetailsDialog_title)
                   .setCancelable(true)
                   .setMessage(getString(R.string.blankDetailsDialog_text, filename, e.getMessage()))
                   .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            dialog.cancel();
                        }
                    });
            return builder.create();
        }

        /**
         * Returns an {@link AlertDialog} with information about a debate format, populated from the
         * debate format XML file.
         * @param filename the file name of the debate format XML file to which this Dialog should
         * relate
         * @return the {@link AlertDialog}
         */
        private AlertDialog getMoreDetailsDialog(String filename) {
            FormatChooserActivity activity = (FormatChooserActivity) getActivity();
            AlertDialog.Builder builder = new AlertDialog.Builder(activity);
            View view = View.inflate(activity, R.layout.view_format_full, null);

            DebateFormatInfo dfi;
            try {
                dfi = activity.getDebateFormatInfo(filename);
            } catch (IOException e) {
                return getBlankDetailsDialog(filename, e);
            } catch (SAXException e) {
                return getBlankDetailsDialog(filename, e);
            }

            String schemaVersion = null;
            if (dfi != null) schemaVersion = dfi.getSchemaVersion();

            populateFileInfo(view, filename, schemaVersion);

            if (dfi != null) {
                FormatChooserActivity.populateBasicInfo(view, dfi);
                populatePrepTimeInfo(view, dfi);
                populateTwoColumnTable(view, R.id.viewFormat_table_speechTypes, R.layout.speech_type_row,
                        dfi.getSpeechFormatDescriptions());
                populateTwoColumnTable(view, R.id.viewFormat_table_speeches, R.layout.speech_row,
                        dfi.getSpeeches());
                builder.setTitle(dfi.getName());
            } else {
                builder.setTitle(filename);
            }

            builder.setCancelable(true);

            AlertDialog dialog = builder.create();
            dialog.setView(view, 0, 10, 10, 15);
            return dialog;

        }

        /**
         * Populates a View with information about a given file
         * @param view the View to populate
         * @param filename the file name
         */
        private void populateFileInfo(View view, String filename, String schemaVersion) {

            FormatChooserActivity activity = (FormatChooserActivity) getActivity();

            // Display its location if it's not a built-in file
            if (activity.mFilesManager.getLocation(filename) == FormatXmlFilesManager.LOCATION_EXTERNAL_STORAGE) {
                TextView fileLocationText = (TextView) view.findViewById(R.id.viewFormat_fileLocationValue);
                fileLocationText.setText(getString(R.string.viewFormat_fileLocationValue_userDefined));
                fileLocationText.setVisibility(View.VISIBLE);
            }

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
                    TextView schemaVersionText = (TextView) view.findViewById(R.id.viewFormat_schemaVersionValue);
                    schemaVersionText.setText(schemaVersionTextValue);
                    schemaVersionText.setVisibility(View.VISIBLE);
                }
            }

            ((TextView) view.findViewById(R.id.viewFormat_fileNameValue)).setText(filename);
        }

        /**
         * Populates a table from an ArrayList of String arrays.
         * @param view the view in which to find the resources
         * @param tableResΙd A resource ID pointing to a <code>TableLayout</code>
         * @param rowResΙd A resource ID pointing to a <code>TableRow</code> <b>layout file</b>.
         * (Not the <code>TableRow</code> itself.)
         * TableRow must have at least two TextView elements, which must have IDs "text1" and "text2".
         * @param list the list of String arrays.  Each array must have two elements.
         */
        private void populateTwoColumnTable(View view, int tableResΙd, int rowResΙd, ArrayList<String[]> list) {
            TableLayout table = (TableLayout) view.findViewById(tableResΙd);

            for (String[] rowText : list) {
                TableRow row = (TableRow) View.inflate(getActivity(), rowResΙd, null);
                ((TextView) row.findViewById(R.id.text1)).setText(rowText[0].concat(" "));
                ((TextView) row.findViewById(R.id.text2)).setText(rowText[1].concat(" "));
                table.addView(row);
            }

        }

        private static void populatePrepTimeInfo(View view, DebateFormatInfo dfi) {
            String prepTimeDescription = dfi.getPrepTimeDescription();

            // If there is prep time, populate the view.
            if (prepTimeDescription != null)
                ((TextView) view.findViewById(R.id.viewFormat_prepTimeValue)).setText(
                        prepTimeDescription);

            // Otherwise, hide the whole row.
            else
                view.findViewById(R.id.viewFormat_prepTimeRow).setVisibility(View.GONE);
        }
    }

    // ******************************************************************************************
    // Private classes
    // ******************************************************************************************

    private class AllInformationFoundException extends SAXException {
        private static final long serialVersionUID = 3195935815375118010L;
    }

    private class DetailsButtonOnClickListener implements OnClickListener {

        private final String filename;

        DetailsButtonOnClickListener(String filename) {
            this.filename = filename;
        }

        @Override
        public void onClick(View v) {
            DialogFragment fragment = MoreDetailsDialogFragment.newInstance(filename);
            fragment.show(getSupportFragmentManager(), DIALOG_TAG_MORE_DETAILS);
        }

    }

    /**
     * This class just looks for the string inside &lt;debateformat name="...">
     * and saves it to <code>mCurrentStyleName</code>.
     */
    private class GetDebateFormatNameXmlContentHandler extends DefaultHandler {

        private StringBuilder mNameBuffer = null;

        @Override
        public void characters(char[] ch, int start, int length) throws SAXException {
            String str = new String(ch, start, length);
            if (mNameBuffer == null) return;
            mNameBuffer = mNameBuffer.append(str);
        }

        @Override
        public void endElement(String uri, String localName, String qName) throws SAXException {
            if (localName.equals(getString(R.string.xml2elemName_name))) {
                mCurrentStyleName = mNameBuffer.toString();
                throw new AllInformationFoundException();
                // We don't need to parse any more once we finish getting the style name
            }
        }

        @Override
        public void startDocument() throws SAXException {
            // initialise
            mCurrentStyleName = null;
        }

        @Override
        public void startElement(String uri, String localName, String qName,
                Attributes atts) throws SAXException {

            if (!uri.equals(DEBATING_TIMER_URI))
                return;

            // To keep things light, we just use the attribute of the root element
            // (schema 1) or the first <name> element (schema 2), whichever we find
            // first.  We don't actually check the schema version, nor do we check
            // that the <name> element is actually the right one.

            if (localName.equals(getString(R.string.xml1elemName_root))) {
                mCurrentStyleName = atts.getValue(DEBATING_TIMER_URI,
                        getString(R.string.xml1attrName_root_name));
                throw new AllInformationFoundException();
                // We don't need to parse any more once we find the style name
            }

            if (localName.equals(getString(R.string.xml2elemName_name)))
                mNameBuffer = new StringBuilder();
        }
    }

    private class LookForCustomCheckboxOnClickListener implements OnClickListener {
        @Override
        public void onClick(View v) {
            CheckBox checkbox = (CheckBox) v;
            boolean checked = checkbox.isChecked();
            mFilesManager.setLookForUserFiles(checked);

            // If either it's not checked (so we don't care about read permissions), or read
            // permissions are already there, refresh the styles list. Ask for the permission if
            // it's not.
            if (!checked || requestReadPermission()) refreshStylesList();
        }
    }

    /**
     * A comparator for DebateFormatListEntries, which sorts the debate formats alphabetically
     * by style name.
     */
    private class StyleEntryComparatorByStyleName implements
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
            invalidateOptionsMenu();
        }
    }

    //******************************************************************************************
    // Public methods
    //******************************************************************************************

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.format_chooser_action_bar, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.formatChooser_actionBar_ok:
                confirmSelectionAndReturn();
                return true;
            case R.id.formatChooser_actionBar_share:
                shareSelection();
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        // disable the share button if the current selection isn't shareable
        MenuItem resetDebateItem = menu.findItem(R.id.formatChooser_actionBar_share);
        String filename = getSelectedFilename();
        boolean selectionShareable = filename != null && mFilesManager.getLocation(filename) == FormatXmlFilesManager.LOCATION_EXTERNAL_STORAGE;
        resetDebateItem.setVisible(selectionShareable);

        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String permissions[], @NonNull int[] grantResults) {
        if (requestCode == REQUEST_TO_READ_EXTERNAL_STORAGE) {
            // If we've just received read permissions, refresh the styles list.
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED)
                refreshStylesList();

            //  Otherwise, uncheck the checkbox and show an error message.
            else {
                CheckBox checkbox = (CheckBox) findViewById(R.id.formatChooser_lookForCustomCheckbox);
                if (checkbox != null) checkbox.setChecked(false);
                mFilesManager.setLookForUserFiles(false);
                showSnackbar(Snackbar.LENGTH_LONG, R.string.formatChooser_lookForCustom_errorNoReadPermission);
            }

        } else if (requestCode == REQUEST_TO_WRITE_EXTERNAL_STORAGE_FOR_IMPORT) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED)
                importIncomingFile();
            else
                showSnackbar(Snackbar.LENGTH_LONG, R.string.formatChooser_lookForCustom_errorNoWritePermissionForImport);
        }
    }

    //******************************************************************************************
    // Protected methods
    //******************************************************************************************

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_format_chooser);
        setSupportActionBar((Toolbar) findViewById(R.id.formatChooser_toolbar));

        DEBATING_TIMER_URI = getString(R.string.xml_uri);

        mFilesManager = new FormatXmlFilesManager(this);
        mStylesArrayAdapter = new DebateFormatEntryArrayAdapter(this, mStylesList,
                new FormatChooserActivityBinder());

        // Set the action bar
        ActionBar bar = getSupportActionBar();
        if (bar != null) bar.setDisplayHomeAsUpEnabled(true);

        // Configure the checkbox
        mInitialLookForCustomFormats = mFilesManager.isLookingForUserFiles();
        CheckBox checkbox = (CheckBox) findViewById(R.id.formatChooser_lookForCustomCheckbox);
        if (checkbox != null) {
            checkbox.setMovementMethod(LinkMovementMethod.getInstance());
            checkbox.setOnClickListener(new LookForCustomCheckboxOnClickListener());
            checkbox.setChecked(mInitialLookForCustomFormats);
        }

        // If we need it, ask the user for read permission. If it's not already granted, treat the
        // initial setting as false.
        if (mInitialLookForCustomFormats) {
            mInitialLookForCustomFormats = requestReadPermission(); // note: this method may show an alert to the user
        }

        // Populate the styles list
        populateStylesList();

        // Configure the ListView
        mStylesListView = (ListView) findViewById(R.id.formatChooser_stylesList);
        if (mStylesListView != null) {
            mStylesListView.setAdapter(mStylesArrayAdapter);
            mStylesListView.setOnItemClickListener(new StylesListViewOnItemClickListener());
        }

        if (Intent.ACTION_VIEW.equals(getIntent().getAction()) && requestWritePermission()) {
            importIncomingFile();

        } else {
            // Select and scroll to the incoming selection (if existent)
            String incomingFilename = getIntent().getStringExtra(EXTRA_XML_FILE_NAME);
            setSelectionAndScroll(incomingFilename);
        }
    }

    //******************************************************************************************
    // Private methods
    //******************************************************************************************

    /**
     * Confirms and handles the selection appropriately, and ends the Activity.
     */
    private void confirmSelectionAndReturn() {
        String selectedFilename = getSelectedFilename();
        String incomingFilename = getIntent().getStringExtra(EXTRA_XML_FILE_NAME);

        if (selectedFilename != null && selectedFilename.equals(incomingFilename) &&
                mInitialLookForCustomFormats == mFilesManager.isLookingForUserFiles()) {
            setResult(RESULT_UNCHANGED);

        } else if (selectedFilename == null) {
            setResult(RESULT_ERROR);
            e(TAG, "Returning error, no entry found");

        } else {
            Intent intent = new Intent();
            Log.v(TAG, "File name is " + selectedFilename);
            intent.putExtra(EXTRA_XML_FILE_NAME, selectedFilename);

            if (ACTION_CHOOSE_DEBATE_STYLE.equals(getIntent().getAction())) {
                Log.i(TAG, "confirmSelectionAndReturn: Returning result OK");
                setResult(RESULT_OK, intent);
            } else {
                Log.i(TAG, "confirmSelectionAndReturn: Starting new DebatingActivity");
                intent.setClass(this, DebatingActivity.class);
                startActivity(intent);
            }
        }

        this.finish();
    }

    /**
     * Given a filename, returns the index in the styles list where the entry is.
     * @param filename the file name of the style
     * @return integer between 0 and <code>mStylesList.length - 1</code>, or
     * <code>ListView.INVALID_POSITION</code> if the item could not be found.
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
        if (index < 0 || index > mStylesList.size())
            return null;
        return mStylesList.get(index).getFilename();
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
        DebateFormatInfoForSchema2 dfi2 = new DebateFormatInfoForSchema2(this, is);

        // If it's not 2.0, check to see if it is 1.0 or 1.1
        if (!dfi2.isSchemaSupported()) {
            DebateFormatInfoExtractorForSchema1 dfie = new DebateFormatInfoExtractorForSchema1(this);
            is.close();
            is = mFilesManager.open(filename); // open again to try schema 1.0
            DebateFormatInfo dfi1 = dfie.getDebateFormatInfo(is);
            if (dfi1.isSchemaSupported()) return dfi1;
        }

        // If it isn't, keep pretending it was 2.0.
        return dfi2;

    }

    @Nullable
    private String getDestinationFilenameFromUri(Uri uri) {
        String filename = null;
        String scheme = uri.getScheme();

        switch (scheme) {
            case "file":
                // Just retrieve the file name
                File file = new File(uri.getPath());
                String name = file.getName();
                if (name.length() > 0)
                    filename = name;
                break;

            case "content":
                // Try to find a name for the file
                Cursor cursor = getContentResolver().query(uri,
                        new String[] {MediaStore.MediaColumns.DISPLAY_NAME, MediaStore.MediaColumns.DATA}, null, null, null);
                if (cursor == null) {
                    e(TAG, "getDestinationFilenameFromUri: cursor was null");
                    return null;
                }
                if (!cursor.moveToFirst()) {
                    e(TAG, "getDestinationFilenameFromUri: failed moving cursor to first row");
                    cursor.close();
                    return null;
                }
                int dataIndex = cursor.getColumnIndex(MediaStore.MediaColumns.DATA);
                int nameIndex = cursor.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME);
                Log.i(TAG, "getDestinationFilenameFromUri: data at column " + dataIndex + ", name at column " + nameIndex);
                if (dataIndex >= 0) {
                    String path = cursor.getString(dataIndex);
                    filename = (new File(path)).getName();

                    Log.i(TAG, "getDestinationFilenameFromUri: got from data column, path: " + path + ", name: " + filename);
                } else if (nameIndex >= 0) {
                    filename = cursor.getString(nameIndex);
                    Log.i(TAG, "getDestinationFilenameFromUri: got from name column: " + filename);
                }
                else Log.e(TAG, "getDestinationFilenameFromUri: no column for file name available");
                cursor.close();
                break;

            default:
                return null;
        }

        // If it doesn't end in the .xml extension, make it end in one
        if (filename != null && !filename.endsWith(".xml")) {

            // Do this by stripping the current extension if there is one...
            int lastIndex = filename.lastIndexOf(".");
            if (lastIndex > 0) filename = filename.substring(0, lastIndex);

            // ...and then adding .xml.
            filename = filename + ".xml";

        }

        return filename;
    }

    private void importIncomingFile() {

        Intent intent = getIntent();
        if (!Intent.ACTION_VIEW.equals(intent.getAction())) {
            e(TAG, "importIncomingFile: Intent action was not ACTION_VIEW");
            return;
        }

        Log.i(TAG, String.format("importIncomingFile: mime type %s, data %s", intent.getType(), intent.getDataString()));

        Uri uri = intent.getData();
        InputStream is;
        try {
            is = getContentResolver().openInputStream(uri);
        } catch (FileNotFoundException e) {
            showSnackbar(LENGTH_LONG, R.string.formatChooser_view_error_generic);
            e(TAG, "importIncomingFile: Could not resolve file" + uri.toString());
            return;
        }

        String filename = getDestinationFilenameFromUri(uri);
        Log.i(TAG, "importIncomingFile: file name is " + filename);

        if (filename == null) {
            showSnackbar(LENGTH_LONG, R.string.formatChooser_view_error_generic);
            e(TAG, "importIncomingFile: File name was null");
            return;
        }

        try {
            mFilesManager.copy(is, filename);
        } catch (IOException e) {
            e.printStackTrace();
            showSnackbar(LENGTH_LONG, R.string.formatChooser_view_error_generic);
            e(TAG, "importIncomingFile: Could not copy file: " + e.getMessage());
            return;
        }

        showSnackbar(LENGTH_SHORT, R.string.formatChooser_snackbar_importSuccessful, filename);

        // Now, show the result
        mFilesManager.setLookForUserFiles(true);
        CheckBox checkbox = (CheckBox) findViewById(R.id.formatChooser_lookForCustomCheckbox);
        if (checkbox != null) checkbox.setChecked(true);
        refreshStylesList();
        setSelectionAndScroll(filename);
    }

    /**
     * @param view the <code>View</code> to be populated
     * @param filename the filename of the XML file from which data is to be taken
     * @throws IOException if there was an IO problem with the XML file
     * @throws SAXException if thrown by the XML parser
     */
    private void populateBasicInfo(View view, String filename) throws IOException, SAXException {
        DebateFormatInfo dfi = getDebateFormatInfo(filename);
        if (dfi != null)
            populateBasicInfo(view, dfi);
    }

    /**
     * Populates the master styles list, <code>mStylesList</code>.  Should be called when this
     * Activity is created, or whenever we want to refresh the styles list. If there is an error so
     * serious that it can't even get the list, we show a dialog to that effect, and leave the list
     * empty.
     */
    private void populateStylesList() {
        String[] fileList;

        try {
             fileList = mFilesManager.list();
        } catch (IOException e) {
            e.printStackTrace();
            ListIOErrorDialogFragment fragment = new ListIOErrorDialogFragment();
            fragment.show(getSupportFragmentManager(), DIALOG_TAG_LIST_IO_ERROR);
            return;
        }

        for (String filename : fileList) {
            if (!filename.endsWith(".xml")) continue;

            InputStream is;

            try {
                is = mFilesManager.open(filename);
            } catch (IOException e) {
                e(TAG, "Couldn't find file: " + filename);
                continue;
            }

            try {
                Xml.parse(is, Encoding.UTF_8,
                        new GetDebateFormatNameXmlContentHandler());

            } catch (AllInformationFoundException e) {
                // This exception means the XML parsing was successful - we just
                // use it to stop the parser.
                if (mCurrentStyleName != null)
                    mStylesList.add(new DebateFormatListEntry(filename, mCurrentStyleName));

            } catch (SAXException|IOException e) {
                mCurrentStyleName = null;
            }

        }

        // Sort alphabetically by style name and tell observers
        mStylesArrayAdapter.sort(new StyleEntryComparatorByStyleName());
        mStylesArrayAdapter.notifyDataSetChanged();
        invalidateOptionsMenu();
    }

    /**
     * Refreshes the styles list, intelligently maintaining the current selection if there is one.
     */
    private void refreshStylesList() {
        // Take note of current selection by file name
        String selectedFilename = getSelectedFilename();

        mStylesList.clear();
        populateStylesList();

        // Restore selection, which may have changed position
        setSelectionAndScroll(selectedFilename);
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
     * Requests the <code>READ_EXTERNAL_STORAGE</code> permission if it hasn't already been granted.
     * We do this here, not in {@link FormatXmlFilesManager}, so that {@link DebatingActivity}
     * doesn't ask for the permission.
     *
     * @return true if the permission is already granted, false otherwise.
     */
    private boolean requestReadPermission() {

        // READ_EXTERNAL_STORAGE started being enforced in API level 19 (KITKAT), so skip this check
        // if we're before then, to avoid calling a constant that's only existed since API level 16
        // (JELLY_BEAN)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT)
            return true;

        boolean granted = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
                == PackageManager.PERMISSION_GRANTED;

        if (!granted) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.READ_EXTERNAL_STORAGE},
                    REQUEST_TO_READ_EXTERNAL_STORAGE);
        }

        return granted;
    }

    /**
     * Requests the <code>WRITE_EXTERNAL_STORAGE</code> permission if it hasn't already been granted.
     * We do this here, not in {@link FormatXmlFilesManager}, so that {@link DebatingActivity}
     * doesn't ask for the permission.
     *
     * @return true if the permission is already granted, false otherwise.
     */
    private boolean requestWritePermission() {

        // WRITE_EXTERNAL_STORAGE started being enforced in API level 19 (KITKAT), so skip this
        // check if we're before then, to avoid calling a constant that's only existed since API
        // level 16 (JELLY_BEAN)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT)
            return true;

        boolean granted = ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                == PackageManager.PERMISSION_GRANTED;

        if (!granted) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                    REQUEST_TO_WRITE_EXTERNAL_STORAGE_FOR_IMPORT);
        }

        return granted;
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
    private void shareSelection() {
        String filename = getSelectedFilename();
        View coordinator = findViewById(R.id.formatChooser_coordinator);

        // Check for error conditions
        if (filename == null) {
            showSnackbar(LENGTH_SHORT, R.string.formatChooser_share_error_noFileSelected);
            return;
        }

        int location = mFilesManager.getLocation(filename);
        switch (location) {
            case FormatXmlFilesManager.LOCATION_EXTERNAL_STORAGE:
                break;
            case FormatXmlFilesManager.LOCATION_ASSETS:
                showSnackbar(LENGTH_SHORT, R.string.formatChooser_share_error_builtInFile);
                return;
            case FormatXmlFilesManager.LOCATION_NOT_FOUND:
            default:
                e(TAG, String.format("shareSelection: getLocation returned result code %d", location));
                showSnackbar(LENGTH_SHORT, R.string.formatChooser_share_error_notFound, filename);
                return;
        }

        File file = mFilesManager.getFileFromExternalStorage(filename);
        if (file == null) {
            e(TAG, String.format("shareSelection: getFileFromExternalStorage returned null on file %s", filename));
            showSnackbar(LENGTH_SHORT, R.string.formatChooser_share_error_generic);
            return;
        }

        Uri fileUri;
        try {
            fileUri = FileProvider.getUriForFile(this, FILES_AUTHORITY, file);
        } catch (IllegalArgumentException e) {
            e(TAG, "shareSelection: tried to get file from outside allowable paths");
            showSnackbar(LENGTH_SHORT, R.string.formatChooser_share_error_generic);
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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            ClipData clipData = new ClipData(filename, new String[]{"text/xml"}, new ClipData.Item(fileUri));
            shareIntent.setClipData(clipData);
        }

        Intent chooserIntent = Intent.createChooser(shareIntent, getString(R.string.formatChooser_share_chooserTitle));
        startActivity(chooserIntent);
    }

    private void showSnackbar(int duration, int stringResId, Object... formatArgs) {
        String string = getString(stringResId, formatArgs);
        View coordinator = findViewById(R.id.formatChooser_coordinator);
        if (coordinator != null) {
            Snackbar snackbar = Snackbar.make(coordinator, string, duration);
            View snackbarText = snackbar.getView();
            TextView textView = (TextView) snackbarText.findViewById(android.support.design.R.id.snackbar_text);
            if (textView != null) textView.setMaxLines(5);
            snackbar.show();
        }
    }

    /**
     * Concatenates a list of <code>String</code>s with line breaks delimiting.
     *
     * @param list An <code>ArrayList</code> of <code>String</code>s.
     * @return the result, a single <code>String</code>
     */
    private static String concatenate(ArrayList<String> list) {
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

    /**
     * @param view the <code>View</code> to be populated
     * @param dfi is an <code>InputStream> for the XML file from which data is to be taken
     */
    private static void populateBasicInfo(View view, DebateFormatInfo dfi) {
        ((TextView) view.findViewById(R.id.viewFormat_tableCell_regionValue)).setText(
                concatenate(dfi.getRegions()));
        ((TextView) view.findViewById(R.id.viewFormat_tableCell_levelValue)).setText(
                concatenate(dfi.getLevels()));
        ((TextView) view.findViewById(R.id.viewFormat_tableCell_usedAtValue)).setText(
                concatenate(dfi.getUsedAts()));
        ((TextView) view.findViewById(R.id.viewFormat_tableCell_descValue)).setText(
                dfi.getDescription());
    }
}
