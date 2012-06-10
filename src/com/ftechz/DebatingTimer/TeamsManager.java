package com.ftechz.DebatingTimer;

import java.util.ArrayList;
import java.util.EnumMap;

/**
 * <b> OBSOLETE, DO NOT USE </b>
 *
 */
public class TeamsManager {
    public enum SpeakerSide {
        Affirmative,
        Negative
    }

    private final EnumMap<SpeakerSide, Team> mSpeakingTeams;
    private final ArrayList<Team> mTeams;

    public TeamsManager() {
        mSpeakingTeams = new EnumMap<SpeakerSide, Team>(SpeakerSide.class);

        // Create 2 teams to be filled
        mTeams = new ArrayList<Team>();
    }

    public Speaker getSpeaker(SpeakerSide side, int number) {
        Team team = mSpeakingTeams.get(side);

        if (team != null) {
            if (number == 0) {
                return team.getLeader();
            } else {
                return team.getMember(number - 1);
            }
        }
        return null;
    }

    public int addTeam(Team team) {
        mTeams.add(team);
        return mTeams.size() - 1;
    }

    public void setSide(int teamIndex, SpeakerSide side) {
        mSpeakingTeams.put(side, mTeams.get(teamIndex));
    }
}
