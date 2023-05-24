package com.barnabwhy.picozen;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class SettingsProvider
{
    public static final String KEY_CURRENT_TAB = "KEY_CURRENT_TAB";
    public static final String KEY_SORT_SPINNER = "KEY_SORT_SPINNER";
    public static final String KEY_SORT_FIELD = "KEY_SORT_FIELD";
    public static final String KEY_SORT_ORDER = "KEY_SORT_ORDER";
    public static final String KEY_GROUP_SPINNER = "KEY_GROUP_SPINNER";
    public static final String KEY_FAVORITE_APPS = "KEY_FAVORITE_APPS";
    public static final String KEY_HIDDEN_APPS = "KEY_HIDDEN_APPS";
    public static final String KEY_RECENTS = "KEY_RECENTS";
    public static final String KEY_SIDELOAD_TYPE = "KEY_SIDELOAD_TYPE";
    public static final String KEY_SIDELOAD_HOST = "KEY_SIDELOAD_HOST";
    public static final String KEY_START_ON_BOOT = "KEY_START_ON_BOOT";

    private static SettingsProvider instance;

    public static synchronized SettingsProvider getInstance (Context context)
    {
        if (SettingsProvider.instance == null) {
            SettingsProvider.instance = new SettingsProvider(context);
        }
        return SettingsProvider.instance;
    }

    //storage
    private final SharedPreferences mPreferences;
    private Map<String, Long> mRecents = new HashMap<>();
    public static final Map<String, Intent> launchIntents = new HashMap<>();
    public static final Map<String, Long> installDates = new HashMap<>();

    private SettingsProvider(Context context) {
        mPreferences = context.getSharedPreferences(context.getPackageName() + "_preferences", Context.MODE_PRIVATE);
    }

    public Map<String, Long> getRecents()
    {
        if (mRecents.isEmpty()) {
            loadRecents();
        }
        return mRecents;
    }

    public void updateRecent(String packageName, Long timestamp) {
        mRecents.put(packageName, timestamp);
        saveRecents();
    }

    public static String getAppDisplayName(Context context, String pkg, CharSequence label)
    {
        String name = context.getSharedPreferences(context.getPackageName() + "_preferences", Context.MODE_PRIVATE).getString(pkg, "");
        if (!name.isEmpty()) {
            return name;
        }

        String retVal = label.toString();
        if (retVal == null || retVal.equals("")) {
            retVal = pkg;
        }
        return retVal;
    }

    public String simplifyName(String name) {
        StringBuilder output = new StringBuilder();
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if ((c >= 'A') && (c <= 'Z')) output.append(c);
            if ((c >= '0') && (c <= '9')) output.append(c);
        }
        return output.toString();
    }

    private void saveRecents() {
        if (mPreferences != null) {
            JSONObject jsonObject = new JSONObject(mRecents);
            String jsonString = jsonObject.toString();
            mPreferences.edit()
                .remove(KEY_RECENTS)
                .putString(KEY_RECENTS, jsonString)
                .apply();
        }
    }

    private void loadRecents() {
        Map<String, Long> outputMap = new HashMap<>();
        try {
            if (mPreferences != null) {
                String jsonString = mPreferences.getString(KEY_RECENTS, (new JSONObject()).toString());
                if (jsonString != null) {
                    JSONObject jsonObject = new JSONObject(jsonString);
                    Iterator<String> keysItr = jsonObject.keys();
                    while (keysItr.hasNext()) {
                        String key = keysItr.next();
                        Long value = jsonObject.getLong(key);
                        outputMap.put(key, value);
                    }
                }
            }
        } catch (JSONException e){
            e.printStackTrace();
        }
        mRecents = outputMap;
    }
}
