package org.yaxim.androidclient.service;

import java.util.Collection;
import java.util.Date;
import javax.net.ssl.SSLContext;
import javax.net.ssl.X509TrustManager;

import org.jivesoftware.smack.AccountManager;
import org.jivesoftware.smack.ConnectionConfiguration;
import org.jivesoftware.smack.ConnectionListener;
import org.jivesoftware.smack.PacketListener;
import org.jivesoftware.smack.Roster;
import org.jivesoftware.smack.RosterEntry;
import org.jivesoftware.smack.RosterGroup;
import org.jivesoftware.smack.RosterListener;
import org.jivesoftware.smack.SmackConfiguration;
import org.jivesoftware.smack.XMPPConnection;
import org.jivesoftware.smack.XMPPException;
import org.jivesoftware.smack.filter.PacketFilter;
import org.jivesoftware.smack.filter.PacketTypeFilter;
import org.jivesoftware.smack.packet.IQ;
import org.jivesoftware.smack.packet.IQ.Type;
import org.jivesoftware.smack.packet.Message;
import org.jivesoftware.smack.packet.Packet;
import org.jivesoftware.smack.packet.Presence;
import org.jivesoftware.smack.packet.Presence.Mode;
import org.jivesoftware.smack.provider.ProviderManager;
import org.jivesoftware.smack.util.StringUtils;
import org.jivesoftware.smackx.ServiceDiscoveryManager;
import org.jivesoftware.smackx.provider.DelayInfoProvider;
import org.jivesoftware.smackx.provider.DeliveryReceiptProvider;
import org.jivesoftware.smackx.provider.DiscoverInfoProvider;
import org.jivesoftware.smackx.packet.DelayInformation;
import org.jivesoftware.smackx.packet.DelayInfo;
import org.jivesoftware.smackx.packet.DeliveryReceipt;
import org.jivesoftware.smackx.packet.DeliveryReceiptRequest;
import org.yaxim.androidclient.YaximApplication;
import org.yaxim.androidclient.data.ChatProvider;
import org.yaxim.androidclient.data.RosterProvider;
import org.yaxim.androidclient.data.YaximConfiguration;
import org.yaxim.androidclient.data.ChatProvider.ChatConstants;
import org.yaxim.androidclient.data.RosterProvider.RosterConstants;
import org.yaxim.androidclient.exceptions.YaximXMPPException;
import org.yaxim.androidclient.util.LogConstants;
import org.yaxim.androidclient.util.PreferenceConstants;
import org.yaxim.androidclient.util.StatusMode;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;

import android.net.Uri;
import android.util.Log;

public class SmackableImp implements Smackable {
	final static private String TAG = "yaxim.SmackableImp";

	final static private int PACKET_TIMEOUT = 30000;

	final static private String[] SEND_OFFLINE_PROJECTION = new String[] {
			ChatConstants._ID, ChatConstants.JID,
			ChatConstants.MESSAGE, ChatConstants.DATE, ChatConstants.PACKET_ID };
	final static private String SEND_OFFLINE_SELECTION =
			ChatConstants.DIRECTION + " = " + ChatConstants.OUTGOING + " AND " +
			ChatConstants.DELIVERY_STATUS + " = " + ChatConstants.DS_NEW;

	static {
		registerSmackProviders();
	}

	static void registerSmackProviders() {
		ProviderManager pm = ProviderManager.getInstance();
		// add IQ handling
		pm.addIQProvider("query","http://jabber.org/protocol/disco#info", new DiscoverInfoProvider());
		// add delayed delivery notifications
		pm.addExtensionProvider("delay","urn:xmpp:delay", new DelayInfoProvider());
		pm.addExtensionProvider("x","jabber:x:delay", new DelayInfoProvider());
		// add delivery receipts
		pm.addExtensionProvider("received", DeliveryReceipt.NAMESPACE, new DeliveryReceiptProvider());
		// add XMPP Ping (XEP-0199)
		pm.addIQProvider("ping","urn:xmpp:ping", new PingProvider());

		ServiceDiscoveryManager.setIdentityName(YaximApplication.XMPP_IDENTITY_NAME);
		ServiceDiscoveryManager.setIdentityType(YaximApplication.XMPP_IDENTITY_TYPE);
	}

