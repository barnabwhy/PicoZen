package com.barnabwhy.picozen.Sideload.Providers;

import android.content.SharedPreferences;
import android.util.Log;
import android.view.View;

import com.barnabwhy.picozen.MainActivity;
import com.barnabwhy.picozen.SettingsProvider;
import com.barnabwhy.picozen.Sideload.SideloadItem;
import com.barnabwhy.picozen.Sideload.SideloadItemType;
import com.barnabwhy.picozen.SideloadAdapter;
import com.hierynomus.msfscc.fileinformation.FileIdBothDirectoryInformation;
import com.hierynomus.smbj.SMBClient;
import com.hierynomus.smbj.auth.AuthenticationContext;
import com.hierynomus.smbj.connection.Connection;
import com.hierynomus.smbj.session.Session;
import com.hierynomus.smbj.share.DiskShare;

import java.io.File;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.function.Consumer;

public class SMBProvider extends AbstractProvider {
    private String currentPath = "/";
    private String server = "";
    private boolean ready = false;
    private SMBClient client;
    private Session session;
    private DiskShare share;

    public SMBProvider(SharedPreferences sharedPreferences, MainActivity mainActivityContext, Runnable notifyCallback) {
        super(sharedPreferences, mainActivityContext, notifyCallback);
        connect();
    }

    private void connect() {
        new Thread(() -> {
            try {
                ready = false;
                state = ProviderState.CONNECTING;
                mainActivityContext.runOnUiThread(notifyCallback);

                client = new SMBClient();

                server = sharedPreferences.getString(SettingsProvider.KEY_SIDELOAD_HOST, "");
                String host = "";
                String user = "";
                String pass = "";
                String shareName = "";
                if (server.contains("@")) {
                    host = server.split("@")[1].split("/")[0];
                    user = server.split("@")[0].split(":")[0];
                    if (server.split("@")[0].split(":").length > 1)
                        pass = server.split("@")[0].split(":")[1];

                    shareName = String.join("/", Arrays.copyOfRange(server.split("@")[1].split("/"), 1, server.split("@")[1].split("/").length));
                } else {
                    host = server.split("/")[0];
                    shareName = String.join("/", Arrays.copyOfRange(server.split("/"), 1, server.split("/").length));
                }

                Log.i("SMB", "Connecting to " + host);
                Connection connection = client.connect(host);
                AuthenticationContext ac = new AuthenticationContext(user, pass.toCharArray(), null);
                Log.i("SMB", "Authenticating as " + user);
                session = connection.authenticate(ac);
                Log.i("SMB", "Connecting to share " + shareName);
                share = (DiskShare) session.connectShare(shareName);

                Log.i("SMB", "Ready");
                ready = true;
                state = ProviderState.IDLE;
                mainActivityContext.runOnUiThread(notifyCallback);
            } catch (Exception e) {
                state = ProviderState.IDLE;
                mainActivityContext.runOnUiThread(notifyCallback);
                e.printStackTrace();
            }

            updateList();
        }).start();
    }

    public void setCurrentPath(String newPath) {
        if(currentPath.equals(newPath))
            return;

        currentPath = newPath;
        Log.i("Path", newPath);
        updateList();
    }

    @Override
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
        new Thread(() -> {
            state = ProviderState.FETCHING;
            mainActivityContext.runOnUiThread(notifyCallback);

            ArrayList<SideloadItem> items = new ArrayList<>();
            if(ready && session != null) {
                if(!server.equals(sharedPreferences.getString(SettingsProvider.KEY_SIDELOAD_HOST, ""))) {
                    connect();
                    return;
                }
                if(!currentPath.equals("") && !currentPath.equals("/")) {
                    String[] pathSegments = currentPath.split("/");
                    String backPath = "/" + String.join("/", Arrays.asList(pathSegments).subList(0, pathSegments.length-1));
                    items.add(new SideloadItem(SideloadItemType.DIRECTORY, "../", backPath, -1, ""));
                }
                try {
                    List<FileIdBothDirectoryInformation> files = share.list(currentPath);
                    for (FileIdBothDirectoryInformation f : files) {
                        if((f.getFileAttributes() & 0x10) > 0) { // if is directory
                            if(f.getFileName().equals(".") || f.getFileName().equals(".."))
                                continue;

                            DateFormat dateFormatter = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT);
                            String modified = dateFormatter.format(f.getChangeTime().toDate());
                            items.add(new SideloadItem(SideloadItemType.DIRECTORY, f.getFileName(), currentPath + f.getFileName(), f.getAllocationSize(), modified));
                        }
                    }
                    for (FileIdBothDirectoryInformation f : files) {
                        if((f.getFileAttributes() & 0x10) < 1) { // if is file
                            DateFormat dateFormatter = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT);
                            String modified = dateFormatter.format(f.getChangeTime().toDate());
                            items.add(new SideloadItem(SideloadItemType.FILE, f.getFileName(), currentPath + f.getFileName(), f.getAllocationSize(), modified));
                        }
                    }
                } catch(Exception e) {
                    e.printStackTrace();
                }
            }
            itemList = items;
            state = ProviderState.IDLE;
            mainActivityContext.runOnUiThread(notifyCallback);
        }).start();
    }

    @Override
    public void downloadFile(SideloadItem item, Consumer<File> startCallback, Consumer<Long> progressCallback, Consumer<File> completeCallback, Consumer<Exception> errorCallback) {
        errorCallback.accept(new Exception("Provider doesn't support downloads"));
    }
}
