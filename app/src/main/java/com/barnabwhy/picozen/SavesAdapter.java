package com.barnabwhy.picozen;

import static android.os.Build.VERSION.SDK_INT;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Environment;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.BaseAdapter;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.core.content.res.ResourcesCompat;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FilenameFilter;
import java.io.IOException;
import java.nio.file.CopyOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Stream;

public class SavesAdapter extends BaseAdapter {
    private static final String APP_ICON_PATH = "https://api.picozen.app/assets/";
    private final SharedPreferences sharedPreferences;
    private final MainActivity mainActivityContext;
    private List<ApplicationInfo> appList;
    private final GridView savesGridView;

    private static class ViewHolder {
        RelativeLayout layout;
        ImageView appIcon;
        TextView name;
    }

    public SavesAdapter(MainActivity context) {
        mainActivityContext = context;

        sharedPreferences = mainActivityContext.getSharedPreferences(mainActivityContext.getPackageName() + "_preferences", Context.MODE_PRIVATE);

        savesGridView = mainActivityContext.findViewById(R.id.saves_grid);

        updateAppList();
    }

    public void updateAppList() {
        mainActivityContext.ensureStoragePermissions();

        appList = getAppList();

        TextView saveGridEmpty = mainActivityContext.findViewById(R.id.save_grid_empty);
        if(appList.size() == 0) {
            savesGridView.setVisibility(View.GONE);
            saveGridEmpty.setVisibility(View.VISIBLE);

            if(SDK_INT >= Build.VERSION_CODES.R) {
                saveGridEmpty.setText(R.string.saves_android_files_bad);
            }
        } else {
            savesGridView.setVisibility(View.VISIBLE);
            saveGridEmpty.setVisibility(View.GONE);
        }

        this.sort();
    }
    public ArrayList<ApplicationInfo> getAppList() {
        ArrayList<ApplicationInfo> installedApps = new ArrayList<>();
        PackageManager pm = mainActivityContext.getPackageManager();
        for (ApplicationInfo app : pm.getInstalledApplications(PackageManager.GET_META_DATA)) {
            if (AppsAdapter.hiddenApps.contains(app.packageName) || !appHasFiles(app)) {
                continue;
            }

            if (!SettingsProvider.launchIntents.containsKey(app.packageName)) {
                SettingsProvider.launchIntents.put(app.packageName, pm.getLaunchIntentForPackage(app.packageName));
            }
            if (SettingsProvider.launchIntents.get(app.packageName) != null) {
                if (!SettingsProvider.installDates.containsKey(app.packageName)) {
                    long installDate;
                    try {
                        PackageInfo packageInfo = pm.getPackageInfo(app.packageName, 0);
                        installDate = packageInfo.firstInstallTime;
                    } catch (PackageManager.NameNotFoundException e) {
                        throw new RuntimeException(e);
                    }
                    SettingsProvider.installDates.put(app.packageName, installDate);
                }
                installedApps.add(app);
            }
        }
        return installedApps;
    }

    public void sort() {
        final PackageManager pm = mainActivityContext.getPackageManager();

        appList.sort((a, b) -> {
            String na;
            String nb;
            na = a.loadLabel(pm).toString().toUpperCase();
            nb = b.loadLabel(pm).toString().toUpperCase();
            return na.compareTo(nb);
        });
        this.notifyDataSetChanged();
    }

    @Override
    public int getCount() {
        return appList.size();
    }

    @Override
    public Object getItem(int position) {
        return appList.get(position);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @SuppressLint("DefaultLocale")
    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        ViewHolder holder;

        LayoutInflater inflater = (LayoutInflater) mainActivityContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);

        if (convertView == null) {
            // Create a new ViewHolder and inflate the layout
            convertView = inflater.inflate(R.layout.lv_saves_item, parent, false);
            holder = new ViewHolder();
            holder.layout = convertView.findViewById(R.id.layout);
            holder.appIcon = convertView.findViewById(R.id.ic_app);
            holder.name = convertView.findViewById(R.id.name);
            convertView.setTag(holder);

            // Set clipToOutline to true on imageView (Workaround for bug)
            holder.layout.setClipToOutline(true);
            holder.appIcon.setClipToOutline(true);
        } else {
            // ViewHolder already exists, reuse it
            holder = (ViewHolder) convertView.getTag();
        }

