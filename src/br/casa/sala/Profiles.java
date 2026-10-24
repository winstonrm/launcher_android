package br.casa.sala;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Shader;
import android.graphics.Typeface;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.security.MessageDigest;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.util.UUID;

/** Local profiles (no account): a name, an avatar and whether it is a kids profile with an age range. */
final class Profiles {
    static final class Profile {
        String id, name;
        int avatar;
        boolean kids;
        boolean pinRequired;
        int age; // maximum age setting for a Kids profile: 3 through 17
        String pinSalt = "", pinHash = "";
    }

    static final String[] AVATARS = {"🦁", "🐼", "🦊", "🐸", "🐙", "🦄", "🐯", "🐨", "🚀", "⚽", "🎮", "🎸", "🌵", "🍕", "👾", "🐶"};
    private static final int[][] COLORS = {
            {0xFFFF8A65, 0xFFE64A19}, {0xFF90CAF9, 0xFF1E88E5}, {0xFFFFCC80, 0xFFF57C00}, {0xFFA5D6A7, 0xFF43A047},
            {0xFFCE93D8, 0xFF8E24AA}, {0xFFF48FB1, 0xFFD81B60}, {0xFFFFE082, 0xFFFFA000}, {0xFFB0BEC5, 0xFF546E7A},
            {0xFF9FA8DA, 0xFF3949AB}, {0xFF80CBC4, 0xFF00897B}, {0xFFEF9A9A, 0xFFE53935}, {0xFFBCAAA4, 0xFF6D4C41},
            {0xFFC5E1A5, 0xFF7CB342}, {0xFFFFAB91, 0xFFF4511E}, {0xFFB39DDB, 0xFF5E35B1}, {0xFF81D4FA, 0xFF039BE5}};

    /** Apps a kids profile gets by default, by age range. Everything else stays hidden there. */
    private static final List<String> KIDS_6 = Arrays.asList("com.google.android.youtube.tvkids", "com.google.android.youtube.tv", "com.netflix.ninja",
            "com.disney.disneyplus");
    private static final List<String> KIDS_11 = Arrays.asList();
    private static final List<String> KIDS_15 = Arrays.asList("com.google.android.youtube.tv", "com.spotify.tv.android",
            "com.crunchyroll.crunchyroid", "com.amazon.amazonvideo.livingroom");

    private Profiles() {}

    private static SharedPreferences store(Context c) {
        return c.getSharedPreferences("perfis", Context.MODE_PRIVATE);
    }

