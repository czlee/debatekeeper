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

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;

import org.xml.sax.Attributes;
import org.xml.sax.ContentHandler;
import org.xml.sax.Locator;
import org.xml.sax.SAXException;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.util.Xml;
import android.util.Xml.Encoding;
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
public class FormatChooserActivity extends Activity {

    private FormatXmlFilesManager mFilesManager;

    private ListView mStylesListView;
    private Button   mOKButton;
    private Button   mCancelButton;
    private String   mCurrentStyleName = null;

    private DebateFormatEntryArrayAdapter mStylesArrayAdapter;
    private final ArrayList<DebateFormatListEntry> mStylesList = new ArrayList<DebateFormatListEntry>();

    private String DEBATING_TIMER_URI;

    private static final int DIALOG_IO_ERROR = 0;
    private static final int DIALOG_MORE_DETAILS = 1;
    private static final String BUNDLE_FILE_NAME = "fn";
    public  static final int RESULT_ERROR = RESULT_FIRST_USER;

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

    /**
     * Interface to {@link DebateFormatEntryArrayAdapter}. Provides a method for
     * the <code>DebateFormatEntryArrayAdapter</code> to request the selected
     * position and whether "more details" mode is on.
     *
     * @author Chuan-Zheng Lee
     *
     */
    public class FormatChooserActivityBinder {
        /**
         * @return the position of the currently checked item.
         */
        public int getSelectedPosition() {
            return mStylesListView.getCheckedItemPosition();
        }

        public void populateBasicInfo(View view, String filename) throws IOException, SAXException {
            FormatChooserActivity.this.populateBasicInfo(view, filename);
        }

        public DetailsButtonOnClickListener getDetailsButtonOnClickListener(String filename) {
            return new DetailsButtonOnClickListener(filename);
        }

    }


    // ******************************************************************************************
    // Private classes
    // ******************************************************************************************

    private class AllInformationFoundException extends SAXException {
        private static final long serialVersionUID = 3195935815375118010L;
    }

    private class DetailsButtonOnClickListener implements OnClickListener {

        private final Bundle bundleForDialog;

        public DetailsButtonOnClickListener(String filename) {
            bundleForDialog = new Bundle();
            bundleForDialog.putString(BUNDLE_FILE_NAME, filename);
        }

        @Override
        public void onClick(View v) {
            removeDialog(DIALOG_MORE_DETAILS);
            showDialog(DIALOG_MORE_DETAILS, bundleForDialog);
        }

    }

    /**
     * This class just looks for the string inside &lt;debateformat name="...">
     * and saves it to <code>mCurrentStyleName</code>.
     */
    private class GetDebateFormatNameXmlContentHandler implements ContentHandler {

        @Override public void characters(char[] arg0, int arg1, int arg2) throws SAXException {}
        @Override public void endDocument() throws SAXException {}
        @Override public void endElement(String arg0, String arg1, String arg2) throws SAXException {}
        @Override public void endPrefixMapping(String prefix) throws SAXException {}
        @Override public void ignorableWhitespace(char[] ch, int start, int length) throws SAXException {}
        @Override public void processingInstruction(String target, String data) throws SAXException {}
        @Override public void setDocumentLocator(Locator locator) {}
        @Override public void skippedEntity(String name) throws SAXException {}
        @Override public void startPrefixMapping(String prefix, String uri) throws SAXException {}

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

