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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map.Entry;

import net.czlee.debatekeeper.R;
import net.czlee.debatekeeper.debateformat.DebateFormat.NoSuchFormatException;
import android.content.Context;

/**
 * DebateFormatBuilderForSchema1 provides mechanisms for building DebateFormats.
 *
 * <p>While "schema 1" refers to the XML schema, this class does not actually handle the XML.
 * It merely provides building methods for other classes to use to construct a {@link DebateFormat}.
 * The salient features of schema 1, that are not true for schema 2, are:</p>
 * <ul>
 * <li>Resources exist in schema 1; they are obsoleted in schema 2.</li>
 * <li>Period types in schema 1 must be specified in this file; for schema 2 formats can take
 * advantage of the "global" period types.</li>
 * <li>All period types in schema 1 are local to the file; in schema 2 period types are global
 * even if specified in a single debate format XML file.</li>
 * </ul>
 *
 * <p>DebateFormatBuilderForSchema1 takes raw information and uses it to build a {@link DebateFormat}.
 * It knows about "resources" and can refer the periods and resources by a string reference.</p>
 *
 * <p>Note that "resources" are a concept known only to {@link DebateFormatBuilderForSchema1}.
 * There is no concept of a "resource" in an instance of {@link DebateFormat}; this class handles
 * resources and integrates them into speeches.</p>
 *
 * <p>It is expected that other classes will be used to interface between a means of storing
 * information about debate formats, and this class.  For example, the {@link DebateFormatBuilderFromXmlForSchema1}
 * class interfaces between XML files and this class.</p>
 *
 * @author Chuan-Zheng Lee
 * @since  2012-06-02
 */
public class DebateFormatBuilderForSchema1 {

    protected enum State {
        ADDING_FORMATS, ADDING_SPEECHES, DONE
    }

    private final Context                          mContext;
    protected State                                mState = State.ADDING_FORMATS;
    protected Resource                             mResourceForAll;
    protected HashMap<String, Resource>            mResources;
    protected HashMap<String, SpeechFormatBuilder> mSpeechFormatBuilders;
    protected PrepTimeControlledBuilder            mPrepTimeControlledBuilder;
    protected DebateFormat                         mDebateFormatBeingBuilt;

    /**
     * Constructor.
     */
    public DebateFormatBuilderForSchema1(Context context) {
        super();
        mResourceForAll = null;
        mResources = new HashMap<String, Resource>();
        mSpeechFormatBuilders = new HashMap<String, SpeechFormatBuilder>();
        mDebateFormatBeingBuilt = new DebateFormat();
        mContext = context;
    }

    //******************************************************************************************
    // Public classes
    //******************************************************************************************

    /**
     * Thrown if there is a problem with the debate format (e.g. duplicates or non-existent
     * references)
     */
    public class DebateFormatBuilderException extends Exception {

        private static final long serialVersionUID = 6082009537966140387L;

        public DebateFormatBuilderException(int resId) {
            super(getString(resId));
        }

        public DebateFormatBuilderException(int resId, Object... formatArgs) {
            super(getString(resId, formatArgs));
        }

    }

    //******************************************************************************************
    // Private classes
    //******************************************************************************************

    /**
     * Base class for classes that can contain speech elements like
     * {@link PeriodInfo}s and {@link BellInfo}s.
     */
    private abstract class SpeechElementsContainer {

        protected final HashMap<String, PeriodInfo> mPeriodInfos;
        protected final ArrayList<BellInfo>         mBellInfos;

        public SpeechElementsContainer() {
            mPeriodInfos = new HashMap<String, PeriodInfo>();
            mBellInfos   = new ArrayList<BellInfo>();
        }

        /**
         * Adds a {@link PeriodInfo} to this <code>SpeechElementsContainer</code>.  The
         * <code>PeriodInfo</code> can then be referenced by {@link BellInfo}s when they are added
         * using <code>addBellInfo(BellInfo, String)</code>.
         * @param ref the short reference for the period
         * @param pi the <code>PeriodInfo</code> object
         * @throws DebateFormatBuilderException if a period with reference 'ref' already exists
         */
        public void addPeriodInfo(String ref, PeriodInfo pi)
                throws DebateFormatBuilderException {

            // Check for duplicate keys
            if (mPeriodInfos.containsKey(ref)) {
                throw new DebateFormatBuilderException(R.string.dfbError_periodInfoDuplicate, ref);
            }

            // If okay, then add
            mPeriodInfos.put(ref, pi);
        }

