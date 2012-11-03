package org.yaxim.bruno.service;

import org.jivesoftware.smack.packet.IQ;

public class Ping extends IQ {

	@Override
	public String getChildElementXML() {
		return "<ping xmlns='urn:xmpp:ping'/>";
	}

}
