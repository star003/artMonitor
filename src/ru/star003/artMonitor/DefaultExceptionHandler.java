package ru.star003.artMonitor;

import java.lang.Thread.UncaughtExceptionHandler;
 
import android.app.Activity;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

public class DefaultExceptionHandler implements UncaughtExceptionHandler {
	 
    //private UncaughtExceptionHandler defaultUEH;
    Activity activity;
    private static final String TAG = "btManager";
 
    public DefaultExceptionHandler(Activity activity) {
        this.activity = activity;
    }
 
    @Override
    public void uncaughtException(Thread thread, Throwable ex) {
 
    	Log.d(TAG, "пришел пиздец.....");
    	
        Intent intent = new Intent(activity, btManager.class);
 
		intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP
		        | Intent.FLAG_ACTIVITY_CLEAR_TASK
		        | Intent.FLAG_ACTIVITY_NEW_TASK);
 
		PendingIntent pendingIntent = PendingIntent.getActivity(
				btManager.getIntance().getBaseContext(), 0, intent, intent.getFlags());
 
		            //Following code will restart your application after 2 seconds
		AlarmManager mgr = (AlarmManager) btManager.getIntance().getBaseContext()
		        .getSystemService(Context.ALARM_SERVICE);
		mgr.set(AlarmManager.RTC, System.currentTimeMillis() + 1000,
		        pendingIntent);
 
		            //This will finish your activity manually
		activity.finish();
 
		            //This will stop your application and take out from it.
		System.exit(2);
    }
}