        /**
         * Adds a {@link BellInfo} to this <code>SpeechElementsContainer</code>.
         * @param bi the <code>BellInfo</code> object
         * @throws DebateFormatBuilderException if a bell with that time already exists
         */
        public void addBellInfo(BellInfo bi) throws DebateFormatBuilderException {
            checkBellInfo(bi);
            mBellInfos.add(bi);
        }

        /**
         * Adds a {@link BellInfo} to this <code>SpeechElementsContainer</code>, including an
         * associated {@link PeriodInfo} reference.
         * @param bi the <code>BellInfo</code> object
         * @param periodInfoRef the short reference for the next period associated with this bell,
         * can be '<code>null</code>' to leave unchanged.
         * @throws DebateFormatBuilderException if a bell with that time already exists, or if
         * the PeriodInfo referenced doesn't exist
         */
        public void addBellInfo(BellInfo bi, String periodInfoRef)
                throws DebateFormatBuilderException {

            checkBellInfo(bi);

            // Add the PeriodInfo, if applicable
            if (periodInfoRef != null) {
                PeriodInfo pi;
                pi = mPeriodInfos.get(periodInfoRef);
                if (pi == null) {
                    throw new DebateFormatBuilderException(
                            R.string.dfbError_periodInfoNotFound, periodInfoRef);
                }
                bi.setNextPeriodInfo(pi);
            }

            // If okay, then add
            mBellInfos.add(bi);
        }

        /**
         * Checks if a {@link PeriodInfo} with a given reference is in this container
         * @param ref the reference of the <code>PeriodInfo</code>
         * @return true if a {@link PeriodInfo} with that reference exists, false otherwise
         */
        public boolean hasPeriodInfo(String ref) {
            return mPeriodInfos.containsKey(ref);
        }

        /**
         * @return the HashMap of {@link PeriodInfo}s associated with this container
         */
        public HashMap<String, PeriodInfo> getPeriodInfos() {
            return mPeriodInfos;
        }

        /**
         * @return the ArrayList of {@link BellInfo}s associated with this container
         */
        public ArrayList<BellInfo> getBellInfos() {
            return mBellInfos;
        }

        /**
         * Checks that a {@link BellInfo} object is valid and consistent with other BellInfo objects
         * in this container.
         * @param bi the <code>BellInfo</code> object
         * @throws DebateFormatBuilderException if there is already a bell at the same time
         */
        protected void checkBellInfo(BellInfo bi) throws DebateFormatBuilderException {
            Iterator<BellInfo> biIterator = mBellInfos.iterator();
            long bellTime = bi.getBellTime();

            // Check for duplicate bells (bells with the same time)
            while (biIterator.hasNext()) {
                BellInfo checkBi = biIterator.next();
                if (checkBi.getBellTime() == bellTime) {
                    String timeStr = secsToText(bellTime);
                    throw new DebateFormatBuilderException(R.string.dfbError_bellDuplicate, timeStr);
                }
            }
        }

    }

    /**
     * A passive data class that holds the bells and periods associated with a
     * "resource" in an XML file (and/or any other allowable future format).
     */
    private class Resource extends SpeechElementsContainer {

        public Resource() {
            super();
        }

    }

    /**
     * A class that is used to build a {@link SpeechFormat} and {@link PrepTimeControlledFormat}.
     * {@link PeriodInfo}s, {@link BellInfo}s and other information can be added using the
     * <code>add*</code> and <code>set*</code> methods.  The child classes implement
     * getSpeechFormat or getPrepTimeControlledFormat to return the
     */
    private abstract class ControlledTimeBuilder extends SpeechElementsContainer {

        private   long                        mLength          = 0;
        protected PeriodInfo                  mFirstPeriodInfo = null;

        public ControlledTimeBuilder(long length) {
            super();
            this.mLength = length;
        }

        /**
         * @return the length of this speech
         */
        public long getLength() {
            return mLength;
        }

