package org.yaxim.androidclient.service;

import java.util.ArrayDeque;

import android.content.ContentResolver;
import android.content.ContentUris;
import android.database.Cursor;
import android.net.Uri;

import org.jivesoftware.smack.XMPPConnection;
import org.jivesoftware.smackx.muc.MultiUserChat;

import org.yaxim.androidclient.data.ChatProvider;
import org.yaxim.androidclient.data.ChatProvider.ChatConstants;

/* The MUCController implements common MUC related management tasks.
 *
 * Tasks are:
 *  - keep track of recent message `_id`s (because SQLite lookup is SLOW)
 *  - keep track of ping/pong
 *  - keep track of joining/leaving
 */
public class MUCController {
	final String jid;
	public MultiUserChat muc;

	static final int LOOKUP_SIZE = 50;
	private ArrayDeque lastIDs = new ArrayDeque<Long>(LOOKUP_SIZE);
	long lastPong = -1;

	MUCController(XMPPConnection c, String jid) {
		this.jid = jid;
		muc = new MultiUserChat(c, jid);
	}

	public synchronized void addPacketID(long id) {
		while (lastIDs.size() >= LOOKUP_SIZE)
			lastIDs.poll();
		android.util.Log.d("MUCController", jid + " -> " + id);
		lastIDs.addLast(id);
		lastPong = System.currentTimeMillis();
	}
	public synchronized void addPacketID(Uri contentUri) {
		addPacketID(ContentUris.parseId(contentUri));
	}

	public synchronized long getFirstPacketID() {
		Long id = (Long)lastIDs.peek();
		android.util.Log.d("MUCController", jid + " <- " + id);
		return (id != null) ? id : 0;
	}

	public synchronized void loadPacketIDs(ContentResolver cr) {
		lastIDs.clear();
		Cursor c = cr.query(ChatProvider.CONTENT_URI, new String[] { ChatConstants._ID },
				"jid = ?", new String[] { jid }, "_id DESC LIMIT " + LOOKUP_SIZE);
		long result = -1;
		while (c.moveToNext()) {
			android.util.Log.d("MUCController", jid + " -> " + c.getLong(0));
			lastIDs.addFirst(c.getLong(0));
		}
		c.close();
	}

	public void cleanup() {
		muc.cleanup();
	}
}
