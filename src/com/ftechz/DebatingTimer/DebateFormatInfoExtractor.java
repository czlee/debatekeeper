package com.ftechz.DebatingTimer;

import java.io.IOException;
import java.io.InputStream;

import org.xml.sax.Attributes;
import org.xml.sax.ContentHandler;
import org.xml.sax.Locator;
import org.xml.sax.SAXException;

import android.content.Context;
import android.util.Log;
import android.util.Xml;
import android.util.Xml.Encoding;

/**
 * Extracts information form an XML file and returns a DebateFormatInfo object.
 *
 * @author Chuan-Zheng Lee
 *
 */
public class DebateFormatInfoExtractor {

    private final Context          mContext;
    private final String           DEBATING_TIMER_URI;
    private DebateFormatInfo mDebateFormatInfo;

    public DebateFormatInfoExtractor(Context context) {
        mContext           = context;
        DEBATING_TIMER_URI = context.getString(R.string.XmlUri);
    }

    // ******************************************************************************************
    // Private classes
    // ******************************************************************************************

    private class DebateFormatInfoContentHandler implements ContentHandler {

        private boolean mIsInRootContext = false;
        private boolean mIsInInfoContext = false;
        private boolean mDescriptionFound = false;
        private String  mThirdLevelInfoContext = null;
        private String  mCharactersBuffer = null;

        @Override public void endDocument() throws SAXException {}
        @Override public void endPrefixMapping(String prefix) throws SAXException {}
        @Override public void ignorableWhitespace(char[] ch, int start, int length) throws SAXException {}
        @Override public void processingInstruction(String target, String data) throws SAXException {}
        @Override public void setDocumentLocator(Locator locator) {}
        @Override public void skippedEntity(String name) throws SAXException {}
        @Override public void startDocument() throws SAXException {}
        @Override public void startPrefixMapping(String prefix, String uri) throws SAXException {}


        @Override
        public void characters(char[] ch, int start, int length)
                throws SAXException {
            String str = new String(ch, start, length);
            if (mCharactersBuffer == null) {
                Log.w(this.getClass().getSimpleName(), String.format("Ignoring characters '%s'", str));
                return;
            }
            mCharactersBuffer = mCharactersBuffer.concat(str);
            Log.i(this.getClass().getSimpleName(), String.format("Just added '%s', buffer is now '%s'", str, mCharactersBuffer));
        }

        @Override
        public void endElement(String uri, String localName, String qName)
                throws SAXException {
            /**
             * <debateformat name="something" schemaversion="1.0">
             * End the root context.
             */
            if (areEqual(localName, R.string.XmlElemNameRoot)) {
                mIsInRootContext = false;
                return;
            }

            if (mIsInInfoContext) {
                if (localName.equals(mThirdLevelInfoContext)) {
                    if (mCharactersBuffer == null) {
                        Log.e(this.getClass().getSimpleName(), "In a third level context but mCharactersBuffer is empty");
                        return;
                    }
                    // <region>
                    if (areEqual(localName, R.string.XmlElemNameInfoRegion)) {
                        mDebateFormatInfo.addRegion(mCharactersBuffer);
                    // <level>
                    } else if (areEqual(localName, R.string.XmlElemNameInfoLevel)) {
                        mDebateFormatInfo.addLevel(mCharactersBuffer);
                    // <usedat>
                    } else if (areEqual(localName, R.string.XmlElemNameInfoUsedAt)) {
                        mDebateFormatInfo.addUsedAt(mCharactersBuffer);
                    // <desc>
                    } else if (areEqual(localName, R.string.XmlElemNameInfoDesc)) {
                        if (!mDescriptionFound) {
                            mDescriptionFound = true;
                            mDebateFormatInfo.setDescription(mCharactersBuffer);
                        }
                    }
                    mThirdLevelInfoContext = null; // end this context
                    mCharactersBuffer = null;
                }
            }



        }

        @Override
        public void startElement(String uri, String localName, String qName,
                Attributes atts) throws SAXException {

            if (!uri.equals(DEBATING_TIMER_URI))
                return;

            /**
             * <debateformat name="something" schemaversion="1.0">
             */
            if (areEqual(localName, R.string.XmlElemNameRoot)) {
                mIsInRootContext = true;
                return;
            }

            // For everything else, we must be inside the root element.
            // If we're not, refuse to do anything.
            if (!mIsInRootContext) {
                return;
            }

            /**
             * <info>
             */
            if (areEqual(localName, R.string.XmlElemNameInfo)) {
                mIsInInfoContext = true;
                return;

            // Inside the <info> tags
            } else if (mIsInInfoContext) {
                mThirdLevelInfoContext = localName;
                mCharactersBuffer = new String();
            }

        }

        private boolean areEqual(String string, int resid) {
            return string.equals(getString(resid));
        }

        private boolean areEqualIgnoringCase(String string, int resid) {
            return string.equalsIgnoreCase(getString(resid));
        }

        private String getString(int resid) {
            return mContext.getString(resid);
        }

        private String getValue(Attributes atts, int localNameResid) {
            return atts.getValue(DEBATING_TIMER_URI, getString(localNameResid));
        }

    }

    // ******************************************************************************************
    // Public methods
    // ******************************************************************************************

    /**
     * Converts a String in the format 00:00 to a long, being the number of seconds
     * @param s the String
     * @return the total number of seconds (minutes + seconds * 60)
     * @throws NumberFormatException
     */
    private static long timeStr2Secs(String s) throws NumberFormatException {
        long seconds = 0;
        String parts[] = s.split(":", 2);
        switch (parts.length){
        case 2:
            long minutes = Long.parseLong(parts[0]);
            seconds += minutes * 60;
            seconds += Long.parseLong(parts[1]);
            break;
        case 1:
            seconds = Long.parseLong(parts[0]);
            break;
        default:
            throw new NumberFormatException();
        }
        return seconds;
    }

    public DebateFormatInfo getDebateFormatInfo(InputStream is) {

        mDebateFormatInfo = new DebateFormatInfo(mContext);

        try {
            Xml.parse(is, Encoding.UTF_8, new DebateFormatInfoContentHandler());
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
            return null;
        } catch (SAXException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
            return null;
        }

        return mDebateFormatInfo;
    }

}