        /**
         * Sets the first period of this speech.  Must be called AFTER the {@link PeriodInfo} is
         * added. BEWARE! The <code>PeriodInfo</code> reference in question must ALREADY exist when
         * this method is called.  This probably means it's smartest to call this method last, even
         * though it might be specified in the user file first!
         * @param firstPeriodRef the short reference to the PeriodInfo object
         * @throws DebateFormatBuilderException if the PeriodInfo referenced doesn't exist
         */
        public void setFirstPeriod(String firstPeriodRef) throws DebateFormatBuilderException {
            PeriodInfo pi = mPeriodInfos.get(firstPeriodRef);
            if (pi == null) {
                throw new DebateFormatBuilderException(R.string.dfbError_periodInfoNotFound, firstPeriodRef);
            }
            mFirstPeriodInfo = pi;
        }

        /**
         * Adds the elements in a resource to this speech
         * @param res the {@link Resource} to add
         * @throws DebateFormatBuilderException if there are any problems with the resource
         */
        public void addResource(Resource res) throws DebateFormatBuilderException {
            // We need to add elements one by one so that they can be error-checked properly.

            // First add the periods
            Iterator<Entry<String, PeriodInfo>> piIterator = res.getPeriodInfos().entrySet().iterator();
            Entry<String, PeriodInfo> piEntry;
            while (piIterator.hasNext()) {
                piEntry = piIterator.next();
                this.addPeriodInfo(piEntry.getKey(), piEntry.getValue());
            }

            // Then add the bells
            Iterator<BellInfo> biIterator = res.getBellInfos().iterator();
            BellInfo bi;
            while (biIterator.hasNext()) {
                bi = biIterator.next();
                this.addBellInfo(bi);
            }
        }

        /**
         * @return <code>true</code> if a finish bell has been defined, <code>false</code> otherwise
         */
        public boolean hasFinishBell() {
            Iterator<BellInfo> biIterator = mBellInfos.iterator();
            while (biIterator.hasNext()) {
                BellInfo checkBi = biIterator.next();
                if (checkBi.getBellTime() == this.getLength())
                    return true;
            }
            return false;
        }

        /**
         * Checks that a {@link BellInfo} object is valid and consistent with other
         * <code>BellInfo</code> objects in this container.
         * @param bi the <code>BellInfo</code> object
         * @throws DebateFormatBuilderException if there is already a bell at the same time or if
         * the bell is after the finish time
         */
        @Override
        protected void checkBellInfo(BellInfo bi)
                throws DebateFormatBuilderException {

            super.checkBellInfo(bi);

            long bellTime = bi.getBellTime();

            // Check that the bell isn't after the finish time
            if (bellTime > mLength) {
                String timeStr = secsToText(bellTime);
                throw new DebateFormatBuilderException(R.string.dfbError_bellAfterFinishTime, timeStr);
            }

        }

    }

    private class SpeechFormatBuilder extends ControlledTimeBuilder {

        public SpeechFormatBuilder(long speechLength) {
            super(speechLength);
        }

        /**
         * Returns the assembled {@link SpeechFormat}
         * @return the assembled <code>SpeechFormat</code>
         */
        public SpeechFormat getSpeechFormat() {
            SpeechFormat sf = new SpeechFormat(getLength());
            if (mFirstPeriodInfo != null) {
                sf.setFirstPeriodInfo(mFirstPeriodInfo);
            }
            Iterator<BellInfo> biIterator = mBellInfos.iterator();
            while (biIterator.hasNext()) {
                BellInfo bi = biIterator.next();
                sf.addBellInfo(bi);
            }
            return sf;
        }

    }

    private class PrepTimeControlledBuilder extends ControlledTimeBuilder {

        public PrepTimeControlledBuilder(long length) {
            super(length);
        }

        public PrepTimeControlledFormat getPrepTimeControlledFormat() {
            PrepTimeControlledFormat ptcf = new PrepTimeControlledFormat(getLength());
            if (mFirstPeriodInfo != null) {
                ptcf.setFirstPeriodInfo(mFirstPeriodInfo);
            }
            Iterator<BellInfo> biIterator = mBellInfos.iterator();
            while (biIterator.hasNext()) {
                BellInfo bi = biIterator.next();
                ptcf.addBellInfo(bi);
            }
            return ptcf;
        }

    }

    //******************************************************************************************
    // Public methods
    //******************************************************************************************

