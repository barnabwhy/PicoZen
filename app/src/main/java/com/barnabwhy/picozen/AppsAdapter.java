package com.barnabwhy.picozen;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Handler;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.view.animation.LinearInterpolator;
import android.view.animation.RotateAnimation;
import android.widget.BaseAdapter;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;

import androidx.core.content.res.ResourcesCompat;

import java.io.DataInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.atomic.AtomicInteger;

public class AppsAdapter extends BaseAdapter
{
    private final MainActivity mainActivityContext;
    private List<ApplicationInfo> appList;
    private final Set<String> favoriteList;
    private final Set<String> hiddenList;

    public enum SORT_FIELD { APP_NAME, RECENT_DATE, INSTALL_DATE }
    public enum SORT_ORDER { ASCENDING, DESCENDING }

    private static int itemScale;
    private final SettingsProvider settingsProvider;
    public static SharedPreferences sharedPreferences;
    private final GridView appGridView;
    private boolean isEditMode;

    public static List<String> hiddenApps;

    public AppsAdapter(MainActivity context)
    {
        if(Utils.isPicoHeadset()) {
            hiddenApps = Arrays.asList(context.getPackageName(), "com.android.traceur", "com.picovr.init.overlay", "com.picovr.provision", "com.pvr.appmanager", "com.pvr.seethrough.setting", "com.pvr.scenemanager");
        } else if(Utils.isOculusHeadset()) {
            hiddenApps = Arrays.asList(context.getPackageName(), "com.android.traceur", "com.oculus.vrshell", "com.oculus.integrity", "com.oculus.gamingactivity", "com.oculus.assistant", "com.oculus.cvp", "com.oculus.os.chargecontrol", "com.oculus.os.clearactivity", "com.oculus.cvpservice", "com.oculus.shellenv", "com.oculus.vrshell.home", "com.oculus.guidebook", "com.android.providers.calendar", "com.oculus.systemsearch", "com.oculus.systemutilities", "com.oculus.systemactivities");
        } else {
            hiddenApps = Arrays.asList(context.getPackageName(), "com.android.traceur", "com.android.providers.calendar");
        }

        mainActivityContext = context;

        appGridView = mainActivityContext.findViewById(R.id.app_grid);
        itemScale = mainActivityContext.getPixelFromDip(200);
        appGridView.setColumnWidth(itemScale);
        sharedPreferences = context.getSharedPreferences(context.getPackageName() + "_preferences", Context.MODE_PRIVATE);
        settingsProvider = SettingsProvider.getInstance(mainActivityContext);

        favoriteList = sharedPreferences.getStringSet(SettingsProvider.KEY_FAVORITE_APPS, new HashSet<>());
        hiddenList = sharedPreferences.getStringSet(SettingsProvider.KEY_HIDDEN_APPS, new HashSet<>());

        isEditMode = false;
        updateAppList();

        // redundancy for package broadcast receiver
        final Handler handler = new Handler();
        Timer timer = new Timer();
        TimerTask updateAppListTask = new TimerTask() {
            @Override
            public void run() {
                handler.post(() -> {
                    try {
                        updateAppList();
                    } catch (Exception e) {
                        Log.e("updateAppListTask", e.toString());
                    }
                });
            }
        };
        timer.schedule(updateAppListTask, 0, 600000); //execute every 10 minutes
    }

    public boolean getEditModeEnabled() {
        return isEditMode;
    }

    public void toggleEditMode() {
        isEditMode = !isEditMode;
        notifyDataSetChanged();
    }

    public void updateAppList() {
        appList = getAppList(sharedPreferences.getInt(SettingsProvider.KEY_GROUP_SPINNER, 0));

        TextView appGridEmpty = mainActivityContext.findViewById(R.id.app_grid_empty);
        if(appList.size() == 0) {
            appGridView.setVisibility(View.GONE);
            appGridEmpty.setVisibility(View.VISIBLE);
        } else {
            appGridView.setVisibility(View.VISIBLE);
            appGridEmpty.setVisibility(View.GONE);
        }

        this.sort(SORT_FIELD.values()[sharedPreferences.getInt(SettingsProvider.KEY_SORT_FIELD, 0)], SORT_ORDER.values()[sharedPreferences.getInt(SettingsProvider.KEY_SORT_ORDER, 0)]);
        notifyDataSetChanged();
    }

    private static class ViewHolder {
        RelativeLayout layout;
        ImageView imageView;
        TextView textView;
        ImageView progressBar;
        View favoriteBtn;
        View hiddenBtn;
        View deleteBtn;
        boolean hasBanner;
    }

