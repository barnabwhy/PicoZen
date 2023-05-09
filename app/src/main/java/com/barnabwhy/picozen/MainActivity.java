package com.barnabwhy.picozen;

import static androidx.core.content.FileProvider.getUriForFile;

import static com.barnabwhy.picozen.SettingsProvider.KEY_CURRENT_TAB;
import com.barnabwhy.picozen.BuildConfig;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.content.res.ResourcesCompat;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.GridView;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;

import org.json.JSONObject;
import org.w3c.dom.Text;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Timer;
import java.util.TimerTask;

import io.noties.markwon.AbstractMarkwonPlugin;
import io.noties.markwon.LinkResolverDef;
import io.noties.markwon.Markwon;
import io.noties.markwon.MarkwonConfiguration;
import io.noties.markwon.SoftBreakAddsNewLinePlugin;
import io.noties.markwon.core.CorePlugin;

public class MainActivity extends AppCompatActivity {
    private ConstraintLayout mainView;
    public static SharedPreferences sharedPreferences;
    private SettingsProvider settingsProvider;

    private GridView appGridView;

    private int selectedPage;

    private AppsAdapter.SORT_FIELD mSortField = AppsAdapter.SORT_FIELD.APP_NAME;
    private AppsAdapter.SORT_ORDER mSortOrder = AppsAdapter.SORT_ORDER.ASCENDING;

    public static boolean foregrounded() {
        ActivityManager.RunningAppProcessInfo appProcessInfo = new ActivityManager.RunningAppProcessInfo();
        ActivityManager.getMyMemoryState(appProcessInfo);
        return (appProcessInfo.importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND
                || appProcessInfo.importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_VISIBLE);
    }

