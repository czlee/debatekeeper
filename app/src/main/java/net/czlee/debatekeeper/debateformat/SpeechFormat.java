/*
 * Copyright (C) 2012 Chuan-Zheng Lee
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


/**
 * SpeechFormat is a passive data class that holds information about a speech format.
 *
 * A speech format defines things like the speech length and bell times.
 * A speech format is generally specific to a debate format (though, programming-wise, need
 * not be).  For example, the Australs debate format has two speech formats: substantive and
 * reply.  The British Parliamentary format has only one speech format.  Most American formats
 * have almost one format for every speech!
 *
 * This class doesn't have much brains, but it does have the ability to intelligently pick a
 * useful bell, i.e. the first bell after a time that is given to it.
 *
 * The SpeechFormat class is processed by BellChain.
 *
 * You can't change the speech length after you've instantiated this object.
 *
 *  @author Chuan-Zheng Lee
 *  @since  2012-06-09
 */
public class SpeechFormat extends ControlledDebatePhaseFormat {

    private final String mReference;

    public SpeechFormat(String reference, long speechLength) {
        super(speechLength);
        mReference = reference;
    }

    //******************************************************************************************
    // Public methods
    //******************************************************************************************

    /**
     * @return a reference string, not strictly part of the speech format but users
     * may find it useful to know what the reference was that was used to create this
     * <code>SpeechFormat</code>.  This may return <code>null</code> if the <code>SpeechFormat</code>
     * was created from a version 1 schema.
     */
    public String getReference() {
        return mReference;
    }

    /**
     * Finds whether any period anywhere in the speech has POIs allowed.
     * @return <code>true</code> if POIs are allowed somewhere in the speech,
     * <code>false</code> otherwise.
     */
    public boolean hasPoisAllowedSomewhere() {
        if (mFirstPeriodInfo.isPoisAllowed()) return true;

        for (BellInfo thisBell : mBells) {
            // Return true as soon as we find one with POIs allowed
            if (thisBell.getNextPeriodInfo().isPoisAllowed()) return true;
        }

        return false;
    }

    @Override
    public boolean isPrep() {
        return false;
    }

}
