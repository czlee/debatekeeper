/*
 * Copyright (C) 2013 Chuan-Zheng Lee
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

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.format.DateUtils;
import android.util.Log;

import androidx.annotation.NonNull;

import net.czlee.debatekeeper.debateformat.BellInfo;
import net.czlee.debatekeeper.debateformat.PrepTimeSimpleFormat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.ListIterator;
import java.util.Locale;

/**
 * PrepTimeBellsManager manages the user-defined bell times for preparation time.
 * It is used by {@link PrepTimeSimpleFormat}.
 *
 * It is responsible for storing information about the user-defined settings for
 * preparation bell times, and for returning a list of bell times based on those
 * settings given a prep time length.  For example, if there is a single bell
 * always five minutes from the end (and two bells at the end), then this would
 * return:
 *   - If prep time is 5 minutes,  bells at: finish (only)
 *   - If prep time is 10 minutes, bells at: 5, finish
 *   - If prep time is 20 minutes, bells at: 15, finish
 *
 * There are three types of user-defined bells:
 *   (1) Time from the start
 *   (2) Time from the end
 *   (3) Time as a proportion of total time (e.g. halfway)
 *
 * @author Chuan-Zheng Lee
 * @since  2013-01-27
 */
public class PrepTimeBellsManager {

    private static final String TAG = "PrepTimeBellsManager";

    public  static final String KEY_TYPE                         = "type";
    public  static final String KEY_TIME                         = "time";
    public  static final String KEY_PROPORTION                   = "proportion";
    public  static final String VALUE_TYPE_START                 = "start";
    public  static final String VALUE_TYPE_FINISH                = "finish";
    public  static final String VALUE_TYPE_PROPORTIONAL          = "proportional";
    public  static final String PREP_TIME_BELLS_PREFERENCES_NAME = "prep_time_bells";
    private static final String KEY_TOTAL_NUMBER_OF_BELLS        = "totalBells";

    private final ArrayList<PrepTimeBellSpec> mBellSpecs = new ArrayList<>();

    private final Context mContext;

    public PrepTimeBellsManager(Context context) {
        mContext = context;
    }

    //******************************************************************************************
    // Private classes
    //******************************************************************************************

    private interface PrepTimeBellSpec {

        /**
         * @param length the length of the prep time
         * @return A {@link BellInfo} object, or <code>null</code> if this bell is
         * incompatible with the length given (e.g. it would occur after the end
         * of the prep time given).
         */
        BellInfo getBell(long length);

        /**
         * Saves this bell specification to a bundle.
         * @param bundle the Bundle to save to
         */
        void saveToBundle(Bundle bundle);

        /**
         * Saves this bell specification to a shared preferences file.
         * @param index the index of this PrepTimeBellSpec in the list
         */
        void saveToPreferences(SharedPreferences.Editor editor, int index);

        /**
         * @return <code>true</code> if the bell is <i>always</i> at the finish; <code>false</code>
         * otherwise. Note that a bell that <i>can</i> be, but is not <i>always</i> at the finish
         * (<i>e.g.</i> a start bell whose time coincides with the prep time length) returns <code>false</code>.
         */
        boolean isAtFinish();

    }

    private abstract static class PrepTimeBellByTime implements PrepTimeBellSpec {

        protected final long time;

        /**
         * Constructor from a {@link Bundle}.
         * @param bundle the Bundle.
         * @throws PrepTimeBellWrongTypeException if the type specified for the given index in
         * the given {@link SharedPreferences} file does not match the expected type.
         * @throws PrepTimeBellConstructorException if there is some other problem with the preferences
         */
        public PrepTimeBellByTime(Bundle bundle) throws PrepTimeBellConstructorException {
            String type = bundle.getString(KEY_TYPE);
            if (type == null)
                throw new PrepTimeBellConstructorException("No type found");
            if (!type.equals(getValueType()))
                throw new PrepTimeBellWrongTypeException(getValueType(), type);
            if (!bundle.containsKey(KEY_TIME))
                throw new PrepTimeBellConstructorException("No time found");
            this.time = bundle.getLong(KEY_TIME, 0);
        }

