package org.yaxim.androidclient.chat;

import java.util.Random;
import java.util.zip.CRC32;
import java.util.zip.Checksum;

import org.yaxim.androidclient.R;
import org.yaxim.androidclient.YaximApplication;
import org.yaxim.androidclient.service.IXMPPMucService;
import org.yaxim.androidclient.service.XMPPService;

import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.DialogInterface.OnClickListener;
import android.graphics.Color;
import android.net.Uri;
import android.os.IBinder;
import android.util.Log;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import com.actionbarsherlock.view.MenuInflater;

public class MUCChatWindow extends ChatWindow {
	private static final String TAG = "yaxim.MUCChatWindow";

	private Intent mMucServiceIntent;
	private ServiceConnection mMucServiceConnection;
	private XMPPMucServiceAdapter mMucServiceAdapter;

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
		inflater.inflate(R.menu.chat_options, menu);
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
	

	private void showUserList() {
		final String[] users = mMucServiceAdapter.getUserList();
		final TypedValue tv = new TypedValue();
		AlertDialog.Builder dialogBuilder = new AlertDialog.Builder(MUCChatWindow.this)
		.setTitle("Users in room "+mWithJabberID)
		.setNeutralButton("Close", null);

		ArrayAdapter<String> adapter = new ArrayAdapter<String>(MUCChatWindow.this, R.layout.single_string) {
			@Override
			public View getView(int position, View convertView, ViewGroup parent) {
				View v = super.getView(position, convertView, parent);
				nick2Color(getItem(position), tv);
				((TextView) v).setTextColor(tv.data);
				return v;
			}
		};
		for(String user : users)
			adapter.add(user);
		Log.d(TAG, "adapter has values: "+adapter.getCount());
		dialogBuilder.setAdapter(adapter, new OnClickListener() {
			@Override
			public void onClick(DialogInterface dialog, int which) {
				String postfix=mChatInput.getSelectionStart()==0 ? ", " : ""; 
				mChatInput.getText().insert(mChatInput.getSelectionStart(), users[which]+postfix);
			}
		});
		AlertDialog dialog = dialogBuilder.create();
		// TODO: this is a fix for broken theming on android 2.x, fix more cleanly!
		if(YaximApplication.getConfig(this).getTheme() == R.style.YaximDarkTheme
				&& android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.HONEYCOMB) {
			dialog.getListView().setBackgroundColor(Color.BLACK);
		} else if(YaximApplication.getConfig(this).getTheme() == R.style.YaximLightTheme
				&& android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.HONEYCOMB) {
			dialog.getListView().setBackgroundColor(Color.WHITE);
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
		Random rand = new Random(nickInt);
		float r1 = -0.15f + ( rand.nextFloat() * (0.15f - -0.15f) );
		float r2 = -0.1f + ( rand.nextFloat() * (0.1f - -0.1f) );
		int blueShift = rand.nextBoolean() ? 45 : -45;
		
		float h, s, v;
		h=Math.abs( nickInt%360 );

		s=0.5f; v=0.5f;
		if(YaximApplication.getConfig(this).getTheme() == R.style.YaximDarkTheme) {
			s=0.75f + r1;
			v=0.9f + r2;
			if(h<=255.0f && h>=225.0f) {
				h = h + blueShift;
			}
		} else if(YaximApplication.getConfig(this).getTheme() == R.style.YaximLightTheme) {
			s=0.7f + r1; 
			v=0.8f + r2;
		}
		
		/*Log.d(TAG, String.format(
				"nick2Color(%s): nickInt: %d, r1: %f, r2: %f, noBlue: %s, h: %f, s: %f, v: %f", 
				nick, nickInt, r1, r2, blueShift, h, s, v));*/
		tv.data = Color.HSVToColor(0xFF, new float[]{h, s, v});
	}
	

}
