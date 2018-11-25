package org.yaxim.androidclient.packet;

import org.jivesoftware.smack.packet.ExtensionElement;
import org.jivesoftware.smack.provider.ExtensionElementProvider;

import org.jivesoftware.smack.util.StringUtils;
import org.jivesoftware.smack.util.XmlStringBuilder;
import org.xmlpull.v1.XmlPullParser;

public class PreAuth implements ExtensionElement {
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

	@Override
	public CharSequence toXML(String enclosingNamespace) {
		XmlStringBuilder xml = new XmlStringBuilder(this);
		xml.attribute("token", token);
		xml.closeEmptyElement();
		return xml;
	}

	public static class Provider extends ExtensionElementProvider<PreAuth> {
		public PreAuth parse(XmlPullParser parser, int initialDepth) throws Exception {
			return new PreAuth(parser.getAttributeValue(null, "token"));
		}
	}
}
