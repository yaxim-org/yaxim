package org.yaxim.androidclient.service;

import java.util.HashMap;
import java.util.Map;

import org.jivesoftware.smack.packet.Message;
import org.yaxim.androidclient.chat.ChatWindow;
import org.yaxim.androidclient.data.YaximConfiguration;
import org.yaxim.androidclient.util.LogConstants;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.os.Vibrator;
import android.preference.PreferenceManager;
import android.telephony.gsm.SmsMessage.MessageClass;
import android.util.Log;
import android.widget.Toast;
import org.yaxim.androidclient.R;

public abstract class GenericService extends Service {

	private static final String TAG = "yaxim.Service";
	private static final String APP_NAME = "yaxim";
	private static final int MAX_TICKER_MSG_LEN = 50;

	protected NotificationManager mNotificationMGR;
	private Notification mNotification;
	private Vibrator mVibrator;
	private Intent mNotificationIntent;
	protected WakeLock mWakeLock;
	//private int mNotificationCounter = 0;
	
	private Map<String, Integer> notificationCount = new HashMap<String, Integer>(2);
	private Map<String, Integer> notificationId = new HashMap<String, Integer>(2);
	protected static int SERVICE_NOTIFICATION = 1;
	protected int lastNotificationId = 2;

	protected YaximConfiguration mConfig;

	@Override
	public IBinder onBind(Intent arg0) {
		Log.i(TAG, "called onBind()");
		return null;
	}

	@Override
	public boolean onUnbind(Intent intent) {
		Log.i(TAG, "called onUnbind()");
		return super.onUnbind(intent);
	}

	@Override
	public void onRebind(Intent intent) {
		Log.i(TAG, "called onRebind()");
		super.onRebind(intent);
	}

