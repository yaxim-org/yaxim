package org.yaxim.androidclient.service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Set;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

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
import org.jivesoftware.smack.util.StringUtils;
import org.yaxim.androidclient.data.ChatProvider;
import org.yaxim.androidclient.data.RosterItem;
import org.yaxim.androidclient.data.RosterProvider;
import org.yaxim.androidclient.data.YaximConfiguration;
import org.yaxim.androidclient.data.ChatProvider.ChatConstants;
import org.yaxim.androidclient.data.RosterProvider.RosterConstants;
import org.yaxim.androidclient.exceptions.YaximXMPPException;
import org.yaxim.androidclient.util.AdapterConstants;
import org.yaxim.androidclient.util.LogConstants;
import org.yaxim.androidclient.util.StatusMode;
import org.yaxim.androidclient.util.StatusModeInt;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.database.Cursor;
import android.os.PowerManager;

import android.net.Uri;
import android.util.Log;

public class SmackableImp implements Smackable {
	final static private String TAG = "SmackableImp";

	final static private int PACKET_TIMEOUT = 12000;
	final static private int KEEPALIVE_TIMEOUT = 300000; // 5min

	final static private String[] SEND_OFFLINE_PROJECTION = new String[] {
			ChatConstants._ID, ChatConstants.JID, ChatConstants.MESSAGE };
	final static private String SEND_OFFLINE_SELECTION = "from_me = 1 AND read = 0";


	private final YaximConfiguration mConfig;
	private final ConnectionConfiguration mXMPPConfig;
	private final XMPPConnection mXMPPConnection;
	private PowerManager.WakeLock mWakeLock;

	private XMPPServiceCallback mServiceCallBack;
	private Roster mRoster;

	private final ConcurrentHashMap<String, ConcurrentHashMap<String, RosterItem>> mRosterItemsByGroup = new ConcurrentHashMap<String, ConcurrentHashMap<String, RosterItem>>();
	private final ContentResolver mContentResolver;

	public SmackableImp(YaximConfiguration config,
			ContentResolver contentResolver,
			PowerManager.WakeLock wakelock) {
		this.mConfig = config;
		this.mXMPPConfig = new ConnectionConfiguration(mConfig.server,
				mConfig.port);
		this.mXMPPConfig.setReconnectionAllowed(true);
		this.mXMPPConnection = new XMPPConnection(mXMPPConfig);
		this.mContentResolver = contentResolver;
		mWakeLock = wakelock;
	}