    static List<Profile> all(Context c) {
        List<Profile> out = new ArrayList<>();
        try {
            SharedPreferences prefs = store(c);
            boolean clearLegacyPins = !prefs.getBoolean("pin_policy_migrated_v1", false);
            JSONArray a = new JSONArray(prefs.getString("lista", "[]"));
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.getJSONObject(i);
                Profile p = new Profile();
                p.id = o.getString("id");
                p.name = o.getString("name");
                p.avatar = o.optInt("avatar");
                p.kids = o.optBoolean("kids");
                p.pinRequired = !clearLegacyPins && o.optBoolean("pinRequired", false);
                p.age = normalizeAge(o.optInt("age", 12));
                p.pinSalt = clearLegacyPins ? "" : o.optString("pinSalt", "");
                p.pinHash = clearLegacyPins ? "" : o.optString("pinHash", "");
                out.add(p);
            }
            if (clearLegacyPins) {
                save(c, out);
                prefs.edit().putBoolean("pin_policy_migrated_v1", true).commit();
            }
        } catch (Exception ignored) {
        }
        return out;
    }

    static void save(Context c, List<Profile> list) {
        JSONArray a = new JSONArray();
        try {
            for (Profile p : list) {
                a.put(new JSONObject().put("id", p.id).put("name", p.name).put("avatar", p.avatar)
                        .put("kids", p.kids).put("pinRequired", p.pinRequired).put("age", normalizeAge(p.age)).put("pinSalt", p.pinSalt == null ? "" : p.pinSalt)
                        .put("pinHash", p.pinHash == null ? "" : p.pinHash));
            }
        } catch (Exception ignored) {
        }
        store(c).edit().putString("lista", a.toString()).commit();
    }

    static Profile current(Context c) {
        String id = store(c).getString("atual", "");
        List<Profile> all = all(c);
        for (Profile p : all) if (p.id.equals(id)) return p;
        if (all.isEmpty()) {
            Profile initial = new Profile();
            initial.id = "p1";
            initial.name = L10n.tr(c, "Usuário");
            initial.avatar = 0;
            initial.kids = false;
            initial.pinRequired = false;
            initial.age = 17;
            all.add(initial);
            save(c, all);
            setCurrent(c, initial.id);
            return initial;
        }
        return all.get(0);
    }

    static void setCurrent(Context c, String id) {
        store(c).edit().putString("atual", id).commit();
    }

    static String newId(Context c) {
        int n = store(c).getInt("seq", 0) + 1;
        store(c).edit().putInt("seq", n).apply();
        return "p" + n;
    }

    /** Each profile keeps its own order, hidden apps and history. The first one keeps the data from before profiles. */
    static String suffix(Context c) {
        return suffix(currentId(c));
    }

    static String currentId(Context c) {
        Profile p = current(c);
        return p == null ? "p1" : p.id;
    }

    static String suffix(String profileId) {
        return profileId == null || profileId.isEmpty() || profileId.equals("p1") ? "" : "_" + profileId;
    }

    static Set<String> kidsApps(int age) {
        Set<String> s = new HashSet<>(KIDS_6);
        if (age >= 12) s.addAll(KIDS_11);
        if (age >= 12) s.addAll(KIDS_15);
        return s;
    }

    /** Keeps legacy limits readable while allowing integer Kids ages from 3 through 17. */
    static int normalizeAge(int age) {
        if (age == 1 || age == 6) return 3; // Livre / legacy youngest range
        if (age == 11) return 10;
        if (age == 15) return 14;
        if (age == 18) return 17;
        return age >= 3 && age <= 17 ? age : 12;
    }

    static String ageLabel(int age) {
        // The profile editor offers each integer age from 3 through 17. Keep
        // these labels literal; normalizeAge() is only for legacy stored values.
        return "Até " + age + " anos";
    }

    static boolean hasPin(Profile p) { return p != null && p.pinHash != null && !p.pinHash.isEmpty(); }

    static void setPin(Profile p, String pin) {
        p.pinSalt = UUID.randomUUID().toString();
        p.pinHash = hash(p.pinSalt, pin);
    }

    static boolean verifyPin(Profile p, String pin) {
        return hasPin(p) && MessageDigest.isEqual(p.pinHash.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                hash(p.pinSalt, pin).getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    private static String hash(String salt, String pin) {
        try {
            PBEKeySpec spec = new PBEKeySpec((salt + ":" + pin).toCharArray(), salt.getBytes(java.nio.charset.StandardCharsets.UTF_8), 120000, 256);
            byte[] bytes = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
            spec.clearPassword();
            StringBuilder out = new StringBuilder();
            for (byte b : bytes) out.append(String.format(java.util.Locale.ROOT, "%02x", b & 0xff));
            return out.toString();
        } catch (Exception e) { return ""; }
    }

    /** A round avatar: the emoji on a soft gradient. */
    static Bitmap avatar(int index, int size) {
        int i = Math.max(0, Math.min(AVATARS.length - 1, index));
        Bitmap b = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas cv = new Canvas(b);
        Paint bg = new Paint(Paint.ANTI_ALIAS_FLAG);
        bg.setShader(new LinearGradient(0, 0, size, size, COLORS[i][0], COLORS[i][1], Shader.TileMode.CLAMP));
        cv.drawCircle(size / 2f, size / 2f, size / 2f, bg);
        Paint t = new Paint(Paint.ANTI_ALIAS_FLAG);
        t.setTextSize(size * 0.52f);
        t.setTextAlign(Paint.Align.CENTER);
        t.setTypeface(Typeface.DEFAULT);
        Paint.FontMetrics fm = t.getFontMetrics();
        cv.drawText(AVATARS[i], size / 2f, size / 2f - (fm.ascent + fm.descent) / 2f, t);
        return b;
    }
}
