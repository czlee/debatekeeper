package com.ftechz.DebatingTimer;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;

import org.xml.sax.Attributes;
import org.xml.sax.ContentHandler;
import org.xml.sax.Locator;
import org.xml.sax.SAXException;

import android.content.Context;
import android.util.Log;
import android.util.Xml;
import android.util.Xml.Encoding;

import com.ftechz.DebatingTimer.DebateFormatBuilder.DebateFormatBuilderException;
import com.ftechz.DebatingTimer.SpeechFormat.CountDirection;

/**
 * DebateFormatBuilderFromXml uses the information in an XML file to build a {@link DebateFormat}.
 *
 * @author Chuan-Zheng Lee
 * @since 2012-06-15
 */
public class DebateFormatBuilderFromXml {

    private final Context             mContext;
    private final DebateFormatBuilder mDfb;
    private final ArrayList<String>   mErrorLog = new ArrayList<String>();

    private final String DEBATING_TIMER_URI;

    public DebateFormatBuilderFromXml(Context context) {
        mContext = context;
        mDfb     = new DebateFormatBuilder(context);

        DEBATING_TIMER_URI = context.getString(R.string.XmlUri);
    }

    //******************************************************************************************
    // Private classes
    //******************************************************************************************
    private enum SecondLevelContextType {
        NONE, INFO, RESOURCE, SPEECH_FORMAT, SPEECHES_LIST
    }

    private class DebateFormatXmlContentHandler implements ContentHandler {

        // endElement should erase these (i.e. set them to null)
        // so that they're only not null when we're inside one of these elements
        private String  mCurrentSpeechFormatFirstPeriod = null;
        private String  mCurrentSpeechFormatRef         = null;
        private Long    mCurrentSpeechFormatLength      = null;
        private String  mCurrentResourceRef             = null;
        private boolean mIsInSpeechesList               = false;
        private boolean mIsInRootContext                = false;

        @Override public void characters(char[] arg0, int arg1, int arg2) throws SAXException {}
        @Override public void endDocument() throws SAXException {}
        @Override public void endPrefixMapping(String prefix) throws SAXException {}
        @Override public void ignorableWhitespace(char[] ch, int start, int length) throws SAXException {}
        @Override public void processingInstruction(String target, String data) throws SAXException {}
        @Override public void setDocumentLocator(Locator locator) {}
        @Override public void skippedEntity(String name) throws SAXException {}
        @Override public void startDocument() throws SAXException {}
        @Override public void startPrefixMapping(String prefix, String uri) throws SAXException {}

        @Override
        public void endElement(String uri, String localName, String qName)
                throws SAXException {
            if (!uri.equals(DEBATING_TIMER_URI))
                return;

            /**
             * <debateformat name="something">
             * End the root context.
             */
            if (areEqual(localName, R.string.XmlElemNameRoot)) {
                mIsInRootContext = false;

            /** <resource ref="string">
             * End the context.
             */
            } else if (areEqual(localName, R.string.XmlElemNameResource)) {
                mCurrentResourceRef = null;

            /** <speechformat ref="string" length="5:00" firstperiod="string" countdir="up">
             * Set the first period, then end the context.
             */
            } else if (areEqual(localName, R.string.XmlElemNameSpeechFormat)) {
                try {
                    mDfb.setFirstPeriod(mCurrentSpeechFormatRef, mCurrentSpeechFormatFirstPeriod);
                } catch (DebateFormatBuilderException e) {
                    logXmlError(e);
                }

                mCurrentSpeechFormatFirstPeriod = null;
                mCurrentSpeechFormatRef = null;
                mCurrentSpeechFormatLength = null;

            /** <speeches>
             * End the speeches context.
             */
            } else if (areEqual(localName, R.string.XmlElemNameSpeechesList)) {
                mIsInSpeechesList = false;
            }

            /** <bell time="1:00" number="1" nextperiod="#stay" sound="#default" pauseonbell="true">
             * Do nothing
             */

            /** <period ref="something" desc="Human readable" bgcolor="#77ffcc00">
             * Do nothing
             */

            /** <include resource="reference">
             * Do nothing
             */
        }

