package org.yaxim.bruno.service;

interface IXMPPChatService {
	void sendMessage(String user, String message);
	boolean isAuthenticated();
	void clearNotifications(String Jid);
}