package org.yaxim.androidclient.service;

import org.yaxim.androidclient.service.ParcelablePresence;

interface IXMPPMucService {
	void syncDbRooms();
	void sendMessage(String room, String message);	
	boolean inviteToRoom(String contactJid, String roomJid);
	String getMyMucNick(String jid);
	List<ParcelablePresence> getUserList(String jid);
	void sendFile(in Uri path, String user, String message);
	
	// TODO: private chat in a room
	//RoomInfo getRoomInfo(String room); TODO: make RoomInfo "parcelable"??
	// TODO: manage roles
	// TODO: manage subjects
	// TODO: manage affiliations
}