        public PrepTimeBellByTime(long time) {
            this.time = time;
        }
        /**
         * Constructor from a {@link SharedPreferences} file.
         * @param prefs the {@link SharedPreferences} object
         * @param index the index of the desired bell specification in <code>prefs</code>.
         * @throws PrepTimeBellWrongTypeException if the type specified for the given index in
         * the given {@link SharedPreferences} file does not match the expected type.
         * @throws PrepTimeBellConstructorException if there is some other problem with the preferences
         */
        public PrepTimeBellByTime(SharedPreferences prefs, int index) throws PrepTimeBellConstructorException {
            String type = prefs.getString(index + KEY_TYPE, "");
            if (!type.equals(getValueType()))
                throw new PrepTimeBellWrongTypeException(index, getValueType(), type);
            if (!prefs.contains(index + KEY_TIME))
                throw new PrepTimeBellConstructorException(index, "No time found");
            this.time = prefs.getLong(index + KEY_TIME, 0);
        }

        @Override
        public void saveToBundle(Bundle bundle) {
            bundle.putString(KEY_TYPE, getValueType());
            bundle.putLong(KEY_TIME, time);
        }

        @Override
        public void saveToPreferences(SharedPreferences.Editor editor, int index) {
            editor.putString(index + KEY_TYPE, getValueType());
            editor.putLong(index + KEY_TIME, time);
        }

        protected abstract String getValueType();

    }

    /**
     * Thrown from {@link PrepTimeBellSpec} constructors if there is a problem with the
     * contents of the {@link SharedPreferences} file
     */
    private static class PrepTimeBellConstructorException extends Exception {

        private static final long serialVersionUID = -3348760736240420667L;

        public PrepTimeBellConstructorException(int index, String detailMessage) {
            super(index + ": " + detailMessage);
        }

        public PrepTimeBellConstructorException(String detailMessage) {
            super(detailMessage);
        }
    }

    private class PrepTimeBellFromFinish extends PrepTimeBellByTime {

        public PrepTimeBellFromFinish(Bundle bundle) throws PrepTimeBellConstructorException {
            super(bundle);
        }

        public PrepTimeBellFromFinish(long time) {
            super(time);
        }

        public PrepTimeBellFromFinish(SharedPreferences prefs, int index) throws PrepTimeBellConstructorException {
            super(prefs, index);
        }

        @Override
        public BellInfo getBell(long length) {
            if (length > time)
                return new BellInfo(length - time, 1);
            else
                return null;
        }

        @NonNull
        @Override
        public String toString() {
            if (time == 0) return mContext.getString(R.string.prepTimeBellDescription_atFinish);
            return mContext.getString(R.string.prepTimeBellDescription_beforeFinish, DateUtils.formatElapsedTime(time));
        }

        @Override
        protected String getValueType() {
            return VALUE_TYPE_FINISH;
        }

        @Override
        public boolean isAtFinish() {
            return (time == 0);
        }

    }

    private class PrepTimeBellFromStart extends PrepTimeBellByTime {

        public PrepTimeBellFromStart(Bundle bundle) throws PrepTimeBellConstructorException {
            super(bundle);
        }

        public PrepTimeBellFromStart(SharedPreferences prefs, int index) throws PrepTimeBellConstructorException {
            super(prefs, index);
        }

        @Override
        public BellInfo getBell(long length) {
            // If the time is within the given length, return a bell at that time.
            // Otherwise, return null.
            if (length > time)
                return new BellInfo(time, 1);
            else
                return null;
        }

        @NonNull
        @Override
        public String toString() {
            if (time == 0) return mContext.getString(R.string.prepTimeBellDescription_atStart);
            return mContext.getString(R.string.prepTimeBellDescription_afterStart, DateUtils.formatElapsedTime(time));
        }

        @Override
        protected String getValueType() {
            return VALUE_TYPE_START;
        }

        @Override
        public boolean isAtFinish() {
            return false; // always false
        }

    }

    private class PrepTimeBellProportional implements PrepTimeBellSpec {

        private final double proportion;

        /**
         * Constructor from a {@link Bundle}.
         * @param bundle the Bundle.
         * @throws PrepTimeBellWrongTypeException if the type specified for the given index in
         * the given {@link SharedPreferences} file does not match "proportional".
         * @throws PrepTimeBellConstructorException if there is some other problem with the preferences
         */
        public PrepTimeBellProportional(Bundle bundle) throws PrepTimeBellConstructorException {
            String type = bundle.getString(KEY_TYPE);
            if (type == null)
                throw new PrepTimeBellConstructorException("No type found");
            if (!type.equals(getValueType()))
                throw new PrepTimeBellWrongTypeException(getValueType(), type);
            if (!bundle.containsKey(KEY_PROPORTION))
                throw new PrepTimeBellConstructorException("No proportion found");
            this.proportion = bundle.getDouble(KEY_PROPORTION, 0);
        }