            if (localName.equals(getString(R.string.XmlElemNameRoot))) {
                mCurrentStyleName = atts.getValue(DEBATING_TIMER_URI,
                        getString(R.string.XmlAttrNameRootName));
                throw new AllInformationFoundException();
                // We don't need to parse any more once we find the style name
            }
        }

    }

    private class OKButtonOnClickListener implements OnClickListener {
        @Override
        public void onClick(View v) {
            int selectedPosition = mStylesListView.getCheckedItemPosition();
            if (selectedPosition == getIncomingSelection()) {
                Toast.makeText(FormatChooserActivity.this,
                        R.string.ToastFormatUnchanged, Toast.LENGTH_SHORT)
                        .show();
                FormatChooserActivity.this.finish();
            } else if (selectedPosition != ListView.INVALID_POSITION) {
                Toast.makeText(FormatChooserActivity.this, getString(R.string.ToastSelection,
                        mStylesList.get(selectedPosition).getStyleName()), Toast.LENGTH_SHORT)
                        .show();
                returnSelectionByPosition(selectedPosition);
            } else {
                Toast.makeText(FormatChooserActivity.this, R.string.ToastNoSelection,
                        Toast.LENGTH_SHORT).show();
                FormatChooserActivity.this.finish();
            }
        }
    }

    private class CancelButtonOnClickListener implements OnClickListener {
        @Override
        public void onClick(View v) {
            FormatChooserActivity.this.finish();
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
        }
    }

    //******************************************************************************************
    // Protected methods
    //******************************************************************************************

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.format_chooser);
        DEBATING_TIMER_URI = getString(R.string.XmlUri);

        mFilesManager = new FormatXmlFilesManager(this);

        mStylesListView = (ListView) findViewById(R.id.StylesListView);
        mOKButton       = (Button)   findViewById(R.id.FormatChooserOKButton);
        mCancelButton   = (Button)   findViewById(R.id.FormatChooserCancelButton);

        mOKButton.setOnClickListener(new OKButtonOnClickListener());
        mCancelButton.setOnClickListener(new CancelButtonOnClickListener());

        try {
            populateStylesLists();
        } catch (IOException e) {
            e.printStackTrace();
            this.showDialog(DIALOG_IO_ERROR);
        }

        mStylesArrayAdapter = new DebateFormatEntryArrayAdapter(this, mStylesList,
                new FormatChooserActivityBinder());

        // Sort alphabetically by style name
        mStylesArrayAdapter.sort(new StyleEntryComparatorByStyleName());

        mStylesListView.setAdapter(mStylesArrayAdapter);

        mStylesListView.setOnItemClickListener(new StylesListViewOnItemClickListener());

        // Select and scroll to the incoming selection (if existent)
        int incomingSelection = getIncomingSelection();
        if (incomingSelection != ListView.INVALID_POSITION) {
            mStylesListView.setItemChecked(incomingSelection, true);
            mStylesListView.smoothScrollToPosition(incomingSelection);
        }
    }

    @Override
    protected Dialog onCreateDialog(int id, Bundle bundle) {
        switch (id) {
        case DIALOG_IO_ERROR:
            return getIOErrorAlert();
        case DIALOG_MORE_DETAILS:
            String filename = bundle.getString(BUNDLE_FILE_NAME);
            return getMoreDetailsDialog(filename);
        default:
            return super.onCreateDialog(id);
        }
    }

    //******************************************************************************************
    // Private methods
    //******************************************************************************************
    /**
     * Populates the master styles list, <code>mStylesList</code>.  Should be called when this
     * Activity is created.
     * @throws IOException if there is an IOException that is so serious that it cannot
     * hope to populate the StylesLists.  Note that this does <b>not</b> include an error
     * opening a single specific file.
     */
    private void populateStylesLists() throws IOException {
        String[] fileList = mFilesManager.list();

        for (int i = 0; i < fileList.length; i++) {
            String filename = fileList[i];
            InputStream is;

            if (!filename.endsWith(".xml"))
                continue;

            try {
                is = mFilesManager.open(filename);
            } catch (IOException e) {
                Log.e(this.getClass().getSimpleName(),
                        String.format("Couldn't find file: %s", filename));
                continue;
            }

            try {
                Xml.parse(is, Encoding.UTF_8,
                        new GetDebateFormatNameXmlContentHandler());

            } catch (AllInformationFoundException e) {
                // This exception means the XML parsing was successful - we just
                // use it to stop the parser.
                if (mCurrentStyleName != null)
                    addStyleToList(filename, mCurrentStyleName);

            } catch (SAXException e) {
                mCurrentStyleName = null;
                continue;
            }

        }

    }

    /**
     * Adds a style to the master styles list.
     * @param filename the file name for this style
     * @param styleName the name of this style
     */
    private void addStyleToList(String filename, String styleName) {
        mStylesList.add(new DebateFormatListEntry(filename, styleName));
    }

    /**
     * @return the selection (as an integer) that was passed in the <code>Intent</code> that
     * started this <code>Activity</code>, or <code>ListView.INVALID_POSITION</code>
     */
    private int getIncomingSelection() {
        Intent data = getIntent();
        String incomingFilename = data.getStringExtra(EXTRA_XML_FILE_NAME);
        if (incomingFilename != null) {
            Iterator<DebateFormatListEntry> entryIterator = mStylesList
                    .iterator();
            while (entryIterator.hasNext()) {
                DebateFormatListEntry se = entryIterator.next();
                if (incomingFilename.equals(se.getFilename())) {
                    return mStylesList.indexOf(se);
                }
            }
        }
        return ListView.INVALID_POSITION;
    }

    /**
     * Ends this Activity, returning a result to the activity that called this Activity.
     * @param position the integer position in the styles list of the user-selected position.
     */
    private void returnSelectionByPosition(int position) {
        Log.v(this.getClass().getSimpleName(),
                String.format("Picked item %d", position));

        Intent intent = new Intent();

        if (position >= mStylesList.size()) {
            setResult(RESULT_ERROR);
            Log.e(this.getClass().getSimpleName(),
                    String.format("No item associated with that"));
        } else {
            String filename = mStylesList.get(position).getFilename();
            Log.v(this.getClass().getSimpleName(), String.format("File name is %s", filename));
            intent.putExtra(EXTRA_XML_FILE_NAME, filename);
            setResult(RESULT_OK, intent);
        }
        FormatChooserActivity.this.finish();
    }

    /**
     * @return An AlertDialog alerting the user to a fatal problem retrieving the styles list,
     * which then exits this Activity upon dismissal.
     */
    private AlertDialog getIOErrorAlert() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.IOErrorDialogTitle)
                .setMessage(R.string.IOErrorDialogMessage)
                .setCancelable(false)
                .setPositiveButton(R.string.IOErrorDialogButton,
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                FormatChooserActivity.this.finish();
                            }
                        });
        return builder.create();
    }

    /**
     * Returns an AlertDialog with information about a debate format, populated from the
     * debate format XML file.
     * @param filename the file name of the debate format XML file to which this Dialog should
     * relate
     * @return the AlertDialog
     */
    private AlertDialog getMoreDetailsDialog(String filename) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View view = View.inflate(this, R.layout.view_format_full, null);

        DebateFormatInfo dfi;
        try {
            dfi = getDebateFormatInfo(filename);
        } catch (IOException e) {
            return getBlankDetailsDialog(filename, e);
        } catch (SAXException e) {
            return getBlankDetailsDialog(filename, e);
        }

        populateFileInfo(view, filename);

        if (dfi != null) {
            populateBasicInfo(view, dfi);
            populatePrepTimeInfo(view, dfi);
            populateTwoColumnTable(view, R.id.ViewFormatTableSpeechTypes, R.layout.speech_type_row,
                    dfi.getSpeechFormatDescriptions());
            populateTwoColumnTable(view, R.id.ViewFormatTableSpeeches, R.layout.speech_row,
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
     * Returns an AlertDialog with an error message explaining why the "more details" Dialog
     * for a given debate format couldn't be populated.
     * @param filename the file name of the debate format XML file to which this Dialog should
     * relate
     * @param e
     * @return the AlertDialog
     */
    private AlertDialog getBlankDetailsDialog(String filename, Exception e) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.BlankDetailsDialogTitle)
               .setCancelable(true)
               .setMessage(getString(R.string.BlankDetailsDialogText, filename, e.getMessage()))
               .setPositiveButton(R.string.BlankDetailsDialogButtonText, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.cancel();
                    }
                });
        return builder.create();
    }

    /**
     * Parses an XML file to get the {@link DebateFormatInfo} object
     * @param filename the filename for the debate format XML file
     * @return the <code>DebateFormatInfo</code> object, or <code>null</code>
     * @throws IOException if there was an IO problem with the XML file
     * @throws SAXException if thrown by the XML parser
     */
    private DebateFormatInfo getDebateFormatInfo(String filename) throws IOException, SAXException {
        InputStream is;
        is = mFilesManager.open(filename);
        DebateFormatInfoExtractor dfie = new DebateFormatInfoExtractor(this);
        return dfie.getDebateFormatInfo(is);
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
     * @param view the <code>View</code> to be populated
     * @param is an <code>InputStream> for the XML file from which data is to be taken
     */
    private void populateBasicInfo(View view, DebateFormatInfo dfi) {
        ((TextView) view.findViewById(R.id.ViewFormatTableCellRegionValue)).setText(
                concatenate(dfi.getRegions()));
        ((TextView) view.findViewById(R.id.ViewFormatTableCellLevelValue)).setText(
                concatenate(dfi.getLevels()));
        ((TextView) view.findViewById(R.id.ViewFormatTableCellUsedAtValue)).setText(
                concatenate(dfi.getUsedAts()));
        ((TextView) view.findViewById(R.id.ViewFormatTableCellDescValue)).setText(
                dfi.getDescription());
    }

    /**
     * Populates a View with information about a given file
     * @param view the View to populate
     * @param filename the file name
     */
    private void populateFileInfo(View view, String filename) {
        if (mFilesManager.getLocation(filename) == FormatXmlFilesManager.LOCATION_USER_DEFINED) {
            TextView fileLocationText = (TextView) view.findViewById(R.id.ViewFormatFileLocationValue);
            fileLocationText.setText(getString(R.string.ViewFormatFileLocationValueUserDefined));
            fileLocationText.setVisibility(View.VISIBLE);
        }
        ((TextView) view.findViewById(R.id.ViewFormatFileNameValue)).setText(filename);
    }

    private void populatePrepTimeInfo(View view, DebateFormatInfo dfi) {
        String prepTimeDescription = dfi.getPrepTimeDescription();

        // If there is prep time, populate the view.
        if (prepTimeDescription != null)
            ((TextView) view.findViewById(R.id.ViewFormatPrepTimeValue)).setText(
                    prepTimeDescription);

        // Otherwise, hide the whole row.
        else
            view.findViewById(R.id.ViewFormatPrepTimeRow).setVisibility(View.GONE);
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
            TableRow row = (TableRow) View.inflate(this, rowResid, null);
            ((TextView) row.findViewById(R.id.text1)).setText(rowText[0].concat(" "));
            ((TextView) row.findViewById(R.id.text2)).setText(rowText[1].concat(" "));
            table.addView(row);
        }

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
    }}