    public int getCount()
    {
        return appList.size();
    }

    public Object getItem(int position)
    {
        return appList.get(position);
    }

    public long getItemId(int position)
    {
        return position;
    }

    public ArrayList<ApplicationInfo> getAppList(int group) {
        ArrayList<ApplicationInfo> installedApps = new ArrayList<>();
        PackageManager pm = mainActivityContext.getPackageManager();
        for (ApplicationInfo app : pm.getInstalledApplications(PackageManager.GET_META_DATA)) {
            if (group == 0 && hiddenList.contains(app.packageName))
                continue;
            if (group == 1 && !favoriteList.contains(app.packageName))
                continue;
            if (group == 2 && !hiddenList.contains(app.packageName))
                continue;
            if(hiddenApps.contains(app.packageName))
                continue;

            if (!SettingsProvider.launchIntents.containsKey(app.packageName)) {
                SettingsProvider.launchIntents.put(app.packageName, pm.getLaunchIntentForPackage(app.packageName));
            }
            if(SettingsProvider.launchIntents.get(app.packageName) != null) {
                if(!SettingsProvider.installDates.containsKey(app.packageName)) {
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

    public String getAppDisplayName(String pkg, CharSequence label)
    {
        String name = mainActivityContext.getSharedPreferences(mainActivityContext.getPackageName() + "_preferences", Context.MODE_PRIVATE).getString(pkg, "");
        if (!name.isEmpty()) {
            return name;
        }

        String retVal = label.toString();
        if (retVal == null || retVal.equals("")) {
            retVal = pkg;
        }
        return retVal;
    }

    private final Handler handler = new Handler();
    @SuppressLint("NewApi")
    public View getView(int position, View convertView, ViewGroup parent)
    {
        ViewHolder holder;

        final ApplicationInfo currentApp = appList.get(position);
        LayoutInflater inflater = (LayoutInflater) mainActivityContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);

        if (convertView == null) {
            // Create a new ViewHolder and inflate the layout
            convertView = inflater.inflate(R.layout.lv_app, parent, false);
            holder = new ViewHolder();
            holder.layout = convertView.findViewById(R.id.layout);
            holder.imageView = convertView.findViewById(R.id.imageLabel);
            holder.textView = convertView.findViewById(R.id.textLabel);
            holder.progressBar = convertView.findViewById(R.id.progress_bar);
            holder.favoriteBtn = convertView.findViewById(R.id.favorite_btn);
            holder.hiddenBtn = convertView.findViewById(R.id.hidden_btn);
            holder.deleteBtn = convertView.findViewById(R.id.delete_btn);
            convertView.setTag(holder);

            // Set clipToOutline to true on imageView (Workaround for bug)
            holder.layout.setClipToOutline(true);

            ViewGroup.LayoutParams params = holder.layout.getLayoutParams();

            //Calculate text height
            holder.textView.measure(0, 0);
            int textHeight = holder.textView.getMeasuredHeight() + ((ViewGroup.MarginLayoutParams) holder.textView.getLayoutParams()).topMargin + ((ViewGroup.MarginLayoutParams) holder.textView.getLayoutParams()).bottomMargin;

            params.width = itemScale;
//            if (style == 0) {
//                if(showTextLabels) {
//                    params.height = (int) ((itemScale) * 0.5625) + textHeight;
//                }else{
                    params.height = (int) ((itemScale) * 0.5625);
//                }
//            } else {
//                if(showTextLabels) {
//                    params.height = (int) (itemScale + textHeight);
//                }else{
//                    params.height = (int) itemScale;
//                }
//            }
            holder.layout.setLayoutParams(params);
        } else {
            // ViewHolder already exists, reuse it
            holder = (ViewHolder) convertView.getTag();
        }

        holder.favoriteBtn.setForeground(ResourcesCompat.getDrawable(mainActivityContext.getResources(), favoriteList.contains(currentApp.packageName) ? R.drawable.ic_favorite_active : R.drawable.ic_favorite, mainActivityContext.getTheme()));
        holder.hiddenBtn.setForeground(ResourcesCompat.getDrawable(mainActivityContext.getResources(), hiddenList.contains(currentApp.packageName) ? R.drawable.ic_hidden_active : R.drawable.ic_hidden, mainActivityContext.getTheme()));

        holder.favoriteBtn.setOnClickListener(view -> {
            ApplicationInfo app = appList.get(position);
            boolean isFavorite = favoriteList.contains(app.packageName);

            if(isFavorite) {
                favoriteList.remove(app.packageName);
            } else {
                favoriteList.add(app.packageName);
                hiddenList.remove(app.packageName);
            }
            sharedPreferences.edit().putStringSet(SettingsProvider.KEY_FAVORITE_APPS, favoriteList).apply();
            sharedPreferences.edit().putStringSet(SettingsProvider.KEY_HIDDEN_APPS, hiddenList).apply();

            if(sharedPreferences.getInt(SettingsProvider.KEY_GROUP_SPINNER, 0) == 0) {
                holder.favoriteBtn.setForeground(ResourcesCompat.getDrawable(mainActivityContext.getResources(), isFavorite ? R.drawable.ic_favorite : R.drawable.ic_favorite_active, mainActivityContext.getTheme()));
            } else {
                // Reload view if state of current group changes
                updateAppList();
            }
        });

        holder.hiddenBtn.setOnClickListener(view -> {
            ApplicationInfo app = appList.get(position);
            boolean isHidden = hiddenList.contains(app.packageName);

            if(isHidden) {
                hiddenList.remove(app.packageName);
            } else {
                hiddenList.add(app.packageName);
                favoriteList.remove(app.packageName);
            }
            sharedPreferences.edit().putStringSet(SettingsProvider.KEY_FAVORITE_APPS, favoriteList).apply();
            sharedPreferences.edit().putStringSet(SettingsProvider.KEY_HIDDEN_APPS, hiddenList).apply();

            // Reload view if state of current group changes
            updateAppList();
        });

//        holder.deleteBtn.setOnClickListener(view -> {
//            ApplicationInfo app = appList.get(position);
//
//            Intent intent = new Intent(mainActivityContext, mainActivityContext.getClass());
//            PendingIntent sender = PendingIntent.getActivity(mainActivityContext, 0, intent, PendingIntent.FLAG_IMMUTABLE);
//            PackageInstaller mPackageInstaller = mainActivityContext.getPackageManager().getPackageInstaller();
//            mPackageInstaller.uninstall(app.packageName, sender.getIntentSender());
//        });

        AtomicInteger hovering = new AtomicInteger();
        View.OnGenericMotionListener listener = (View view, MotionEvent event) -> {
            int action = event.getAction();
            if (action == MotionEvent.ACTION_HOVER_ENTER || action == MotionEvent.ACTION_DOWN) {
                hovering.getAndIncrement();

                holder.layout.findViewById(R.id.app_icon_overlay).setVisibility(View.VISIBLE);
                holder.textView.setVisibility(View.VISIBLE);
            } else if (action == MotionEvent.ACTION_HOVER_EXIT || action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                hovering.getAndDecrement();

                if(hovering.get() <= 0) {
                    holder.layout.findViewById(R.id.app_icon_overlay).setVisibility(View.INVISIBLE);
                    holder.textView.setVisibility(holder.hasBanner ? View.INVISIBLE : View.VISIBLE);
                }
            }
            if(hovering.get() < 0) {
                hovering.set(0);
            }
            return false;
        };

        holder.layout.setOnGenericMotionListener(listener);
        holder.favoriteBtn.setOnGenericMotionListener(listener);
        holder.hiddenBtn.setOnGenericMotionListener(listener);
        holder.deleteBtn.setOnGenericMotionListener(listener);

        holder.favoriteBtn.setVisibility(isEditMode ? View.VISIBLE : View.GONE);
        holder.hiddenBtn.setVisibility(isEditMode ? View.VISIBLE : View.GONE);
//        holder.deleteBtn.setVisibility(isEditMode ? View.VISIBLE : View.GONE);

        // set value into textview
        PackageManager pm = mainActivityContext.getPackageManager();
        String name = getAppDisplayName(currentApp.packageName, currentApp.loadLabel(pm));
        holder.textView.setText(name);

        holder.layout.setOnClickListener(view -> {
            holder.progressBar.setVisibility(View.VISIBLE);
            RotateAnimation rotateAnimation = new RotateAnimation(
                    0, 360,
                    Animation.RELATIVE_TO_SELF, 0.5f,
                    Animation.RELATIVE_TO_SELF, 0.5f
            );
            rotateAnimation.setDuration(1000);
            rotateAnimation.setRepeatCount(Animation.INFINITE);
            rotateAnimation.setInterpolator(new LinearInterpolator());
            holder.progressBar.startAnimation(rotateAnimation);
            if(!mainActivityContext.openApp(currentApp)) {
                holder.progressBar.setVisibility(View.GONE);
                holder.progressBar.clearAnimation();
            }

            handler.removeCallbacksAndMessages(null);
            handler.postDelayed(() -> {
                AlphaAnimation fadeOut = new AlphaAnimation(1, 0);
                fadeOut.setDuration(1000);
                fadeOut.setFillAfter(true);
                holder.progressBar.startAnimation(fadeOut);
            }, 2000);
            handler.postDelayed(() -> {
                holder.progressBar.setVisibility(View.GONE);
                holder.progressBar.clearAnimation();
            }, 3000);
        });
//        holder.layout.setOnLongClickListener(view -> {
//            showAppDetails(currentApp);
//            return false;
//        });

        // set application icon
        try {
            loadIcon(mainActivityContext, holder, currentApp, name);
        } catch (Resources.NotFoundException e) {
            Log.e("loadIcon", "Error loading icon for app: " + currentApp.packageName, e);
        }
        return convertView;
    }

    private Long getInstallDate(ApplicationInfo applicationInfo) {
        return SettingsProvider.installDates.getOrDefault(applicationInfo.packageName, 0L);
    }

    public void sort(SORT_FIELD field, SORT_ORDER order) {
        final PackageManager pm = mainActivityContext.getPackageManager();
        final Map<String, Long> recents = settingsProvider.getRecents();

        appList.sort((a, b) -> {
            String na;
            String nb;
            long naL;
            long nbL;
            int result;
            switch (field) {
                case RECENT_DATE:
                    if (recents.containsKey(a.packageName)) {
                        naL = recents.get(a.packageName);
                    } else {
                        naL = getInstallDate(a);
                    }
                    if (recents.containsKey(b.packageName)) {
                        nbL = recents.get(b.packageName);
                    } else {
                        nbL = getInstallDate(b);
                    }
                    result = Long.compare(naL, nbL);
                    break;

                case INSTALL_DATE:
                    naL = getInstallDate(a);
                    nbL = getInstallDate(b);
                    result = Long.compare(naL, nbL);
                    break;

                default: //by APP_NAME
                    na = SettingsProvider.getAppDisplayName(mainActivityContext, a.packageName, a.loadLabel(pm)).toUpperCase();
                    nb = SettingsProvider.getAppDisplayName(mainActivityContext, b.packageName, b.loadLabel(pm)).toUpperCase();
                    result = na.compareTo(nb);
                    break;
            }

            return order == SORT_ORDER.ASCENDING ? result : -result;
        });
        this.notifyDataSetChanged();
    }

    public static File pkg2path(Context context, String pkg) {
        return new File(context.getCacheDir(), pkg + ".webp");
    }

    private final String ICONS_URL = "https://api.picozen.app/assets/";
    protected static final HashMap<String, Drawable> iconCache = new HashMap<>();
    protected static final HashMap<String, GradientDrawable> gradientCache = new HashMap<>();
    protected static final HashSet<String> ignoredIcons = new HashSet<>();

    private void loadIcon(Activity activity, ViewHolder holder, ApplicationInfo app, String name) {
        String pkg = app.packageName;

        if (iconCache.containsKey("banners."+pkg)) {
            holder.imageView.setImageDrawable(iconCache.get("banners."+pkg));
            setAppIconType(holder, true);
            return;
        }else{
            setAppIconType(holder, false);
            PackageManager pm = activity.getPackageManager();
            Resources resources;
            try {
                resources = pm.getResourcesForApplication(app.packageName);
            } catch (PackageManager.NameNotFoundException e) {
                e.printStackTrace();
                return;
            }
            int iconId = app.icon;
            if (iconId == 0) {
                iconId = android.R.drawable.sym_def_app_icon;
            }
            Drawable appIcon = ResourcesCompat.getDrawableForDensity(resources, iconId, DisplayMetrics.DENSITY_XXXHIGH, null);
            holder.imageView.setImageDrawable(appIcon);

            Bitmap icon = Utils.drawableToBitmap(appIcon);

            if(gradientCache.containsKey(pkg)) {
                holder.layout.findViewById(R.id.app_color_bg).setBackground(gradientCache.get(pkg));
            } else {
                Utils.createIconPaletteAsync(icon, mainActivityContext.getResources(), (gd) -> {
                    gradientCache.put(pkg, gd);
                    holder.layout.findViewById(R.id.app_color_bg).setBackground(gd);
                    return null;
                });
            }
        }

        final File file = pkg2path(activity, "banners."+pkg);
        if (file.exists()) {
            Log.i("Exists", file.getAbsolutePath());
            if (updateIcon(holder, file, "banners."+pkg)) {
                return;
            }
        }
        downloadIcon(activity, pkg, name, () -> updateIcon(holder, file, "banners."+pkg));
    }

    public static boolean isImageFileComplete(File imageFile) {
        boolean success = false;
        try {
            Bitmap bitmap = BitmapFactory.decodeFile(imageFile.getAbsolutePath());
            success = (bitmap != null);
        } catch (Exception e) {
            // do nothing
        }
        if (!success) {
            Log.e("imgComplete", "Failed to read image file: " + imageFile);
        }
        return success;
    }

    public static void clearIconCache() {
        ignoredIcons.clear();
        iconCache.clear();
    }

    public static void clearAllIcons(MainActivity activity) {
        for (String pkg : iconCache.keySet()) {
            final File file = pkg2path(activity, pkg);
            Log.i("Cache file", file.getAbsolutePath() + " | Exists: " + file.exists());
            if (file.exists()) {
                file.delete();
            }
        }
        clearIconCache();
    }

    public void setAppIconType(ViewHolder holder, boolean isBanner) {
        ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams) holder.imageView.getLayoutParams();
        holder.hasBanner = isBanner;
        if(isBanner) {
            params.height = (int)(itemScale * 0.5625);
            params.rightMargin = 0;
            params.topMargin = 0;
            params.bottomMargin = 0;
            holder.imageView.setLayoutParams(params);
            holder.imageView.setScaleType(ImageView.ScaleType.CENTER_CROP);
            holder.textView.setVisibility(View.INVISIBLE);
        } else {
            params.height = mainActivityContext.getPixelFromDip(40);
            params.rightMargin = mainActivityContext.getPixelFromDip(15);
            params.bottomMargin = mainActivityContext.getPixelFromDip(15);
            params.topMargin = (int)(itemScale * 0.5625 - params.height - params.bottomMargin);
            holder.imageView.setLayoutParams(params);
            holder.imageView.setScaleType(ImageView.ScaleType.FIT_END);
            holder.textView.setVisibility(View.VISIBLE);
        }
    }

    public boolean updateIcon(ViewHolder holder, File file, String pkg) {
        try {
            Drawable drawable = Drawable.createFromPath(file.getAbsolutePath());
            if (drawable != null) {
                holder.imageView.setImageDrawable(drawable);
                iconCache.put(pkg, drawable);
                setAppIconType(holder, true);

                return true;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }

    private void downloadIcon(final Activity activity, String pkg, @SuppressWarnings("unused") String name, final Runnable callback) {
        final String device = Utils.isOculusHeadset() ? "oculus" : (Utils.isMagicLeapHeadset() ? "leap" : "pico");
        final File file = pkg2path(activity, "banners." + pkg);
        new Thread(() -> {
            try {
                synchronized (pkg) {
                    if (downloadIconFromUrl(ICONS_URL + pkg + "/banner.png?useBackup=1&device="+device, file)) {
                        activity.runOnUiThread(callback);
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }

    protected static boolean downloadIconFromUrl(String url, File outputFile) {
        try {
            return saveStream(new URL(url).openStream(), outputFile);
        } catch (Exception e) {
            return false;
        }
    }

    protected static boolean saveStream(InputStream is, File outputFile) {
        try {
            DataInputStream dis = new DataInputStream(is);

            int length;
            byte[] buffer = new byte[65536];
            FileOutputStream fos = new FileOutputStream(outputFile);
            while ((length = dis.read(buffer)) > 0) {
                fos.write(buffer, 0, length);
            }
            fos.flush();
            fos.close();

            if (!isImageFileComplete(outputFile)) {
                return false;
            }

            Bitmap bitmap = BitmapFactory.decodeFile(outputFile.getAbsolutePath());
            if (bitmap != null) {
                int width = bitmap.getWidth();
                int height = bitmap.getHeight();
                float aspectRatio = (float) width / height;
                if (width > 512) {
                    width = 512;
                    height = Math.round(width / aspectRatio);
                    bitmap = Bitmap.createScaledBitmap(bitmap, width, height, false);
                }
                try {
                    fos = new FileOutputStream(outputFile);
                    bitmap.compress(Bitmap.CompressFormat.WEBP, 75, fos);
                    fos.close();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }

            return true;
        } catch (Exception e) {
            return false;
        }
    }

}
