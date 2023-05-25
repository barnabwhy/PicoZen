package com.barnabwhy.picozen.Sideload.Providers;

import android.content.SharedPreferences;
import android.os.Environment;
import android.util.Log;
import android.view.View;
import android.widget.TextView;

import com.barnabwhy.picozen.MainActivity;
import com.barnabwhy.picozen.R;
import com.barnabwhy.picozen.SettingsProvider;
import com.barnabwhy.picozen.Sideload.SideloadItem;
import com.barnabwhy.picozen.Sideload.SideloadItemType;
import com.barnabwhy.picozen.SideloadAdapter;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.DataInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.function.Consumer;

public class ProxyProvider extends AbstractProvider {
    private static final String FILES_PATH = "https://files-pico.doesnt-like.me";
    private String currentPath = "/";

    public ProxyProvider(SharedPreferences sharedPreferences, MainActivity mainActivityContext, Runnable notifyCallback) {
        super(sharedPreferences, mainActivityContext, notifyCallback);
        state = ProviderState.IDLE;
        updateList();
    }

    public void updateList() {
        Thread thread = new Thread() {
            @Override
            public void run() {
                if (sharedPreferences.getString(SettingsProvider.KEY_SIDELOAD_HOST, "").equals("")) {
                    itemList = new ArrayList<>();
                    state = ProviderState.IDLE;
                } else {
                    itemList = getItemsAtPath(currentPath);
                    Log.i("Items", "Size: "+itemList.size());
                    mainActivityContext.ensureStoragePermissions();
                }

                mainActivityContext.runOnUiThread(notifyCallback);
            }
        };
        thread.start();
    }

    public void setCurrentPath(String newPath) {
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

    private ArrayList<SideloadItem> getItemsAtPath(String path) {
        ArrayList<SideloadItem> items = new ArrayList<>();
        if(!path.equals("") && !path.equals("/")) {
            String[] pathSegments = path.split("/");
            String backPath = String.join("/", Arrays.asList(pathSegments).subList(0, pathSegments.length-1));
            items.add(new SideloadItem(SideloadItemType.DIRECTORY, "../", backPath, -1, ""));
        }
        try {
            state = ProviderState.FETCHING;
            mainActivityContext.runOnUiThread(notifyCallback);

            URL u = new URL(FILES_PATH + path + "?host=" + sharedPreferences.getString(SettingsProvider.KEY_SIDELOAD_HOST, ""));
            InputStream stream = u.openStream();
            int bufferSize = 1024;
            char[] buffer = new char[bufferSize];
            StringBuilder out = new StringBuilder();
            Reader in = new InputStreamReader(stream, StandardCharsets.UTF_8);
            for (int numRead; (numRead = in.read(buffer, 0, buffer.length)) > 0; ) {
                out.append(buffer, 0, numRead);
            }
            JSONObject json = new JSONObject(out.toString());
            JSONArray dirArray = json.getJSONArray("dirs");
            for(int i = 0; i < dirArray.length(); i++) {
                JSONObject dir = dirArray.getJSONObject(i);
                String dirPath = dir.getString("path");
                String name = dir.getString("name");
                long size = dir.getLong("size");
                String date = "";
                if(dir.has("date"))
                    date = dir.getString("date");

                items.add(new SideloadItem(SideloadItemType.DIRECTORY, name, dirPath, size, date));
            }

            JSONArray fileArray = json.getJSONArray("files");
            for(int i = 0; i < fileArray.length(); i++) {
                JSONObject file = fileArray.getJSONObject(i);
                String filePath = file.getString("path");
                String name = file.getString("name");
                long size = file.getLong("size");
                String date = "";
                if(file.has("date"))
                    date = file.getString("date");

                items.add(new SideloadItem(SideloadItemType.FILE, name, filePath, size, date));
            }

            state = ProviderState.IDLE;
        } catch (Exception e) {
            state = ProviderState.ERROR;
            Log.e("Error", e.toString());
        }
        mainActivityContext.runOnUiThread(notifyCallback);
        return items;
    }


    public void downloadFile(SideloadItem item, Consumer<File> startCallback, Consumer<Long> progressCallback, Consumer<File> completeCallback, Consumer<Exception> errorCallback) {
        File file = null;
        try {
            String fileUrl = FILES_PATH + item.getPath() + "?download&host=" + sharedPreferences.getString(SettingsProvider.KEY_SIDELOAD_HOST, "");
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
            if(downloadFileFromUrl(fileUrl, file, progressCallback)) {
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

    protected static boolean downloadFileFromUrl(String url, File outputFile, Consumer<Long> progressCallback) {
        try {
            return saveStream(new URL(url).openStream(), outputFile, progressCallback);
        } catch (Exception e) {
            Log.e("Error", e.toString());
            return false;
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
