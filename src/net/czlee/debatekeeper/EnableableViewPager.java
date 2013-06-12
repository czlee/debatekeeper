/*
 * Adapted from:
 * http://blog.svpino.com/2011/08/disabling-pagingswiping-on-android.html
 * http://stackoverflow.com/questions/7814017/disable-viewpager
 */

package net.czlee.debatekeeper;

import android.content.Context;
import android.support.v4.view.ViewPager;
import android.util.AttributeSet;
import android.view.MotionEvent;

public class EnableableViewPager extends ViewPager {

    private boolean mPagingEnabled = true;

    public EnableableViewPager(Context context) {
        super(context);
    }

    public EnableableViewPager(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (mPagingEnabled)
            return super.onTouchEvent(event);
        return false;
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent event) {
        if (mPagingEnabled)
            return super.onInterceptTouchEvent(event);
        return false;
    }

    public void setPagingEnabled(boolean enable) {
        this.mPagingEnabled = enable;
    }

}
