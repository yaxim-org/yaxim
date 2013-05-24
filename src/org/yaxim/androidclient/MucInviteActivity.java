package org.yaxim.androidclient;

import org.yaxim.androidclient.service.IXMPPMucService;
import org.yaxim.androidclient.service.XMPPService;

import com.actionbarsherlock.app.SherlockActivity;

import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteException;
import android.app.Activity;
import android.app.NotificationManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.util.Log;
import android.view.Menu;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

public class MucInviteActivity extends SherlockActivity {
	public static final String INTENT_EXTRA_ROOM = "room";
	public static final String INTENT_EXTRA_BODY = "body";
	public static final String INTENT_EXTRA_ID = "id";
	public static final String TAG = "yaxim.MucInviteActivity";
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_muc_invite);

		Intent intent = getIntent();
	    
	    if(!(intent.hasExtra(INTENT_EXTRA_BODY) && intent.hasExtra(INTENT_EXTRA_ROOM))) {
	    	return;
	    }
	    
	    
	    NotificationManager notificationMgr = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
	    notificationMgr.cancel(intent.getIntExtra(INTENT_EXTRA_ID, -1));
	    
	    final String room = intent.getStringExtra(INTENT_EXTRA_ROOM);
	    String body = intent.getStringExtra(INTENT_EXTRA_BODY);

	    TextView roomText = (TextView) findViewById(R.id.bodyText);
	    TextView bodyText = (TextView) findViewById(R.id.roomText);
	    Button joinButton = (Button) findViewById(R.id.joinButton);
	    Button rejectButton = (Button) findViewById(R.id.rejectButton);
	    final EditText nickText = (EditText) findViewById(R.id.nickEditText);
	    
	    roomText.setText(room);
	    bodyText.setText(body);
	    
	    joinButton.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) { // TODO: code-duplication from MainWindow.addRoom ...but how to generalize/call?
				final String nick = nickText.getText().toString(); 
				if(nick.equals("")) {
					Toast errorToast = new Toast(getApplicationContext());
					errorToast.setText("Please enter a Nickname!");
					errorToast.show();
					return;
				}
								
				Intent muctestIntent = new Intent(MucInviteActivity.this, XMPPService.class);
				muctestIntent.setAction("org.yaxim.androidclient.XMPPSERVICE");
				Uri dtaUri = Uri.parse(room+"?chat");
				muctestIntent.setData(dtaUri);
				
				ServiceConnection muctestServiceConnection = new ServiceConnection() {
					public void onServiceConnected(ComponentName name, IBinder service) {
						IXMPPMucService mucService = IXMPPMucService.Stub.asInterface(service);
						try {
							mucService.addRoom(room, "", nick);
						} catch (RemoteException e) {
							e.printStackTrace();
						}
						MucInviteActivity.this.finish();
					}
					public void onServiceDisconnected(ComponentName name) {}
				};
				bindService(muctestIntent, muctestServiceConnection, BIND_AUTO_CREATE);
			}
		});
	    
	    rejectButton.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				MucInviteActivity.this.finish(); // TODO: find out whether to send out reject message?
			}
		});
	    
	}


}
