package br.casa.sala;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

/** Local cinema listings and showtimes, with cached artwork and a TMDB fallback. */
final class Cinema {
    static final class Screening {
        String theater, room, time, url;
        double distance;
    }
    static final class Movie {
        int id;
        String title, overview, release, image;
        double vote;
        final List<Screening> screenings = new ArrayList<>();
    }
    static final class Reminder {
        String title;
        long at;
        boolean notified;
    }
    private static final long MAX_AGE = 6 * 3600_000L;
    private Cinema() {}

    static List<Movie> cached(Context c) {
        List<Movie> out = new ArrayList<>();
        try {
            JSONArray a = new JSONObject(prefs(c).getString("movies", "[]")).getJSONArray("items");
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.getJSONObject(i);
                Movie m = new Movie(); m.id = o.optInt("id"); m.title = o.optString("title");
                m.overview = o.optString("overview"); m.release = o.optString("release"); m.vote = o.optDouble("vote");
                m.image = o.optString("image");
                JSONArray sessions = o.optJSONArray("screenings");
                if (sessions != null) for (int j = 0; j < sessions.length(); j++) {
                    JSONObject s = sessions.getJSONObject(j); Screening show = new Screening();
                    show.theater = s.optString("theater"); show.room = s.optString("room"); show.time = s.optString("time");
                    show.url = s.optString("url"); show.distance = s.optDouble("distance"); m.screenings.add(show);
                }
                if (new File(m.image).exists()) out.add(m);
            }
        } catch (Exception ignored) { }
        return out;
    }

    static List<Movie> load(Context c) {
        List<Movie> old = cached(c);
        SharedPreferences p = prefs(c);
        String locationKey = locationKey(c);
        if (System.currentTimeMillis() - p.getLong("time", 0) < MAX_AGE && !old.isEmpty()
                && locationKey.equals(p.getString("location", ""))) return old;
        if (Weather.hasLocation(c) && !Config.CINEMA_TOKEN.isEmpty()) {
            List<Movie> openCinema = loadOpenCinema(c);
            if (!openCinema.isEmpty()) {
                saveMovies(c, openCinema);
                return openCinema;
            }
        }
        if (!Config.INGRESSO_PARTNER.isEmpty() && Weather.hasLocation(c)) {
            List<Movie> local = loadIngresso(c);
            if (!local.isEmpty()) {
                saveMovies(c, local);
                return local;
            }
        }
        if (!Tmdb.enabled()) return old;
        try {
            JSONArray results = Tmdb.get("/movie/now_playing?language=pt-BR&region=BR&page=1").getJSONArray("results");
            JSONArray saved = new JSONArray(); List<Movie> out = new ArrayList<>();
            File dir = new File(c.getFilesDir(), "cinema"); dir.mkdirs();
            for (int i = 0; i < results.length() && out.size() < 20; i++) {
                JSONObject o = results.getJSONObject(i); String path = o.optString("backdrop_path", "");
                if (path.isEmpty() || "null".equals(path)) path = o.optString("poster_path", "");
                if (path.isEmpty() || "null".equals(path)) continue;
                Movie m = new Movie(); m.id = o.optInt("id"); m.title = o.optString("title");
                m.overview = o.optString("overview"); m.release = o.optString("release_date"); m.vote = o.optDouble("vote_average");
                File image = new File(dir, m.id + ".jpg");
                if (!image.exists()) Tmdb.download("https://image.tmdb.org/t/p/w780" + path, image);
                if (!image.exists()) continue;
                m.image = image.getPath(); out.add(m);
                saved.put(new JSONObject().put("id", m.id).put("title", m.title).put("overview", m.overview)
                        .put("release", m.release).put("vote", m.vote).put("image", m.image));
            }
            if (!out.isEmpty()) { p.edit().putString("movies", new JSONObject().put("items", saved).toString())
                    .putLong("time", System.currentTimeMillis()).apply(); return out; }
        } catch (Exception e) { android.util.Log.w("Sala", "Cinema: " + e); }
        return old;
    }

    /** Open Cinema Project's geospatial feed. It has indie/repertory coverage and can be empty by region. */
    private static List<Movie> loadOpenCinema(Context c) {
        List<Movie> out = new ArrayList<>();
        try {
            String query = "?lat=" + Weather.latitude(c) + "&lon=" + Weather.longitude(c) + "&radius_km=50";
            JSONObject response = new JSONObject(request("https://opencinemaproject.com/api/v1/public/screenings" + query,
                    Config.CINEMA_TOKEN));
            JSONArray screenings = response.optJSONArray("screenings");
            if (screenings == null) return out;
            Map<String, Movie> byFilm = new HashMap<>();
            for (int i = 0; i < screenings.length() && i < 100; i++) {
                JSONObject item = screenings.optJSONObject(i);
                if (item == null) continue;
                String filmId = item.optString("film_id");
                String title = item.optString("film_title").trim();
                if (title.isEmpty()) continue;
                String key = filmId.isEmpty() ? title.toLowerCase(java.util.Locale.ROOT) : filmId;
                Movie movie = byFilm.get(key);
                if (movie == null) {
                    movie = new Movie(); movie.title = title; movie.overview = ""; movie.release = "";
                    movie.id = key.hashCode();
                    byFilm.put(key, movie);
                }
                JSONObject checkout = item.optJSONObject("checkout");
                Screening show = new Screening();
                show.theater = item.optString("theater_name", "Cinema");
                show.room = "";
                show.time = item.optString("start_time");
                show.url = checkout == null ? "" : checkout.optString("url", "");
                show.distance = item.optDouble("distance_km", -1);
                if (movie.screenings.size() < 12) movie.screenings.add(show);
            }
            File dir = new File(c.getFilesDir(), "cinema_open"); dir.mkdirs();
            for (Movie movie : byFilm.values()) {
                if (movie.screenings.isEmpty()) continue;
                movie.image = findArtwork(c, movie.title, movie.id, dir);
                out.add(movie);
            }
            out.sort((a, b) -> a.title.compareToIgnoreCase(b.title));
        } catch (Exception e) { android.util.Log.w("Sala", "Sessões Open Cinema: " + e); }
        return out;
    }

    private static String findArtwork(Context c, String title, int id, File dir) {
        if (!Tmdb.enabled()) return "";
        File image = new File(dir, Integer.toHexString(id) + ".jpg");
        if (image.exists()) return image.getPath();
        try {
            JSONObject result = Tmdb.get("/search/movie?language=pt-BR&query=" + URLEncoder.encode(title, "UTF-8"));
            JSONArray items = result.optJSONArray("results");
            if (items == null || items.length() == 0) return "";
            String path = items.getJSONObject(0).optString("backdrop_path", "");
            if (path.isEmpty() || "null".equals(path)) path = items.getJSONObject(0).optString("poster_path", "");
            if (path.isEmpty() || "null".equals(path)) return "";
            Tmdb.download("https://image.tmdb.org/t/p/w780" + path, image);
            return image.exists() ? image.getPath() : "";
        } catch (Exception ignored) { return ""; }
    }

    private static List<Movie> loadIngresso(Context c) {
        List<Movie> out = new ArrayList<>();
        try {
            String partner = URLEncoder.encode(Config.INGRESSO_PARTNER, "UTF-8");
            JSONArray theaters = new JSONArray(request("https://api-content.ingresso.com/v0/theaters?partnership=" + partner));
            List<JSONObject> nearby = new ArrayList<>();
            final double lat = Weather.latitude(c), lon = Weather.longitude(c);
            for (int i = 0; i < theaters.length(); i++) {
                JSONObject theater = theaters.getJSONObject(i), geo = theater.optJSONObject("geolocation");
                if (geo == null || !theater.has("cityId")) continue;
                double distance = distanceKm(lat, lon, geo.optDouble("lat"), geo.optDouble("lng"));
                if (distance > 100) continue;
                theater.put("_distance", distance); nearby.add(theater);
            }
            Collections.sort(nearby, (a, b) -> Double.compare(a.optDouble("_distance"), b.optDouble("_distance")));
            Map<String, Movie> byId = new HashMap<>();
            for (int i = 0; i < Math.min(6, nearby.size()); i++) {
                JSONObject theater = nearby.get(i);
                String city = theater.optString("cityId"), theaterId = theater.optString("id");
                JSONArray days = new JSONArray(request("https://api-content.ingresso.com/v0/sessions/city/"
                        + URLEncoder.encode(city, "UTF-8") + "/theater/" + URLEncoder.encode(theaterId, "UTF-8")
                        + "?partnership=" + partner));
                for (int day = 0; day < days.length(); day++) {
                    JSONArray movies = days.getJSONObject(day).optJSONArray("movies");
                    if (movies == null) continue;
                    for (int j = 0; j < movies.length(); j++) {
                        JSONObject source = movies.getJSONObject(j);
                        String key = source.optString("id", source.optString("title"));
                        Movie movie = byId.get(key);
                        if (movie == null) {
                            movie = new Movie(); movie.title = source.optString("title"); movie.overview = "";
                            movie.id = key.hashCode(); movie.release = "";
                            JSONArray images = source.optJSONArray("images");
                            if (images != null && images.length() > 0) {
                                JSONObject image = images.getJSONObject(0);
                                movie.image = downloadImage(c, movie.id, image.optString("url"));
                            }
                            byId.put(key, movie);
                        }
                        JSONArray rooms = source.optJSONArray("rooms");
                        if (rooms == null) continue;
                        for (int k = 0; k < rooms.length(); k++) {
                            JSONObject room = rooms.getJSONObject(k); JSONArray sessions = room.optJSONArray("sessions");
                            if (sessions == null) continue;
                            for (int z = 0; z < sessions.length(); z++) {
                                JSONObject session = sessions.getJSONObject(z), date = session.optJSONObject("date");
                                Screening show = new Screening(); show.theater = theater.optString("name");
                                show.room = room.optString("name"); show.distance = theater.optDouble("_distance");
                                show.time = session.optString("time", date == null ? "" : date.optString("hour"));
                                show.url = session.optString("siteURL", source.optString("siteURLByTheater"));
                                if (!show.time.isEmpty() && movie.screenings.size() < 24) movie.screenings.add(show);
                            }
                        }
                    }
                }
            }
            for (Movie movie : byId.values()) if (!movie.screenings.isEmpty()) out.add(movie);
            out.sort((a, b) -> a.title.compareToIgnoreCase(b.title));
        } catch (Exception e) { android.util.Log.w("Sala", "Sessões Ingresso.com: " + e); }
        return out;
    }

    private static String downloadImage(Context c, int id, String url) {
        if (url == null || url.isEmpty()) return "";
        File dir = new File(c.getFilesDir(), "cinema_ingresso"); dir.mkdirs();
        File image = new File(dir, Integer.toHexString(id) + ".jpg");
        if (image.exists()) return image.getPath();
        try {
            String full = url.startsWith("http") ? url : "https://api-content.ingresso.com" + (url.startsWith("/") ? "" : "/") + url;
            Tmdb.download(full, image);
            return image.exists() ? image.getPath() : "";
        } catch (Exception ignored) { return ""; }
    }

    private static double distanceKm(double lat1, double lon1, double lat2, double lon2) {
        double dLat = Math.toRadians(lat2 - lat1), dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2) + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return 6371 * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }

    private static void saveMovies(Context c, List<Movie> movies) {
        JSONArray a = new JSONArray();
        try {
            for (Movie movie : movies) {
                JSONArray sessions = new JSONArray();
                for (Screening show : movie.screenings) sessions.put(new JSONObject().put("theater", show.theater)
                        .put("room", show.room).put("time", show.time).put("url", show.url).put("distance", show.distance));
                a.put(new JSONObject().put("id", movie.id).put("title", movie.title).put("overview", movie.overview)
                        .put("release", movie.release).put("vote", movie.vote).put("image", movie.image).put("screenings", sessions));
            }
            prefs(c).edit().putString("movies", new JSONObject().put("items", a).toString())
                    .putLong("time", System.currentTimeMillis()).putString("location", locationKey(c)).apply();
        } catch (Exception e) { android.util.Log.w("Sala", "Cache de sessões: " + e); }
    }

    private static String request(String url) throws Exception { return request(url, ""); }

    private static String request(String url, String bearerToken) throws Exception {
        HttpURLConnection h = (HttpURLConnection) new URL(url).openConnection();
        h.setConnectTimeout(7000); h.setReadTimeout(9000); h.setRequestProperty("User-Agent", "LauncherTV/0.1 (Android TV)");
        if (bearerToken != null && !bearerToken.isEmpty())
            h.setRequestProperty("Authorization", "Bearer " + bearerToken);
        try (InputStream in = h.getInputStream(); ByteArrayOutputStream b = new ByteArrayOutputStream()) {
            byte[] buf = new byte[8192];
            for (int n; (n = in.read(buf)) > 0; ) b.write(buf, 0, n);
            return new String(b.toByteArray(), StandardCharsets.UTF_8);
        } finally { h.disconnect(); }
    }

    static List<Reminder> reminders(Context c) {
        List<Reminder> out = new ArrayList<>();
        try {
            JSONArray a = new JSONArray(prefs(c).getString("reminders", "[]"));
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.getJSONObject(i); Reminder r = new Reminder();
                r.title = o.optString("title"); r.at = o.optLong("at"); r.notified = o.optBoolean("notified"); out.add(r);
            }
        } catch (Exception ignored) { }
        return out;
    }

    static void addReminder(Context c, String title, long at) {
        List<Reminder> all = reminders(c); Reminder r = new Reminder(); r.title = title; r.at = at; all.add(r);
        while (all.size() > 20) all.remove(0);
        saveReminders(c, all);
    }

    static void removeReminder(Context c, long at) {
        List<Reminder> all = reminders(c);
        for (int i = all.size() - 1; i >= 0; i--) if (all.get(i).at == at) all.remove(i);
        saveReminders(c, all);
    }

    static void markNotified(Context c, long at) {
        List<Reminder> all = reminders(c);
        for (Reminder r : all) if (r.at == at) r.notified = true;
        saveReminders(c, all);
    }

    private static void saveReminders(Context c, List<Reminder> all) {
        JSONArray a = new JSONArray();
        for (Reminder r : all) try { a.put(new JSONObject().put("title", r.title).put("at", r.at).put("notified", r.notified)); }
        catch (Exception ignored) { }
        prefs(c).edit().putString("reminders", a.toString()).apply();
    }

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences("cinema" + Profiles.suffix(c), Context.MODE_PRIVATE);
    }

    private static String locationKey(Context c) {
        return Weather.hasLocation(c) ? String.format(java.util.Locale.US, "%.3f,%.3f", Weather.latitude(c), Weather.longitude(c)) : "";
    }
}
