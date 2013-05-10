package edu.rosehulman.me435;

import java.util.Locale;

import android.database.Cursor;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.speech.tts.TextToSpeech.OnInitListener;
import android.util.Log;

public class TtsAccessoryActivity extends SpeechAccessoryActivity implements OnInitListener {

	public static final String TAG = TtsAccessoryActivity.class.getSimpleName();
	protected TextToSpeech mTts;
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		mTts = new TextToSpeech(this, this);
	}

	@Override
	public void onInit(int status) {
		if (status == TextToSpeech.SUCCESS) {
			 
            int result = mTts.setLanguage(Locale.US);
 
            if (result == TextToSpeech.LANG_MISSING_DATA
                    || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                Log.e(TAG, "This Language is not supported");
            } else {
            	Log.d(TAG, "TTS Ready");
            }
        } else {
            Log.e(TAG, "Initilization Failed!");
        }
	}
	
	@Override
	protected void onDestroy() {
		if (mTts != null) {
			mTts.stop();
			mTts.shutdown();
		}
		super.onDestroy();
	}
	
	/**
	 * Call this method to speak text.
	 * 
	 * @param messageToSpeak String to speak.
	 */
	protected void speak(String messageToSpeak) {
		mTts.speak(messageToSpeak, TextToSpeech.QUEUE_FLUSH, null);
	}
	
	/**
	 * Simple function that makes a beep using the default notification.
	 */
	protected void playNotificationBeep() {
   	 try {
   	        Uri notification = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
   	        Ringtone r = RingtoneManager.getRingtone(this, notification);
   	        r.play();
   	    } catch (Exception e) {}
	}
	
	/**
	 * Simple function that plays default ring tone.
	 */
	protected void playDefaultRingtone() {
   	 try {
   	        Uri notification = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE);
   	        Ringtone r = RingtoneManager.getRingtone(this, notification);
   	        r.play();
   	    } catch (Exception e) {}
	}
	
	/**
	 * Simple function that plays some valid ring tone (picked not sure how).
	 */
	protected void playValidRingtone() {
		try {
			Uri someRingtoneUri = RingtoneManager.getValidRingtoneUri(this);
			Log.d(TAG, "URI used for rington " + someRingtoneUri);
			// Note you could manually alter that last value in the URI (see playRingtone).
   	        Ringtone r = RingtoneManager.getRingtone(this, someRingtoneUri);
			r.play();
		} catch (Exception e) {
		}
	}
	
	/**
	 * This is a hacky, hacky, hacky function.  If you would like to guess at valid
	 * sound number you can play with this method.  It's a guess can check game, many
	 * sounds don't work.  On a Nexus 7 sounds in the range of 40-50 seem to work. 
	 *
	 * @param mediaFileId Guess for a valid file number.  You can use the playValidRingtone
	 * 		function to learn one valid number, then guess a bit higher or lower to try more.
	 */
	protected void playRingtone(int mediaFileId) {
		try {
			Uri ringtoneUri = Uri.parse("content://media/internal/audio/media/" + mediaFileId);
   	        Ringtone r = RingtoneManager.getRingtone(this, ringtoneUri);
			r.play();
		} catch (Exception e) {
		}
	}
}
