package org.yaxim.androidclient.list;

import android.app.Activity;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseExpandableListAdapter;
import android.widget.Filter;
import android.widget.Filterable;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import org.yaxim.androidclient.R;
import org.yaxim.androidclient.data.EntityInfo;
import org.yaxim.androidclient.data.EntityList;

import java.util.ArrayList;

/**
 * Expandable adapter based on lists of entities.
 */

public class EntityListAdapter extends BaseExpandableListAdapter implements Filterable {
	public ArrayList<EntityList> groups = new ArrayList<EntityList>();
	LayoutInflater inflater;
	Activity act;


	public EntityListAdapter(Activity act) {
		this.act = act;
		inflater = (LayoutInflater)act.getSystemService(act.LAYOUT_INFLATER_SERVICE);
	}

	public void add(EntityList pg) {
		groups.add(pg);
		notifyDataSetChanged();
	}

	@Override
	public int getGroupCount() {
		return groups.size();
	}

	@Override
	public int getChildrenCount(int groupId) {
		EntityList el = groups.get(groupId);
		if (el == null || el.items == null)
			return 0;
		return el.items.size();
	}

	@Override
	public Object getGroup(int groupId) {
		return groups.get(groupId);
	}

	@Override
	public Object getChild(int groupId, int childId) {
		return groups.get(groupId).items.get(childId);
	}

	@Override
	public long getGroupId(int groupId) {
		return groupId;
	}

	@Override
	public long getChildId(int groupId, int childId) {
		return getCombinedChildId(groupId, childId);
	}

	@Override
	public boolean hasStableIds() {
		return false;
	}

	public static class GroupHolder {
		TextView groupname;
		ProgressBar progressBar;
		TextView members;
	}

	public static class EntityHolder {
		TextView unread;
		ImageView icon;
		TextView name;
		TextView nusers;
		TextView status;
		EntityInfo ei;
	}

	@Override
	public View getGroupView(int groupId, boolean isExpanded, View view, ViewGroup parent) {
		GroupHolder holder;
		if (view == null) {
			view = inflater.inflate(R.layout.maingroup_row, parent, false);
			holder = new GroupHolder();
			holder.groupname = (TextView)view.findViewById(R.id.groupname);
			holder.progressBar = (ProgressBar) view.findViewById(R.id.loading_progress);
			holder.members = (TextView)view.findViewById(R.id.members);
			view.setTag(holder);
		} else
			holder = (GroupHolder)view.getTag();
		EntityList el = groups.get(groupId);
		holder.groupname.setText(el.groupName);
		String countText = (el.isError()) ? "" : ""+el.items.size();
		holder.progressBar.setVisibility(el.isLoading() ? View.VISIBLE : View.GONE);
		holder.members.setText(countText);
		return view;
	}

	@Override
	public View getChildView(int groupId, int childId, boolean isLastChild, View view, ViewGroup parent) {
		return getChildView(inflater, groups.get(groupId).items.get(childId), isLastChild, view, parent);
	}

	public static View getChildView(LayoutInflater inflater, EntityInfo ei, boolean isLastChild, View view, ViewGroup parent) {
		EntityHolder holder;
		if (view == null) {
			view = inflater.inflate(R.layout.mainchild_row, parent, false);
			holder = new EntityHolder();
			holder.unread = (TextView) view.findViewById(R.id.roster_unreadmsg_cnt);
			holder.icon = (ImageView)view.findViewById(R.id.roster_icon);
			holder.name = (TextView) view.findViewById(R.id.roster_screenname);
			holder.nusers = (TextView) view.findViewById(R.id.roster_nusers);
			holder.status = (TextView) view.findViewById(R.id.roster_statusmsg);
			view.setTag(holder);
		} else
			holder = (EntityHolder)view.getTag();
		int drawableId = android.R.drawable.ic_dialog_alert;

		// error handling: SM == null --> error
		if (ei.statusMode != null)
			drawableId = ei.statusMode.getDrawableId();
		holder.name.setSingleLine(ei.statusMode != null);

		holder.icon.setImageResource(drawableId);
		holder.unread.setText(ei.unread > 0 ? "" + ei.unread : "");
		holder.name.setText(ei.name);
		holder.nusers.setText("" + ei.users);
		holder.nusers.setVisibility(ei.users == 0 ? View.GONE : View.VISIBLE);
		holder.status.setText(ei.status);
		holder.status.setVisibility(TextUtils.isEmpty(ei.status) ? View.GONE : View.VISIBLE);
		holder.ei = ei;
		return view;
	}

	@Override
	public boolean isChildSelectable(int groupId, int childId) {
		return !groups.get(groupId).isError();
	}

	@Override
	public Filter getFilter() {
		return null;
	}
}
