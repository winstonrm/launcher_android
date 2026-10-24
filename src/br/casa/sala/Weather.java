package br.casa.sala;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/** Open-Meteo current, hourly and daily forecast. Location is only saved after user consent or manual selection. */
final class Weather {
    private static final long MAX_AGE = 30 * 60_000L;
    private Weather() {}

    static final class Forecast {
        String place, currentSummary;
        double currentTemp, apparentTemp;
        int currentCode, humidity;
        boolean day;
        double wind, uv;
        JSONArray daily, hourly;
        JSONObject dailyData, hourlyData;
    }

    static boolean hasLocation(Context c) {
        SharedPreferences p = prefs(c);
        return p.contains("lat") && p.contains("lon");
    }

    static double latitude(Context c) { return prefs(c).getFloat("lat", 0); }
    static double longitude(Context c) { return prefs(c).getFloat("lon", 0); }
    static String place(Context c) {
        SharedPreferences p = prefs(c);
        String saved = p.getString("place", "").trim();
        if (!isCoordinates(saved)) return saved;
        // Forecast responses already include the timezone resolved from the approved coordinates.
        // Use its city component when reverse geocoding did not provide a readable name.
        try {
            String timezone = new JSONObject(p.getString("json", "")).optString("timezone", "");
            int cityPart = timezone.lastIndexOf('/');
            if (cityPart >= 0 && cityPart < timezone.length() - 1)
                return timezone.substring(cityPart + 1).replace('_', ' ');
        } catch (Exception ignored) { }
        return saved;
    }

    private static boolean isCoordinates(String value) {
        return value == null || value.isEmpty() || value.matches("[-+0-9.,\\s]+")
                || value.matches("[-+]?\\d+(\\.\\d+)?\\s*,\\s*[-+]?\\d+(\\.\\d+)?");
    }

    static void saveLocation(Context c, double lat, double lon, String name) {
        prefs(c).edit().putFloat("lat", (float) lat).putFloat("lon", (float) lon)
                .putString("place", name == null ? "" : name).remove("json").apply();
    }

    static void clearLocation(Context c) {
        prefs(c).edit().remove("lat").remove("lon").remove("place").remove("json").remove("t").apply();
    }

    static String current(Context c) {
        Forecast f = forecast(c);
        if (f == null) return "";
        return icon(f.currentCode, true) + "  " + Math.round(f.currentTemp) + "°";
    }

    static Forecast forecast(Context c) {
        if (!hasLocation(c)) return null;
        SharedPreferences p = prefs(c);
        String raw = p.getString("json", "");
        if (!raw.isEmpty() && System.currentTimeMillis() - p.getLong("t", 0) < MAX_AGE) {
            try { return parse(raw, place(c)); } catch (Exception ignored) { }
        }
        try {
            String url = "https://api.open-meteo.com/v1/forecast?latitude=" + latitude(c) + "&longitude=" + longitude(c)
                    + "&current=temperature_2m,relative_humidity_2m,apparent_temperature,is_day,precipitation,weather_code,wind_speed_10m"
                    + "&hourly=temperature_2m,precipitation_probability,weather_code,relative_humidity_2m,wind_speed_10m"
                    + "&daily=weather_code,temperature_2m_max,temperature_2m_min,precipitation_probability_max,precipitation_sum,uv_index_max,sunrise,sunset"
                    + "&forecast_days=7&timezone=auto";
            raw = request(url);
            p.edit().putString("json", raw).putLong("t", System.currentTimeMillis()).apply();
            return parse(raw, place(c));
        } catch (Exception e) {
            android.util.Log.w("Sala", "Previsão do tempo: " + e);
            try { return raw.isEmpty() ? null : parse(raw, place(c)); } catch (Exception ignored) { return null; }
        }
    }

    /** Geocodes a user-entered city; never guesses a default location. Call off the main thread. */
    static JSONObject findPlace(Context c, String query) {
        try {
            String q = URLEncoder.encode(query.trim(), "UTF-8");
            JSONObject root = new JSONObject(request("https://geocoding-api.open-meteo.com/v1/search?name=" + q
                    + "&count=1&language=" + (L10n.isEnglish(c) ? "en" : "pt") + "&countryCode=BR&format=json"));
            JSONArray results = root.optJSONArray("results");
            return results == null || results.length() == 0 ? null : results.getJSONObject(0);
        } catch (Exception e) { return null; }
    }

    /** Temporary label until the forecast response provides a city-level timezone. */
    static String nameFor(double lat, double lon) {
        return String.format(java.util.Locale.US, "%.2f, %.2f", lat, lon);
    }

    private static SharedPreferences prefs(Context c) { return c.getSharedPreferences("tempo", Context.MODE_PRIVATE); }

    private static Forecast parse(String raw, String place) throws Exception {
        JSONObject root = new JSONObject(raw), cur = root.getJSONObject("current");
        Forecast f = new Forecast();
        f.place = place;
        f.currentTemp = cur.optDouble("temperature_2m");
        f.apparentTemp = cur.optDouble("apparent_temperature", f.currentTemp);
        f.currentCode = cur.optInt("weather_code");
        f.day = cur.optInt("is_day", 1) == 1;
        f.humidity = cur.optInt("relative_humidity_2m");
        f.wind = cur.optDouble("wind_speed_10m");
        f.dailyData = root.getJSONObject("daily");
        f.hourlyData = root.getJSONObject("hourly");
        f.daily = f.dailyData.getJSONArray("time");
        f.hourly = f.hourlyData.getJSONArray("time");
        JSONArray uv = f.dailyData.optJSONArray("uv_index_max");
        f.uv = uv == null ? 0 : uv.optDouble(0, 0);
        return f;
    }

    static String icon(int code, boolean day) {
        if (code <= 1) return day ? "☀️" : "🌙";
        if (code == 2) return day ? "⛅" : "☁️";
        if (code == 3 || code == 45 || code == 48) return "☁️";
        if (code >= 95) return "⛈️";
        return "🌧️";
    }

    static String description(int code) {
        if (code == 0) return "Céu limpo";
        if (code == 1) return "Predominantemente limpo";
        if (code == 2) return "Parcialmente nublado";
        if (code == 3) return "Nublado";
        if (code == 45 || code == 48) return "Neblina";
        if (code >= 51 && code <= 57) return "Garoa";
        if (code >= 61 && code <= 67) return "Chuva";
        if (code >= 71 && code <= 77) return "Neve";
        if (code >= 80 && code <= 82) return "Pancadas de chuva";
        if (code == 85 || code == 86) return "Pancadas de neve";
        if (code >= 95) return "Trovoadas";
        return "Condição indisponível";
    }

    private static String request(String url) throws Exception {
        HttpURLConnection h = (HttpURLConnection) new URL(url).openConnection();
        h.setConnectTimeout(6000); h.setReadTimeout(8000);
        try (InputStream in = h.getInputStream(); ByteArrayOutputStream b = new ByteArrayOutputStream()) {
            byte[] buf = new byte[4096];
            for (int n; (n = in.read(buf)) > 0; ) b.write(buf, 0, n);
            return new String(b.toByteArray(), StandardCharsets.UTF_8);
        } finally { h.disconnect(); }
    }
}
