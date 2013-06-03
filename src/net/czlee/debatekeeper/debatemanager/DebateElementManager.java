package net.czlee.debatekeeper.debatemanager;

import net.czlee.debatekeeper.AlertManager;
import net.czlee.debatekeeper.DebatingTimerService.GuiUpdateBroadcastSender;

/**
 * DebateElementManager is an abstract base class for classes that manage elements that
 * are part of a debate.
 *
 * <p>Current known subclasses: {@link MainTimerManager}, {@link PoiManager}.</p>
 *
 * @author Chuan-Zheng Lee
 * @since 2012-09-09
 */
public abstract class DebateElementManager {

    protected final AlertManager mAlertManager;

    // Abstract methods
    public abstract boolean isRunning();
    public abstract void start();
    public abstract void stop();

    private GuiUpdateBroadcastSender mBroadcastSender;

    protected static final long TIMER_DELAY = 1000;
    protected static final long TIMER_PERIOD = 1000;

    public DebateElementManager(AlertManager am) {
        super();
        this.mAlertManager = am;
    }

    //******************************************************************************************
    // Public methods
    //******************************************************************************************

    /**
     * Sets a broadcast sender for this speech manager.
     * <code>MainTimerManager</code> will call <code>sendBroadcast()</code> on the broadcast sender
     * when the timer counts up/down.
     * @param sender the {@link GuiUpdateBroadcastSender}
     */
    public void setBroadcastSender(GuiUpdateBroadcastSender sender) {
        this.mBroadcastSender = sender;
    }

    //******************************************************************************************
    // Protected methods
    //******************************************************************************************

    protected void sendBroadcast() {
        if (mBroadcastSender != null)
            mBroadcastSender.sendBroadcast();
    }

}