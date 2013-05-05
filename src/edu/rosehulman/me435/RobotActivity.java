package edu.rosehulman.me435;



import java.util.ArrayList;
import java.util.Timer;
import java.util.TimerTask;

import android.location.Location;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.WindowManager;

public class RobotActivity extends SpeechAccessoryActivity implements FieldGpsListener,
FieldOrientationListener {

	private static final String TAG = RobotActivity.class.getSimpleName();
	/** Field GPS instance that gives field feet and field bearings. */
	protected FieldGps mFieldGps;

	/** Field orientation instance that gives field heading via sensors. */
	protected FieldOrientation mFieldOrientation;

	
	// GPS member variables.
	protected double mCurrentGpsX, mCurrentGpsY, mCurrentGpsHeading;
	protected int mGpsCounter = 0;
	public static final double NO_HEADING = 360.0;
	protected ArrayList<Double> mSavedGpsXValues = new ArrayList<Double>();
	protected ArrayList<Double> mSavedGpsYValues = new ArrayList<Double>();
	protected ArrayList<Double> mSavedGpsHeadings = new ArrayList<Double>();
	protected ArrayList<Double> mSavedGpsDistances = new ArrayList<Double>();
	protected int mGettingFartherAwayCounter = 0;

	// Movement
	protected double mCurrentSensorHeading;
	protected boolean mMovingForward = false;
	protected boolean mMovingStraight = false;
	protected double mGuessX, mGuessY;
	public static final double DEFAULT_SPEED_FT_PER_SEC = 3.0;
	protected int mLeftDutyCycle, mRightDutyCycle;
	public static final String WHEEL_MODE_REVERSE = "REVERSE";
	public static final String WHEEL_MODE_BRAKE = "BRAKE";
	public static final String WHEEL_MODE_FORWARD = "FORWARD";
	
	
	// Timing
	protected Timer mTimer;
	public static final int LOOP_INTERVAL_MS = 100;
	protected Handler mCommandHandler = new Handler();
	
	// Voice
	protected int mVoiceCommandAngle, mVoiceCommandDistance;
	
	// Field GPS locations
	// TODO: Change as necessary to match the field.
	public static final double RED_HOME_LATITUDE = 39.48579108058;
	public static final double RED_HOME_LONGITUDE = -87.32197124182;
	public static final double BLUE_HOME_LATITUDE = 39.48606291942;
	public static final double BLUE_HOME_LONGITUDE = -87.32202075818;
	
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

		double currentGpsDistance = NavUtils.getDistance(mCurrentGpsX, mCurrentGpsY, 0, 0);
		int lastGpsReadingIndex = mSavedGpsDistances.size() - 1;
		double oldGpsDistance = lastGpsReadingIndex < 0 ? 1000 : mSavedGpsDistances.get(lastGpsReadingIndex);
		if (currentGpsDistance > oldGpsDistance) {
			mGettingFartherAwayCounter++;
		} else if (currentGpsDistance < oldGpsDistance) {
			mGettingFartherAwayCounter = 0;
		}
		
		// If the vehicle is currently going straight and heading is present.
	    if (heading < 180.0 && heading > -180.0) {
			mCurrentGpsHeading = heading;
	    	if (mMovingStraight) {
		        mFieldOrientation.setCurrentFieldHeading(mCurrentGpsHeading);
	    	}
	    }
	    
	    // Save all the GPS info.
	    mSavedGpsXValues.add(mCurrentGpsX);
		mSavedGpsYValues.add(mCurrentGpsY);
		mSavedGpsHeadings.add(mCurrentGpsHeading);
		mSavedGpsDistances.add(currentGpsDistance);
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
}
