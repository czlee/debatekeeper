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
import java.math.BigInteger;
import java.util.ArrayList;

import net.czlee.debatekeeper.DebateFormatBuilder.DebateFormatBuilderException;

import org.xml.sax.Attributes;
import org.xml.sax.ContentHandler;
import org.xml.sax.Locator;
import org.xml.sax.SAXException;

import android.content.Context;
import android.util.Log;
import android.util.Xml;
import android.util.Xml.Encoding;

/**
 * DebateFormatBuilderFromXml uses the information in an XML file to build a {@link DebateFormat}.
 *
 * @author Chuan-Zheng Lee
 * @since  2012-06-15
 */
public class DebateFormatBuilderFromXml {

    private final Context             mContext;
    private final DebateFormatBuilder mDfb;
    private final ArrayList<String>   mErrorLog = new ArrayList<String>();
    private       String              mSchemaVersion = null;

    private final String DEBATING_TIMER_URI;
    private static final String MAXIMUM_SCHEMA_VERSION = "1.1";

    public DebateFormatBuilderFromXml(Context context) {
        mContext = context;
        mDfb     = new DebateFormatBuilder(context);

        DEBATING_TIMER_URI = context.getString(R.string.xml_uri);
    }

    //******************************************************************************************
    // Private classes
    //******************************************************************************************
    private class DebateFormatXmlContentHandler implements ContentHandler {

        // endElement should erase these (i.e. set them to null) so that they're only not null
        // when we're inside one of these elements.  NOTE however that they may be null even when
        // we are inside one of these elements, if the element in question had an error.  (We will
        // still be between the relevant tags; there just won't be an active resource/
        // speech format).  That is:
        //      m*Ref is NOT null           implies       we are in * context
        // but  m*Ref is null            does NOT imply   we are NOT in * context
        // and we are NOT in * context   does NOT imply   m*Ref is null
        private String  mCurrentFirstPeriod                 = null;
        private String  mCurrentSpeechFormatRef             = null;
        private String  mCurrentResourceRef                 = null;

        private DebateFormatXmlSecondLevelContext mCurrentSecondLevelContext
                = DebateFormatXmlSecondLevelContext.NONE;

        private boolean mIsInRootContext                = false;

        @Override public void characters(char[] ch, int start, int length) throws SAXException {}
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
             * <debateformat name="something" schemaversion="1.0">
             * End the root context.
             */
            if (areEqual(localName, R.string.xmlElemName_root)) {
                mIsInRootContext = false;

            /** <resource ref="string">
             * End the context.
             */
            } else if (areEqual(localName, R.string.xmlElemName_resource)) {
                mCurrentSecondLevelContext = DebateFormatXmlSecondLevelContext.NONE;
                mCurrentResourceRef = null;

            /**
             * <preptime-controlled length="7:00" firstperiod="string">
             * Set the first period and finish bell, then end the context.
             */
            } else if (areEqual(localName, R.string.xmlElemName_prepTimeControlledFormat)) {
                try {
                    mDfb.setFirstPeriodOfPrepTime(mCurrentFirstPeriod);
                } catch (DebateFormatBuilderException e) {
                    logXmlError(e);
                }

                try {
                    // If there isn't already a finish bell in this, add one and log the error.
                    if (!mDfb.hasFinishBellInPrepTimeControlled()) {
                        logXmlError(R.string.xmlError_prepTimeNoFinishBell);
                        mDfb.addBellInfoToPrepTimeAtFinish(new BellInfo(0, 2), null);
                    }
                } catch (DebateFormatBuilderException e) {
                    logXmlError(e);
                }

                mCurrentSecondLevelContext = DebateFormatXmlSecondLevelContext.NONE;
                mCurrentFirstPeriod = null;

            /** <speechtype ref="string" length="5:00" firstperiod="string" countdir="up">
             * Set the first period and finish bell, then end the context.
             */
            } else if (areEqual(localName, R.string.xmlElemName_speechFormat)) {
                try {
                    mDfb.setFirstPeriodOfSpeechFormat(mCurrentSpeechFormatRef, mCurrentFirstPeriod);
                } catch (DebateFormatBuilderException e) {
                    logXmlError(e);
                }

                try {
                    // If there isn't already a finish bell in this, add one and log the error.
                    if (!mDfb.hasFinishBellInSpeechFormat(mCurrentSpeechFormatRef)) {
                        logXmlError(R.string.xmlError_speechFormatNoFinishBell, mCurrentSpeechFormatRef);
                        mDfb.addBellInfoToSpeechFormatAtFinish(mCurrentSpeechFormatRef, new BellInfo(0, 2), null);
                    }
                } catch (DebateFormatBuilderException e) {
                    logXmlError(e);
                }

                mCurrentSecondLevelContext = DebateFormatXmlSecondLevelContext.NONE;
                mCurrentFirstPeriod = null;
                mCurrentSpeechFormatRef = null;

            /** <speeches>
             * End the speeches context.
             */
            } else if (areEqual(localName, R.string.xmlElemName_speechesList)) {
                mCurrentSecondLevelContext = DebateFormatXmlSecondLevelContext.NONE;
            }

