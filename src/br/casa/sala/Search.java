package br.casa.sala;

import android.app.SearchManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Search movies and series (TMDB, with where to watch) and installed apps. */
final class Search {
    static class Title {
        String name, kind, year, image;
        int kidsSafeUpToAge = 99;
        List<String> apps = new ArrayList<>(); // installed packages that carry it
    }



    private Search() {}

    /** Movies and series matching the query, and which of the installed apps have them in Brazil. Off the main thread. */
    static List<Title> titles(Context c, String q, Set<String> installed, int kidsAge) {
        List<Title> out = new ArrayList<>();
        if (!Tmdb.enabled()) return out;
        try {
            JSONArray r = Tmdb.get("/search/multi?language=" + L10n.language(c) + "&region=BR&include_adult=false&query="
                    + URLEncoder.encode(q, "UTF-8")).getJSONArray("results");
            File dir = new File(c.getCacheDir(), "busca");
            dir.mkdirs();
            for (int i = 0; i < r.length() && out.size() < 12; i++) {
                JSONObject o = r.getJSONObject(i);
                String type = o.optString("media_type");
                if (!type.equals("movie") && !type.equals("tv")) continue;
                if (kidsAge > 0 && (!forKids(o.optJSONArray("genre_ids"), kidsAge)
                        || !withinBrazilianRating(type, o.optLong("id"), kidsAge))) continue;
                String path = o.optString("backdrop_path", "");
                if (path.isEmpty() || path.equals("null")) continue;
                Title t = new Title();
                t.name = o.optString(type.equals("tv") ? "name" : "title");
                t.kind = type.equals("tv") ? "Série" : "Filme";
                t.kidsSafeUpToAge = kidsAge > 0 ? kidsAge : 99;
                String date = o.optString(type.equals("tv") ? "first_air_date" : "release_date", "");
                t.year = date.length() >= 4 ? date.substring(0, 4) : "";
                try {
                    JSONObject br = Tmdb.get("/" + type + "/" + o.getLong("id") + "/watch/providers?x=1")
                            .getJSONObject("results").optJSONObject("BR");
                    Set<String> apps = new LinkedHashSet<>();
                    if (br != null) {
                        for (String k : new String[]{"flatrate", "free", "ads"}) {
                            JSONArray list = br.optJSONArray(k);
                            if (list == null) continue;
                            for (int j = 0; j < list.length(); j++) {
                                String pkg = Tmdb.appFor(list.getJSONObject(j).optString("provider_name"));
                                if (pkg != null && installed.contains(pkg)) apps.add(pkg);
                            }
                        }
                    }
                    t.apps.addAll(apps);
                } catch (Exception ignored) {
                }
                File f = new File(dir, Integer.toHexString(path.hashCode()) + ".jpg");
                if (!f.exists()) Tmdb.download("https://image.tmdb.org/t/p/w780" + path, f);
                t.image = f.getPath();
                out.add(t);
            }
        } catch (Exception e) {
            android.util.Log.w("Sala", "Busca TMDB: " + e);
        }
        return out;
    }

    /** Kids profiles only see animation, family and kids titles (and, from 12, adventure and comedy). */
    private static boolean forKids(JSONArray genres, int age) {
        if (age >= 14) return true; // Certification is checked separately for these ranges.
        if (genres == null) return false;
        for (int i = 0; i < genres.length(); i++) {
            int g = genres.optInt(i);
            if (g == 16 || g == 10751 || g == 10762) return true;
            if (age > 10 && (g == 10759 || g == 35 || g == 12)) return true;
        }
        return false;
    }

