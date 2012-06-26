package com.ftechz.DebatingTimer;

/**
 * This interface is passed to {@link AlertManager} via its <b>setScreenColourInverter()</b> method.
 * <code>AlertManager</code> uses it to flash the screen rapidly at bell times.
 * @author Chuan-Zheng Lee
 * @since  2012-06-27
 */
public interface FlashScreenListener {
    public void flashScreen(boolean invert);
}
