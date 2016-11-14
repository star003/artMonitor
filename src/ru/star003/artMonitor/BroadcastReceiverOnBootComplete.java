package ru.star003.artMonitor;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

public class BroadcastReceiverOnBootComplete extends BroadcastReceiver {

	private static final String TAG = "btManager";
	
    @Override
    public void onReceive(Context context, Intent intent) {
    	
    	Log.d(TAG, "BroadcastReceiverOnBootComplete -  onReceive");
    	
        if (intent.getAction().equalsIgnoreCase(Intent.ACTION_BOOT_COMPLETED)) {
            Intent serviceIntent = new Intent(context, AndroidServiceStartOnBoot.class);
            context.startService(serviceIntent);
        }
    }
}