        /**
         * Constructor from a {@link SharedPreferences} file.
         * @param prefs the {@link SharedPreferences} object
         * @param index the index of the desired bell specification in <code>prefs</code>.
         * @throws PrepTimeBellWrongTypeException if the type specified for the given index in
         * the given {@link SharedPreferences} file does not match "proportional".
         * @throws PrepTimeBellConstructorException if there is some other problem with the preferences
         */
        public PrepTimeBellProportional(SharedPreferences prefs, int index) throws PrepTimeBellConstructorException {
            String type = prefs.getString(index + KEY_TYPE, "");
            if (!type.equals(getValueType()))
                throw new PrepTimeBellWrongTypeException(index, getValueType(), type);
            if (!prefs.contains(index + KEY_PROPORTION))
                throw new PrepTimeBellConstructorException(index, "No proportion found");
            this.proportion = Double.parseDouble(prefs.getString(index + KEY_PROPORTION, "0"));
        }

        @Override
        public BellInfo getBell(long length) {
            // Calculate when the bell should ring
            long time = Math.round(length * proportion);
            return new BellInfo(time, 1);
        }

        @Override
        public void saveToBundle(Bundle bundle) {
            bundle.putString(KEY_TYPE, getValueType());
            bundle.putDouble(KEY_PROPORTION, proportion);
        }

        @Override
        public void saveToPreferences(SharedPreferences.Editor editor, int index) {
            editor.putString(index + KEY_TYPE, getValueType());
            editor.putString(index + KEY_PROPORTION, String.valueOf(proportion));
        }

        @NonNull
        @Override
        public String toString() {
            if (proportion == 0) return mContext.getString(R.string.prepTimeBellDescription_atStart);
            if (proportion == 1) return mContext.getString(R.string.prepTimeBellDescription_atFinish);
            double percentage = proportion * 100;
            String percentageStr;
            if (percentage == Math.round(percentage)) percentageStr = String.format(Locale.getDefault(), "%d", Math.round(percentage));
            else percentageStr = String.format(Locale.getDefault(), "%.1f", percentage);
            return mContext.getString(R.string.prepTimeBellDescription_proportional,
                    percentageStr);
        }

        private String getValueType() {
            return VALUE_TYPE_PROPORTIONAL;
        }

        @Override
        public boolean isAtFinish() {
            return (proportion == 1);
        }

    }

    /**
     * Thrown from {@link PrepTimeBellSpec} constructors if the type stored in the
     * {@link SharedPreferences} file does not match the class of the constructor.
     */
    private static class PrepTimeBellWrongTypeException extends PrepTimeBellConstructorException {

        private static final long serialVersionUID = 7237927620650297337L;

        public PrepTimeBellWrongTypeException(int index, String expected, String actual) {
            super(index, "Expected " + expected + ", found " + actual);
        }

        public PrepTimeBellWrongTypeException(String expected, String actual) {
            super("Expected " + expected + ", found " + actual);
        }

    }

    //******************************************************************************************
    // Public methods
    //******************************************************************************************

    /**
     * Adds a bell based on the information in a given {@link Bundle}.
     * The <code>Bundle</code> must have the following entries:
     * <ul><li>A "type", a String that is either "start", "finish" or "proportional"</li>
     * <li>If "type" is "start" or "finish", then a "time", an int in seconds</li>
     * <li>If "type" is "proportional", then a "proportion", a double between 0 and 1</li>
     * </ul>
     * @param bundle the {@link Bundle}
     */
    public void addFromBundle(Bundle bundle) {
        PrepTimeBellSpec bell = createFromBundle(bundle);
        mBellSpecs.add(bell);
    }

    /**
     * Deletes the bell at the given index.
     * @param index the index to delete
     */
    public void deleteBell(int index) {
        mBellSpecs.remove(index);
    }

    /**
     * Deletes all bells, but if there is an existing bell that is at the finish, it leaves the
     * first such bell there.
     */
    public void deleteAllBells(boolean spareFinish) {
        ListIterator<PrepTimeBellSpec> iterator = mBellSpecs.listIterator();
        boolean finishBellFound = false;
        while (iterator.hasNext()) {
            PrepTimeBellSpec spec = iterator.next();

            // If it's the first finish bell and we're sparing finish bells, don't delete it
            if (!finishBellFound && spareFinish && spec.isAtFinish()) {
                finishBellFound = true;
                continue;
            }

            iterator.remove();
        }
    }

