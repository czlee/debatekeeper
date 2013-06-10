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

package net.czlee.debatekeeper.debateformat;


/**
 * PrepTimeControlledFormat is a passive data class that holds information about a prep
 * time format which is controlled by the chair.  This is in contrast to
 * {@link PrepTimeSimpleFormat} which is not controlled by the chair and applies
 * to the majority of debate formats.  In a controlled prep time, bells are rung
 * by the chair to indicate certain events, and all teams are in the room for all
 * preparation.
 *
 * Its functionality is mostly derived from {@link ControlledSpeechOrPrepFormat}.
 *
 * @author Chuan-Zheng Lee
 *
 */
public class PrepTimeControlledFormat extends ControlledSpeechOrPrepFormat
        implements PrepTimeFormat {

    public PrepTimeControlledFormat(long length) {
        super(length);
    }

    /* (non-Javadoc)
     * @see net.czlee.debatekeeper.PrepTimeFormat#isControlled()
     */
    @Override
    public boolean isControlled() {
        return true;
    }

    @Override
    public boolean isPrep() {
        return true;
    }
}
