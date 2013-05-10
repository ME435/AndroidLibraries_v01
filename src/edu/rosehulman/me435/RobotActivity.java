package edu.rosehulman.me435;



import java.util.ArrayList;
import java.util.Timer;
import java.util.TimerTask;


import android.location.Location;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.WindowManager;

public class RobotActivity extends TtsAccessoryActivity implements FieldGpsListener,
FieldOrientationListener {

	/** TAG used in log messages set to RobotActivity. */
	private static final String TAG = RobotActivity.class.getSimpleName();
	
	/** Field GPS instance that gives field feet and field bearings. */
	protected FieldGps mFieldGps;

	/** Field orientation instance that gives field heading via sensors. */
	protected FieldOrientation mFieldOrientation;
	
	// GPS member variables.
	/** Most recent readings of the GPS. */
	protected double mCurrentGpsX, mCurrentGpsY, mCurrentGpsHeading;
	
	/** Counter that tracks the total number of GPS readings. */
	protected int mGpsCounter = 0;
	
	/** Headings are between -180 and 180, when no heading is given use this. */
	public static final double NO_HEADING = 360.0;

	/**
	 * Sometimes the GPS doesn't give headings, and sometimes the sensor heading
	 * is just plain wrong.  In that case, the best guess you've got is to
	 * use the last two GPS readings and calculate a heading from the X and Y
	 * values.  This is sometimes useful as a backup plan.
	 */
	protected double mCurrentCalculatedGpsHeading;

	/** Current distance of the GPS reading to 0, 0. */
	protected double mCurrentGpsDistance;
	
	/** Array that holds every GPS X value ever read. */
	protected ArrayList<Double> mSavedGpsXValues = new ArrayList<Double>();
	
	/** Array that holds every GPS Y value ever read. */
	protected ArrayList<Double> mSavedGpsYValues = new ArrayList<Double>();

	/** Array that holds every GPS heading value ever read. */
	protected ArrayList<Double> mSavedGpsHeadings = new ArrayList<Double>();

	/** Array that holds every GPS distance away from 0, 0. */
	protected ArrayList<Double> mSavedGpsDistances = new ArrayList<Double>();

	/** Array that holds every calculated GPS heading.  Note, this one does
	 * NOT come from the GPS, it's simple math on the last two GPS points. */
	protected ArrayList<Double> mSavedCalculatedGpsHeadings = new ArrayList<Double>();
	
	/** Counter that track how many GPS reading in a row farther away. */
	protected int mGettingFartherAwayCounter = 0;
	
	/** When testing you can speak all GPS readings.  Set to false to turn off. */
	protected boolean mTalkingGps = false;

	/** When testing you can beep for all GPS readings. */
	protected boolean mBeepingGps = true;
	
	// Movement
	/** Most recent sensor heading (updates MANY times per second). */
	protected double mCurrentSensorHeading;
	
	/** Boolean set to true when the robot is moving forward. */
	protected boolean mMovingForward = false;
	
	/** Boolean set to true when the robot is moving forward in a straight line. */
	protected boolean mMovingStraight = false;
	
	/** Guess at the XY value based on the last GPS reading and current speed. */
	protected double mGuessX, mGuessY;
	
	/** Simple default robot speed used to determine the guess XY (adjust as necessary). */
	public static final double DEFAULT_SPEED_FT_PER_SEC = 3.0;
	
	/** Current wheel duty cycle.  Note always use sendWheelSpeed for robot commands. */
	protected int mLeftDutyCycle, mRightDutyCycle;
	
	/** Simple constants used to define the magic communication words for wheel modes. */
	public static final String WHEEL_MODE_REVERSE = "REVERSE";
	public static final String WHEEL_MODE_BRAKE = "BRAKE";
	public static final String WHEEL_MODE_FORWARD = "FORWARD";
	
	
	// Timing
	/** Timer used to magically call the loop function. */
	protected Timer mTimer;
	
	/** Interval that sets how often the loop function is called. */
	public static final int LOOP_INTERVAL_MS = 100;
	
	/** Magic tool we use to execute code after a delay. */
	protected Handler mCommandHandler = new Handler();
	
	// Voice
	/** Most recent voice command angle and distance values. */
	protected int mVoiceCommandAngle, mVoiceCommandDistance;
	
	// Field GPS locations
	// TODO: Change as necessary to match the field.
	/** Latitude and Longitude values of the field home bases. */
	public static final double RED_HOME_LATITUDE = 39.48579108058;
	public static final double RED_HOME_LONGITUDE = -87.32197124182;
	public static final double BLUE_HOME_LATITUDE = 39.48606291942;
	public static final double BLUE_HOME_LONGITUDE = -87.32202075818;
	
	/** Function called 10 times per second. */
	public void loop() {
		if (mMovingForward) {
			mGuessX += DEFAULT_SPEED_FT_PER_SEC * (double)LOOP_INTERVAL_MS / 1000.0 * Math.cos(Math.toRadians(mCurrentSensorHeading));
			mGuessY += DEFAULT_SPEED_FT_PER_SEC * (double)LOOP_INTERVAL_MS / 1000.0 * Math.sin(Math.toRadians(mCurrentSensorHeading));			
		}
		// Do more in subclass.
	};
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

		// Assume you are on the red team to start the app (can be changed later).
		mFieldGps = new FieldGps(this, RED_HOME_LATITUDE, RED_HOME_LONGITUDE, BLUE_HOME_LATITUDE, BLUE_HOME_LONGITUDE);
		mFieldOrientation = new FieldOrientation(this, RED_HOME_LATITUDE, RED_HOME_LONGITUDE, BLUE_HOME_LATITUDE, BLUE_HOME_LONGITUDE);
	}
	
	public void setTeamToRed(boolean isRed) {
		float[] originToXAxisLocation = new float[2];
	    Location redHome = new Location("Fake");
		redHome.setLatitude(RED_HOME_LATITUDE);
		redHome.setLongitude(RED_HOME_LONGITUDE);
		Location blueHome = new Location("Fake");
		blueHome.setLatitude(BLUE_HOME_LATITUDE);
		blueHome.setLongitude(BLUE_HOME_LONGITUDE);
		if (isRed) {
			mFieldGps.setLocationOnXAxis(blueHome);
			mFieldGps.setOriginLocation(redHome);
			// Borrowing skills from a FieldOrientation constructor.
		    Location.distanceBetween(RED_HOME_LATITUDE, RED_HOME_LONGITUDE, BLUE_HOME_LATITUDE, BLUE_HOME_LONGITUDE,
		        originToXAxisLocation);
		} else {
			// Swap red and blue.
			mFieldGps.setLocationOnXAxis(redHome);
			mFieldGps.setOriginLocation(blueHome);
		    Location.distanceBetween(BLUE_HOME_LATITUDE, BLUE_HOME_LONGITUDE, RED_HOME_LATITUDE, RED_HOME_LONGITUDE,
			        originToXAxisLocation);
		}
		Log.d(TAG, "Setting field bearing to " + originToXAxisLocation[1]);
		mFieldOrientation.setFieldBearing(originToXAxisLocation[1]);
	}
	

	@Override
	public void onLocationChanged(double x, double y, double heading,
			Location location) {
		mGpsCounter++;
		mCurrentGpsX = x;
		mCurrentGpsY = y;
		mCurrentGpsHeading = NO_HEADING;
		mGuessX = mCurrentGpsX;
		mGuessY = mCurrentGpsY;

		mCurrentGpsDistance = NavUtils.getDistance(mCurrentGpsX, mCurrentGpsY, 0, 0);
		int lastGpsReadingIndex = mSavedGpsDistances.size() - 1;
		double oldGpsDistance = lastGpsReadingIndex < 0 ? 1000 : mSavedGpsDistances.get(lastGpsReadingIndex);
		if (mCurrentGpsDistance > oldGpsDistance) {
			mGettingFartherAwayCounter++;
		} else if (mCurrentGpsDistance < oldGpsDistance) {
			mGettingFartherAwayCounter = 0;
		}

	    // Not sure if calculated GPS Heading is useful, but here it is.
	    // Simply uses the last two GPS readings to determine the heading.
		if (mSavedGpsXValues.size() > 0) {
			double oldGpsX = mSavedGpsXValues.get(mSavedGpsXValues.size() - 1);
			double oldGpsY = mSavedGpsYValues.get(mSavedGpsYValues.size() - 1);
			mCurrentCalculatedGpsHeading = NavUtils.getTargetHeading(oldGpsX, oldGpsY, mCurrentGpsX, mCurrentGpsY);
		} else {
			mCurrentCalculatedGpsHeading = NO_HEADING;
		}
		
		// If the vehicle is currently going straight and heading is present.
	    if (heading < 180.0 && heading > -180.0) {
			mCurrentGpsHeading = heading;
	    	if (mMovingStraight) {
		        mFieldOrientation.setCurrentFieldHeading(mCurrentGpsHeading);
		        if (mTalkingGps) {
		        	speak("GPS X " + Math.round(mCurrentGpsX) + " Y " + Math.round(mCurrentGpsY) +
		        			" heading " + Math.round(mCurrentSensorHeading) + " used to reset sensor");
		        }
	    	} else {
	    		if (mTalkingGps) {
		        	speak("GPS X " + Math.round(mCurrentGpsX) + " Y " + Math.round(mCurrentGpsY) +
		        			" with heading " + Math.round(mCurrentSensorHeading));
	    		}
			}
	        if (mBeepingGps) {
	        	headingBeep();
	        }
	    } else {
	    	if (mTalkingGps) {
	    		speak("GPS X " + Math.round(mCurrentGpsX) + " Y " + Math.round(mCurrentGpsY));
	    	}
	        if (mBeepingGps) {
	        	playNotificationBeep();
	        }
	        // Consider reseting the sensor heading using the calculated heading.
	        int calculatedGpsTrustThresholdCount = 3;
	        if (mMovingStraight && mSavedCalculatedGpsHeadings.size() > calculatedGpsTrustThresholdCount) {
		        boolean resetSensorHeadingToCalculatedGpsHeading = true;
		        double calculatedGpsTrustThresholdAngle = 15.0;
		        double oldCalculatedGpsHeading;
		        for (int i = 0; i < calculatedGpsTrustThresholdCount; i++) {
		        	oldCalculatedGpsHeading = mSavedCalculatedGpsHeadings.get(mSavedGpsXValues.size() - i - 1);
		        	if (Math.abs(mCurrentCalculatedGpsHeading - oldCalculatedGpsHeading) > calculatedGpsTrustThresholdAngle) {
		        		resetSensorHeadingToCalculatedGpsHeading = false;
		        		break;
		        	}
		        }
		        if (resetSensorHeadingToCalculatedGpsHeading) {
			        mFieldOrientation.setCurrentFieldHeading(mCurrentCalculatedGpsHeading);
			        // Note, I could take an average of the last few readings instead.
			        if (mTalkingGps) {
			        	speak("GPS calculated heading " + Math.round(mCurrentCalculatedGpsHeading) + " used to reset sensor");
			        }
			        if (mBeepingGps) {
			        	headingBeep();
			        }
		        }	        	
	        }
	    }
		
	    // Save all the GPS info.
	    mSavedGpsXValues.add(mCurrentGpsX);
		mSavedGpsYValues.add(mCurrentGpsY);
		mSavedGpsHeadings.add(mCurrentGpsHeading);
		mSavedGpsDistances.add(mCurrentGpsDistance);
		mSavedCalculatedGpsHeadings.add(mCurrentCalculatedGpsHeading);
	}

	@Override
	public void onSensorChanged(double fieldHeading, float[] orientationValues) {
		mCurrentSensorHeading = fieldHeading;
	}

	@Override
	protected void onVoiceCommand(int angle, int distance) {
		super.onVoiceCommand(angle, distance);
		mVoiceCommandAngle = angle;
		mVoiceCommandDistance = distance;
	}
	
	@Override
	protected void onStart() {
		super.onStart();
		mTimer = new Timer();
		mTimer.scheduleAtFixedRate(new TimerTask() {
			@Override
			public void run() {
		          runOnUiThread(new Runnable() {
		            public void run() {
		            	loop();
		            }
		          });
			}
		}, 0, LOOP_INTERVAL_MS);
	    mFieldOrientation.registerListener(this);
		mFieldGps.requestLocationUpdates(this);
	}

	@Override
	protected void onStop() {
		super.onStop();
		mTimer.cancel();
		mTimer = null;
	    mFieldOrientation.unregisterListener();
		mFieldGps.removeUpdates();
	}
	
	public void sendWheelSpeed(int leftDutyCycle, int rightDutyCycle) {
		mLeftDutyCycle = leftDutyCycle;
		mRightDutyCycle = rightDutyCycle;
		// The member variable has a sign, parameter used for communication.
		leftDutyCycle = Math.abs(leftDutyCycle);
		rightDutyCycle = Math.abs(rightDutyCycle);
		String leftMode = WHEEL_MODE_BRAKE;
		String rightMode = WHEEL_MODE_BRAKE;
		if (mLeftDutyCycle < 0) {
			leftMode = WHEEL_MODE_REVERSE;
		} else if (mLeftDutyCycle > 0) {
			leftMode = WHEEL_MODE_FORWARD;
		}
		if (mRightDutyCycle < 0) {
			rightMode = WHEEL_MODE_REVERSE;
		} else if (mRightDutyCycle > 0) {
			rightMode = WHEEL_MODE_FORWARD;
		}
		// Set member variables to track movement type.
		mMovingForward = mLeftDutyCycle > 30 && mRightDutyCycle > 30;
		mMovingStraight = mLeftDutyCycle > 230 && mRightDutyCycle > 230;
		String command = "WHEEL SPEED " + leftMode + " " + leftDutyCycle +
				rightMode + " " + rightDutyCycle;
		sendCommand(command);
	}

	
	/**
	 * Helper to generate multiple beeps.  1 or 2 recommend, 3 is pushing it.
	 * Notice you may need to visit Settings -> Sound -> Volumes to turn up
	 * the Notification sound.
	 * 
	 * @param numberOfBeeps Number of beeps.
	 */
	protected void beep(int numberOfBeeps) {
		playNotificationBeep();
		long beepLength = 400;
		for (int i = 1; i < numberOfBeeps; i++) {
			mCommandHandler.postDelayed(new Runnable() {
				@Override
				public void run() {
					playNotificationBeep();
				}
			}, i * beepLength);			
		}
	}
	
	protected void headingBeep() {
		playValidRingtone();
	}
}
