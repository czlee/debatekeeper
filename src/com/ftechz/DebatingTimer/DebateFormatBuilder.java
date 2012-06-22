/**
 *
 */
package com.ftechz.DebatingTimer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map.Entry;

import android.content.Context;

import com.ftechz.DebatingTimer.DebateFormat.NoSuchFormatException;

/**
 * DebateFormatBuilder provides mechanisms for building DebateFormats.
 *
 * DebateFormatBuilder takes raw information and uses it to build a {@link DebateFormat}.  It knows
 * about "resources" and can refer the periods and resources by a string reference.
 *
 * DebateFormatBuilder may be used directly or extended to more specific cases, e.g. an XML file
 * parser.
 *
 * @author Chuan-Zheng Lee
 * @since  2012-06-02
 */
public class DebateFormatBuilder {

    protected enum State {
        ADDING_FORMATS, ADDING_SPEECHES, DONE
    }

    private final Context                          mContext;
    protected State                                mState = State.ADDING_FORMATS;
    protected Resource                             mResourceForAll;
    protected HashMap<String, Resource>            mResources;
    protected HashMap<String, SpeechFormatBuilder> mSpeechFormatBuilders;
    protected DebateFormat                         mDebateFormatBeingBuilt;

    //******************************************************************************************
    // Public classes
    //******************************************************************************************

    /**
     * Thrown if there is a problem with the debate format (e.g. duplicates or non-existent
     * references)
     */
    public class DebateFormatBuilderException extends Exception {

        private static final long serialVersionUID = 6082009537966140387L;

