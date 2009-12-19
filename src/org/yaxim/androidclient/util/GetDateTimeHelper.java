package org.yaxim.androidclient.util;

import android.text.format.Time;

public class GetDateTimeHelper{
		
	public static String setDate(){
		Time myTime= new Time();
		myTime.setToNow();
		
	return myTime.format("%d-%m-%y %H:%M:%S");
	}

}
