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

package net.czlee.debatekeeper.debateformat

import android.util.Log

/**
 * DebateFormat is a passive data class that holds information about a debate format.
 *
 * Examples of debate formats: British Parliamentary, Australs, Australian Easters.  This does
 * nothing other than provide information.  The class DebateManager deals with managing the debate.
 *
 * The DebateFormatBuilderFromXml class should be used to construct one of these.  DebateFormat
 * doesn't know about how formats are specified (in e.g. XML), it just knows about its speech
 * formats.
 *
 * @author Chuan-Zheng Lee
 * @since  2012-06-02
 */
class DebateFormat {

    /** The name of this debate format. */
    var name: String = ""

    /** The short name of this debate format. */
    var shortName: String? = null

    private var mPrepTimeFormat: PrepTimeFormat? = null
    private val mSpeechFormats = HashMap<String, SpeechFormat>()
    private val mSpeechSpecs = ArrayList<SpeechSpec>()

    /**
     * Thrown when a speech is added with a speech format that is not currently defined.
     */
    class NoSuchFormatException(detailMessage: String) : Exception(detailMessage)

    /**
     * Collection of information relating to a speech.  For internal (private) use only; future
     * implementations may do away with this class.
     */
    private class SpeechSpec(val name: String, val type: String)

    /**
     * Sets the prep format.
     * @param ptf the [PrepTimeFormat]
     */
    fun setPrepFormat(ptf: PrepTimeFormat?) {
        mPrepTimeFormat = ptf
    }

    /**
     * Adds a speech format to the internal collection of speech formats.
     * @param formatRef the key (reference) to the speech format
     * @param sf the SpeechFormat
     */
    fun addSpeechFormat(formatRef: String, sf: SpeechFormat) {
        mSpeechFormats[formatRef] = sf
    }

    /**
     * @param formatRef the key (reference) to the speech format
     * @return `true` if a speech format with this reference exists, `false` otherwise
     */
    fun hasSpeechFormat(formatRef: String): Boolean {
        return mSpeechFormats.containsKey(formatRef)
    }

    /**
     * Adds a speech to the internal list of speeches.
     * @param name The human-readable name of the speech, e.g. "1st Affirmative", "Prime Minister"
     * @param formatRef A String representing the SpeechFormat, which must have already been added
     * using [addSpeechFormat].
     */
    @Throws(NoSuchFormatException::class)
    fun addSpeech(name: String, formatRef: String) {
        // The speech type must already exist.
        if (!mSpeechFormats.containsKey(formatRef)) {
            throw NoSuchFormatException(
                    String.format("Added a speech with non-existent format ref '%s'", formatRef))
        }
        mSpeechSpecs.add(SpeechSpec(name, formatRef))
    }

    /**
     * @return true if this format has prep time associated with it, false otherwise
     */
    fun hasPrepFormat(): Boolean {
        return mPrepTimeFormat != null
    }

    /**
     * Returns the prep format.
     * @return the prep format
     */
    fun getPrepFormat(): PrepTimeFormat? {
        return mPrepTimeFormat
    }

    /**
     * Returns the speech format for a specified speech.
     * @param index the index of the speech (0 for first speech, 1 for second, etc.)
     * @return a SpeechFormat object for that speech, or null if there is no such speech or if
     * the speech has no such format.
     */
    fun getSpeechFormat(index: Int): DebatePhaseFormat? {
        // 1. Retrieve the speech type
        val speechType = try {
            mSpeechSpecs[index].type
        } catch (e: IndexOutOfBoundsException) {
            Log.e(TAG, "Attempted to retrieve speech format for index $index")
            return null
        }

        // 2. Retrieve the speech format for that type
        val speechFormat = mSpeechFormats[speechType]

        if (speechFormat == null) {
            Log.e(TAG, "No speech format for key $speechType")
        }

        return speechFormat
    }

    /**
     * Returns the name of a specified speech.
     * @param index the index of the speech (0 for first speech, 1 for second, etc.)
     * @return the name of the speech, or null if there is no such speech.
     */
    fun getSpeechName(index: Int): String? {
        return try {
            mSpeechSpecs[index].name
        } catch (e: IndexOutOfBoundsException) {
            Log.e(TAG, "Attempted to retrieve speech name for index $index")
            null
        }
    }

    /**
     * @return the number of speeches in this debate
     */
    fun numberOfSpeeches(): Int {
        return mSpeechSpecs.size
    }

    companion object {
        private const val TAG = "DebateFormat"
    }
}
