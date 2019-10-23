package org.yaxim.androidclient.service;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
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
import android.text.TextUtils;
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

	static class NotificationData {
		int id;
		String displayName;
		ArrayList<String> messages;
		SpannableStringBuilder bigText;
		CharSequence lastMessage;
		String lastNickname;
		long timestamp;
		boolean isMuc;
		boolean shown;

		public NotificationData(int id) {
			this.id = id;
			messages = new ArrayList<String>();
			bigText = new SpannableStringBuilder();
		}
	}

	protected Map<String, NotificationData> notifications = new HashMap<String, NotificationData>(2);

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

	public void setGracePeriod(long when) {
		if (when > gracePeriodStart || when == 0) {
			String when_s = (when == 0) ? "cleared" :
				new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS").format(new Date(when));
			Log.d(TAG, "user activity from different device: " + when_s);
			gracePeriodStart = when;
		}
	}

	private void addNotificationMGR() {
		mNotificationMGR = NotificationManagerCompat.from(this);
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
			NotificationManager oreoDamager = getSystemService(NotificationManager.class);
			NotificationChannel nc_status = new NotificationChannel("status",
					getString(R.string.notification_status), NotificationManager.IMPORTANCE_LOW);
			oreoDamager.createNotificationChannel(nc_status);
			oreoDamager.deleteNotificationChannel("messages"); // is "msg" now
			oreoDamager.createNotificationChannel(mConfig.createNotificationChannelFor(false, null, getString(R.string.preftitle_notify_msg)));
			oreoDamager.createNotificationChannel(mConfig.createNotificationChannelFor(true, null, getString(R.string.preftitle_notify_muc)));
		}
		mNotificationIntent = new Intent(this, ChatWindow.class);
	}

	protected NotificationData appendToNotification(String[] jid, String fromDisplayName, String message,
			Message.Type msgType, long timestamp) {
		if (message == null) {
			clearNotification(jid[0]);
			return null;
		}

		String fromJid = jid[0];

		NotificationData nd = notifications.get(fromJid);
		if (nd == null) {
			nd = new NotificationData(++lastNotificationId);
			notifications.put(fromJid, nd);
		}
		nd.isMuc = (msgType==Message.Type.groupchat);
		nd.displayName = fromDisplayName;

		// /me processing
		boolean slash_me = message.startsWith("/me ");
		String from_nickname = nd.isMuc ? jid[1] : fromDisplayName;
		nd.lastNickname = nd.isMuc ? jid[1] : null;

		if (nd.bigText.length() > 0)
			nd.bigText.append("\n");
		if (nd.isMuc && !slash_me) {
			// work around .append(stylable) only available in SDK 21+
			int start = nd.bigText.length();
			nd.bigText.append(jid[1]).append(":");
			nd.bigText.setSpan(new StyleSpan(Typeface.BOLD),
					start, nd.bigText.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
			nd.bigText.append(" ");
		}
		// TODO: get real Notification color; using #808080 for now as Styling fallback
		SpannableStringBuilder body = MessageStylingHelper.formatMessage(message,
				from_nickname, null, 0xff808080);
		nd.bigText.append(body);

		//adding the messages into the messageList
		nd.messages.add(body.toString());

		nd.lastMessage = body;
		nd.timestamp = timestamp;
		nd.shown = false;
		return nd;
	}

	protected void notifyClient(String[] jid, String fromDisplayName, String message,
								boolean showNotification, boolean silent_notification, Message.Type msgType,
								long timestamp) {
		if (message == null) {
			clearNotification(jid[0]);
			return;
		}

		boolean isMuc = (msgType == Message.Type.groupchat);
		boolean is_error = (msgType == Message.Type.error);

		// Override silence when activity from other client happened recently
		long silent_seconds = (System.currentTimeMillis() - gracePeriodStart)/1000;
		if (!silent_notification && silent_seconds < SECONDS_OF_SILENCE) {
			Log.d(TAG, "Silent notification: last activity was " + silent_seconds + "s ago.");
			silent_notification = true;
		}

		Uri sound = Uri.parse(mConfig.getJidString(isMuc, PreferenceConstants.RINGTONENOTIFY, jid[0], ""));
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

		NotificationData nd = appendToNotification(jid, fromDisplayName, message, msgType, timestamp);
		displayNotification(jid[0], nd, silent_notification);
	}

	protected void displayNotification(String jid, NotificationData nd, boolean silent_notification) {
		mWakeLock.acquire();
		Uri sound = Uri.parse(mConfig.getJidString(nd.isMuc, PreferenceConstants.RINGTONENOTIFY, jid, ""));
		boolean vibrate = false;
		if(!silent_notification) {
			String vibration = mConfig.getJidString(nd.isMuc, PreferenceConstants.VIBRATIONNOTIFY, jid, "OFF");
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
		boolean blink = mConfig.getJidBoolean(nd.isMuc, PreferenceConstants.LEDNOTIFY, jid, false);

		Notification n = setNotification(jid, nd,
				blink, sound, vibrate);

		nd.shown = true;
		mNotificationMGR.notify(nd.id, n);
		updateBadger();
		mWakeLock.release();

	}

	private Notification setNotification(String fromJid, NotificationData nd,
			 boolean blink, Uri ringtone, boolean vibrate) {

		String author_title;
		if (nd.lastNickname != null)
			author_title = getString(R.string.notification_muc_message, nd.lastNickname, nd.displayName/* = name of chatroom */);
		else
			author_title = nd.displayName; // removed "Message from" prefix for brevity
		String ticker;
		if (mConfig.getJidBoolean(nd.isMuc, PreferenceConstants.TICKER, fromJid, true)) {
			String msg_string = nd.lastMessage.toString();
			int newline = msg_string.indexOf('\n');
			int limit = 0;
			String messageSummary = msg_string;
			if (newline >= 0)
				limit = newline;
			if (limit > MAX_TICKER_MSG_LEN || nd.lastMessage.length() > MAX_TICKER_MSG_LEN)
				limit = MAX_TICKER_MSG_LEN;
			if (limit > 0)
				messageSummary = msg_string.substring(0, limit) + "…";
			ticker = author_title + ": " + messageSummary;
		} else
			ticker = getString(R.string.notification_anonymous_message);

		Intent msgHeardIntent = new Intent(this, YaximBroadcastReceiver.class)
			.addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
			.setAction("org.yaxim.androidclient.ACTION_MESSAGE_HEARD")
			.putExtra("jid", fromJid);

		Intent msgResponseIntent = new Intent(this, YaximBroadcastReceiver.class)
			.addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
			.setAction("org.yaxim.androidclient.ACTION_MESSAGE_REPLY")
			.putExtra("jid", fromJid);

		PendingIntent msgHeardPendingIntent = PendingIntent.getBroadcast(
					getApplicationContext(),
					notifications.get(fromJid).id,
					msgHeardIntent,
					PendingIntent.FLAG_UPDATE_CURRENT);
		PendingIntent msgResponsePendingIntent = PendingIntent.getBroadcast(
					getApplicationContext(),
					notifications.get(fromJid).id,
					msgResponseIntent,
					PendingIntent.FLAG_UPDATE_CURRENT);
		RemoteInput remoteInput = new RemoteInput.Builder("voicereply")
			.setLabel(getString(R.string.notification_reply))
			.build();
		UnreadConversation.Builder ucb = new UnreadConversation.Builder(nd.displayName)
			.setReadPendingIntent(msgHeardPendingIntent)
			.setReplyAction(msgResponsePendingIntent, remoteInput);

		//adding a loop outside
		for (String msg_one : nd.messages){
			ucb.addMessage(msg_one).setLatestTimestamp(System.currentTimeMillis());
		}
		//ucb.addMessage(msg_long.toString()).setLatestTimestamp(System.currentTimeMillis());

		Uri userNameUri = Uri.parse(fromJid);
		Intent chatIntent = new Intent(this, nd.isMuc ? MUCChatWindow.class : ChatWindow.class);
		chatIntent.setData(userNameUri);
		chatIntent.putExtra(ChatWindow.INTENT_EXTRA_USERNAME, nd.displayName);
		//XXX chatIntent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK|Intent.FLAG_ACTIVITY_NEW_TASK);

		// create back-stack (WTF were you smoking, Google!?)
		//need to set flag FLAG_UPDATE_CURRENT to get extras transferred
		PendingIntent pi = TaskStackBuilder.create(this)
			.addNextIntentWithParentStack(chatIntent)
			.getPendingIntent(0, PendingIntent.FLAG_UPDATE_CURRENT);

		String notification_channel = mConfig.getEffectiveNotificationChannelId(nd.isMuc, fromJid);
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
			if (mConfig.getJidOverride(nd.isMuc, fromJid)) {
				getSystemService(NotificationManager.class).createNotificationChannel(
						mConfig.createNotificationChannelFor(nd.isMuc, fromJid, nd.displayName));
			}
		}

		NotificationCompat.Action actMarkRead = new NotificationCompat.Action.Builder(
				android.R.drawable.ic_menu_close_clear_cancel,
				getString(R.string.notification_mark_read), msgHeardPendingIntent).build();
		NotificationCompat.Action actReply = new NotificationCompat.Action.Builder(
				android.R.drawable.ic_menu_edit,
				getString(R.string.notification_reply), msgResponsePendingIntent)
			.addRemoteInput(remoteInput).build();
		// TODO: split public and private parts, use .setPublicVersion()
		NotificationCompat.Builder notificationBuilder = new NotificationCompat.Builder(this, notification_channel)
			.setContentTitle(author_title)
			.setContentText(nd.lastMessage)
			.setStyle(new NotificationCompat.BigTextStyle()
					 .setBigContentTitle(nd.displayName)
					.bigText(nd.bigText))
			.setTicker(ticker)
			.setWhen(nd.timestamp)
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
			.setNumber(nd.messages.size())
			;
		if (blink)
			notificationBuilder.setLights(Color.MAGENTA, 300, 1000);
		if (ringtone != null)
			notificationBuilder.setSound(ringtone);
		return notificationBuilder.build();
	}

	protected void displayPendingNonMUCNotifications() {
		for (Map.Entry<String, NotificationData> entry : notifications.entrySet()) {
			NotificationData nd = entry.getValue();
			if (!nd.shown && !nd.isMuc) {
				logInfo("Showing delayed notification for " + entry.getKey());
				displayNotification(entry.getKey(), nd, false);
			}
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

	public void updateBadger() {
		int count = 0;
		for (NotificationData nd : notifications.values())
			count += nd.messages.size();
		ShortcutBadger.applyCount(this, count);
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
		NotificationData nd = notifications.get(Jid);
		if (nd != null) {
			mNotificationMGR.cancel(nd.id);
			notifications.remove(Jid);
			updateBadger();
		}
	}

}