    /**
     * Adds a new blank resource to the debate format being constructed by this builder.
     * After this is called, elements such as {@link PeriodInfo}s and {@link BellInfo}s can be
     * added using <code>addPeriodInfoToResource</code>, <code>addBellInfoToResource</code>, etc.
     * @param ref the short reference for the resource. If "#all", it becomes the common reference.
     * @throws DebateFormatBuilderException if a resource with reference 'ref' already exists
     * @throws IllegalStateException if the "adding speeches" state has already started
     */
    public void addNewResource(String ref) throws DebateFormatBuilderException {
        assertFormatsAreAddable();
        if (ref.equalsIgnoreCase(getString(R.string.xml1attrValue_resource_ref_common))) {
            if (mResourceForAll != null) {
                throw new DebateFormatBuilderException(R.string.dfbError_resourceDuplicate,
                        getString(R.string.xml1attrValue_resource_ref_common));
            }
            mResourceForAll = new Resource();
        } else if (!mResources.containsKey(ref)) {
            Resource res = new Resource();
            mResources.put(ref, res);
        } else {
            throw new DebateFormatBuilderException(R.string.dfbError_resourceDuplicate, ref);
        }
    }

    /**
     * Adds a new blank {@link SpeechFormat} to the debate format being constructed by this builder.
     * After this is called, elements such as {@link PeriodInfo}s and {@link BellInfo}s can be
     * added using <code>addPeriodInfoToSpeechFormat</code>, <code>addBellInfoToSpeechFormat</code>,
     * etc.  Resources can then be included in speech formats using <code>includeResource</code>.
     * The speech format will also include the "#all" resource if it has been added BEFORE this
     * speech format is added.
     * @param ref the short reference for the resource.
     * @param length the length in seconds of the speech
     * @throws DebateFormatBuilderException if a resource with reference 'ref' already exists
     * @throws IllegalStateException if the "adding speeches" state has already started
     */
    public void addNewSpeechFormat(String ref, long length) throws DebateFormatBuilderException {
        assertFormatsAreAddable();
        if (!mSpeechFormatBuilders.containsKey(ref)) {
            SpeechFormatBuilder sfb = new SpeechFormatBuilder(length);

            // Add the common resource, if there is one
            if (mResourceForAll != null) {
                sfb.addResource(mResourceForAll);
            }

            mSpeechFormatBuilders.put(ref, sfb);
        } else {
            throw new DebateFormatBuilderException(R.string.dfbError_speechFormatDuplicate, ref);
        }
    }

    /**
     * Adds simple prep time to the debate.  This can only be called once.
     * @param length the length in seconds of the prep time
     * @throws DebateFormatBuilderException if there is already prep time in this debate
     * format
     */
    public void addPrepTimeSimple(long length) throws DebateFormatBuilderException {
        if (mDebateFormatBeingBuilt.hasPrepFormat() || mPrepTimeControlledBuilder != null)
            throw new DebateFormatBuilderException(R.string.dfbError_multiplePrepTimes);
        PrepTimeSimpleFormat ptsf = new PrepTimeSimpleFormat(length);
        mDebateFormatBeingBuilt.setPrepFormat(ptsf);
    }

    /**
     * Adds controlled prep time to the debate.  This can only be called once.
     * @param length the length in seconds of the prep time
     * @throws DebateFormatBuilderException if there is already prep time in this debate
     * format
     */
    public void addPrepTimeControlled(long length) throws DebateFormatBuilderException {
        if (mDebateFormatBeingBuilt.hasPrepFormat() || mPrepTimeControlledBuilder != null)
            throw new DebateFormatBuilderException(R.string.dfbError_multiplePrepTimes);
        mPrepTimeControlledBuilder = new PrepTimeControlledBuilder(length);
    }

    /**
     * Adds a speech to the debate.  Once this is called, calls that modify the speech formats
     * are illegal.
     * @param name the name of the speech, e.g. "1st Affirmative", "Prime Minister"
     * @param formatRef the short reference for the speech format
     * @throws DebateFormatBuilderException if there is no speech format with that reference
     * @throws IllegalStateException if getDebate() has already been called
     */
    public void addSpeech(String name, String formatRef) throws DebateFormatBuilderException {

        // Check the state
        if (mState == State.DONE)
            throw new IllegalStateException("getDebateFormat() has already been called");

        // If this is the first call, build the speeches
        if (mState == State.ADDING_FORMATS)
            this.buildSpeeches();

        // Now, add the speech.
        // (If mState == State.ADDING_SPEECHES, this is the only thing that happens.)
        try {
            mDebateFormatBeingBuilt.addSpeech(name, formatRef);
        } catch (NoSuchFormatException e) {
            throw new DebateFormatBuilderException(
                    R.string.dfbError_addSpeechSpeechFormatNotFound, formatRef, name);
        }

    }

