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

import org.xml.sax.SAXException
import java.io.IOException
import java.io.InputStream

interface DebateFormatBuilderFromXml {

    /**
     * Builds a debate from a given input stream, which must be an XML file.
     * @param is an [InputStream] to an XML file
     * @return the [DebateFormat]
     * @throws IOException if there was an IO error with the `InputStream`
     * @throws SAXException if thrown by the XML parser
     * @throws IllegalStateException if there were no speeches in this format
     */
    @Throws(IOException::class, SAXException::class)
    fun buildDebateFromXml(inputStream: InputStream): DebateFormat

    /**
     * @return true if there are errors in the error log
     */
    fun hasErrors(): Boolean

    /**
     * @return `true` if the schema version is too old for this class, `false`
     * otherwise. Returns `false` if there is no schema version, this includes if this
     * builder hasn't parsed an XML file yet.
     */
    fun isSchemaOutdated(): Boolean

    /**
     * @return `true` if the schema is too new for this class, `false` otherwise.
     * Returns `false` if the schema version is unknown or invalid.
     */
    fun isSchemaTooNew(): Boolean

    /**
     * An [List] of [String]s, each item being an error found by the XML parser
     */
    val errorLog: List<String>

    /**
     * The schema version of the processed file
     */
    val schemaVersion: String?

    /**
     * The highest schema version supported by this builder
     */
    val supportedSchemaVersion: String
}
