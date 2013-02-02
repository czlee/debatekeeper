package net.czlee.debatekeeper;


public interface PrepTimeFormat extends SpeechOrPrepFormat {

    /* (non-Javadoc)
     * @see net.czlee.debatekeeper.SpeechOrPrepFormat#getSpeechLength()
     */
    @Override
    public abstract long getLength();

    /* (non-Javadoc)
     * @see net.czlee.debatekeeper.SpeechOrPrepFormat#getFirstPeriodInfo()
     */
    @Override
    public abstract PeriodInfo getFirstPeriodInfo();

    /* (non-Javadoc)
     * @see net.czlee.debatekeeper.SpeechOrPrepFormat#getBellAtTime(long)
     */
    @Override
    public abstract BellInfo getBellAtTime(long seconds);

    /* (non-Javadoc)
     * @see net.czlee.debatekeeper.SpeechOrPrepFormat#getPeriodInfoForTime(long)
     */
    @Override
    public abstract PeriodInfo getPeriodInfoForTime(long seconds);

    /**
     * @return <code>true</code> if this format is "controlled" prep time
     */
    public abstract boolean isControlled();

}