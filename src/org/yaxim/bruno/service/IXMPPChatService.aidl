package org.yaxim.bruno.service;

interface IXMPPChatService {
	void sendMessage(String user, String message, String lmc, long _id);
	boolean isAuthenticated();
	void clearNotifications(String Jid);
	void sendFile(in Uri path, String user, int flags);
}