package br.casa.sala;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Popular titles on the streaming apps installed on the TV (Brazil), from TMDB. Fetched at most every 12 h on a
 * background thread; posters are cached as small JPEGs, so browsing the home screen never touches the network.
 */
final class Tmdb {
    static final class Pick {
        String title, kind, year, overview, pkg, provider, backdrop, thumb, path, rating;
        double popularity, vote;
    }

    private static final long MAX_AGE = 6 * 3600_000L;
    private static final String CACHE = "tmdb6.json";
    private static final String API = "https://api.themoviedb.org/3";

    /** Words in a TMDB provider name -> the app package on the TV. Checked in order; first match wins. */
    private static final String[][] APPS = {
            {"netflix", "com.netflix.ninja"},
            {"prime video", "com.amazon.amazonvideo.livingroom"},
            {"disney", "com.disney.disneyplus"},
            {"globoplay", "com.globo.globotv"},
            {"hbo max", "com.wbd.stream"},
            {"max", "com.wbd.stream"},
            {"apple tv", "com.apple.atve.androidtv.appletv"},
            {"crunchyroll", "com.crunchyroll.crunchyroid"},
            {"paramount", "com.cbs.ca"},
            {"claro", "br.com.claro.now.smarttvclient"},
            {"iqiyi", "com.iqiyi.i18n.tv"},
            {"viki", "com.viki.android"},
            {"youtube", "com.google.android.youtube.tv"},
            {"twitch", "tv.twitch.android.app"},
    };

    static String appFor(String providerName) {
        String n = providerName.toLowerCase();
        for (String[] a : APPS) {
            boolean match = a[0].equals("max") ? n.equals("max") || n.startsWith("max ") : n.contains(a[0]);
            if (match) return a[1];
        }
        return null;
    }

    private Tmdb() {}

    static boolean enabled() {
        return Config.TMDB_KEY != null && !Config.TMDB_KEY.isEmpty();
    }

