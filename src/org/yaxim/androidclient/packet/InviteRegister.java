package org.yaxim.androidclient.packet;

import org.jivesoftware.smack.packet.ExtensionElement;
import org.jivesoftware.smack.provider.ExtensionElementProvider;
import org.jivesoftware.smack.util.XmlStringBuilder;
import org.xmlpull.v1.XmlPullParser;

public class InviteRegister implements ExtensionElement {
	public final static String NAMESPACE = "urn:xmpp:invite";
	public final static String ELEMENT = "register";
	@Override
	public String getNamespace() {
		return NAMESPACE;
	}

	@Override
	public String getElementName() {
		return ELEMENT;
	}

	@Override
	public CharSequence toXML(String enclosingNamespace) {
		XmlStringBuilder xml = new XmlStringBuilder(this);
		xml.closeEmptyElement();
		return xml;
	}

	public static class StreamFeatureProvider extends ExtensionElementProvider<ExtensionElement> {
		@Override
		public InviteRegister parse(XmlPullParser parser, int initialDepth) throws Exception {
			return new InviteRegister();
		}
	}
}
