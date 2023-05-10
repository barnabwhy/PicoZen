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
    public static final String KEY_FTP_HOST = "KEY_FTP_HOST";

    private final String KEY_APP_GROUPS = "prefAppGroups";
    private final String KEY_APP_LIST = "prefAppList";
    private final String KEY_SELECTED_GROUPS = "prefSelectedGroups";
    private final String SEPARATOR = "#@%";

    private static SettingsProvider instance;
    private static Context context;

    public static synchronized SettingsProvider getInstance (Context context)
    {
        if (SettingsProvider.instance == null) {
            SettingsProvider.instance = new SettingsProvider(context);
        }
        return SettingsProvider.instance;
    }

    //storage
    private final SharedPreferences mPreferences;
    private Map<String, String> mAppList = new HashMap<>();
    private Set<String> mAppGroups = new HashSet<>();
    private Set<String> mSelectedGroups = new HashSet<>();
    private Map<String, Long> mRecents = new HashMap<>();
    Set<String> def = new HashSet<>();
    public static final Map<String, Intent> launchIntents = new HashMap<>();
    public static final Map<String, Long> installDates = new HashMap<>();

    private SettingsProvider(Context context) {
        mPreferences = context.getSharedPreferences(context.getPackageName() + "_preferences", Context.MODE_PRIVATE);
        SettingsProvider.context = context;
    }

    public void setAppList(Map<String, String> appList)
    {
        mAppList = appList;
        storeValues();
    }

    public Map<String, String> getAppList()
    {
        readValues();
        return mAppList;
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

    public boolean hasMetadata(ApplicationInfo app, String metadata)
    {
        if (app.metaData != null)
        {
            for (String key : app.metaData.keySet())
            {
                if (metadata.compareTo(key) == 0)
                {
                    return true;
                }
            }
        }
        return false;
    }

    public void setAppGroups(Set<String> appGroups)
    {
        mAppGroups = appGroups;
        storeValues();
    }

    public Set<String> getAppGroups()
    {
        readValues();
        return mAppGroups;
    }

    public void setSelectedGroups(Set<String> appGroups)
    {
        mSelectedGroups = appGroups;
        storeValues();
    }

    public Set<String> getSelectedGroups()
    {
        readValues();
        return mSelectedGroups;
    }

    public ArrayList<String> getAppGroupsSorted(boolean selected)
    {
        readValues();
        ArrayList<String> output = new ArrayList<>(selected ? mSelectedGroups : mAppGroups);
        Collections.sort(output, (a, b) -> {
            String name1 = simplifyName(a.toUpperCase());
            String name2 = simplifyName(b.toUpperCase());
            return name1.compareTo(name2);
        });
        return output;
    }

    public void resetGroups(){
        def = new HashSet<>();
        SharedPreferences.Editor editor = mPreferences.edit();
        editor.remove(KEY_APP_GROUPS);
        editor.remove(KEY_SELECTED_GROUPS);
        editor.remove(KEY_APP_LIST);
        editor.apply();
        readValues();
    }

    private synchronized void readValues()
    {
        try
        {
            mAppList.clear();
            Set<String> apps = new HashSet<>();
            apps = mPreferences.getStringSet(KEY_APP_LIST, apps);
            for (String s : apps) {
                String[] data = s.split(SEPARATOR);
                mAppList.put(data[0], data[1]);
            }
        }
        catch(Exception e)
        {
            e.printStackTrace();
        }
    }

    private synchronized void storeValues()
    {
        try
        {
            SharedPreferences.Editor editor = mPreferences.edit();
            editor.putStringSet(KEY_APP_GROUPS, mAppGroups);
            editor.putStringSet(KEY_SELECTED_GROUPS, mSelectedGroups);

            Set<String> apps = new HashSet<>();
            for (String pkg : mAppList.keySet()) {
                apps.add(pkg + SEPARATOR + mAppList.get(pkg));
            }
            editor.putStringSet(KEY_APP_LIST, apps);

            editor.apply();
        }
        catch(Exception e)
        {
            e.printStackTrace();
        }
    }

    public String addGroup() {
        String name = "XXX";
        List<String> groups = getAppGroupsSorted(false);
        if (groups.contains(name)) {
            int index = 1;
            while (groups.contains(name + index)) {
                index++;
            }
            name = name + index;
        }
        groups.add(name);
        setAppGroups(new HashSet<>(groups));
        return name;
    }

    public void selectGroup(String name) {
        Set<String> selectFirst = new HashSet<>();
        selectFirst.add(name);
        setSelectedGroups(selectFirst);
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

    public void setAppDisplayName(ApplicationInfo appInfo, String newName)
    {
        SharedPreferences.Editor editor = mPreferences.edit();
        editor.putString(appInfo.packageName, newName);
        editor.apply();
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

    public boolean isPlatformEnabled(String key) {
        return mPreferences.getBoolean(key, true);
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
