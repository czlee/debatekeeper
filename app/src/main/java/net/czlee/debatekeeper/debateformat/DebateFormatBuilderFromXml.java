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

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;

import org.xml.sax.SAXException;

public interface DebateFormatBuilderFromXml {

    //******************************************************************************************
    // Public methods
    //******************************************************************************************
    /**
     * Builds a debate from a given input stream, which must be an XML file.
     * @param is an {@link InputStream} to an XML file
     * @return the {@link DebateFormat}
     * @throws IOException if there was an IO error with the <code>InputStream</code>
     * @throws SAXException if thrown by the XML parser
     * @throws IllegalStateException if there were no speeches in this format
     */
    public abstract DebateFormat buildDebateFromXml(InputStream is)
            throws IOException, SAXException;

    /**
     * @return true if there are errors in the error log
     */
    public abstract boolean hasErrors();

    /**
     * @return <code>true</code> if the schema version is supported.
     * <code>false</code> if there is no schema version, this includes if this builder hasn't parsed
     * an XML file yet.
     */
    public abstract boolean isSchemaSupported() throws IllegalArgumentException;

    /**
     * @return <code>true</code> if the schema is too new for this class, <code>false</code> otherwise.
     * Returns <code>false</code> if the schema version is unknown or invalid.
     */
    public abstract boolean isSchemaTooNew();

    /**
     * @return An <i>ArrayList</i> of <code>String</code>s, each item being an error found by
     * the XML parser
     */
    public abstract ArrayList<String> getErrorLog();

    /**
     * @return the schema version of the processed file
     */
    public abstract String getSchemaVersion();

    /**
     * @return the highest schema version supported by this builder
     */
    public abstract String getSupportedSchemaVersion();

}