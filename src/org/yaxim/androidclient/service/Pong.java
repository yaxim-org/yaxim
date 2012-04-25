package org.yaxim.androidclient.service;

import org.jivesoftware.smack.packet.IQ;

public class Pong extends IQ {

	public Pong(Ping ping) {
		this.setType(Type.RESULT);
		this.setTo(ping.getFrom());
		this.setPacketID(ping.getPacketID());
	}
	
	@Override
	public String getChildElementXML() {
		return null;
	}

}
