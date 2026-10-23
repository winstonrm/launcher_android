package br.casa.sala;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** What was played in each app, captured from media sessions by MediaListener. Stored per package, newest first. */
final class History {
    static final String PREFS = "historico";
    static final String ACTION_UPDATED = "br.casa.sala.MEDIA_ITEM_UPDATED";

    /** History belongs to the profile that is watching. */
    static String prefs(Context c) {
        return PREFS + Profiles.suffix(c);
    }
    private static String prefs(Context c, String profileId) {
        return PREFS + Profiles.suffix(profileId);
    }

    /** The currently owned player stays assigned to the profile that opened it. */
    static String ownerForPackage(Context c, String pkg) {
        SharedPreferences owners = c.getSharedPreferences("historico_donos", Context.MODE_PRIVATE);
        String id = owners.getString(pkg, "");
        if (id.isEmpty()) {
            id = Profiles.currentId(c);
            owners.edit().putString(pkg, id).apply();
        }
        return id;
    }

    /** Called when a profile explicitly launches/resumes an app, assigning its playback to that profile. */
    static void claimPackage(Context c, String pkg) {
        c.getSharedPreferences("historico_donos", Context.MODE_PRIVATE).edit()
                .putString(pkg, Profiles.currentId(c)).apply();
    }
    private static final int MAX = 12;

    static final class Item {
        String pkg, title, sub, art, mediaId, mediaUri, profileId;
        long pos, dur, time;
        boolean playing;
    }

    private History() {}

    static synchronized void put(Context c, Item it) {
        if (it.profileId == null || it.profileId.isEmpty()) it.profileId = Profiles.currentId(c);
        SharedPreferences p = c.getSharedPreferences(prefs(c, it.profileId), Context.MODE_PRIVATE);
        try {
            if (it.playing) clearOtherPlayingItems(c, it.pkg, it.profileId);
            JSONArray old = new JSONArray(p.getString(it.pkg, "[]"));
            JSONArray out = new JSONArray();
            if (it.art == null || it.art.isEmpty()) {
                for (int i = 0; i < old.length(); i++) {
                    JSONObject o = old.getJSONObject(i);
                    if (it.title.equals(o.optString("title")) && !o.optString("art").isEmpty()) it.art = o.optString("art");
                }
                if (it.art == null || it.art.isEmpty()) it.art = artIfSaved(c, it.pkg, it.title, it.profileId);
            }
            out.put(toJson(it));
            for (int i = 0; i < old.length() && out.length() < MAX; i++) {
                JSONObject o = old.getJSONObject(i);
                if (!it.title.equals(o.optString("title"))) out.put(o);
            }
            p.edit().putString(it.pkg, out.toString()).apply();
        } catch (JSONException ignored) {
        }
    }

    /** Media playback is exclusive on this TV: don't leave old apps marked as playing. */
    private static void clearOtherPlayingItems(Context c, String currentPackage, String profileId) {
        String name = prefs(c, profileId);
        for (Map.Entry<String, ?> entry : c.getSharedPreferences(name, Context.MODE_PRIVATE).getAll().entrySet()) {
            String pkg = entry.getKey();
            if (pkg.equals(currentPackage) || !(entry.getValue() instanceof String)) continue;
            try {
                JSONArray old = new JSONArray((String) entry.getValue());
                boolean changed = false;
                for (int i = 0; i < old.length(); i++) {
                    JSONObject item = old.getJSONObject(i);
                    if (item.optBoolean("playing", false)) {
                        item.put("playing", false);
                        changed = true;
                    }
                }
                if (changed) c.getSharedPreferences(name, Context.MODE_PRIVATE).edit()
                        .putString(pkg, old.toString()).apply();
            } catch (JSONException ignored) { }
        }
    }

    static List<Item> get(Context c, String pkg) {
        return get(c, pkg, Profiles.currentId(c));
    }

    static List<Item> get(Context c, String pkg, String profileId) {
        List<Item> out = new ArrayList<>();
        try {
            JSONArray a = new JSONArray(c.getSharedPreferences(prefs(c, profileId), Context.MODE_PRIVATE).getString(pkg, "[]"));
            for (int i = 0; i < a.length(); i++) {
                Item it = fromJson(pkg, a.getJSONObject(i));
                it.profileId = profileId;
                if (it.art == null || it.art.isEmpty()) it.art = artIfSaved(c, pkg, it.title, profileId);
                out.add(it);
            }
        } catch (JSONException ignored) {
        }
        return out;
    }

