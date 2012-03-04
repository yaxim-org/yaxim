package org.yaxim.androidclient.service;

import android.util.Log;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.os.Build;

// Code to make a Service stay in the foreground from
// http://devtcg.blogspot.com/2009/12/gracefully-supporting-multiple-android.html
abstract class ServiceNotification {
	public static ServiceNotification getInstance() {
		/*
		if (Integer.parseInt(Build.VERSION.SDK) <= 4)
			return PreEclair.Holder.sInstance;
		else
		*/
			return EclairAndBeyond.Holder.sInstance;
	}

	public abstract void showNotification(Service context, int id, Notification notification);
	public abstract void hideNotification(Service context, int id);

	/*
	private static class PreEclair extends ServiceNotification {
		private static class Holder {
			private static final PreEclair sInstance = new PreEclair();
		}
		private NotificationManager getNotificationManager(Context context) {
			return (NotificationManager)context.getSystemService(Context.NOTIFICATION_SERVICE);
		}
		public void showNotification(Service context, int id, Notification n) {
			context.setForeground(true);
			getNotificationManager(context).notify(id, n);
		}
		public void hideNotification(Service context, int id) {
			context.setForeground(false);
			getNotificationManager(context).cancel(id);
		}
	}
	*/

	private static class EclairAndBeyond extends ServiceNotification {
		private static class Holder {
			private static final EclairAndBeyond sInstance = new EclairAndBeyond();
		}
		public void showNotification(Service context, int id, Notification n) {
			Log.d("EclairAndBeyond", "showNotification " + id + " " + n);
			context.startForeground(id, n);
		}
		public void hideNotification(Service context, int id) {
			Log.d("EclairAndBeyond", "hideNotification");
			context.stopForeground(true);
		}
	}

}

