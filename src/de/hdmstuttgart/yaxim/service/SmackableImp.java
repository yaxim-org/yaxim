package de.hdmstuttgart.yaxim.service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.regex.Pattern;

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

import de.hdmstuttgart.yaxim.data.RosterItem;
import de.hdmstuttgart.yaxim.exceptions.YaximXMPPException;
import de.hdmstuttgart.yaxim.util.AdapterConstants;
import de.hdmstuttgart.yaxim.util.StatusMode;

public class SmackableImp implements Smackable {

	final static private int PACKET_TIMEOUT = 12000;

	final private String jabServer;
	final private String jabUserName;
	final private String jabPassword;
	final private String jabRessource;
	final private int jabPort;
	final private int jabPriority;

	private ConnectionConfiguration config;
	private XMPPConnection conn;
	private XMPPServiceCallback callBack;
	private Roster roster;

	private ConcurrentHashMap<String, ConcurrentHashMap<String, RosterItem>> rosterItemsByGroup;
	private HashMap<String, ArrayList<String>> incomingMessageQueue;

	public SmackableImp(String jabServer, String jabUserName,
			String jabPassword, String jabRessource, int jabPort,
			int jabPriority) {

		rosterItemsByGroup = new ConcurrentHashMap<String, ConcurrentHashMap<String, RosterItem>>();
		incomingMessageQueue = new HashMap<String, ArrayList<String>>();

		this.jabServer = jabServer;
		this.jabUserName = jabUserName;
		this.jabPassword = jabPassword;
		this.jabRessource = jabRessource;
		this.jabPort = jabPort;
		this.jabPriority = validatePriority(jabPriority);
		createConnection();
	}

	private int validatePriority(int jabPriority) {
		if (jabPriority > 127)
			return 127;
		else if (jabPriority < -127)
			return -127;
		return jabPriority;
	}

	public boolean doConnect() throws YaximXMPPException {
		if (!conn.isConnected()) {

			tryToConnect();

			if (isAuthenticated()) {
				registerMessageHandler();
				registerRosterHandler();
				Presence presence = new Presence(Presence.Type.available);
				presence.setPriority(jabPriority);
				conn.sendPacket(presence);
				setRosterEntries();
			}
		}
		return (conn.isConnected() && conn.isAuthenticated());
	}

	public void addRosterItem(String user, String alias, String group)
			throws YaximXMPPException {
		tryToAddRosterEntry(user, alias, group);
	}

	public void removeRosterItem(String user) throws YaximXMPPException {
		tryToRemoveRosterEntry(user);
		callBack.rosterChanged();
	}

	public void renameRosterItem(String user, String newName)
			throws YaximXMPPException {
		roster = conn.getRoster();
		RosterEntry rosterEntry = roster.getEntry(user);

		if (!(newName.length() > 0) || (rosterEntry == null)) {
			throw new YaximXMPPException("JabberID to rename is invalid!");
		}
		rosterEntry.setName(newName);
	}

	public void addRosterGroup(String group) {
		roster = conn.getRoster();
		roster.createGroup(group);
	}

	public void renameRosterGroup(String group, String newGroup) {
		roster = conn.getRoster();
		RosterGroup groupToRename = roster.getGroup(group);
		rosterItemsByGroup.remove(group);
		groupToRename.setName(newGroup);
	}

	public void moveRosterItemToGroup(String user, String group)
			throws YaximXMPPException {
		tryToMoveRosterEntryToGroup(user, group);
	}

	private ArrayList<String> getMessageQueueForContact(String jabberID) {
		if (!incomingMessageQueue.containsKey(jabberID)) {
			ArrayList<String> queue = new ArrayList<String>();
			incomingMessageQueue.put(jabberID, queue);
			return queue;
		}
		return incomingMessageQueue.get(jabberID);
	}

	public ArrayList<String> pullMessagesForContact(String jabberID) {
		ArrayList<String> queue = getMessageQueueForContact(jabberID);
		incomingMessageQueue.remove(jabberID);
		return queue;
	}

	private void tryToConnect() throws YaximXMPPException {
		try {
			SmackConfiguration.setPacketReplyTimeout(PACKET_TIMEOUT);
			conn.connect();
			conn.login(jabUserName, jabPassword, jabRessource);
		} catch (XMPPException e) {
			throw new YaximXMPPException(e.getMessage());
		}
	}

