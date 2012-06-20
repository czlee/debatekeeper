package com.ftechz.DebatingTimer;

/**
 * Enumerated constants for the second-level context of a debate format XML file.
 * Also provides an overridden <code>toString()</code> method.
 * @author Chuan-Zheng Lee
 * @since  2012-06-12
 */
public enum DebateFormatXmlSecondLevelContextType {
    NONE ("no context"),
    INFO ("info"),
    RESOURCE ("resource"),
    SPEECH_FORMAT("speech format"),
    SPEECHES_LIST("speeches list");

    private final String name;

    private DebateFormatXmlSecondLevelContextType(String name){
        this.name = name;
    }

    @Override
    public String toString() {
        return name;
    }
}