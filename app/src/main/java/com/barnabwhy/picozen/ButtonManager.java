package com.barnabwhy.picozen;

import android.accessibilityservice.AccessibilityService;
import android.content.Context;
import android.content.Intent;
import android.view.accessibility.AccessibilityEvent;

@SuppressWarnings("CommentedOutCode")
public class ButtonManager extends AccessibilityService
{
    public void onAccessibilityEvent(AccessibilityEvent event)
    {
        if (event.getEventType() == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            String eventPackage = event.getPackageName().toString();
            String[] libraryAccessibilityEventPackages = getResources().getStringArray(R.array.library_accessibility_event_packages);
            
            for (String eventName : libraryAccessibilityEventPackages) {
                if (eventName.compareTo(eventPackage) == 0) {
                    MainActivity.reset(getApplicationContext());
                    break;
                }
            }
        }
    }

    public void onInterrupt() {}

    //protected void onServiceConnected() {
    //    super.onServiceConnected();
    //}

    @SuppressWarnings("UnusedReturnValue")
    public static boolean isAccessibilityInitialized(Context context)
    {
        try {
            android.provider.Settings.Secure.getInt(
                    context.getApplicationContext().getContentResolver(),
                    android.provider.Settings.Secure.ACCESSIBILITY_ENABLED);

            String settingValue = android.provider.Settings.Secure.getString(
                    context.getApplicationContext().getContentResolver(),
                    android.provider.Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
            if (settingValue != null) {
                return settingValue.contains(context.getPackageName());
            }
        } catch (android.provider.Settings.SettingNotFoundException e) {
            return false;
        }
        return false;
    }

    public static void requestAccessibility(Context context) {
        Intent localIntent = new Intent("android.settings.ACCESSIBILITY_SETTINGS");
        localIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        localIntent.setPackage("com.android.settings");
        context.startActivity(localIntent);
    }
}