	private void tryToMoveRosterEntryToGroup(String user, String group)
			throws YaximXMPPException {

		if (!(group.length() > 0)) {
			throw new YaximXMPPException("Can't move " + user
					+ " to a group without a name!");
		}

		roster = conn.getRoster();
		RosterGroup rosterGroup = roster.getGroup(group);

		if (!(group.equals(AdapterConstants.EMPTY_GROUP))) {
			if (rosterGroup == null)
				rosterGroup = roster.createGroup(group);
		}

		RosterEntry rosterEntry = roster.getEntry(user);
		unSetRosterEntry(rosterEntry);
		removeRosterEntryFromGroups(rosterEntry);

		if (group.equals(AdapterConstants.EMPTY_GROUP))
			return;
		else {
			try {
				rosterGroup.addEntry(rosterEntry);
			} catch (XMPPException e) {
				throw new YaximXMPPException(e.getMessage());
			}
		}
	}

	private void removeRosterEntryFromGroups(RosterEntry rosterEntry)
			throws YaximXMPPException {
		Collection<RosterGroup> oldGroups = rosterEntry.getGroups();
		for (RosterGroup group : oldGroups) {
			tryToRemoveUserFromGroup(group, rosterEntry);
			if (group.getEntries().size() < 1)
				rosterItemsByGroup.remove(group.getName());
		}
	}

	private void tryToRemoveUserFromGroup(RosterGroup group,
			RosterEntry rosterEntry) throws YaximXMPPException {
		try {
			group.removeEntry(rosterEntry);
		} catch (XMPPException e) {
			throw new YaximXMPPException(e.getMessage());
		}
	}

	private void tryToRemoveRosterEntry(String user) throws YaximXMPPException {
		roster = conn.getRoster();
		try {
			RosterEntry rosterEntry = roster.getEntry(user);
			unSetRosterEntry(rosterEntry);
			roster.removeEntry(rosterEntry);
		} catch (XMPPException e) {
			throw new YaximXMPPException(e.getMessage());
		}
	}

	private void tryToAddRosterEntry(String user, String alias, String group)
			throws YaximXMPPException {
		roster = conn.getRoster();
		try {
			roster.createEntry(user, alias, new String[] { group });
		} catch (XMPPException e) {
			throw new YaximXMPPException(e.getMessage());
		}
	}

	private void setRosterEntries() {
		roster = conn.getRoster();
		Collection<RosterEntry> rosterEntries = roster.getEntries();
		for (RosterEntry rosterEntry : rosterEntries) {
			setRosterEntry(rosterEntry);
		}
	}

	private void setRosterEntry(RosterEntry rosterEntry) {
		String groupName = getGroup(rosterEntry.getGroups());
		ConcurrentMap<String, RosterItem> entryMap = getEntryMapForGroup(groupName);
		RosterItem rosterItem = getRosterItemForRosterEntry(rosterEntry);
		entryMap.put(rosterItem.jabberID, rosterItem);
	}

	private void unSetRosterEntry(RosterEntry rosterEntry) {
		String groupName = getGroup(rosterEntry.getGroups());
		ConcurrentMap<String, RosterItem> entryMap = getEntryMapForGroup(groupName);
		String jabberID = rosterEntry.getUser();
		if (entryMap.containsKey(jabberID))
			entryMap.remove(jabberID);
		if (entryMap.size() < 1)
			rosterItemsByGroup.remove(groupName);
	}

	private ConcurrentMap<String, RosterItem> getEntryMapForGroup(
			String groupName) {
		ConcurrentHashMap<String, RosterItem> tmpItemList;
		if (rosterItemsByGroup.containsKey(groupName))
			return rosterItemsByGroup.get(groupName);
		else {
			tmpItemList = new ConcurrentHashMap<String, RosterItem>();
			rosterItemsByGroup.put(groupName, tmpItemList);
		}
		return tmpItemList;
	}

	public ArrayList<RosterItem> getRosterEntriesByGroup(String group) {
		ArrayList<RosterItem> rosterItems = new ArrayList<RosterItem>(
				rosterItemsByGroup.get(group).values());
		Collections.sort(rosterItems);
		return rosterItems;
	}

