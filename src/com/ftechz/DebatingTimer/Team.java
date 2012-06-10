package com.ftechz.DebatingTimer;

import java.util.ArrayList;

/**
 * <b> OBSOLETE, DO NOT USE </b>
 *
 */
public class Team {
    private String mName;
    private Speaker mLeader;
    private final ArrayList<Speaker> mMembers = new ArrayList<Speaker>();

    public String getName() {
        return mName;
    }

    public void setName(String name) {
        mName = name;
    }

    public Speaker getLeader() {
        return mLeader;
    }

    public void setLeader(Speaker leader) {
        mLeader = leader;
    }

    public Speaker getMember(int number) {
        if (number < mMembers.size()) {
            return mMembers.get(number);
        } else {
            return null;
        }
    }

    public void addMember(Speaker speaker, boolean leader) {
        mMembers.add(speaker);
        if (leader) {
            setLeader(speaker);
        }
    }
}
