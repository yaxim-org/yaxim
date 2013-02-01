package org.yaxim.androidclient.service;

interface IXMPPMucService {
	void mucTest();
	boolean joinRoom(String room, String nickname, String password, int historyLen);
	boolean createRoom(String room, String nickname, String password);
	void quitRoom(String room);
	String[] getJoinedRooms();
	void sendMessage(String room, String message);	
	// TODO: invite
	// TODO: private chat in a room
	//RoomInfo getRoomInfo(String room); TODO: make RoomInfo "parcelable"??
	// TODO: manage roles
	// TODO: manage subjects
	// TODO: manage affiliations
}