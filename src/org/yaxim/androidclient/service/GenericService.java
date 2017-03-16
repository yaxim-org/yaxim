package org.yaxim.androidclient.service;

import java.util.HashMap;
import java.util.Map;

import org.jivesoftware.smack.packet.Message;
import org.yaxim.androidclient.chat.ChatWindow;
import org.yaxim.androidclient.chat.MUCChatWindow;
import org.yaxim.androidclient.data.YaximConfiguration;
import org.yaxim.androidclient.util.LogConstants;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.os.Vibrator;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.NotificationCompat.CarExtender;
import android.support.v4.app.NotificationCompat.CarExtender.UnreadConversation;
import android.support.v4.app.NotificationManagerCompat;
import android.support.v4.app.RemoteInput;
import android.support.v4.app.TaskStackBuilder;
import android.util.Log;
import android.widget.Toast;
import org.yaxim.androidclient.R;

public abstract class GenericService extends Service {

	private static final String TAG = "yaxim.Service";
	private static final String APP_NAME = "yaxim";
	private static final int MAX_TICKER_MSG_LEN = 45;

	protected NotificationManagerCompat mNotificationMGR;
	private Notification mNotification;
	private Vibrator mVibrator;
	private Intent mNotificationIntent;
	protected WakeLock mWakeLock;
	//private int mNotificationCounter = 0;
	
	protected Map<String, Integer> notificationCount = new HashMap<String, Integer>(2);
	protected Map<String, Integer> notificationId = new HashMap<String, Integer>(2);
	protected Map<String, StringBuilder> notificationBigText = new HashMap<String, StringBuilder>(2);
	protected static int SERVICE_NOTIFICATION = 1;
	protected int lastNotificationId = 2;