	private final YaximConfiguration mConfig;
	private final ConnectionConfiguration mXMPPConfig;
	private final XMPPConnection mXMPPConnection;

	private XMPPServiceCallback mServiceCallBack;
	private Roster mRoster;
	private RosterListener mRosterListener;
	private PacketListener mPacketListener;

	private final ContentResolver mContentResolver;

	private PacketListener mSendFailureListener;
	private PacketListener mPingListener;
	private PacketListener mPongListener;
	private String mPingID;

	private PendingIntent mPingAlarmPendIntent;
	private PendingIntent mPongTimeoutAlarmPendIntent;
	private static final String PING_ALARM = "org.yaxim.androidclient.PING_ALARM";
	private static final String PONG_TIMEOUT_ALARM = "org.yaxim.androidclient.PONG_TIMEOUT_ALARM";
	private Intent mPingAlarmIntent = new Intent(PING_ALARM);
	private Intent mPongTimeoutAlarmIntent = new Intent(PONG_TIMEOUT_ALARM);
	private Service mService;

	private PongTimeoutAlarmReceiver mPongTimeoutAlarmReceiver = new PongTimeoutAlarmReceiver();
	private BroadcastReceiver mPingAlarmReceiver = new PingAlarmReceiver();


	public SmackableImp(YaximConfiguration config,
			ContentResolver contentResolver,
			Service service) {
		this.mConfig = config;
		// allow custom server / custom port to override SRV record
		if (mConfig.customServer.length() > 0 || mConfig.port != PreferenceConstants.DEFAULT_PORT_INT)
			this.mXMPPConfig = new ConnectionConfiguration(mConfig.customServer,
					mConfig.port, mConfig.server);
		else
			this.mXMPPConfig = new ConnectionConfiguration(mConfig.server); // use SRV
		this.mXMPPConfig.setReconnectionAllowed(false);
		this.mXMPPConfig.setSendPresence(false);
		this.mXMPPConfig.setDebuggerEnabled(mConfig.smackdebug);
		if (config.require_ssl)
			this.mXMPPConfig.setSecurityMode(ConnectionConfiguration.SecurityMode.required);

		// register MemorizingTrustManager for HTTPS
		try {
			SSLContext sc = SSLContext.getInstance("TLS");
			sc.init(null, new X509TrustManager[] { YaximApplication.getApp(service).mMTM },
					new java.security.SecureRandom());
			this.mXMPPConfig.setCustomSSLContext(sc);
		} catch (java.security.GeneralSecurityException e) {
			debugLog("initialize MemorizingTrustManager: " + e);
		}

		this.mXMPPConnection = new XMPPConnection(mXMPPConfig);
		this.mContentResolver = contentResolver;
		this.mService = service;
	}

	public boolean doConnect(boolean create_account) throws YaximXMPPException {
		tryToConnect(create_account);
		// actually, authenticated must be true now, or an exception must have
		// been thrown.
		if (isAuthenticated()) {
			registerMessageListener();
			registerMessageSendFailureListener();
			registerPingListener();
			registerPongListener();
			sendOfflineMessages();
			// we need to "ping" the service to let it know we are actually
			// connected, even when no roster entries will come in
			mServiceCallBack.rosterChanged();
			registerRosterListener();
			setRosterEntries();
		}
		return isAuthenticated();
	}

	private void initServiceDiscovery() {
		// register connection features
		ServiceDiscoveryManager sdm = ServiceDiscoveryManager.getInstanceFor(mXMPPConnection);
		if (sdm == null)
			sdm = new ServiceDiscoveryManager(mXMPPConnection);

		sdm.addFeature("http://jabber.org/protocol/disco#info");
		sdm.addFeature(DeliveryReceipt.NAMESPACE);
		sdm.addFeature("urn:xmpp:ping");
	}

	public void addRosterItem(String user, String alias, String group)
			throws YaximXMPPException {
		tryToAddRosterEntry(user, alias, group);
	}

	public void removeRosterItem(String user) throws YaximXMPPException {
		debugLog("removeRosterItem(" + user + ")");

		tryToRemoveRosterEntry(user);
		mServiceCallBack.rosterChanged();
	}