    /** The search endpoint omits age ratings, so ask TMDB for the Brazilian certification per result. */
    private static boolean withinBrazilianRating(String type, long id, int age) {
        try {
            JSONObject response = Tmdb.get(type.equals("movie")
                    ? "/movie/" + id + "/release_dates?x=1"
                    : "/tv/" + id + "/content_ratings?x=1");
            JSONArray countries = response.getJSONArray("results");
            for (int i = 0; i < countries.length(); i++) {
                JSONObject country = countries.getJSONObject(i);
                String code = country.optString("iso_3166_1");
                if (!"BR".equals(code)) continue;
                JSONArray ratings = country.optJSONArray(type.equals("movie") ? "release_dates" : "rating");
                if (ratings == null) {
                    if (!type.equals("movie")) return allowedRating(country.optString("rating"), age);
                    continue;
                }
                for (int j = 0; j < ratings.length(); j++) {
                    JSONObject item = ratings.optJSONObject(j);
                    if (item != null && allowedRating(item.optString("certification"), age)) return true;
                }
            }
        } catch (Exception e) {
            android.util.Log.d("Sala", "Classificação BR indisponível para resultado de busca: " + e);
        }
        return false;
    }

    static boolean allowedRating(String rating, int age) {
        String value = rating == null ? "" : rating.trim().toUpperCase(java.util.Locale.ROOT);
        if (value.isEmpty()) return false;
        if (value.equals("L")) return true;
        if (age <= 1) return false;
        try { return Integer.parseInt(value.replaceAll("[^0-9]", "")) <= age; }
        catch (Exception ignored) { return false; }
    }



    /** Opens an app straight on its own search for the query (or just the app, if it has no search we can reach). */
    static Intent inApp(Context c, String pkg, String q) {
        String enc = Uri.encode(q);
        Intent i = null;
        if (pkg.equals("com.google.android.youtube.tv")) {
            i = new Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/results?search_query=" + enc));
        } else if (pkg.equals("com.netflix.ninja")) {
            i = new Intent(Intent.ACTION_VIEW, Uri.parse("nflx://www.netflix.com/search?q=" + enc));
        } else if (pkg.equals("com.amazon.amazonvideo.livingroom")) {
            i = new Intent(Intent.ACTION_VIEW, Uri.parse("https://app.primevideo.com/search?phrase=" + enc));
        } else if (pkg.equals("com.disney.disneyplus")) {
            i = new Intent(Intent.ACTION_VIEW, Uri.parse("https://www.disneyplus.com/search?q=" + enc));
        } else if (pkg.equals("com.wbd.stream")) {
            i = new Intent(Intent.ACTION_VIEW, Uri.parse("https://play.max.com/search?q=" + enc));
        } else if (pkg.equals("com.crunchyroll.crunchyroid")) {
            // The Android TV build may accept ACTION_SEARCH but ignore its query. Its registered
            // Crunchyroll URI opens the app's own search with the supplied title.
            i = new Intent(Intent.ACTION_VIEW, Uri.parse("crunchyroll://search?q=" + enc));
        } else {
            Intent s = new Intent(Intent.ACTION_SEARCH).setPackage(pkg).putExtra(SearchManager.QUERY, q);
            if (!c.getPackageManager().queryIntentActivities(s, 0).isEmpty()) i = s;
        }
        if (i != null) {
            i.setPackage(pkg);
            if (c.getPackageManager().resolveActivity(i, 0) != null) {
                android.util.Log.i("SalaSearch", "Abrindo busca no app " + pkg + " via " + i.getAction());
                return i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            }
        }
        android.util.Log.w("SalaSearch", "Sem rota de busca direta; encaminhando consulta ao launcher de " + pkg);
        Intent launch = c.getPackageManager().getLeanbackLaunchIntentForPackage(pkg);
        if (launch == null) launch = c.getPackageManager().getLaunchIntentForPackage(pkg);
        if (launch == null) return null;
        // Some TV apps read a query from their regular launch intent even though they do not
        // publish ACTION_SEARCH or a title deep link. Unsupported extras are safely ignored.
        launch.putExtra(SearchManager.QUERY, q);
        launch.putExtra(SearchManager.USER_QUERY, q);
        launch.putExtra(Intent.EXTRA_TEXT, q);
        launch.putExtra("android.intent.extra.QUERY", q);
        return launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    }

    /** Whether opening the app lands on its search (true) or only opens it (false). */
    static boolean canSearchIn(Context c, String pkg) {
        Intent i = inApp(c, pkg, "x");
        return i != null && (Intent.ACTION_SEARCH.equals(i.getAction()) || Intent.ACTION_VIEW.equals(i.getAction()));
    }
}
