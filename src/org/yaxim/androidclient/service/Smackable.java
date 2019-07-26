package org.yaxim.androidclient.service;

import java.util.List;

import org.jivesoftware.smack.XMPPConnection;
import org.yaxim.androidclient.exceptions.YaximXMPPException;
import org.yaxim.androidclient.util.ConnectionState;


public interface Smackable {
	boolean isAuthenticated();
	void requestConnectionState(ConnectionState new_state);
	void requestConnectionState(ConnectionState new_state, boolean create_account);
	ConnectionState getConnectionState();
	long getConnectionStateTimestamp();
	String getLastError();

	void addRosterItem(String user, String alias, String group, String token) throws YaximXMPPException;
	void removeRosterItem(String user) throws YaximXMPPException;
	void renameRosterItem(String user, String newName) throws YaximXMPPException;
	void moveRosterItemToGroup(String user, String group) throws YaximXMPPException;
	void renameRosterGroup(String group, String newGroup) throws YaximXMPPException;
	void sendPresenceRequest(String user, String type) throws YaximXMPPException;
	void addRosterGroup(String group);
	String changePassword(String newPassword);
	
	void setStatusFromConfig();
	void sendMessage(String user, String message, String lmc, String oob, long upsert_id);
	void sendServerPing();
	void setUserWatching(boolean user_watching);
	boolean hasFileUpload();

	void registerCallback(XMPPServiceCallback callBack);
	void unRegisterCallback();
	
	void syncDbRooms();
	boolean inviteToRoom(String contactJid, String roomJid);
	
	String getNameForJID(String jid);
	String getMyMucNick(String jid);

	XMPPConnection getConnection();

}