	public void renameRosterItem(String user, String newName)
			throws YaximXMPPException {
		mRoster = mXMPPConnection.getRoster();
		RosterEntry rosterEntry = mRoster.getEntry(user);

		if (!(newName.length() > 0) || (rosterEntry == null)) {
			throw new YaximXMPPException("JabberID to rename is invalid!");
		}
		rosterEntry.setName(newName);
	}

	public void addRosterGroup(String group) {
		mRoster = mXMPPConnection.getRoster();
		mRoster.createGroup(group);
	}

	public void renameRosterGroup(String group, String newGroup) {
		mRoster = mXMPPConnection.getRoster();
		RosterGroup groupToRename = mRoster.getGroup(group);
		groupToRename.setName(newGroup);
	}

	public void moveRosterItemToGroup(String user, String group)
			throws YaximXMPPException {
		tryToMoveRosterEntryToGroup(user, group);
	}

	public void requestAuthorizationForRosterItem(String user) {
		Presence response = new Presence(Presence.Type.subscribe);
		response.setTo(user);
		mXMPPConnection.sendPacket(response);
	}

	private void tryToConnect(boolean create_account) throws YaximXMPPException {
		try {
			if (mXMPPConnection.isConnected()) {
				try {
					mXMPPConnection.disconnect();
				} catch (Exception e) {
					debugLog("conn.disconnect() failed: " + e);
				}
			}
			SmackConfiguration.setPacketReplyTimeout(PACKET_TIMEOUT);
			SmackConfiguration.setKeepAliveInterval(-1);
			mXMPPConnection.connect();
			if (!mXMPPConnection.isConnected()) {
				throw new YaximXMPPException("SMACK connect failed without exception!");
			}
			mXMPPConnection.addConnectionListener(new ConnectionListener() {
				public void connectionClosedOnError(Exception e) {
					mServiceCallBack.disconnectOnError();
				}
				public void connectionClosed() { }
				public void reconnectingIn(int seconds) { }
				public void reconnectionFailed(Exception e) { }
				public void reconnectionSuccessful() { }
			});
			initServiceDiscovery();
			// SMACK auto-logins if we were authenticated before
			if (!mXMPPConnection.isAuthenticated()) {
				if (create_account) {
					Log.d(TAG, "creating new server account...");
					AccountManager am = new AccountManager(mXMPPConnection);
					am.createAccount(mConfig.userName, mConfig.password);
				}
				mXMPPConnection.login(mConfig.userName, mConfig.password,
						mConfig.ressource);
			}
			setStatusFromConfig();

		} catch (XMPPException e) {
			throw new YaximXMPPException(e.getLocalizedMessage(), e.getWrappedThrowable());
		} catch (Exception e) {
			// actually we just care for IllegalState or NullPointer or XMPPEx.
			Log.e(TAG, "tryToConnect(): " + Log.getStackTraceString(e));
			throw new YaximXMPPException(e.getLocalizedMessage(), e.getCause());
		}
	}

	private void tryToMoveRosterEntryToGroup(String userName, String groupName)
			throws YaximXMPPException {

		mRoster = mXMPPConnection.getRoster();
		RosterGroup rosterGroup = getRosterGroup(groupName);
		RosterEntry rosterEntry = mRoster.getEntry(userName);

		removeRosterEntryFromGroups(rosterEntry);

		if (groupName.length() == 0)
			return;
		else {
			try {
				rosterGroup.addEntry(rosterEntry);
			} catch (XMPPException e) {
				throw new YaximXMPPException(e.getLocalizedMessage());
			}
		}
	}

	private RosterGroup getRosterGroup(String groupName) {
		RosterGroup rosterGroup = mRoster.getGroup(groupName);

		// create group if unknown
		if ((groupName.length() > 0) && rosterGroup == null) {
			rosterGroup = mRoster.createGroup(groupName);
		}
		return rosterGroup;

	}

	private void removeRosterEntryFromGroups(RosterEntry rosterEntry)
			throws YaximXMPPException {
		Collection<RosterGroup> oldGroups = rosterEntry.getGroups();

		for (RosterGroup group : oldGroups) {
			tryToRemoveUserFromGroup(group, rosterEntry);
		}
	}

