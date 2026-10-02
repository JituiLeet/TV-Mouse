package com.jituileet.tvmouse;

import android.content.Context;
import android.graphics.BitmapFactory;
import android.net.Uri;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;

/** Stores user-created mouse pointer appearances without external libraries. */
public final class AppearanceManager {
    private static final String PREF = "appearances";
    private static final String SELECTED = "selected";
    private static final int MAX = 32;
    private static final int REQUIRED_SIZE = 128;

    private AppearanceManager() {}

    public static String getSelected(Context c) {
        return c.getSharedPreferences(PREF, Context.MODE_PRIVATE).getString(SELECTED, "default");
    }

    public static void select(Context c, String id) {
        c.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit().putString(SELECTED, id).apply();
        MouseService s = ServiceRegistry.get();
        if (s != null) s.applyAppearance();
    }

    public static int count(Context c) {
        return c.getSharedPreferences(PREF, Context.MODE_PRIVATE).getInt("count", 0);
    }

    public static String name(Context c, int index) {
        return c.getSharedPreferences(PREF, Context.MODE_PRIVATE).getString("name_" + index, c.getString(R.string.appearance_fallback_name, index + 1));
    }

    public static String file(Context c, int index) {
        return c.getSharedPreferences(PREF, Context.MODE_PRIVATE).getString("file_" + index, "");
    }

    public static String id(int index) { return "custom_" + index; }

    public static boolean isCustom(String id) { return id != null && id.startsWith("custom_"); }

    public static String selectedDisplayName(Context c) {
        String id = getSelected(c);
        if ("default".equals(id)) return c.getString(R.string.default_appearance);
        try {
            int index = Integer.parseInt(id.substring("custom_".length()));
            if (index >= 0 && index < count(c)) return name(c, index);
        } catch (Exception ignored) {}
        return c.getString(R.string.default_appearance);
    }

    public static Result importImage(Context c, Uri uri, String displayName) {
        if (uri == null) return Result.error(c.getString(R.string.appearance_error_no_image));
        if (count(c) >= MAX) return Result.error(c.getString(R.string.appearance_error_max, MAX));
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inJustDecodeBounds = true;
        InputStream in = null;
        try {
            in = c.getContentResolver().openInputStream(uri);
            BitmapFactory.decodeStream(in, null, o);
        } catch (Exception e) {
            return Result.error(c.getString(R.string.appearance_error_read));
        } finally { close(in); }
        if (o.outWidth != REQUIRED_SIZE || o.outHeight != REQUIRED_SIZE) {
            return Result.error(c.getString(R.string.appearance_error_size));
        }
        if (o.outMimeType == null || (!o.outMimeType.equals("image/png") && !o.outMimeType.equals("image/jpeg"))) {
            return Result.error(c.getString(R.string.appearance_error_type));
        }
        String safe = displayName == null ? "" : displayName.trim();
        if (safe.length() == 0) return Result.error(c.getString(R.string.appearance_error_name));
        if (safe.length() > 24) safe = safe.substring(0, 24);

        int index = count(c);
        File dir = new File(c.getFilesDir(), "appearances");
        if (!dir.exists() && !dir.mkdirs()) return Result.error(c.getString(R.string.appearance_error_directory));
        File out = new File(dir, "appearance_" + index + ".png");
        InputStream src = null; OutputStream dst = null;
        try {
            src = c.getContentResolver().openInputStream(uri);
            dst = new FileOutputStream(out);
            byte[] buf = new byte[8192]; int n;
            while ((n = src.read(buf)) != -1) dst.write(buf, 0, n);
        } catch (Exception e) {
            out.delete();
            return Result.error(c.getString(R.string.appearance_error_save));
        } finally { close(src); close(dst); }

        c.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit()
            .putInt("count", index + 1)
            .putString("name_" + index, safe)
            .putString("file_" + index, out.getAbsolutePath())
            .apply();
        select(c, id(index));
        return Result.ok(id(index), safe);
    }

    public static File selectedFile(Context c) {
        String id = getSelected(c);
        if (!isCustom(id)) return null;
        try {
            int index = Integer.parseInt(id.substring("custom_".length()));
            if (index < 0 || index >= count(c)) return null;
            String path = file(c, index);
            if (path.length() == 0) return null;
            File f = new File(path);
            return f.isFile() ? f : null;
        } catch (Exception e) { return null; }
    }

    private static void close(java.io.Closeable c) { if (c != null) try { c.close(); } catch (Exception ignored) {} }

    public static final class Result {
        public final boolean success; public final String message; public final String id; public final String name;
        private Result(boolean success, String message, String id, String name) { this.success=success; this.message=message; this.id=id; this.name=name; }
        static Result ok(String id, String name) { return new Result(true, "", id, name); }
        static Result error(String m) { return new Result(false, m, null, null); }
    }
}
