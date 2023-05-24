package com.barnabwhy.picozen.Sideload.Providers;

import android.content.SharedPreferences;
import android.util.Log;
import android.view.View;

import com.barnabwhy.picozen.MainActivity;
import com.barnabwhy.picozen.Sideload.SideloadItem;
import com.barnabwhy.picozen.Sideload.SideloadItemType;
import com.barnabwhy.picozen.SideloadAdapter;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

abstract public class AbstractProvider {

    protected final SharedPreferences sharedPreferences;
    protected final MainActivity mainActivityContext;
    protected List<SideloadItem> itemList;
    protected Runnable notifyCallback;
    protected boolean disabled = false;

    public enum ProviderState {
        CONNECTING,
        IDLE,
        FETCHING,
    }

    ProviderState state;

    public AbstractProvider(SharedPreferences sharedPreferences, MainActivity mainActivityContext, Runnable notifyCallback) {
        itemList = new ArrayList<>();
        this.sharedPreferences = sharedPreferences;
        this.mainActivityContext = mainActivityContext;
        this.notifyCallback = () -> {
            if(!disabled)
                notifyCallback.run();
        };
        state = ProviderState.CONNECTING;
    }

    public ProviderState getState() {
        return state;
    }

    public List<SideloadItem> getList() {
        return itemList;
    }

    public SideloadItem getItem(int position) {
        return itemList.get(position);
    }

    public int getCount() {
        return itemList.size();
    }

    public SideloadItem getByPath(String path) {
        for (SideloadItem item : itemList) {
            if(item.getPath().equals(path)) {
                return item;
            }
        }
        return null;
    }


    public abstract void setHolder(int position, SideloadAdapter.ViewHolder holder);
    abstract public void updateList();
    public abstract void downloadFile(SideloadItem item, Consumer<File> startCallback, Consumer<Long> progressCallback, Consumer<File> completeCallback, Consumer<Exception> errorCallback);
    public void cleanup() {
        Log.i("Provider", "Cleaning up provider");
        disabled = true;
    };
}