	private RosterItem getRosterItemForRosterEntry(RosterEntry entry) {
		String jabberID = entry.getUser();
		String userName = getName(entry);
		Presence userPresence = roster.getPresence(entry.getUser());
		StatusMode userStatus = getStatus(userPresence);
		String userStatusMessage = userPresence.getStatus();
		String userGroups = getGroup(entry.getGroups());

		return new RosterItem(jabberID, userName, userStatus,
				userStatusMessage, userGroups);
	}

	public ArrayList<String> getRosterGroups() {
		ArrayList<String> rosterGroups = new ArrayList<String>(
				rosterItemsByGroup.keySet());
		Collections.sort(rosterGroups, new Comparator<String>() {
			public int compare(String object1, String object2) {
				if (object1.equals(AdapterConstants.EMPTY_GROUP))
					return -1;
				return object1.toLowerCase().compareTo(object2.toLowerCase());
			}
		});
		return rosterGroups;
	}

	public void setStatus(StatusMode status, String statusMsg) {
		Presence presence = new Presence(Presence.Type.available);
		Mode mode = stringToMode(status.name());
		presence.setMode(mode);
		presence.setStatus(statusMsg);
		conn.sendPacket(presence);
	}

	private Mode stringToMode(String modeStr) {
		return Mode.valueOf(modeStr);
	}

	public void sendMessage(String user, String message) {
		final Message newMessage = new Message(user, Message.Type.chat);
		newMessage.setBody(message);
		if (isAuthenticated()) {
			conn.sendPacket(newMessage);
		}
	}

	public boolean isAuthenticated() {
		if (conn != null) {
			return conn.isAuthenticated();
		}
		return false;
	}

	public void registerCallback(XMPPServiceCallback callBack) {
		this.callBack = callBack;
	}

	public void unRegisterCallback() {
		conn.disconnect();
		rosterItemsByGroup.clear();
		this.callBack = null;
	}

	private void createConnection() {
		config = new ConnectionConfiguration(jabServer, jabPort);
		config.setReconnectionAllowed(true);
		conn = new XMPPConnection(config);
	}

	private void registerRosterHandler() {
		roster = conn.getRoster();

		roster.addRosterListener(new RosterListener() {

			public void entriesAdded(Collection<String> entries) {
				for (String entry : entries) {
					RosterEntry rosterEntry = roster.getEntry(entry);
					setRosterEntry(rosterEntry);
				}
				callBack.rosterChanged();
			}

			public void entriesDeleted(Collection<String> entries) {
				for (String entry : entries) {
					RosterEntry rosterEntry = roster.getEntry(entry);
					unSetRosterEntry(rosterEntry);
				}
				callBack.rosterChanged();
			}

			public void entriesUpdated(Collection<String> entries) {
				for (String entry : entries) {
					RosterEntry rosterEntry = roster.getEntry(entry);
					unSetRosterEntry(rosterEntry);
					setRosterEntry(rosterEntry);
				}
				callBack.rosterChanged();
			}

			public void presenceChanged(Presence presence) {
				String jabberID = getJabberID(presence.getFrom());
				RosterEntry rosterEntry = roster.getEntry(jabberID);
				setRosterEntry(rosterEntry);
				callBack.rosterChanged();
			}
		});
	}

	private String getJabberID(String from) {
		Pattern p = Pattern.compile("\\/");
		String[] res = p.split(from);
		return res[0];
	}

	private void registerMessageHandler() {
		PacketTypeFilter filter = new PacketTypeFilter(Message.class);
		PacketListener myListener = new PacketListener() {

			public void processPacket(Packet arg0) {
				if (arg0 instanceof Message) {
					Message message = (Message) arg0;
					String msg = message.getBody();
					String jabberID = getJabberID(message.getFrom()).toLowerCase();

					if (!callBack.isBoundTo(jabberID)) {
						ArrayList<String> queue = getMessageQueueForContact(jabberID);
						queue.add(msg);
					}
					
					callBack.newMessage(jabberID, msg);
				}
			}
		};
		conn.addPacketListener(myListener, filter);
	}

	private String getGroup(Collection<RosterGroup> groups) {
		for (RosterGroup group : groups) {
			return group.getName();
		}
		return AdapterConstants.EMPTY_GROUP;
	}

	private String getName(RosterEntry rosterEntry) {
		String name = rosterEntry.getName();
		if (name != null) {
			return name;
		}
		return StringUtils.parseName(rosterEntry.getUser());
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
}
