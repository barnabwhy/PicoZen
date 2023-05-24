package com.barnabwhy.picozen.Sideload.Providers;

import android.content.SharedPreferences;
import android.util.Log;
import android.view.View;

import com.barnabwhy.picozen.MainActivity;
import com.barnabwhy.picozen.SettingsProvider;
import com.barnabwhy.picozen.Sideload.SideloadItem;
import com.barnabwhy.picozen.Sideload.SideloadItemType;
import com.barnabwhy.picozen.SideloadAdapter;

import org.apache.commons.net.ftp.FTP;
import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPFile;
import org.apache.commons.net.ftp.FTPReply;

import java.io.File;
import java.io.IOException;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.function.Consumer;

public class FTPProvider extends AbstractProvider {
    private FTPClient ftp;
    private String currentPath = "/";
    private String server = "";
    private boolean ready = false;
    private boolean updating = false;
    private Thread ftpThread;

    public FTPProvider(SharedPreferences sharedPreferences, MainActivity mainActivityContext, Runnable notifyCallback) {
        super(sharedPreferences, mainActivityContext, notifyCallback);
        connectFtp(true);
    }

    private void connectFtp(boolean force) {
        ftpThread = new Thread(() -> {
            if(force || (ftp != null && ready && !ftp.isConnected())) {
                try {
                    state = ProviderState.CONNECTING;
                    mainActivityContext.runOnUiThread(notifyCallback);
                    if(ready) {
                        ftp = null;
                    }
                    ready = false;

                    ftp = new FTPClient();

                    int reply;
                    server = sharedPreferences.getString(SettingsProvider.KEY_SIDELOAD_HOST, "");
                    String host = "";
                    String user = "anonymous";
                    String pass = "";
                    if (server.contains("@")) {
                        host = server.split("@")[1];
                        user = server.split("@")[0].split(":")[0];
                        if (server.split("@")[0].split(":").length > 1)
                            pass = server.split("@")[0].split(":")[1];
                    } else {
                        host = server;
                    }

                    Log.i("FTP", "Going to connect to " + host + ".");
                    ftp.connect(host);
                    Log.i("FTP", "Connected to " + host + ".");
                    Log.i("FTP", ftp.getReplyString());

                    reply = ftp.getReplyCode();

                    if (!FTPReply.isPositiveCompletion(reply)) {
                        ftp.disconnect();
                        ready = false;
                        Log.e("FTP", "Server refused connection.");
                    } else {
                        Log.i("FTP", "Server connected.");
                        ftp.login(user, pass);

                        ftp.setBufferSize(1024000);
                        ftp.setFileType(FTP.BINARY_FILE_TYPE);
                        ftp.enterLocalPassiveMode();

                        Log.i("FTP", "Server login complete.");
                        ready = true;
                        state = ProviderState.IDLE;
                        mainActivityContext.runOnUiThread(notifyCallback);
                    }
                } catch (IOException e) {
                    Log.e("FTP Error", e.getLocalizedMessage());
                    e.printStackTrace();
                }

                mainActivityContext.runOnUiThread(this::updateList);
            }
        });
        ftpThread.setPriority(8);
        ftpThread.start();
    }

    protected void finalize() {
        cleanup();
    }

    @Override
    public void cleanup() {
        super.cleanup();
        new Thread(() -> {
            try {
                ftp.abort();
                ftp.logout();
                ftp.disconnect();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }).start();
    }

    public void setCurrentPath(String newPath) {
        if(currentPath.equals(newPath))
            return;

        currentPath = newPath;
        Log.i("Path", newPath);
        updateList();
    }

    public void setHolder(int position, SideloadAdapter.ViewHolder holder) {
        SideloadItem item = itemList.get(position);
        holder.name.setText(item.getName());

        if(item.getType() == SideloadItemType.DIRECTORY) {
            holder.size.setVisibility(View.GONE);
            holder.downloadIcon.setVisibility(View.GONE);
            holder.openFolderIcon.setVisibility(View.VISIBLE);

            holder.layout.setOnClickListener(view -> {
                this.setCurrentPath(item.getPath());
            });
        } else {
            holder.size.setVisibility(View.VISIBLE);
            holder.downloadIcon.setVisibility(View.VISIBLE);
            holder.openFolderIcon.setVisibility(View.GONE);
        }
    }

    public void updateList() {
        getItemsAtPath(currentPath);
    }

    private void getItemsAtPath(String path) {
        if (!sharedPreferences.getString(SettingsProvider.KEY_SIDELOAD_HOST, "").equals(server)) {
            connectFtp(true);
            return;
        }

        state = ProviderState.FETCHING;
        notifyCallback.run();

        ArrayList<SideloadItem> items = new ArrayList<>();
        if(!path.equals("") && !path.equals("/")) {
            String[] pathSegments = path.split("/");
            String backPath = "/" + String.join("/", Arrays.asList(pathSegments).subList(0, pathSegments.length-1));
            items.add(new SideloadItem(SideloadItemType.DIRECTORY, "../", backPath, -1, ""));
        }
        if(ready && !updating) {
            updating = true;
            Thread thread = new Thread(() -> {
                try {
                    if (ftp.isAvailable() && ftp.isConnected()) {
                        if(!path.equals(currentPath))
                            return;
                        Log.i("FTP", "Starting file update ("+path+")");
                        FTPFile[] files = ftp.listFiles(currentPath);
                        if(!path.equals(currentPath))
                            return;

                        for (FTPFile file : files) {
                            if (file.isDirectory()) {
                                DateFormat dateFormatter = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT);
                                String modified = dateFormatter.format(file.getTimestamp().getTime());
                                items.add(new SideloadItem(SideloadItemType.DIRECTORY, file.getName(), currentPath + file.getName() + "/", file.getSize(), modified));
                            }
                        }
                        for (FTPFile file : files) {
                            if (!file.isDirectory()) {
                                DateFormat dateFormatter = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT);
                                String modified = dateFormatter.format(file.getTimestamp().getTime());
                                items.add(new SideloadItem(SideloadItemType.FILE, file.getName(), currentPath + file.getName(), file.getSize(), modified));
                            }
                        }

                        updating = false;
                        state = ProviderState.IDLE;
                        mainActivityContext.runOnUiThread(() -> {
                            Log.i("FTP", "Updated files (" + items.size() + " total)");
                            itemList = items;
                            notifyCallback.run();
                        });
                    } else {
                        updating = false;
                        state = ProviderState.IDLE;
                        mainActivityContext.runOnUiThread(notifyCallback);
                        Log.i("FTP", "Tried to update files but not connected, attempting to connect");
                        connectFtp(false);
                    }
                } catch (Exception e) {
                    Log.e("FTP Error", e.toString());
                    updating = false;
                    state = ProviderState.IDLE;
                    mainActivityContext.runOnUiThread(notifyCallback);
                }
            });
            thread.setPriority(8);
            thread.start();
        }
    }

    @Override
    public void downloadFile(SideloadItem item, Consumer<File> startCallback, Consumer<Long> progressCallback, Consumer<File> completeCallback, Consumer<Exception> errorCallback) {
        errorCallback.accept(new Exception("Provider doesn't support file downloads"));
    }
}
