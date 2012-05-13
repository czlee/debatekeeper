package com.ftechz.DebatingTimer;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.FragmentTransaction;
import android.view.View;
import android.widget.Button;
import android.widget.TabHost;
import android.widget.TabHost.TabContentFactory;
import java.util.HashMap;

/**
 * This activity allows the user to configure a debate.
 * 
 * TODO: In the future, this Activity should be subordinate to DebatingActivity.
 * It should allow the user to:
 *   - Change the current configuration (which should be part of the saved state for DebatingActivity)
 *   - Define new configurations and remove configurations
 */
public class ConfigActivity extends FragmentActivity implements TabHost.OnTabChangeListener {
    private TabHost mTabHost;
    private HashMap<String, TabInfo> mMapTabInfo = new HashMap<String, TabInfo>();
    private TabInfo mLastTab = null;
    private Button createDebateButton;

    private AlarmChain.Event prepAlerts[];
    private AlarmChain.Event substativeSpeechAlerts[];
    private AlarmChain.Event replySpeechAlerts[];

    private class TabInfo {
        private String tag;
        private Class nTabClass;
        private Bundle args;
        public Fragment mFragment;
        TabInfo(String tag, Class tabClass, Bundle args) {
            this.tag = tag;
            this.nTabClass = tabClass;
            this.args = args;
        }

    }

    private class TabFactory implements TabContentFactory {

        private final Context mContext;

        public TabFactory(Context context) {
            mContext = context;
        }

