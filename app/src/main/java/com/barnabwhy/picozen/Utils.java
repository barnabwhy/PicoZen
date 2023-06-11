package com.barnabwhy.picozen;

import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;

import androidx.palette.graphics.Palette;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.function.Function;

public class Utils {

    protected Utils() {

    }

    public static boolean isMagicLeapHeadset() {
        String vendor = Build.MANUFACTURER.toUpperCase();
        return vendor.startsWith("MAGIC LEAP");
    }

    public static boolean isOculusHeadset() {
        String vendor = Build.MANUFACTURER.toUpperCase();
        return vendor.startsWith("META") || vendor.startsWith("OCULUS");
    }

    public static boolean isPicoHeadset() {
        String vendor = Build.MANUFACTURER.toUpperCase();
        return vendor.startsWith("PICO") || vendor.startsWith("PİCO") || true;
    }

    // Generate palette asynchronously and use it on a different
    // thread using onGenerated()
    public static void createIconPaletteAsync(Bitmap bitmap, Resources res, Function<GradientDrawable, Void> onGenerated) {
        Palette.from(bitmap).generate(p -> {
            // Use generated instance
            GradientDrawable gd = new GradientDrawable(
                    GradientDrawable.Orientation.TOP_BOTTOM,
                    new int[] { p.getVibrantColor(res.getColor(R.color.bg_med)), p.getDarkVibrantColor(res.getColor(R.color.bg_dark)) });
            gd.setCornerRadius(0f);
            onGenerated.apply(gd);
        });
    }

    public static Bitmap drawableToBitmap(Drawable drawable) {
        if (drawable instanceof BitmapDrawable) {
            BitmapDrawable bitmapDrawable = (BitmapDrawable) drawable;
            if(bitmapDrawable.getBitmap() != null) {
                return bitmapDrawable.getBitmap();
            }
        }

        Bitmap bitmap;
        if(drawable.getIntrinsicWidth() <= 0 || drawable.getIntrinsicHeight() <= 0) {
            bitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888); // Single color bitmap will be created of 1x1 pixel
        } else {
            bitmap = Bitmap.createBitmap(drawable.getIntrinsicWidth(), drawable.getIntrinsicHeight(), Bitmap.Config.ARGB_8888);
        }

        Canvas canvas = new Canvas(bitmap);
        drawable.setBounds(0, 0, canvas.getWidth(), canvas.getHeight());
        drawable.draw(canvas);
        return bitmap;
    }

    public static Bitmap getBitmapFromUrl(String imageUrl)
    {
        try
        {
            URL url = new URL(imageUrl);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setDoInput(true);
            connection.connect();
            InputStream input = connection.getInputStream();
            Bitmap bitmap = BitmapFactory.decodeStream(input);
            return bitmap;

        } catch (Exception e) {
            e.printStackTrace();
            return null;

        }
    }
}
