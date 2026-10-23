package br.casa.sala;

import android.content.ComponentName;
import android.app.Notification;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSession;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.os.SystemClock;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.os.Bundle;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Only woken by the system when media sessions change: records what each app plays (title, position, artwork)
 * so the home screen can show it. Needs notification access (granted once over ADB).
 */
public class MediaListener extends NotificationListenerService implements MediaSessionManager.OnActiveSessionsChangedListener {
    private static final String NETFLIX = "com.netflix.ninja";
    private static final String PRIME = "com.amazon.amazonvideo.livingroom";
    private static volatile MediaListener live;
    private static final Set<String> IGNORE = new HashSet<>();
    static {
        IGNORE.add("com.android.bluetooth");
        IGNORE.add("com.google.android.katniss");
    }

    private MediaSessionManager msm;
    private final Map<MediaSession.Token, Watch> watched = new HashMap<>();

    @Override public void onCreate() { super.onCreate(); live = this; }

    @Override public void onDestroy() {
        if (live == this) live = null;
        super.onDestroy();
    }

    /** Uses the streamer's own active media notification, which often opens its current player. */
    static boolean openResumeNotification(String pkg, String title) {
        MediaListener service = live;
        if (service == null || pkg == null) return false;
        try {
            StatusBarNotification[] active = service.getActiveNotifications();
            if (active == null) return false;
            StatusBarNotification best = null;
            int bestScore = -1;
            for (StatusBarNotification sbn : active) {
                if (!pkg.equals(sbn.getPackageName())) continue;
                Notification n = sbn.getNotification();
                if (n == null || n.contentIntent == null) continue;
                Bundle x = n.extras;
                boolean media = n.category != null && Notification.CATEGORY_TRANSPORT.equals(n.category);
                if (x != null && x.get(Notification.EXTRA_MEDIA_SESSION) instanceof MediaSession.Token) media = true;
                if (!media) continue;
                int score = 1;
                if (x != null && title != null) {
                    String t = text(x.getCharSequence(Notification.EXTRA_TITLE));
                    String body = text(x.getCharSequence(Notification.EXTRA_TEXT));
                    if (title.equalsIgnoreCase(t) || title.equalsIgnoreCase(body)) score += 4;
                    else if ((t != null && (t.contains(title) || title.contains(t)))
                            || (body != null && (body.contains(title) || title.contains(body)))) score += 2;
                }
                if (sbn.getPostTime() > (best == null ? 0 : best.getPostTime())) score++;
                if (score > bestScore) { best = sbn; bestScore = score; }
            }
            if (best == null) return false;
            best.getNotification().contentIntent.send();
            return true;
        } catch (Exception e) {
            android.util.Log.w("Sala", "Abrir notificação de retomada " + pkg + ": " + e);
            return false;
        }
    }

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        if (sbn == null || (!NETFLIX.equals(sbn.getPackageName()) && !PRIME.equals(sbn.getPackageName()))) return;
        Bundle extras = sbn.getNotification().extras;
        if (extras == null) return;
        String title = text(extras.getCharSequence(Notification.EXTRA_TITLE));
        String subtitle = text(extras.getCharSequence(Notification.EXTRA_TEXT));
        if (genericLabel(sbn.getPackageName(), title)) title = null;
        if (title == null || title.trim().isEmpty()) title = firstText(
                text(extras.getCharSequence(Notification.EXTRA_BIG_TEXT)), subtitle,
                text(extras.getCharSequence(Notification.EXTRA_SUB_TEXT)),
                text(extras.getCharSequence(Notification.EXTRA_INFO_TEXT)));
        MediaController controller = activeController(sbn.getPackageName());
        if (controller == null) return;
        MediaMetadata metadata = controller.getMetadata();
        String sessionTitle = metadata == null ? null : first(metadata,
                MediaMetadata.METADATA_KEY_DISPLAY_TITLE, MediaMetadata.METADATA_KEY_TITLE);
        if (genericLabel(sbn.getPackageName(), sessionTitle)) sessionTitle = null;
        if (sessionTitle != null) title = sessionTitle;
        if (title == null || title.trim().isEmpty()) return;