        public DebateFormatBuilderException(String detailMessage) {
            super(detailMessage);
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
                throw new DebateFormatBuilderException(
                        getString(R.string.DfbErrorPeriodInfoDuplicate, ref));
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
                            getString(R.string.DfbErrorPeriodInfoNotFound, periodInfoRef));
                }
                bi.setNextPeriodInfo(pi);
            }

            // If okay, then add
            mBellInfos.add(bi);
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
            BellInfo checkBi;
            long bellTime = bi.getBellTime();

            // Check for duplicate bells (bells with the same time)
            while (biIterator.hasNext()) {
                checkBi = biIterator.next();
                if (checkBi.getBellTime() == bellTime) {
                    String timeStr = secsToText(bellTime);
                    throw new DebateFormatBuilderException(
                            getString(R.string.DfbErrorBellDuplicate, timeStr));
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
     * A class that is used to build a {@link SpeechFormat}.  {@link PeriodInfo}s, {@link BellInfo}s
     * and other information can be added using the <code>add*</code> and <code>set*</code> methods,
     * then <code>getSpeechFormat()</code> is used to get the built <code>SpeechFormat</code>.
     */
    private class SpeechFormatBuilder extends SpeechElementsContainer {

        private long                        mSpeechLength     = 0;
        private SpeechFormat.CountDirection mCountDirection  = null;
        private PeriodInfo                  mFirstPeriodInfo = null;

        public SpeechFormatBuilder(long speechLength) {
            super();
            this.mSpeechLength = speechLength;
        }

        /**
         * @return the length of this speech
         */
        public long getSpeechLength() {
            return mSpeechLength;
        }

        /**
         * Sets the count direction of this speech.
         * @param countDirection the new count direction
         */
        public void setCountDirection(SpeechFormat.CountDirection countDirection) {
            this.mCountDirection = countDirection;
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
            PeriodInfo pi;
            pi = mPeriodInfos.get(firstPeriodRef);
            if (pi == null) {
                throw new DebateFormatBuilderException(
                        getString(R.string.DfbErrorPeriodInfoNotFound, firstPeriodRef));
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
         * Returns the assembled {@link SpeechFormat}
         * @return the assembled <code>SpeechFormat</code>
         */
        public SpeechFormat getSpeechFormat() {
            SpeechFormat sf = new SpeechFormat(mSpeechLength);
            if (mCountDirection != null) {
                sf.setCountDirection(mCountDirection);
            }
            if (mFirstPeriodInfo != null) {
                sf.setFirstPeriodInfo(mFirstPeriodInfo);
            }
            Iterator<BellInfo> biIterator = mBellInfos.iterator();
            BellInfo bi;
            while (biIterator.hasNext()) {
                bi = biIterator.next();
                sf.addBellInfo(bi);
            }
            return sf;
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
            if (bellTime > mSpeechLength) {
                String timeStr = secsToText(bellTime);
                throw new DebateFormatBuilderException(
                        getString(R.string.DfbErrorBellAfterFinishTime, timeStr));
            }

        }

    }

    //******************************************************************************************
    // Public methods
    //******************************************************************************************

    /**
     * Constructor.
     */
    public DebateFormatBuilder(Context context) {
        super();
        mResourceForAll = null;
        mResources = new HashMap<String, Resource>();
        mSpeechFormatBuilders = new HashMap<String, SpeechFormatBuilder>();
        mDebateFormatBeingBuilt = new DebateFormat();
        mContext = context;
    }

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
        if (ref.equalsIgnoreCase(getString(R.string.XmlAttrNameResourceRefCommon))) {
            if (mResourceForAll != null) {
                throw new DebateFormatBuilderException(getString(
                        R.string.DfbErrorResourceDuplicate,
                        getString(R.string.XmlAttrNameResourceRefCommon)));
            }
            mResourceForAll = new Resource();
        } else if (!mResources.containsKey(ref)) {
            Resource res = new Resource();
            mResources.put(ref, res);
        } else {
            throw new DebateFormatBuilderException(
                    getString(R.string.DfbErrorResourceDuplicate, ref));
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
            throw new DebateFormatBuilderException(
                    getString(R.string.DfbErrorSpeechFormatDuplicate, ref));
        }
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
                    getString(R.string.DfbErrorSpeechFormatNotFound, formatRef));
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
        bi.setBellTime(sfb.getSpeechLength());
        addBellInfoToSpeechFormat(speechRef, bi, periodInfoRef);
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
     * Sets the count direction of a speech format.
     * @param speechRef the short reference for the speech
     * @param countDir the new count direction
     * @throws DebateFormatBuilderException if there is no speech with reference 'speechRef'
     * @throws IllegalStateException if the "adding speeches" state has already started
     */
    public void setCountDirection(String speechRef, SpeechFormat.CountDirection countDir)
            throws DebateFormatBuilderException {

        assertFormatsAreAddable();
        SpeechFormatBuilder sfb = getSpeechFormatBuilder(speechRef);
        sfb.setCountDirection(countDir);
    }

    /**
     * Sets the first period of a speech format.
     * @param speechRef the short reference for the speech
     * @param firstPeriodRef the short reference for the first period
     * @throws DebateFormatBuilderException if there is no speech with reference 'speechRef' or no
     * period with reference 'firstPeriodRef'
     * @throws IllegalStateException if the "adding speeches" state has already started
     */
    public void setFirstPeriod(String speechRef, String firstPeriodRef)
            throws DebateFormatBuilderException {

        assertFormatsAreAddable();
        SpeechFormatBuilder sfb = getSpeechFormatBuilder(speechRef);
        sfb.setFirstPeriod(firstPeriodRef);
    }

    /**
     * Sets the name of the debate format being built
     * @param name the name of this debate format
     */
    public void setDebateFormatName(String name) {
        mDebateFormatBeingBuilt.setName(name);
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

    /**
     * @param ref the short reference for the resource
     * @return the {@link Resource}
     * @throws DebateFormatBuilderException if there is no resource with that reference
     */
    private Resource getResource(String ref) throws DebateFormatBuilderException {
        Resource res;
        if (ref.equalsIgnoreCase(mContext.getString(R.string.XmlAttrNameResourceRefCommon)))
            res = mResourceForAll;
        else res = mResources.get(ref);
        if (res == null) {
            throw new DebateFormatBuilderException(
                    getString(R.string.DfbErrorResourceNotFound, ref));
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
            throw new DebateFormatBuilderException(
                    getString(R.string.DfbErrorSpeechFormatNotFound, ref));
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
    }

    private String getString(int resId) {
        return mContext.getString(resId);
    }

    private String getString(int resId, Object... formatArgs) {
        return mContext.getString(resId, formatArgs);
    }

}