    public static void reset(Context context) {
        try {
            Intent intent = new Intent(context, MainActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS);
            intent.putExtra("wasActive", foregrounded());
            context.startActivity(intent);
            ((Activity) context).finish();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

//    @Override
//    protected void onNewIntent(Intent intent)
//    {
//        super.onNewIntent(intent);
//        if(!intent.getBooleanExtra("wasActive", false)) {
//            this.moveTaskToBack(true);
//        }
//    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        checkForUpdates();

        sharedPreferences = getSharedPreferences(getPackageName() + "_preferences", Context.MODE_PRIVATE);
        settingsProvider = SettingsProvider.getInstance(this);

        mainView = findViewById(R.id.main_layout);
        mainView.setClipToOutline(true);

        findViewById(R.id.link_github).setOnClickListener(view -> {
            Uri uri = Uri.parse("https://www.github.com/barnabwhy/PicoZen/");
            Intent intent = new Intent(Intent.ACTION_VIEW, uri);
            startActivity(intent);
        });
        findViewById(R.id.link_discord).setOnClickListener(view -> {
            Uri uri = Uri.parse("https://discord.gg/yTvpqq7Rzk");
            Intent intent = new Intent(Intent.ACTION_VIEW, uri);
            startActivity(intent);
        });

        appGridView = findViewById(R.id.app_grid);

        mainView.post(() -> {
            View settingsBtn = findViewById(R.id.settings_btn);
            View settingsTooltip = findViewById(R.id.settings_tooltip);
            settingsBtn.setOnClickListener(view -> {
                openSettings();
            });

            settingsBtn.setOnHoverListener((View view, MotionEvent event) -> {
                if (event.getActionMasked() == MotionEvent.ACTION_HOVER_ENTER) {
                    view.setForeground(ResourcesCompat.getDrawable(getResources(), R.drawable.ic_settings_hover, getTheme()));
                    view.setBackground(ResourcesCompat.getDrawable(getResources(), R.drawable.bg_ico_btn_hover, getTheme()));
                    settingsTooltip.setVisibility(View.VISIBLE);
                } else if (event.getActionMasked() == MotionEvent.ACTION_HOVER_EXIT) {
                    view.setForeground(ResourcesCompat.getDrawable(getResources(), R.drawable.ic_settings, getTheme()));
                    view.setBackground(ResourcesCompat.getDrawable(getResources(), R.drawable.bg_ico_btn, getTheme()));
                    settingsTooltip.setVisibility(View.GONE);
                }
                return false;
            });
        });

        LinearLayout listView = findViewById(R.id.list_view);

        selectedPage = sharedPreferences.getInt(KEY_CURRENT_TAB, 0);
        final int[] pageList = { R.id.apps_page, R.id.tweaks_page, R.id.sideload_page, R.id.about_page };

        final int childCount = listView.getChildCount();
        for (int i = 0; i < childCount; i++) {
            if(i > 0 && i < 3)
                continue; // temp, while tweaks and sideload tools don't exist

            View v = listView.getChildAt(i);
            int finalI = i;
            if (finalI == selectedPage) {
                v.setBackground(ResourcesCompat.getDrawable(getResources(), R.drawable.bg_list_item_s, getTheme()));
                findViewById(pageList[i]).setVisibility(View.VISIBLE);
            } else {
                v.setBackground(ResourcesCompat.getDrawable(getResources(), R.drawable.bg_list_item, getTheme()));
                findViewById(pageList[i]).setVisibility(View.GONE);
            }

            v.setOnHoverListener((View view, MotionEvent event) -> {
                if (event.getActionMasked() == MotionEvent.ACTION_HOVER_ENTER) {
                    if (finalI == selectedPage) {
                        view.setBackground(ResourcesCompat.getDrawable(getResources(), R.drawable.bg_list_item_hover_s, getTheme()));
                    } else {
                        view.setBackground(ResourcesCompat.getDrawable(getResources(), R.drawable.bg_list_item_hover, getTheme()));
                    }
                } else if (event.getActionMasked() == MotionEvent.ACTION_HOVER_EXIT) {
                    if (finalI == selectedPage) {
                        view.setBackground(ResourcesCompat.getDrawable(getResources(), R.drawable.bg_list_item_s, getTheme()));
                    } else {
                        v.setBackground(ResourcesCompat.getDrawable(getResources(), R.drawable.bg_list_item, getTheme()));
                    }
                }
                return false;
            });

            v.setOnClickListener(view -> {
                selectPage(finalI);
            });

            appGridView.setAdapter(new AppsAdapter(this));
        }

        // Set sort button
        mSortField = AppsAdapter.SORT_FIELD.values()[sharedPreferences.getInt(SettingsProvider.KEY_SORT_FIELD, 0)];
        mSortOrder = AppsAdapter.SORT_ORDER.values()[sharedPreferences.getInt(SettingsProvider.KEY_SORT_ORDER, 0)];
        Spinner sortSpinner = findViewById(R.id.sort);
        ArrayAdapter<CharSequence> sortAdapter = ArrayAdapter.createFromResource(this, R.array.sort_options, R.layout.spinner_item);
        sortAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        sortSpinner.setAdapter(sortAdapter);
        sortSpinner.setSelection(sharedPreferences.getInt(SettingsProvider.KEY_SORT_SPINNER, 0));
        sortSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int pos, long id) {
                switch (pos) {
                    //case 0 = default
                    case 1:
                        mSortField = AppsAdapter.SORT_FIELD.APP_NAME;
                        mSortOrder = AppsAdapter.SORT_ORDER.DESCENDING;
                        break;
                    case 2:
                        mSortField = AppsAdapter.SORT_FIELD.RECENT_DATE;
                        mSortOrder = AppsAdapter.SORT_ORDER.ASCENDING;
                        break;
                    case 3:
                        mSortField = AppsAdapter.SORT_FIELD.RECENT_DATE;
                        mSortOrder = AppsAdapter.SORT_ORDER.DESCENDING;
                        break;
                    case 4:
                        mSortField = AppsAdapter.SORT_FIELD.INSTALL_DATE;
                        mSortOrder = AppsAdapter.SORT_ORDER.ASCENDING;
                        break;
                    case 5:
                        mSortField = AppsAdapter.SORT_FIELD.INSTALL_DATE;
                        mSortOrder = AppsAdapter.SORT_ORDER.DESCENDING;
                        break;
                    default:
                        mSortField = AppsAdapter.SORT_FIELD.APP_NAME;
                        mSortOrder = AppsAdapter.SORT_ORDER.ASCENDING;
                        break;
                }

                //update UI
                if (appGridView.getAdapter() != null) {
                    ((AppsAdapter) appGridView.getAdapter()).sort(mSortField, mSortOrder);
                }

                //persist sort settings
                SharedPreferences.Editor editor = sharedPreferences.edit();
                editor.putInt(SettingsProvider.KEY_SORT_SPINNER, pos);
                editor.putInt(SettingsProvider.KEY_SORT_FIELD, mSortField.ordinal());
                editor.putInt(SettingsProvider.KEY_SORT_ORDER, mSortOrder.ordinal());
                editor.apply();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {

            }
        });

