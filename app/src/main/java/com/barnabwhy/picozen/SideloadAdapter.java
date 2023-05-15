package com.barnabwhy.picozen;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Environment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
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
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class SideloadAdapter extends BaseAdapter {
    private static final String FILES_PATH = "https://files-pico.doesnt-like.me";
    private final SharedPreferences sharedPreferences;
    private final MainActivity mainActivityContext;

    private ArrayList<DirItem> dirList;
    private ArrayList<DirItem> fileList;

    private String currentPath = "/";

    private DownloadInfo currentDownload;

    private static class ViewHolder {
        RelativeLayout layout;
        TextView name;
        TextView modified;
        TextView size;
        ImageView downloadIcon;
        ImageView openFolderIcon;
    }
    private static class DownloadInfo {
        DirItem dirItem;
        long downloadedBytes;
    }
    private static class DirItem {
        String path;
        String name;
        long size;
        String modifiedAt;
        ViewHolder holder;
        public DirItem(String path, String name, long size, String modifiedAt) {
            this.path = path;
            this.name = name;
            this.size = size;
            this.modifiedAt = modifiedAt;
        }
    }

    public SideloadAdapter(MainActivity context) {
        mainActivityContext = context;

        sharedPreferences = mainActivityContext.getSharedPreferences(mainActivityContext.getPackageName() + "_preferences", Context.MODE_PRIVATE);

        dirList = new ArrayList<>();
        fileList = new ArrayList<>();

        updateCurrentDirectory();
    }

    public void updateCurrentDirectory() {
        Thread thread = new Thread() {
            @Override
            public void run() {
                if (sharedPreferences.getString(SettingsProvider.KEY_FTP_HOST, "").equals("")) {
                    dirList = new ArrayList<>();
                    fileList = new ArrayList<>();
                } else {
                    dirList = getDirsAtPath(currentPath);
                    fileList = getFilesAtPath(currentPath);
                }

                mainActivityContext.runOnUiThread(() -> {
                    if (dirList.size() == 0 && fileList.size() == 0) {
                        mainActivityContext.findViewById(R.id.sideload_grid).setVisibility(View.GONE);
                        mainActivityContext.findViewById(R.id.sideload_grid_empty).setVisibility(View.VISIBLE);

                        if (sharedPreferences.getString(SettingsProvider.KEY_FTP_HOST, "").equals("")) {
                            ((TextView)mainActivityContext.findViewById(R.id.sideload_grid_empty)).setText(R.string.no_sideload_ftp_server);
                        } else {
                            ((TextView)mainActivityContext.findViewById(R.id.sideload_grid_empty)).setText(R.string.fetch_files_error);
                        }
                    } else {
                        mainActivityContext.findViewById(R.id.sideload_grid).setVisibility(View.VISIBLE);
                        mainActivityContext.findViewById(R.id.sideload_grid_empty).setVisibility(View.GONE);
                    }

                    notifyDataSetChanged();
                });
            }
        };
        thread.start();
    }

    public void setCurrentPath(String newPath) {
        currentPath = newPath;
        Log.i("Path", newPath);
        updateCurrentDirectory();
    }

    public ArrayList<DirItem> getDirsAtPath(String path) {
        ArrayList<DirItem> dirs = new ArrayList<>();
        if(!path.equals("") && !path.equals("/")) {
            String[] pathSegments = path.split("/");
            String backPath = String.join("/", Arrays.asList(pathSegments).subList(0, pathSegments.length-1));
            dirs.add(new DirItem(backPath, "../", -1, ""));
        }
        try {
            URL u = new URL(FILES_PATH + path + "?host=" + sharedPreferences.getString(SettingsProvider.KEY_FTP_HOST, ""));
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
                dirs.add(new DirItem(dirPath, name, size, date));
            }
        } catch (Exception e) {
            Log.e("Error", e.toString());
        }
        return dirs;
    }
    public ArrayList<DirItem> getFilesAtPath(String path) {
        ArrayList<DirItem> files = new ArrayList<>();
        try {
            URL u = new URL(FILES_PATH + path + "?host=" + sharedPreferences.getString(SettingsProvider.KEY_FTP_HOST, ""));
            InputStream stream = u.openStream();
            int bufferSize = 1024;
            char[] buffer = new char[bufferSize];
            StringBuilder out = new StringBuilder();
            Reader in = new InputStreamReader(stream, StandardCharsets.UTF_8);
            for (int numRead; (numRead = in.read(buffer, 0, buffer.length)) > 0; ) {
                out.append(buffer, 0, numRead);
            }
            JSONObject json = new JSONObject(out.toString());
            JSONArray dirArray = json.getJSONArray("files");
            for(int i = 0; i < dirArray.length(); i++) {
                JSONObject file = dirArray.getJSONObject(i);
                String filePath = file.getString("path");
                String name = file.getString("name");
                long size = file.getLong("size");
                String date = "";
                if(file.has("date"))
                    date = file.getString("date");
                files.add(new DirItem(filePath, name, size, date));
            }
        } catch (Exception e) {
            Log.e("Error", e.toString());
        }
        return files;
    }

    @Override
    public int getCount() {
        return dirList.size() + fileList.size();
    }

    @Override
    public DirItem getItem(int position) {
        if(position < dirList.size()) {
            return dirList.get(position);
        } else {
            return fileList.get(position - dirList.size());
        }
    }

    public String getType(int position) {
        if(position < dirList.size()) {
            return "dir";
        } else {
            return "file";
        }
    }

    public DirItem getByPath(String path) {
        for (DirItem item : dirList) {
            if(item.path.equals(path)) {
                return item;
            }
        }
        for (DirItem item : fileList) {
            if(item.path.equals(path)) {
                return item;
            }
        }
        return null;
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @SuppressLint("DefaultLocale")
    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        ViewHolder holder;

        final DirItem current = getItem(position);
        LayoutInflater inflater = (LayoutInflater) mainActivityContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);

        if (convertView == null) {
            // Create a new ViewHolder and inflate the layout
            convertView = inflater.inflate(R.layout.lv_dir_item, parent, false);
            holder = new ViewHolder();
            holder.layout = convertView.findViewById(R.id.layout);
            holder.name = convertView.findViewById(R.id.name);
            holder.modified = convertView.findViewById(R.id.modified);
            holder.size = convertView.findViewById(R.id.size);
            holder.downloadIcon = convertView.findViewById(R.id.ic_download);
            holder.openFolderIcon = convertView.findViewById(R.id.ic_open_folder);
            convertView.setTag(holder);

            // Set clipToOutline to true on imageView (Workaround for bug)
            holder.layout.setClipToOutline(true);
        } else {
            // ViewHolder already exists, reuse it
            holder = (ViewHolder) convertView.getTag();
        }

        current.holder = holder;

        holder.name.setText(current.name);

        if(getType(position).equals("dir")) {
            holder.size.setVisibility(View.GONE);
            holder.downloadIcon.setVisibility(View.GONE);
            holder.openFolderIcon.setVisibility(View.VISIBLE);

            holder.layout.setOnClickListener(view -> {
                setCurrentPath(current.path);
            });
        } else {
            holder.size.setVisibility(View.VISIBLE);
            holder.downloadIcon.setVisibility(View.VISIBLE);
            holder.openFolderIcon.setVisibility(View.GONE);

            holder.layout.setOnClickListener(view -> {
                Thread thread = new Thread() {
                    @Override
                    public void run() {
                        checkStoragePermissions();

                        if(currentDownload != null)
                            return;

                        currentDownload = new DownloadInfo();
                        currentDownload.dirItem = current;
                        AtomicReference<AlertDialog> dialog = new AtomicReference<>();
                        mainActivityContext.runOnUiThread(() -> {
                            dialog.set(showDownloadDialog());
                        });

                        AtomicBoolean cancelled = new AtomicBoolean(false);
                        AtomicLong lastProgressTime = new AtomicLong();
                        downloadFile(current, (file) -> {
                            mainActivityContext.runOnUiThread(() -> {
                                ((TextView)dialog.get().findViewById(R.id.file_name)).setText(file.getName());
                                dialog.get().findViewById(R.id.cancel_btn).setVisibility(View.VISIBLE);
                                dialog.get().findViewById(R.id.cancel_btn).setOnClickListener(view -> {
                                    cancelled.set(true);
                                    file.delete();
                                });
                            });
                        }, (progress) -> {
                            if(progress != current.size && System.currentTimeMillis() - lastProgressTime.get() < 100)
                                return;

                            lastProgressTime.set(System.currentTimeMillis());

                            mainActivityContext.runOnUiThread(() -> {
                                currentDownload.downloadedBytes = progress;
                                ((TextView)dialog.get().findViewById(R.id.progress_text)).setText(String.format("%s / %s (%01.2f%%)", bytesReadable(currentDownload.downloadedBytes), bytesReadable(current.size), ((double)progress / current.size) * 100.0));

                                View progressBar = dialog.get().findViewById(R.id.progress_bar);
                                ViewGroup.LayoutParams params = progressBar.getLayoutParams();
                                params.width = (int) (((View)progressBar.getParent()).getWidth() * ((double)progress / current.size));
                                params.height = ((View)progressBar.getParent()).getHeight();
                                progressBar.setLayoutParams(params);
                                progressBar.setVisibility(View.VISIBLE);
                            });
                        }, outFile -> {
                            mainActivityContext.runOnUiThread(() -> {
                                class CompleteData {
                                    Exception error;
                                    File installableFile;
                                    File obbDir;
                                };

                                Consumer<CompleteData> onComplete = (data) -> {
                                    if (data.error != null) {
                                        ((TextView)dialog.get().findViewById(R.id.progress_text)).setText(String.format(mainActivityContext.getResources().getString(R.string.an_error_occurred), data.error.getLocalizedMessage()));
                                        dialog.get().findViewById(R.id.progress_bar).setVisibility(View.GONE);
                                    } else {
                                        ((TextView)dialog.get().findViewById(R.id.progress_text)).setText(R.string.download_complete);
                                        if (data.installableFile != null) {
                                            dialog.get().findViewById(R.id.install_btn).setVisibility(View.VISIBLE);
                                            File finalInstallableFile = data.installableFile;
                                            File finalObbDir = data.obbDir;
                                            dialog.get().findViewById(R.id.install_btn).setOnClickListener(view -> {
                                                if (finalObbDir != null) {
                                                    File obbDest = new File(Environment.getExternalStorageDirectory().getPath() + "/Android/obb/" + finalObbDir.getName());
                                                    finalObbDir.renameTo(obbDest);
                                                }
                                                Intent intent = new Intent(Intent.ACTION_INSTALL_PACKAGE);
                                                intent.setData(FileProvider.getUriForFile(mainActivityContext, BuildConfig.APPLICATION_ID + ".provider", finalInstallableFile));
                                                intent.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                                                mainActivityContext.startActivity(intent);
                                            });
                                        }
                                    }

                                    dialog.get().setCancelable(true);
                                    dialog.get().findViewById(R.id.cancel_btn).setVisibility(View.GONE);
                                    dialog.get().findViewById(R.id.dismiss_btn).setVisibility(View.VISIBLE);
                                    dialog.get().findViewById(R.id.dismiss_btn).setOnClickListener(view -> {
                                        dialog.get().dismiss();
                                    });
                                    currentDownload = null;
                                };

                                if (getFileExtension(outFile).equals(".zip")) {
                                    ((TextView) dialog.get().findViewById(R.id.progress_text)).setText(R.string.extracting_zip);
                                    Thread zipThread = new Thread(() -> {
                                        Exception error = null;
                                        File installableFile = null;
                                        File obbDir = null;

                                        try {
                                            File dir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).getAbsolutePath() + "/PicoZen/" + outFile.getName().substring(0, outFile.getName().lastIndexOf(".")));
                                            unzip(outFile, dir);

                                            File[] files = dir.listFiles();
                                            for (File file : Objects.requireNonNull(files)) {
                                                if (getFileExtension(file).equals(".apk")) {
                                                    installableFile = file;
                                                    break;
                                                }
                                            }
                                            if (installableFile != null) {
                                                for (File file : Objects.requireNonNull(files)) {
                                                    if (file.getName().equals(getPackageName(mainActivityContext, installableFile))) {
                                                        obbDir = file;
                                                        break;
                                                    }
                                                }
                                            }
                                        } catch (Exception e) {
                                            e.printStackTrace();
                                            error = e;
                                        }

                                        CompleteData completeData = new CompleteData();
                                        completeData.error = error;
                                        completeData.installableFile = installableFile;
                                        completeData.obbDir = obbDir;

                                        mainActivityContext.runOnUiThread(() -> {
                                            onComplete.accept(completeData);
                                        });
                                    });
                                    zipThread.start();
                                } else {
                                    File installableFile = null;
                                    if (getFileExtension(outFile).equals(".apk")) {
                                        installableFile = outFile;
                                    }

                                    CompleteData completeData = new CompleteData();
                                    completeData.error = null;
                                    completeData.installableFile = installableFile;
                                    completeData.obbDir = null;
                                    onComplete.accept(completeData);
                                }
                            });
                        }, e -> {
                            mainActivityContext.runOnUiThread(() -> {
                                if(cancelled.get()) {
                                    ((TextView)dialog.get().findViewById(R.id.progress_text)).setText(R.string.download_cancelled);
                                } else {
                                    ((TextView)dialog.get().findViewById(R.id.progress_text)).setText(String.format(mainActivityContext.getResources().getString(R.string.an_error_occurred), e.getLocalizedMessage()));
                                }
                                dialog.get().findViewById(R.id.progress_bar).setVisibility(View.GONE);

                                dialog.get().setCancelable(true);
                                dialog.get().findViewById(R.id.cancel_btn).setVisibility(View.GONE);
                                dialog.get().findViewById(R.id.dismiss_btn).setVisibility(View.VISIBLE);
                                dialog.get().findViewById(R.id.dismiss_btn).setOnClickListener(view -> {
                                    dialog.get().dismiss();
                                });
                                currentDownload = null;
                            });
                        });
                    }
                };
                thread.start();
            });
        }

        holder.size.setText(bytesReadable(current.size));
        holder.modified.setText(current.modifiedAt);

        return convertView;
    }

    private void downloadFile(DirItem item, Consumer<File> startCallback, Consumer<Long> progressCallback, Consumer<File> completeCallback, Consumer<Exception> errorCallback) {
        File file = null;
        try {
            String fileUrl = FILES_PATH + item.path + "?download&host=" + sharedPreferences.getString(SettingsProvider.KEY_FTP_HOST, "");
            final File dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
            Files.createDirectories(Paths.get(dir.getAbsolutePath() + "/PicoZen"));
            file = new File(dir.getAbsolutePath() + "/PicoZen/" + item.name);
            Log.i("File", file.getName());
            int i = 1;
            while(file.exists()) {
                file = new File(dir.getAbsolutePath() + "/PicoZen/" + item.name.substring(0, item.name.lastIndexOf(".")) + " (" + i + ")." + item.name.substring(item.name.lastIndexOf(".") + 1));
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

    private void checkStoragePermissions() {
        Log.i("Permissions", "Checking Storage Permissions");

        int writePermissionCode = ContextCompat.checkSelfPermission(mainActivityContext, Manifest.permission.WRITE_EXTERNAL_STORAGE);//get current write permission
        int readPermissionCode = ContextCompat.checkSelfPermission(mainActivityContext, Manifest.permission.READ_EXTERNAL_STORAGE);//ge current read permission
        Log.i("Permissions", "Fetching Read & Write Codes: " + readPermissionCode + "/" + writePermissionCode);

        //if permissions to read and write to external storage is not granted
        if (writePermissionCode != PackageManager.PERMISSION_GRANTED || readPermissionCode != PackageManager.PERMISSION_GRANTED) {
            //request read and write permissions
            ActivityCompat.requestPermissions(mainActivityContext, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, PackageManager.PERMISSION_GRANTED);
            ActivityCompat.requestPermissions(mainActivityContext, new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, PackageManager.PERMISSION_GRANTED);
            Log.i("Permissions", "Asking For Storage Permissions");
        } else {//else: if permissions to read and write is already granted
//            permissionsGranted = true;//set permissions granted bool to true
        }
    }

    private AlertDialog showDownloadDialog() {
        View dialogOverlay = mainActivityContext.findViewById(R.id.dialog_overlay);
        dialogOverlay.setVisibility(View.VISIBLE);

        AlertDialog.Builder builder = new AlertDialog.Builder(mainActivityContext, R.style.CustomDialog);
        builder.setView(R.layout.dialog_download);
        AlertDialog dialog = builder.create();

        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setCancelable(false);
        dialog.show();

        WindowManager.LayoutParams lp = new WindowManager.LayoutParams();
        lp.copyFrom(dialog.getWindow().getAttributes());
        lp.width = 560;
        lp.height = 320;

        dialog.getWindow().setAttributes(lp);
        dialog.findViewById(R.id.layout).requestLayout();
        dialog.getWindow().setBackgroundDrawableResource(R.drawable.bg_dialog);
        ((TextView)dialog.findViewById(R.id.file_name)).setText(currentDownload.dirItem.name);
        ((TextView)dialog.findViewById(R.id.progress_text)).setText(String.format("0 B / %s (0%%)", bytesReadable(currentDownload.dirItem.size)));
        ((View)dialog.findViewById(R.id.progress_bar).getParent()).setClipToOutline(true);

        dialog.setOnDismissListener(d -> {
            dialogOverlay.setVisibility(View.GONE);
        });

        return dialog;
    }

    @SuppressLint("DefaultLocale")
    private static String bytesReadable(long bytes) {
        final String[] byteTypes = { "KB", "MB", "GB", "TB", "PB" };
        double size = bytes;
        String currentByteType = "B";

        for (String byteType : byteTypes) {
            if (size < 1024)
                break;

            currentByteType = byteType;
            size = size / 1024;
        }

        return String.format("%01.2f %s", size, currentByteType);
    }

    public static void unzip(File zipFile, File targetDirectory) throws IOException {
        try (ZipInputStream zis = new ZipInputStream(
                new BufferedInputStream(Files.newInputStream(zipFile.toPath())))) {
            ZipEntry ze;
            int count;
            byte[] buffer = new byte[8192];
            while ((ze = zis.getNextEntry()) != null) {
                File file = new File(targetDirectory, ze.getName());
                File dir = ze.isDirectory() ? file : file.getParentFile();
                if (!dir.isDirectory() && !dir.mkdirs())
                    throw new FileNotFoundException("Failed to ensure directory: " +
                            dir.getAbsolutePath());
                if (ze.isDirectory())
                    continue;
                try (FileOutputStream fout = new FileOutputStream(file)) {
                    while ((count = zis.read(buffer)) != -1)
                        fout.write(buffer, 0, count);
                }
            /* if time should be restored as well
            long time = ze.getTime();
            if (time > 0)
                file.setLastModified(time);
            */
            }
        }
    }

    private static String getFileExtension(File file) {
        String name = file.getName();
        int lastIndexOf = name.lastIndexOf(".");
        if (lastIndexOf == -1) {
            return ""; // empty extension
        }
        return name.substring(lastIndexOf);
    }

    public static String getPackageName(Context context, File file) {
        PackageManager packageManager = context.getPackageManager();
        PackageInfo info = packageManager.getPackageArchiveInfo(file.getAbsolutePath(), PackageManager.GET_ACTIVITIES);
        if (info != null) {
            ApplicationInfo appInfo = info.applicationInfo;
            return appInfo.packageName;
        }
        return null;
    }
}