        History.Item item = new History.Item();
        item.pkg = NETFLIX;
        item.profileId = History.ownerForPackage(this, item.pkg);
        item.title = title.trim();
        item.sub = subtitle;
        item.time = System.currentTimeMillis();
        item.playing = true;
        if (metadata != null) {
            item.mediaId = first(metadata, MediaMetadata.METADATA_KEY_MEDIA_ID);
            item.mediaUri = first(metadata, MediaMetadata.METADATA_KEY_MEDIA_URI);
            item.dur = Math.max(0, metadata.getLong(MediaMetadata.METADATA_KEY_DURATION));
        }
        PlaybackState state = controller.getPlaybackState();
        if (state != null) {
            item.pos = Math.max(0, state.getPosition());
            item.playing = state.getState() == PlaybackState.STATE_PLAYING;
        }
        Bitmap artwork = notificationArtwork(extras);
        if (artwork == null && metadata != null) artwork = metadata.getBitmap(MediaMetadata.METADATA_KEY_ART);
        if (artwork == null && metadata != null) artwork = metadata.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART);
        item.art = History.saveArt(this, item.pkg, item.title, artwork, item.profileId);
        History.put(this, item);
        notifyHome();
    }

    private boolean isNetflixPlaying() {
        return netflixController() != null;
    }

    private MediaController activeController(String pkg) {
        try {
            if (msm == null) msm = (MediaSessionManager) getSystemService(MEDIA_SESSION_SERVICE);
            if (msm == null) return null;
            List<MediaController> sessions = msm.getActiveSessions(new ComponentName(this, MediaListener.class));
            for (MediaController controller : sessions) {
                if (!pkg.equals(controller.getPackageName())) continue;
                PlaybackState state = controller.getPlaybackState();
                if (state != null && (state.getState() == PlaybackState.STATE_PLAYING
                        || state.getState() == PlaybackState.STATE_PAUSED
                        || state.getState() == PlaybackState.STATE_BUFFERING)) return controller;
            }
        } catch (Exception ignored) { }
        return null;
    }

    private static boolean genericLabel(String pkg, String value) {
        if (value == null || value.trim().isEmpty()) return true;
        String v = value.trim();
        return "Netflix".equalsIgnoreCase(v) || "Prime Video".equalsIgnoreCase(v)
                || v.startsWith("Continuar no ");
    }

    private static String firstText(String... values) {
        for (String value : values) if (value != null && !value.trim().isEmpty()) return value;
        return null;
    }

    private MediaController netflixController() {
        try {
            if (msm == null) msm = (MediaSessionManager) getSystemService(MEDIA_SESSION_SERVICE);
            if (msm == null) return null;
            List<MediaController> sessions = msm.getActiveSessions(new ComponentName(this, MediaListener.class));
            for (MediaController controller : sessions) {
                if (!NETFLIX.equals(controller.getPackageName())) continue;
                PlaybackState state = controller.getPlaybackState();
                if (state == null || (state.getState() != PlaybackState.STATE_PLAYING
                        && state.getState() != PlaybackState.STATE_PAUSED)) continue;
                return controller;
            }
        } catch (Exception ignored) { }
        return null;
    }

    private Bitmap notificationArtwork(Bundle extras) {
        Object picture = extras.get(Notification.EXTRA_PICTURE);
        if (!(picture instanceof Bitmap)) picture = extras.get(Notification.EXTRA_LARGE_ICON);
        if (picture instanceof Bitmap) return (Bitmap) picture;
        if (picture instanceof android.graphics.drawable.Icon) {
            try {
                android.graphics.drawable.Drawable drawable = ((android.graphics.drawable.Icon) picture).loadDrawable(this);
                if (drawable == null) return null;
                int width = drawable.getIntrinsicWidth() > 0 ? drawable.getIntrinsicWidth() : 96;
                int height = drawable.getIntrinsicHeight() > 0 ? drawable.getIntrinsicHeight() : 96;
                Bitmap bitmap = Bitmap.createBitmap(Math.min(width, 512), Math.min(height, 512), Bitmap.Config.ARGB_8888);
                drawable.setBounds(0, 0, bitmap.getWidth(), bitmap.getHeight());
                drawable.draw(new Canvas(bitmap));
                return bitmap;
            } catch (Exception ignored) { }
        }
        return null;
    }

    private static String text(CharSequence value) {
        return value == null ? null : value.toString();
    }

    static final class Watch extends MediaController.Callback {
        final MediaListener owner;
        final MediaController c;
        String lastKey = "";
        String lastItemKey = "";
        boolean lastPlaying;
        boolean hasLastPlaying;

        Watch(MediaListener owner, MediaController c) {
            this.owner = owner;
            this.c = c;
        }

        @Override
        public void onMetadataChanged(MediaMetadata m) {
            owner.record(c, this);
        }

        @Override
        public void onPlaybackStateChanged(PlaybackState s) {
            owner.record(c, this);
        }

        @Override
        public void onSessionDestroyed() {
            owner.record(c, this);
        }
    }

    @Override
    public void onListenerConnected() {
        msm = (MediaSessionManager) getSystemService(MEDIA_SESSION_SERVICE);
        ComponentName me = new ComponentName(this, MediaListener.class);
        try {
            msm.addOnActiveSessionsChangedListener(this, me);
            onActiveSessionsChanged(msm.getActiveSessions(me));
        } catch (SecurityException ignored) {
        }
    }

    @Override
    public void onListenerDisconnected() {
        if (msm != null) msm.removeOnActiveSessionsChangedListener(this);
        for (Watch w : watched.values()) w.c.unregisterCallback(w);
        watched.clear();
    }

    @Override
    public void onActiveSessionsChanged(List<MediaController> list) {
        if (list == null) return;
        Set<MediaSession.Token> now = new HashSet<>();
        for (MediaController c : list) {
            if (IGNORE.contains(c.getPackageName())) continue;
            now.add(c.getSessionToken());
            if (!watched.containsKey(c.getSessionToken())) {
                Watch w = new Watch(this, c);
                c.registerCallback(w);
                watched.put(c.getSessionToken(), w);
                record(c, w);
            }
        }
        for (MediaSession.Token t : new HashSet<>(watched.keySet())) {
            if (!now.contains(t)) {
                Watch w = watched.remove(t);
                record(w.c, w);
                w.c.unregisterCallback(w);
            }
        }
    }

    void record(MediaController c, Watch w) {
        MediaMetadata m;
        PlaybackState s;
        try {
            m = c.getMetadata();
            s = c.getPlaybackState();
        } catch (Exception e) {
            return;
        }
        String profileId = History.ownerForPackage(this, c.getPackageName());
        String title = m == null ? null : first(m, MediaMetadata.METADATA_KEY_DISPLAY_TITLE, MediaMetadata.METADATA_KEY_TITLE);
        if (title == null || title.trim().isEmpty()) {
            // Some apps (Netflix) play without saying what: still remember that something was being watched there.
            boolean active = s != null && (s.getState() == PlaybackState.STATE_PLAYING || s.getState() == PlaybackState.STATE_PAUSED);
            if (!active) return;
            String label;
            try {
                label = String.valueOf(getPackageManager().getApplicationLabel(getPackageManager().getApplicationInfo(c.getPackageName(), 0)));
            } catch (Exception e) {
                label = c.getPackageName();
            }
            title = "Continuar no " + label;
            if (NETFLIX.equals(c.getPackageName()) || PRIME.equals(c.getPackageName())) {
                List<History.Item> previous = History.get(this, c.getPackageName(), profileId);
                if (!previous.isEmpty() && !previous.get(0).title.startsWith("Continuar no ")) {
                    // Keep the title last published by the app's notification when
                    // its media session exposes only generic app metadata.
                    title = previous.get(0).title;
                }
            }
            m = null;
        }
        History.Item it = new History.Item();
        it.pkg = c.getPackageName();
        it.profileId = profileId;
        it.title = title.trim();
        it.sub = m == null ? null : first(m, MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE, MediaMetadata.METADATA_KEY_ARTIST,
                MediaMetadata.METADATA_KEY_ALBUM);
        it.dur = m == null ? 0 : Math.max(0, m.getLong(MediaMetadata.METADATA_KEY_DURATION));
        it.mediaId = m == null ? null : first(m, MediaMetadata.METADATA_KEY_MEDIA_ID);
        it.mediaUri = m == null ? null : first(m, MediaMetadata.METADATA_KEY_MEDIA_URI);
        android.util.Log.i("Sala", "Mídia " + it.pkg + " id=" + it.mediaId + " uri=" + it.mediaUri);
        it.time = System.currentTimeMillis();
        if (s != null) {
            it.playing = s.getState() == PlaybackState.STATE_PLAYING;
            long pos = s.getPosition();
            if (it.playing && s.getLastPositionUpdateTime() > 0) {
                pos += (long) ((SystemClock.elapsedRealtime() - s.getLastPositionUpdateTime()) * s.getPlaybackSpeed());
            }
            it.pos = Math.max(0, pos);
        }
        String key = it.title + "|" + it.playing + "|" + (it.pos / 10000);
        if (key.equals(w.lastKey)) return;
        w.lastKey = key;
        String itemKey = it.title + "|" + String.valueOf(it.mediaId);
        boolean itemChanged = !itemKey.equals(w.lastItemKey)
                || !w.hasLastPlaying || it.playing != w.lastPlaying;
        w.lastItemKey = itemKey;
        w.lastPlaying = it.playing;
        w.hasLastPlaying = true;
        Bitmap art = m == null ? null : m.getBitmap(MediaMetadata.METADATA_KEY_ART);
        if (art == null && m != null) art = m.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART);
        if (art == null && m != null && m.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON) != null) {
            // A display icon is often just the provider logo. Clear an older copy
            // mistakenly cached as artwork for this same item.
            History.clearArt(this, it.pkg, it.title, it.profileId);
        }
        // DISPLAY_ICON identifies the app or media session in several providers
        // (not the episode/song artwork). Keep it out of the full-card artwork;
        // MainActivity renders the app logo in the same bounded badge for every app.
        it.art = History.saveArt(this, it.pkg, it.title, art, it.profileId);
        // Keep the URI even when the session also supplies a bitmap. YouTube TV can
        // expose the video ID in its thumbnail URL while the image itself is present.
        String artUrl = m == null ? null : first(m, MediaMetadata.METADATA_KEY_ART_URI,
                MediaMetadata.METADATA_KEY_ALBUM_ART_URI);
        if ("com.google.android.youtube.tv".equals(it.pkg)) {
            String videoId = youtubeVideoId(it.mediaUri);
            if (videoId == null) videoId = youtubeVideoId(it.mediaId);
            if (videoId == null) videoId = youtubeVideoId(artUrl);
            if (videoId != null) {
                it.mediaId = videoId;
                it.mediaUri = "https://www.youtube.com/watch?v=" + videoId;
            }
            android.util.Log.i("Sala", "YouTube: id do vídeo " + (videoId == null ? "indisponível" : "recuperado"));
        }
        History.put(this, it);
        if (itemChanged) notifyHome();
        if (artUrl != null && artUrl.startsWith("http")) {
            // The app only gave a link: fetch the thumbnail in the background and store it with the entry.
            final History.Item copy = it;
            new Thread(() -> {
                String path = History.saveArtFromUrl(this, copy.pkg, copy.title, artUrl, copy.profileId);
                if (path != null) {
                    copy.art = path;
                    History.put(this, copy);
                    notifyHome();
                }
            }).start();
        }
    }

    private void notifyHome() {
        android.content.Intent update = new android.content.Intent(History.ACTION_UPDATED);
        update.setPackage(getPackageName());
        sendBroadcast(update);
    }

    private static String first(MediaMetadata m, String... keys) {
        for (String k : keys) {
            CharSequence v = m.getText(k);
            if (v != null && v.toString().trim().length() > 0) return v.toString();
        }
        return null;
    }

    /** Extracts a YouTube video id from watch, short, embed, or thumbnail URLs. */
    private static String youtubeVideoId(String value) {
        if (value == null || value.trim().isEmpty()) return null;
        String candidate = value.trim();
        if (candidate.matches("[A-Za-z0-9_-]{11}")) return candidate;
        try {
            android.net.Uri uri = android.net.Uri.parse(candidate);
            String host = uri.getHost();
            if (host != null && (host.equals("youtu.be") || host.endsWith(".youtu.be"))) {
                String path = uri.getLastPathSegment();
                if (path != null && path.matches("[A-Za-z0-9_-]{11}")) return path;
            }
            String queryId = uri.getQueryParameter("v");
            if (queryId != null && queryId.matches("[A-Za-z0-9_-]{11}")) return queryId;
            for (String part : uri.getPathSegments()) {
                if (part.matches("[A-Za-z0-9_-]{11}")) return part;
            }
        } catch (Exception ignored) { }
        Matcher matcher = Pattern.compile("(?:/vi(?:_webp)?/|/embed/|/shorts/|/live/)([A-Za-z0-9_-]{11})(?:[/?.]|$)")
                .matcher(candidate);
        return matcher.find() ? matcher.group(1) : null;
    }
}
