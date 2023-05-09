package com.barnabwhy.picozen;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class Receiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent.getAction().equals(Intent.ACTION_BOOT_COMPLETED)) {
//            boolean autorunEnabled = sharedPreferences.getBoolean(KEY_AUTORUN, true);
            boolean autorunEnabled = false;
            if (autorunEnabled) {
                Intent i = new Intent(context, MainActivity.class);
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(i);
            }
        }

        // listen for package broadcasts so we can update the app list better
        if (intent.getAction().equals(Intent.ACTION_PACKAGE_ADDED)
            ||intent.getAction().equals(Intent.ACTION_PACKAGE_CHANGED)
            ||intent.getAction().equals(Intent.ACTION_PACKAGE_REMOVED)) {
            Intent local = new Intent();
            local.setAction("picozen.applist.update");
            context.sendBroadcast(local);
        }
    }
}