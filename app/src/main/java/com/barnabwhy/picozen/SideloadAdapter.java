package com.barnabwhy.picozen;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Environment;
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
import androidx.core.content.FileProvider;

import com.barnabwhy.picozen.Sideload.Providers.AbstractProvider;
import com.barnabwhy.picozen.Sideload.Providers.EmptyProvider;
import com.barnabwhy.picozen.Sideload.Providers.FTPProvider;
import com.barnabwhy.picozen.Sideload.Providers.ProxyProvider;
import com.barnabwhy.picozen.Sideload.SideloadItem;
import com.barnabwhy.picozen.Sideload.SideloadItemType;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Enumeration;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;

public class SideloadAdapter extends BaseAdapter {
    private final SharedPreferences sharedPreferences;
    private final MainActivity mainActivityContext;

    private AbstractProvider provider;

    private DownloadInfo currentDownload;

    public AbstractProvider getProvider() {
        return provider;
    }

    public static class ViewHolder {
        public RelativeLayout layout;
        public TextView name;
        public TextView modified;
        public TextView size;
        public ImageView downloadIcon;
        public ImageView openFolderIcon;
    }
    private static class DownloadInfo {
        SideloadItem item;
        long downloadedBytes;
    }

    public enum SideloadProviderType {
        NONE,
        FTP,
        PROXY
    }

    public SideloadAdapter(MainActivity context) {
        mainActivityContext = context;
        sharedPreferences = mainActivityContext.getSharedPreferences(mainActivityContext.getPackageName() + "_preferences", Context.MODE_PRIVATE);

        setProvider(SideloadProviderType.values()[sharedPreferences.getInt(SettingsProvider.KEY_SIDELOAD_TYPE, 0)]);
    }

    public void setProvider(SideloadProviderType type) {
        if(provider != null) {
            provider.cleanup();
        }
        if(type == SideloadProviderType.NONE) {
            provider = new EmptyProvider(sharedPreferences, mainActivityContext, this::notifyDataSetChanged);
        } else if(type == SideloadProviderType.FTP) {
            provider = new FTPProvider(sharedPreferences, mainActivityContext, this::notifyDataSetChanged);
        } else if(type == SideloadProviderType.PROXY) {
            provider = new ProxyProvider(sharedPreferences, mainActivityContext, this::notifyDataSetChanged);
        }
    }

    @Override
    public void notifyDataSetChanged() {
        if (getCount() == 0) {
            mainActivityContext.findViewById(R.id.sideload_grid).setVisibility(View.GONE);
            mainActivityContext.findViewById(R.id.sideload_grid_empty).setVisibility(View.VISIBLE);
            if (sharedPreferences.getInt(SettingsProvider.KEY_SIDELOAD_TYPE, 0) == 0 || sharedPreferences.getString(SettingsProvider.KEY_SIDELOAD_HOST, "").equals("")) {
                ((TextView)mainActivityContext.findViewById(R.id.sideload_grid_empty)).setText(R.string.no_sideload_server);
            } else {
                ((TextView)mainActivityContext.findViewById(R.id.sideload_grid_empty)).setText(R.string.fetch_files_error);
            }
        } else {
            mainActivityContext.findViewById(R.id.sideload_grid).setVisibility(View.VISIBLE);
            mainActivityContext.findViewById(R.id.sideload_grid_empty).setVisibility(View.GONE);
        }
        super.notifyDataSetChanged();
    }

    @Override
    public int getCount() {
        return getProvider().getCount();
    }

    @Override
    public SideloadItem getItem(int position) {
        return getProvider().getItem(position);
    }

