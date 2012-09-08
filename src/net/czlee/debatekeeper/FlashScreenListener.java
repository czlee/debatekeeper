/*
 * Copyright (C) 2012 Chuan-Zheng Lee
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

package net.czlee.debatekeeper;

/**
 * This interface is passed to {@link AlertManager} via its <b>setScreenColourInverter()</b> method.
 * <code>AlertManager</code> uses it to flash the screen rapidly at bell times.
 * @author Chuan-Zheng Lee
 * @since  2012-06-27
 */
public interface FlashScreenListener {
    public void flashScreenOn(int colour);
    public void flashScreenOff();
}
