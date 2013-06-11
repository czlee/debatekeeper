package net.czlee.debatekeeper.debateformat;


/**
 * <p>PrepTimeFormat is an extension of {@link DebatePhaseFormat} that adds methods relevant
 * only to prep times.</p>
 * @author Chuan-Zheng Lee
 *
 */
public interface PrepTimeFormat extends DebatePhaseFormat {

    /**
     * @return <code>true</code> if this format is "controlled" prep time
     */
    public abstract boolean isControlled();

}