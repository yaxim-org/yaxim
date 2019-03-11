package org.yaxim.androidclient.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import org.jivesoftware.smack.packet.Message;
import org.yaxim.androidclient.chat.ChatWindow;
import org.yaxim.androidclient.chat.MUCChatWindow;
import org.yaxim.androidclient.data.YaximConfiguration;
import org.yaxim.androidclient.util.LogConstants;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.os.Vibrator;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.NotificationCompat.CarExtender;
import android.support.v4.app.NotificationCompat.CarExtender.UnreadConversation;
import android.support.v4.app.NotificationManagerCompat;
import android.support.v4.app.RemoteInput;
import android.support.v4.app.TaskStackBuilder;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.StyleSpan;
import android.util.Log;
import android.widget.Toast;
import org.yaxim.androidclient.R;
import org.yaxim.androidclient.util.MessageStylingHelper;
import org.yaxim.androidclient.util.PreferenceConstants;

import me.leolin.shortcutbadger.ShortcutBadger;

import static android.support.v4.app.NotificationCompat.DEFAULT_VIBRATE;

public abstract class GenericService extends Service {

	private static final String TAG = "yaxim.Service";
	private static final String APP_NAME = "yaxim";
	private static final int MAX_TICKER_MSG_LEN = 45;

	private static final int SECONDS_OF_SILENCE = 144; /* Conversations: grace_period = "short" */

	protected NotificationManagerCompat mNotificationMGR;
	private Vibrator mVibrator;
	private Intent mNotificationIntent;
	protected WakeLock mWakeLock;
	//private int mNotificationCounter = 0;
	
	protected Map<String, Integer> notificationCount = new HashMap<String, Integer>(2);
	protected Map<String, Integer> notificationId = new HashMap<String, Integer>(2);
	protected Map<String, SpannableStringBuilder> notificationBigText = new HashMap<String, SpannableStringBuilder>(2);

	//create a hashmap of JID to message list
	protected Map<String, ArrayList<String>> notificationMessage = new HashMap<>();

	protected static int SERVICE_NOTIFICATION = 1;
	protected int lastNotificationId = 2;
	protected long gracePeriodStart = 0;

	protected YaximConfiguration mConfig;

	@Override
	public void onCreate() {
		Log.i(TAG, "called onCreate()");
		super.onCreate();
		mConfig = org.yaxim.androidclient.YaximApplication.getConfig();
		mVibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
		mWakeLock = ((PowerManager)getSystemService(Context.POWER_SERVICE))
				.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, APP_NAME);
		addNotificationMGR();
		updateBadger();
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

	public void setGracePeriod(boolean silence) {
		gracePeriodStart = silence ? System.currentTimeMillis() : 0;
	}

	private void addNotificationMGR() {
		mNotificationMGR = NotificationManagerCompat.from(this);
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
			NotificationManager oreoDamager = getSystemService(NotificationManager.class);
			NotificationChannel nc_status = new NotificationChannel("status",
					getString(R.string.notification_status), NotificationManager.IMPORTANCE_LOW);
			oreoDamager.createNotificationChannel(nc_status);
			NotificationChannel nc_msg = new NotificationChannel("messages",
				getString(R.string.notification_msg), NotificationManager.IMPORTANCE_DEFAULT);
			nc_msg.setShowBadge(true);
			oreoDamager.createNotificationChannel(nc_msg);
		}
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

		Uri sound = Uri.parse(mConfig.getJidString(isMuc, PreferenceConstants.RINGTONENOTIFY, fromJid, ""));
		if (!showNotification) {
			if (is_error)
				shortToastNotify(getString(R.string.notification_error) + " " + message);
			// only play sound and return
			try {
				if (!silent_notification && !Uri.EMPTY.equals(sound))
					RingtoneManager.getRingtone(getApplicationContext(), sound).play();
			} catch (NullPointerException e) {
				// ignore NPE when ringtone was not found
			}
			return;
		}
		mWakeLock.acquire();