        @Override
        public View createTabContent(String tag) {
            View v = new View(mContext);
            v.setMinimumWidth(0);
            v.setMinimumHeight(0);
            return v;
        }
    }

    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.configure);

        createDebateButton = (Button) findViewById(R.id.createDebateButton);
        createDebateButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View pV) {
                if(mBinder != null)
                {
                    Debate debate =  mBinder.createDebate();
                    setupDebate(debate);
                    Intent intent = new Intent(ConfigActivity.this, DebatingActivity.class);
                    startActivity(intent);
                }
            }
        });

        // Setup TabHost
        initialiseTabHost(savedInstanceState);

        if (savedInstanceState != null) {
            mTabHost.setCurrentTabByTag(savedInstanceState.getString("tab")); //set the tab as per the saved state
        }

        Intent intent = new Intent(this, DebatingTimerService.class);
        startService(intent);

        bindService(intent, mConnection, Context.BIND_AUTO_CREATE);
    }

    protected void onSaveInstanceState(Bundle outState) {
        outState.putString("tab", mTabHost.getCurrentTabTag()); //save the tab selected
        super.onSaveInstanceState(outState);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        unbindService(mConnection);
        Intent intent = new Intent(this, DebatingTimerService.class);
        stopService(intent);
    }

    /*
     * Setup TabHost
     */
    private void initialiseTabHost(Bundle args) {
        mTabHost = (TabHost)findViewById(android.R.id.tabhost);
        mTabHost.setup();
        TabInfo tabInfo;
        ConfigActivity.addTab(this, this.mTabHost,
                this.mTabHost.newTabSpec("Type").setIndicator("Type"),
                ( tabInfo = new TabInfo("Type", ConfigTypeFragment.class, args)));
        this.mMapTabInfo.put(tabInfo.tag, tabInfo);
        ConfigActivity.addTab(this, this.mTabHost,
                this.mTabHost.newTabSpec("Speakers").setIndicator("Speakers"),
                (tabInfo = new TabInfo("Speakers", ConfigSpeakersFragment.class, args)));
        this.mMapTabInfo.put(tabInfo.tag, tabInfo);

        // Default to first tab
        this.onTabChanged("Type");
        //
        mTabHost.setOnTabChangedListener(this);
    }

    private static void addTab(ConfigActivity activity, TabHost tabHost, TabHost.TabSpec tabSpec, TabInfo tabInfo) {
        // Attach a Tab view factory to the spec
        tabSpec.setContent(activity.new TabFactory(activity));
        String tag = tabSpec.getTag();

        // Check to see if we already have a fragment for this tab, probably
        // from a previously saved state.  If so, deactivate it, because our
        // initial state is that a tab isn't shown.
        tabInfo.mFragment = activity.getSupportFragmentManager().findFragmentByTag(tag);
        if (tabInfo.mFragment != null && !tabInfo.mFragment.isDetached()) {
            FragmentTransaction ft = activity.getSupportFragmentManager().beginTransaction();
            ft.detach(tabInfo.mFragment);
            ft.commit();
            activity.getSupportFragmentManager().executePendingTransactions();
        }

        tabHost.addTab(tabSpec);
    }

    public void onTabChanged(String tag) {
        TabInfo newTab = (TabInfo) this.mMapTabInfo.get(tag);
        if (mLastTab != newTab) {
            FragmentTransaction ft = this.getSupportFragmentManager().beginTransaction();
            if (mLastTab != null) {
                if (mLastTab.mFragment != null) {
                    ft.detach(mLastTab.mFragment);
                }
            }
            if (newTab != null) {
                if (newTab.mFragment == null) {
                    newTab.mFragment = Fragment.instantiate(this,
                            newTab.nTabClass.getName(), newTab.args);
                    ft.add(R.id.realtabcontent, newTab.mFragment, newTab.tag);
                } else {
                    ft.attach(newTab.mFragment);
                }
            }

            mLastTab = newTab;
            ft.commit();
            this.getSupportFragmentManager().executePendingTransactions();
        }
    }

    private DebatingTimerService.DebatingTimerServiceBinder mBinder;

    /** Defines callbacks for service binding, passed to bindService() */
    private ServiceConnection mConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName className,
                                       IBinder service) {

            mBinder = (DebatingTimerService.DebatingTimerServiceBinder) service;
        }

        @Override
        public void onServiceDisconnected(ComponentName arg0) {

        }
    };

    // TODO: Move this to DebatingActivity and delete it from here
    public void setupDebate(Debate debate)
    {
//        prepAlerts = new AlarmChain.AlarmChainAlert[] {
//                new SpeakerTimer.WarningAlert(60),  // 1 minute
//                new SpeakerTimer.WarningAlert(120), // 2 minutes
//                new SpeakerTimer.FinishAlert(420)   // 7 minutes
//        };
//
//        substativeSpeechAlerts = new AlarmChain.AlarmChainAlert[] {
//                new SpeakerTimer.WarningAlert(240), // 4 minutes
//                new SpeakerTimer.FinishAlert(360),  // 6 minutes
//                new SpeakerTimer.OvertimeAlert(375, 15)  // 6:15, repeating every 5
//        };
//
//        replySpeechAlerts = new AlarmChain.AlarmChainAlert[] {
//                new SpeakerTimer.WarningAlert(120),
//                new SpeakerTimer.FinishAlert(180),
//                new SpeakerTimer.OvertimeAlert(195, 15)
//        };

        prepAlerts = new AlarmChain.Event[] {
                new SpeakerTimer.Event(5, 1),  //
                new SpeakerTimer.Event(10, 1), //
                new SpeakerTimer.Event(15, 2)   //
        };

        substativeSpeechAlerts = new AlarmChain.Event[] {
                new SpeakerTimer.Event(5, 1),
                new SpeakerTimer.Event(10, 2),
                new SpeakerTimer.RepeatedEvent(15, 3, 3)
        };

        replySpeechAlerts = new AlarmChain.Event[] {
                new SpeakerTimer.Event(3, 1),
                new SpeakerTimer.Event(6, 2),
                new SpeakerTimer.RepeatedEvent(9, 3, 3)
        };

        // Set up speakers
        ConfigSpeakersFragment speakersFragment = (ConfigSpeakersFragment) mMapTabInfo.get("Speakers").mFragment;
        Team team1 = new Team();
        team1.addMember(new Speaker(speakersFragment.speaker1Field.getText().toString()), true);
        team1.addMember(new Speaker(speakersFragment.speaker2Field.getText().toString()), false);
        
        Team team2 = new Team();
        team2.addMember(new Speaker(speakersFragment.speaker3Field.getText().toString()), true);
        team2.addMember(new Speaker(speakersFragment.speaker4Field.getText().toString()), false);

        int team1Index = debate.addTeam(team1);
        int team2Index = debate.addTeam(team2);

        debate.setSide(team1Index, TeamsManager.SpeakerSide.Affirmative);
        debate.setSide(team2Index, TeamsManager.SpeakerSide.Negative);

        //Add in the alarm sets
        debate.addAlarmSet("prep", prepAlerts, 15);
        debate.addAlarmSet("substantiveSpeech", substativeSpeechAlerts, 15);
        debate.addAlarmSet("replySpeech", replySpeechAlerts, 9);

        // Add in the stages
        debate.addStage(new PrepTimer("Preparation"), "prep");
        debate.addStage(
                new SpeakerTimer("1st Affirmative", TeamsManager.SpeakerSide.Affirmative, 1),
                "substantiveSpeech");
        debate.addStage(
                new SpeakerTimer("1st Negative", TeamsManager.SpeakerSide.Negative, 1),
                "substantiveSpeech");
        debate.addStage(
                new SpeakerTimer("2nd Affirmative", TeamsManager.SpeakerSide.Affirmative, 2),
                "substantiveSpeech");
        debate.addStage(
                new SpeakerTimer("2nd Negative", TeamsManager.SpeakerSide.Negative, 2),
                "substantiveSpeech");
        debate.addStage(
                new SpeakerTimer("Negative Leader's Reply", TeamsManager.SpeakerSide.Negative, 0),
                "replySpeech");
        debate.addStage(
                new SpeakerTimer("Affirmative Leader's Reply", TeamsManager.SpeakerSide.Affirmative, 0),
                "replySpeech");
    }
}