	private void tryToRemoveUserFromGroup(RosterGroup group,
			RosterEntry rosterEntry) throws YaximXMPPException {
		try {
			group.removeEntry(rosterEntry);
		} catch (XMPPException e) {
			throw new YaximXMPPException(e.getLocalizedMessage());
		}
	}

	private void tryToRemoveRosterEntry(String user) throws YaximXMPPException {
		mRoster = mXMPPConnection.getRoster();
		try {
			RosterEntry rosterEntry = mRoster.getEntry(user);

			if (rosterEntry != null) {
				mRoster.removeEntry(rosterEntry);
			}
		} catch (XMPPException e) {
			throw new YaximXMPPException(e.getLocalizedMessage());
		}
	}

	private void tryToAddRosterEntry(String user, String alias, String group)
			throws YaximXMPPException {
		mRoster = mXMPPConnection.getRoster();
		try {
			mRoster.createEntry(user, alias, new String[] { group });
		} catch (XMPPException e) {
			throw new YaximXMPPException(e.getLocalizedMessage());
		}
	}

	private void setRosterEntries() {
		mRoster = mXMPPConnection.getRoster();
		Collection<RosterEntry> rosterEntries = mRoster.getEntries();
		StringBuilder exclusion = new StringBuilder(RosterConstants.JID + " NOT IN (");
		boolean first = true;
		for (RosterEntry rosterEntry : rosterEntries) {
			updateRosterEntryInDB(rosterEntry);
			if (first)
				first = false;
			else
				exclusion.append(",");
			exclusion.append("'").append(rosterEntry.getUser()).append("'");
		}
		exclusion.append(")");
		mContentResolver.delete(RosterProvider.CONTENT_URI, exclusion.toString(), null);
	}


	public void setStatusFromConfig() {
		Presence presence = new Presence(Presence.Type.available);
		Mode mode = Mode.valueOf(mConfig.statusMode);
		presence.setMode(mode);
		presence.setStatus(mConfig.statusMessage);
		presence.setPriority(mConfig.priority);
		mXMPPConnection.sendPacket(presence);
	}

	public void sendOfflineMessages() {
		Cursor cursor = mContentResolver.query(ChatProvider.CONTENT_URI,
				SEND_OFFLINE_PROJECTION, SEND_OFFLINE_SELECTION,
				null, null);
		final int      _ID_COL = cursor.getColumnIndexOrThrow(ChatConstants._ID);
		final int      JID_COL = cursor.getColumnIndexOrThrow(ChatConstants.JID);
		final int      MSG_COL = cursor.getColumnIndexOrThrow(ChatConstants.MESSAGE);
		final int       TS_COL = cursor.getColumnIndexOrThrow(ChatConstants.DATE);
		final int PACKETID_COL = cursor.getColumnIndexOrThrow(ChatConstants.PACKET_ID);
		ContentValues mark_sent = new ContentValues();
		mark_sent.put(ChatConstants.DELIVERY_STATUS, ChatConstants.DS_SENT);
		while (cursor.moveToNext()) {
			int _id = cursor.getInt(_ID_COL);
			String toJID = cursor.getString(JID_COL);
			String message = cursor.getString(MSG_COL);
			String packetID = cursor.getString(PACKETID_COL);
			long ts = cursor.getLong(TS_COL);
			Log.d(TAG, "sendOfflineMessages: " + toJID + " > " + message);
			final Message newMessage = new Message(toJID, Message.Type.chat);
			newMessage.setBody(message);
			DelayInformation delay = new DelayInformation(new Date(ts));
			newMessage.addExtension(delay);
			newMessage.addExtension(new DelayInfo(delay));
			newMessage.addExtension(new DeliveryReceiptRequest());
			if ((packetID != null) && (packetID.length() > 0)) {
				newMessage.setPacketID(packetID);
			} else {
				packetID = newMessage.getPacketID();
				mark_sent.put(ChatConstants.PACKET_ID, packetID);
			}
			Uri rowuri = Uri.parse("content://" + ChatProvider.AUTHORITY
				+ "/" + ChatProvider.TABLE_NAME + "/" + _id);
			mContentResolver.update(rowuri, mark_sent,
						null, null);
			mXMPPConnection.sendPacket(newMessage);		// must be after marking delivered, otherwise it may override the SendFailListener
		}
		cursor.close();
	}

