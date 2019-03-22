package org.yaxim.androidclient;

import org.yaxim.androidclient.data.YaximConfiguration;
import org.yaxim.androidclient.service.SmackableImp;
import org.yaxim.androidclient.service.YaximBroadcastReceiver;

import android.app.Application;
import android.content.IntentFilter;
import android.support.v7.app.AppCompatDelegate;

import de.duenndns.ssl.MemorizingTrustManager;

public class YaximApplication extends Application {
	// identity type, see:
	// http://xmpp.org/registrar/disco-categories.html
	// identity name is `app_name` string
	public static final String XMPP_IDENTITY_TYPE = "phone";

	private static YaximApplication app;

	// MTM is needed globally for both the backend (connect)
	// and the frontend (display dialog)
	public MemorizingTrustManager mMTM;

	private YaximConfiguration mConfig;
	private SmackableImp mSmackable;

	public static YaximApplication getInstance() {
		return app;
	}

	public YaximApplication() {
		super();
		app = this;
	}

	@Override
	public void onCreate() {
		super.onCreate();
		AppCompatDelegate.setCompatVectorFromResourcesEnabled(true);
		mMTM = new MemorizingTrustManager(this);
		mConfig = new YaximConfiguration(this);
		// since Android 7, you need to manually register for network changes
		// https://developer.android.com/training/monitoring-device-state/connectivity-monitoring.html#MonitorChanges
		registerReceiver(new YaximBroadcastReceiver(), new IntentFilter(android.net.ConnectivityManager.CONNECTIVITY_ACTION));
	}

	public static YaximApplication getApp() { return app; }
	public static YaximConfiguration getConfig() { return app.mConfig; }

	// short-cut from the UI to the network
	public SmackableImp getSmackable() {
		return mSmackable;
	}
	public void setSmackable(SmackableImp smackable) {
		mSmackable = smackable;
	}
}

