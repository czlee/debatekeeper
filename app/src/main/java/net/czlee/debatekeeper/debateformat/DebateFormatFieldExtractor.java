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

/**
 * DebateFormatFieldExtractor provides a method to extract a single field (and only one field) from
 * a given input stream. It must be tied to a context, so that it can access its resources. It
 * should be used when <strong>only</strong> one field is desired. Callers that wish to read other
 * things in the file should use another class designed to do more with the debate format file.
 *
 * @author Chuan-Zheng Lee
 * @since 2016-09-25
 */

public class DebateFormatFieldExtractor {

    private final String DEBATING_TIMER_URI;
    private final Resources mResources;
    private final String mFieldName;
    private String mFieldValue;

    public DebateFormatFieldExtractor(Context context, int fieldNameResId) {
        mResources = context.getResources();
        DEBATING_TIMER_URI = mResources.getString(R.string.xml_uri);
        mFieldName = mResources.getString(fieldNameResId);
    }

    /**
     * Parses the XML file to retrieve the value held in the requested field for the given input
     * stream.
     *
     * @param is an {@link InputStream}
     * @return the name of the style, e.g. "British Parliamentary", or null if the file is not a
     * valid debate format XML file.
     */
    public String getFieldValue(InputStream is) throws IOException, SAXException {
        mFieldValue = null;

        try {
            Xml.parse(is, Xml.Encoding.UTF_8, new GetDebateFormatNameXmlContentHandler());
        } catch (AllInformationFoundException e) {
            return mFieldValue;
        }

        return null;
    }

    //******************************************************************************************
    // Private classes
    //******************************************************************************************

    private static class AllInformationFoundException extends SAXException {
        private static final long serialVersionUID = 3195935815375118010L;
    }

    /**
     * This class just looks for first &lt;name&gt; element and saves its contents to
     * <code>mCurrentStyleName</code>.
     */
    private class GetDebateFormatNameXmlContentHandler extends DefaultHandler {

        private StringBuilder mNameBuffer = null;

        @Override
        public void characters(char[] ch, int start, int length) {
            String str = new String(ch, start, length);
            if (mNameBuffer == null) return;
            mNameBuffer = mNameBuffer.append(str);
        }

        @Override
        public void endElement(String uri, String localName, String qName) throws SAXException {
            if (localName.equals(mFieldName)) {
                mFieldValue = mNameBuffer.toString();
                throw new AllInformationFoundException();
                // We don't need to parse any more once we finish getting the style name
            }
        }

        @Override
        public void startDocument() {
            // initialise
            mFieldValue = null;
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

            if (localName.equals(mResources.getString(R.string.xml1elemName_root))) {
                mFieldValue = atts.getValue(DEBATING_TIMER_URI, mFieldName);
                throw new AllInformationFoundException();
                // We don't need to parse any more once we find the style name
            }

            if (localName.equals(mFieldName))
                mNameBuffer = new StringBuilder();
        }
    }

}