	public static void sendOfflineMessage(ContentResolver cr, String toJID, String message) {
		ContentValues values = new ContentValues();
		values.put(ChatConstants.DIRECTION, ChatConstants.OUTGOING);
		values.put(ChatConstants.JID, toJID);
		values.put(ChatConstants.MESSAGE, message);
		values.put(ChatConstants.DELIVERY_STATUS, ChatConstants.DS_NEW);
		values.put(ChatConstants.DATE, System.currentTimeMillis());

		cr.insert(ChatProvider.CONTENT_URI, values);
	}

	public void sendReceipt(String toJID, String id) {
		Log.d(TAG, "sending XEP-0184 ack to " + toJID + " id=" + id);
		final Message ack = new Message(toJID, Message.Type.normal);
		ack.addExtension(new DeliveryReceipt(id));
		mXMPPConnection.sendPacket(ack);
	}

	public void sendMessage(String toJID, String message) {
		final Message newMessage = new Message(toJID, Message.Type.chat);
		newMessage.setBody(message);
		newMessage.addExtension(new DeliveryReceiptRequest());
		if (isAuthenticated()) {
			addChatMessageToDB(ChatConstants.OUTGOING, toJID, message, ChatConstants.DS_SENT,
					System.currentTimeMillis(), newMessage.getPacketID());
			mXMPPConnection.sendPacket(newMessage);
		} else {
			// send offline -> store to DB
			addChatMessageToDB(ChatConstants.OUTGOING, toJID, message, ChatConstants.DS_NEW,
					System.currentTimeMillis(), newMessage.getPacketID());
		}
	}

	public boolean isAuthenticated() {
		if (mXMPPConnection != null) {
			return (mXMPPConnection.isConnected() && mXMPPConnection
					.isAuthenticated());
		}
		return false;
	}

	public void registerCallback(XMPPServiceCallback callBack) {
		this.mServiceCallBack = callBack;
	}

	public void unRegisterCallback() {
		debugLog("unRegisterCallback()");
		// remove callbacks _before_ tossing old connection
		try {
			mXMPPConnection.getRoster().removeRosterListener(mRosterListener);
			mXMPPConnection.removePacketListener(mPacketListener);
			mXMPPConnection.removePacketSendFailureListener(mSendFailureListener);
			mXMPPConnection.removePacketListener(mPingListener);
			mXMPPConnection.removePacketListener(mPongListener);
			((AlarmManager)mService.getSystemService(Context.ALARM_SERVICE)).cancel(mPingAlarmPendIntent);
			((AlarmManager)mService.getSystemService(Context.ALARM_SERVICE)).cancel(mPongTimeoutAlarmPendIntent);
			mService.unregisterReceiver(mPingAlarmReceiver);
			mService.unregisterReceiver(mPongTimeoutAlarmReceiver);
		} catch (Exception e) {
			// ignore it!
		}
		if (mXMPPConnection.isConnected()) {
			// work around SMACK's #%&%# blocking disconnect()
			new Thread() {
				public void run() {
					debugLog("shutDown thread started");
					mXMPPConnection.disconnect();
					debugLog("shutDown thread finished");
				}
			}.start();
		}
		setStatusOffline();
		this.mServiceCallBack = null;
	}
	
	public String getNameForJID(String jid) {
		if (null != this.mRoster.getEntry(jid) && null != this.mRoster.getEntry(jid).getName() && this.mRoster.getEntry(jid).getName().length() > 0) {
			return this.mRoster.getEntry(jid).getName();
		} else {
			return jid;
		}			
	}

	private void setStatusOffline() {
		ContentValues values = new ContentValues();
		values.put(RosterConstants.STATUS_MODE, StatusMode.offline.ordinal());
		mContentResolver.update(RosterProvider.CONTENT_URI, values, null, null);
	}

