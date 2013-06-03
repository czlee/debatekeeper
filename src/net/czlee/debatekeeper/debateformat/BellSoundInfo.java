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

import net.czlee.debatekeeper.R;
import net.czlee.debatekeeper.R.raw;

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
    protected int  mSoundResid   = R.raw.desk_bell; // default sound
    protected int  mTimesToPlay  = 1;               // default times to play
    protected long mRepeatPeriod = 500;

    public BellSoundInfo() {}

    public BellSoundInfo(int timesToPlay) {
        super();
        mTimesToPlay = timesToPlay;
    }

    public void setTimesToPlay(int timesToPlay) {
        mTimesToPlay = timesToPlay;
    }

    public int getSoundResid() {
        return mSoundResid;
    }

    public int getTimesToPlay() {
        return mTimesToPlay;
    }

    public long getRepeatPeriod() {
        return mRepeatPeriod;
    }

    /**
     * @return true if this sound can be played, false if it is silent
     */
    public boolean isPlayable() {
        return mSoundResid != 0 && mTimesToPlay != 0;
    }
}