package edu.rosehulman.me435;

import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.List;

import root.gast.speech.RecognizerIntentFactory;
import root.gast.speech.SpeechRecognizingActivity;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbAccessory;
import android.hardware.usb.UsbManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.util.Log;
import android.widget.Toast;

public class SpeechAccessoryActivity extends SpeechRecognizingActivity {

	private static final String TAG = SpeechAccessoryActivity.class
			.getSimpleName();
	private PendingIntent mPermissionIntent;
	private static final String ACTION_USB_PERMISSION = "edu.rosehulman.me435.action.USB_PERMISSION";
	private boolean mPermissionRequestPending;
	private UsbManager mUsbManager;
	private UsbAccessory mAccessory;
	private ParcelFileDescriptor mFileDescriptor;
	private FileInputStream mInputStream;
	private FileOutputStream mOutputStream;

	private static final String KEYWORD_ANGLE = "angle";
	private static final String KEYWORD_DISTANCE = "distance";
	private static final String KEYWORD_NEGATIVE = "negative";
	private static final int DEFAULT_DISTANCE = 10;
	protected String mRobotName = null;

	// Rx runnable.
	private Runnable mRxRunnable = new Runnable() {

		public void run() {
			int ret = 0;
			byte[] buffer = new byte[255];

			// Loop that runs forever (or until a -1 error state).
			while (ret >= 0) {
				try {
					ret = mInputStream.read(buffer);
				} catch (IOException e) {
					break;
				}

				if (ret > 0) {
					// Convert the bytes into a string.
					String received = new String(buffer, 0, ret);
					final String receivedCommand = received.trim();
					runOnUiThread(new Runnable() {
						public void run() {
							onCommandReceived(receivedCommand);
						}
					});
				}
			}
		}
	};

	/**
	 * Override this method with your activity if you'd like to receive
	 * messages.
	 * 
	 * @param receivedCommand
	 */
	protected void onCommandReceived(final String receivedCommand) {
		// Toast.makeText(this, "Received command = " + receivedCommand,
		// Toast.LENGTH_SHORT).show();
		Log.d(TAG, "Received command = " + receivedCommand);
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		mUsbManager = (UsbManager) getSystemService(Context.USB_SERVICE);
		mPermissionIntent = PendingIntent.getBroadcast(this, 0, new Intent(
				ACTION_USB_PERMISSION), 0);

		IntentFilter filter = new IntentFilter(ACTION_USB_PERMISSION);
		filter.addAction(UsbManager.ACTION_USB_ACCESSORY_DETACHED);
		registerReceiver(mUsbReceiver, filter);
	}