	private void registerRosterListener() {
		// flush roster on connecting.
		mRoster = mXMPPConnection.getRoster();

		mRosterListener = new RosterListener() {

			public void entriesAdded(Collection<String> entries) {
				debugLog("entriesAdded(" + entries + ")");

				for (String entry : entries) {
					RosterEntry rosterEntry = mRoster.getEntry(entry);
					addRosterEntryToDB(rosterEntry);
				}
				mServiceCallBack.rosterChanged();
			}

			public void entriesDeleted(Collection<String> entries) {
				debugLog("entriesDeleted(" + entries + ")");

				for (String entry : entries) {
					deleteRosterEntryFromDB(entry);
				}
				mServiceCallBack.rosterChanged();
			}

			public void entriesUpdated(Collection<String> entries) {
				debugLog("entriesUpdated(" + entries + ")");

				for (String entry : entries) {
					RosterEntry rosterEntry = mRoster.getEntry(entry);
					updateRosterEntryInDB(rosterEntry);
				}
				mServiceCallBack.rosterChanged();
			}

			public void presenceChanged(Presence presence) {
				debugLog("presenceChanged(" + presence.getFrom() + "): " + presence);

				String jabberID = getJabberID(presence.getFrom());
				RosterEntry rosterEntry = mRoster.getEntry(jabberID);
				updateRosterEntryInDB(rosterEntry);
				mServiceCallBack.rosterChanged();
			}
		};
		mRoster.addRosterListener(mRosterListener);
	}

	private String getJabberID(String from) {
		String[] res = from.split("/");
		return res[0].toLowerCase();
	}

	public void changeMessageDeliveryStatus(String packetID, int new_status) {
		ContentValues cv = new ContentValues();
		cv.put(ChatConstants.DELIVERY_STATUS, new_status);
		Uri rowuri = Uri.parse("content://" + ChatProvider.AUTHORITY + "/"
				+ ChatProvider.TABLE_NAME);
		mContentResolver.update(rowuri, cv,
				ChatConstants.PACKET_ID + " = ? AND " +
				ChatConstants.DIRECTION + " = " + ChatConstants.OUTGOING,
				new String[] { packetID });
	}

	private void sendPing() {
		Ping ping = new Ping();
		ping.setType(Type.GET);
		ping.setTo(mConfig.server);
		mPingID = ping.getPacketID();
		debugLog("Send PING with ID " + mPingID);
		mXMPPConnection.sendPacket(ping);
	}

	/**
	 * BroadcastReceiver to trigger reconnect on pong timeout.
	 */
	private class PongTimeoutAlarmReceiver extends BroadcastReceiver {
		public void onReceive(Context ctx, Intent i) {
			debugLog("Pong Timeout Alarm received.");
			mServiceCallBack.disconnectOnError();
			unRegisterCallback();
		}
	}

	/**
	 * BroadcastReceiver to trigger sending pings to the server
	 */
	private class PingAlarmReceiver extends BroadcastReceiver {
		public void onReceive(Context ctx, Intent i) {
			if (mXMPPConnection.isAuthenticated()) {
				debugLog("Ping Alarm received.");
				sendPing();
				((AlarmManager)mService.getSystemService(Context.ALARM_SERVICE)).set(AlarmManager.RTC_WAKEUP, 
						System.currentTimeMillis() + PACKET_TIMEOUT + 3000, mPongTimeoutAlarmPendIntent);
			} else
				debugLog("Ping Alarm received, but not connected to server.");
		}
	}

