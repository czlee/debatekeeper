package com.ftechz.DebatingTimer;

import java.util.ArrayList;
import java.util.HashMap;

import android.util.Log;

/**
 * DebateFormat is a passive data class that holds information about a debate format.
 *
 * Examples of debate formats: British Parliamentary, Australs, Australian Easters.  This does
 * nothing other than provide information.  The class DebateManager deals with managing the debate.
 *
 * The DebateFormatBuilder class should be used to construct one of these.  DebateFormat doesn't
 * know about how formats are specified (in e.g. XML), it just knows about its speech formats.
 *
 * @author Chuan-Zheng Lee
 * @since 2012-06-02
 */
public class DebateFormat {

    private final HashMap<String, SpeechFormat>  mSpeechFormats;
    private final ArrayList<SpeechSpec>          mSpeechSpecs;

    //******************************************************************************************
    // Public classes
    //******************************************************************************************

    /**
     * Thrown when a speech is added with a speech format that is not currently defined.
     */
    public class NoSuchFormatException extends Exception {

        private static final long serialVersionUID = 7705013466811555014L;

        public NoSuchFormatException(String detailMessage) {
            super(detailMessage);
        }

    }

    //******************************************************************************************
    // Private classes
    //******************************************************************************************

    /**
     * Collection of information relating to a speech.  For internal (private) use only; future
     * implementations may do away with this class.
     */
    private class SpeechSpec {
        public final String name;
        public final String type;

        public SpeechSpec(String name, String type) {
            super();
            this.name = name;
            this.type = type;
        }
    }

    //******************************************************************************************
    // Public methods
    //******************************************************************************************

    /**
     * Constructor.
     */
    public DebateFormat() {
        super();
        mSpeechFormats = new HashMap<String, SpeechFormat>();
        mSpeechSpecs   = new ArrayList<SpeechSpec>();
    }

    /**
     * Adds a speech format to the internal collection of speech formats.
     * @param formatRef the key (reference) to the speech format
     * @param sf the SpeechFormat
     */
    public void addSpeechFormat(String formatRef, SpeechFormat sf) {
        mSpeechFormats.put(formatRef, sf);
    }

    /**
     * Adds a speech to the internal list of speeches.
     * @param name The human-readable name of the speech, e.g. "1st Affirmative", "Prime Minister"
     * @param formatRef A String representing the SpeechFormat, which must have already been added using
     * addSpeechFormat().
     */
    public void addSpeech(String name, String formatRef) throws NoSuchFormatException {
        // The speech type must already exist.
        if (!mSpeechFormats.containsKey(formatRef)) {
//            Log.e(this.getClass().getSimpleName(),  // or the line below
            throw new NoSuchFormatException(
                    String.format("Added a speech with non-existent format ref '%s'", formatRef));
        }
        mSpeechSpecs.add(new SpeechSpec(name, formatRef));
    }

    /**
     * Returns the speech format for a specified speech.
     * @param index the index of the speech (0 for first speech, 1 for second, etc.)
     * @return a SpeechFormat object for that speech, or null if there is no such speech or if
     * the speech has no such format.
     */
    public SpeechFormat getSpeechFormat(int index) {
        String speechType;
        SpeechFormat speechFormat;

        // 1. Retrieve the speech type
        try {
            speechType = mSpeechSpecs.get(index).type;
        } catch (IndexOutOfBoundsException e) {
            Log.e(this.getClass().getSimpleName(),
                    String.format("Attempted to retrieve speech format for index %d", index));
            return null;
        }

        // 2. Retrieve the speech format for that type
        speechFormat = mSpeechFormats.get(speechType);

        if (speechFormat == null) {
            Log.e(this.getClass().getSimpleName(),
                    String.format("No speech format for key '%s'", speechType));
        }

        return speechFormat;
    }

    /**
     * Returns the name of a specified speech.
     * @param index the index of the speech (0 for first speech, 1 for second, etc.)
     * @return the name of the speech, or null if there is no such speech.
     */
    public String getSpeechName(int index) {
        try {
            return mSpeechSpecs.get(index).name;
        } catch (IndexOutOfBoundsException e) {
            Log.e(this.getClass().getSimpleName(),
                    String.format("Attempted to retrieve speech name for index %d", index));
            return null;
        }
    }

    /**
     * @return the number of speeches in this debate
     */
    public int numberOfSpeeches() {
        return mSpeechSpecs.size();
    }

}