    public SideloadItemType getType(int position) {
        return getItem(position).getType();
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @SuppressLint("DefaultLocale")
    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        ViewHolder holder;

        final SideloadItem current = getItem(position);
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

        getProvider().setHolder(position, holder);

        if(getType(position) != SideloadItemType.DIRECTORY) {
            holder.layout.setOnClickListener(view -> {
                Thread thread = new Thread() {
                    @Override
                    public void run() {
                        mainActivityContext.ensureStoragePermissions();

                        if(currentDownload != null)
                            return;

                        currentDownload = new DownloadInfo();
                        currentDownload.item = current;
                        AtomicReference<AlertDialog> dialog = new AtomicReference<>();
                        mainActivityContext.runOnUiThread(() -> {
                            dialog.set(showDownloadDialog());
                        });

                        AtomicBoolean cancelled = new AtomicBoolean(false);
                        AtomicLong lastProgressTime = new AtomicLong();
                        getProvider().downloadFile(current, (file) -> {
                            mainActivityContext.runOnUiThread(() -> {
                                ((TextView)dialog.get().findViewById(R.id.file_name)).setText(file.getName());
                                dialog.get().findViewById(R.id.cancel_btn).setVisibility(View.VISIBLE);
                                dialog.get().findViewById(R.id.cancel_btn).setOnClickListener(view -> {
                                    cancelled.set(true);
                                    file.delete();
                                });
                            });
                        }, (progress) -> {
                            if(progress != current.getSize() && System.currentTimeMillis() - lastProgressTime.get() < 100)
                                return;

                            lastProgressTime.set(System.currentTimeMillis());

                            mainActivityContext.runOnUiThread(() -> {
                                currentDownload.downloadedBytes = progress;
                                ((TextView)dialog.get().findViewById(R.id.progress_text)).setText(String.format("%s / %s (%01.2f%%)", bytesReadable(currentDownload.downloadedBytes), bytesReadable(current.getSize()), ((double)progress / current.getSize()) * 100.0));

                                View progressBar = dialog.get().findViewById(R.id.progress_bar);
                                ViewGroup.LayoutParams params = progressBar.getLayoutParams();
                                params.width = (int) (((View)progressBar.getParent()).getWidth() * ((double)progress / current.getSize()));
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
                                    boolean cancelled;
                                };

                                Consumer<CompleteData> onComplete = (data) -> {
                                    if (data.error != null) {
                                        if(data.cancelled) {
                                            ((TextView)dialog.get().findViewById(R.id.progress_text)).setText(R.string.download_cancelled);
                                        } else {
                                            ((TextView)dialog.get().findViewById(R.id.progress_text)).setText(String.format(mainActivityContext.getResources().getString(R.string.an_error_occurred), data.error.getLocalizedMessage()));
                                        }
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

                                            mainActivityContext.runOnUiThread(() -> {
                                                dialog.get().findViewById(R.id.cancel_btn).setVisibility(View.VISIBLE);
                                                dialog.get().findViewById(R.id.cancel_btn).setOnClickListener(view -> {
                                                    cancelled.set(true);
                                                    outFile.delete();
                                                    if (dir.exists()) {
                                                        String deleteCmd = "rm -r " + dir.getAbsolutePath();
                                                        Runtime runtime = Runtime.getRuntime();
                                                        try {
                                                            runtime.exec(deleteCmd);
                                                        } catch (IOException e) { }
                                                    }
                                                });
                                            });

                                            long uncompressedSize = 0;
                                            ZipFile f = new ZipFile(outFile);
                                            Enumeration<ZipEntry> entries = (Enumeration<ZipEntry>) f.entries();
                                            while(entries.hasMoreElements()) {
                                                ZipEntry entry = entries.nextElement();
                                                uncompressedSize += entry.getSize();
                                            }

                                            long finalUncompressedSize = uncompressedSize;
                                            unzip(outFile, dir, processedBytes -> {
                                                mainActivityContext.runOnUiThread(() -> {
                                                    ((TextView) dialog.get().findViewById(R.id.progress_text)).setText(String.format(mainActivityContext.getResources().getString(R.string.extracting_zip_progress), bytesReadable(processedBytes) + "/" + bytesReadable(finalUncompressedSize)));
                                                });
                                            });

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

                                        outFile.delete();

                                        CompleteData completeData = new CompleteData();
                                        completeData.error = error;
                                        completeData.installableFile = installableFile;
                                        completeData.obbDir = obbDir;
                                        completeData.cancelled = cancelled.get();

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
                                    completeData.cancelled = false;
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

        holder.size.setText(bytesReadable(current.getSize()));
        holder.modified.setText(current.getModifiedAt());

        return convertView;
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
        lp.width = mainActivityContext.getPixelFromDip(480); // 600px on PICO 4
        lp.height = mainActivityContext.getPixelFromDip(256); // 320px on PICO 4

        dialog.getWindow().setAttributes(lp);
        dialog.findViewById(R.id.layout).requestLayout();
        dialog.getWindow().setBackgroundDrawableResource(R.drawable.bg_dialog);
        ((TextView)dialog.findViewById(R.id.file_name)).setText(currentDownload.item.getName());
        ((TextView)dialog.findViewById(R.id.progress_text)).setText(String.format("0 B / %s (0%%)", bytesReadable(currentDownload.item.getSize())));
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

    public static void unzip(File zipFile, File targetDirectory, Consumer<Long> progressCallback) throws IOException {
        try (ZipInputStream zis = new ZipInputStream(
                new BufferedInputStream(Files.newInputStream(zipFile.toPath())))) {
            ZipEntry ze;
            int count;
            byte[] buffer = new byte[8192];
            long processedBytes = 0;
            while ((ze = zis.getNextEntry()) != null) {
                if(!zipFile.exists()) {
                    throw new IOException();
                }
                File file = new File(targetDirectory, ze.getName());
                File dir = ze.isDirectory() ? file : file.getParentFile();
                if (!dir.isDirectory() && !dir.mkdirs())
                    throw new FileNotFoundException("Failed to ensure directory: " +
                            dir.getAbsolutePath());
                if (ze.isDirectory())
                    continue;
                try (FileOutputStream fout = new FileOutputStream(file)) {
                    while ((count = zis.read(buffer)) != -1) {
                        fout.write(buffer, 0, count);
                        processedBytes += count;
                        progressCallback.accept(processedBytes);
                        if(!zipFile.exists()) {
                            throw new IOException();
                        }
                    }
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
