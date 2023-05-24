package com.barnabwhy.picozen.Sideload.Providers;

import android.content.SharedPreferences;

import com.barnabwhy.picozen.MainActivity;
import com.barnabwhy.picozen.Sideload.SideloadItem;
import com.barnabwhy.picozen.SideloadAdapter;

import java.io.File;
import java.util.function.Consumer;

public class EmptyProvider extends AbstractProvider {

    public EmptyProvider(SharedPreferences sharedPreferences, MainActivity mainActivityContext, Runnable notifyCallback) {
        super(sharedPreferences, mainActivityContext, notifyCallback);
    }

    @Override
    public void setHolder(int position, SideloadAdapter.ViewHolder holder) {
        // Do nothing
    }

    public void updateList() {
        // Do nothing
        notifyCallback.run();
    }

    @Override
    public void downloadFile(SideloadItem item, Consumer<File> startCallback, Consumer<Long> progressCallback, Consumer<File> completeCallback, Consumer<Exception> errorCallback) {
        errorCallback.accept(new Exception("Empty provider doesn't support file downloads"));
    }
}
