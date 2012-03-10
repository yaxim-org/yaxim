package org.yaxim.androidclient.service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.Set;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import javax.net.ssl.SSLContext;
import javax.net.ssl.X509TrustManager;

import de.duenndns.ssl.MemorizingTrustManager;

import org.jivesoftware.smack.ConnectionConfiguration;
import org.jivesoftware.smack.PacketListener;
import org.jivesoftware.smack.Roster;
import org.jivesoftware.smack.RosterEntry;
import org.jivesoftware.smack.RosterGroup;
import org.jivesoftware.smack.RosterListener;
import org.jivesoftware.smack.SmackConfiguration;
import org.jivesoftware.smack.XMPPConnection;
import org.jivesoftware.smack.XMPPException;
import org.jivesoftware.smack.filter.PacketTypeFilter;
import org.jivesoftware.smack.packet.Message;
import org.jivesoftware.smack.packet.Packet;
import org.jivesoftware.smack.packet.Presence;
import org.jivesoftware.smack.packet.Presence.Mode;
import org.jivesoftware.smack.provider.ProviderManager;
import org.jivesoftware.smack.util.StringUtils;
import org.jivesoftware.smackx.ServiceDiscoveryManager;
import org.jivesoftware.smackx.provider.DelayInfoProvider;
import org.jivesoftware.smackx.provider.DeliveryReceiptProvider;
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
import org.yaxim.androidclient.util.AdapterConstants;
import org.yaxim.androidclient.util.LogConstants;
import org.yaxim.androidclient.util.PreferenceConstants;
import org.yaxim.androidclient.util.StatusMode;

import android.app.Service;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.database.Cursor;

import android.net.Uri;
import android.util.Log;

public class SmackableImp implements Smackable {
	final static private String TAG = "yaxim.SmackableImp";

	final static private int PACKET_TIMEOUT = 12000;
	final static private int KEEPALIVE_TIMEOUT = 300000; // 5min

	final static private String[] SEND_OFFLINE_PROJECTION = new String[] {
			ChatConstants._ID, ChatConstants.JID,
			ChatConstants.MESSAGE, ChatConstants.DATE, ChatConstants.PACKET_ID };
	final static private String SEND_OFFLINE_SELECTION = "from_me = 1 AND read = 0";

	static {
		registerSmackProviders();
	}

	static void registerSmackProviders() {
		ProviderManager pm = ProviderManager.getInstance();
		// add delayed delivery notifications
		pm.addExtensionProvider("delay","urn:xmpp:delay", new DelayInfoProvider());
		pm.addExtensionProvider("x","jabber:x:delay", new DelayInfoProvider());
		// add delivery receipts
		pm.addExtensionProvider("received", DeliveryReceipt.NAMESPACE, new DeliveryReceiptProvider());

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
	}

