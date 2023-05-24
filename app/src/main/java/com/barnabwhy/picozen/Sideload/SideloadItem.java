package com.barnabwhy.picozen.Sideload;

import android.view.View;

import com.barnabwhy.picozen.SideloadAdapter;

public class SideloadItem {

    private SideloadItemType type;
    private String name;
    private String path;
    private long size;
    private String modifiedAt;

    private String packageLabel;
    private String packageName;
    private String packageVersion;

    SideloadAdapter.ViewHolder holder;

    public SideloadItem(SideloadItemType type, String name, String path, long size, String modifiedAt) {
        this.type = type;
        this.name = name;
        this.path = path;
        this.size = size;
        this.modifiedAt = modifiedAt;
    }

    public void fetchInfo() {

    }

    public void setHolder(SideloadAdapter.ViewHolder holder) {
        this.holder = holder;
    }

    public SideloadItemType getType() {
        return type;
    }

    public String getName() {
        return name;
    }

    public String getPath() {
        return path;
    }

    public long getSize() {
        return size;
    }

    public String getModifiedAt() {
        return modifiedAt;
    }

    public String getPackageLabel() {
        return packageLabel;
    }

    public String getPackageName() {
        return packageName;
    }

    public String getPackageVersion() {
        return packageVersion;
    }
}