		// Override silence when activity from other client happened recently
		long silent_seconds = (System.currentTimeMillis() - gracePeriodStart)/1000;
		if (!silent_notification && silent_seconds < SECONDS_OF_SILENCE) {
			Log.d(TAG, "Silent notification: last activity was " + silent_seconds + "s ago.");
			silent_notification = true;
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
		String from_nickname = isMuc ? jid[1] : fromUserName;

		SpannableStringBuilder msg_long = notificationBigText.get(fromJid);
		if (msg_long == null) {
			msg_long = new SpannableStringBuilder();
			notificationBigText.put(fromJid, msg_long);
		} else
			msg_long.append("\n");
		if (isMuc && !slash_me) {
			// work around .append(stylable) only available in SDK 21+
			int start = msg_long.length();
			msg_long.append(jid[1]).append(":");
			msg_long.setSpan(new StyleSpan(Typeface.BOLD),
					start, msg_long.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
			msg_long.append(" ");
		}
		// TODO: get real Notification color; using #808080 for now as Styling fallback
		SpannableStringBuilder body = MessageStylingHelper.formatMessage(message,
				from_nickname, null, 0xff808080);
		msg_long.append(body);

		//add the message into message list of individual sender
		ArrayList<String> messageList = notificationMessage.get(fromJid);
		if (messageList == null){
			messageList = new ArrayList<>();
			notificationMessage.put(fromJid,messageList);
		}
		//adding the messages into the messageList
		messageList.add(body.toString());

		boolean vibrate = false;
		if(!silent_notification) {
			String vibration = mConfig.getJidString(isMuc, PreferenceConstants.VIBRATIONNOTIFY, fromJid, "OFF");
			switch (vibration) {
			case "SYSTEM":
				vibrate = true;
				break;
			case "ALWAYS":
				mVibrator.vibrate(400);
				break;
			}
		} else
			sound = null; // override ringtone with silent_notification
		boolean blink = mConfig.getJidBoolean(isMuc, PreferenceConstants.LEDNOTIFY, fromJid, false);
		Notification n = setNotification(fromJid, jid[1], fromUserName,
				body, msg_long, messageList,
				blink, sound, vibrate, isMuc);

		
		mNotificationMGR.notify(notifyId, n);
		updateBadger();
		mWakeLock.release();
	}
	
	private Notification setNotification(String fromJid, String fromResource, String fromUserId, CharSequence message, SpannableStringBuilder msg_long,
										 ArrayList<String> messageList, boolean blink, Uri ringtone, boolean vibrate, boolean isMuc) {
		
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
		if (mConfig.getJidBoolean(isMuc, PreferenceConstants.TICKER, fromJid, true)) {
			String msg_string = message.toString();
			int newline = msg_string.indexOf('\n');
			int limit = 0;
			String messageSummary = msg_string;
			if (newline >= 0)
				limit = newline;
			if (limit > MAX_TICKER_MSG_LEN || message.length() > MAX_TICKER_MSG_LEN)
				limit = MAX_TICKER_MSG_LEN;
			if (limit > 0)
				messageSummary = msg_string.substring(0, limit) + "…";
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

		//adding a loop outside
		for (String msg_one : messageList){
			ucb.addMessage(msg_one).setLatestTimestamp(System.currentTimeMillis());
		}
		//ucb.addMessage(msg_long.toString()).setLatestTimestamp(System.currentTimeMillis());

		Uri userNameUri = Uri.parse(fromJid);
		Intent chatIntent = new Intent(this, isMuc ? MUCChatWindow.class : ChatWindow.class);
		chatIntent.setData(userNameUri);
		chatIntent.putExtra(ChatWindow.INTENT_EXTRA_USERNAME, fromUserId);
		//XXX chatIntent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK|Intent.FLAG_ACTIVITY_NEW_TASK);

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
		// TODO: split public and private parts, use .setPublicVersion()
		NotificationCompat.Builder notificationBuilder = new NotificationCompat.Builder(this, "messages")
			.setContentTitle(title)
			.setContentText(message)
			.setStyle(new NotificationCompat.BigTextStyle()
					 .setBigContentTitle(author)
					.bigText(msg_long))
			.setTicker(ticker)
			.setSmallIcon(R.drawable.sb_message)
			.setCategory(Notification.CATEGORY_MESSAGE)
			.setContentIntent(pi)
			.setAutoCancel(true);
		if (Build.VERSION.SDK_INT >= 24) // use Android7 in-notification reply, fall back to Activity
			notificationBuilder.addAction(actReply);
		notificationBuilder
			.addAction(actMarkRead)
			//.addAction(android.R.drawable.ic_menu_share, "Forward", msgHeardPendingIntent)
			.extend(new CarExtender().setUnreadConversation(ucb.build()))
			.extend(new NotificationCompat.WearableExtender()
					.addAction(actReply)
					.addAction(actMarkRead))
			.setDefaults(vibrate ? DEFAULT_VIBRATE : 0)
			.setNumber(mNotificationCounter)
			;
		if (blink)
			notificationBuilder.setLights(Color.MAGENTA, 300, 1000);
		if (ringtone != null)
			notificationBuilder.setSound(ringtone);
		return notificationBuilder.build();
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

	public void updateBadger() {
		int count = 0;
		for (int c : notificationCount.values())
			count += c;
		ShortcutBadger.applyCount(this, count);
	}

	public void resetNotificationCounter(String userJid) {
		notificationCount.remove(userJid);
		notificationBigText.remove(userJid);
		//clean up notification message of individual sender
		notificationMessage.remove(userJid);
		updateBadger();
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
