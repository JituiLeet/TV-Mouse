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


    public static String getSelectedFixed(Context c) {
        android.content.SharedPreferences p=c.getSharedPreferences(PREF, Context.MODE_PRIVATE);
        return p.getBoolean("fixed_shared", true) ? getSelected(c) : p.getString("fixed_selected", "default");
    }
    public static boolean isFixedShared(Context c) {
        return c.getSharedPreferences(PREF, Context.MODE_PRIVATE).getBoolean("fixed_shared", true);
    }
    public static void setFixedShared(Context c, boolean shared) {
        c.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit().putBoolean("fixed_shared", shared).apply();
        MouseService s=ServiceRegistry.get(); if(s!=null && s.isScroll()) s.applyAppearance();
    }
    public static void selectFixed(Context c, String id) {
        c.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit().putString("fixed_selected", id).putBoolean("fixed_shared", false).apply();
        MouseService s=ServiceRegistry.get(); if(s!=null && s.isScroll()) s.applyAppearance();
    }
    public static boolean isLinkShared(Context c) { return c.getSharedPreferences(PREF, Context.MODE_PRIVATE).getBoolean("link_shared", false); }
    public static void setLinkShared(Context c, boolean shared) {
        c.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit().putBoolean("link_shared", shared).apply();
        MouseService s=ServiceRegistry.get(); if(s!=null) s.applyAppearance();
    }
    public static String getSelectedLink(Context c) { return c.getSharedPreferences(PREF, Context.MODE_PRIVATE).getString("link_selected", "default"); }
    public static void selectLink(Context c, String id) {
        c.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit().putString("link_selected", id).putBoolean("link_shared", false).apply();
        MouseService s=ServiceRegistry.get(); if(s!=null) s.applyAppearance();
    }
    public static File selectedLinkFile(Context c) {
        if(isLinkShared(c)) return selectedFile(c);
        String id=getSelectedLink(c); if(!isCustom(id)) return null;
        try { int index=Integer.parseInt(id.substring("custom_".length())); if(index<0||index>=count(c))return null; String path=file(c,index); if(path.length()==0)return null; File f=new File(path); return f.isFile()?f:null; }
        catch(Exception e){return null;}
    }

    public static File selectedFixedFile(Context c) {
        if(isFixedShared(c)) return selectedFile(c);
        String id=getSelectedFixed(c);
        if(!isCustom(id)) return null;
        try {
            int index=Integer.parseInt(id.substring("custom_".length()));
            if(index<0 || index>=count(c)) return null;
            String path=file(c,index); if(path.length()==0)return null;
            File f=new File(path); return f.isFile()?f:null;
        } catch(Exception e){return null;}
    }


    public static boolean rename(Context c, int index, String newName) {
        if (index < 0 || index >= count(c) || newName == null) return false;
        String safe = newName.trim();
        if (safe.length() == 0) return false;
        if (safe.length() > 24) safe = safe.substring(0, 24);
        c.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit().putString("name_" + index, safe).apply();
        return true;
    }

    public static boolean delete(Context c, int index) {
        int total = count(c);
        if (index < 0 || index >= total) return false;
        android.content.SharedPreferences prefs = c.getSharedPreferences(PREF, Context.MODE_PRIVATE);
        String general = prefs.getString(SELECTED, "default");
        String fixed = prefs.getString("fixed_selected", "default");
        String link = prefs.getString("link_selected", "default");
        String deletedPath = prefs.getString("file_" + index, "");
        if (deletedPath.length() > 0) new File(deletedPath).delete();

        android.content.SharedPreferences.Editor e = prefs.edit();
        for (int i = index; i < total - 1; i++) {
            e.putString("name_" + i, prefs.getString("name_" + (i + 1), c.getString(R.string.appearance_fallback_name, i + 2)));
            e.putString("file_" + i, prefs.getString("file_" + (i + 1), ""));
        }
        e.remove("name_" + (total - 1)).remove("file_" + (total - 1)).putInt("count", total - 1);
        e.putString(SELECTED, remapId(general, index));
        e.putString("fixed_selected", remapId(fixed, index));
        e.putString("link_selected", remapId(link, index));
        e.apply();
        MouseService service = ServiceRegistry.get();
        if (service != null) service.applyAppearance();
        return true;
    }

    private static String remapId(String id, int deletedIndex) {
        if (!isCustom(id)) return id == null ? "default" : id;
        try {
            int n = Integer.parseInt(id.substring("custom_".length()));
            if (n == deletedIndex) return "default";
            if (n > deletedIndex) n--;
            return "custom_" + n;
        } catch (Exception e) { return "default"; }
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
        File out = new File(dir, "appearance_" + System.currentTimeMillis() + "_" + index + ".png");
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
