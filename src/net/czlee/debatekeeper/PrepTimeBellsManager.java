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

import java.util.ArrayList;
import java.util.Iterator;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;

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

    public  static final String KEY_TYPE                         = "type";
    public  static final String KEY_TIME                         = "time";
    public  static final String KEY_PROPORTION                   = "proportion";
    public  static final String VALUE_TYPE_START                 = "start";
    public  static final String VALUE_TYPE_FINISH                = "finish";
    public  static final String VALUE_TYPE_PROPORTIONAL          = "proportional";
    public  static final String PREP_TIME_BELLS_PREFERENCES_NAME = "prep_time_bells";
    private static final String KEY_TOTAL_NUMBER_OF_BELLS        = "totalBells";

    private final ArrayList<PrepTimeBellSpec> mBellSpecs = new ArrayList<PrepTimeBellSpec>();

    private final Context mContext;

    public PrepTimeBellsManager(Context context) {
        mContext = context;
    }

    //******************************************************************************************
    // Private classes
    //******************************************************************************************

    private interface PrepTimeBellSpec {

        /**
         * @param length
         * @return A {@link BellInfo} object, or <code>null</code> if this bell is
         * incompatible with the length given (e.g. it would occur after the end
         * of the prep time given).
         */
        public BellInfo getBell(long length);

        /**
         * Saves this bell specification to a bundle.
         * @param bundle the Bundle to save to
         */
        public void saveToBundle(Bundle bundle);

        /**
         * Saves this bell specification to a shared preferences file.
         * @param index the index of this PrepTimeBellSpec in the list
         */
        public void saveToPreferences(SharedPreferences.Editor editor, int index);

    }

    private abstract class PrepTimeBellByTime implements PrepTimeBellSpec {

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
            String type = prefs.getString(String.valueOf(index) + KEY_TYPE, "");
            if (!type.equals(getValueType()))
                throw new PrepTimeBellWrongTypeException(index, getValueType(), type);
            if (!prefs.contains(String.valueOf(index) + KEY_TIME))
                throw new PrepTimeBellConstructorException(index, "No time found");
            this.time = prefs.getLong(String.valueOf(index) + KEY_TIME, 0);
        }

        @Override
        public void saveToBundle(Bundle bundle) {
            bundle.putString(KEY_TYPE, getValueType());
            bundle.putLong(KEY_TIME, time);
        }

        @Override
        public void saveToPreferences(SharedPreferences.Editor editor, int index) {
            editor.putString(String.valueOf(index) + KEY_TYPE, getValueType());
            editor.putLong(String.valueOf(index) + KEY_TIME, time);
        }

        protected abstract String getValueType();

    }

    /**
     * Thrown from {@link PrepTimeBellSpec} constructors if there is a problem with the
     * contents of the {@link SharedPreferences} file
     */
    private class PrepTimeBellConstructorException extends Exception {

        private static final long serialVersionUID = -3348760736240420667L;

        public PrepTimeBellConstructorException(int index, String detailMessage) {
            super(String.valueOf(index) + ": " + detailMessage);
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

        @Override
        public String toString() {
            if (time == 0) return mContext.getString(R.string.PrepTimeBellAtFinishDescription);
            return mContext.getString(R.string.PrepTimeBellFinishDescription, secsToText(time));
        }

        @Override
        protected String getValueType() {
            return VALUE_TYPE_FINISH;
        }

    }

    private class PrepTimeBellFromStart extends PrepTimeBellByTime {

        public PrepTimeBellFromStart(Bundle bundle) throws PrepTimeBellConstructorException {
            super(bundle);
        }

        public PrepTimeBellFromStart(long time) {
            super(time);
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

        @Override
        public String toString() {
            if (time == 0) return mContext.getString(R.string.PrepTimeBellAtStartDescription);
            return mContext.getString(R.string.PrepTimeBellStartDescription, secsToText(time));
        }

        @Override
        protected String getValueType() {
            return VALUE_TYPE_START;
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
            String type = prefs.getString(String.valueOf(index) + KEY_TYPE, "");
            if (!type.equals(getValueType()))
                throw new PrepTimeBellWrongTypeException(index, getValueType(), type);
            if (!prefs.contains(String.valueOf(index) + KEY_PROPORTION))
                throw new PrepTimeBellConstructorException(index, "No proportion found");
            this.proportion = Double.parseDouble(prefs.getString(String.valueOf(index) + KEY_PROPORTION, "0"));
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
            editor.putString(String.valueOf(index) + KEY_TYPE, getValueType());
            editor.putString(String.valueOf(index) + KEY_PROPORTION, String.valueOf(proportion));
        }

        @Override
        public String toString() {
            if (proportion == 0) return mContext.getString(R.string.PrepTimeBellAtStartDescription);
            if (proportion == 1) return mContext.getString(R.string.PrepTimeBellAtFinishDescription);
            Double percentage = proportion * 100;
            String percentageStr;
            if (percentage == Math.round(percentage)) percentageStr = String.format("%d", Math.round(percentage));
            else percentageStr = String.format("%.1f", percentage);
            return mContext.getString(R.string.PrepTimeBellProportionalDescription,
                    percentageStr);
        }

        private String getValueType() {
            return VALUE_TYPE_PROPORTIONAL;
        }

    }

    /**
     * Thrown from {@link PrepTimeBellSpec} constructors if the type stored in the
     * {@link SharedPreferences} file does not match the class of the constructor.
     */
    private class PrepTimeBellWrongTypeException extends PrepTimeBellConstructorException {

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

    public void addFromBundle(Bundle bundle) {
        PrepTimeBellSpec bell = createFromBundle(bundle);
        mBellSpecs.add(bell);
    }

    public void deleteBell(int position) {
        mBellSpecs.remove(position);
    }

    /**
     * @param index
     * @return a {@link Bundle} representing the bell at <code>index</code>
     */
    public Bundle getBellBundle(int index) {
        Bundle bundle = new Bundle();
        PrepTimeBellSpec bellSpec = mBellSpecs.get(index);
        bellSpec.saveToBundle(bundle);
        return bundle;
    }

    public ArrayList<String> getBellDescriptions() {
        ArrayList<String> descriptions = new ArrayList<String>(mBellSpecs.size());
        Iterator<PrepTimeBellSpec> iterator = mBellSpecs.iterator();
        while (iterator.hasNext())
            descriptions.add(iterator.next().toString());
        return descriptions;
    }

    /**
     * Returns a list of the bells that the current user-defined settings imply
     * for prep time of a given length
     * @param length total length of the prep time
     * @return an {@link ArrayList} of {@link BellInfo} objects sorted by time
     */
    public ArrayList<BellInfo> getBellsList(long length) {

        Iterator<PrepTimeBellSpec> specIterator = mBellSpecs.iterator();
        ArrayList<BellInfo> bells = new ArrayList<BellInfo>();

        // First, generate all the bells.
        // But don't add bells that are at the same time as some other bell in the list.
        while (specIterator.hasNext()) {
            PrepTimeBellSpec spec = specIterator.next();
            BellInfo bell = spec.getBell(length);
            if (bell != null) {

                boolean duplicate = false;

                // Treat as a "duplicate" if the bell is within fifteen seconds of another bell.
                // (The bell added first always gets priority.)
                Iterator<BellInfo> bellIterator = bells.iterator();
                while (bellIterator.hasNext()) {
                    BellInfo checkBell = bellIterator.next();
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
            Log.w(this.getClass().getSimpleName(), "No file found, loaded default");
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
                Log.e(this.getClass().getSimpleName(), indexStr + ": No type found");
                continue;
            }

            PrepTimeBellSpec bell;

            try {
                if (type.equals(VALUE_TYPE_START)) {
                    bell = new PrepTimeBellFromStart(prefs, index);
                } else if (type.equals(VALUE_TYPE_FINISH)) {
                    bell = new PrepTimeBellFromFinish(prefs, index);
                } else if (type.equals(VALUE_TYPE_PROPORTIONAL)) {
                    bell = new PrepTimeBellProportional(prefs, index);
                } else {
                    Log.e(this.getClass().getSimpleName(), indexStr + ": Unrecognised type: " + type);
                    continue;
                }
                Log.v(this.getClass().getSimpleName(), indexStr + ": Found a " + type);
            } catch (PrepTimeBellConstructorException e) {
                Log.e(this.getClass().getSimpleName(), e.getLocalizedMessage());
                continue;
            }

            mBellSpecs.add(bell);

        }
    }

    public void replaceFromBundle(int index, Bundle bundle) {
        PrepTimeBellSpec bell = createFromBundle(bundle);
        mBellSpecs.remove(index);
        mBellSpecs.add(index, bell);
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
        editor.commit();
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
            Log.e(this.getClass().getSimpleName(), "Create from bundle: No type found");
            return null;
        }

        PrepTimeBellSpec bell;

        try {
            if (type.equals(VALUE_TYPE_START)) {
                bell = new PrepTimeBellFromStart(bundle);
            } else if (type.equals(VALUE_TYPE_FINISH)) {
                bell = new PrepTimeBellFromFinish(bundle);
            } else if (type.equals(VALUE_TYPE_PROPORTIONAL)) {
                bell = new PrepTimeBellProportional(bundle);
            } else {
                Log.e(this.getClass().getSimpleName(), "Create from bundle: Unrecognised type: " + type);
                return null;
            }
            Log.v(this.getClass().getSimpleName(), "Create from bundle: Found a " + type);
        } catch (PrepTimeBellConstructorException e) {
            Log.e(this.getClass().getSimpleName(), e.getLocalizedMessage());
            return null;
        }

        return bell;
    }

    private static String secsToText(long time) {
        return String.format("%02d:%02d", time / 60, time % 60);
    }

}
