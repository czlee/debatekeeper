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

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;

import net.czlee.debatekeeper.debateformat.DebateFormatInfo;
import net.czlee.debatekeeper.debateformat.DebateFormatInfoExtractorForSchema1;
import net.czlee.debatekeeper.debateformat.DebateFormatInfoForSchema2;
import net.czlee.debatekeeper.debateformat.XmlUtilities;
import net.czlee.debatekeeper.debateformat.XmlUtilities.IllegalSchemaVersionException;

import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import android.annotation.TargetApi;
import android.app.ActionBar;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.FragmentActivity;
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
import android.widget.Button;
import android.widget.ListView;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;
import android.widget.Toast;

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
public class FormatChooserActivity extends FragmentActivity {

    private static final String TAG = "FormatChooserActivity";

    private FormatXmlFilesManager mFilesManager;

    private ListView mStylesListView;
    private String mCurrentStyleName = null;

    private DebateFormatEntryArrayAdapter mStylesArrayAdapter;
    private final ArrayList<DebateFormatListEntry> mStylesList = new ArrayList<DebateFormatListEntry>();

    private String DEBATING_TIMER_URI;

    private static final String DIALOG_ARGUMENT_FILE_NAME = "fn";
    private static final String DIALOG_TAG_MORE_DETAILS   = "md";
    private static final String DIALOG_TAG_LIST_IO_ERROR  = "io";
    private static final String DIALOG_TAG_CONFIRM_DELETE = "cd";

    public  static final int RESULT_ERROR = RESULT_FIRST_USER;

    public static final String CURRENT_SCHEMA_VERSION = "2.0";

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

        public DebateFormatListEntry(String filename, String styleName) {
            this.filename = filename;
            this.styleName = styleName;
        }

        public String getFilename() {
            return filename;
        }

        public String getStyleName() {
            return styleName;
        }