        @Override
        public void startElement(String uri, String localName, String qName,
                Attributes atts) throws SAXException {
            if (!uri.equals(DEBATING_TIMER_URI))
                return;

            /**
             * <debateformat name="something">
             */
            if (areEqual(localName, R.string.XmlElemNameRoot)) {

                String name = getValue(atts, R.string.XmlAttrNameRootName);
                if (name == null) {
                    logXmlError(R.string.XmlErrorRootNoName);
                    return;
                }

                mDfb.setDebateFormatName(name);
                mIsInRootContext = true;
                return;
            }

            // For everything else, we must be inside the root element.
            // If we're not, refuse to do anything.
            if (!mIsInRootContext) {
                logXmlError(R.string.XmlErrorSomethingOutsideRoot);
                return;
            }

            /** <resource ref="string">
             * Create a reference with the reference as specified in 'ref'.
             * Must not be inside a resource or speech format.
             * 'ref' is mandatory.
             */
            if (areEqual(localName, R.string.XmlElemNameResource)) {

                // 1. Get the reference string.
                String reference = getValue(atts, R.string.XmlAttrNameCommonRef);
                if (reference == null) {
                    logXmlError(R.string.XmlErrorResourceNoRef);
                    return;
                }

                // 2. Check we're not inside any contexts.
                // If we are, ignore and reset all contexts.
                if (!assertNotInsideAnySecondLevelContextAndResetOtherwise()) {
                    logXmlError(R.string.XmlErrorResourceInsideContext, reference,
                            getCurrentSecondLevelContextTypeStr());
                    return;
                }

                // 3. Start a new resource
                try {
                    mDfb.addNewResource(reference);
                } catch (DebateFormatBuilderException e) {
                    logXmlError(e);
                    return;
                }

                // If we succeeded in adding the resource, take note of this reference string for
                // all this resource's sub-elements.  (Don't do this if there was an error, so that
                // sub-elements can be ignored.)
                mCurrentResourceRef = reference;

            /** <speechformat ref="string" length="5:00" firstperiod="string" countdir="up">
             * Create a speech format.
             * 'ref' and 'length' are mandatory.
             * 'firstperiod' and 'countdir' are optional.
             */
            } else if (areEqual(localName, R.string.XmlElemNameSpeechFormat)) {

                // 1. Get the reference string. Mandatory; exit on error.
                String reference = getValue(atts, R.string.XmlAttrNameCommonRef);
                if (reference == null) {
                    logXmlError(R.string.XmlErrorSpeechFormatNoRef);
                    return;
                }

                // 2. Check we're not inside any contexts.
                // If we are, ignore and reset all contexts.
                if (!assertNotInsideAnySecondLevelContextAndResetOtherwise()) {
                    logXmlError(R.string.XmlErrorSpeechFormatInsideContext, reference,
                            getCurrentSecondLevelContextTypeStr());
                    return;
                }

                // 3. Get the length string, then convert it to seconds. Mandatory; exit on error.
                // Take note of it, in case bells use "finish" as their bell time.
                String lengthStr = getValue(atts, R.string.XmlAttrNameSpeechFormatLength);
                long length = 0;
                if (lengthStr == null) {
                    logXmlError(R.string.XmlErrorSpeechFormatNoLength, reference);
                    return;
                }
                try {
                    length = timeStr2Secs(lengthStr);
                } catch (NumberFormatException e) {
                    logXmlError(R.string.XmlErrorSpeechFormatInvalidLength, reference, lengthStr);
                    return;
                }
                mCurrentSpeechFormatLength = length;

                // 4. Add the speech format.
                try {
                    mDfb.addNewSpeechFormat(reference, length);
                } catch (DebateFormatBuilderException e) {
                    logXmlError(e);
                    return;
                }

                // 5. If we got this far, take note of this reference string for all this speech
                // format's sub-elements.  (Don't do this if there was an error, so that
                // sub-elements can be ignored.)
                mCurrentSpeechFormatRef = reference;

                // Now do the optional attributes...

                // 6. Get the count direction, and set it if it's present
                String countdir = getValue(atts, R.string.XmlAttrNameSpeechFormatCountDir);
                if (countdir != null) {
                    try {
                        if (areEqualIgnoringCase(countdir,
                                R.string.XmlAttrValueSpeechFormatCountDirUp)) {
                            mDfb.setCountDirection(reference, CountDirection.COUNT_UP);
                        } else if (areEqualIgnoringCase(countdir,
                                R.string.XmlAttrValueSpeechFormatCountDirDown)) {
                            mDfb.setCountDirection(reference, CountDirection.COUNT_DOWN);
                        } else if (areEqualIgnoringCase(countdir,
                                R.string.XmlAttrValueSpeechFormatCountDirUser)) {
                            mDfb.setCountDirection(reference, CountDirection.COUNT_USER);
                        }
                    } catch (DebateFormatBuilderException e) {
                        logXmlError(R.string.XmlErrorSpeechFormatUnexpectedlyNotFound, reference);
                    }
                }

                // 7. Get the first period, and take note for later.
                // We'll deal with it as we exit this element, because the period is defined
                // inside the element.
                mCurrentSpeechFormatFirstPeriod =
                        getValue(atts, R.string.XmlAttrNameSpeechFormatFirstPeriod);

            /** <bell time="1:00" number="1" nextperiod="#stay" sound="#default" pauseonbell="true">
             * Create a BellInfo.
             * This must be inside a resource or speech format.
             * 'time' is mandatory.
             * All other attributes are optional.
             */
            } else if (areEqual(localName, R.string.XmlElemNameBell)) {

                // 1. Get the bell time. Mandatory; exit on error.
                String timeStr = getValue(atts, R.string.XmlAttrNameBellTime);;
                long time = 0;
                if (timeStr == null) {
                    logXmlError(R.string.XmlErrorBellNoTime, getContextStr());
                    return;
                } else if (areEqualIgnoringCase(timeStr, R.string.XmlAttrValueBellTimeFinish)) {
                    if (mCurrentSpeechFormatLength == null) {
                        logXmlError(R.string.XmlErrorBellFinishTimeUnexpectedlyNotFound, getContextStr());
                        return;
                    }
                    time = mCurrentSpeechFormatLength.longValue();
                } else {
                    try {
                        time = timeStr2Secs(timeStr);
                    } catch (NumberFormatException e) {
                        logXmlError(R.string.XmlErrorBellInvalidTime, getContextStr(), timeStr);
                        return;
                    }
                }

                // 2. Get the number of times to play, or default to 1.
                String numberStr = getValue(atts, R.string.XmlAttrNameBellNumber);
                int number = 1;
                if (numberStr != null) {
                    try {
                        number = Integer.parseInt(numberStr);
                    } catch (NumberFormatException e) {
                        logXmlError(R.string.XmlErrorBellInvalidNumber, getContextStr(), timeStr);
                    }
                }

                // 3. Get the next period reference, or default to null
                // "#stay" means null (i.e. leave unchanged)
                String periodInfoRef = getValue(atts, R.string.XmlAttrNameBellNextPeriod);
                if (periodInfoRef != null)
                    if (areEqualIgnoringCase(periodInfoRef, R.string.XmlAttrValueCommonStay))
                        periodInfoRef = null;

                // 4. We now have enough information to create the bell.
                BellInfo bi = new BellInfo(time, number);

                // 5. Get the sound to play, or default to the default
                String bellSound = getValue(atts, R.string.XmlAttrNameBellSound);
                if (bellSound != null) {
                    if (areEqualIgnoringCase(bellSound, R.string.XmlAttrValueCommonStay))
                        bellSound = null;
                    else if (areEqualIgnoringCase(bellSound, R.string.XmlAttrValueBellNextPeriodSilent))
                        bi.setSound(0);
                    else if (areEqualIgnoringCase(bellSound, R.string.XmlAttrValueCommonDefault));
                        // Do nothing
                    else
                        logXmlError(R.string.XmlErrorBellInvalidSound, getContextStr(), bellSound);
                }

                // 6. Determine whether to pause on this bell
                String pauseOnBell = getValue(atts, R.string.XmlAttrNameBellPauseOnBell);
                if (pauseOnBell != null) {
                    if (areEqualIgnoringCase(pauseOnBell, R.string.XmlAttrValueCommonTrue))
                        bi.setPauseOnBell(true);
                    else if (areEqualIgnoringCase(pauseOnBell, R.string.XmlAttrValueCommonFalse))
                        bi.setPauseOnBell(false);
                    else
                        logXmlError(R.string.XmlErrorBellInvalidPauseOnBell, getContextStr(), pauseOnBell);
                }

                // Finally, add the bell
                try {
                    switch (getCurrentSecondLevelContextType()) {
                    case RESOURCE:
                        mDfb.addBellInfoToResource(mCurrentResourceRef, bi, periodInfoRef);
                        break;
                    case SPEECH_FORMAT:
                        mDfb.addBellInfoToSpeechFormat(mCurrentSpeechFormatRef, bi, periodInfoRef);
                        break;
                    default:
                        logXmlError(R.string.XmlErrorBellOutsideContext);
                    }
                } catch (DebateFormatBuilderException e) {
                    logXmlError(e);
                }

            /** <period ref="something" desc="Human readable" bgcolor="#77ffcc00">
             * Create a PeriodInfo.
             * This must be inside a resource or speech format.
             * 'ref' is mandatory.
             * 'desc' and 'bgcolor' are optional.
             */
            } else if (areEqual(localName, R.string.XmlElemNamePeriod)){

                // 1. Get the reference. Mandatory; exit on error.
                String reference = getValue(atts, R.string.XmlAttrNameCommonRef);
                if (reference == null) {
                    logXmlError(R.string.XmlErrorPeriodNoRef, getContextStr());
                    return;
                }

                // 2. Get the description (implicitly default to null)
                String description = getValue(atts, R.string.XmlAttrNamePeriodDesc);
                if (description != null) {
                    if (areEqualIgnoringCase(description, R.string.XmlAttrValueCommonStay))
                        description = null;
                }

                // 3. Get the background colour (implicitly default to null)
                String bgcolorStr = getValue(atts, R.string.XmlAttrNamePeriodBgcolor);
                Integer backgroundColor = null;
                if (bgcolorStr != null) {
                    if (areEqualIgnoringCase(bgcolorStr, R.string.XmlAttrValueCommonStay))
                        backgroundColor = null;
                    else if (bgcolorStr.startsWith("#")) {
                        try {
                            backgroundColor = Integer.parseInt(bgcolorStr.substring(1), 16);
                        } catch (NumberFormatException e) {
                            logXmlError(R.string.XmlErrorPeriodInvalidColor, reference, bgcolorStr);
                        }
                    } else {
                        logXmlError(R.string.XmlErrorPeriodInvalidColor, reference, bgcolorStr);
                    }
                }

                // 4. We now have enough information to make the PeriodInfo
                PeriodInfo pi = new PeriodInfo(description, backgroundColor);

                // Finally, add the period
                try {
                    switch (getCurrentSecondLevelContextType()) {
                    case RESOURCE:
                        mDfb.addPeriodInfoToResource(mCurrentResourceRef, reference, pi);
                        break;
                    case SPEECH_FORMAT:
                        mDfb.addPeriodInfoToSpeechFormat(mCurrentSpeechFormatRef, reference, pi);
                        break;
                    default:
                        logXmlError(R.string.XmlErrorPeriodOutsideContext, reference);
                    }

                } catch (DebateFormatBuilderException e) {
                    logXmlError(e);
                }

            /** <include resource="reference">
             * Include a resource in a speech format.
             * This must be in a speech format.
             * 'resource' is mandatory.
             */
            } else if (areEqual(localName, R.string.XmlElemNameInclude)) {

                // 1. Get the resource reference. Mandatory; exit on error.
                String resourceRef = getValue(atts, R.string.XmlAttrNameIncludeResource);
                if (resourceRef == null) {
                    logXmlError(R.string.XmlErrorIncludeNoResource, getContextStr());
                }

                // 2. Check we're inside a speech format
                if (getCurrentSecondLevelContextType() != SecondLevelContextType.SPEECH_FORMAT) {
                    logXmlError(R.string.XmlErrorIncludeOutsideSpeechFormat, resourceRef);
                }

                // 3. Include the resource
                try {
                    mDfb.includeResource(mCurrentSpeechFormatRef, resourceRef);
                } catch (DebateFormatBuilderException e){
                    logXmlError(e);
                }

            /** <speeches>
             * Start the speeches context.
             */
            } else if (areEqual(localName, R.string.XmlElemNameSpeechesList)) {
                if (!assertNotInsideAnySecondLevelContextAndResetOtherwise()) {
                    logXmlError(R.string.XmlErrorSpeechesListInsideContext, getCurrentSecondLevelContextTypeStr());
                    return;
                }

                mIsInSpeechesList = true;

            /**
             * <speech name="1st Affirmative" format="formatname">
             * Add a speech.
             * This must be inside the speeches context.
             */
            } else if (areEqual(localName, R.string.XmlElemNameSpeech)) {

                // 1. Get the speech name.
                String name = getValue(atts, R.string.XmlAttrNameSpeechName);
                if (name == null) {
                    logXmlError(R.string.XmlErrorSpeechNoName);
                    return;
                }

                // 2. Get the speech format.
                String format = getValue(atts, R.string.XmlAttrNameSpeechFormat);
                if (format == null) {
                    logXmlError(R.string.XmlErrorSpeechNoFormat);
                    return;
                }

                // 3. We must be inside the speeches list.
                if (getCurrentSecondLevelContextType() != SecondLevelContextType.SPEECHES_LIST) {
                    logXmlError(R.string.XmlErrorSpeechOutsideSpeechesList, name);
                    return;
                }

                // Finally, add the speech.
                try {
                    mDfb.addSpeech(name, format);
                } catch (DebateFormatBuilderException e) {
                    logXmlError(e);
                }
            }
        }

