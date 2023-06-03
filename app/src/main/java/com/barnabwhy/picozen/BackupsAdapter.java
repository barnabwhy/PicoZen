package com.barnabwhy.picozen;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public class BackupsAdapter extends BaseAdapter {
    MainActivity context;
    SavesAdapter savesAdapter;
    LayoutInflater inflater;
    String backupsPath;
    ApplicationInfo app;
    AlertDialog dialog;
    ArrayList<String> backupDirs;
    Map<String, String> backupDates;

    String lastRestore = "";
    boolean lastSuccess = false;

    public BackupsAdapter(MainActivity mainContext, SavesAdapter savesAdapter, String backupsPath, ApplicationInfo app, AlertDialog dialog, String[] backupDirs) {
        this.context = mainContext;
        this.savesAdapter = savesAdapter;
        this.backupsPath = backupsPath;
        this.app = app;
        this.dialog = dialog;
        this.backupDirs = new ArrayList<>(Arrays.asList(backupDirs));

        backupDates = new HashMap<>();
        for(String backupName : backupDirs) {
            String dateStr = mainContext.getResources().getString(R.string.unknown_date);
            if(backupName.contains("@")) {
                dateStr = backupName.split("@")[1];
            }
            backupDates.put(backupName, dateStr);
        }

        inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
    }

    @Override
    public int getCount() {
        return backupDirs.size();
    }

    @Override
    public Object getItem(int i) {
        return null;
    }

    @Override
    public long getItemId(int i) {
        return 0;
    }

    @Override
    public View getView(int i, View view, ViewGroup parent) {
        if(view == null) {
            view = inflater.inflate(R.layout.lv_backup_item, parent, false);
            view.setClipToOutline(true);
        }

        TextView backupDate = view.findViewById(R.id.backup_date);
        backupDate.setText(backupDates.get(backupDirs.get(i)));

        Path p_backupPath = Paths.get(backupsPath + backupDirs.get(i));
        TextView backupSize = view.findViewById(R.id.backup_size);
        backupSize.setText(SavesAdapter.bytesReadable(getDirectorySize(p_backupPath)));

        TextView restoreButton = view.findViewById(R.id.restore_btn);
        restoreButton.setText(lastRestore.equals(backupDirs.get(i)) ? (lastSuccess ? R.string.restore_success : R.string.restore_fail) : R.string.restore);


        View finalView = view;
        view.findViewById(R.id.restore_btn).setOnClickListener(v -> {
            dialog.findViewById(R.id.backup_btn).setOnClickListener(null);
            finalView.findViewById(R.id.restore_btn).setOnClickListener(null);
            finalView.findViewById(R.id.delete_backup_btn).setOnClickListener(null);
            ((TextView)dialog.findViewById(R.id.backup_btn)).setText(R.string.backup);
            ((TextView)finalView.findViewById(R.id.restore_btn)).setText(R.string.restoring);
            new Thread(() -> savesAdapter.restoreSave(p_backupPath.toString(), app, success -> context.runOnUiThread(() -> {
                lastRestore = backupDirs.get(i);
                lastSuccess = success;
                notifyDataSetChanged();
                ((TextView) finalView.findViewById(R.id.restore_btn)).setText(success ? R.string.restore_success : R.string.restore_fail);
                savesAdapter.updateSaveManagerDialog(dialog, app, false);
            }))).start();
        });

        view.findViewById(R.id.delete_backup_btn).setOnClickListener(v -> {
            dialog.findViewById(R.id.backup_btn).setOnClickListener(null);
            finalView.findViewById(R.id.restore_btn).setOnClickListener(null);
            finalView.findViewById(R.id.delete_backup_btn).setOnClickListener(null);
            ((TextView)dialog.findViewById(R.id.backup_btn)).setText(R.string.backup);
            ((TextView)finalView.findViewById(R.id.restore_btn)).setText(R.string.restore);
            new Thread(() -> savesAdapter.deleteSaveBackup(p_backupPath.toString(), success -> context.runOnUiThread(() -> savesAdapter.updateSaveManagerDialog(dialog, app, true)))).start();
        });

        return view;
    }

    private long getDirectorySize(Path path) {
        try {
            return Files.walk(path).mapToLong(p -> p.toFile().length() ).sum();
        } catch (Exception e) {
            return 0L;
        }
    }
}
