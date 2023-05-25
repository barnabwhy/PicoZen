package com.barnabwhy.picozen.Sideload.Providers;

import android.content.SharedPreferences;
import android.os.Environment;
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

import java.io.DataInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
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

    @Override
    public boolean usesAddress() {
        return true;
    }

    public FTPProvider(SharedPreferences sharedPreferences, MainActivity mainActivityContext, Runnable notifyCallback) {
        super(sharedPreferences, mainActivityContext, notifyCallback);
        connectFtp(true);
    }

    private void connectFtp(boolean force) {
        Thread ftpThread = new Thread(() -> {
            if (force || (ftp != null && ready && !ftp.isConnected())) {
                try {
                    state = ProviderState.CONNECTING;
                    mainActivityContext.runOnUiThread(notifyCallback);
                    if (ready) {
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

                        state = ProviderState.ERROR;
                        mainActivityContext.runOnUiThread(notifyCallback);
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

                        mainActivityContext.runOnUiThread(this::updateList);
                    }
                } catch (IOException e) {
                    state = ProviderState.ERROR;
                    mainActivityContext.runOnUiThread(notifyCallback);
                    Log.e("FTP Error", e.getLocalizedMessage());
                    e.printStackTrace();
                }
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

        ArrayList<SideloadItem> items = new ArrayList<>();
        if(!path.equals("") && !path.equals("/")) {
            String[] pathSegments = path.split("/");
            String backPath = "/" + String.join("/", Arrays.asList(pathSegments).subList(0, pathSegments.length-1));
            items.add(new SideloadItem(SideloadItemType.DIRECTORY, "../", backPath, -1, ""));
        }
        if(ready && !updating) {
            state = ProviderState.FETCHING;
            notifyCallback.run();

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
                    state = ProviderState.ERROR;
                    mainActivityContext.runOnUiThread(notifyCallback);
                }
            });
            thread.setPriority(8);
            thread.start();
        }
    }

    @Override
    public void downloadFile(SideloadItem item, Consumer<File> startCallback, Consumer<Long> progressCallback, Consumer<File> completeCallback, Consumer<Exception> errorCallback) {
        File file = null;
        try {
            final File dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
            Files.createDirectories(Paths.get(dir.getAbsolutePath() + "/PicoZen"));
            file = new File(dir.getAbsolutePath() + "/PicoZen/" + item.getName());
            Log.i("File", file.getName());
            int i = 1;
            while(file.exists()) {
                file = new File(dir.getAbsolutePath() + "/PicoZen/" + item.getName().substring(0, item.getName().lastIndexOf(".")) + " (" + i + ")." + item.getName().substring(item.getName().lastIndexOf(".") + 1));
                i++;
                Log.i("File", file.getName());
            }
            startCallback.accept(file);
            Log.i("Download", "Started");

            InputStream is = ftp.retrieveFileStream(item.getPath());
            if(saveStream(is, file, progressCallback)) {
                completeCallback.accept(file);
            } else {
                file.delete();
                errorCallback.accept(null);
            }
        } catch(Exception e) {
            Log.e("Error", e.toString());
            if(file != null && file.exists()) {
                file.delete();
            }
            errorCallback.accept(e);
        }
    }

    protected static boolean saveStream(InputStream is, File outputFile, Consumer<Long> progressCallback) {
        try {
            DataInputStream dis = new DataInputStream(is);

            long processed = 0;
            int length;
            byte[] buffer = new byte[65536];
            FileOutputStream fos = new FileOutputStream(outputFile);

            while ((length = dis.read(buffer)) > 0) {
                if(!outputFile.canWrite()) {
                    fos.flush();
                    fos.close();
                    is.close();
                    dis.close();
                    return false;
                }

                fos.write(buffer, 0, length);
                fos.flush();
                processed += length;
                progressCallback.accept(processed);
            }
            fos.flush();
            fos.close();
            is.close();
            dis.close();

            return true;
        } catch (Exception e) {
            Log.e("Error", e.toString());
            return false;
        }
    }
}
