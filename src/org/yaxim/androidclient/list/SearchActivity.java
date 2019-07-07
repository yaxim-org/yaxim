package org.yaxim.androidclient.list;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ExpandableListView;
import android.widget.LinearLayout;

import org.yaxim.androidclient.R;
import org.yaxim.androidclient.data.EntityInfo;
import org.yaxim.androidclient.data.EntityList;
import org.yaxim.androidclient.util.XMPPHelper;

/**
 * Activity used to search for JIDs, MUCs, etc.
 *
 * This provides access to:
 *   - a small "popup" view with details for entered JIDs
 *   - a (search query filtered) bookmarks list
 *   - MUC search from MUClumbus
 */

public class SearchActivity extends EntityListActivity {
	EntityList bookmarks;
	EntityList search;
	EntityListLoader mucSearchLoader;
	EntityViewLoader jidLoader;
	View jidView;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		searchByDefault = true;
		super.onCreate(savedInstanceState);
		sv.setQueryHint(getString(android.R.string.search_go));

		// initialize and auto-load bookmarks
		bookmarks = new EntityList(ela, getString(R.string.group_bookmarks));
		ela.add(bookmarks);
		new EntityListLoader(EntityListLoader.MODE_BOOKMARKS, bookmarks).execute();

		// initialize MUC search and delay loading until user taps
		search = new EntityList(ela, getString(R.string.group_mucsearch));
		ela.add(search);

		// initialize head view for direct JID addition
		LayoutInflater inflater = (LayoutInflater)getSystemService(LAYOUT_INFLATER_SERVICE);
		jidView = EntityListAdapter.getChildView(inflater, new EntityInfo(), false, null, elv);
		jidView.setVisibility(View.GONE);
		jidView.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View view) {
				EntityListAdapter.EntityHolder eh = (EntityListAdapter.EntityHolder) view.getTag();
				onEntityClick(eh.ei);
			}
		});
		//Android sucks: you can't have a GONE HeaderView!?!
		//elv.addHeaderView(jidView);
		((LinearLayout)findViewById(R.id.muclist_layout)).addView(jidView, 0);

		elv.setAdapter(ela);
		// start MUC search on group expansion, because privacy and bot load
		elv.setOnGroupExpandListener(
				new ExpandableListView.OnGroupExpandListener() {
					public void onGroupExpand(int groupPosition) {
						switch (groupPosition) {
							case 1:
								if (mucSearchLoader != null)
									return;
								mucSearchLoader = new EntityListLoader(EntityListLoader.MODE_MUCLUMBUS, search);
								initiateMUCSearch(sv.getQuery().toString());
								break;
						}
					}
				});
	}

	@Override
	protected void onResume() {
		super.onResume();
		// auto-expand bookmarks
		elv.expandGroup(0);
	}

	protected void initiateMUCSearch(String query) {
		if (mucSearchLoader == null)
			return; // user didn't enable remote search yet
		if (mucSearchLoader.search != null && mucSearchLoader.search.equals(query))
			return; // no need to reload the same list
		mucSearchLoader.cancel(true);
		mucSearchLoader = new EntityListLoader(EntityListLoader.MODE_MUCLUMBUS, search);
		mucSearchLoader.execute(query);
	}

	@Override
	public void searchLater(String query) {
		query = XMPPHelper.mxid2jid(query);
		if (jidLoader != null) {
			jidLoader.cancel(true);
		}
		jidLoader = new EntityViewLoader(SearchActivity.this, jidView);
		jidLoader.execute(query);
		initiateMUCSearch(query);
	}

	@Override
	public void searchNow(String query) {
		bookmarks.getFilter().filter(query);
	}
}