	protected YaximConfiguration mConfig;

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
		mNotificationMGR = NotificationManagerCompat.from(this);
		mNotificationIntent = new Intent(this, ChatWindow.class);
	}

	protected void notifyClient(String[] jid, String fromUserName, String message,
			boolean showNotification, boolean silent_notification, Message.Type msgType) {
		String fromJid = jid[0];
		boolean isMuc = (msgType==Message.Type.groupchat);
		boolean is_error = (msgType==Message.Type.error);

		if (message == null) {
			clearNotification(fromJid);
			return;
		}
		
		if (!showNotification) {
			if (is_error)
				shortToastNotify(getString(R.string.notification_error) + " " + message);
			// only play sound and return
			try {
				Uri sound = isMuc? mConfig.notifySoundMuc : mConfig.notifySound;
				if (!silent_notification && !Uri.EMPTY.equals(sound))
					RingtoneManager.getRingtone(getApplicationContext(), sound).play();
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

		int notifyId = 0;
		if (notificationId.containsKey(fromJid)) {
			notifyId = notificationId.get(fromJid);
		} else {
			lastNotificationId++;
			notifyId = lastNotificationId;
			notificationId.put(fromJid, Integer.valueOf(notifyId));
		}

		// /me processing
		boolean slash_me = message.startsWith("/me ");
		if (slash_me) {
			message = String.format("\u25CF %s %s", isMuc ? jid[1] : fromUserName, message.substring(4));
		}

		StringBuilder msg_long = notificationBigText.get(fromJid);
		if (msg_long == null) {
			msg_long = new StringBuilder();
			notificationBigText.put(fromJid, msg_long);
		} else
			msg_long.append("\n");
		if (isMuc && !slash_me)
			msg_long.append(jid[1]).append("▶ ");
		msg_long.append(message);

		setNotification(fromJid, jid[1], fromUserName, message, msg_long.toString(), is_error, isMuc);
		setLEDNotification(isMuc);
		
		
		if(!silent_notification) {
			mNotification.sound = isMuc? mConfig.notifySoundMuc : mConfig.notifySound;
			// If vibration is set to "system default", add the vibration flag to the 
			// notification and let the system decide.
			String vibration = isMuc ? mConfig.vibraNotifyMuc : mConfig.vibraNotify;
			if ("SYSTEM".equals(vibration)) {
				mNotification.defaults |= Notification.DEFAULT_VIBRATE;
			} else if ("ALWAYS".equals(vibration)) {
				mVibrator.vibrate(400);
			}
		}
		mNotificationMGR.notify(notifyId, mNotification);
		mWakeLock.release();
	}
	
	private void setNotification(String fromJid, String fromResource, String fromUserId, String message, String msg_long,
			boolean is_error, boolean isMuc) {
		
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
		String title;
		if (isMuc)
			title = getString(R.string.notification_muc_message, fromResource, author/* = name of chatroom */);
		else
			title = author; // removed "Message from" prefix for brevity
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
			ticker = title + ": " + messageSummary;
		} else
			ticker = getString(R.string.notification_anonymous_message);

		Intent msgHeardIntent = new Intent()
			.addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
			.setAction("org.yaxim.androidclient.ACTION_MESSAGE_HEARD")
			.putExtra("jid", fromJid);

		Intent msgResponseIntent = new Intent()
			.addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
			.setAction("org.yaxim.androidclient.ACTION_MESSAGE_REPLY")
			.putExtra("jid", fromJid);

		PendingIntent msgHeardPendingIntent = PendingIntent.getBroadcast(
					getApplicationContext(),
					notificationId.get(fromJid),
					msgHeardIntent,
					PendingIntent.FLAG_UPDATE_CURRENT);
		PendingIntent msgResponsePendingIntent = PendingIntent.getBroadcast(
					getApplicationContext(),
					notificationId.get(fromJid),
					msgResponseIntent,
					PendingIntent.FLAG_UPDATE_CURRENT);
		RemoteInput remoteInput = new RemoteInput.Builder("voicereply")
			.setLabel(getString(R.string.notification_reply))
			.build();
		UnreadConversation.Builder ucb = new UnreadConversation.Builder(author)
			.setReadPendingIntent(msgHeardPendingIntent)
			.setReplyAction(msgResponsePendingIntent, remoteInput);
		ucb.addMessage(msg_long.replace("▶ ", ": ")).setLatestTimestamp(System.currentTimeMillis());

		Uri userNameUri = Uri.parse(fromJid);
		Intent chatIntent = new Intent(this, isMuc ? MUCChatWindow.class : ChatWindow.class);
		chatIntent.setData(userNameUri);
		chatIntent.putExtra(ChatWindow.INTENT_EXTRA_USERNAME, fromUserId);

		// create back-stack (WTF were you smoking, Google!?)
		//need to set flag FLAG_UPDATE_CURRENT to get extras transferred
		PendingIntent pi = TaskStackBuilder.create(this)
			.addNextIntentWithParentStack(chatIntent)
			.getPendingIntent(0, PendingIntent.FLAG_UPDATE_CURRENT);

		NotificationCompat.Action actMarkRead = new NotificationCompat.Action.Builder(
				android.R.drawable.ic_menu_close_clear_cancel,
				getString(R.string.notification_mark_read), msgHeardPendingIntent).build();
		NotificationCompat.Action actReply = new NotificationCompat.Action.Builder(
				android.R.drawable.ic_menu_edit,
				getString(R.string.notification_reply), msgResponsePendingIntent)
			.addRemoteInput(remoteInput).build();
		mNotification = new NotificationCompat.Builder(this)
			.setContentTitle(title)
			.setContentText(message)
			.setStyle(new NotificationCompat.BigTextStyle()
					 .setBigContentTitle(author)
					.bigText(msg_long))
			.setTicker(ticker)
			.setSmallIcon(R.drawable.sb_message)
			.setCategory(Notification.CATEGORY_MESSAGE)
			.setContentIntent(pi)
			.setAutoCancel(true)
			//.addAction(actReply) // TODO: use Android7 in-notification reply, fall back to Activity
			.addAction(actMarkRead)
			//.addAction(android.R.drawable.ic_menu_share, "Forward", msgHeardPendingIntent)
			.extend(new CarExtender().setUnreadConversation(ucb.build()))
			.extend(new NotificationCompat.WearableExtender()
					.addAction(actReply)
					.addAction(actMarkRead))
			.build();
		mNotification.defaults = 0;

		if (mNotificationCounter > 1)
			mNotification.number = mNotificationCounter;
	}

	private void setLEDNotification(boolean isMuc) {
		if ((!isMuc && mConfig.isLEDNotify) || (isMuc && mConfig.isLEDNotifyMuc)) {
			android.content.res.TypedArray a =
				getTheme().obtainStyledAttributes(mConfig.getTheme(),
					new int[] { android.R.attr.windowBackground });
			mNotification.ledARGB = a.getInt(0, Color.MAGENTA);
			mNotification.ledOnMS = 300;
			mNotification.ledOffMS = 1000;
			mNotification.flags |= Notification.FLAG_SHOW_LIGHTS;
			a.recycle();
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
		notificationBigText.remove(userJid);
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
			resetNotificationCounter(Jid);
		}
	}

}
