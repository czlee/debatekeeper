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

    private static final String KEY_TYPE                         = "-type";
    private static final String KEY_TOTAL_NUMBER_OF_BELLS        = "totalBells";
    public  static final String PREP_TIME_BELLS_PREFERENCES_NAME = "prep_time_bells";

    private final ArrayList<PrepTimeBellSpec> mBellSpecs = new ArrayList<PrepTimeBellSpec>();

    private final Context mContext;

    public PrepTimeBellsManager(Context context) {
        mContext = context;
    }

    //******************************************************************************************
    // Public interface
    //******************************************************************************************

    public interface PrepTimeBellSpec {
        /**
         * @param length
         * @return A {@link BellInfo} object, or <code>null</code> if this bell is
         * incompatible with the length given (e.g. it would occur after the end
         * of the prep time given).
         */
        public BellInfo getBell(long length);

        /**
         * Saves this bell specification to a shared preferences file.
         * @param index the index of this PrepTimeBellSpec in the list
         */
        public void saveToPreferences(SharedPreferences.Editor editor, int index);

    }

    //******************************************************************************************
    // Private classes
    //******************************************************************************************

    /**
     * Thrown from {@link PrepTimeBellSpec} constructors if there is a problem with the
     * contents of the {@link SharedPreferences} file
     */
    private class PrepTimeBellConstructorException extends Exception {

        private static final long serialVersionUID = -3348760736240420667L;

        public PrepTimeBellConstructorException(int index, String detailMessage) {
            super(String.valueOf(index) + ": " + detailMessage);
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
    }

    private class PrepTimeBellFromStart implements PrepTimeBellSpec {

        private final long time;

        public  static final String VALUE_TYPE = "start";
        private static final String KEY_TIME   = "-time";

        public PrepTimeBellFromStart(long time) {
            this.time = time;
        }

        /**
         * Constructor from a {@link SharedPreferences} file.
         * @param prefs the {@link SharedPreferences} object
         * @param index the index of the desired bell specification in <code>prefs</code>.
         * @throws PrepTimeBellWrongTypeException if the type specified for the given index in
         * the given {@link SharedPreferences} file does not match "start".
         */
        public PrepTimeBellFromStart(SharedPreferences prefs, int index) throws PrepTimeBellConstructorException {
            String type = prefs.getString(String.valueOf(index) + KEY_TYPE, "");
            if (!type.equals(VALUE_TYPE))
                throw new PrepTimeBellWrongTypeException(index, VALUE_TYPE, type);
            if (!prefs.contains(String.valueOf(index) + KEY_TIME))
                throw new PrepTimeBellConstructorException(index, "No time found");
            this.time = prefs.getLong(String.valueOf(index) + KEY_TIME, 0);
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
        public void saveToPreferences(SharedPreferences.Editor editor, int index) {
            editor.putString(String.valueOf(index) + KEY_TYPE, VALUE_TYPE);
            editor.putLong(String.valueOf(index) + KEY_TIME, time);
        }

        @Override
        public String toString() {
            if (time == 0) return mContext.getString(R.string.PrepTimeBellAtStartDescription);
            return mContext.getString(R.string.PrepTimeBellStartDescription, secsToText(time));
        }

    }

    private class PrepTimeBellFromFinish implements PrepTimeBellSpec {

        private final long time;

        public  static final String VALUE_TYPE = "finish";
        private static final String KEY_TIME   = "-time";

        public PrepTimeBellFromFinish(long time) {
            this.time = time;
        }

        /**
         * Constructor from a {@link SharedPreferences} file.
         * @param prefs the {@link SharedPreferences} object
         * @param index the index of the desired bell specification in <code>prefs</code>.
         * @throws PrepTimeBellWrongTypeException if the type specified for the given index in
         * the given {@link SharedPreferences} file does not match "finish".
         */
        public PrepTimeBellFromFinish(SharedPreferences prefs, int index) throws PrepTimeBellConstructorException {
            String type = prefs.getString(String.valueOf(index) + KEY_TYPE, "");
            if (!type.equals(VALUE_TYPE))
                throw new PrepTimeBellWrongTypeException(index, VALUE_TYPE, type);
            if (!prefs.contains(String.valueOf(index) + KEY_TIME))
                throw new PrepTimeBellConstructorException(index, "No time found");
            this.time = prefs.getLong(String.valueOf(index) + KEY_TIME, 0);
        }

        @Override
        public BellInfo getBell(long length) {
            if (length > time)
                return new BellInfo(length - time, 1);
            else
                return null;
        }

        @Override
        public void saveToPreferences(SharedPreferences.Editor editor, int index) {
            editor.putString(String.valueOf(index) + KEY_TYPE, VALUE_TYPE);
            editor.putLong(String.valueOf(index) + KEY_TIME, time);
        }

        @Override
        public String toString() {
            if (time == 0) return mContext.getString(R.string.PrepTimeBellAtFinishDescription);
            return mContext.getString(R.string.PrepTimeBellFinishDescription, secsToText(time));
        }

    }

    private class PrepTimeBellProportional implements PrepTimeBellSpec {

        private final float proportion;

        public  static final String VALUE_TYPE     = "proportional";
        private static final String KEY_PROPORTION = "-proportion";

        public PrepTimeBellProportional (float proportion) {
            this.proportion = proportion;
        }

        /**
         * Constructor from a {@link SharedPreferences} file.
         * @param prefs the {@link SharedPreferences} object
         * @param index the index of the desired bell specification in <code>prefs</code>.
         * @throws PrepTimeBellWrongTypeException if the type specified for the given index in
         * the given {@link SharedPreferences} file does not match "proportional".
         */
        public PrepTimeBellProportional(SharedPreferences prefs, int index) throws PrepTimeBellConstructorException {
            String type = prefs.getString(String.valueOf(index) + KEY_TYPE, "");
            if (!type.equals(VALUE_TYPE))
                throw new PrepTimeBellWrongTypeException(index, VALUE_TYPE, type);
            if (!prefs.contains(String.valueOf(index) + KEY_PROPORTION))
                throw new PrepTimeBellConstructorException(index, "No proportion found");
            this.proportion = prefs.getFloat(String.valueOf(index) + KEY_PROPORTION, 0);
        }

        @Override
        public BellInfo getBell(long length) {
            // Calculate when the bell should ring
            long time = (long) (length * proportion);
            return new BellInfo(time, 1);
        }

        @Override
        public void saveToPreferences(SharedPreferences.Editor editor, int index) {
            editor.putString(String.valueOf(index) + KEY_TYPE, VALUE_TYPE);
            editor.putFloat(String.valueOf(index) + KEY_PROPORTION, proportion);
        }

        @Override
        public String toString() {
            if (proportion == 0) return mContext.getString(R.string.PrepTimeBellAtStartDescription);
            if (proportion == 1) return mContext.getString(R.string.PrepTimeBellAtFinishDescription);
            return mContext.getString(R.string.PrepTimeBellProportionalDescription,
                    Math.round(proportion * 100));
        }

    }

    //******************************************************************************************
    // Public methods
    //******************************************************************************************

    /**
     * Adds a bell specification, specified relative to the start of prep time.
     * @param time the time from the start of prep time
     */
    public void addBellFromStart(long time) {
        PrepTimeBellSpec bell = new PrepTimeBellFromStart(time);
        mBellSpecs.add(bell);
    }

    public void addBellFromFinish(long time) {
        PrepTimeBellSpec bell = new PrepTimeBellFromFinish(time);
        mBellSpecs.add(bell);
    }

    public void addBellProportional(float proportion) {
        PrepTimeBellSpec bell = new PrepTimeBellProportional(proportion);
        mBellSpecs.add(bell);
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
            addBellFromFinish(0);
            addBellFromStart(600);
            addBellProportional((float) 0.5);
            addBellFromFinish(300);
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
                if (type.equals(PrepTimeBellFromStart.VALUE_TYPE)) {
                    bell = new PrepTimeBellFromStart(prefs, index);
                } else if (type.equals(PrepTimeBellFromFinish.VALUE_TYPE)) {
                    bell = new PrepTimeBellFromFinish(prefs, index);
                } else if (type.equals(PrepTimeBellProportional.VALUE_TYPE)) {
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

        // TODO write this method
        return bells;

    }

    public ArrayList<PrepTimeBellSpec> getBellSpecs() {
        return mBellSpecs;
    }

    //******************************************************************************************
    // Private methods
    //******************************************************************************************

    private static String secsToText(long time) {
        return String.format("%02d:%02d", time / 60, time % 60);
    }


}
