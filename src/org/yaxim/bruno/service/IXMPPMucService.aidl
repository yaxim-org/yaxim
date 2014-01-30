package org.yaxim.bruno.service;

import org.yaxim.bruno.service.ParcelablePresence;

interface IXMPPMucService {
	void syncDbRooms();
	void sendMessage(String room, String message);	
	boolean inviteToRoom(String contactJid, String roomJid);
	String getMyMucNick(String jid);
	List<ParcelablePresence> getUserList(String jid);
	
	// TODO: private chat in a room
	//RoomInfo getRoomInfo(String room); TODO: make RoomInfo "parcelable"??
	// TODO: manage roles
	// TODO: manage subjects
	// TODO: manage affiliations
}
