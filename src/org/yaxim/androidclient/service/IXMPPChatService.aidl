package org.yaxim.androidclient.service;

interface IXMPPChatService {
	void sendMessage(String user, String message, String lmc, long _id);
	boolean isAuthenticated();
	void clearNotifications(String Jid);
	boolean hasFileUpload();
	void sendFile(in Uri path, String user, int flags);
}