        ApplicationInfo current = appList.get(position);

        holder.name.setText(current.loadLabel(mainActivityContext.getPackageManager()));
        holder.appIcon.setImageDrawable(current.loadIcon(mainActivityContext.getPackageManager()));

        holder.layout.setOnClickListener(v -> openSaveManagerDialog(current));

        return convertView;
    }

    @SuppressLint("DefaultLocale")
    public static String bytesReadable(long bytes) {
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

    private boolean appHasFiles(ApplicationInfo app) {
        File dataDir = new File(Environment.getExternalStorageDirectory().getPath() + "/Android/data/" + app.packageName);
        if (dataDir.exists()) {
            File filesDir = new File(Environment.getExternalStorageDirectory().getPath() + "/Android/data/" + app.packageName + "/files");
            if (filesDir.exists()) {
                return !isEmpty(filesDir.toPath());
            }
        }
        return false;
    }

    private boolean isEmpty(Path path) {
        if (Files.isDirectory(path)) {
            try (Stream<Path> entries = Files.list(path)) {
                return !entries.findFirst().isPresent();
            } catch (Exception e) {
                return true;
            }
        }

        return false;
    }
    private long getDirectorySize(Path path) {
        try {
            return Files.walk(path).mapToLong( p -> p.toFile().length() ).sum();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private AlertDialog openSaveManagerDialog(ApplicationInfo app) {
        View dialogOverlay = mainActivityContext.findViewById(R.id.dialog_overlay);
        dialogOverlay.setVisibility(View.VISIBLE);

        AlertDialog.Builder builder = new AlertDialog.Builder(mainActivityContext, R.style.CustomDialog);
        builder.setView(R.layout.dialog_save_man);
        AlertDialog dialog = builder.create();

        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.show();

        DisplayMetrics displayMetrics = mainActivityContext.getResources().getDisplayMetrics();

        WindowManager.LayoutParams lp = new WindowManager.LayoutParams();
        lp.copyFrom(dialog.getWindow().getAttributes());
        lp.width = mainActivityContext.getPixelFromDip(480); // 600px on PICO 4
        lp.height = displayMetrics.heightPixels - mainActivityContext.getPixelFromDip(80); // 700px on PICO 4

        dialog.getWindow().setAttributes(lp);
        dialog.findViewById(R.id.layout).requestLayout();
        dialog.getWindow().setBackgroundDrawableResource(R.drawable.bg_dialog);

        dialog.setOnDismissListener(d -> dialogOverlay.setVisibility(View.GONE));

        updateSaveManagerDialog(dialog, app, true);

        dialog.findViewById(R.id.cancel_btn).setOnClickListener(view -> dialog.dismiss());

        return dialog;
    }
    public void updateSaveManagerDialog(AlertDialog dialog, ApplicationInfo app, boolean backupAdapterReset) {
        String filesPath = Environment.getExternalStorageDirectory().getPath() + "/Android/data/" + app.packageName + "/files";
        String backupPath = Environment.getExternalStorageDirectory().getPath() + "/Android/data/" + mainActivityContext.getPackageName() + "/files/backups/" + app.packageName;
        String backupsPath = Environment.getExternalStorageDirectory().getPath() + "/Android/data/" + mainActivityContext.getPackageName() + "/files/backups/";
        File file = new File(backupsPath);
        String[] backups = file.list(new FilenameFilter() {
            @Override
            public boolean accept(File current, String name) {
                return name.startsWith(app.packageName) && new File(current, name).isDirectory();
            }
        });

        if(backupAdapterReset || ((GridView) dialog.findViewById(R.id.backups_list)).getAdapter() == null)
            ((GridView) dialog.findViewById(R.id.backups_list)).setAdapter(new BackupsAdapter(mainActivityContext, this, backupsPath, app, dialog, backups));

        ((TextView) dialog.findViewById(R.id.app_name)).setText(app.loadLabel(mainActivityContext.getPackageManager()));
        ((TextView) dialog.findViewById(R.id.package_name)).setText(app.packageName);
        ((TextView) dialog.findViewById(R.id.files_size)).setText(String.format(mainActivityContext.getResources().getString(R.string.files_size), bytesReadable(getDirectorySize(Paths.get(filesPath)))));

        dialog.findViewById(R.id.backup_btn).setOnClickListener(view -> {
            dialog.findViewById(R.id.backup_btn).setOnClickListener(null);
            ((TextView)dialog.findViewById(R.id.backup_btn)).setText(R.string.backing_up);
            new Thread(() -> backupSave(app, success -> mainActivityContext.runOnUiThread(() -> {
                ((TextView)dialog.findViewById(R.id.backup_btn)).setText(success ? R.string.backup_success : R.string.backup_fail);
                updateSaveManagerDialog(dialog, app, true);
            }))).start();
        });
    }

    public void backupSave(ApplicationInfo app, Consumer<Boolean> callback) {
        DateFormat df = DateFormat.getDateTimeInstance();
        String filesPath = Environment.getExternalStorageDirectory().getPath() + "/Android/data/" + app.packageName + "/files";
        String backupPath = Environment.getExternalStorageDirectory().getPath() + "/Android/data/" + mainActivityContext.getPackageName() + "/files/backups/" + app.packageName + "@" + df.format(new Date());
        boolean success = true;
        try {
            if (new File(backupPath).exists()) {
                delete(new File(backupPath));
            }
            copyFileOrFolder(new File(filesPath), new File(backupPath), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            e.printStackTrace();
            success = false;
        }
        callback.accept(success);
    }

    public void restoreSave(String backupPath, ApplicationInfo app, Consumer<Boolean> callback) {
        String filesPath = Environment.getExternalStorageDirectory().getPath() + "/Android/data/" + app.packageName + "/files";
        boolean success = true;
        if(!new File(backupPath).exists())
            return;

        try {
            if (new File(filesPath).exists()) {
                delete(new File(filesPath));
            }
            copyFileOrFolder(new File(backupPath), new File(filesPath), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            e.printStackTrace();
            success = false;
        }
        callback.accept(success);
    }

    public void deleteSaveBackup(String backupPath, Consumer<Boolean> callback) {
        boolean success = true;
        try {
            if (new File(backupPath).exists()) {
                delete(new File(backupPath));
            }
        } catch (IOException e) {
            e.printStackTrace();
            success = false;
        }
        callback.accept(success);
    }

    public static void copyFileOrFolder(File source, File dest, CopyOption...  options) throws IOException {
        if (source.isDirectory()) {
            copyFolder(source, dest, options);
        } else {
            ensureParentFolder(dest);
            copyFile(source, dest, options);
        }
    }

    private static void copyFolder(File source, File dest, CopyOption... options) throws IOException {
        if (!dest.exists())
            dest.mkdirs();
        File[] contents = source.listFiles();
        if (contents != null) {
            for (File f : contents) {
                File newFile = new File(dest.getAbsolutePath() + File.separator + f.getName());
                if (f.isDirectory())
                    copyFolder(f, newFile, options);
                else
                    copyFile(f, newFile, options);
            }
        }
    }

    private static void copyFile(File source, File dest, CopyOption... options) throws IOException {
        Files.copy(source.toPath(), dest.toPath(), options);
    }

    private static void ensureParentFolder(File file) {
        File parent = file.getParentFile();
        if (parent != null && !parent.exists())
            parent.mkdirs();
    }

    void delete(File f) throws IOException {
        if (f.isDirectory()) {
            for (File c : f.listFiles())
                delete(c);
        }
        if (!f.delete())
            throw new FileNotFoundException("Failed to delete file: " + f);
    }
}
