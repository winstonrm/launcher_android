package br.casa.sala;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/** A small per-profile watchlist backed by app-private preferences. */
final class Watchlist {
    static final class Entry extends Search.Title { }
    private Watchlist() {}

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences("watchlist" + Profiles.suffix(c), Context.MODE_PRIVATE);
    }

    static List<Entry> all(Context c) {
        List<Entry> out = new ArrayList<>();
        try {
            JSONArray a = new JSONArray(prefs(c).getString("items", "[]"));
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.getJSONObject(i); Entry e = new Entry();
                e.name = o.optString("name"); e.kind = o.optString("kind"); e.year = o.optString("year"); e.image = o.optString("image");
                e.kidsSafeUpToAge = o.optInt("kidsAge", 99);
                JSONArray apps = o.optJSONArray("apps");
                if (apps != null) for (int j = 0; j < apps.length(); j++) e.apps.add(apps.optString(j));
                if (!e.name.isEmpty()) out.add(e);
            }
        } catch (Exception ignored) { }
        return out;
    }

    static boolean contains(Context c, String name, String kind) {
        for (Entry e : all(c)) if (e.name.equals(name) && e.kind.equals(kind)) return true;
        return false;
    }

    static void toggle(Context c, Search.Title title) {
        List<Entry> entries = all(c);
        for (int i = 0; i < entries.size(); i++) {
            Entry e = entries.get(i);
            if (e.name.equals(title.name) && e.kind.equals(title.kind)) { entries.remove(i); save(c, entries); return; }
        }
        Entry e = new Entry(); e.name = title.name; e.kind = title.kind; e.year = title.year; e.image = title.image;
        e.kidsSafeUpToAge = title.kidsSafeUpToAge;
        e.apps.addAll(title.apps); entries.add(0, e);
        while (entries.size() > 100) entries.remove(entries.size() - 1);
        save(c, entries);
    }

    private static void save(Context c, List<Entry> entries) {
        JSONArray a = new JSONArray();
        try {
            for (Entry e : entries) {
                JSONArray apps = new JSONArray(); for (String pkg : e.apps) apps.put(pkg);
                a.put(new JSONObject().put("name", e.name).put("kind", e.kind).put("year", e.year)
                        .put("image", e.image).put("kidsAge", e.kidsSafeUpToAge).put("apps", apps));
            }
            prefs(c).edit().putString("items", a.toString()).apply();
        } catch (Exception ignored) { }
    }
}