    /**
     * Adds a new {@link PeriodInfo} to a resource in this builder
     * @param resourceRef the short reference for the resource ("#all" for the common resource)
     * @param periodInfoRef the short reference for the period
     * @param pi the <code>PeriodInfo</code> object
     * @throws DebateFormatBuilderException if there is no resource with reference 'resourceRef' or
     * if 'periodInfoRef' is a duplicate reference
     * @throws IllegalStateException if the "adding speeches" state has already started
     */
    public void addPeriodInfoToResource(String resourceRef, String periodInfoRef, PeriodInfo pi)
            throws DebateFormatBuilderException {

        assertFormatsAreAddable();
        Resource res = getResource(resourceRef);
        addPeriodInfo(res, periodInfoRef, pi);
    }

    /**
     * Adds a new {@link PeriodInfo} to a speech format in this builder
     * @param speechRef the short reference for the speech
     * @param periodInfoRef the short reference for the period
     * @param pi the <code>PeriodInfo</code> object
     * @throws DebateFormatBuilderException if there is no speech with reference 'speechRef' or if
     * 'periodInfoRef' is a duplicate reference
     * @throws IllegalStateException if the "adding speeches" state has already started
     */
    public void addPeriodInfoToSpeechFormat(String speechRef, String periodInfoRef, PeriodInfo pi)
            throws DebateFormatBuilderException {

        assertFormatsAreAddable();
        SpeechFormatBuilder sfb = getSpeechFormatBuilder(speechRef);
        addPeriodInfo(sfb, periodInfoRef, pi);
    }

    /**
     * Adds a new {@link PeriodInfo} to the prep time in this builder
     * @param periodInfoRef the short reference for the period
     * @param pi the <code>PeriodInfo</code> object
     * @throws DebateFormatBuilderException if there is no controlled prep time or 'periodInfoRef'
     * is a duplicate reference
     * @throws IllegalStateException if the "adding speeches" state has already started
     */
    public void addPeriodInfoToPrepTime(String periodInfoRef, PeriodInfo pi)
            throws DebateFormatBuilderException {

        assertPrepTimeIsControlled();
        addPeriodInfo(mPrepTimeControlledBuilder, periodInfoRef, pi);
    }

    /**
     * Adds a new {@link BellInfo} to a resource in this builder
     * @param resourceRef the short reference for the resource
     * @param bi the <code>BellInfo</code> object
     * @param periodInfoRef the short reference for the next period associated with the bell, can
     * be <code>null</code> to leave the existing next period in the 'bi' unchanged
     * @throws DebateFormatBuilderException if there is no resource with reference 'resourceRef' or
     * no such period with reference 'periodInfoRef' or if there is already a bell at the same time
     * @throws IllegalStateException if the "adding speeches" state has already started
     */
    public void addBellInfoToResource(String resourceRef, BellInfo bi, String periodInfoRef)
            throws DebateFormatBuilderException {

        assertFormatsAreAddable();
        Resource res = getResource(resourceRef);
        addBellInfo(res, bi, periodInfoRef);
    }

    /**
     * Adds a new {@link BellInfo} to a speech format in this builder
     * @param speechRef the short reference for the speech
     * @param bi the <code>BellInfo</code> object
     * @param periodInfoRef the short reference for the next period associated with the bell, can
     * be <code>null</code> to leave the existing next period in the 'bi' unchanged
     * @throws DebateFormatBuilderException if there is no speech with reference 'speechRef' or no
     * period with reference 'periodInfoRef' or if there is already a bell at the same time
     * @throws IllegalStateException if the "adding speeches" state has already started
     */
    public void addBellInfoToSpeechFormat(String speechRef, BellInfo bi, String periodInfoRef)
            throws DebateFormatBuilderException {

        assertFormatsAreAddable();
        SpeechFormatBuilder sfb = getSpeechFormatBuilder(speechRef);
        addBellInfo(sfb, bi, periodInfoRef);
    }

