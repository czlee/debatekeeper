package com.ftechz.DebatingTimer;

import java.util.ArrayList;
import java.util.EnumMap;

/**
 *
 */
public class SpeakersManager {
    public enum SpeakerSide {
        Affirmative,
        Negative
    }

    private class Team {
        String name;
        Speaker leader;
        ArrayList<Speaker> members = new ArrayList<Speaker>();
    }

    private EnumMap<SpeakerSide, Team> mSpeakingTeams;
    private Team mTeams[];

    public SpeakersManager()
    {
        mSpeakingTeams = new EnumMap<SpeakerSide, Team>(SpeakerSide.class);

        // Create 2 teams to be filled
        mTeams = new Team[]{ new Team(), new Team() };
    }

    public Speaker getSpeaker(SpeakerSide side, int number)
    {
        Team team = mSpeakingTeams.get(side);

        if(number == 0)
        {
            return team.leader;
        }
        else
        {
            return team.members.get(number - 1);
        }
    }

    public void addSpeaker(Speaker speaker, int team, boolean leader)
    {
        mTeams[team].members.add(speaker);
        if(leader)
        {
            mTeams[team].leader = speaker;
        }
    }

    public void setSides(int team, SpeakerSide side)
    {
        mSpeakingTeams.put(side, mTeams[team]);
    }
}