	/**
	 * Registers a smack packet listener for IQ packets, intended to recognize "pongs" with
	 * a packet id matching the last "ping" sent to the server.
	 *
	 * Also sets up the AlarmManager Timer plus necessary intents.
	 */
	private void registerPongListener() {

		if (mPongListener != null)
			mXMPPConnection.removePacketListener(mPongListener);

		PacketFilter filter = new PacketFilter() {

			@Override
			public boolean accept(Packet packet) {
				if ((packet instanceof IQ)) {
					debugLog("got IQ packet with ID: " + packet.getPacketID());
					return true;
				}
				return false;
			}
		};

		mPongListener = new PacketListener() {

			@Override
			public void processPacket(Packet packet) {
				if (packet == null) return;

				if (packet.getPacketID().equals(mPingID)) {
					debugLog("got Pong");
					((AlarmManager)mService.getSystemService(Context.ALARM_SERVICE)).cancel(mPongTimeoutAlarmPendIntent);
				}
			}

		};

		mXMPPConnection.addPacketListener(mPongListener, filter);
		mPingAlarmPendIntent = PendingIntent.getBroadcast(mService.getApplicationContext(), 0, mPingAlarmIntent,
					PendingIntent.FLAG_UPDATE_CURRENT);
		mPongTimeoutAlarmPendIntent = PendingIntent.getBroadcast(mService.getApplicationContext(), 0, mPongTimeoutAlarmIntent,
					PendingIntent.FLAG_UPDATE_CURRENT);
		mService.registerReceiver(mPingAlarmReceiver, new IntentFilter(PING_ALARM));
		mService.registerReceiver(mPongTimeoutAlarmReceiver, new IntentFilter(PONG_TIMEOUT_ALARM));
		((AlarmManager)mService.getSystemService(Context.ALARM_SERVICE)).setInexactRepeating(AlarmManager.RTC_WAKEUP, 
				System.currentTimeMillis() + AlarmManager.INTERVAL_FIFTEEN_MINUTES, AlarmManager.INTERVAL_FIFTEEN_MINUTES, mPingAlarmPendIntent);
	}

	/**
	 * Registers a smack packet listener for IQ packets with a ping inside.
	 * Generates and sends a matching IQ response ("pong") to the peer.
	 */
	private void registerPingListener() {

		if (mPingListener != null)
			mXMPPConnection.removePacketListener(mPingListener);

		PacketFilter filter = new PacketFilter() {

			@Override
			public boolean accept(Packet packet) {
				if (packet instanceof Ping) return true;
				return false;
			}
		};

		mPingListener = new PacketListener() {

			@Override
			public void processPacket(Packet packet) {
				if (packet == null) return;

				mXMPPConnection.sendPacket(new Pong((Ping)packet));
			}

		};

		mXMPPConnection.addPacketListener(mPingListener, filter);
	}

	private void registerMessageSendFailureListener() {
		// do not register multiple packet listeners
		if (mSendFailureListener != null)
			mXMPPConnection.removePacketSendFailureListener(mSendFailureListener);

		PacketTypeFilter filter = new PacketTypeFilter(Message.class);

		mSendFailureListener = new PacketListener() {
			public void processPacket(Packet packet) {
				try {
				if (packet instanceof Message) {
					Message msg = (Message) packet;
					String chatMessage = msg.getBody();

					Log.d("SmackableImp", "message " + chatMessage + " could not be sent (ID:" + (msg.getPacketID() == null ? "null" : msg.getPacketID()) + ")");
					changeMessageDeliveryStatus(msg.getPacketID(), ChatConstants.DS_NEW);
				}
				} catch (Exception e) {
					// SMACK silently discards exceptions dropped from processPacket :(
					Log.e(TAG, "failed to process packet:");
					e.printStackTrace();
				}
			}
		};

		mXMPPConnection.addPacketSendFailureListener(mSendFailureListener, filter);
	}

