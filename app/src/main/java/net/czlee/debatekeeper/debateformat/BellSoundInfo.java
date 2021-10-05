/*
 * Copyright (C) 2012-2021 Chuan-Zheng Lee
 *
 * This file is part of the Debatekeeper app, which is licensed under the GNU
 * General Public Licence version 3 (GPLv3).  You can redistribute and/or modify
 * it under the terms of the GPLv3, and you must not use this file except in
 * compliance with the GPLv3.
 *
 * This app is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR
 * A PARTICULAR PURPOSE.  See the GNU General Public Licence for more details.
 *
 * You should have received a copy of the GNU General Public Licence along with
 * this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package net.czlee.debatekeeper.debateformat;

import net.czlee.debatekeeper.R;

/**
 * BellSoundInfo is a passive data class containing information about a bell sound.
 *
 * It is used to abstract away the mechanics of playing a bell sound away from other classes.
 * Playing a bell sound is a non-trivial exercise.  It's not necessarily just playing a single
 * sound file once, because the bell may be played multiple times.  For example, the end of
 * most speeches are signalled with a double bell, not a single one.  For some bell sounds,
 * a "double bell" may be a single bell repeated twice; for others, there may be another sound
 * file for a double bell that only needs to be played once.
 *
 * BellSoundInfo is handled by BellRepeater, and is a member of BellInfo.
 *
 * @author Chuan-Zheng Lee
 * @since  2012-05-30
 */
public class BellSoundInfo {

    protected int mNumberOfBells = 1;
    protected long mRepeatPeriod = 500;

    /**
     * Array of sound resource IDs. If more than one is provided, the second should be a double bell
     * sound, the third should be a triple bell sound, etc.
     */
    protected int[] mSoundResIds = {R.raw.desk_bell, R.raw.desk_bell_double, R.raw.desk_bell_triple};

    //******************************************************************************************
    // Public constructors
    //******************************************************************************************

    public BellSoundInfo() {}

    public BellSoundInfo(int numberOfBells) {
        super();
        mNumberOfBells = numberOfBells;
    }

    public void setNumberOfBells(int numberOfBells) {
        mNumberOfBells = numberOfBells;
    }

    //******************************************************************************************
    // Public methods
    //******************************************************************************************

    /**
     * Gets the resource ID of the sound file that should be played by the media player.
     * @return resource ID
     */
    public int getSoundResId() {
        if (mNumberOfBells > 0 && mNumberOfBells <= mSoundResIds.length)
            return mSoundResIds[mNumberOfBells - 1];
        else
            return mSoundResIds[0];
    }

    /**
     * Returns the number of times this bell sound is configured to play. This may not be the same
     * as the number of times the sound file should be played, if the sound file itself contains
     * multiple bell, see {@link BellSoundInfo#getTimesToRepeatMedia()}.
     * @return number of bells
     */
    public int getNumberOfBells() {
        return mNumberOfBells;
    }

    /**
     * Returns the number of times the sound file should be played. This may not be the same as the
     * number of bells, if the sound file itself contains multiple bells, see
     * {@link BellSoundInfo#getNumberOfBells()}.
     * @return number of times to repeat the sound file
     */
    public int getTimesToRepeatMedia() {
        if (mNumberOfBells > 0 && mNumberOfBells <= mSoundResIds.length)
            return 1;
        else
            return mNumberOfBells;
    }

    public long getRepeatPeriod() {
        return mRepeatPeriod;
    }

}