        @Override
        public String toString() {
            return styleName;
        }

    }

    public static class DialogConfirmDeleteFragment extends DialogFragment {

        static DialogConfirmDeleteFragment newInstance(String filename) {
            DialogConfirmDeleteFragment fragment = new DialogConfirmDeleteFragment();
            Bundle args = new Bundle();
            args.putString(DIALOG_ARGUMENT_FILE_NAME, filename);
            fragment.setArguments(args);
            return fragment;
        }

        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            final FormatChooserActivity activity = (FormatChooserActivity) getActivity();
            final String filename = getArguments().getString(DIALOG_ARGUMENT_FILE_NAME);

            AlertDialog.Builder builder = new AlertDialog.Builder(activity);
            builder.setMessage(getString(R.string.formatChooser_dialog_confirmDelete_message, filename))
                   .setCancelable(true)
                   .setPositiveButton(R.string.formatChooser_dialog_confirmDelete_buttonPositive,
                           new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    activity.deleteFormatFile(filename);
                                }
                            })
                   .setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    dialog.dismiss();
                                }
                            });

            return builder.create();
        }

    }

    /**
     * An {@link AlertDialog} alerting the user to a fatal problem retrieving the styles list,
     * which then exits this Activity upon dismissal.
     * @author Chuan-Zheng Lee
     *
     */
    public static class DialogListIOErrorFragment extends DialogFragment {

        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            final Activity activity = getActivity();

            AlertDialog.Builder builder = new AlertDialog.Builder(activity);
            builder.setTitle(R.string.formatChooser_dialog_ioError_title)
                   .setMessage(R.string.formatChooser_dialog_ioError_message)
                   .setCancelable(false)
                   .setPositiveButton(R.string.formatChooser_dialog_ioError_button,
                            new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    activity.finish();
                                }
                            });
            return builder.create();
        }

    }

    public static class DialogMoreDetailsFragment extends DialogFragment {

        static DialogMoreDetailsFragment newInstance(String filename) {
            DialogMoreDetailsFragment fragment = new DialogMoreDetailsFragment();
            Bundle args = new Bundle();
            args.putString(DIALOG_ARGUMENT_FILE_NAME, filename);
            fragment.setArguments(args);
            return fragment;
        }

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
         * @param e
         * @return the {@link AlertDialog}
         */
        private AlertDialog getBlankDetailsDialog(String filename, Exception e) {
            FormatChooserActivity activity = (FormatChooserActivity) getActivity();
            AlertDialog.Builder builder = new AlertDialog.Builder(activity);
            builder.setTitle(R.string.formatChooser_dialog_blankDetails_title)
                   .setCancelable(true)
                   .setMessage(getString(R.string.formatChooser_dialog_blankDetails_text, filename, e.getMessage()))
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
            if (activity.getFileLocation(filename) == FormatXmlFilesManager.LOCATION_USER_DEFINED) {
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
         * @param view
         * @param tableResid A resource ID pointing to a <code>TableLayout</code>
         * @param rowResid A resource ID pointing to a <code>TableRow</code> <b>layout file</b>.
         * (Not the <code>TableRow</code> itself.)
         * TableRow must have at least two TextView elements, which must have IDs "text1" and "text2".
         * @param list the list of String arrays.  Each array must have two elements.
         */
        private void populateTwoColumnTable(View view, int tableResid, int rowResid, ArrayList<String[]> list) {
            TableLayout table = (TableLayout) view.findViewById(tableResid);

            Iterator<String[]> iterator = list.iterator();

            while (iterator.hasNext()) {
                String[] rowText = iterator.next();
                TableRow row = (TableRow) View.inflate(getActivity(), rowResid, null);
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

    /**
     * Interface to {@link DebateFormatEntryArrayAdapter}. Provides a method for
     * the <code>DebateFormatEntryArrayAdapter</code> to request the selected
     * position and whether "more details" mode is on.
     *
     * @author Chuan-Zheng Lee
     *
     */
    public class FormatChooserActivityBinder {
        public DetailsButtonOnClickListener getDetailsButtonOnClickListener(String filename) {
            return new DetailsButtonOnClickListener(filename);
        }

        /**
         * @return the position of the currently checked item.
         */
        public int getSelectedPosition() {
            return mStylesListView.getCheckedItemPosition();
        }

        public void populateBasicInfo(View view, String filename) throws IOException, SAXException {
            FormatChooserActivity.this.populateBasicInfo(view, filename);
        }

    }



    // ******************************************************************************************
    // Private classes
    // ******************************************************************************************

    private class AllInformationFoundException extends SAXException {
        private static final long serialVersionUID = 3195935815375118010L;
    }

    private class CancelButtonOnClickListener implements OnClickListener {
        @Override
        public void onClick(View v) {
            FormatChooserActivity.this.finish();
        }
    }

    private class DetailsButtonOnClickListener implements OnClickListener {

        private final String filename;

        public DetailsButtonOnClickListener(String filename) {
            this.filename = filename;
        }

        @Override
        public void onClick(View v) {
            DialogFragment fragment = DialogMoreDetailsFragment.newInstance(filename);
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

            if (localName.equals(getString(R.string.xml2elemName_name))) {
                mNameBuffer = new StringBuilder();
                return;
            }
        }



    }

    private class OKButtonOnClickListener implements OnClickListener {
        @Override
        public void onClick(View v) {
            confirmSelectionAndReturn();
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
        @TargetApi(Build.VERSION_CODES.HONEYCOMB)
        @Override
        public void onItemClick(AdapterView<?> parent, View view, int position,
                long id) {
            mStylesArrayAdapter.notifyDataSetChanged();
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB)
                invalidateOptionsMenu();
        }
    }

    //******************************************************************************************
    // Public methods
    //******************************************************************************************

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // We only show these buttons if there is an action bar.  In Gingerbread and earlier,
        // we show dedicated OK/Cancel buttons.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
            MenuInflater inflater = getMenuInflater();
            inflater.inflate(R.menu.format_chooser_action_bar, menu);
            return true;
        }
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
        case android.R.id.home:
        case R.id.formatChooser_actionBar_cancel:
            finish();
            break;
        case R.id.formatChooser_actionBar_ok:
            confirmSelectionAndReturn();
            break;
        case R.id.formatChooser_actionBar_share:
            shareCurrentSelection();
            break;
        case R.id.formatChooser_actionBar_delete:
            promptToDeleteForCurrentSelection();
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {

        MenuItem shareItem = menu.findItem(R.id.formatChooser_actionBar_share);
        shareItem.setEnabled(getSelectedFilename() != null);
        return super.onPrepareOptionsMenu(menu);
    }

    //******************************************************************************************
    // Protected methods
    //******************************************************************************************

    @TargetApi(11)
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_format_chooser);
        DEBATING_TIMER_URI = getString(R.string.xml_uri);

        setTitle(R.string.activityName_FormatChooser);

        mFilesManager = new FormatXmlFilesManager(this);

        // Set the action bar
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
            ActionBar bar = getActionBar();
            bar.setDisplayHomeAsUpEnabled(true);
        }

        // Set OnClickListeners
        // These buttons only exist in layouts for versions Gingerbread and older
        Button okButton = (Button) findViewById(R.id.formatChooser_okButton);
        if (okButton != null) okButton.setOnClickListener(new OKButtonOnClickListener());
        Button cancelButton = (Button) findViewById(R.id.formatChooser_cancelButton);
        if (cancelButton != null) cancelButton.setOnClickListener(new CancelButtonOnClickListener());

        // Populate mStylesList
        populateStylesLists();

        mStylesArrayAdapter = new DebateFormatEntryArrayAdapter(this, mStylesList,
                new FormatChooserActivityBinder());

        // Sort alphabetically by style name
        mStylesArrayAdapter.sort(new StyleEntryComparatorByStyleName());

        // Configure the ListView
        mStylesListView = (ListView) findViewById(R.id.formatChooser_stylesList);
        mStylesListView.setAdapter(mStylesArrayAdapter);
        mStylesListView.setOnItemClickListener(new StylesListViewOnItemClickListener());

        // Deal with the incoming intent, if any
        Intent intent = getIntent();
        if (intent.getAction() == Intent.ACTION_VIEW) {
            importIncomingFile(intent);

        // If not, select and scroll to the incoming selection (if existent)
        } else {
            int incomingSelection = getIncomingSelection();
            if (incomingSelection != ListView.INVALID_POSITION) {
                mStylesListView.setItemChecked(incomingSelection, true);
                mStylesListView.smoothScrollToPosition(incomingSelection);
            }
        }

    }

    //******************************************************************************************
    // Private methods
    //******************************************************************************************

    /**
     * Adds a style to the master styles list from a file name.
     * <p>Note: This does <b>not</b> throw an exception if it encounters an error. Callers must
     * check the return value if they care about whether the file was successfully added.</p>
     * @param filename the file name for this style
     * @return <code>true</code> if the file was successfully added, <code>false</code> if not
     */
    private boolean addFileToStylesList(String filename) {
        InputStream is;

        if (!filename.endsWith(".xml"))
            return false;

        try {
            is = mFilesManager.open(filename);
        } catch (IOException e) {
            Log.e(TAG, "Couldn't find file: "+ filename);
            return false;
        }

        try {
            Xml.parse(is, Encoding.UTF_8,
                    new GetDebateFormatNameXmlContentHandler());

        } catch (AllInformationFoundException e) {

            // This exception means the XML parsing was successful - we just
            // use it to stop the parser.
            if (mCurrentStyleName != null) {

                mStylesList.add(new DebateFormatListEntry(filename, mCurrentStyleName));

                // We won't need this value any more so delete it so we don't ever get confused.
                mCurrentStyleName = null;

                return true;
            }
            else return false;

        } catch (SAXException e) {
            mCurrentStyleName = null;
            return false;

        } catch (IOException e) {
            mCurrentStyleName = null;
            return false;
        }

        return false;

    }

    /**
     * Confirms and handles the selection appropriately, and ends the Activity.
     */
    private void confirmSelectionAndReturn() {
        String filename = getSelectedFilename();

        // TODO Make sure this method always goes to DebatingActivity, as it might have
        // launched from a file being opened

        if (filename == null) {

            // Nothing was selected, so just make a Toast to say so and exit.
            // (In theory, it might be that it was an invalid selection without a file name, but
            // this should never happen.)
            Toast.makeText(FormatChooserActivity.this, R.string.formatChooser_toast_noSelection,
                    Toast.LENGTH_SHORT).show();
            FormatChooserActivity.this.finish();

        } else if (filename.equals(getIncomingFilename())) {

            // Nothing has changed, so just make a Toast to say so and exit.
            Toast.makeText(FormatChooserActivity.this,
                    R.string.formatChooser_toast_formatUnchanged, Toast.LENGTH_SHORT)
                    .show();
            FormatChooserActivity.this.finish();

        } else {

            // Return the filename to DebatingActivity
            Log.v(TAG, "Returning file " + filename);
            Intent intent = new Intent();
            intent.putExtra(EXTRA_XML_FILE_NAME, filename);
            setResult(RESULT_OK, intent);
            FormatChooserActivity.this.finish();

        }
    }

    /**
     * Deletes the file with the given file name and removes it from the styles list
     * @param filename the file name to delete
     */
    private void deleteFormatFile(String filename) {

        boolean result = mFilesManager.delete(filename);

        if (result) {
            Log.v(TAG, "Deleted " + filename);
            Toast.makeText(FormatChooserActivity.this,
                    getString(R.string.formatChooser_toast_deleted, filename),
                    Toast.LENGTH_SHORT).show();

            DebateFormatListEntry entry = getEntryForFilename(filename);

            // Re-populate the styles lists
            if (entry != null) {
                boolean removed = mStylesList.remove(entry);
                if (!removed)
                    Log.v(TAG, "Could not remove " + filename + " from list");
                mStylesArrayAdapter.notifyDataSetChanged();
            }

            else Log.e(TAG, "Entry for " + filename + " not found");

        } else {
            Log.e(TAG, "Could not delete " + filename);
            Toast.makeText(FormatChooserActivity.this,
                    getString(R.string.formatChooser_toast_errorDeleting, filename),
                    Toast.LENGTH_SHORT).show();
        }

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

    /**
     * Returns the {@link DebateFormatListEntry} for the given file name.
     * @param filename a file name
     * @return the {@link DebateFormatListEntry} object for that file, or <code>null</code> if
     * there is no such object
     */
    private DebateFormatListEntry getEntryForFilename(String filename) {
        Iterator<DebateFormatListEntry> iterator = mStylesList.iterator();
        while (iterator.hasNext()) {
            DebateFormatListEntry entry = iterator.next();
            if (filename.equals(entry.getFilename()))
                return entry;
        }
        return null;
    }

    /**
     * @param filename a file name
     * @return an integer representing the location of the file
     */
    private int getFileLocation(String filename) {
        return mFilesManager.getLocation(filename);
    }

    /**
     * @return the selection (as an integer) that was passed in the {@link Intent} that
     * started this <code>Activity</code>, or <code>ListView.INVALID_POSITION</code>
     */
    private int getIncomingSelection() {
        String incomingFilename = getIncomingFilename();
        return getPositionOfFilename(incomingFilename);
    }

    /**
     * @return the filename that was passed in the {@link Intent} that started this
     * <code>Activity</code>, or <code>null</code> if no filename was passed in
     */
    private String getIncomingFilename() {
        Intent data = getIntent();
        String incomingFilename = data.getStringExtra(EXTRA_XML_FILE_NAME);
        return incomingFilename;
    }

    /**
     * @return the name of the currently-selected file, or <code>null</code> if nothing
     * is selected or if the currently-selected item appears to be out of range
     */
    private String getSelectedFilename() {

        int selectedPosition = mStylesListView.getCheckedItemPosition();

        // Do nothing if nothing is selected.
        if (selectedPosition == ListView.INVALID_POSITION) {
            Log.i(TAG, "Nothing was selected");
            return null;
        }

        if (selectedPosition >= mStylesList.size()) {
            Log.e(TAG, "Item " + selectedPosition + " does not exist");
            return null;
        }

        DebateFormatListEntry entry = mStylesList.get(selectedPosition);

        if (entry != null)
            return entry.getFilename();
        else
            return null;

    }

    /**
     * @param filename
     * @return
     */
    private int getPositionOfFilename(String filename) {
        if (filename != null) {
            Iterator<DebateFormatListEntry> entryIterator = mStylesList.iterator();
            while (entryIterator.hasNext()) {
                DebateFormatListEntry se = entryIterator.next();
                if (filename.equals(se.getFilename()))
                    return mStylesList.indexOf(se);
            }
        }
        return ListView.INVALID_POSITION;
    }

    private void importIncomingFile(Intent intent) {
        Log.i(TAG, "mime type: " + intent.getType());
        Log.i(TAG, "data: " + intent.getDataString());

        // TODO check for the <debate-format> element, show a dialog, and if the user agrees
        // then copy the file to the /debatekeeper directory, checking for overwrites
        // (including overriding built-in styles).

        Uri uri = intent.getData();
        File file = new File(uri.getPath());
        String filename = file.getName();

        try {
            mFilesManager.copy(file);
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
            Toast.makeText(this, "Failed to import " + filename + ", type " + intent.getType(), Toast.LENGTH_LONG).show();
            return;
        }

        Toast.makeText(this, "Successfully imported " + filename + ", type " + intent.getType(), Toast.LENGTH_LONG).show();

        // Remove the existing entry, if there is one
        DebateFormatListEntry entry = getEntryForFilename(filename);
        if (entry != null) {
            boolean removed = mStylesList.remove(entry);
            if (!removed)
                Log.v(TAG, "Could not remove " + filename + " from list");
        }

        // Add the new entry
        addFileToStylesList(filename);
        mStylesArrayAdapter.notifyDataSetChanged();
        mStylesArrayAdapter.sort(new StyleEntryComparatorByStyleName());

        // Select the newly-imported file
        int selection = getPositionOfFilename(filename);
        if (selection != ListView.INVALID_POSITION) {
            mStylesListView.setItemChecked(selection, true);
            mStylesListView.smoothScrollToPosition(selection);
        }

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
     * Activity is created.
     * @throws IOException if there is an IOException that is so serious that it cannot
     * hope to populate the StylesLists.  Note that this does <b>not</b> include an error
     * opening a single specific file.
     */
    private void populateStylesLists() {
        String[] fileList;

        try {
            fileList = mFilesManager.list();
        } catch (IOException e1) {
            e1.printStackTrace();
            DialogListIOErrorFragment fragment = new DialogListIOErrorFragment();
            fragment.show(getSupportFragmentManager(), DIALOG_TAG_LIST_IO_ERROR);
            return;
        }

        for (int i = 0; i < fileList.length; i++) {
            String filename = fileList[i];
            addFileToStylesList(filename);
        }

    }

    /**
     * Displays a dialog prompting the user to confirm that he wishes to delete the currently-
     * selected file, or displays a toast with an error message if for whatever reason the selected
     * file can't be deleted.
     */
    private void promptToDeleteForCurrentSelection() {
        String filename = getSelectedFilename();

        if (filename == null) {
            Toast.makeText(FormatChooserActivity.this, R.string.formatChooser_toast_noSelection,
                    Toast.LENGTH_SHORT).show();
            return;
        }

        switch (mFilesManager.getLocation(filename)) {
        case FormatXmlFilesManager.LOCATION_ASSETS:
            Toast.makeText(FormatChooserActivity.this, R.string.formatChooser_toast_deleteBuiltIn,
                    Toast.LENGTH_SHORT).show();
            return;
        case FormatXmlFilesManager.LOCATION_NOT_FOUND:
            Toast.makeText(FormatChooserActivity.this, R.string.formatChooser_toast_notFound,
                    Toast.LENGTH_SHORT).show();
            return;
        }

        DialogFragment fragment = DialogConfirmDeleteFragment.newInstance(filename);
        fragment.show(getSupportFragmentManager(), DIALOG_TAG_CONFIRM_DELETE);

    }

    /**
     * Creates and initiates an {@link Intent} that shares the currently-selected file.  If no
     * file is selected, it does nothing.  If there is an error, it shows a Toast and then does
     * nothing.
     */
    private void shareCurrentSelection() {

        String filename = getSelectedFilename();

        // Do nothing if not file name is selected
        if (filename == null) {
            Toast.makeText(this, R.string.formatChooser_toast_noSelection, Toast.LENGTH_SHORT).show();
            return;
        }

        // Do nothing if it is a built-in file, or if it is not found.
        switch (getFileLocation(filename)) {
        case FormatXmlFilesManager.LOCATION_ASSETS:
            Log.e(TAG, "File " + filename + " is not user-defined");
            Toast.makeText(this, R.string.formatChooser_toast_shareBuiltIn, Toast.LENGTH_SHORT).show();
            return;
        case FormatXmlFilesManager.LOCATION_NOT_FOUND:
            Log.e(TAG, "File " + filename + " not found");
            Toast.makeText(this, R.string.formatChooser_toast_noSelection, Toast.LENGTH_SHORT).show();
            return;
        }

        File file = mFilesManager.getFile(filename);

        // Do nothing if the file was not found.
        if (file == null) {
            Log.e(TAG, "Could not get File object for " + filename);
            Toast.makeText(this, R.string.formatChooser_toast_noSelection, Toast.LENGTH_SHORT).show();
            return;
        }

        Log.v(TAG, "Sharing item " + filename);

        Uri uri = Uri.fromFile(file);

        Intent intent = new Intent();
        intent.setAction(Intent.ACTION_SEND);
        intent.setType("text/xml");
        intent.putExtra(Intent.EXTRA_STREAM, uri);
        startActivity(Intent.createChooser(intent, getString(R.string.formatChooser_shareChooser_title)));
    }

    /**
     * Concatenates a list of <code>String</code>s with line breaks delimiting.
     * @param list An <code>ArrayList</code> of <code>String</code>s.
     * @return the result, a single <code>String</code>
     */
    private static String concatenate(ArrayList<String> list) {
        String str = new String();
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
     * @param is an <code>InputStream> for the XML file from which data is to be taken
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
    }}
