package org.yaxim.androidclient.data;

import android.widget.Filter;
import android.widget.Filterable;

import org.yaxim.androidclient.list.EntityListAdapter;

import java.util.ArrayList;
import java.util.HashMap;

/**
 * Filterable list of entities for a sub-tree in an EntityListAdapter.
 *
 * This list preserves the insertion order and allows addressing / replacing by JID.
 *
 * It supports the states "loading" and "error".
 */

public class EntityList implements Filterable {
	EntityListAdapter adapter;

	private ArrayList<EntityInfo> unfiltered_items = new ArrayList<>();
	private HashMap<String, Integer> jid_index = new HashMap<>();
	private EntityInfoFilter pif = new EntityInfoFilter();

	public String groupName;
	public ArrayList<EntityInfo> items = unfiltered_items;
	private boolean loading;
	private boolean error;

	public EntityList(EntityListAdapter adapter, String groupName) {
		this.adapter = adapter;
		this.groupName = groupName;
		loading = false;
		error = false;
	}

	public void startLoading() {
		loading = true;
		error = false;
		adapter.notifyDataSetChanged();
	}
	public void finishLoading() {
		loading = false;
		error = false;
		adapter.notifyDataSetChanged();
	}

	public boolean isLoading() {
		return loading;
	}

	public boolean isError() {
		return error;
	}

	public void add(EntityInfo pp, boolean notify) {
		Integer idx = jid_index.get(pp.jid);
		if (idx != null)
			unfiltered_items.set(idx, pp);
		else {
			jid_index.put(pp.jid, unfiltered_items.size());
			unfiltered_items.add(pp);
		}
		if (notify)
			adapter.notifyDataSetChanged();
	}
	public void clear() {
		unfiltered_items.clear();
		jid_index.clear();
		loading = true;
		error = false;
		adapter.notifyDataSetChanged();
	}

	public void setError(Throwable ex) {
		unfiltered_items.clear();
		unfiltered_items.add(EntityInfo.fromError(ex));
		loading = false;
		error = true;
		adapter.notifyDataSetChanged();
	}

	@Override
	public Filter getFilter() {
		return pif;
	}

	class EntityInfoFilter extends Filter {
		@Override
		protected FilterResults performFiltering(CharSequence search) {
			ArrayList<EntityInfo> filtered = new ArrayList<>();
			String needle = search.toString().toLowerCase();
			for (EntityInfo item : unfiltered_items) {
				if (item.jid.contains(needle) || item.name.contains(needle))
					filtered.add(item);
			}
			FilterResults fr = new FilterResults();
			fr.values = filtered;
			fr.count = filtered.size();
			return fr;
		}

		@Override
		protected void publishResults(CharSequence charSequence, FilterResults filterResults) {
			items = (ArrayList<EntityInfo>) filterResults.values;
			adapter.notifyDataSetChanged();
		}
	}
}
