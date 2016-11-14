package ru.star003.artMonitor;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.util.Log;

public class AndroidServiceStartOnBoot extends Service {

	private static final String TAG = "btManager";
	
    @Override
    public IBinder onBind(Intent intent) {
    	
    	Log.d(TAG, "AndroidServiceStartOnBoot -  IBinder");
        return null;
        
    }

    @Override
    public void onCreate() {
    	Log.d(TAG, "AndroidServiceStartOnBoot -  onCreate");
        super.onCreate();
       // here you can add whatever you want this service to do
    }

}