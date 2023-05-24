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
import org.apache.commons.net.ftp.FTPClientConfig;
import org.apache.commons.net.ftp.FTPFile;
import org.apache.commons.net.ftp.FTPReply;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.function.Consumer;

public class FTPProvider extends AbstractProvider {
    private final FTPClient ftp;
    private String currentPath = "";
    private String server = "";
    private boolean connecting = false;
    private boolean updating = false;

    public FTPProvider(SharedPreferences sharedPreferences, MainActivity mainActivityContext, Runnable notifyCallback) {
        super(sharedPreferences, mainActivityContext, notifyCallback);
        ftp = new FTPClient();
        connectFtp();
    }

    private void connectFtp() {
        new Thread(() -> {
            if((!connecting && !ftp.isConnected()) || !sharedPreferences.getString(SettingsProvider.KEY_SIDELOAD_HOST, "").equals(server)) {
                try {
                    if(connecting || ftp.isConnected()) {
                        try {
                            ftp.logout();
                            ftp.disconnect();
                            ftp.abort();
                        } catch(Exception e) { }
                    }

                    connecting = true;

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
                        Log.e("FTP", "Server refused connection.");
                    } else {
                        Log.i("FTP", "Server connected.");
                        ftp.login(user, pass);

                        ftp.setBufferSize(1024000);
                        ftp.setFileType(FTP.BINARY_FILE_TYPE);
                        ftp.enterLocalPassiveMode();

                        Log.i("FTP", "Server login complete.");
                    }
                } catch (IOException e) {
                    Log.e("FTP Error", e.getLocalizedMessage());
                    e.printStackTrace();
                }

                connecting = false;
                mainActivityContext.runOnUiThread(this::updateList);
            }
        }).start();
    }

    protected void finalize() {
        new Thread(() -> {
            try {
                ftp.logout();
                ftp.disconnect();
                ftp.abort();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }).start();
    }

    public void setCurrentPath(String newPath) {
        currentPath = newPath;
        Log.i("Path", newPath);
        updating = false;
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
        getItemsAtPath(currentPath, notifyCallback);
    }

    private void getItemsAtPath(String path, Runnable completeCallback) {
        ArrayList<SideloadItem> items = new ArrayList<>();
        if(!path.equals("") && !path.equals("/")) {
            String[] pathSegments = path.split("/");
            String backPath = String.join("/", Arrays.asList(pathSegments).subList(0, pathSegments.length-1));
            items.add(new SideloadItem(SideloadItemType.DIRECTORY, "../", backPath, -1, ""));
        }
        if(!updating) {
            updating = true;
            new Thread(() -> {
                try {
                    if (ftp.isConnected() && ftp.isAvailable()) {
                        Log.i("FTP", "Starting file update");
                        FTPFile[] files = ftp.listFiles(currentPath);
                        if(!path.equals(currentPath))
                            return;

                        for (FTPFile file : files) {
                            if (file.isDirectory()) {
                                DateFormat dateFormatter = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT);
                                String modified = dateFormatter.format(file.getTimestamp().getTime());
                                items.add(new SideloadItem(SideloadItemType.DIRECTORY, file.getName(), currentPath + "/" + file.getName(), file.getSize(), modified));
                            }
                        }
                        for (FTPFile file : files) {
                            if (!file.isDirectory()) {
                                DateFormat dateFormatter = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT);
                                String modified = dateFormatter.format(file.getTimestamp().getTime());
                                items.add(new SideloadItem(SideloadItemType.FILE, file.getName(), currentPath + "/" + file.getName(), file.getSize(), modified));
                            }
                        }

                        mainActivityContext.runOnUiThread(() -> {
                            Log.i("FTP", "Updated files (" + items.size() + " total)");
                            itemList = items;
                            completeCallback.run();
                        });
                    } else {
                        Log.i("FTP", "Tried to update files but not connected, attempting to connect");
                        connectFtp();
                    }
                } catch (Exception e) {
                    Log.e("FTP Error", e.toString());
                }
                updating = false;
            }).start();
        }
    }

    @Override
    public void downloadFile(SideloadItem item, Consumer<File> startCallback, Consumer<Long> progressCallback, Consumer<File> completeCallback, Consumer<Exception> errorCallback) {
        errorCallback.accept(new Exception("Empty provider doesn't support file downloads"));
    }
}
