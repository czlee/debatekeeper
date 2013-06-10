package net.czlee.debatekeeper;

import android.content.Context;
import android.support.v4.view.ViewPager;
import android.util.AttributeSet;
import android.view.MotionEvent;

/**
 * Adapted from:
 * http://blog.svpino.com/2011/08/disabling-pagingswiping-on-android.html
 * http://stackoverflow.com/questions/7814017/disable-viewpager
 */
public class EnableableViewPager extends ViewPager {

    public interface PagingEnabledIndicator {
        public abstract boolean isPagingEnabled();
    }

    private PagingEnabledIndicator mPagingEnabledIndicator;

    public EnableableViewPager(Context context) {
        super(context);
    }

    public EnableableViewPager(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (this.isPagingEnabled())
            return super.onTouchEvent(event);
        return false;
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent event) {
        if (this.isPagingEnabled())
            return super.onInterceptTouchEvent(event);
        return false;
    }

    public void setPagingEnabledIndicator(PagingEnabledIndicator indicator) {
        this.mPagingEnabledIndicator = indicator;
    }

    private boolean isPagingEnabled() {
        if (mPagingEnabledIndicator != null)
            return mPagingEnabledIndicator.isPagingEnabled();
        else
            return true;
    }

}
