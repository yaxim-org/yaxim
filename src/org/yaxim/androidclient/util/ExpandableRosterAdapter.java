package org.yaxim.androidclient.util;

import java.util.List;
import java.util.Map;

import org.yaxim.androidclient.data.RosterItem;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.SimpleExpandableListAdapter;
import android.widget.TextView;
import org.yaxim.androidclient.R;

public class ExpandableRosterAdapter extends SimpleExpandableListAdapter {

	List<? extends List<? extends Map<String, ?>>> rosterContent;

	public ExpandableRosterAdapter(Context context,
			List<? extends Map<String, ?>> groupData, int expandedGroupLayout,
			int collapsedGroupLayout, String[] groupFrom, int[] groupTo,
			List<? extends List<? extends Map<String, ?>>> childData,
			int childLayout, int lastChildLayout, String[] childFrom,
			int[] childTo) {

		super(context, groupData, expandedGroupLayout, collapsedGroupLayout,
				groupFrom, groupTo, childData, childLayout, lastChildLayout,
				childFrom, childTo);
		this.rosterContent = childData;
	}

	public ExpandableRosterAdapter(Context context,
			List<? extends Map<String, ?>> groupData, int expandedGroupLayout,
			int collapsedGroupLayout, String[] groupFrom, int[] groupTo,
			List<? extends List<? extends Map<String, ?>>> childData,
			int childLayout, String[] childFrom, int[] childTo) {
		super(context, groupData, expandedGroupLayout, collapsedGroupLayout,
				groupFrom, groupTo, childData, childLayout, childFrom, childTo);
		this.rosterContent = childData;
	}

	public ExpandableRosterAdapter(Context context,
			List<? extends Map<String, ?>> groupData, int groupLayout,
			String[] groupFrom, int[] groupTo,
			List<? extends List<? extends Map<String, ?>>> childData,
			int childLayout, String[] childFrom, int[] childTo) {
		super(context, groupData, groupLayout, groupFrom, groupTo, childData,
				childLayout, childFrom, childTo);
		this.rosterContent = childData;
	}

	@Override
	public View getChildView(int groupPosition, int childPosition,
			boolean isLastChild, View convertView, ViewGroup parent) {

		View currentRow;
		if (convertView == null)
			currentRow = newChildView(isLastChild, parent);
		else
			currentRow = convertView;

		RosterItem contactListItem = getRosterItem(groupPosition, childPosition);
		setLabel(currentRow, contactListItem);
		String statusMessage = contactListItem.statusMessage;
		setStatusMessage(currentRow, statusMessage);
		StatusMode presenceMode = contactListItem.getStatusMode();
		setIcon(currentRow, presenceMode);

		return currentRow;
	}

	private void setLabel(View currentRow, RosterItem contactListItem) {
		TextView label = (TextView) currentRow
				.findViewById(R.id.roster_screenname);
		label.setText(contactListItem.toString());
	}

	private void setIcon(View currentRow, StatusMode presenceMode) {
		ImageView statusIcon = (ImageView) currentRow
				.findViewById(R.id.roster_icon);
		statusIcon.setImageResource(getIconForPresenceMode(presenceMode));
	}
	
	private void setStatusMessage(View currentRow, String statusMessage) {
		TextView status = (TextView) currentRow
				.findViewById(R.id.roster_statusmsg);
		if ((statusMessage == null) || (statusMessage.length() == 0)) {
			status.setVisibility(View.GONE);
		} else {
			status.setVisibility(View.VISIBLE);
			status.setText(statusMessage);
		}
	}

	private RosterItem getRosterItem(int groupPosition, int childPosition) {
		return (RosterItem) rosterContent.get(groupPosition).get(childPosition)
				.get(AdapterConstants.CONTACT_ID);
	}


	@Override
	public View getGroupView(int groupPosition, boolean isExpanded,
			View convertView, ViewGroup parent) {
		return super.getGroupView(groupPosition, isExpanded, convertView,
				parent);
	}

	private int getIconForPresenceMode(StatusMode presenceMode) {

		switch (presenceMode) {
		case chat:
			return R.drawable.chat;
		case available:
			return R.drawable.available;
		case away:
			return R.drawable.away;
		case dnd:
			return R.drawable.donotdisturb;
		case xa:
			return R.drawable.xa;
		case offline:
			return R.drawable.offline;
		}

		return android.R.drawable.presence_offline;
	}

}