    /**
     * @return <code>true</code> if there is at least one bell that is always at the finish,
     * <code>false</code> otherwise
     */
    public boolean hasFinishBell() {
        for (PrepTimeBellSpec mBellSpec : mBellSpecs)
            if (mBellSpec.isAtFinish())
                return true;
        return false;
    }

    /**
     * @return <code>true</code> if there is at least one bell spec.
     */
    public boolean hasBells() {
        return mBellSpecs.size() > 0;
    }

    /**
     * @return <code>true</code> if there is at least one bell that is not always at the finish,
     * or if there is more than one finish bell, <code>false</code> otherwise.  The effect of this
     * is that it will return <code>false</code> only if there is either a single finish bell only,
     * or no bells at all.
     */
    public boolean hasBellsOtherThanFinish() {
        switch (mBellSpecs.size()) {
        case 0:
            return false;
        case 1:
            return !mBellSpecs.get(0).isAtFinish();
        default:
            return true;
        }
    }

    /**
     * @param index the index to look for
     * @return a {@link Bundle} representing the bell at <code>index</code>
     */
    public Bundle getBellBundle(int index) {
        Bundle bundle = new Bundle();
        PrepTimeBellSpec bellSpec = mBellSpecs.get(index);
        bellSpec.saveToBundle(bundle);
        return bundle;
    }

    /**
     * @return An {@link ArrayList} of Strings being descriptions of the bell specs
     */
    public ArrayList<String> getBellDescriptions() {
        ArrayList<String> descriptions = new ArrayList<>(mBellSpecs.size());
        for (PrepTimeBellSpec mBellSpec : mBellSpecs)
            descriptions.add(mBellSpec.toString());
        return descriptions;
    }

    /**
     * @param index the index of the bell spec whose description to retrieve
     * @return a description of the bell
     */
    public String getBellDescription(int index) {
        return mBellSpecs.get(index).toString();
    }

    /**
     * Returns a list of the bells that the current user-defined settings imply
     * for prep time of a given length
     * @param length total length of the prep time
     * @return an {@link ArrayList} of {@link BellInfo} objects sorted by time
     */
    public ArrayList<BellInfo> getBellsList(long length) {

        Iterator<PrepTimeBellSpec> specIterator = mBellSpecs.iterator();
        ArrayList<BellInfo> allBells = new ArrayList<>();

        // First, generate all the bells and put them in a list.
        // Don't bother adding null bells.
        while (specIterator.hasNext()) {
            PrepTimeBellSpec spec = specIterator.next();
            BellInfo bell = spec.getBell(length);
            if (bell == null) continue;

            // If it's a finish bell, make it a double bell
            if (spec.isAtFinish()) bell.getBellSoundInfo().setTimesToPlay(2);

            allBells.add(bell);
        }

        // Then, sort the bells in order of priority.
        // Currently, this sorts them in descending order of number of bells to play.
        Collections.sort(allBells, (lhs, rhs) -> rhs.getBellSoundInfo().getTimesToPlay() - lhs.getBellSoundInfo().getTimesToPlay());

        // Then, run through the bells, adding only non-duplicates.
        Iterator<BellInfo> allBellsIterator = allBells.iterator();
        ArrayList<BellInfo> bells = new ArrayList<>();
        while (allBellsIterator.hasNext()) {
            BellInfo bell = allBellsIterator.next();

            boolean duplicate = false;

            // Treat as a "duplicate" if the bell is within fifteen seconds of another bell.
            // (The bells are already in prioritised order.)
            for (BellInfo checkBell : bells) {
                if (Math.abs(checkBell.getBellTime() - bell.getBellTime()) < 15) {
                    duplicate = true;
                    break;
                }
            }

            // If duplicate, skip the rest
            if (duplicate) continue;

            // Otherwise, add the bell.
            bells.add(bell);
        }

        return bells;

    }