	public boolean doConnect() throws YaximXMPPException {
		tryToConnect();
		// actually, authenticated must be true now, or an exception must have
		// been thrown.
		if (isAuthenticated()) {
			registerMessageListener();
			registerMessageSendFailureListener();
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

		sdm.addFeature(DeliveryReceipt.NAMESPACE);
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

	private void tryToConnect() throws YaximXMPPException {
		try {
			if (mXMPPConnection.isConnected()) {
				try {
					mXMPPConnection.disconnect();
				} catch (Exception e) {
					debugLog("conn.disconnect() failed: " + e);
				}
			}
			SmackConfiguration.setPacketReplyTimeout(PACKET_TIMEOUT);
			SmackConfiguration.setKeepAliveInterval(KEEPALIVE_TIMEOUT);
			mXMPPConnection.connect();
			if (!mXMPPConnection.isConnected()) {
				throw new YaximXMPPException("SMACK connect failed without exception!");
			}
			initServiceDiscovery();
			// SMACK auto-logins if we were authenticated before
			if (!mXMPPConnection.isAuthenticated()) {
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

		if (!(groupName.length() > 0)) {
			throw new YaximXMPPException("Can't move " + userName
					+ " to a group without a name!");
		}

		mRoster = mXMPPConnection.getRoster();
		RosterGroup rosterGroup = getRosterGroup(groupName);
		RosterEntry rosterEntry = mRoster.getEntry(userName);

		removeRosterEntryFromGroups(rosterEntry);

		if (groupName.equals(AdapterConstants.EMPTY_GROUP))
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

		if (!(groupName.equals(AdapterConstants.EMPTY_GROUP))) {
			if (rosterGroup == null) {
				rosterGroup = mRoster.createGroup(groupName);
			}
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
		for (RosterEntry rosterEntry : rosterEntries) {
			addRosterEntryToDB(rosterEntry);
		}
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
		ContentValues mark_delivered = new ContentValues();
		mark_delivered.put(ChatConstants.HAS_BEEN_READ, ChatConstants.DELIVERED);
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
				mark_delivered.put(ChatConstants.PACKET_ID, packetID);
			}
			Uri rowuri = Uri.parse("content://" + ChatProvider.AUTHORITY
				+ "/" + ChatProvider.TABLE_NAME + "/" + _id);
				mContentResolver.update(rowuri, mark_delivered,
						null, null);
			mXMPPConnection.sendPacket(newMessage);		// must be after marking delivered, otherwise it may override the SendFailListener
		}
		cursor.close();
	}

	public static void sendOfflineMessage(ContentResolver cr, String toJID, String message) {
		ContentValues values = new ContentValues();
		values.put(ChatConstants.FROM_ME, true);
		values.put(ChatConstants.JID, toJID);
		values.put(ChatConstants.MESSAGE, message);
		values.put(ChatConstants.HAS_BEEN_READ, false);
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
			addChatMessageToDB(ChatConstants.OUTGOING, toJID, message, ChatConstants.DELIVERED,
					System.currentTimeMillis(), newMessage.getPacketID());
			mXMPPConnection.sendPacket(newMessage);
		} else {
			// send offline -> store to DB
			addChatMessageToDB(ChatConstants.OUTGOING, toJID, message, ChatConstants.UNREAD,
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
		mContentResolver.delete(RosterProvider.GROUPS_URI, "", null);
		mContentResolver.delete(RosterProvider.CONTENT_URI, "", null);
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
					RosterEntry rosterEntry = mRoster.getEntry(entry);
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
				debugLog("presenceChanged(" + presence.getFrom() + ")");

				String jabberID = getJabberID(presence.getFrom());
				RosterEntry rosterEntry = mRoster.getEntry(jabberID);
				updateOrInsertRosterEntryToDB(rosterEntry);
				mServiceCallBack.rosterChanged();
			}
		};
		mRoster.addRosterListener(mRosterListener);
	}

	private String getJabberID(String from) {
		String[] res = from.split("/");
		return res[0].toLowerCase();
	}

	public void markMessageAsSendFailed(String packetID) {
		ContentValues mark_undelivered = new ContentValues();
		mark_undelivered.put(ChatConstants.HAS_BEEN_READ, ChatConstants.UNREAD);
		Uri rowuri = Uri.parse("content://" + ChatProvider.AUTHORITY + "/"
				+ ChatProvider.TABLE_NAME);
		mContentResolver.update(rowuri, mark_undelivered,
				ChatConstants.PACKET_ID + " = ? AND " + ChatConstants.FROM_ME + " = 1", new String[] { packetID });
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
					markMessageAsSendFailed(msg.getPacketID());
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
					String toJID = getJabberID(msg.getTo());

					if (msg.getExtension("request", DeliveryReceipt.NAMESPACE) != null) {
						// got XEP-0184 request, send receipt
						sendReceipt(fromJID, msg.getPacketID());
					}
					addChatMessageToDB(ChatConstants.INCOMING, fromJID, chatMessage, ChatConstants.UNREAD, ts, msg.getPacketID());
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

	private void addChatMessageToDB(boolean from_me, String JID,
			String message, boolean read, long ts, String packetID) {
		ContentValues values = new ContentValues();

		values.put(ChatConstants.FROM_ME, from_me);
		values.put(ChatConstants.JID, JID);
		values.put(ChatConstants.MESSAGE, message);
		values.put(ChatConstants.HAS_BEEN_READ, read);
		values.put(ChatConstants.DATE, ts);
		values.put(ChatConstants.PACKET_ID, packetID);

		mContentResolver.insert(ChatProvider.CONTENT_URI, values);
	}

	private void addRosterEntryToDB(final RosterEntry entry) {
		ContentValues values = getContentValuesForRosterEntry(entry);
		Uri uri = mContentResolver.insert(RosterProvider.CONTENT_URI, values);
		debugLog("addRosterEntryToDB: Inserted " + uri);
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

	private void deleteRosterEntryFromDB(final String jabberID) {
		int count = mContentResolver.delete(RosterProvider.CONTENT_URI,
				RosterConstants.JID + " = ?", new String[] { jabberID });
		debugLog("deleteRosterEntryFromDB: Deleted " + count + " entries");
	}

	private void updateRosterEntryInDB(final RosterEntry entry) {
		final ContentValues values = getContentValuesForRosterEntry(entry);

		mContentResolver.update(RosterProvider.CONTENT_URI, values,
				RosterConstants.JID + " = ?", new String[] { entry.getUser() });
	}

	private void updateOrInsertRosterEntryToDB(final RosterEntry entry) {
		try {
			//deleteRosterEntryFromDB(entry.getUser());
			addRosterEntryToDB(entry);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	private String getGroup(Collection<RosterGroup> groups) {
		for (RosterGroup group : groups) {
			return group.getName();
		}
		return AdapterConstants.EMPTY_GROUP;
	}

	private String getName(RosterEntry rosterEntry) {
		String name = rosterEntry.getName();
		if (name != null && name != "") {
			return name;
		}
		name = StringUtils.parseName(rosterEntry.getUser());
		if (name != "") {
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
