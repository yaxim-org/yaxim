package org.yaxim.androidclient.service;

import java.util.List;

import org.yaxim.androidclient.exceptions.YaximXMPPException;
import org.yaxim.androidclient.util.ConnectionState;


public interface Smackable {
	boolean doConnect(boolean create_account) throws YaximXMPPException;
	boolean isAuthenticated();
	void requestConnectionState(ConnectionState new_state);
	void requestConnectionState(ConnectionState new_state, boolean create_account);
	ConnectionState getConnectionState();
	String getLastError();

	void addRosterItem(String user, String alias, String group) throws YaximXMPPException;
	void removeRosterItem(String user) throws YaximXMPPException;
	void renameRosterItem(String user, String newName) throws YaximXMPPException;
	void moveRosterItemToGroup(String user, String group) throws YaximXMPPException;
	void renameRosterGroup(String group, String newGroup);
	void sendPresenceRequest(String user, String type);
	void addRosterGroup(String group);
	String changePassword(String newPassword);
	
	void setStatusFromConfig();
	void sendMessage(String user, String message);
	void sendServerPing();
	void setUserWatching(boolean user_watching);
	
	void registerCallback(XMPPServiceCallback callBack);
	void unRegisterCallback();
	
	void sendMucMessage(String room, String message);
	void syncDbRooms();
	boolean inviteToRoom(String contactJid, String roomJid);
	
	String getNameForJID(String jid);
	List<ParcelablePresence> getUserList(String jid);

}
