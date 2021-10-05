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

package net.czlee.debatekeeper;

import android.content.Context;
import androidx.viewpager.widget.ViewPager;
import android.util.AttributeSet;
import android.view.MotionEvent;

/**
 * EnableableViewPager is a subclass of {@link ViewPager} that allows for paging to be enabled
 * or disabled.  Enable or disable paging by using the <code>setPagingEnabled(boolean)</code>
 * method.  This implementation allows for paging to be enabled or disabled in the middle of
 * a swipe gesture: when enabled, it starts the gesture from wherever it is at the time; when
 * disabled, it cancels the gesture.
 *
 * @author Chuan-Zheng Lee
 *
 */
public class EnableableViewPager extends ViewPager {

    private boolean mLastPagingEnabled = false;
    private boolean mPagingEnabled = true;

    public EnableableViewPager(Context context) {
        super(context);
    }

    public EnableableViewPager(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        boolean execute = false;

        // Allow for situations where paging becomes enabled or disabled in the middle of a touch.
        // This can create awkward situations where a gesture starts to be seen in the middle of it
        // (so appears not to have started) or is cut off (so appears not to have finished).
        // We catch these out by tracking mPagingEnabled and modifying the action if it appears
        // to have changed since the last touch event.  If paging has just been enabled and it
        // looks like we're in the middle of an gesture, change it to the beginning of a gesture,
        // i.e. ACTION_DOWN.  If paging has just been disabled, change it to an ACTION_CANCEL so
        // that the ViewPager will scroll back to the current item.
        if (mLastPagingEnabled && !mPagingEnabled) {
            event.setAction(MotionEvent.ACTION_CANCEL);
            execute = true;
        } else if (!mLastPagingEnabled && mPagingEnabled && event.getAction() == MotionEvent.ACTION_MOVE) {
            event.setAction(MotionEvent.ACTION_DOWN);
            execute = true;
        } else if (mPagingEnabled) {
            execute = true;
        }

        mLastPagingEnabled = mPagingEnabled;

        return execute && super.onTouchEvent(event);
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent event) {
        return (mPagingEnabled || mLastPagingEnabled) && super.onInterceptTouchEvent(event);
    }

    public void setPagingEnabled(boolean enable) {
        mPagingEnabled = enable;
    }

}