        // ******** Private methods ********

        private String getContextStr() {
            if (mCurrentResourceRef != null) {
                return String.format("%s '%s'", getString(R.string.XmlElemNameResource), mCurrentResourceRef);
            } else if (mCurrentSpeechFormatRef != null) {
                return String.format("%s '%s'", getString(R.string.XmlElemNameSpeechFormat), mCurrentSpeechFormatRef);
            } else {
                return "unknown context";
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

        /**
         * Checks we're not currently inside a context.
         * If we are, reset all contexts and return false.
         * @return true if the assertion passes, false if it fails
         */
        private boolean assertNotInsideAnySecondLevelContextAndResetOtherwise() {
            if (getCurrentSecondLevelContextType() != SecondLevelContextType.NONE) {
                mCurrentResourceRef = null;
                mCurrentSpeechFormatRef = null;
                mCurrentSpeechFormatFirstPeriod = null;
                mCurrentSpeechFormatLength = null;
                return false;
            }
            return true;
        }

        private SecondLevelContextType getCurrentSecondLevelContextType() {
            if (mCurrentResourceRef != null)
                return SecondLevelContextType.RESOURCE;
            else if (mCurrentSpeechFormatRef != null)
                return SecondLevelContextType.SPEECH_FORMAT;
            else if (mIsInSpeechesList == true)
                return SecondLevelContextType.SPEECHES_LIST;
            else
                return SecondLevelContextType.NONE;
        }

        private String getCurrentSecondLevelContextTypeStr() {
            switch (getCurrentSecondLevelContextType()) {
            case NONE:
                return "no context";
            case RESOURCE:
                return "resource";
            case SPEECH_FORMAT:
                return "speech format";
            case SPEECHES_LIST:
                return "speeches list";
            case INFO:
                return "info";
            default:
                return "context unknown";
            }

        }

    }


    //******************************************************************************************
    // Public methods
    //******************************************************************************************
    /**
     * Builds a debate from a given input stream, which must be an XML file.
     * @param is an {@link InputStream} to an XML file
     * @return the {@link DebateFormat}
     */
    public DebateFormat buildDebateFromXml(InputStream is) {
        try {
            Xml.parse(is, Encoding.UTF_8, new DebateFormatXmlContentHandler());
        } catch (IOException e) {
            // TODO Auto-generated catch block
            Log.e(this.getClass().getSimpleName(), "IO exception");
            e.printStackTrace();
            return null;
        } catch (SAXException e) {
            // TODO Auto-generated catch block
            Log.e(this.getClass().getSimpleName(), "SAX exception");
            e.printStackTrace();
            return null;
        }

        return mDfb.getDebateFormat();

    }

    //******************************************************************************************
    // Private methods
    //******************************************************************************************
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

    /**
     * Logs an XML-related error from an exception.
     * @param e the Exception
     */
    private void logXmlError(Exception e) {
        mErrorLog.add(e.getMessage());
        Log.e("logXmlError", e.getMessage());
    }

    /**
     * Logs an XML-related error from a string resource.
     * @param resId the resource ID of the string resource
     */
    private void logXmlError(int resId) {
        mErrorLog.add(mContext.getString(resId));
        Log.e("logXmlError", mContext.getString(resId));
    }

    /**
     * Logs an XML-related error from a string resource and formats according to
     * <code>String.format</code>
     * @param resId the resource ID of the string resource
     * @param formatArgs arguments to pass to <code>String.format</code>
     */
    private void logXmlError(int resId, Object... formatArgs) {
        mErrorLog.add(mContext.getString(resId, formatArgs));
        Log.e("logXmlError", mContext.getString(resId, formatArgs));
    }

}
