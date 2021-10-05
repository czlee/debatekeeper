/*
 * Copyright (C) 2013 Chuan-Zheng Lee
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
 * <p>PrepTimeFormat is an extension of {@link DebatePhaseFormat} that adds methods relevant
 * only to prep times.</p>
 * @author Chuan-Zheng Lee
 *
 */
public interface PrepTimeFormat extends DebatePhaseFormat {

    /**
     * @return <code>true</code> if this format is "controlled" prep time
     */
    boolean isControlled();

}