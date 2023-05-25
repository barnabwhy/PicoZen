package com.barnabwhy.picozen.Sideload.Providers;

import android.content.SharedPreferences;

import com.barnabwhy.picozen.MainActivity;
import com.barnabwhy.picozen.Sideload.SideloadItem;
import com.barnabwhy.picozen.SideloadAdapter;

import java.io.File;
import java.util.function.Consumer;

public class EmptyProvider extends AbstractProvider {
    @Override
    public boolean usesAddress() {
        return false;
    }

    public EmptyProvider(SharedPreferences sharedPreferences, MainActivity mainActivityContext, Runnable notifyCallback) {
        super(sharedPreferences, mainActivityContext, notifyCallback);
        state = ProviderState.IDLE;
        updateList();
    }

    @Override
    public void setHolder(int position, SideloadAdapter.ViewHolder holder) {
        // Do nothing
    }

    public void updateList() {
        // Do nothing
        mainActivityContext.runOnUiThread(notifyCallback);
    }

    @Override
    public void downloadFile(SideloadItem item, Consumer<File> startCallback, Consumer<Long> progressCallback, Consumer<File> completeCallback, Consumer<Exception> errorCallback) {
        errorCallback.accept(new Exception("Provider doesn't support downloads"));
    }
}