        // Set group button
        Spinner groupSpinner = findViewById(R.id.group);
        ArrayAdapter<CharSequence> groupApdater = ArrayAdapter.createFromResource(this, R.array.group_options, R.layout.spinner_item);
        groupApdater.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        groupSpinner.setAdapter(groupApdater);
        groupSpinner.setSelection(sharedPreferences.getInt(SettingsProvider.KEY_GROUP_SPINNER, 0));
        groupSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int pos, long id) {
                //persist sort settings
                SharedPreferences.Editor editor = sharedPreferences.edit();
                editor.putInt(SettingsProvider.KEY_GROUP_SPINNER, pos);
                editor.apply();

                //update UI
                ((AppsAdapter)appGridView.getAdapter()).updateAppList();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {

            }
        });
    private void selectPage(int i) {
        final int[] pageList = { R.id.apps_page, R.id.tweaks_page, R.id.sideload_page, R.id.about_page };
        LinearLayout listView = findViewById(R.id.list_view);
        View v = listView.getChildAt(i);
        listView.getChildAt(selectedPage).setBackground(ResourcesCompat.getDrawable(getResources(), R.drawable.bg_list_item, getTheme()));
        findViewById(pageList[selectedPage]).setVisibility(View.GONE);
        selectedPage = i;
        sharedPreferences.edit().putInt(KEY_CURRENT_TAB, i).apply();
        v.setBackground(ResourcesCompat.getDrawable(getResources(), R.drawable.bg_list_item_s, getTheme()));
        findViewById(pageList[i]).setVisibility(View.VISIBLE);
    }

    private void checkForUpdates() {
        Context mainContext = this;
        Thread thread = new Thread() {
            @Override
            public void run() {
                try {
                    URL u = new URL("https://api.github.com/repos/barnabwhy/PicoZen/releases/tags/" + BuildConfig.VERSION_NAME);
                    InputStream stream = u.openStream();
                    int bufferSize = 1024;
                    char[] buffer = new char[bufferSize];
                    StringBuilder out = new StringBuilder();
                    Reader in = new InputStreamReader(stream, StandardCharsets.UTF_8);
                    for (int numRead; (numRead = in.read(buffer, 0, buffer.length)) > 0; ) {
                        out.append(buffer, 0, numRead);
                    }
                    JSONObject json = new JSONObject(out.toString());
                    String str = json.getString("body");
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            final Markwon markwon = Markwon.builder(mainContext)
                                    .usePlugin(CorePlugin.create())
                                    .usePlugin(SoftBreakAddsNewLinePlugin.create())
                                    .build();
                            markwon.setMarkdown((TextView)findViewById(R.id.changelog), str);
                        }
                    });
                } catch (Exception e) {
                    Log.e("Error", e.toString());
                }

                try {
                    URL u = new URL("https://api.github.com/repos/barnabwhy/PicoZen/releases/latest");
                    InputStream stream = u.openStream();
                    int bufferSize = 1024;
                    char[] buffer = new char[bufferSize];
                    StringBuilder out = new StringBuilder();
                    Reader in = new InputStreamReader(stream, StandardCharsets.UTF_8);
                    for (int numRead; (numRead = in.read(buffer, 0, buffer.length)) > 0; ) {
                        out.append(buffer, 0, numRead);
                    }
                    JSONObject json = new JSONObject(out.toString());
                    String str = json.getString("tag_name");
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            TextView verText = (TextView)findViewById(R.id.new_version);
                            verText.setText(String.format(getResources().getString(R.string.new_version_available), str));
                            verText.setVisibility(str.equals(BuildConfig.VERSION_NAME) ? View.GONE : View.VISIBLE);
                            verText.setOnClickListener(view -> {
                                Uri uri = Uri.parse("https://www.github.com/barnabwhy/PicoZen/releases/"+str);
                                Intent intent = new Intent(Intent.ACTION_VIEW, uri);
                                startActivity(intent);
                            });

                        }
                    });
                } catch (Exception e) {
                    Log.e("Error", e.toString());
                }
            }
        };
        thread.start();
    }

    @SuppressLint("ClickableViewAccessibility")
    private void openSettings() {
        View dialogOverlay = findViewById(R.id.dialog_overlay);
        dialogOverlay.setVisibility(View.VISIBLE);

        AlertDialog.Builder builder = new AlertDialog.Builder(this, R.style.CustomDialog);
        builder.setView(R.layout.dialog_settings);
        AlertDialog dialog = builder.create();

        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.show();

        WindowManager.LayoutParams lp = new WindowManager.LayoutParams();
        lp.copyFrom(dialog.getWindow().getAttributes());
        lp.width = 560;
        lp.height = 640;

        dialog.getWindow().setAttributes(lp);
        dialog.findViewById(R.id.layout).requestLayout();
        dialog.getWindow().setBackgroundDrawableResource(R.drawable.bg_dialog);

        View accessibilityService = dialog.findViewById(R.id.configure_accessibility);
        accessibilityService.setOnClickListener(view -> {
            ButtonManager.isAccessibilityInitialized(this);
            ButtonManager.requestAccessibility(this);
        });

        View iconRefresh = dialog.findViewById(R.id.refresh_icon_cache);
        iconRefresh.setOnClickListener(view -> {
            AppsAdapter.clearAllIcons(this);
            ((AppsAdapter) appGridView.getAdapter()).notifyDataSetChanged();
        });

        AppsAdapter appsAdapter = (AppsAdapter)appGridView.getAdapter();

        View toggleEditMode = dialog.findViewById(R.id.toggle_edit_mode);
        ((TextView)toggleEditMode.findViewById(R.id.toggle_edit_mode_btn)).setText(appsAdapter.getEditModeEnabled() ? R.string.disable : R.string.enable);

        toggleEditMode.setOnClickListener(view -> {
            appsAdapter.toggleEditMode();
            ((TextView)toggleEditMode.findViewById(R.id.toggle_edit_mode_btn)).setText(appsAdapter.getEditModeEnabled() ? R.string.disable : R.string.enable);
        });

        View checkUpdatesBtn = dialog.findViewById(R.id.check_for_updates_btn);
        checkUpdatesBtn.setOnClickListener(view -> {
            checkForUpdates();
            selectPage(3); // go to about
            dialog.dismiss();
        });

        View closeBtn = dialog.findViewById(R.id.close_btn);
        closeBtn.setOnClickListener(view -> {
            dialog.dismiss();
        });

        closeBtn.setOnHoverListener((View view, MotionEvent event) -> {
            if (event.getActionMasked() == MotionEvent.ACTION_HOVER_ENTER) {
                view.setForeground(ResourcesCompat.getDrawable(getResources(), R.drawable.ic_close_hover, getTheme()));
                view.setBackground(ResourcesCompat.getDrawable(getResources(), R.drawable.bg_ico_btn_hover, getTheme()));
            } else if (event.getActionMasked() == MotionEvent.ACTION_HOVER_EXIT) {
                view.setForeground(ResourcesCompat.getDrawable(getResources(), R.drawable.ic_close, getTheme()));
                view.setBackground(ResourcesCompat.getDrawable(getResources(), R.drawable.bg_ico_btn, getTheme()));
            }
            return false;
        });

        TextView versionText = dialog.findViewById(R.id.version_number);
        versionText.setText(String.format(getResources().getString(R.string.version_text), BuildConfig.VERSION_NAME));

        dialog.setOnDismissListener(d -> {
            dialogOverlay.setVisibility(View.GONE);
        });
    }

    private final Handler handler = new Handler();

    public boolean openApp(ApplicationInfo app) {
        settingsProvider.updateRecent(app.packageName, System.currentTimeMillis());
        return runApp(this, app, false);
    }

    private boolean runApp(Context context, ApplicationInfo app, boolean multiwindow) {
        Intent launchIntent = context.getPackageManager().getLaunchIntentForPackage(app.packageName);
        if (launchIntent != null) {
            context.getApplicationContext().startActivity(launchIntent);
        } else {
            Log.e("runApp", "Failed to launch");
            return false;
        }
        return true;
    }
}