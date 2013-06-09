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

package net.czlee.debatekeeper.debateformat;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.util.ArrayList;

import net.czlee.debatekeeper.R;
import net.czlee.debatekeeper.debateformat.DebateFormatBuilderForSchema1.DebateFormatBuilderException;
import net.czlee.debatekeeper.debateformat.XmlUtilities.IllegalSchemaVersionException;

import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import android.content.Context;
import android.util.Log;
import android.util.Xml;
import android.util.Xml.Encoding;

/**
 * DebateFormatBuilderFromXmlForSchema1 uses the information in an XML file to build a {@link DebateFormat}.
 *
 * @author Chuan-Zheng Lee
 * @since  2012-06-15
 */
public class DebateFormatBuilderFromXmlForSchema1 implements DebateFormatBuilderFromXml {

    private final Context                       mContext;
    private final DebateFormatBuilderForSchema1 mDfb;
    private final ArrayList<String>             mErrorLog = new ArrayList<String>();
    private       String                        mSchemaVersion = null;

    private final String DEBATING_TIMER_URI;
    private static final String MINIMUM_SCHEMA_VERSION = "1.0";
    private static final String MAXIMUM_SCHEMA_VERSION = "1.1";

    public DebateFormatBuilderFromXmlForSchema1(Context context) {
        mContext = context;
        mDfb     = new DebateFormatBuilderForSchema1(context);

        DEBATING_TIMER_URI = context.getString(R.string.xml_uri);
    }

    //******************************************************************************************
    // Private classes
    //******************************************************************************************
    private class DebateFormatXmlContentHandler extends DefaultHandler {

        // endElement should erase these (i.e. set them to null) so that they're only not null
        // when we're inside one of these elements.  NOTE however that they may be null even when
        // we are inside one of these elements, if the element in question had an error.  (We will
        // still be between the relevant tags; there just won't be an active resource/
        // speech format).  That is:
        //      m*Ref is NOT null           implies       we are in * context
        // but  m*Ref is null            does NOT imply   we are NOT in * context
        // and we are NOT in * context   does NOT imply   m*Ref is null
        private String  mCurrentFirstPeriod     = null;
        private String  mCurrentSpeechFormatRef = null;
        private String  mCurrentResourceRef     = null;

        private DebateFormatXmlSecondLevelContext mCurrentSecondLevelContext
                = DebateFormatXmlSecondLevelContext.NONE;

        private boolean mIsInRootContext = false;