	@Override
	public void onCreate() {
		Log.i(TAG, "called onCreate()");
		super.onCreate();
		mConfig = org.yaxim.androidclient.YaximApplication.getConfig(this);
		mVibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
		mWakeLock = ((PowerManager)getSystemService(Context.POWER_SERVICE))
				.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, APP_NAME);
		addNotificationMGR();
	}

	@Override
	public void onDestroy() {
		Log.i(TAG, "called onDestroy()");
		super.onDestroy();
	}

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		Log.i(TAG, "called onStartCommand()");
		return START_STICKY;
	}

	private void addNotificationMGR() {
		mNotificationMGR = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
		mNotificationIntent = new Intent(this, ChatWindow.class);
	}

	protected void notifyClient(String fromJid, String fromUserName, String message,
			boolean showNotification, boolean silent_notification, boolean is_error, Message.Type msgType) {
		boolean isMuc = (msgType==Message.Type.groupchat);
		
		if(isMuc && mConfig.highlightNickMuc) {
			Cursor cursor = getContentResolver().query(RosterProvider.MUCS_URI, new String[] {RosterConstants.NICKNAME},
					RosterConstants.JID+"='"+fromJid+"'", null, null);
			cursor.moveToFirst();
			String nick = cursor.getString( cursor.getColumnIndexOrThrow(RosterConstants.NICKNAME) );
			if(!message.contains(nick) && mConfig.highlightNickMuc) { // if we're not mentioned and highlight only on nick is set
				return;
			}
			if((fromJid.contains("/") && fromJid.split("/")[1].equals(nick))) { // if this is a message from us
				return;
			}
		} 
		
		if (!showNotification) {
			if (is_error)
				shortToastNotify(getString(R.string.notification_error) + " " + message);
			// only play sound and return
			try {
				if (!silent_notification) {
					Uri sound = isMuc? mConfig.notifySoundMuc : mConfig.notifySound;
					RingtoneManager.getRingtone(getApplicationContext(), sound).play();
				}
			} catch (NullPointerException e) {
				// ignore NPE when ringtone was not found
			}
			return;
		}
		mWakeLock.acquire();

		// Override silence when notification is created initially
		// if there is no open notification for that JID, and we get a "silent"
		// one (i.e. caused by an incoming carbon message), we still ring/vibrate,
		// but only once. As long as the user ignores the notifications, no more
		// sounds are made. When the user opens the chat window, the counter is
		// reset and a new sound can be made.
		if (silent_notification && !notificationCount.containsKey(fromJid)) {
			silent_notification = false;		
		}

		setNotification(fromJid, fromUserName, message, is_error, isMuc);
		setLEDNotification(isMuc);
		mNotification.sound = mConfig.notifySound;
		
		
		int notifyId = 0;
		if (notificationId.containsKey(fromJid)) {
			notifyId = notificationId.get(fromJid);
		} else {
			lastNotificationId++;
			notifyId = lastNotificationId;
			notificationId.put(fromJid, Integer.valueOf(notifyId));
		}

		// If vibration is set to "system default", add the vibration flag to the 
		// notification and let the system decide.
		if(!silent_notification && ((!isMuc && "SYSTEM".equals(mConfig.vibraNotify)) 
				|| (isMuc && "SYSTEM".equals(mConfig.vibraNotifyMuc)))) {
			mNotification.defaults |= Notification.DEFAULT_VIBRATE;
		}
		mNotificationMGR.notify(notifyId, mNotification);
		
		// If vibration is forced, vibrate now.
		if(!silent_notification && ((!isMuc && "ALWAYS".equals(mConfig.vibraNotify))
				|| (isMuc && "ALWAYS".equals(mConfig.vibraNotifyMuc)))) {
			mVibrator.vibrate(400);
		}
		mWakeLock.release();
	}
	
	private long fetchNewestOwnMsgDate(String fromJid) {
		Cursor cursor = getContentResolver().query(ChatProvider.CONTENT_URI, 
				new String[]{ChatConstants.DATE}, 
				String.format("%s = '%s' AND %s = %s AND %s = %s", ChatConstants.JID, fromJid, 
						ChatConstants.DIRECTION, ChatConstants.OUTGOING,
						ChatConstants.WAS_CARBON, ChatConstants.MSG_CARBON), 
				null, 
				ChatConstants.DATE+" desc");
		if(cursor.getCount() == 0) {
			Log.w(TAG, "could not find newest own (carbons) msg");
			return 0;
		}
		cursor.moveToFirst();
		long ret = cursor.getLong( cursor.getColumnIndexOrThrow(ChatConstants.DATE) );
		return ret;
	}

	private long fetchLastMsgDate(String fromJid) {
		Cursor cursor = getContentResolver().query(ChatProvider.CONTENT_URI, 
				new String[]{ChatConstants.DATE}, 
				String.format("%s = '%s'", ChatConstants.JID, fromJid), 
				null, ChatConstants.DATE+" desc");
		cursor.moveToFirst();
		if(cursor.getCount() < 2) {
			Log.w(TAG, "could not fetch date oflast message, timeout won't work");
			return 0;
		}
		cursor.moveToNext(); // we need the 2nd entry, as the first is the newly added message
		long ret = cursor.getLong( cursor.getColumnIndexOrThrow(ChatConstants.DATE) );
		return ret;
	}

	private void setNotification(String fromJid, String fromUserId, String message, boolean is_error,
			boolean isMuc) {
		
		int mNotificationCounter = 0;
		if (notificationCount.containsKey(fromJid)) {
			mNotificationCounter = notificationCount.get(fromJid);
		}
		mNotificationCounter++;
		notificationCount.put(fromJid, mNotificationCounter);
		String author;
		if (null == fromUserId || fromUserId.length() == 0) {
			author = fromJid;
		} else {
			author = fromUserId;
		}
		String title = getString(R.string.notification_message, author);
		String ticker;
		if ((!isMuc && mConfig.ticker) || (isMuc && mConfig.tickerMuc)) {
			int newline = message.indexOf('\n');
			int limit = 0;
			String messageSummary = message;
			if (newline >= 0)
				limit = newline;
			if (limit > MAX_TICKER_MSG_LEN || message.length() > MAX_TICKER_MSG_LEN)
				limit = MAX_TICKER_MSG_LEN;
			if (limit > 0)
				messageSummary = message.substring(0, limit) + " [...]";
			ticker = title + ":\n" + messageSummary;
		} else
			ticker = getString(R.string.notification_anonymous_message, author);
		mNotification = new Notification(R.drawable.sb_message, ticker,
				System.currentTimeMillis());
		Uri userNameUri = Uri.parse(fromJid);
		mNotificationIntent.setData(userNameUri);
		mNotificationIntent.putExtra(ChatWindow.INTENT_EXTRA_USERNAME, fromUserId);
		mNotificationIntent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
		
		//need to set flag FLAG_UPDATE_CURRENT to get extras transferred
		PendingIntent pendingIntent = PendingIntent.getActivity(this, 0,
				mNotificationIntent, PendingIntent.FLAG_UPDATE_CURRENT);

		mNotification.setLatestEventInfo(this, title, message, pendingIntent);
		if (mNotificationCounter > 1)
			mNotification.number = mNotificationCounter;
		mNotification.flags = Notification.FLAG_AUTO_CANCEL;
	}

	private void setLEDNotification(boolean isMuc) {
		if ((!isMuc && mConfig.isLEDNotify) || (isMuc && mConfig.isLEDNotifyMuc)) {
			mNotification.ledARGB = Color.MAGENTA;
			mNotification.ledOnMS = 300;
			mNotification.ledOffMS = 1000;
			mNotification.flags |= Notification.FLAG_SHOW_LIGHTS;
		}
	}

	protected void shortToastNotify(String msg) {
		Toast toast = Toast.makeText(this, msg, Toast.LENGTH_SHORT);
		toast.show();
	}
	protected void shortToastNotify(Throwable e) {
		e.printStackTrace();
		while (e.getCause() != null)
			e = e.getCause();
		shortToastNotify(e.getMessage());
	}

	public void resetNotificationCounter(String userJid) {
		notificationCount.remove(userJid);
	}

	protected void logError(String data) {
		if (LogConstants.LOG_ERROR) {
			Log.e(TAG, data);
		}
	}

	protected void logInfo(String data) {
		if (LogConstants.LOG_INFO) {
			Log.i(TAG, data);
		}
	}

	public void clearNotification(String Jid) {
		int notifyId = 0;
		if (notificationId.containsKey(Jid)) {
			notifyId = notificationId.get(Jid);
			mNotificationMGR.cancel(notifyId);
		}
	}

}
