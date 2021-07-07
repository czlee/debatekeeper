package net.czlee.debatekeeper.debateformat;

import android.content.Context;
import android.content.res.Resources;
import android.util.Xml;

import net.czlee.debatekeeper.R;

import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;

/**
 * DebateFormatStyleNameExtractor provides a method to extract the style name (and only the style
 * name) from a given input stream. It must be tied to a context, so that it can access its
 * resources. It should be used when <strong>only</strong> the style name is desired. Callers that
 * wish to read other things in the file should use another class designed to do more with the
 * debate format file.
 *
 * @author Chuan-Zheng Lee
 * @since 2016-09-25
 */

public class DebateFormatStyleNameExtractor {

    private final String DEBATING_TIMER_URI;
    private Resources mResources;
    private String mCurrentStyleName;

    public DebateFormatStyleNameExtractor(Context context) {
        mResources = context.getResources();
        DEBATING_TIMER_URI = mResources.getString(R.string.xml_uri);
    }

    /**
     * Parses the XML file to retrieve the name of the style for the given input stream.
     * @param is an {@link InputStream}
     * @return the name of the style, e.g. "British Parliamentary", or null if the file is not a
     * valid debate format XML file.
     *
     */
    public String getStyleName(InputStream is) throws IOException, SAXException {
        mCurrentStyleName = null;

        try {
            Xml.parse(is, Xml.Encoding.UTF_8, new GetDebateFormatNameXmlContentHandler());
        } catch (AllInformationFoundException e) {
            return mCurrentStyleName;
        }

        return null;
    }

    //******************************************************************************************
    // Private classes
    //******************************************************************************************

    private class AllInformationFoundException extends SAXException {
        private static final long serialVersionUID = 3195935815375118010L;
    }

    /**
     * This class just looks for first &lt;name&gt; element and saves its contents to
     * <code>mCurrentStyleName</code>.
     */
    private class GetDebateFormatNameXmlContentHandler extends DefaultHandler {

        private StringBuilder mNameBuffer = null;
        private String mCurrentLang = null;
        private int mLevel = 0;
        private ArrayList<String> mLanguages;
        /// Names, keyed by language
        private HashMap<String, String> mNames;

        @Override
        public void characters(char[] ch, int start, int length) throws SAXException {
            if (mNameBuffer == null) return;
            String str = new String(ch, start, length);
            mNameBuffer = mNameBuffer.append(str);
        }

        @Override
        public void endDocument() throws SAXException {
            LanguageChooser languageChooser = new LanguageChooser();
            // Choose appropriate name language
            String bestLang = languageChooser.choose(mLanguages.toArray(new String[0]));
            // Map back to the matching name
            mCurrentStyleName = mNames.get(bestLang);
            throw new AllInformationFoundException();
        }

        @Override
        public void endElement(String uri, String localName, String qName) throws SAXException {
            if (mNameBuffer != null) {
                if(!mNames.containsKey(mCurrentLang)) {
                    mLanguages.add(mCurrentLang);
                    mNames.put(mCurrentLang, mNameBuffer.toString());
                }
                mNameBuffer = null;
            }
            --mLevel;
        }

        @Override
        public void startDocument() throws SAXException {
            // initialise
            mCurrentStyleName = null;
            mNames = new HashMap<String, String>();
            mLanguages = new ArrayList<String>();
        }

        @Override
        public void startElement(String uri, String localName, String qName,
                                 Attributes atts) throws SAXException {

            if (!uri.equals(DEBATING_TIMER_URI))
                return;

            // Schema 1: Use the attribute of the root element

            if (localName.equals(mResources.getString(R.string.xml1elemName_root))) {
                mCurrentStyleName = atts.getValue(DEBATING_TIMER_URI,
                        mResources.getString(R.string.xml1attrName_root_name));
                throw new AllInformationFoundException();
                // We don't need to parse any more once we find the style name
            }

            // Schema 2: Track all <name> elements of the root, stored by language

            if ((mLevel == 1) // Look for <name> elements below root
                && localName.equals(mResources.getString(R.string.xml2elemName_name))) {
                mNameBuffer = new StringBuilder();
                mCurrentLang = atts.getValue(mResources.getString(R.string.xml2attrName_language));
                if ((mCurrentLang == null) || mCurrentLang.isEmpty()) mCurrentLang = "en-US";
            }

            ++mLevel;
        }
    }

}
