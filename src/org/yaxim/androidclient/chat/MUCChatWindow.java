package org.yaxim.androidclient.chat;

import java.util.List;
import java.util.zip.CRC32;
import java.util.zip.Checksum;

import org.yaxim.androidclient.R;
import org.yaxim.androidclient.YaximApplication;
import org.yaxim.androidclient.data.ChatProvider.ChatConstants;
import org.yaxim.androidclient.service.IXMPPMucService;
import org.yaxim.androidclient.service.ParcelablePresence;
import org.yaxim.androidclient.service.XMPPService;

import android.app.AlertDialog;
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

import com.actionbarsherlock.view.MenuInflater;

public class MUCChatWindow extends ChatWindow {
	private static final String TAG = "yaxim.MUCChatWindow";

	private Intent mMucServiceIntent;
	private ServiceConnection mMucServiceConnection;
	private XMPPMucServiceAdapter mMucServiceAdapter;

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
	public boolean onCreateOptionsMenu(com.actionbarsherlock.view.Menu menu) {
		Log.d(TAG, "creating options menu, we're a muc");
		MenuInflater inflater = getSupportMenuInflater(); 
		inflater.inflate(R.menu.muc_options, menu);
		return true;
	}
	
	@Override
	public boolean onOptionsItemSelected(com.actionbarsherlock.view.MenuItem item) {
		Log.d(TAG, "options item selected");
		switch (item.getItemId()) {
		case R.id.chat_optionsmenu_userlist:
			showUserList();
			return true;
		default:
			return super.onOptionsItemSelected(item);
		}
	}
	
	private void addNicknameToInput(String nickname) {
		int cursor_position = mChatInput.getSelectionStart();
		String postfix = (cursor_position == 0) ? ", " : " ";
		mChatInput.getText().insert(cursor_position, nickname + postfix);
	}

	private void showUserList() {
		final List<ParcelablePresence> users = mMucServiceAdapter.getUserList();
		if (users == null)
			return;
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
		AlertDialog dialog = dialogBuilder.create();
		dialog.getListView().setOnItemLongClickListener(new OnItemLongClickListener() {
			@Override
			public boolean onItemLongClick(AdapterView<?> parent,
					View view, int position, long id) {
				Log.d(TAG, "long clicked: " + position + ": " + users.get(position).resource);
				return true;
			}});
		// TODO: this is a fix for broken theming on android 2.x, fix more cleanly!
		if(android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.HONEYCOMB) {
			boolean is_dark = (YaximApplication.getConfig(this).getTheme() == R.style.YaximDarkTheme);
			dialog.getListView().setBackgroundColor(is_dark ? Color.BLACK : Color.WHITE);
		}
		dialog.show();
	}

	
	public String jid2nickname(String jid, String resource) {
		return resource;
	}

	@Override
	public void nick2Color(String nick, TypedValue tv) {
		Checksum nickCRC = new CRC32();
		nickCRC.update(nick.getBytes(), 0, nick.length());
		int nickInt = (int)nickCRC.getValue();
		int theme = YaximApplication.getConfig(this).getTheme();
		// default HSV is for dark theme, bright and light
		float h = Math.abs(nickInt % 360), s = 0.5f, v= 0.9f;

		if (theme == R.style.YaximDarkTheme) {
			// make blue nicks a bit lighter on dark
			if(h<=255.0f && h>=225.0f)
				s=0.4f;
		} else { // light theme: strong and darker nick color
			s=0.85f;
			v=0.6f;
		}
		tv.data = Color.HSVToColor(0xFF, new float[]{h, s, v});
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
