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
            throws IOException, SAXException, IllegalStateException;

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
     * @return An <i>ArrayList</i> of <code>String</code>s, each item being an error found by
     * the XML parser
     */
    public abstract ArrayList<String> getErrorLog();

}