package org.yaxim.androidclient.service;

import android.content.ContentResolver;
import android.content.ContentUris;
import android.database.Cursor;
import android.net.Uri;

import android.support.v4.util.CircularArray;
import android.util.Log;

import org.jivesoftware.smack.XMPPConnection;
import org.jivesoftware.smackx.muc.MultiUserChat;

import org.jivesoftware.smackx.muc.MultiUserChatManager;
import org.jivesoftware.smackx.muc.RoomInfo;
import org.jxmpp.jid.impl.JidCreate;
import org.jxmpp.stringprep.XmppStringprepException;
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
	static final String TAG = "yaxim.MUCController";
	final String jid;
	public MultiUserChat muc;
	boolean isSynchronized = false;
	boolean isTimeout = false;


	public static final int LOOKUP_SIZE = 250;
	private CircularArray lastIDs = new CircularArray<Long>(LOOKUP_SIZE);
	long lastPong = -1;

	MUCController(XMPPConnection c, String jid) {
		this.jid = jid;
		muc = MultiUserChatManager.getInstanceFor(c).getMultiUserChat(JidCreate.entityBareFromUnescapedOrThrowUnchecked(jid));
	}

	public synchronized void setLastActivity() {
		lastPong = System.currentTimeMillis();
	}
	public synchronized void addPacketID(long id) {
		while (lastIDs.size() >= LOOKUP_SIZE)
			lastIDs.popFirst();
		//Log.d(TAG, jid + " -> " + id);
		lastIDs.addLast(id);
		setLastActivity();
	}
	public synchronized void addPacketID(Uri contentUri) {
		addPacketID(ContentUris.parseId(contentUri));
	}

	public synchronized long getFirstPacketID() {
		if (lastIDs.isEmpty())
			return 0;
		long id = (Long)lastIDs.getFirst();
		Log.d(TAG, jid + " <- " + id);
		return id;
	}

	public synchronized void loadPacketIDs(ContentResolver cr) {
		lastIDs.clear();
		Cursor c = cr.query(ChatProvider.CONTENT_URI, new String[] { ChatConstants._ID /*, ChatConstants.PACKET_ID*/ },
				"jid = ?", new String[] { jid }, "_id DESC LIMIT " + LOOKUP_SIZE);
		long result = -1;
		while (c.moveToNext()) {
			//Log.d(TAG, jid + ": " + c.getString(1) +  " -> " + c.getLong(0));
			lastIDs.addFirst(c.getLong(0));
		}
		c.close();
	}

	public void cleanup() {
		//SMAXX muc.cleanup();
		isSynchronized = false;
		isTimeout = false;
	}
}
