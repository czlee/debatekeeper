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

import java.util.List;

/**
 * Interface for passive data classes holding information about a debate format that would
 * be human-readable on a quick "information" screen about the debate format.
 * @author Chuan-Zheng Lee
 * @since  2013-06-04
 */
public interface DebateFormatInfo {

    /**
     * @return the name of the format, or an empty string if there is none
     */
    String getName();

    /**
     * @return a description, or a String with a single hyphen if there is none
     */
    String getDescription();

    /**
     * @return a list of regions, or an empty list if there were none
     */
    List<String> getRegions();

    /**
     * @return a list of levels, or an empty list if there were none
     */
    List<String> getLevels();

    /**
     * @return a list of tournaments, or an empty list if there were none
     */
    List<String> getUsedAts();

    /**
     * @return a list of languages supported, or an empty list if there were none
     */
    List<String> getDisplayLanguages();

    /**
     * @return a description of prep time, or <code>null</code> if there is no prep time for this
     * format
     */
    String getPrepTimeDescription();

    /**
     * @return the schema version, or <code>null</code> if there is none
     */
    String getSchemaVersion();

    /**
     * @return <code>true</code> if the schema is supported by this class, <code>false</code> otherwise
     */
    boolean isSchemaSupported();

    /**
     * Returns a list of all the speech formats in this debate format, with descriptions.
     * @return An <code>ArrayList</code> of <code>String</code> arrays. Each
     *         <code>String</code> array has two elements. The first element is
     *         the speech type reference. The second element is a short
     *         description of the speech type. The <code>ArrayList</code> is
     *         sorted in the order the speech types appear in the debate. If a
     *         speech type isn't used, it isn't part of the returned
     *         <code>ArrayList</code>.
     */
    List<String[]> getSpeechFormatDescriptions();

    /**
     * Returns a list of speeches in this debate format.
     * @param  descriptions Value returned by getSpeechFormatDescriptions()
     * @return An <code>ArrayList</code> of <code>String</code> arrays. Each
     *         <code>String</code> array has two elements.  The first element
     *         is the name of the speech, the second element is the reference
     *         for the format that speech uses.
     */
    List<String[]> getSpeeches(List<String[]> descriptions);

}