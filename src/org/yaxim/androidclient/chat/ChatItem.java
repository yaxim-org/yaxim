package org.yaxim.androidclient.chat;


public class ChatItem {
	private String name;
	private String message;
	
	
	public ChatItem(String name, String message){
		this.name = name;
		this.message = message;
		
	}
	
	public String getName(){
		return (name);
	}
	
	public String getMessage(){
		return (message);
	}
}
