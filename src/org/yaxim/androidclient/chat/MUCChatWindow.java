package org.yaxim.androidclient.chat;

import java.util.List;

import org.yaxim.androidclient.R;
import org.yaxim.androidclient.data.ChatProvider.ChatConstants;
import org.yaxim.androidclient.data.ChatHelper;
import org.yaxim.androidclient.data.ChatRoomHelper;
import org.yaxim.androidclient.dialogs.ConfirmDialog;
import org.yaxim.androidclient.dialogs.EditMUCDialog;
import org.yaxim.androidclient.service.IXMPPMucService;
import org.yaxim.androidclient.service.ParcelablePresence;
import org.yaxim.androidclient.service.XMPPService;
import org.yaxim.androidclient.util.XEP0392Helper;
import org.yaxim.androidclient.util.XMPPHelper;

import android.support.v7.app.AlertDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.DialogInterface.OnClickListener;
import android.database.Cursor;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;
import android.text.TextUtils;
import android.util.Log;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.AdapterView.OnItemLongClickListener;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;

public class MUCChatWindow extends ChatWindow {
	private static final String TAG = "yaxim.MUCChatWindow";

	private Intent mMucServiceIntent;
	private ServiceConnection mMucServiceConnection;
	private XMPPMucServiceAdapter mMucServiceAdapter;
	private String myNick;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		// fill in nickname on tap
		getListView().setOnItemClickListener(new OnItemClickListener() {
			@Override
			public void onItemClick(AdapterView<?> parent,
					View view, int position, long id) {
				Cursor c = (Cursor)parent.getItemAtPosition(position);
				addNicknameToInput(c.getString(c.getColumnIndex(ChatConstants.RESOURCE)));
			}});
		XMPPHelper.setStaticNFC(this, "xmpp:" + java.net.URLEncoder.encode(mWithJabberID) + "?join");
	}

	@Override
	protected void registerXMPPService() {
		super.registerXMPPService();

		mMucServiceIntent = new Intent(this, XMPPService.class);
		Uri dtaUri = Uri.parse(mWithJabberID+"?chat");
		mMucServiceIntent.setData(dtaUri);
		mMucServiceIntent.setAction("org.yaxim.androidclient.XMPPSERVICE");

		mMucServiceConnection = new ServiceConnection() {
			public void onServiceConnected(ComponentName name, IBinder service) {
				mMucServiceAdapter = new XMPPMucServiceAdapter(
						IXMPPMucService.Stub.asInterface(service), 
						mWithJabberID);
				myNick = mMucServiceAdapter.getMyMucNick();
				mChatAdapter.mScreenName = myNick;
				supportInvalidateOptionsMenu();
				getListView().invalidateViews();
			}
			public void onServiceDisconnected(ComponentName name) {
			}
		};
	

	}

	@Override
	protected void unbindXMPPService() {
		super.unbindXMPPService();
		try {
			unbindService(mMucServiceConnection);
		} catch (IllegalArgumentException e) {
			Log.e(TAG, "Service wasn't bound!");
		}
	}

	@Override
	protected void bindXMPPService() {
		super.bindXMPPService();
		bindService(mMucServiceIntent, mMucServiceConnection, BIND_AUTO_CREATE);
	}


	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		Log.d(TAG, "creating options menu, we're a muc");
		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.muc_options, menu);
		inflateGenericContactOptions(menu);
		return true;
	}
	
	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		Log.d(TAG, "options item selected");
		switch (item.getItemId()) {
		case R.id.chat_optionsmenu_userlist:
			showUserList();
			return true;
		case R.id.roster_contextmenu_muc_edit:
			new EditMUCDialog(this, mWithJabberID).dontOpen().show();
			return true;
		case R.id.roster_contextmenu_muc_share:
			ChatHelper.showQrDialog(this, mWithJabberID,
				XMPPHelper.createMucLinkHTTPS(mWithJabberID), mUserScreenName);
			return true;
		case R.id.roster_contextmenu_muc_leave:
			ConfirmDialog.show(this, R.string.roster_contextmenu_muc_leave,
					getString(R.string.muc_leave_question, mWithJabberID), mWithJabberID,
					new ConfirmDialog.Ok() {
						@Override
						public void ok(final String jid) {
							if (ChatRoomHelper.leaveRoom(MUCChatWindow.this, jid))
								ChatRoomHelper.syncDbRooms(MUCChatWindow.this);
							// XXX: if we do not unbind here, we will leak the service
							unbindXMPPService();
							finish();
						}
					});
			return true;
		default:
			return super.onOptionsItemSelected(item);
		}
	}
	
	private void addNicknameToInput(String nickname) {
		if (TextUtils.isEmpty(nickname) || nickname.equalsIgnoreCase(myNick))
			return;
		int cursor_position = mChatInput.getSelectionStart();
		String postfix = (cursor_position == 0) ? ": " : " ";
		mChatInput.getText().insert(cursor_position, nickname + postfix);
	}

	private void showUserList() {
		if (mMucServiceAdapter == null)
			return;
		final List<ParcelablePresence> users = mMucServiceAdapter.getUserList();
		if (users == null) {
			Toast.makeText(this, R.string.Global_authenticate_first, Toast.LENGTH_SHORT).show();
			return;
		}
		AlertDialog.Builder dialogBuilder = new AlertDialog.Builder(MUCChatWindow.this)
		.setTitle(getString(R.string.chat_muc_userlist, mWithJabberID))
		.setNegativeButton(android.R.string.cancel, null);

		PresenceArrayAdapter adapter = new PresenceArrayAdapter(MUCChatWindow.this, users);

		Log.d(TAG, "adapter has values: "+adapter.getCount());
		dialogBuilder.setAdapter(adapter, new OnClickListener() {
			@Override
			public void onClick(DialogInterface dialog, int which) {
				addNicknameToInput(users.get(which).resource);
			}
		});
		final AlertDialog dialog = dialogBuilder.create();
		dialog.getListView().setOnItemLongClickListener(new OnItemLongClickListener() {
			@Override
			public boolean onItemLongClick(AdapterView<?> parent,
					View view, int position, long id) {
				String nickname = users.get(position).resource;
				ChatHelper.startChatActivity(MUCChatWindow.this, users.get(position).bare_jid,
						String.format("%s (%s)", nickname, mUserScreenName), null);
				dialog.dismiss();
				return true;
			}});
		dialog.show();
	}

	
	public String jid2nickname(String jid, String resource) {
		return resource;
	}

	@Override
	public boolean isFromMe(boolean from_me, String resource) {
		return from_me || (!TextUtils.isEmpty(myNick) && myNick.equals(resource));
	}

	@Override
	public void nick2Color(String nick, TypedValue tv) {
		if (nick == null || nick.length() == 0) // no color for empty nickname
			return;
		tv.data = XEP0392Helper.mixNickWithBackground(nick, getTheme(), mConfig.getTheme());
	}
	

	private class PresenceArrayAdapter extends ArrayAdapter<ParcelablePresence> {
		TypedValue tv = new TypedValue();

		public PresenceArrayAdapter(Context context, List<ParcelablePresence> pp) {
			super(context, R.layout.mainchild_row, pp);
		}

		@Override
		public View getView(int position, View convertView, ViewGroup parent) {
			ParcelablePresence pp = getItem(position);

			if (convertView == null)
				convertView = getLayoutInflater().inflate(R.layout.mainchild_row, parent, false);
			
			TextView nick = ((TextView)convertView.findViewById(R.id.roster_screenname));
			TextView statusmsg = ((TextView)convertView.findViewById(R.id.roster_statusmsg));
			
			nick.setText(pp.resource);
			nick2Color(pp.resource, tv);
			nick.setTextColor(tv.data);
			nick.setTypeface(null, android.graphics.Typeface.BOLD);
			
			boolean hasStatus = pp.status != null && pp.status.length() > 0;
			statusmsg.setText(pp.status);
			statusmsg.setVisibility(hasStatus ? View.VISIBLE : View.GONE);
			
			((ImageView)convertView.findViewById(R.id.roster_icon)).setImageResource(pp.status_mode.getDrawableId());
			
			return convertView;
		}
	}
}