            /** <bell time="1:00" number="1" nextperiod="#stay" pauseonbell="true">
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
             * <debateformat name="something" schemaversion="1.0">
             */
            if (areEqual(localName, R.string.xmlElemName_root)) {

                String name = getValue(atts, R.string.xmlAttrName_root_name);
                if (name == null) {
                    logXmlError(R.string.xmlError_rootNoName);
                    return;
                }

                mSchemaVersion = getValue(atts, R.string.xmlAttrName_root_schemaVersion);
                if (mSchemaVersion == null) {
                    logXmlError(R.string.xmlError_rootNoSchemaVersion);
                } else {
                    try {
                        if (!isSchemaSupported())
                            logXmlError(R.string.xmlError_rootNewSchemaVersion, mSchemaVersion, MAXIMUM_SCHEMA_VERSION);
                    } catch (IllegalArgumentException e) {
                        logXmlError(R.string.xmlError_rootInvalidSchemaVersion, mSchemaVersion);
                    }
                }


                mDfb.setDebateFormatName(name);
                mIsInRootContext = true;
                return;
            }

            // For everything else, we must be inside the root element.
            // If we're not, refuse to do anything.
            if (!mIsInRootContext) {
                logXmlError(R.string.xmlError_somethingOutsideRoot);
                return;
            }