	public boolean doConnect() throws YaximXMPPException {
		tryToConnect();
		// actually, authenticated must be true now, or an exception must have
		// been thrown.
		if (isAuthenticated()) {
			registerMessageListener();
			registerRosterListener();
			Presence presence = new Presence(Presence.Type.available);
			presence.setPriority(mConfig.priority);
			mXMPPConnection.sendPacket(presence);
			setRosterEntries();
		}
		return isAuthenticated();
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
		mRosterItemsByGroup.remove(group);
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
			// SMACK auto-logins if we were authenticated before
			if (!mXMPPConnection.isAuthenticated()) {
				mXMPPConnection.login(mConfig.userName, mConfig.password,
						mConfig.ressource);
			}
			sendOfflineMessages();
		} catch (Exception e) {
			// actually we just care for IllegalState, NullPointer or XMPPEx.
			throw new YaximXMPPException(e.getLocalizedMessage());
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
			if (group.getEntries().size() < 1) {
				mRosterItemsByGroup.remove(group.getName());
			}
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
			setRosterEntry(rosterEntry);
			addRosterEntryToDB(rosterEntry);
		}
		mServiceCallBack.rosterChanged();
	}

	private void setRosterEntry(RosterEntry rosterEntry) {
		String groupName = getGroup(rosterEntry.getGroups());
		ConcurrentMap<String, RosterItem> entryMap = getEntryMapForGroup(groupName);
		RosterItem rosterItem = getRosterItemForRosterEntry(rosterEntry);
		entryMap.put(rosterItem.jabberID, rosterItem);
	}

	private void unSetRosterEntry(RosterEntry rosterEntry) {
		String jabberID = rosterEntry.getUser();

		Set<Entry<String, ConcurrentHashMap<String, RosterItem>>> groupMaps = mRosterItemsByGroup
				.entrySet();

		for (Entry<String, ConcurrentHashMap<String, RosterItem>> entry : groupMaps) {
			ConcurrentHashMap<String, RosterItem> entryMap = entry.getValue();
			String groupName = entry.getKey();

			if (entryMap.containsKey(jabberID)) {
				entryMap.remove(jabberID);
			}
			if (entryMap.size() < 1) {
				mRosterItemsByGroup.remove(groupName);
			}
		}
	}

	private ConcurrentMap<String, RosterItem> getEntryMapForGroup(
			String groupName) {
		ConcurrentHashMap<String, RosterItem> tmpItemList;
		if (mRosterItemsByGroup.containsKey(groupName))
			return mRosterItemsByGroup.get(groupName);
		else {
			tmpItemList = new ConcurrentHashMap<String, RosterItem>();
			mRosterItemsByGroup.put(groupName, tmpItemList);
		}
		return tmpItemList;
	}

	public ArrayList<RosterItem> getRosterEntriesByGroup(String group) {
		ArrayList<RosterItem> groupItems = new ArrayList<RosterItem>();

		ConcurrentHashMap<String, RosterItem> rosterItemMap = mRosterItemsByGroup
				.get(group);

		if (rosterItemMap != null) {
			groupItems.addAll(rosterItemMap.values());
			Collections.sort(groupItems);
		}

		return groupItems;
	}

	private RosterItem getRosterItemForRosterEntry(RosterEntry entry) {
		String jabberID = entry.getUser();
		String userName = getName(entry);
		Presence userPresence = mRoster.getPresence(entry.getUser());
		StatusMode userStatus = getStatus(userPresence);
		String userStatusMessage = userPresence.getStatus();
		String userGroups = getGroup(entry.getGroups());


		debugLog("getRosterItemForRosterEntry(): " + jabberID + " -> " + userName);
		return new RosterItem(jabberID, userName, userStatus,
				userStatusMessage, userGroups);
	}

	public ArrayList<String> getRosterGroups() {
		ArrayList<String> rosterGroups = new ArrayList<String>(
				mRosterItemsByGroup.keySet());
		Collections.sort(rosterGroups, new Comparator<String>() {
			public int compare(String group1, String group2) {
				if (group1.equals(AdapterConstants.EMPTY_GROUP))
					return -1;
				return group1.toLowerCase().compareTo(group2.toLowerCase());
			}
		});
		return rosterGroups;
	}

	public void setStatus(StatusMode status, String statusMsg) {
		Presence presence = new Presence(Presence.Type.available);
		Mode mode = stringToMode(status.name());
		presence.setMode(mode);
		presence.setStatus(statusMsg);
		mXMPPConnection.sendPacket(presence);
	}

	private Mode stringToMode(String modeStr) {
		return Mode.valueOf(modeStr);
	}

	public void sendOfflineMessages() {
		Cursor cursor = mContentResolver.query(ChatProvider.CONTENT_URI,
				SEND_OFFLINE_PROJECTION, SEND_OFFLINE_SELECTION,
				null, null);
		final int _ID_COL = cursor.getColumnIndexOrThrow(ChatConstants._ID);
		final int JID_COL = cursor.getColumnIndexOrThrow(ChatConstants.JID);
		final int MSG_COL = cursor.getColumnIndexOrThrow(ChatConstants.MESSAGE);
		ContentValues mark_delivered = new ContentValues();
		mark_delivered.put(ChatConstants.HAS_BEEN_READ, ChatConstants.DELIVERED);
		while (cursor.moveToNext()) {
			int _id = cursor.getInt(_ID_COL);
			String toJID = cursor.getString(JID_COL);
			String message = cursor.getString(MSG_COL);
			Log.d(TAG, "sendOfflineMessages: " + toJID + " > " + message);
			final Message newMessage = new Message(toJID, Message.Type.chat);
			newMessage.setBody(message);
			mXMPPConnection.sendPacket(newMessage);

			Uri rowuri = Uri.parse("content://" + ChatProvider.AUTHORITY
				+ "/" + ChatProvider.TABLE_NAME + "/" + _id);
				mContentResolver.update(rowuri, mark_delivered,
						null, null);
			mContentResolver.update(ChatProvider.CONTENT_URI, mark_delivered,
					SEND_OFFLINE_SELECTION, null);
		}
	}

	public void sendMessage(String toJID, String message) {
		final Message newMessage = new Message(toJID, Message.Type.chat);
		newMessage.setBody(message);
		if (isAuthenticated()) {
			mXMPPConnection.sendPacket(newMessage);
			addChatMessageToDB(ChatConstants.OUTGOING, toJID, message, ChatConstants.DELIVERED);
		} else {
			// send offline -> store to DB
			addChatMessageToDB(ChatConstants.OUTGOING, toJID, message, ChatConstants.UNREAD);
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
		if (mXMPPConnection.isConnected()) {
			mXMPPConnection.disconnect();
		}
		mRosterItemsByGroup.clear();
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
		values.put(RosterConstants.STATUS_MODE, StatusModeInt.MODE_OFFLINE);
		mContentResolver.update(RosterProvider.CONTENT_URI, values, null, null);
	};

	private void registerRosterListener() {
		// flush roster on connecting.
		mContentResolver.delete(RosterProvider.CONTENT_URI, "", null);
		mRoster = mXMPPConnection.getRoster();

		mRoster.addRosterListener(new RosterListener() {

			public void entriesAdded(Collection<String> entries) {
				debugLog("entriesAdded(" + entries + ")");

				for (String entry : entries) {
					RosterEntry rosterEntry = mRoster.getEntry(entry);
					setRosterEntry(rosterEntry);
					addRosterEntryToDB(rosterEntry);
				}
				mServiceCallBack.rosterChanged();
			}

			public void entriesDeleted(Collection<String> entries) {
				debugLog("entriesDeleted(" + entries + ")");

				for (String entry : entries) {
					RosterEntry rosterEntry = mRoster.getEntry(entry);
					unSetRosterEntry(rosterEntry);
					deleteRosterEntryFromDB(rosterEntry);
				}
				mServiceCallBack.rosterChanged();
			}

			public void entriesUpdated(Collection<String> entries) {
				debugLog("entriesUpdated(" + entries + ")");

				for (String entry : entries) {
					RosterEntry rosterEntry = mRoster.getEntry(entry);
					unSetRosterEntry(rosterEntry);
					setRosterEntry(rosterEntry);
					updateRosterEntryInDB(rosterEntry);
				}
				mServiceCallBack.rosterChanged();
			}

			public void presenceChanged(Presence presence) {
				debugLog("presenceChanged(" + presence.getFrom() + ")");

				String jabberID = getJabberID(presence.getFrom());
				RosterEntry rosterEntry = mRoster.getEntry(jabberID);
				setRosterEntry(rosterEntry);
				mServiceCallBack.rosterChanged();
				updateOrInsertRosterEntryToDB(rosterEntry);
			}
		});
	}

	private String getJabberID(String from) {
		String[] res = from.split("/");
		return res[0].toLowerCase();
	}

	private void registerMessageListener() {
		PacketTypeFilter filter = new PacketTypeFilter(Message.class);

		PacketListener listener = new PacketListener() {

			public void processPacket(Packet packet) {
				mWakeLock.acquire();
				debugLog("processPacket: " + packet);
				if (packet instanceof Message) {
					Message msg = (Message) packet;
					String chatMessage = msg.getBody();

					if (chatMessage == null) {
						return;
					}

					String fromJID = getJabberID(msg.getFrom());
					String toJID = getJabberID(msg.getTo());

					addChatMessageToDB(ChatConstants.INCOMING, fromJID, chatMessage, ChatConstants.UNREAD);
					mServiceCallBack.newMessage(fromJID, chatMessage);
				}
				mWakeLock.release();
			}
		};

		mXMPPConnection.addPacketListener(listener, filter);
	}

	private void addChatMessageToDB(boolean from_me, String JID,
			String message, boolean read) {
		ContentValues values = new ContentValues();

		values.put(ChatConstants.FROM_ME, from_me);
		values.put(ChatConstants.JID, JID);
		values.put(ChatConstants.MESSAGE, message);
		values.put(ChatConstants.HAS_BEEN_READ, read);
		values.put(ChatConstants.DATE, System.currentTimeMillis());

		mContentResolver.insert(ChatProvider.CONTENT_URI, values);
	}

	private void addRosterEntryToDB(final RosterEntry entry) {
		final ContentValues values = getContentValuesForRosterEntry(entry);

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

	private void deleteRosterEntryFromDB(final RosterEntry entry) {
		int count = mContentResolver.delete(RosterProvider.CONTENT_URI,
				RosterConstants.JID + " = ?", new String[] { entry.getUser() });
		debugLog("deleteRosterEntryFromDB: Deleted " + count + " entries");
	}

	private void updateRosterEntryInDB(final RosterEntry entry) {
		final ContentValues values = getContentValuesForRosterEntry(entry);

		mContentResolver.update(RosterProvider.CONTENT_URI, values,
				RosterConstants.JID + " = ?", new String[] { entry.getUser() });
	}

	private void updateOrInsertRosterEntryToDB(final RosterEntry entry) {
		try {
			deleteRosterEntryFromDB(entry);
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
		if (presence.getType() == Presence.Type.available) {
			final Mode mode = presence.getMode();
			if (mode != null) {
				switch (mode) {
				case chat:
					return StatusModeInt.MODE_CHAT;
				case away:
					return StatusModeInt.MODE_AWAY;
				case xa:
					return StatusModeInt.MODE_XA;
				case dnd:
					return StatusModeInt.MODE_DND;
				}
			}
			return StatusModeInt.MODE_AVAILABLE;
		}
		return StatusModeInt.MODE_OFFLINE;
	}

	private void debugLog(String data) {
		if (LogConstants.LOG_DEBUG) {
			Log.d(TAG, data);
		}
	}
}