    /**
     * Adds a new {@link BellInfo} to a speech format in this builder, but replaces the bell
     * time with the finish time of that speech format.
     * @param speechRef the short reference for the speech
     * @param bi the <code>BellInfo</code> object. The bell time of this bell doesn't matter
     * because it will be overwritten with the finish time of this speech.
     * @param periodInfoRef the short reference for the next period associated with the bell, can
     * be <code>null</code> to leave the existing next period in the 'bi' unchanged
     * @throws DebateFormatBuilderException if there is no speech with reference 'speechRef' or no
     * period with reference 'periodInfoRef' or if there is already a bell at the same time
     * @throws IllegalStateException if the "adding speeches" state has already started
     */
    public void addBellInfoToSpeechFormatAtFinish(String speechRef, BellInfo bi, String periodInfoRef)
            throws DebateFormatBuilderException {
        SpeechFormatBuilder sfb = getSpeechFormatBuilder(speechRef);
        bi.setBellTime(sfb.getLength());
        addBellInfoToSpeechFormat(speechRef, bi, periodInfoRef);
    }

    /**
     * Adds a new {@link BellInfo} to the controlled prep time in this builder
     * @param bi the <code>BellInfo</code> object
     * @param periodInfoRef the short reference for the next period associated with the bell, can
     * be <code>null</code> to leave the existing next period in the 'bi' unchanged
     * @throws DebateFormatBuilderException if there is no controlled prep time or no
     * period with reference 'periodInfoRef' or if there is already a bell at the same time
     * @throws IllegalStateException if the "adding speeches" state has already started
     */
    public void addBellInfoToPrepTime(BellInfo bi, String periodInfoRef)
            throws DebateFormatBuilderException {
        assertPrepTimeIsControlled();
        addBellInfo(mPrepTimeControlledBuilder, bi, periodInfoRef);
    }

    /**
     * Adds a new {@link BellInfo} to the controlled prep time in this builder, but replaces the bell
     * time with the finish time of that controlled prep time.
     * @param bi the <code>BellInfo</code> object. The bell time of this bell doesn't matter
     * because it will be overwritten with the finish time of this prep time.
     * @param periodInfoRef the short reference for the next period associated with the bell, can
     * be <code>null</code> to leave the existing next period in the 'bi' unchanged
     * @throws DebateFormatBuilderException if prep time is not controlled
     */
    public void addBellInfoToPrepTimeAtFinish(BellInfo bi, String periodInfoRef)
            throws DebateFormatBuilderException {
        bi.setBellTime(mPrepTimeControlledBuilder.getLength());
        addBellInfoToPrepTime(bi, periodInfoRef);
    }

    /**
     * Adds the elements of a resource (that has already been added to this builder) to a speech.
     * @param speechRef the short reference for the speech
     * @param resourceRef the short reference for the reference
     * @throws DebateFormatBuilderException if there is no speech with reference 'speechRef' or no
     * resource with reference 'resourceRef' or if there are any problems with the resource
     * @throws IllegalStateException if the "adding speeches" state has already started
     */
    public void includeResource(String speechRef, String resourceRef)
            throws DebateFormatBuilderException {

        assertFormatsAreAddable();
        SpeechFormatBuilder sfb = getSpeechFormatBuilder(speechRef);
        Resource res = getResource(resourceRef);
        sfb.addResource(res);
    }

    /**
     * Sets the first period of a speech format.
     * @param speechRef the short reference for the speech
     * @param firstPeriodRef the short reference for the first period
     * @throws DebateFormatBuilderException if there is no speech with reference 'speechRef' or no
     * period with reference 'firstPeriodRef'
     * @throws IllegalStateException if the "adding speeches" state has already started
     */
    public void setFirstPeriodOfSpeechFormat(String speechRef, String firstPeriodRef)
            throws DebateFormatBuilderException {

        assertFormatsAreAddable();
        SpeechFormatBuilder sfb = getSpeechFormatBuilder(speechRef);
        sfb.setFirstPeriod(firstPeriodRef);
    }

    /**
     * Sets the first period of prep time.
     * @param firstPeriodRef the short reference for the prep time
     * @throws DebateFormatBuilderException if prep time is not controlled
     */
    public void setFirstPeriodOfPrepTime(String firstPeriodRef)
            throws DebateFormatBuilderException {
        assertPrepTimeIsControlled();
        mPrepTimeControlledBuilder.setFirstPeriod(firstPeriodRef);
    }

