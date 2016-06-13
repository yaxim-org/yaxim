package org.yaxim.androidclient.packet;

import org.jivesoftware.smack.packet.PacketExtension;
import org.jivesoftware.smack.provider.PacketExtensionProvider;

import org.xmlpull.v1.XmlPullParser;

public class PreAuth implements PacketExtension {
	public final static String NAMESPACE = "urn:xmpp:pars:0";
	public final static String ELEMENT = "preauth";
	private String token;

	public PreAuth(String token) {
		this.token = token;
	}

	public String getElementName() {
		return ELEMENT;
	}

	public String getNamespace() {
		return NAMESPACE;
	}

	public String getToken() {
		return token;
	}

	public String toXML() {
		return "<" + getElementName() + " token=\"" + getToken() + "\" xmlns=\"" + getNamespace() + "\" />";
	}

	public static class Provider implements PacketExtensionProvider {
		public PacketExtension parseExtension(XmlPullParser parser) throws Exception {
			return new PreAuth(parser.getAttributeValue(null, "token"));
		}
	}
}
