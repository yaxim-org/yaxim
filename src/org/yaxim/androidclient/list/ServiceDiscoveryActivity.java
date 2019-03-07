package org.yaxim.androidclient.list;

import android.os.Bundle;

import org.yaxim.androidclient.R;
import org.yaxim.androidclient.data.EntityList;

/**
 * An activity to browse the Service Discovery (disco#items) list on a JID.
 */
public class ServiceDiscoveryActivity extends EntityListActivity {
	String jid;
	EntityList results;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		if (getIntent().getData() == null) {
			finish();
			return;
		}
		jid = getIntent().getDataString();
		results = new EntityList(ela, jid);
		ela.add(results);
		elv.setAdapter(ela);
		new EntityListLoader(EntityListLoader.MODE_DOMAIN, results).execute(jid);
		setTitle(getString(R.string.title_service_disco, jid));
		sv.setQueryHint(getString(android.R.string.search_go));

	}

	@Override
	protected void onResume() {
		super.onResume();
		elv.expandGroup(0);
	}
	@Override
	public void searchNow(String query) {
		results.getFilter().filter(query);
	}
}
