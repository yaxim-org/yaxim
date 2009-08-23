package de.hdmstuttgart.yaxim.chat;

interface IXMPPChatCallback {
		void newMessage(String from, String message);
}