    static List<Pick> cached(Context c, int age) {
        List<Pick> out = new ArrayList<>();
        if (!enabled()) {
            clearCache(c);
            return out;
        }
        try {
            JSONArray a = new JSONObject(read(new File(c.getFilesDir(), cacheName(c, age)))).getJSONArray("picks");
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.getJSONObject(i);
                Pick p = new Pick();
                p.title = o.getString("title");
                p.kind = o.getString("kind");
                p.year = o.optString("year");
                p.overview = o.optString("overview");
                p.vote = o.optDouble("vote");
                p.backdrop = o.getString("backdrop");
                p.thumb = o.getString("thumb");
                p.pkg = o.getString("pkg");
                p.provider = o.getString("provider");
                p.rating = o.optString("rating", "");
                // Recheck cached recommendations too. Old or unclassified series
                // must never slip into a child profile through a stale cache.
                if (age > 0 && "Série".equals(p.kind) && !Search.allowedRating(p.rating, age)) continue;
                if (new File(p.backdrop).exists() && new File(p.thumb).exists()) out.add(p);
            }
        } catch (Exception ignored) {
        }
        return out;
    }

    /** Remove stale TMDB data when this build has no key, so an unconfigured install stays empty. */
    private static void clearCache(Context c) {
        File[] files = c.getFilesDir().listFiles();
        if (files != null) for (File f : files) {
            String name = f.getName();
            if (name.startsWith("tmdb6") || name.startsWith("tmdb_")) f.delete();
        }
        c.getSharedPreferences("tmdb", Context.MODE_PRIVATE).edit().clear().apply();
    }

    /** Refreshes the cache if it is old. Returns true when new picks were saved. Call off the main thread. */
    /** Kids profiles get their own selection, filtered by the Brazilian age rating. */
    private static String cacheName(Context c, int age) {
        String suffix = age > 0 ? "_k" + age : "";
        return CACHE.replace(".json", suffix + "_" + (L10n.isEnglish(c) ? "en" : "pt") + ".json");
    }

    private static String kidsFilter(String kind, int age) {
        if (age <= 0) return "";
        if (kind.equals("movie")) {
            String rating = ratingLimit(age);
            return "&certification_country=BR&certification.lte=" + rating + "&region=BR"
                    + (age <= 10 ? "&with_genres=16%7C10751" : "");
        }
        // Discover TV does not support certification filters; validate each result
        // against /tv/{id}/content_ratings below instead.
        if (age <= 10) return "&with_genres=10762%7C16%7C10751";
        if (age <= 12) return "&with_genres=10762%7C16%7C10751%7C10759%7C35%7C12";
        return "";
    }

    /** TMDB expects Brazilian certificate labels, while Kids settings accept every age from 3 to 17. */
    private static String ratingLimit(int age) {
        if (age < 10) return "L";
        if (age < 12) return "10";
        if (age < 14) return "12";
        if (age < 16) return "14";
        return "16";
    }

    static boolean refresh(Context c, Set<String> installed, int age) {
        if (!enabled()) return false;
        File cache = new File(c.getFilesDir(), cacheName(c, age));
        // Refresh every few hours, and right away when a streaming app is installed or removed.
        String apps = coveredApps(installed);
        boolean sameApps = apps.equals(c.getSharedPreferences("tmdb", Context.MODE_PRIVATE).getString("apps" + age + (L10n.isEnglish(c) ? "_en" : "_pt"), ""));
        if (cache.exists() && sameApps && System.currentTimeMillis() - cache.lastModified() < MAX_AGE) return false;
        try {
            Map<Integer, String[]> providers = new HashMap<>(); // id -> {pkg, name}
            JSONArray list = get("/watch/providers/tv?watch_region=BR&language=" + L10n.language(c)).getJSONArray("results");
            for (int i = 0; i < list.length(); i++) {
                JSONObject o = list.getJSONObject(i);
                String name = o.getString("provider_name");
                String pkg = appFor(name);
                // One provider per app (TMDB also lists "with ads" variants of the same service).
                if (pkg != null && installed.contains(pkg) && !providers.containsValue(null) && !hasPkg(providers, pkg)) {
                    providers.put(o.getInt("provider_id"), new String[]{pkg, name});
                }
            }
            StringBuilder names = new StringBuilder();
            for (String[] v : providers.values()) names.append(v[1]).append(", ");
            android.util.Log.i("Sala", "TMDB apps cobertos (" + providers.size() + "): " + names);
            List<List<Pick>> perProvider = new ArrayList<>();
            for (Map.Entry<Integer, String[]> e : providers.entrySet()) {
                List<Pick> mine = new ArrayList<>();
                for (String kind : new String[]{"tv", "movie"}) {
                    JSONArray r = get("/discover/" + kind + "?watch_region=BR&language=" + L10n.language(c) + "&sort_by=popularity.desc"
                            + "&with_watch_monetization_types=flatrate&with_watch_providers=" + e.getKey() + kidsFilter(kind, age)).getJSONArray("results");
                    int taken = 0;
                    int checkedRatings = 0;
                    for (int i = 0; i < r.length() && taken < 6; i++) {
                        JSONObject o = r.getJSONObject(i);
                        long tmdbId = o.optLong("id", 0);
                        String rating = o.optString("certification", "");
                        if (kind.equals("tv") && age > 0) {
                            // Bound network work per provider while continuing past
                            // unrated/ineligible entries to fill the recommendation row.
                            if (checkedRatings >= 24) break;
                            checkedRatings++;
                            rating = brazilianTvRating(c, tmdbId);
                            if (!Search.allowedRating(rating, age)) continue;
                        }
                        String path = o.optString("backdrop_path", "");
                        if (path.isEmpty() || "null".equals(path)) continue;
                        Pick p = new Pick();
                        p.title = o.optString(kind.equals("tv") ? "name" : "title");
                        p.kind = kind.equals("tv") ? "Série" : "Filme";
                        String date = o.optString(kind.equals("tv") ? "first_air_date" : "release_date", "");
                        p.year = date.length() >= 4 ? date.substring(0, 4) : "";
                        p.overview = o.optString("overview", "");
                        p.vote = o.optDouble("vote_average", 0);
                        p.rating = rating;
                        p.popularity = o.optDouble("popularity");
                        p.pkg = e.getValue()[0];
                        p.provider = e.getValue()[1];
                        p.path = path;
                        mine.add(p);
                        taken++;
                    }
                }
                mine.sort((x, y) -> Double.compare(y.popularity, x.popularity));
                perProvider.add(mine);
            }
            // Round robin, so every app gets a turn near the front.
            List<Pick> picks = new ArrayList<>();
            for (int i = 0; picks.size() < 120; i++) {
                boolean any = false;
                for (List<Pick> l : perProvider) {
                    if (i < l.size()) {
                        picks.add(l.get(i));
                        any = true;
                    }
                }
                if (!any) break;
            }
            JSONArray out = new JSONArray();
            int index = 0;
            for (Pick p : picks) {
                boolean hero = index++ < 10;
                String id = Integer.toHexString(p.path.hashCode());
                File big = new File(c.getFilesDir(), "tmdb_h" + id + ".jpg");
                File small = new File(c.getFilesDir(), "tmdb_m" + id + ".jpg");
                try {
                    if (!small.exists()) download("https://image.tmdb.org/t/p/w780" + p.path, small);
                    if (hero && !big.exists()) download("https://image.tmdb.org/t/p/w1280" + p.path, big);
                } catch (Exception skip) {
                    continue;
                }
                out.put(new JSONObject().put("title", p.title).put("kind", p.kind).put("year", p.year)
                        .put("overview", p.overview).put("vote", p.vote).put("backdrop", hero ? big.getPath() : small.getPath())
                        .put("thumb", small.getPath()).put("pkg", p.pkg).put("provider", p.provider)
                        .put("rating", p.rating == null ? "" : p.rating));
            }
            try (FileOutputStream w = new FileOutputStream(cache)) {
                w.write(new JSONObject().put("picks", out).toString().getBytes(StandardCharsets.UTF_8));
            }
            c.getSharedPreferences("tmdb", Context.MODE_PRIVATE).edit().putString("apps" + age + (L10n.isEnglish(c) ? "_en" : "_pt"), apps).apply();
            File[] files = c.getFilesDir().listFiles();
            if (files != null) {
                String keep = out.toString();
                for (File f : files) {
                    // Other profiles' selections share this folder: only drop images unused for a few days.
                    boolean old = System.currentTimeMillis() - f.lastModified() > 3 * 24 * 3600_000L;
                    if (f.getName().startsWith("tmdb_") && old && !keep.contains(f.getName())) f.delete();
                }
            }
            return true;
        } catch (Exception e) {
            android.util.Log.w("Sala", "TMDB: " + e);
            return false;
        }
    }

    /** TV Discover has no Brazilian age-certification filter, so check the title's BR rating. */
    private static String brazilianTvRating(Context c, long id) {
        if (id <= 0) return "";
        String rating = null;
        File cache = new File(c.getFilesDir(), "tmdb_rating_tv_" + id + ".txt");
        if (cache.exists()) {
            try (InputStream in = new java.io.FileInputStream(cache)) {
                rating = new String(readAll(in), StandardCharsets.UTF_8);
            } catch (Exception ignored) {}
        }
        if (rating == null) {
            try {
                JSONArray countries = get("/tv/" + id + "/content_ratings?x=1").getJSONArray("results");
                for (int i = 0; i < countries.length(); i++) {
                    JSONObject country = countries.getJSONObject(i);
                    if ("BR".equals(country.optString("iso_3166_1"))) {
                        rating = country.optString("rating", "").trim();
                        break;
                    }
                }
                if (rating == null || rating.isEmpty()) rating = "?";
                try (FileOutputStream out = new FileOutputStream(cache)) {
                    out.write(rating.getBytes(StandardCharsets.UTF_8));
                }
            } catch (Exception e) {
                android.util.Log.d("Sala", "Classificação BR indisponível para recomendação de série: " + e);
                return "";
            }
        }
        return rating == null || "?".equals(rating) ? "" : rating;
    }

    /** The streaming apps on the TV that TMDB can fill, as a stable string to notice changes. */
    private static String coveredApps(Set<String> installed) {
        java.util.TreeSet<String> s = new java.util.TreeSet<>();
        for (String[] a : APPS) if (installed.contains(a[1])) s.add(a[1]);
        return s.toString();
    }

    private static boolean hasPkg(Map<Integer, String[]> m, String pkg) {
        for (String[] v : m.values()) if (v[0].equals(pkg)) return true;
        return false;
    }

    static JSONObject get(String path) throws Exception {
        boolean bearer = Config.TMDB_KEY.length() > 40;
        URL u = new URL(API + path + (bearer ? "" : "&api_key=" + Config.TMDB_KEY));
        HttpURLConnection h = (HttpURLConnection) u.openConnection();
        h.setConnectTimeout(8000);
        h.setReadTimeout(10000);
        h.setRequestProperty("Accept", "application/json");
        if (bearer) h.setRequestProperty("Authorization", "Bearer " + Config.TMDB_KEY);
        try (InputStream in = h.getInputStream()) {
            return new JSONObject(new String(readAll(in), StandardCharsets.UTF_8));
        } finally {
            h.disconnect();
        }
    }

    static void download(String url, File to) throws Exception {
        HttpURLConnection h = (HttpURLConnection) new URL(url).openConnection();
        h.setConnectTimeout(8000);
        h.setReadTimeout(15000);
        try (InputStream in = h.getInputStream(); FileOutputStream out = new FileOutputStream(to)) {
            out.write(readAll(in));
        } finally {
            h.disconnect();
        }
    }

    private static byte[] readAll(InputStream in) throws Exception {
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        byte[] buf = new byte[16384];
        for (int n; (n = in.read(buf)) > 0; ) b.write(buf, 0, n);
        return b.toByteArray();
    }

    private static String read(File f) throws Exception {
        try (InputStream in = new java.io.FileInputStream(f)) {
            return new String(readAll(in), StandardCharsets.UTF_8);
        }
    }
}
