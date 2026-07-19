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

package net.czlee.debatekeeper.debateformat

/**
 * Interface for passive data classes holding information about a debate format that would
 * be human-readable on a quick "information" screen about the debate format.
 * @author Chuan-Zheng Lee
 * @since  2013-06-04
 */
interface DebateFormatInfo {

    /** The name of the format, or an empty string if there is none. */
    val name: String

    /** A description, or a String with a single hyphen if there is none. */
    val description: String

    /** A list of regions, or an empty list if there were none. */
    val regions: List<String>

    /** A list of levels, or an empty list if there were none. */
    val levels: List<String>

    /** A list of tournaments, or an empty list if there were none. */
    val usedAts: List<String>

    /** A list of languages supported, or an empty list if there were none. */
    val displayLanguages: List<String>

    /**
     * A description of prep time, or `null` if there is no prep time for this format.
     */
    val prepTimeDescription: String?

    /** The schema version, or `null` if there is none. */
    val schemaVersion: String?

    /** `true` if the schema is supported by this class, `false` otherwise. */
    val isSchemaSupported: Boolean

    /**
     * Returns a list of all the speech formats in this debate format, with descriptions.
     * @return a list of `String` arrays. Each `String` array has two elements. The first element
     * is the speech type reference. The second element is a short description of the speech
     * type. The list is sorted in the order the speech types appear in the debate. If a speech
     * type isn't used, it isn't part of the returned list.
     */
    val speechFormatDescriptions: List<Array<String>>

    /**
     * Returns a list of speeches in this debate format.
     * @param descriptions value returned by [speechFormatDescriptions]
     * @return a list of `String` arrays. Each `String` array has two elements.  The first element
     * is the name of the speech, the second element is the reference for the format that speech
     * uses.
     */
    fun getSpeeches(descriptions: List<Array<String>>): List<Array<String>>
}
