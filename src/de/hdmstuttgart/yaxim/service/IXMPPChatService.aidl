package de.hdmstuttgart.yaxim.service;

import de.hdmstuttgart.yaxim.chat.IXMPPChatCallback;

interface IXMPPChatService {
	void sendMessage(String user, String message);
	List<String> pullMessagesForContact(String jabberID);

	void registerChatCallback(IXMPPChatCallback callback, String jabberID);
	void unregisterChatCallback(IXMPPChatCallback callback, String jabberID);
	boolean isAuthenticated();
}