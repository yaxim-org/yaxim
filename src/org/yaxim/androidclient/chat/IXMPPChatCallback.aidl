package org.yaxim.androidclient.chat;

interface IXMPPChatCallback {
		void newMessage(String from, String message);
}