    /**
     * Loads bell specifications from a {@link SharedPreferences} file
     * @param prefs a {@link SharedPreferences} instance
     */
    public void loadFromPreferences(SharedPreferences prefs) {
        // Clear the bells list.
        mBellSpecs.clear();

        // Check if there's anything in the file.  We just do this by checking the "total
        // number of bells" field, and assume that if we can't find it, the file is empty
        // (since the file is useless to us without this field anyway).
        if (!prefs.contains(KEY_TOTAL_NUMBER_OF_BELLS)) {
            // If the file was empty, then load the default: a single bell at the end.
            PrepTimeBellFromFinish bell = new PrepTimeBellFromFinish(0);
            mBellSpecs.add(bell);
            Log.i(TAG, "No file found, loaded default");
            return;
        }

        // If we get to this point, that means there's something in the preferences file.
        // Proceed to load it.

        // Get the total number of bells
        int numberOfBells = prefs.getInt(KEY_TOTAL_NUMBER_OF_BELLS, 0);

        // For each bell, pass preferences to the appropriate constructor.
        for (int index = 0; index < numberOfBells; index++) {

            String indexStr = String.valueOf(index);

            String type = prefs.getString(indexStr + KEY_TYPE, "");

            // If no type found, that's an error.  Skip.
            if (type.equals("")) {
                Log.e(TAG, indexStr + ": No type found");
                continue;
            }

            PrepTimeBellSpec bell;

            try {
                switch (type) {
                    case VALUE_TYPE_START:
                        bell = new PrepTimeBellFromStart(prefs, index);
                        break;
                    case VALUE_TYPE_FINISH:
                        bell = new PrepTimeBellFromFinish(prefs, index);
                        break;
                    case VALUE_TYPE_PROPORTIONAL:
                        bell = new PrepTimeBellProportional(prefs, index);
                        break;
                    default:
                        Log.e(TAG, indexStr + ": Unrecognised type: " + type);
                        continue;
                }
                // Log.v(TAG, indexStr + ": Found a " + type);
            } catch (PrepTimeBellConstructorException e) {
                Log.e(TAG, e.getLocalizedMessage());
                continue;
            }

            mBellSpecs.add(bell);

        }
    }

    public void replaceFromBundle(int index, Bundle bundle) {
        PrepTimeBellSpec bell = createFromBundle(bundle);
        mBellSpecs.set(index, bell);
    }

    /**
     * Saves bell specifications to a {@link SharedPreferences} file
     * @param prefs a {@link SharedPreferences} instance
     */
    public void saveToPreferences(SharedPreferences prefs) {
        // Convention:
        //  The keys are always numbers, followed by a hyphen, followed by a parameter.
        //  For example: "0-type", "0-time", "1-type", "1-time", etc.
        //  The numbers in the keys denote the order in which the bells are listed in the
        //  edit screen.  The saving and loading is handled by individual PrepTimeBellSpec
        //  subclasses.
        // Also, a separate field denotes the total number of bell specifications.

        Iterator<PrepTimeBellSpec> specIterator = mBellSpecs.iterator();
        SharedPreferences.Editor editor = prefs.edit();
        int index = 0;

        // Save all the bell specifications
        while (specIterator.hasNext()) {
            PrepTimeBellSpec spec = specIterator.next();
            spec.saveToPreferences(editor, index);
            index++;
        }

        // Save the total number of bells
        editor.putInt(KEY_TOTAL_NUMBER_OF_BELLS, index);

        // Commit the changes
        editor.apply();
    }

    //******************************************************************************************
    // Private methods
    //******************************************************************************************

    /**
     * Creates a {@link PrepTimeBellSpec} from a {@link Bundle}.
     * @param bundle the {@link Bundle} containing the information from which the {@link PrepTimeBellSpec}
     * is to be created
     * @return the assembled {@link PrepTimeBellSpec}, or <code>null</code> if there was an error.
     */
    private PrepTimeBellSpec createFromBundle(Bundle bundle) {
        String type = bundle.getString(KEY_TYPE);

        // If no type found, that's an error.  Skip.
        if (type == null) {
            Log.e(TAG, "createFromBundle: No type found");
            return null;
        }

        PrepTimeBellSpec bell;

        try {
            switch (type) {
                case VALUE_TYPE_START:
                    bell = new PrepTimeBellFromStart(bundle);
                    break;
                case VALUE_TYPE_FINISH:
                    bell = new PrepTimeBellFromFinish(bundle);
                    break;
                case VALUE_TYPE_PROPORTIONAL:
                    bell = new PrepTimeBellProportional(bundle);
                    break;
                default:
                    Log.e(TAG, "createFromBundle: Unrecognised type: " + type);
                    return null;
            }
            Log.v(TAG, "createFromBundle: Found a " + type);
        } catch (PrepTimeBellConstructorException e) {
            Log.e(TAG, e.getLocalizedMessage());
            return null;
        }

        return bell;
    }

}