        @Override
        public void endElement(String uri, String localName, String qName)
                throws SAXException {
            if (!uri.equals(DEBATING_TIMER_URI))
                return;

            /**
             * <debateformat name="something" schemaversion="1.0">
             * End the root context.
             */
            if (areEqual(localName, R.string.xml1elemName_root)) {
                mIsInRootContext = false;

            /** <resource ref="string">
             * End the context.
             */
            } else if (areEqual(localName, R.string.xml1elemName_resource)) {
                mCurrentSecondLevelContext = DebateFormatXmlSecondLevelContext.NONE;
                mCurrentResourceRef = null;

            /**
             * <preptime-controlled length="7:00" firstperiod="string">
             * Set the first period and finish bell, then end the context.
             */
            } else if (areEqual(localName, R.string.xml1elemName_prepTimeControlledFormat)) {
                try {
                    mDfb.setFirstPeriodOfPrepTime(mCurrentFirstPeriod);
                } catch (DebateFormatBuilderException e) {
                    logXmlError(e);
                }

                try {
                    // If there isn't already a finish bell in this, add one and log the error.
                    if (!mDfb.hasFinishBellInPrepTimeControlled()) {
                        logXmlError(R.string.xml1error_prepTimeNoFinishBell);
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
            } else if (areEqual(localName, R.string.xml1elemName_speechFormat)) {
                try {
                    mDfb.setFirstPeriodOfSpeechFormat(mCurrentSpeechFormatRef, mCurrentFirstPeriod);
                } catch (DebateFormatBuilderException e) {
                    logXmlError(e);
                }

                try {
                    // If there isn't already a finish bell in this, add one and log the error.
                    if (!mDfb.hasFinishBellInSpeechFormat(mCurrentSpeechFormatRef)) {
                        logXmlError(R.string.xml1error_speechFormatNoFinishBell, mCurrentSpeechFormatRef);
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
            } else if (areEqual(localName, R.string.xml1elemName_speechesList)) {
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
            if (areEqual(localName, R.string.xml1elemName_root)) {

                String name = getValue(atts, R.string.xml1attrName_root_name);
                if (name == null) {
                    logXmlError(R.string.xml1error_rootNoName);
                    return;
                }

                mSchemaVersion = getValue(atts, R.string.xml1attrName_root_schemaVersion);
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
                logXmlError(R.string.xml1error_somethingOutsideRoot);
                return;
            }

            /** <resource ref="string">
             * Create a reference with the reference as specified in 'ref'.
             * Must not be inside a resource or speech format.
             * 'ref' is mandatory.
             */
            if (areEqual(localName, R.string.xml1elemName_resource)) {

                // 1. Get the reference string.
                String reference = getValue(atts, R.string.xml1attrName_common_ref);
                if (reference == null) {
                    logXmlError(R.string.xml1error_resourceNoRef);
                    return;
                }

                // 2. Check we're not inside any contexts.
                // If we are, ignore and reset all contexts.
                if (!assertNotInsideAnySecondLevelContextAndResetOtherwise()) {
                    logXmlError(R.string.xml1error_resourceInsideContext, reference,
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
            } else if (areEqual(localName, R.string.xml1elemName_prepTimeSimpleFormat)) {

                // 1. Check we're not inside any contexts.  If we are, ignore and reset all
                // contexts.
                if (!assertNotInsideAnySecondLevelContextAndResetOtherwise()) {
                    logXmlError(R.string.xml1error_prepTimeInsideContext,
                            getCurrentSecondLevelContext().toString());
                    return;
                }

                // 2. Get the length string, then convert it to seconds. Mandatory; exit on error.
                // Take note of it, in case bells use "finish" as their bell time.
                String lengthStr = getValue(atts, R.string.xml1attrName_controlledTimeLength);
                long length = 0;
                if (lengthStr == null) {
                    logXmlError(R.string.xml1error_prepTimeNoLength);
                    return;
                }
                try {
                    length = XmlUtilities.timeStr2Secs(lengthStr);
                } catch (NumberFormatException e) {
                    logXmlError(R.string.xml1error_prepTimeInvalidLength, lengthStr);
                    return;
                }

                // 3. Create the prep time.
                try {
                    mDfb.addPrepTimeSimple(length);
                } catch (DebateFormatBuilderException e) {
                    logXmlError(e);
                    return;
                }

            /**
             * <preptime-controlled length="7:00" firstperiod="string">
             * Create controlled prep time
             * 'length' is mandatory.
             * 'firstperiod' is optional.
             */
            } else if (areEqual(localName, R.string.xml1elemName_prepTimeControlledFormat)) {

                // 1. Check we're not inside any contexts.  If we are, ignore and reset all
                // contexts.
                if (!assertNotInsideAnySecondLevelContextAndResetOtherwise()) {
                    logXmlError(R.string.xml1error_prepTimeInsideContext,
                            getCurrentSecondLevelContext().toString());
                    return;
                }

                // 2. Get the length string, then convert it to seconds. Mandatory; exit on error.
                // Take note of it, in case bells use "finish" as their bell time.
                String lengthStr = getValue(atts, R.string.xml1attrName_controlledTimeLength);
                long length = 0;
                if (lengthStr == null) {
                    logXmlError(R.string.xml1error_prepTimeNoLength);
                    return;
                }
                try {
                    length = XmlUtilities.timeStr2Secs(lengthStr);
                } catch (NumberFormatException e) {
                    logXmlError(R.string.xml1error_prepTimeInvalidLength, lengthStr);
                    return;
                }

                // 3. Create the prep time.
                try {
                    mDfb.addPrepTimeControlled(length);
                } catch (DebateFormatBuilderException e) {
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
                        getValue(atts, R.string.xml1attrName_controlledTimeFirstPeriod);

            /** <speechtype ref="string" length="5:00" firstperiod="string" countdir="up">
             * Create a speech format.
             * 'ref' and 'length' are mandatory.
             * 'firstperiod' and 'countdir' are optional.
             */
            } else if (areEqual(localName, R.string.xml1elemName_speechFormat)) {

                // 1. Get the reference string. Mandatory; exit on error.
                String reference = getValue(atts, R.string.xml1attrName_common_ref);
                if (reference == null) {
                    logXmlError(R.string.xml1error_speechFormatNoRef);
                    return;
                }

                // 2. Check we're not inside any contexts.
                // If we are, ignore and reset all contexts.
                if (!assertNotInsideAnySecondLevelContextAndResetOtherwise()) {
                    logXmlError(R.string.xml1error_speechFormatInsideContext, reference,
                            getCurrentSecondLevelContext().toString());
                    return;
                }

                // 3. Get the length string, then convert it to seconds. Mandatory; exit on error.
                // Take note of it, in case bells use "finish" as their bell time.
                String lengthStr = getValue(atts, R.string.xml1attrName_controlledTimeLength);
                long length = 0;
                if (lengthStr == null) {
                    logXmlError(R.string.xml1error_speechFormatNoLength, reference);
                    return;
                }
                try {
                    length = XmlUtilities.timeStr2Secs(lengthStr);
                } catch (NumberFormatException e) {
                    logXmlError(R.string.xml1error_speechFormatInvalidLength, reference, lengthStr);
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
                String countdir = getValue(atts, R.string.xml1attrName_speechFormat_countDir);
                if (countdir != null) {
                    logXmlError(R.string.xml1error_speechFormatFoundCountDir);
                }

                // 7. Get the first period, and take note for later.
                // We'll deal with it as we exit this element, because the period is defined
                // inside the element.
                mCurrentFirstPeriod =
                        getValue(atts, R.string.xml1attrName_controlledTimeFirstPeriod);

            /** <bell time="1:00" number="1" nextperiod="#stay" sound="#default" pauseonbell="true">
             * Create a BellInfo.
             * This must be inside a resource or speech format.
             * 'time' is mandatory.
             * All other attributes are optional.
             */
            } else if (areEqual(localName, R.string.xml1elemName_bell)) {

                // 1. Get the bell time. Mandatory; exit on error.
                String timeStr = getValue(atts, R.string.xml1attrName_bell_time);;
                long time = 0;
                boolean atFinish = false;
                if (timeStr == null) {
                    logXmlError(R.string.xml1error_bellNoTime, getCurrentContextAndReferenceStr());
                    return;
                } else if (areEqualIgnoringCase(timeStr, R.string.xml1attrValue_bell_time_finish)) {
                    time = 0;  // will be overwritten addBellInfoToSpeechFormatAtFinish().
                    atFinish = true;
                } else {
                    try {
                        time = XmlUtilities.timeStr2Secs(timeStr);
                    } catch (NumberFormatException e) {
                        logXmlError(R.string.xml1error_bellInvalidTime, getCurrentContextAndReferenceStr(), timeStr);
                        return;
                    }
                }

                // 2. Get the number of times to play, or default to 1.
                String numberStr = getValue(atts, R.string.xml1attrName_bell_number);
                int number = 1;
                if (numberStr != null) {
                    try {
                        number = Integer.parseInt(numberStr);
                    } catch (NumberFormatException e) {
                        logXmlError(R.string.xml1error_bellInvalidNumber, getCurrentContextAndReferenceStr(), timeStr);
                    }
                }

                // 3. We now have enough information to create the bell.
                BellInfo bi = new BellInfo(time, number);

                // 4. Get the next period reference, or default to null
                // "#stay" means null (i.e. leave unchanged)
                String periodInfoRef = getValue(atts, R.string.xml1attrName_bell_nextPeriod);
                if (periodInfoRef != null)
                    if (areEqualIgnoringCase(periodInfoRef, R.string.xml1attrValue_common_stay))
                        periodInfoRef = null;

                // 5. Determine whether to pause on this bell
                String pauseOnBellStr = getValue(atts, R.string.xml1attrName_bell_pauseOnBell);
                if (pauseOnBellStr != null) {
                    if (areEqualIgnoringCase(pauseOnBellStr, R.string.xml1attrValue_common_true))
                        bi.setPauseOnBell(true);
                    else if (areEqualIgnoringCase(pauseOnBellStr, R.string.xml1attrValue_common_false))
                        bi.setPauseOnBell(false);
                    else
                        logXmlError(R.string.xml1error_bellInvalidPauseOnBell, getCurrentContextAndReferenceStr(), pauseOnBellStr);
                }

                // Finally, add the bell, but first check that the period info exists (and nullify
                // if it doesn't, so that the bell still gets added)
                try {
                    switch (getCurrentSecondLevelContext()) {
                    case RESOURCE:
                        if (mCurrentResourceRef == null) break;
                        if (periodInfoRef != null && !mDfb.hasPeriodInfoInResource(mCurrentResourceRef, periodInfoRef)) {
                            logXmlError(R.string.xml1error_resourcePeriodInfoNotFound, periodInfoRef, mCurrentResourceRef);
                            periodInfoRef = null;
                        }
                        mDfb.addBellInfoToResource(mCurrentResourceRef, bi, periodInfoRef);
                        break;
                    case PREP_TIME_CONTROLLED:
                        if (periodInfoRef != null && !mDfb.hasPeriodInfoInPrepTimeControlled(periodInfoRef)) {
                            logXmlError(R.string.xml1error_prepTimePeriodInfoNotFound, periodInfoRef, mCurrentResourceRef);
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
                            logXmlError(R.string.xml1error_speechFormatPeriodInfoNotFound, periodInfoRef, mCurrentSpeechFormatRef);
                            periodInfoRef = null;
                        }
                        if (atFinish)
                            mDfb.addBellInfoToSpeechFormatAtFinish(mCurrentSpeechFormatRef, bi, periodInfoRef);
                        else
                            mDfb.addBellInfoToSpeechFormat(mCurrentSpeechFormatRef, bi, periodInfoRef);
                        break;
                    default:
                        logXmlError(R.string.xml1error_bellOutsideContext);
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
            } else if (areEqual(localName, R.string.xml1elemName_period)){

                // 1. Get the reference. Mandatory; exit on error.
                String reference = getValue(atts, R.string.xml1attrName_common_ref);
                if (reference == null) {
                    logXmlError(R.string.xml1error_periodNoRef, getCurrentContextAndReferenceStr());
                    return;
                }

                // 2. Get the description (implicitly default to null)
                String description = getValue(atts, R.string.xml1attrName_period_desc);
                if (description != null) {
                    if (areEqualIgnoringCase(description, R.string.xml1attrValue_common_stay))
                        description = null;
                }

                // 3. Get the background colour (implicitly default to null)
                String bgcolorStr = getValue(atts, R.string.xml1attrName_period_bgcolor);
                Integer backgroundColor = null;
                if (bgcolorStr != null) {
                    if (areEqualIgnoringCase(bgcolorStr, R.string.xml1attrValue_common_stay))
                        backgroundColor = null;
                    else if (bgcolorStr.startsWith("#")) {
                        try {
                            // We need to do it via BigInteger in order for large unsigned 32-bit
                            // integers to be parsed as unsigned integers.
                            backgroundColor = new BigInteger(bgcolorStr.substring(1), 16).intValue();
                        } catch (NumberFormatException e) {
                            logXmlError(R.string.xml1error_periodInvalidColor, reference, bgcolorStr);
                        }
                    } else {
                        logXmlError(R.string.xml1error_periodInvalidColor, reference, bgcolorStr);
                    }
                }

                // 4. Get whether POIs are allowed (implicitly default to false)
                String poisAllowedStr = getValue(atts, R.string.xml1attrName_period_poisAllowed);
                boolean poisAllowed = false;
                if (poisAllowedStr != null) {
                    if (areEqualIgnoringCase(poisAllowedStr, R.string.xml1attrValue_common_true))
                        poisAllowed = true;
                    else if (areEqualIgnoringCase(poisAllowedStr, R.string.xml1attrValue_common_false))
                        poisAllowed = false;
                    else
                        logXmlError(R.string.xml1error_periodInvalidPoisAllowed, reference, poisAllowedStr);
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
                        logXmlError(R.string.xml1error_periodOutsideContext, reference);
                    }

                } catch (DebateFormatBuilderException e) {
                    logXmlError(e);
                }

            /** <include resource="reference">
             * Include a resource in a speech format.
             * This must be in a speech format.
             * 'resource' is mandatory.
             */
            } else if (areEqual(localName, R.string.xml1elemName_include)) {

                // 1. Get the resource reference. Mandatory; exit on error.
                String resourceRef = getValue(atts, R.string.xml1attrName_include_resource);
                if (resourceRef == null) {
                    logXmlError(R.string.xml1error_includeNoResource, getCurrentContextAndReferenceStr());
                    return;
                }

                // 2. Check we're inside a speech format
                if (getCurrentSecondLevelContext() != DebateFormatXmlSecondLevelContext.SPEECH_FORMAT) {
                    logXmlError(R.string.xml1error_includeOutsideSpeechFormat, resourceRef);
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
            } else if (areEqual(localName, R.string.xml1elemName_speechesList)) {
                if (!assertNotInsideAnySecondLevelContextAndResetOtherwise()) {
                    logXmlError(R.string.xml1error_speechesListInsideContext,
                            getCurrentSecondLevelContext().toString());
                    return;
                }

                mCurrentSecondLevelContext = DebateFormatXmlSecondLevelContext.SPEECHES_LIST;

            /**
             * <speech name="1st Affirmative" type="formatname">
             * Add a speech.
             * This must be inside the speeches context.
             */
            } else if (areEqual(localName, R.string.xml1elemName_speech)) {

                // 1. Get the speech name.
                String name = getValue(atts, R.string.xml1attrName_speech_name);
                if (name == null) {
                    logXmlError(R.string.xml1error_speechNoName);
                    return;
                }

                // 2. Get the speech format.
                String format = getValue(atts, R.string.xml1attrName_speech_format);
                if (format == null) {
                    logXmlError(R.string.xml1error_speechNoFormat, name);
                    return;
                }

                // 3. We must be inside the speeches list.
                if (getCurrentSecondLevelContext() != DebateFormatXmlSecondLevelContext.SPEECHES_LIST) {
                    logXmlError(R.string.xml1error_speechOutsideSpeechesList, name);
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
                return String.format("%s '%s'", getString(R.string.xml1elemName_resource), mCurrentResourceRef);
            } else if (mCurrentSpeechFormatRef != null) {
                return String.format("%s '%s'", getString(R.string.xml1elemName_speechFormat), mCurrentSpeechFormatRef);
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
    /* (non-Javadoc)
     * @see net.czlee.debatekeeper.debateformat.DebateFormatBuilderFromXml#buildDebateFromXml(java.io.InputStream)
     */
    @Override
    public DebateFormat buildDebateFromXml(InputStream is)
            throws IOException, SAXException {
        Xml.parse(is, Encoding.UTF_8, new DebateFormatXmlContentHandler());
        return mDfb.getDebateFormat();
    }

    /* (non-Javadoc)
     * @see net.czlee.debatekeeper.debateformat.DebateFormatBuilderFromXml#hasErrors()
     */
    @Override
    public boolean hasErrors() {
        return mErrorLog.size() > 0;
    }

    /* (non-Javadoc)
     * @see net.czlee.debatekeeper.debateformat.DebateFormatBuilderFromXml#isSchemaSupported()
     */
    @Override
    public boolean isSchemaSupported() {
        if (mSchemaVersion == null)
            return false; // either not built, or if it was built then probably the wrong schema
        try {
            return (XmlUtilities.compareSchemaVersions(mSchemaVersion, MAXIMUM_SCHEMA_VERSION) <= 0)
                    && (XmlUtilities.compareSchemaVersions(mSchemaVersion, MINIMUM_SCHEMA_VERSION) >= 0);
        } catch (IllegalSchemaVersionException e) {
            return false;
        }
    }

    @Override
    public boolean isSchemaTooNew() {
        if (mSchemaVersion == null)
            return false; // either not built, or if it was built then probably the wrong schema
        try {
            return XmlUtilities.compareSchemaVersions(mSchemaVersion, MAXIMUM_SCHEMA_VERSION) > 0;
        } catch (IllegalSchemaVersionException e) {
            return false;
        }
    }

    /* (non-Javadoc)
     * @see net.czlee.debatekeeper.debateformat.DebateFormatBuilderFromXml#getErrorLog()
     */
    @Override
    public ArrayList<String> getErrorLog() {
        return mErrorLog;
    }

    /* (non-Javadoc)
     * @see net.czlee.debatekeeper.debateformat.DebateFormatBuilderFromXml#getSchemaVersion()
     */
    @Override
    public String getSchemaVersion() {
        return mSchemaVersion;
    }

    /* (non-Javadoc)
     * @see net.czlee.debatekeeper.debateformat.DebateFormatBuilderFromXml#getSupportedSchemaVersion()
     */
    @Override
    public String getSupportedSchemaVersion() {
        return MAXIMUM_SCHEMA_VERSION;
    }

    //******************************************************************************************
    // Private methods
    //******************************************************************************************

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