    /** The newest item of every app. */
    static List<Item> latest(Context c) {
        List<Item> out = new ArrayList<>();
        for (Map.Entry<String, ?> e : c.getSharedPreferences(prefs(c), Context.MODE_PRIVATE).getAll().entrySet()) {
            List<Item> l = get(c, e.getKey(), Profiles.currentId(c));
            if (!l.isEmpty()) out.add(l.get(0));
        }
        return out;
    }

    /** Saves a small JPEG of the artwork once; returns its path. */
    static String saveArt(Context c, String pkg, String title, Bitmap b, String profileId) {
        if (b == null) return null;
        File f = artFile(c, pkg, title, profileId);
        if (f.exists()) return f.getPath();
        int w = Math.min(1280, b.getWidth());
        int h = Math.max(1, b.getHeight() * w / Math.max(1, b.getWidth()));
        try (FileOutputStream out = new FileOutputStream(f)) {
            Bitmap s = Bitmap.createScaledBitmap(b, w, h, true);
            s.compress(Bitmap.CompressFormat.JPEG, 82, out);
            if (s != b) s.recycle();
            return f.getPath();
        } catch (Exception e) {
            return null;
        }
    }

    private static File artFile(Context c, String pkg, String title, String profileId) {
        String owner = "p1".equals(profileId) ? "" : profileId;
        return new File(c.getFilesDir(), "art_" + Integer.toHexString((owner + pkg + title).hashCode()) + ".jpg");
    }

    /** A thumbnail saved before for this title, if any. */
    static String artIfSaved(Context c, String pkg, String title, String profileId) {
        File f = artFile(c, pkg, title, profileId);
        return f.exists() ? f.getPath() : "";
    }

    /** Remove a previously cached session/app icon when it was mistaken for content artwork. */
    static synchronized void clearArt(Context c, String pkg, String title) {
        clearArt(c, pkg, title, Profiles.currentId(c));
    }

    static synchronized void clearArt(Context c, String pkg, String title, String profileId) {
        artFile(c, pkg, title, profileId).delete();
        SharedPreferences p = c.getSharedPreferences(prefs(c, profileId), Context.MODE_PRIVATE);
        try {
            JSONArray old = new JSONArray(p.getString(pkg, "[]"));
            JSONArray out = new JSONArray();
            for (int i = 0; i < old.length(); i++) {
                JSONObject o = old.getJSONObject(i);
                if (title.equals(o.optString("title"))) o.remove("art");
                out.put(o);
            }
            p.edit().putString(pkg, out.toString()).apply();
        } catch (JSONException ignored) {
        }
    }

    /** Downloads a thumbnail the app gave as a link (YouTube does this) and keeps it like the others. */
    static String saveArtFromUrl(Context c, String pkg, String title, String url, String profileId) {
        File f = artFile(c, pkg, title, profileId);
        if (f.exists()) return f.getPath();
        try {
            java.net.HttpURLConnection h = (java.net.HttpURLConnection) new java.net.URL(url).openConnection();
            h.setConnectTimeout(6000);
            h.setReadTimeout(8000);
            try (java.io.InputStream in = h.getInputStream()) {
                Bitmap b = android.graphics.BitmapFactory.decodeStream(in);
                return saveArt(c, pkg, title, b, profileId);
            } finally {
                h.disconnect();
            }
        } catch (Exception e) {
            return null;
        }
    }

    private static JSONObject toJson(Item it) throws JSONException {
        return new JSONObject().put("title", it.title).put("sub", it.sub == null ? "" : it.sub)
                .put("art", it.art == null ? "" : it.art).put("pos", it.pos).put("dur", it.dur)
                .put("time", it.time).put("playing", it.playing)
                .put("mid", it.mediaId == null ? "" : it.mediaId).put("uri", it.mediaUri == null ? "" : it.mediaUri);
    }

    private static Item fromJson(String pkg, JSONObject o) {
        Item it = new Item();
        it.pkg = pkg;
        it.title = o.optString("title");
        it.sub = o.optString("sub");
        it.art = o.optString("art");
        it.pos = o.optLong("pos");
        it.dur = o.optLong("dur");
        it.time = o.optLong("time");
        it.playing = o.optBoolean("playing");
        it.mediaId = o.optString("mid");
        it.mediaUri = o.optString("uri");
        return it;
    }
}
