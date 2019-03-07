/**
 *
 * Copyright 2003-2007 Jive Software.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
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

import org.jivesoftware.smack.packet.IQ;
import org.jivesoftware.smack.packet.SimpleIQ;
import org.jivesoftware.smack.provider.IQProvider;

import org.jxmpp.jid.EntityBareJid;
import org.jxmpp.jid.impl.JidCreate;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Implements the protocol currently used to search MUCs on a MUClumbus bot.
 *
 * This part is the response IQ and parser.
 *
 * Documentation at https://search.jabber.network/docs/api
 *
 * @author Georg Lukas
 */
public class MuclumbusResult extends SimpleIQ {

    public static final String ELEMENT = "result";

    /**
     * List of search result items.
     */
    List<Item> items;

    /**
     * Creates a new IQ result with a list of search result items.
     */
    public MuclumbusResult(List<Item> items) {
        super(ELEMENT, MuclumbusIQ.NAMESPACE);
        this.items = items;
    }

    /**
     * Obtain the search results.
     *
     * @return the list of search results from this response
     */
    public List<Item> getItems() {
        return items;
    }

    public static class Item {
        public EntityBareJid address;
        public String name;
        public String description;
        public int nusers;
        public String language;
        public boolean is_open;
        public String anonymity_mode;
    }

    /** Parse an individual <item/> from the XML parser.
     *
     */
    protected static Item parseItem(XmlPullParser parser, EntityBareJid jid) throws XmlPullParserException, IOException {
        boolean done = false;
        Item item = new Item();
        item.address = jid;
        while (!done) {
            int eventType = parser.next();
            if ((eventType == XmlPullParser.START_TAG) && ("name".equals(parser.getName()))) {
                item.name = parser.nextText();
            }
            else if ((eventType == XmlPullParser.START_TAG) && ("description".equals(parser.getName()))) {
                item.description = parser.nextText();
            }
            else if ((eventType == XmlPullParser.START_TAG) && ("nusers".equals(parser.getName()))) {
                item.nusers = Integer.valueOf(parser.nextText()).intValue();
            }
            else if ((eventType == XmlPullParser.START_TAG) && ("language".equals(parser.getName()))) {
                item.language = parser.nextText();
            }
            else if ((eventType == XmlPullParser.START_TAG) && ("is-open".equals(parser.getName()))) {
                item.is_open = true;
            }
            else if ((eventType == XmlPullParser.START_TAG) && ("anonymity-mode".equals(parser.getName()))) {
                item.anonymity_mode = parser.nextText();
            }
            else if (eventType == XmlPullParser.END_TAG && "item".equals(parser.getName())) {
                done = true;
            }
        }
        return item;
	}

    /**
     * Result parsing Provider.
     */
    public static class Provider extends IQProvider<IQ> {
        @Override
        public IQ parse(XmlPullParser parser, int initialDepth) throws Exception {
            ArrayList<Item> items = new ArrayList<>();

            boolean done = false;

            while (!done) {
                int eventType = parser.next();
                if (eventType == XmlPullParser.START_TAG && parser.getName().equals("item")) {
                    EntityBareJid jid = JidCreate.entityBareFrom(parser.getAttributeValue("", "address"));
                    items.add(parseItem(parser, jid));
                }
                else if (eventType == XmlPullParser.END_TAG && parser.getName().equals(ELEMENT)) {
                    done = true;
                }
            }
            return new MuclumbusResult(items);
        }
    }

}
