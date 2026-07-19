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

package net.czlee.debatekeeper.debateformat

import net.czlee.debatekeeper.R

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
class BellSoundInfo @JvmOverloads constructor(var numberOfBells: Int = 1) {

    val repeatPeriod: Long = 500

    /**
     * Array of sound resource IDs. If more than one is provided, the second should be a double bell
     * sound, the third should be a triple bell sound, etc.
     */
    private val soundResIds = intArrayOf(R.raw.desk_bell, R.raw.desk_bell_double, R.raw.desk_bell_triple)

    /**
     * The resource ID of the sound file that should be played by the media player.
     */
    val soundResId: Int
        get() = if (numberOfBells > 0 && numberOfBells <= soundResIds.size)
            soundResIds[numberOfBells - 1]
        else
            soundResIds[0]

    /**
     * The number of times the sound file should be played. This may not be the same as the
     * number of bells, if the sound file itself contains multiple bells, see [numberOfBells].
     */
    val timesToRepeatMedia: Int
        get() = if (numberOfBells > 0 && numberOfBells <= soundResIds.size)
            1
        else
            numberOfBells
}
