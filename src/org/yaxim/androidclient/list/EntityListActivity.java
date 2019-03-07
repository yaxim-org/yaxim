package org.yaxim.androidclient.list;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.view.MenuItemCompat;
import android.support.v7.widget.SearchView;
import android.text.InputType;
import android.text.TextUtils;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ExpandableListView;

import org.jivesoftware.smackx.bookmarks.BookmarkedConference;
import org.yaxim.androidclient.R;
import org.yaxim.androidclient.YaximApplication;
import org.yaxim.androidclient.data.ChatHelper;
import org.yaxim.androidclient.data.EntityInfo;
import org.yaxim.androidclient.dialogs.EditMUCDialog;

/** Base activity for a tree of entities (e.g. Roster, Service Disco, ...).
 *
 * This activity provides:
 *   - a RAM-based EntityListAdapter
 *   - a search menu widget with 500ms delayed auto-search
 *   - default click handlers for known entity types
 *
 * If searchByDefault=true is set <b>befrore onCreate</b>, the
 * activity will be opened with search, and closed when search is closed.
 *
 */
public class EntityListActivity extends AppCompatActivity implements ExpandableListView.OnChildClickListener, SearchView.OnQueryTextListener {
	ExpandableListView elv;
	EntityListAdapter ela;
	protected SearchView sv;
	boolean searchByDefault = false;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_entitylist);

		// Search View
		final boolean sbd = searchByDefault;
		sv = new SearchView(getSupportActionBar().getThemedContext()) {
			@Override
			public void onActionViewCollapsed() {
				super.onActionViewCollapsed();
				if (sbd)
					finish();
			}
		};
		sv.setOnQueryTextListener(this);
		sv.setInputType(InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);

		elv = (ExpandableListView) findViewById(android.R.id.list);
		elv.setOnChildClickListener(this);
		ela = new EntityListAdapter(this);

	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		MenuItem si = menu.add(android.R.string.search_go);
		si.setIcon(android.R.drawable.ic_menu_search);
		MenuItemCompat.setActionView(si, sv);
		MenuItemCompat.setShowAsAction(si, MenuItem.SHOW_AS_ACTION_IF_ROOM | MenuItem.SHOW_AS_ACTION_COLLAPSE_ACTION_VIEW);
		if (searchByDefault)
			MenuItemCompat.expandActionView(si);

		return true;
	}

	public boolean onEntityClick(EntityInfo ei) {
		if (ei.type.contains(EntityInfo.Type.Domain)) {
			startActivity(new Intent(this, ServiceDiscoveryActivity.class).setData(Uri.parse(ei.jid)));
			return true;
		} else
		if (ei.type.contains(EntityInfo.Type.User)) {
			ChatHelper.startChatActivity(this, ei.jid, ei.name, null);
		} else
		if (ei.type.contains(EntityInfo.Type.MUC)) {
			String nickname = YaximApplication.getConfig().screenName;
			String password = null;
			if (ei.data instanceof BookmarkedConference) {
				BookmarkedConference bc = (BookmarkedConference)ei.data;
				if (!TextUtils.isEmpty(bc.getNickname()))
					nickname = bc.getNickname().toString();
				password = bc.getPassword();
			}
			new EditMUCDialog(this, ei.jid, ei.name, nickname, password).show();
		}
		return true;

	}
	// ExpandableListView.OnChildClickListener
	@Override
	public boolean onChildClick(ExpandableListView parent, View view, int groupPosition, int itemPosition, long id) {
		EntityListAdapter.EntityHolder eh = (EntityListAdapter.EntityHolder) view.getTag();
		return onEntityClick(eh.ei);
	}

	private String mSearchQuery;
	private Handler mSearchHandler = new Handler();
	private Runnable mSearchLater = new Runnable() {
		@Override
		public void run() {
			searchLater(mSearchQuery);
		}
	};

	public void searchNow(String query) {
		// override this to perform an immediate search
	}

	public void searchLater(String query) {
		// override this to perform an "expensive"/delayed search
	}

	// SearchView.OnQueryTextListener
	@Override
	public boolean onQueryTextSubmit(String query) {
		mSearchQuery = query;
		mSearchHandler.removeCallbacks(mSearchLater);
		mSearchHandler.post(mSearchLater);
		return true;
	}

	@Override
	public boolean onQueryTextChange(String query) {
		searchNow(query);
		mSearchQuery = query;
		mSearchHandler.removeCallbacks(mSearchLater);
		mSearchHandler.postDelayed(mSearchLater, 500);
		return true;
	}

}