    /**
     * Sets the name of the debate format being built
     * @param name the name of this debate format
     */
    public void setDebateFormatName(String name) {
        mDebateFormatBeingBuilt.setName(name);
    }

    /**
     * Checks if a {@link PeriodInfo} with a given reference has been added to a resource
     * @param resourceRef the name of the {@link Resource} to check
     * @param periodInfoRef the name of the <code>PeriodInfo</code> to check
     * @return true if a <code>PeriodInfo</code> with that name has been added, false otherwise
     * @throws DebateFormatBuilderException if there is no resource with reference 'resourceRef'
     */
    public boolean hasPeriodInfoInResource(String resourceRef, String periodInfoRef)
            throws DebateFormatBuilderException {
        Resource res = getResource(resourceRef);
        return res.hasPeriodInfo(periodInfoRef);
    }

    /**
     * Checks if a speech format has a finish bell.
     * @param speechRef the name of the {@link SpeechFormat} to check
     * @return <code>true</code> if the speech has a finish bell, <code>false</code> otherwise
     * @throws DebateFormatBuilderException if there is no speech format with reference 'speechRef'
     */
    public boolean hasFinishBellInSpeechFormat(String speechRef)
            throws DebateFormatBuilderException {
        SpeechFormatBuilder sfb = getSpeechFormatBuilder(speechRef);
        return sfb.hasFinishBell();
    }

    /**
     * Checks if a {@link PeriodInfo} with a given reference has been added to a speech format
     * @param speechRef the name of the {@link SpeechFormat} to check
     * @param periodInfoRef the name of the <code>PeriodInfo</code> to check
     * @return <code>true</code> if a <code>PeriodInfo</code> with that name has been added,
     * <code>false</code> otherwise
     * @throws DebateFormatBuilderException if there is no speech format with reference 'speechRef'
     */
    public boolean hasPeriodInfoInSpeechFormat(String speechRef, String periodInfoRef)
            throws DebateFormatBuilderException {
        SpeechFormatBuilder sfb = getSpeechFormatBuilder(speechRef);
        return sfb.hasPeriodInfo(periodInfoRef);
    }

    /**
     * Checks if the controlled prep time has a finish bell.
     * @param speechRef the name of the {@link SpeechFormat} to check
     * @return <code>true</code> if the prep time has a finish bell, <code>false</code> otherwise
     * @throws DebateFormatBuilderException if prep time is not controlled
     */
    public boolean hasFinishBellInPrepTimeControlled() throws DebateFormatBuilderException {
        if (mPrepTimeControlledBuilder == null)
            throw new DebateFormatBuilderException(R.string.dfbError_prepTimeNotControlled);
        return mPrepTimeControlledBuilder.hasFinishBell();
    }

    /**
     * Checks if a {@link PeriodInfo} with a given reference has been added to the prep time
     * @param periodInfoRef the name of the <code>PeriodInfo</code> to check
     * @return <code>true</code> if a <code>PeriodInfo</code> with that name has been added,
     * <code>false</code> otherwise
     * @throws DebateFormatBuilderException if prep time is not controlled
     */
    public boolean hasPeriodInfoInPrepTimeControlled(String periodInfoRef)
            throws DebateFormatBuilderException {
        if (mPrepTimeControlledBuilder == null)
            throw new DebateFormatBuilderException(R.string.dfbError_prepTimeNotControlled);
        return mPrepTimeControlledBuilder.hasPeriodInfo(periodInfoRef);
    }

    /**
     * Gets the name of the debate format being built
     * @return the name of this debate format
     */
    public String getDebateFormatName() {
        return mDebateFormatBeingBuilt.getName();
    }

    /**
     * Returns the assembled {@link DebateFormat}.  Calls to any other method than this one, after
     * this has been called once, are illegal.
     * @return the assembled <code>DebateFormat</code>
     * @throws IllegalStateException if there are no speeches added when this is called
     */
    public DebateFormat getDebateFormat() {
        if (mDebateFormatBeingBuilt.numberOfSpeeches() == 0) {
            throw new IllegalStateException("There are no speeches in this format!");
        }
        mState = State.DONE;
        return mDebateFormatBeingBuilt;
    }

    //******************************************************************************************
    // Private methods
    //******************************************************************************************

