package org.yaxim.androidclient.service;

interface IXMPPChatService {
	void sendMessage(String user, String message);
	List<String> pullMessagesForContact(String jabberID);

	boolean isAuthenticated();
}