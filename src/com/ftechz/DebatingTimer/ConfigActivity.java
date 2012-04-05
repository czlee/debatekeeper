package com.ftechz.DebatingTimer;

import android.content.Context;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.FragmentTransaction;
import android.view.View;
import android.widget.Button;
import android.widget.TabHost;
import android.widget.TabHost.TabContentFactory;
import java.util.HashMap;

/**
 * Activity for configuring a debate
 */
public class ConfigActivity extends FragmentActivity implements TabHost.OnTabChangeListener {
    private TabHost mTabHost;
    private HashMap mMapTabInfo = new HashMap();
    private TabInfo mLastTab = null;
    private Button createDebateButton;

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

            }
        });

        // Setup TabHost
        initialiseTabHost(savedInstanceState);

        if (savedInstanceState != null) {
            mTabHost.setCurrentTabByTag(savedInstanceState.getString("tab")); //set the tab as per the saved state
        }
    }

    protected void onSaveInstanceState(Bundle outState) {
        outState.putString("tab", mTabHost.getCurrentTabTag()); //save the tab selected
        super.onSaveInstanceState(outState);
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

}