    private void assertFormatsAreAddable() {
        if (mState != State.ADDING_FORMATS) {
            throw new IllegalStateException("You can't modify speech formats after addSpeech() is called");
        }
    }

    private void assertPrepTimeIsControlled() throws DebateFormatBuilderException {
        assertFormatsAreAddable();
        if (mState != State.ADDING_FORMATS) {
            throw new IllegalStateException("You can't modify prep time after addSpeech() is called");
        }
        if (mPrepTimeControlledBuilder == null)
            throw new DebateFormatBuilderException(R.string.dfbError_prepTimeNotControlled);
    }

    /**
     * @param ref the short reference for the resource
     * @return the {@link Resource}
     * @throws DebateFormatBuilderException if there is no resource with that reference
     */
    private Resource getResource(String ref) throws DebateFormatBuilderException {
        Resource res;
        if (ref.equalsIgnoreCase(mContext.getString(R.string.xml1attrValue_resource_ref_common)))
            res = mResourceForAll;
        else res = mResources.get(ref);
        if (res == null) {
            throw new DebateFormatBuilderException(R.string.dfbError_resourceNotFound, ref);
        }
        return res;
    }

    /**
     * @param ref the short reference for the speech format
     * @return the {@link SpeechFormatBuilder}
     * @throws DebateFormatBuilderException if there is no speech format with that reference
     */
    private SpeechFormatBuilder getSpeechFormatBuilder(String ref)
            throws DebateFormatBuilderException {

        SpeechFormatBuilder sfb = mSpeechFormatBuilders.get(ref);
        if (sfb == null) {
            throw new DebateFormatBuilderException(R.string.dfbError_speechFormatNotFound, ref);
        }
        return sfb;
    }

    /**
     * Adds a {@link PeriodInfo} to a {@link SpeechElementsContainer}
     * @param sec the <code>SpeechElementsContainer</code>
     * @param ref the short reference for the PeriodInfo
     * @param pi the PeriodInfo object
     * @throws DebateFormatBuilderException if a period with that reference already exists
     */
    private void addPeriodInfo(SpeechElementsContainer sec, String ref, PeriodInfo pi)
            throws DebateFormatBuilderException {

        sec.addPeriodInfo(ref, pi);
    }

    /**
     * Adds a {@link BellInfo} to a {@link SpeechElementsContainer}
     * @param sec the <code>SpeechElementsContainer</code>
     * @param bi the <code>BellInfo</code> object
     * @param periodInfoRef the short reference for the next period associated with the bell, can
     * be null to leave the existing next period in the 'bi' unchanged
     * @throws DebateFormatBuilderException if a bell at that time already exists, or if there is
     * no period with that reference
     */
    private void addBellInfo(SpeechElementsContainer sec, BellInfo bi, String periodInfoRef)
            throws DebateFormatBuilderException {

        sec.addBellInfo(bi, periodInfoRef);
    }

    private static String secsToText(long time) {
        return String.format("%02d:%02d", time / 60, time % 60);
    }

    /**
     * Builds the speech formats
     * @throws IllegalStateException if buildSpeeches() has been called before
     */
    private void buildSpeeches() {

        // We should only do this once, i.e. formats must be "addable" when we do this
        assertFormatsAreAddable();

        // We now change the state so that formats are no longer addable
        mState = State.ADDING_SPEECHES;

        Iterator<Entry<String, SpeechFormatBuilder>> sfbIterator = mSpeechFormatBuilders.entrySet().iterator();

        while (sfbIterator.hasNext()) {
            Entry<String, SpeechFormatBuilder> sfbEntry;
            String name;
            SpeechFormatBuilder sfb;
            SpeechFormat sf;

            sfbEntry = sfbIterator.next();
            name     = sfbEntry.getKey();
            sfb      = sfbEntry.getValue();
            sf       = sfb.getSpeechFormat();

            mDebateFormatBeingBuilt.addSpeechFormat(name, sf);
        }

        // Also, build the prep time
        if (mPrepTimeControlledBuilder != null) {
            PrepTimeControlledFormat ptcf = mPrepTimeControlledBuilder.getPrepTimeControlledFormat();
            mDebateFormatBeingBuilt.setPrepFormat(ptcf);
        }
    }

    private String getString(int resId) {
        return mContext.getString(resId);
    }

    private String getString(int resId, Object... formatArgs) {
        return mContext.getString(resId, formatArgs);
    }

}
