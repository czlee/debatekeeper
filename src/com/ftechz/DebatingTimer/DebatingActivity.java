package com.ftechz.DebatingTimer;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import com.ftechz.DebatingTimer.AlarmChain.Event;

/**
 * DebatingActivity The first Activity shown when application is started... for
 * now
 *
 */
public class DebatingActivity extends Activity {
	private TextView mStateText;
	private TextView mSpeakerNameText;
	private TextView mCurrentTimeText;
	private TextView mNextTimeText;
	private TextView mFinalTimeText;

	// The buttons are allocated as follows:
	// When at startOfSpeaker: [Start] [Next Speaker]
	// When running:           [Stop]
	// When stopped by user:   [Resume] [Restart] [Next Speaker]
	// When stopped by alarm:  [Resume]
	private Button leftControlButton;
	private Button centreControlButton;
	private Button rightControlButton;

	private Debate mDebate;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.debate_activity);

		mStateText = (TextView) findViewById(R.id.stateText);
		mSpeakerNameText = (TextView) findViewById(R.id.titleText);
		mCurrentTimeText = (TextView) findViewById(R.id.currentTime);
		mNextTimeText = (TextView) findViewById(R.id.nextTime);
		mFinalTimeText = (TextView) findViewById(R.id.finalTime);
		leftControlButton = (Button) findViewById(R.id.leftControlButton);
		centreControlButton = (Button) findViewById(R.id.centreControlButton);
		rightControlButton = (Button) findViewById(R.id.rightControlButton);

		leftControlButton.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View pV) {
				switch (mDebate.getDebateStatus()) {
				case StartOfSpeaker:
					mDebate.start();
					break;
				case TimerRunning:
					mDebate.stop();
					break;
				case TimerStoppedByAlarm:
				case TimerStoppedByUser:
					mDebate.resume();
					break;
				default:
					break;
				}
				updateGui();
			}
		});

		centreControlButton.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View pV) {
                switch (mDebate.getDebateStatus()) {
                case TimerStoppedByUser:
                    mDebate.resetSpeaker();
                    break;
                default:
                    break;
                }
                updateGui();
            }
        });

		rightControlButton.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View pV) {
			    switch (mDebate.getDebateStatus()) {
			    case StartOfSpeaker:
			    case TimerStoppedByUser:
			        mDebate.prepareNextSpeaker();
	                break;
                default:
                    break;
			    }
                updateGui();
            }
		});

		Intent intent = new Intent(this, DebatingTimerService.class);
		startService(intent);

		bindService(intent, mConnection, Context.BIND_AUTO_CREATE);
	}

	// Updates the buttons according to the current status of the debate
	private void updateButtons() {
		switch (mDebate.getDebateStatus()) {
		case StartOfSpeaker:
		    setButtons(R.string.startTimer, R.string.nullButtonText, R.string.nextSpeaker);
			break;
		case TimerRunning:
		    setButtons(R.string.stopTimer, R.string.nullButtonText, R.string.nullButtonText);
			break;
		case TimerStoppedByAlarm:
		    setButtons(R.string.resumeTimerAfterAlarm, R.string.nullButtonText, R.string.nullButtonText);
			break;
		case TimerStoppedByUser:
		    setButtons(R.string.resumeTimerAfterUserStop, R.string.resetTimer, R.string.nextSpeaker);
			break;
		case EndOfDebate:
			break;
		default:
			break;
		}
	}

	// Sets the text and visibility of all buttons
	private void setButtons(int leftResid, int centreResid, int rightResid) {
	    setButton(leftControlButton, leftResid);
	    setButton(centreControlButton, centreResid);
	    setButton(rightControlButton, rightResid);
	}

	// Sets the text and visibility of a single button
	private void setButton(Button button, int resid) {
	    button.setText(resid);
	    int visibility = (resid == R.string.nullButtonText) ? View.GONE : View.VISIBLE;
	    button.setVisibility(visibility);
	}

	@Override
	protected void onDestroy() {
		super.onDestroy();

		unbindService(mConnection);
		Intent intent = new Intent(this, DebatingTimerService.class);
		stopService(intent);
	}

	private String secsToMinuteSecText(long time) {
		return String.format("%02d:%02d", time / 60, time % 60);
	}

	public void updateGui() {
		if (mDebate != null) {
			mStateText.setText(mDebate.getStageStateText());
			mSpeakerNameText.setText(mDebate.getStageName());
			mCurrentTimeText.setText(secsToMinuteSecText(mDebate.getStageCurrentTime()));
			mNextTimeText.setText(String.format(
		        this.getString(R.string.nextBell),
		        secsToMinuteSecText(mDebate.getStageNextTime())
	        ));
			mFinalTimeText.setText(String.format(
                this.getString(R.string.speechLength),
                secsToMinuteSecText(mDebate.getStageFinalTime())
            ));

			updateButtons();
		}
	}

	// Second tick broadcast
	private final BroadcastReceiver broadcastReceiver = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) {
			updateGui();
		}
	};

	@Override
	public void onResume() {
		super.onResume();
		registerReceiver(broadcastReceiver, new IntentFilter(
				DebatingTimerService.BROADCAST_ACTION));
	}

	@Override
	public void onPause() {
		super.onPause();
		unregisterReceiver(broadcastReceiver);
	}

	private DebatingTimerService.DebatingTimerServiceBinder mBinder;

	/** Defines callbacks for service binding, passed to bindService() */
	private final ServiceConnection mConnection = new ServiceConnection() {

		@Override
		public void onServiceConnected(ComponentName className, IBinder service) {

			mBinder = (DebatingTimerService.DebatingTimerServiceBinder) service;
			mDebate = mBinder.getDebate();
			if (mDebate == null) {
				mDebate = mBinder.createDebate();
				setupDefaultDebate(mDebate);
				mDebate.resetSpeaker();
			}
		}

		@Override
		public void onServiceDisconnected(ComponentName arg0) {
			mDebate = null;
		}
	};

	// TODO: Remove this from ConfigActivity (it is called setupDebate() there)
	public void setupDefaultDebate(Debate debate) {
		// prepAlerts = new AlarmChain.AlarmChainAlert[] {
		// new SpeakerTimer.WarningAlert(60), // 1 minute
		// new SpeakerTimer.WarningAlert(120), // 2 minutes
		// new SpeakerTimer.FinishAlert(420) // 7 minutes
		// };
		//
		// substativeSpeechAlerts = new AlarmChain.AlarmChainAlert[] {
		// new SpeakerTimer.WarningAlert(240), // 4 minutes
		// new SpeakerTimer.FinishAlert(360), // 6 minutes
		// new SpeakerTimer.OvertimeAlert(375, 15) // 6:15, repeating every 5
		// };
		//
		// replySpeechAlerts = new AlarmChain.AlarmChainAlert[] {
		// new SpeakerTimer.WarningAlert(120),
		// new SpeakerTimer.FinishAlert(180),
		// new SpeakerTimer.OvertimeAlert(195, 15)
		// };

		Event[] prepAlerts = new AlarmChain.Event[] {
				new SpeakerTimer.Event(5, 1, "Choose moot"), //
				new SpeakerTimer.Event(10, 1, "Choose side"), //
				new SpeakerTimer.Event(15, 2, "Prepare debate") //
		};

		Event[] substativeSpeechAlerts = new AlarmChain.Event[] {
                new SpeakerTimer.Event(5, 1, "Points of information allowed"),
				new SpeakerTimer.Event(10, 1, "Warning bell rung"),
				new SpeakerTimer.Event(15, 2, "Overtime"),
				new SpeakerTimer.RepeatedEvent(20, 3, 3) };

		Event[] replySpeechAlerts = new AlarmChain.Event[] {
				new SpeakerTimer.Event(3, 1, "Warning bell rung"),
				new SpeakerTimer.Event(6, 2, "Overtime"),
				new SpeakerTimer.RepeatedEvent(9, 3, 3) };

		// Set up speakers
		Team team1 = new Team();
		team1.addMember(new Speaker("1st Affirmative"), true);
		team1.addMember(new Speaker("2nd Affirmative"), false);
		team1.addMember(new Speaker("3rd Affirmative"), false);

		Team team2 = new Team();
		team2.addMember(new Speaker("1st Negative"), true);
		team2.addMember(new Speaker("2nd Negative"), false);
		team2.addMember(new Speaker("3rd Negative"), false);

		int team1Index = debate.addTeam(team1);
		int team2Index = debate.addTeam(team2);

		debate.setSide(team1Index, TeamsManager.SpeakerSide.Affirmative);
		debate.setSide(team2Index, TeamsManager.SpeakerSide.Negative);

		// Add in the alarm sets
		debate.addAlarmSet("prep", prepAlerts, 15);
		debate.addAlarmSet("substantiveSpeech", substativeSpeechAlerts, 20);
		debate.addAlarmSet("replySpeech", replySpeechAlerts, 15);

		// Add in the stages
		// debate.addStage(new PrepTimer("Preparation"), "prep");
		debate.addStage(new SpeakerTimer("1st Affirmative",
				TeamsManager.SpeakerSide.Affirmative, 1), "substantiveSpeech");
		debate.addStage(new SpeakerTimer("1st Negative",
				TeamsManager.SpeakerSide.Negative, 1), "substantiveSpeech");
		debate.addStage(new SpeakerTimer("2nd Affirmative",
				TeamsManager.SpeakerSide.Affirmative, 2), "substantiveSpeech");
		debate.addStage(new SpeakerTimer("2nd Negative",
				TeamsManager.SpeakerSide.Negative, 2), "substantiveSpeech");
		debate.addStage(new SpeakerTimer("3nd Affirmative",
				TeamsManager.SpeakerSide.Affirmative, 3), "substantiveSpeech");
		debate.addStage(new SpeakerTimer("3nd Negative",
				TeamsManager.SpeakerSide.Negative, 3), "substantiveSpeech");
		debate.addStage(new SpeakerTimer("Negative Leader's Reply",
				TeamsManager.SpeakerSide.Negative, 0), "replySpeech");
		debate.addStage(new SpeakerTimer("Affirmative Leader's Reply",
				TeamsManager.SpeakerSide.Affirmative, 0), "replySpeech");
	}

}