	private void registerMessageListener() {
		// do not register multiple packet listeners
		if (mPacketListener != null)
			mXMPPConnection.removePacketListener(mPacketListener);

		PacketTypeFilter filter = new PacketTypeFilter(Message.class);

		mPacketListener = new PacketListener() {
			public void processPacket(Packet packet) {
				try {
				if (packet instanceof Message) {
					Message msg = (Message) packet;
					String chatMessage = msg.getBody();

					DeliveryReceipt dr = (DeliveryReceipt)msg.getExtension("received", DeliveryReceipt.NAMESPACE);
					if (dr != null) {
						Log.d(TAG, "got delivery receipt for " + dr.getId());
						changeMessageDeliveryStatus(dr.getId(), ChatConstants.DS_DELIVERED);
					}

					if (chatMessage == null) {
						return;
					}
					if (msg.getType() == Message.Type.error) {
						chatMessage = "<Error> " + chatMessage;
					}

					long ts;
					DelayInfo timestamp = (DelayInfo)msg.getExtension("delay", "urn:xmpp:delay");
					if (timestamp == null)
						timestamp = (DelayInfo)msg.getExtension("x", "jabber:x:delay");
					if (timestamp != null)
						ts = timestamp.getStamp().getTime();
					else
						ts = System.currentTimeMillis();

					String fromJID = getJabberID(msg.getFrom());

					if (msg.getExtension("request", DeliveryReceipt.NAMESPACE) != null) {
						// got XEP-0184 request, send receipt
						sendReceipt(msg.getFrom(), msg.getPacketID());
					}
					addChatMessageToDB(ChatConstants.INCOMING, fromJID, chatMessage, ChatConstants.DS_NEW, ts, msg.getPacketID());
					mServiceCallBack.newMessage(fromJID, chatMessage);
				}
				} catch (Exception e) {
					// SMACK silently discards exceptions dropped from processPacket :(
					Log.e(TAG, "failed to process packet:");
					e.printStackTrace();
				}
			}
		};

		mXMPPConnection.addPacketListener(mPacketListener, filter);
	}

	private void addChatMessageToDB(int direction, String JID,
			String message, int delivery_status, long ts, String packetID) {
		ContentValues values = new ContentValues();

		values.put(ChatConstants.DIRECTION, direction);
		values.put(ChatConstants.JID, JID);
		values.put(ChatConstants.MESSAGE, message);
		values.put(ChatConstants.DELIVERY_STATUS, delivery_status);
		values.put(ChatConstants.DATE, ts);
		values.put(ChatConstants.PACKET_ID, packetID);

		mContentResolver.insert(ChatProvider.CONTENT_URI, values);
	}

	private ContentValues getContentValuesForRosterEntry(final RosterEntry entry) {
		final ContentValues values = new ContentValues();

		values.put(RosterConstants.JID, entry.getUser());
		values.put(RosterConstants.ALIAS, getName(entry));

		Presence presence = mRoster.getPresence(entry.getUser());
		values.put(RosterConstants.STATUS_MODE, getStatusInt(presence));
		values.put(RosterConstants.STATUS_MESSAGE, presence.getStatus());
		values.put(RosterConstants.GROUP, getGroup(entry.getGroups()));

		return values;
	}

	private void addRosterEntryToDB(final RosterEntry entry) {
		ContentValues values = getContentValuesForRosterEntry(entry);
		Uri uri = mContentResolver.insert(RosterProvider.CONTENT_URI, values);
		debugLog("addRosterEntryToDB: Inserted " + uri);
	}

	private void deleteRosterEntryFromDB(final String jabberID) {
		int count = mContentResolver.delete(RosterProvider.CONTENT_URI,
				RosterConstants.JID + " = ?", new String[] { jabberID });
		debugLog("deleteRosterEntryFromDB: Deleted " + count + " entries");
	}

	private void updateRosterEntryInDB(final RosterEntry entry) {
		final ContentValues values = getContentValuesForRosterEntry(entry);

		if (mContentResolver.update(RosterProvider.CONTENT_URI, values,
				RosterConstants.JID + " = ?", new String[] { entry.getUser() }) == 0)
			addRosterEntryToDB(entry);
	}

	private String getGroup(Collection<RosterGroup> groups) {
		for (RosterGroup group : groups) {
			return group.getName();
		}
		return "";
	}

	private String getName(RosterEntry rosterEntry) {
		String name = rosterEntry.getName();
		if (name != null && name.length() > 0) {
			return name;
		}
		name = StringUtils.parseName(rosterEntry.getUser());
		if (name.length() > 0) {
			return name;
		}
		return rosterEntry.getUser();
	}

	private StatusMode getStatus(Presence presence) {
		if (presence.getType() == Presence.Type.available) {
			if (presence.getMode() != null) {
				return StatusMode.valueOf(presence.getMode().name());
			}
			return StatusMode.available;
		}
		return StatusMode.offline;
	}

	private int getStatusInt(final Presence presence) {
		return getStatus(presence).ordinal();
	}

	private void debugLog(String data) {
		if (LogConstants.LOG_DEBUG) {
			Log.d(TAG, data);
		}
	}
}
