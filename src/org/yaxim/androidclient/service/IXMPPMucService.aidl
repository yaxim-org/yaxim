package org.yaxim.androidclient.service;

interface IXMPPMucService {
	void syncDbRooms();
	boolean addRoom(String jid, String password, String nickname);
	boolean removeRoom(String jid);
	boolean createAndJoinRoom(String jid, String password, String nickname);
	void sendMessage(String room, String message);	
	String[] getRooms();
	boolean isRoom(String jid);
	boolean inviteToRoom(String contactJid, String roomJid);
	String[] getUserList(String jid);
	
	// TODO: private chat in a room
	//RoomInfo getRoomInfo(String room); TODO: make RoomInfo "parcelable"??
	// TODO: manage roles
	// TODO: manage subjects
	// TODO: manage affiliations
}