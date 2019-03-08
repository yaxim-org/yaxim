package org.yaxim.androidclient.list;

import android.os.AsyncTask;
import android.text.TextUtils;
import android.util.Log;

import org.jivesoftware.smack.XMPPConnection;
import org.jivesoftware.smack.XMPPException;
import org.jivesoftware.smack.packet.IQ;
import org.jivesoftware.smackx.bookmarks.BookmarkManager;
import org.jivesoftware.smackx.bookmarks.BookmarkedConference;
import org.jivesoftware.smackx.disco.ServiceDiscoveryManager;
import org.jivesoftware.smackx.disco.packet.DiscoverInfo;
import org.jivesoftware.smackx.disco.packet.DiscoverItems;
import org.jivesoftware.smackx.muc.HostedRoom;
import org.jivesoftware.smackx.muc.MultiUserChatManager;
import org.jxmpp.jid.DomainBareJid;
import org.jxmpp.jid.EntityBareJid;
import org.jxmpp.jid.impl.JidCreate;
import org.yaxim.androidclient.YaximApplication;
import org.yaxim.androidclient.data.EntityInfo;
import org.yaxim.androidclient.data.EntityList;
import org.yaxim.androidclient.packet.MuclumbusIQ;
import org.yaxim.androidclient.packet.MuclumbusResult;
import org.yaxim.androidclient.util.StatusMode;
import org.yaxim.androidclient.util.XMPPHelper;

import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * AsyncTask for loading a list of entities from the XMPP connection.
 *
 * TODO: refactor this into an abstract base class and sub-class for each use case.
 */
class EntityListLoader extends AsyncTask<String, EntityInfo, Throwable> {
	public static final int MODE_BOOKMARKS = 0;
	public static final int MODE_DOMAIN = 1;
	public static final int MODE_MUCLUMBUS = 2;
	int mode;
	private EntityList el;
	String search;

	public EntityListLoader(int mode, EntityList el) {
		this.mode = mode;
		this.el = el;
		el.startLoading();
	}

	void loadBookmarksOrThrow(XMPPConnection c) throws Exception {
		List<BookmarkedConference> list = BookmarkManager.getBookmarkManager(c).getBookmarkedConferences();
		if (isCancelled())
			return;
		for (BookmarkedConference conf : list) {
			StatusMode sm = conf.isAutoJoin() ? StatusMode.available : StatusMode.offline;
			String name = conf.getName();
			if (TextUtils.isEmpty(name))
				name = XMPPHelper.capitalizeString(conf.getJid().getLocalpart().toString());
			publishProgress(new EntityInfo(EnumSet.of(EntityInfo.Type.MUC, EntityInfo.Type.Known), conf.getJid().toString(), sm, 0, name, conf.getJid().toString(), 0, conf));
		}

	}

	private String langToDisplay(String lang) {
		if (TextUtils.isEmpty(lang))
			return null;
		Locale l;
		String[] tagsoup = lang.split("-");
		if (tagsoup.length >= 2)
			l = new Locale(tagsoup[0], tagsoup[1]);
		else if (tagsoup.length == 1)
			l = new Locale(tagsoup[0]);
		else
			return null;
		if (l != null && l.getDisplayLanguage() != null)
			return l.getDisplayLanguage();
		return null;
	}

	void loadMyMUCs(XMPPConnection c) throws Exception {
		MultiUserChatManager mucmgr = MultiUserChatManager.getInstanceFor(c);
		//muc_domains.add(JidCreate.domainBareFrom("chat.yax.im"));
		List<DomainBareJid> muc_domains = mucmgr.getMucServiceDomains();
		for (DomainBareJid jid : muc_domains) {
			if (isCancelled())
				return;
			Map<EntityBareJid, HostedRoom> mucs = mucmgr.getRoomsHostedBy(jid);
			for (HostedRoom muc : mucs.values()) {
				StatusMode sm = StatusMode.offline;
				String name = muc.getName();
				if (TextUtils.isEmpty(name))
					name = XMPPHelper.capitalizeString(muc.getJid().getLocalpart().toString());
				publishProgress(new EntityInfo(EnumSet.of(EntityInfo.Type.MUC), muc.getJid().toString(), sm, 0, name, muc.getJid().toString(), 0, muc));
			}

		}
	}