            /** <resource ref="string">
             * Create a reference with the reference as specified in 'ref'.
             * Must not be inside a resource or speech format.
             * 'ref' is mandatory.
             */
            if (areEqual(localName, R.string.xmlElemName_resource)) {

                // 1. Get the reference string.
                String reference = getValue(atts, R.string.xmlAttrName_common_ref);
                if (reference == null) {
                    logXmlError(R.string.xmlError_resourceNoRef);
                    return;
                }

                // 2. Check we're not inside any contexts.
                // If we are, ignore and reset all contexts.
                if (!assertNotInsideAnySecondLevelContextAndResetOtherwise()) {
                    logXmlError(R.string.xmlError_resourceInsideContext, reference,
                            getCurrentSecondLevelContext().toString());
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
                mCurrentSecondLevelContext = DebateFormatXmlSecondLevelContext.RESOURCE;
                mCurrentResourceRef = reference;

            /**
             * <preptime length="15:00" />
             * Create simple prep time.
             * 'length' is mandatory.
             */
            } else if (areEqual(localName, R.string.xmlElemName_prepTimeSimpleFormat)) {

                // 1. Check we're not inside any contexts.  If we are, ignore and reset all
                // contexts.
                if (!assertNotInsideAnySecondLevelContextAndResetOtherwise()) {
                    logXmlError(R.string.xmlError_prepTimeInsideContext,
                            getCurrentSecondLevelContext().toString());
                    return;
                }

                // 2. Get the length string, then convert it to seconds. Mandatory; exit on error.
                // Take note of it, in case bells use "finish" as their bell time.
                String lengthStr = getValue(atts, R.string.xmlAttrName_controlledTimeLength);
                long length = 0;
                if (lengthStr == null) {
                    logXmlError(R.string.xmlError_prepTimeNoLength);
                    return;
                }
                try {
                    length = timeStr2Secs(lengthStr);
                } catch (NumberFormatException e) {
                    logXmlError(R.string.xmlError_prepTimeInvalidLength, lengthStr);
                    return;
                }

                // 3. Create the prep time.
                try {
                    mDfb.addPrepTimeSimple(length);
                } catch (DebateFormatBuilderException e) {
                    // TODO Auto-generated catch block
                    logXmlError(e);
                    return;
                }

            /**
             * <preptime-controlled length="7:00" firstperiod="string">
             * Create controlled prep time
             * 'length' is mandatory.
             * 'firstperiod' is optional.
             */
            } else if (areEqual(localName, R.string.xmlElemName_prepTimeControlledFormat)) {

                // 1. Check we're not inside any contexts.  If we are, ignore and reset all
                // contexts.
                if (!assertNotInsideAnySecondLevelContextAndResetOtherwise()) {
                    logXmlError(R.string.xmlError_prepTimeInsideContext,
                            getCurrentSecondLevelContext().toString());
                    return;
                }

                // 2. Get the length string, then convert it to seconds. Mandatory; exit on error.
                // Take note of it, in case bells use "finish" as their bell time.
                String lengthStr = getValue(atts, R.string.xmlAttrName_controlledTimeLength);
                long length = 0;
                if (lengthStr == null) {
                    logXmlError(R.string.xmlError_prepTimeNoLength);
                    return;
                }
                try {
                    length = timeStr2Secs(lengthStr);
                } catch (NumberFormatException e) {
                    logXmlError(R.string.xmlError_prepTimeInvalidLength, lengthStr);
                    return;
                }

                // 3. Create the prep time.
                try {
                    mDfb.addPrepTimeControlled(length);
                } catch (DebateFormatBuilderException e) {
                    // TODO Auto-generated catch block
                    logXmlError(e);
                    return;
                }

                // 4. If we got this far, take note of this reference string for all this speech
                // format's sub-elements.  (Don't do this if there was an error, so that
                // sub-elements can be ignored.)
                mCurrentSecondLevelContext = DebateFormatXmlSecondLevelContext.PREP_TIME_CONTROLLED;

                // Now do the optional attributes...

                // 5. Get the first period, and take note for later.
                // We'll deal with it as we exit this element, because the period is defined
                // inside the element.
                mCurrentFirstPeriod =
                        getValue(atts, R.string.xmlAttrName_controlledTimeFirstPeriod);

            /** <speechtype ref="string" length="5:00" firstperiod="string" countdir="up">
             * Create a speech format.
             * 'ref' and 'length' are mandatory.
             * 'firstperiod' and 'countdir' are optional.
             */
            } else if (areEqual(localName, R.string.xmlElemName_speechFormat)) {

                // 1. Get the reference string. Mandatory; exit on error.
                String reference = getValue(atts, R.string.xmlAttrName_common_ref);
                if (reference == null) {
                    logXmlError(R.string.xmlError_speechFormatNoRef);
                    return;
                }

                // 2. Check we're not inside any contexts.
                // If we are, ignore and reset all contexts.
                if (!assertNotInsideAnySecondLevelContextAndResetOtherwise()) {
                    logXmlError(R.string.xmlError_speechFormatInsideContext, reference,
                            getCurrentSecondLevelContext().toString());
                    return;
                }

                // 3. Get the length string, then convert it to seconds. Mandatory; exit on error.
                // Take note of it, in case bells use "finish" as their bell time.
                String lengthStr = getValue(atts, R.string.xmlAttrName_controlledTimeLength);
                long length = 0;
                if (lengthStr == null) {
                    logXmlError(R.string.xmlError_speechFormatNoLength, reference);
                    return;
                }
                try {
                    length = timeStr2Secs(lengthStr);
                } catch (NumberFormatException e) {
                    logXmlError(R.string.xmlError_speechFormatInvalidLength, reference, lengthStr);
                    return;
                }

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
                mCurrentSecondLevelContext = DebateFormatXmlSecondLevelContext.SPEECH_FORMAT;
                mCurrentSpeechFormatRef = reference;

                // Now do the optional attributes...

                // The 'countdir' attribute is obsolete.  If we find it, use an appropriate
                // warning message.
                String countdir = getValue(atts, R.string.xmlAttrName_speechFormat_countDir);
                if (countdir != null) {
                    logXmlError(R.string.xmlError_speechFormatFoundCountDir);
                }

                // 7. Get the first period, and take note for later.
                // We'll deal with it as we exit this element, because the period is defined
                // inside the element.
                mCurrentFirstPeriod =
                        getValue(atts, R.string.xmlAttrName_controlledTimeFirstPeriod);

            /** <bell time="1:00" number="1" nextperiod="#stay" sound="#default" pauseonbell="true">
             * Create a BellInfo.
             * This must be inside a resource or speech format.
             * 'time' is mandatory.
             * All other attributes are optional.
             */
            } else if (areEqual(localName, R.string.xmlElemName_bell)) {

                // 1. Get the bell time. Mandatory; exit on error.
                String timeStr = getValue(atts, R.string.xmlAttrName_bell_time);;
                long time = 0;
                boolean atFinish = false;
                if (timeStr == null) {
                    logXmlError(R.string.xmlError_bellNoTime, getCurrentContextAndReferenceStr());
                    return;
                } else if (areEqualIgnoringCase(timeStr, R.string.xmlAttrValue_bell_time_finish)) {
                    time = 0;  // will be overwritten addBellInfoToSpeechFormatAtFinish().
                    atFinish = true;
                } else {
                    try {
                        time = timeStr2Secs(timeStr);
                    } catch (NumberFormatException e) {
                        logXmlError(R.string.xmlError_bellInvalidTime, getCurrentContextAndReferenceStr(), timeStr);
                        return;
                    }
                }

                // 2. Get the number of times to play, or default to 1.
                String numberStr = getValue(atts, R.string.xmlAttrName_bell_number);
                int number = 1;
                if (numberStr != null) {
                    try {
                        number = Integer.parseInt(numberStr);
                    } catch (NumberFormatException e) {
                        logXmlError(R.string.xmlError_bellInvalidNumber, getCurrentContextAndReferenceStr(), timeStr);
                    }
                }

                // 3. We now have enough information to create the bell.
                BellInfo bi = new BellInfo(time, number);

                // 4. Get the next period reference, or default to null
                // "#stay" means null (i.e. leave unchanged)
                String periodInfoRef = getValue(atts, R.string.xmlAttrName_bell_nextPeriod);
                if (periodInfoRef != null)
                    if (areEqualIgnoringCase(periodInfoRef, R.string.xmlAttrValue_common_stay))
                        periodInfoRef = null;

                // 5. Determine whether to pause on this bell
                String pauseOnBellStr = getValue(atts, R.string.xmlAttrName_bell_pauseOnBell);
                if (pauseOnBellStr != null) {
                    if (areEqualIgnoringCase(pauseOnBellStr, R.string.xmlAttrValue_common_true))
                        bi.setPauseOnBell(true);
                    else if (areEqualIgnoringCase(pauseOnBellStr, R.string.xmlAttrValue_common_false))
                        bi.setPauseOnBell(false);
                    else
                        logXmlError(R.string.xmlError_bellInvalidPauseOnBell, getCurrentContextAndReferenceStr(), pauseOnBellStr);
                }

                // Finally, add the bell, but first check that the period info exists (and nullify
                // if it doesn't, so that the bell still gets added)
                try {
                    switch (getCurrentSecondLevelContext()) {
                    case RESOURCE:
                        if (mCurrentResourceRef == null) break;
                        if (periodInfoRef != null && !mDfb.hasPeriodInfoInResource(mCurrentResourceRef, periodInfoRef)) {
                            logXmlError(R.string.xmlError_resourcePeriodInfoNotFound, periodInfoRef, mCurrentResourceRef);
                            periodInfoRef = null;
                        }
                        mDfb.addBellInfoToResource(mCurrentResourceRef, bi, periodInfoRef);
                        break;
                    case PREP_TIME_CONTROLLED:
                        if (periodInfoRef != null && !mDfb.hasPeriodInfoInPrepTimeControlled(periodInfoRef)) {
                            logXmlError(R.string.xmlError_prepTimePeriodInfoNotFound, periodInfoRef, mCurrentResourceRef);
                            periodInfoRef = null;
                        }
                        if (atFinish)
                            mDfb.addBellInfoToPrepTimeAtFinish(bi, periodInfoRef);
                        else
                            mDfb.addBellInfoToPrepTime(bi, periodInfoRef);
                        break;
                    case SPEECH_FORMAT:
                        if (mCurrentSpeechFormatRef == null) break;
                        if (periodInfoRef != null && !mDfb.hasPeriodInfoInSpeechFormat(mCurrentSpeechFormatRef, periodInfoRef)) {
                            logXmlError(R.string.xmlError_speechFormatPeriodInfoNotFound, periodInfoRef, mCurrentSpeechFormatRef);
                            periodInfoRef = null;
                        }
                        if (atFinish)
                            mDfb.addBellInfoToSpeechFormatAtFinish(mCurrentSpeechFormatRef, bi, periodInfoRef);
                        else
                            mDfb.addBellInfoToSpeechFormat(mCurrentSpeechFormatRef, bi, periodInfoRef);
                        break;
                    default:
                        logXmlError(R.string.xmlError_bellOutsideContext);
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
            } else if (areEqual(localName, R.string.xmlElemName_period)){

                // 1. Get the reference. Mandatory; exit on error.
                String reference = getValue(atts, R.string.xmlAttrName_common_ref);
                if (reference == null) {
                    logXmlError(R.string.xmlError_periodNoRef, getCurrentContextAndReferenceStr());
                    return;
                }

                // 2. Get the description (implicitly default to null)
                String description = getValue(atts, R.string.xmlAttrName_period_desc);
                if (description != null) {
                    if (areEqualIgnoringCase(description, R.string.xmlAttrValue_common_stay))
                        description = null;
                }

                // 3. Get the background colour (implicitly default to null)
                String bgcolorStr = getValue(atts, R.string.xmlAttrName_period_bgcolor);
                Integer backgroundColor = null;
                if (bgcolorStr != null) {
                    if (areEqualIgnoringCase(bgcolorStr, R.string.xmlAttrValue_common_stay))
                        backgroundColor = null;
                    else if (bgcolorStr.startsWith("#")) {
                        try {
                            // We need to do it via BigInteger in order for large unsigned 32-bit
                            // integers to be parsed as unsigned integers.
                            backgroundColor = new BigInteger(bgcolorStr.substring(1), 16).intValue();
                        } catch (NumberFormatException e) {
                            logXmlError(R.string.xmlError_periodInvalidColor, reference, bgcolorStr);
                        }
                    } else {
                        logXmlError(R.string.xmlError_periodInvalidColor, reference, bgcolorStr);
                    }
                }

                // 4. Get whether POIs are allowed (implicitly default to false)
                String poisAllowedStr = getValue(atts, R.string.xmlAttrName_period_poisAllowed);
                boolean poisAllowed = false;
                if (poisAllowedStr != null) {
                    if (areEqualIgnoringCase(poisAllowedStr, R.string.xmlAttrValue_common_true))
                        poisAllowed = true;
                    else if (areEqualIgnoringCase(poisAllowedStr, R.string.xmlAttrValue_common_false))
                        poisAllowed = false;
                    else
                        logXmlError(R.string.xmlError_periodInvalidPoisAllowed, reference, poisAllowedStr);
                }

                // 5. We now have enough information to make the PeriodInfo
                PeriodInfo pi = new PeriodInfo(description, backgroundColor, poisAllowed);

                // Finally, add the period
                try {
                    switch (getCurrentSecondLevelContext()) {
                    case RESOURCE:
                        if (mCurrentResourceRef != null)
                            mDfb.addPeriodInfoToResource(mCurrentResourceRef, reference, pi);
                        break;
                    case PREP_TIME_CONTROLLED:
                        mDfb.addPeriodInfoToPrepTime(reference, pi);
                        break;
                    case SPEECH_FORMAT:
                        if (mCurrentSpeechFormatRef != null)
                            mDfb.addPeriodInfoToSpeechFormat(mCurrentSpeechFormatRef, reference, pi);
                        break;
                    default:
                        logXmlError(R.string.xmlError_periodOutsideContext, reference);
                    }

                } catch (DebateFormatBuilderException e) {
                    logXmlError(e);
                }

            /** <include resource="reference">
             * Include a resource in a speech format.
             * This must be in a speech format.
             * 'resource' is mandatory.
             */
            } else if (areEqual(localName, R.string.xmlElemName_include)) {

                // 1. Get the resource reference. Mandatory; exit on error.
                String resourceRef = getValue(atts, R.string.xmlAttrName_include_resource);
                if (resourceRef == null) {
                    logXmlError(R.string.xmlError_includeNoResource, getCurrentContextAndReferenceStr());
                    return;
                }

                // 2. Check we're inside a speech format
                if (getCurrentSecondLevelContext() != DebateFormatXmlSecondLevelContext.SPEECH_FORMAT) {
                    logXmlError(R.string.xmlError_includeOutsideSpeechFormat, resourceRef);
                    return;
                }

                // 3. Include the resource
                try {
                    if (mCurrentSpeechFormatRef != null)
                        mDfb.includeResource(mCurrentSpeechFormatRef, resourceRef);
                } catch (DebateFormatBuilderException e){
                    logXmlError(e);
                }

            /** <speeches>
             * Start the speeches context.
             */
            } else if (areEqual(localName, R.string.xmlElemName_speechesList)) {
                if (!assertNotInsideAnySecondLevelContextAndResetOtherwise()) {
                    logXmlError(R.string.xmlError_speechesListInsideContext,
                            getCurrentSecondLevelContext().toString());
                    return;
                }

                mCurrentSecondLevelContext = DebateFormatXmlSecondLevelContext.SPEECHES_LIST;

            /**
             * <speech name="1st Affirmative" type="formatname">
             * Add a speech.
             * This must be inside the speeches context.
             */
            } else if (areEqual(localName, R.string.xmlElemName_speech)) {

                // 1. Get the speech name.
                String name = getValue(atts, R.string.xmlAttrName_speech_name);
                if (name == null) {
                    logXmlError(R.string.xmlError_speechNoName);
                    return;
                }

                // 2. Get the speech format.
                String format = getValue(atts, R.string.xmlAttrName_speech_format);
                if (format == null) {
                    logXmlError(R.string.xmlError_speechNoFormat, name);
                    return;
                }

                // 3. We must be inside the speeches list.
                if (getCurrentSecondLevelContext() != DebateFormatXmlSecondLevelContext.SPEECHES_LIST) {
                    logXmlError(R.string.xmlError_speechOutsideSpeechesList, name);
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

        private String getCurrentContextAndReferenceStr() {
            if (mCurrentResourceRef != null) {
                return String.format("%s '%s'", getString(R.string.xmlElemName_resource), mCurrentResourceRef);
            } else if (mCurrentSpeechFormatRef != null) {
                return String.format("%s '%s'", getString(R.string.xmlElemName_speechFormat), mCurrentSpeechFormatRef);
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
            if (getCurrentSecondLevelContext() != DebateFormatXmlSecondLevelContext.NONE) {
                mCurrentResourceRef = null;
                mCurrentSpeechFormatRef = null;
                mCurrentFirstPeriod = null;
                return false;
            }
            return true;
        }

        private DebateFormatXmlSecondLevelContext getCurrentSecondLevelContext() {
            return mCurrentSecondLevelContext;
        }

    }


    //******************************************************************************************
    // Public methods
    //******************************************************************************************
    /**
     * Builds a debate from a given input stream, which must be an XML file.
     * @param is an {@link InputStream} to an XML file
     * @return the {@link DebateFormat}
     * @throws IOException if there was an IO error with the <code>InputStream</code>
     * @throws SAXException if thrown by the XML parser
     * @throws IllegalStateException if there were no speeches in this format
     */
    public DebateFormat buildDebateFromXml(InputStream is)
            throws IOException, SAXException, IllegalStateException {
        Xml.parse(is, Encoding.UTF_8, new DebateFormatXmlContentHandler());
        return mDfb.getDebateFormat();
    }

    /**
     * @return true if there are errors in the error log
     */
    public boolean hasErrors() {
        return mErrorLog.size() > 0;
    }

    /**
     * @return <code>true</code> if the schema version is supported.
     * <code>false</code> if there is no schema version, this includes if this builder hasn't parsed
     * an XML file yet.
     */
    public boolean isSchemaSupported() throws IllegalArgumentException {
        if (mSchemaVersion == null)
            return false;
        return (compareSchemaVersions(mSchemaVersion, MAXIMUM_SCHEMA_VERSION) <= 0);
    }

    /**
     * @return The schema version, could be <code>null</code>
     */
    public String getSchemaVersion() {
        return mSchemaVersion;
    }

    /**
     * @return An <i>ArrayList</i> of <code>String</code>s, each item being an error found by
     * the XML parser
     */
    public ArrayList<String> getErrorLog() {
        return mErrorLog;
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
     * @param a
     * @param b
     * @return 1 if a > b, 0 if a == b, 1 if a < b
     */
    private static int compareSchemaVersions(String a, String b) throws IllegalArgumentException {
        int[] a_int = versionToIntArray(a);
        int[] b_int = versionToIntArray(b);
        int min_length = (a_int.length > b_int.length) ? b_int.length : a_int.length;
        for (int i = 0; i < min_length; i++) {
            if (a_int[i] > b_int[i]) return 1;
            if (a_int[i] < b_int[i]) return -1;
        }
        return 0;
    }

    /**
     * @param version
     * @return an integer array
     */
    private static int[] versionToIntArray(String version) throws IllegalArgumentException {
        int[] result = new int[2];
        String[] parts = version.split("\\.", 2);
        if (parts.length != 2)
            throw new IllegalArgumentException("version must be in the form 'a.b' where a and b are numbers");
        for (int i = 0; i < 2; i++) {
            try {
                result[i] = Integer.parseInt(parts[i]);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("version must be in the form 'a.b' where a and b are numbers");
            }
        }
        return result;
    }

    private void addToErrorLog(String message) {
        String bullet = "• ";
        String line   = bullet.concat(message);
        mErrorLog.add(line);
    }

    /**
     * Logs an XML-related error from an exception.
     * @param e the Exception
     */
    private void logXmlError(Exception e) {
        addToErrorLog(e.getMessage());
        Log.e("logXmlError", e.getMessage());
    }

    /**
     * Logs an XML-related error from a string resource.
     * @param resId the resource ID of the string resource
     */
    private void logXmlError(int resId) {
        addToErrorLog(mContext.getString(resId));
        Log.e("logXmlError", mContext.getString(resId));
    }

    /**
     * Logs an XML-related error from a string resource and formats according to
     * <code>String.format</code>
     * @param resId the resource ID of the string resource
     * @param formatArgs arguments to pass to <code>String.format</code>
     */
    private void logXmlError(int resId, Object... formatArgs) {
        addToErrorLog(mContext.getString(resId, formatArgs));
        Log.e("logXmlError", mContext.getString(resId, formatArgs));
    }

}