	private final BroadcastReceiver mUsbReceiver = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) {
			String action = intent.getAction();
			if (ACTION_USB_PERMISSION.equals(action)) {
				synchronized (this) {
					UsbAccessory accessory = (UsbAccessory) intent
							.getParcelableExtra(UsbManager.EXTRA_ACCESSORY);
					if (intent.getBooleanExtra(
							UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
						openAccessory(accessory);
					} else {
						Log.d(TAG, "permission denied for accessory "
								+ accessory);
					}
					mPermissionRequestPending = false;
				}
			} else if (UsbManager.ACTION_USB_ACCESSORY_DETACHED.equals(action)) {
				UsbAccessory accessory = (UsbAccessory) intent
						.getParcelableExtra(UsbManager.EXTRA_ACCESSORY);
				if (accessory != null && accessory.equals(mAccessory)) {
					closeAccessory();
				}
			}
		}
	};

	protected void sendCommand(String commandString) {
		new AsyncTask<String, Void, Void>() {
			@Override
			protected Void doInBackground(String... params) {
				String command = params[0];
				char[] buffer = new char[command.length() + 1];
				byte[] byteBuffer = new byte[command.length() + 1];
				command.getChars(0, command.length(), buffer, 0);
				buffer[command.length()] = '\n';
				for (int i = 0; i < command.length() + 1; i++) {
					byteBuffer[i] = (byte) buffer[i];
				}
				if (mOutputStream != null) {
					try {
						mOutputStream.write(byteBuffer);
					} catch (IOException e) {
						Log.e(TAG, "write failed", e);
					}
				}
				return null;
			}
		}.execute(commandString);
	}

	@Override
	protected void onResume() {
		super.onResume();
		if (mInputStream != null && mOutputStream != null) {
			return;
		}

		UsbAccessory[] accessories = mUsbManager.getAccessoryList();
		UsbAccessory accessory = (accessories == null ? null : accessories[0]);
		if (accessory != null) {
			if (mUsbManager.hasPermission(accessory)) {
				Log.d(TAG, "Permission ready.");
				openAccessory(accessory);
			} else {
				Log.d(TAG, "Requesting permission.");
				synchronized (mUsbReceiver) {
					if (!mPermissionRequestPending) {
						mUsbManager.requestPermission(accessory,
								mPermissionIntent);
						mPermissionRequestPending = true;
					}
				}
			}
		} else {
			Log.d(TAG, "mAccessory is null.");
		}
	}

	private void openAccessory(UsbAccessory accessory) {
		Log.d(TAG, "Open accessory called.");
		mFileDescriptor = mUsbManager.openAccessory(accessory);
		if (mFileDescriptor != null) {
			Log.d(TAG, "accessory opened");
			mAccessory = accessory;
			FileDescriptor fd = mFileDescriptor.getFileDescriptor();
			mInputStream = new FileInputStream(fd);
			mOutputStream = new FileOutputStream(fd);
			Thread thread = new Thread(null, mRxRunnable, TAG);
			thread.start();
		} else {
			Log.d(TAG, "accessory open fail");
		}
	}

	private void closeAccessory() {
		Log.d(TAG, "Close accessory called.");
		try {
			if (mFileDescriptor != null) {
				mFileDescriptor.close();
			}
		} catch (IOException e) {
		} finally {
			mFileDescriptor = null;
			mAccessory = null;
			mInputStream = null;
			mOutputStream = null;
		}
	}

	@Override
	protected void onPause() {
		super.onPause();
		closeAccessory();
	}

	@Override
	protected void onDestroy() {
		super.onDestroy();
		unregisterReceiver(mUsbReceiver);
	}

	// ------------ Speech area --------------------------

	/**
	 * Set the name of your robot for voice commands.
	 * 
	 * @param robotName
	 *            Name given to your robot.
	 */
	protected void setRobotName(String robotName) {
		mRobotName = robotName.toLowerCase();
	}

	/**
	 * Start listening for a voice command.
	 * 
	 * @param prompt
	 *            Text show to the user in the listen box.
	 */
	protected void startListening(String prompt) {
		recognize(RecognizerIntentFactory.getSimpleRecognizerIntent(prompt));
	}

	@Override
	protected void speechNotAvailable() {
		Toast.makeText(this, "speechNotAvailable", Toast.LENGTH_SHORT).show();
	}

	@Override
	protected void directSpeechNotAvailable() {
		Toast.makeText(this, "speechNotAvailable", Toast.LENGTH_SHORT).show();
	}

	@Override
	protected void languageCheckResult(String languageToUse) {
		Log.d(TAG, "languageCheckResult");
	}


	@Override
	protected void receiveWhatWasHeard(List<String> heard,
			float[] confidenceScores) {
		for (String command : heard) {
			command = command.toLowerCase();
			if (mRobotName == null || command.contains(mRobotName)) {
				// We have a command for this robot.
				boolean negativeAngle = false, negativeDistance = false;
				if (command.contains(KEYWORD_ANGLE)) {
					// We have an angle command.
					int[] numericValues = new int[2];
					String wordAfterAngle = getNextWord(command, KEYWORD_ANGLE);
					if (wordAfterAngle.equalsIgnoreCase(KEYWORD_NEGATIVE)) {
						negativeAngle = true;
						wordAfterAngle = getNextWord(command, KEYWORD_NEGATIVE);
					}
					if (convertWordToInt(wordAfterAngle, numericValues, 0)) {
						// Got an angle next get a distance if available.
						numericValues[1] = DEFAULT_DISTANCE;
						if (command.contains(KEYWORD_DISTANCE)) {
							String wordAfterDistance = getNextWord(command,
									KEYWORD_DISTANCE);
							if (wordAfterDistance
									.equalsIgnoreCase(KEYWORD_NEGATIVE)) {
								negativeDistance = true;
								wordAfterDistance = getNextWord(
										command.substring(command
												.indexOf(KEYWORD_DISTANCE)),
										KEYWORD_NEGATIVE);
							}
							convertWordToInt(wordAfterDistance, numericValues, 1);							
						}
						int angle = negativeAngle ? -numericValues[0]
								: numericValues[0];
						int distance = negativeDistance ? -numericValues[1]
								: numericValues[1];
						onVoiceCommand(angle, distance);
						break;
					}
				}
			}
		}
	}

	/**
	 * Helper method to get the next word, which is hopefully a number.
	 * For example after the word "angle" you hope the next word is a number.
	 * 
	 * @param command Entire command heard.
	 * @param keyword Keyword ("angle" or "distance") to look after.
	 * @return The String after the keyword.  "" if none is found.
	 */
	private String getNextWord(String command, String keyword) {
		String nextWord = "";
		int startIndex = command.indexOf(keyword) + keyword.length() + 1;
		if (command.indexOf(keyword) < 0 || startIndex >= command.length()) {
			// There is no keyword or no word after the keyword.
			return "";
		}
		int endIndex = command.indexOf(" ", startIndex);
		if (endIndex < 0) {
			nextWord = command.substring(startIndex);
		} else {
			nextWord = command.substring(startIndex, endIndex);
		}
		return nextWord;
	}

	/**
	 * Converts a string to a number.
	 * 
	 * @param potentialNumber String the is hopefully a number.
	 * @param numericValues An array to populate with the number.
	 * @param index The array index to populate with the number.
	 * @return True if the String is a number.
	 */
	private boolean convertWordToInt(String potentialNumber,
			int[] numericValues, int index) {
		try {
			numericValues[index] = Integer.parseInt(potentialNumber);
		} catch (NumberFormatException e) {
			// Wasn't a number, see if it's a written out single digit word.
			if (!isSignalDigitWord(potentialNumber, numericValues, index)) {
				return false;
			}
		}
		return true;
	}

	/**
	 * Helper method to convert words like "one" into an int.
	 * Uses a brute force approach to convert common words to ints.
	 *
	 * @param potentialNumber String that might be a written out number.
	 * @param numericValues An array to populate with the number.
	 * @param index The array index to populate with the number.
	 * @return True if the String is a number.
	 */
	private boolean isSignalDigitWord(String potentialNumber,
			int[] numericValues, int index) {
		if (potentialNumber.equalsIgnoreCase("zero")) {
			numericValues[index] = 0;
			return true;
		} else if (potentialNumber.equalsIgnoreCase("one")) {
			numericValues[index] = 1;
			return true;
		} else if (potentialNumber.equalsIgnoreCase("two") || potentialNumber.equalsIgnoreCase("to") || potentialNumber.equalsIgnoreCase("too")) {
			numericValues[index] = 2;
			return true;
		} else if (potentialNumber.equalsIgnoreCase("three")) {
			numericValues[index] = 3;
			return true;
		} else if (potentialNumber.equalsIgnoreCase("four") || potentialNumber.equalsIgnoreCase("for")) {
			numericValues[index] = 4;
			return true;
		} else if (potentialNumber.equalsIgnoreCase("five")) {
			numericValues[index] = 5;
			return true;
		} else if (potentialNumber.equalsIgnoreCase("six")) {
			numericValues[index] = 6;
			return true;
		} else if (potentialNumber.equalsIgnoreCase("seven")) {
			numericValues[index] = 7;
			return true;
		} else if (potentialNumber.equalsIgnoreCase("eight")) {
			numericValues[index] = 8;
			return true;
		} else if (potentialNumber.equalsIgnoreCase("nine")) {
			numericValues[index] = 9;
			return true;
		} else if (potentialNumber.equalsIgnoreCase("ten")) {
			numericValues[index] = 10;
			return true;
		}
		return false;
	}

	/**
	 * This method is called if a valid voice command is received.
	 * Subclasses of this activity should override this method so
	 * that they can actually do something with the command.
	 * @param angle
	 * @param distance
	 */
	protected void onVoiceCommand(int angle, int distance) {
		Log.d(TAG, "Voice command for angle " + angle + " distance " + distance);
	}

	@Override
	protected void recognitionFailure(int errorCode) {
		Toast.makeText(this, "recognitionFailure", Toast.LENGTH_SHORT).show();
	}

}