	void loadServerList(XMPPConnection c, String muc_domains[]) throws Exception {
		ServiceDiscoveryManager sdm = ServiceDiscoveryManager.getInstanceFor(c);
		publishProgress(null);
		for (String jid : muc_domains) {
			if (isCancelled())
				return;
			DiscoverItems items = sdm.discoverItems(JidCreate.from(jid));
			for (DiscoverItems.Item item : items.getItems()) {
				StatusMode sm = StatusMode.available;
				String name = item.getName();
				if (TextUtils.isEmpty(name)) {
					name = item.getEntityID().toString();
				}
				publishProgress(new EntityInfo(EnumSet.of(EntityInfo.Type.Domain), item.getEntityID().toString(), sm, 0, name, "", 0, item));
			}
			// second round: disco#info them all!
			for (DiscoverItems.Item item : items.getItems()) {
				if (isCancelled())
					return;
				try {
					DiscoverInfo di = sdm.discoverInfo(item.getEntityID());
					publishProgress(EntityInfo.fromDisco(item.getEntityID().toString(), di));
				} catch (Exception e) {
					// we can ignore this
					e.printStackTrace();
				}
			}
			if (isCancelled())
				return;

		}
	}

	void loadMUClumbus(XMPPConnection c, String query) throws Exception {
		IQ search = MuclumbusIQ.searchFor(query);
		//search.setTo(JidCreate.from("rodrigo.de.mucobedo@dreckshal.de"));
		search.setTo(JidCreate.from("muc@yax.im/bot"));
		IQ result = c.sendIqRequestAndWaitForResponse(search);
		Log.d("MUClumbus", result.toString());
		if (result.getType() == IQ.Type.error)
			throw new XMPPException.XMPPErrorException(result, result.getError());
		else if (result instanceof MuclumbusResult) {
			MuclumbusResult r = (MuclumbusResult) result;
			publishProgress(null);
			for (MuclumbusResult.Item muc : r.getItems()) {
				StatusMode sm = muc.is_open ? StatusMode.available : StatusMode.dnd;
				String desc = muc.description;
				String lang = langToDisplay(muc.language);
				if (lang != null) {
					muc.name = muc.name + " (" + lang + ")";
				}
				publishProgress(new EntityInfo(EnumSet.of(EntityInfo.Type.MUC, EntityInfo.Type.SearchResult),
						muc.address.toString(), sm, 0, muc.name, desc, muc.nusers, muc));
			}

		}
	}

	/** run a query on the background thread.
	 *
	 * @param query search query to perform (domain JID or MUClumbus search query)
	 * @return an error if something went wrong or null
	 */
	@Override
	protected Throwable doInBackground(String... query) {
		if (query.length > 0)
			search = query[0];
		XMPPConnection c = YaximApplication.getInstance().getSmackable().getConnection();
		try {
			switch (mode) {
				case MODE_BOOKMARKS:
					loadBookmarksOrThrow(c);
					break;
				case MODE_DOMAIN:
					loadServerList(c, query);
					break;
				case MODE_MUCLUMBUS:
					loadMUClumbus(c, query[0]);
					break;
			}
		} catch (Exception e) {
			return e;
		}
		return null;
	}

	/** foreground sibling of publishProgress.
	 *
	 * @param ei entity to add to the list
	 *    Semantics of the parameter:
	 *      - no parameter - empty the underlying list, e.g. when we want to re-populate
	 *      - null parameter - ignore
	 *      - EntityInfo parameter - add/update ei to the underlying list and notify
	 */
	@Override
	protected void onProgressUpdate(EntityInfo... ei) {
		if (ei != null && ei.length > 0) {
			// EntityInfo.fromDisco() will return null sometimes
			if (ei[0] == null)
				return;
			el.add(ei[0], true);
		} else
			el.clear();
	}

	@Override
	protected void onPostExecute(Throwable ex) {
		if (ex != null) {
			el.setError(ex);
			return;
		}
		el.finishLoading();
	}

}
