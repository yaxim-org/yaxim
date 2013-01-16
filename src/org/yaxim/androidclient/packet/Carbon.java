/**
 * Copyright 2013 Georg Lukas
 *
 * All rights reserved. Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.yaxim.androidclient.packet;

import org.jivesoftware.smack.packet.Packet;
import org.jivesoftware.smack.packet.PacketExtension;
import org.jivesoftware.smack.provider.PacketExtensionProvider;
import org.jivesoftware.smack.util.PacketParserUtils;
import org.jivesoftware.smackx.packet.DelayInfo;
import org.jivesoftware.smackx.provider.DelayInfoProvider;
import org.xmlpull.v1.XmlPullParser;

public class Carbon implements PacketExtension {
    public static final String NAMESPACE = "urn:xmpp:carbons:2";

    Direction dir;
    Forwarded fwd;

    public Carbon() {
    }

    public Carbon(Direction dir, Forwarded fwd) {
        this.dir = dir;
        this.fwd = fwd;
    }

    @Override
    public String getElementName() {
	return dir.toString();
    }

    @Override
    public String getNamespace() {
	return NAMESPACE;
    }

    @Override
    public String toXML() {
	StringBuilder buf = new StringBuilder();
	buf.append("<").append(getElementName()).append(" xmlns=\"")
		.append(getNamespace()).append("\">");

	buf.append(fwd.toXML());

	buf.append("</").append(getElementName()).append(">");
	return buf.toString();
    }

    public Forwarded getForwarded() {
	return fwd;
    }

    public static enum Direction {
	received,
	sent
    }

    public static class Provider implements PacketExtensionProvider {

	public PacketExtension parseExtension(XmlPullParser parser) throws Exception {
	    Direction dir = Direction.valueOf(parser.getName());
	    Forwarded fwd;
	    android.util.Log.d("Carbon", "at the beginning: " + parser.getName());
	    parser.next();
	    android.util.Log.d("Carbon", "after first: " + parser.getName());
	    if (parser.getName().equals("forwarded"))
		fwd = (Forwarded)new Forwarded.Provider().parseExtension(parser);
	    else throw new Exception("sent/received must contain exactly one <forwarded> tag");
	    int type = parser.next();
	    android.util.Log.d("Carbon", "at the end: " + parser.getName() + " t="+type);
	    return new Carbon(dir, fwd);
	}
    }
}
