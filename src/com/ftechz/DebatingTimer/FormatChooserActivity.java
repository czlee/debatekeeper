package com.ftechz.DebatingTimer;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Comparator;

import org.xml.sax.Attributes;
import org.xml.sax.ContentHandler;
import org.xml.sax.Locator;
import org.xml.sax.SAXException;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.AssetManager;
import android.os.Bundle;
import android.util.Log;
import android.util.Xml;
import android.util.Xml.Encoding;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.ListView;

/**
 * This Activity displays a list of styles.  The user
 * @author Chuan-Zheng Lee
 */
public class FormatChooserActivity extends Activity {

    private ListView mStylesListView;
    private String   mCurrentStyleName = null;

    private final ArrayList<StyleEntry> mStylesList = new ArrayList<StyleEntry>();

    private String DEBATING_TIMER_URI;

    private static final int DIALOG_IO_ERROR = 0;
    public  static final int RESULT_ERROR = RESULT_FIRST_USER;

    public static final String EXTRA_XML_FILE_NAME = "xmlfn";

    public FormatChooserActivity() {
        super();
    }

    //******************************************************************************************
    // Private classes
    //******************************************************************************************

    private class AllInformationFoundException extends SAXException {}

    /**
     * This class just looks for the string inside &lt;debateformat name="..."> and
     * saves it to <code>mCurrentStyleName</code>.
     */
    private class GetDebateFormatNameXmlContentHandler implements ContentHandler {

        @Override public void characters(char[] arg0, int arg1, int arg2) throws SAXException {}
        @Override public void endDocument() throws SAXException {}
        @Override public void endElement(String arg0, String arg1, String arg2) throws SAXException {}
        @Override public void endPrefixMapping(String arg0) throws SAXException {}
        @Override public void ignorableWhitespace(char[] arg0, int arg1, int arg2) throws SAXException {}
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
                mCurrentStyleName = atts.getValue(DEBATING_TIMER_URI, getString(R.string.XmlAttrNameRootName));
                throw new AllInformationFoundException(); // We don't need to parse any more once we find the style name
            }
        }

    }

    public class StylesListOnItemClickListener implements OnItemClickListener {
        @Override
        public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
            Log.v(this.getClass().getSimpleName(), String.format("Picked item %d", position));

            Intent intent = new Intent();

            if (position >= mStylesList.size()) {
                setResult(RESULT_ERROR);
                Log.e(this.getClass().getSimpleName(), String.format("No item associated with that"));
            } else {
                String filename = mStylesList.get(position).getFilename();
                Log.v(this.getClass().getSimpleName(), String.format("File name is %s", filename));
                intent.putExtra(EXTRA_XML_FILE_NAME, filename);
                setResult(RESULT_OK, intent);
            }
        FormatChooserActivity.this.finish();
        }
    }

    /**
     * Maps file names to style names.
     * Keys are file names, values are style names.
     * Supports toString() so that it can be passed to an ArrayAdapter.
     */
    private class StyleEntry {

        private final String filename;
        private final String styleName;

        public StyleEntry(String filename, String styleName) {
            this.filename  = filename;
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

    private class StyleEntryComparatorByStyleName implements Comparator<StyleEntry> {

        @Override
        public int compare(StyleEntry lhs, StyleEntry rhs) {
            return lhs.getStyleName().compareToIgnoreCase(rhs.getStyleName());
        }

    }

    //******************************************************************************************
    // Public methods
    //******************************************************************************************

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.styles_list);
        DEBATING_TIMER_URI = getString(R.string.XmlUri);

        mStylesListView = (ListView) findViewById(R.id.stylesList);

        mStylesListView.setOnItemClickListener(new StylesListOnItemClickListener());

        try {
            populateStylesLists();
        } catch (IOException e) {
            e.printStackTrace();
            this.showDialog(DIALOG_IO_ERROR);
        }

        ArrayAdapter<StyleEntry> adapter = new ArrayAdapter<StyleEntry>(this, R.layout.styles_list_item, mStylesList);

        // Sort alphabetically by style name
        adapter.sort(new StyleEntryComparatorByStyleName());

        mStylesListView.setAdapter(adapter);

    }

    //******************************************************************************************
    // Protected methods
    //******************************************************************************************
    @Override
    protected Dialog onCreateDialog(int id) {
        switch (id) {
        case DIALOG_IO_ERROR:
            return getIOErrorAlert();
        default:
            return null;
        }
    }

    //******************************************************************************************
    // Private methods
    //******************************************************************************************
    private void populateStylesLists() throws IOException {
        final AssetManager assets = getAssets();
        String [] fileList = assets.list("");

        for (int i = 0; i < fileList.length; i++) {
            String filename = fileList[i];
            InputStream is;

            if (!filename.endsWith(".xml"))
                continue;

            try {
                is = assets.open(filename);
            } catch (IOException e) {
                Log.e(this.getClass().getSimpleName(), String.format("Couldn't find file: %s", filename));
                continue;
            }

            try {
                Xml.parse(is, Encoding.UTF_8, new GetDebateFormatNameXmlContentHandler());

            } catch (AllInformationFoundException e) {
                // This exception means the XML parsing was successful - we just use it to stop the parser.
                if (mCurrentStyleName != null)
                    addStyleToList(filename, mCurrentStyleName);

            } catch (SAXException e) {
                mCurrentStyleName = null;
                continue;
            }

        }

    }

    private void addStyleToList(String filename, String styleName) {
        mStylesList.add(new StyleEntry(filename, styleName));
    }

    private AlertDialog getIOErrorAlert() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.IOErrorDialogTitle)
               .setMessage(R.string.IOErrorDialogMessage)
               .setCancelable(false)
               .setPositiveButton(R.string.IOErrorDialogButton, new DialogInterface.OnClickListener() {

                @Override
                public void onClick(DialogInterface dialog, int which) {
                    FormatChooserActivity.this.finish();
                }
            });
        return builder.create();
        }
}
