package br.casa.sala;

import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.provider.Settings;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.ViewConfiguration;
import android.view.View;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Filters only dedicated TV remote shortcuts so they work over any foreground app. */
public final class RemoteShortcutService extends AccessibilityService {
    private WindowManager windowManager;
    private View quickPanel;
    private final android.os.Handler handler = new android.os.Handler(android.os.Looper.getMainLooper());
    private boolean settingsKeyHeld;
    private boolean fullSettingsOpened;
    private final Runnable openFullSettingsAfterHold = () -> {
        if (!settingsKeyHeld) return;
        fullSettingsOpened = true;
        openFullSettings();
    };

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        android.accessibilityservice.AccessibilityServiceInfo info = getServiceInfo();
        if (info != null) {
            info.flags |= android.accessibilityservice.AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS;
            setServiceInfo(info);
        }
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        // Window-change events are declared only to satisfy the service contract; no UI content is read.
    }

    @Override
    public void onInterrupt() { dismissQuickPanel(); }

    @Override
    public void onDestroy() {
        handler.removeCallbacks(openFullSettingsAfterHold);
        dismissQuickPanel();
        super.onDestroy();
    }

    @Override
    protected boolean onKeyEvent(KeyEvent event) {
        if (event.getAction() == KeyEvent.ACTION_DOWN
                && (event.getKeyCode() == 570 || event.getScanCode() == 570
                || event.getScanCode() == 0x000c0055
                || event.getKeyCode() == 83 || event.getScanCode() == 561
                || event.getKeyCode() == 365 || event.getScanCode() == 365
                || event.getScanCode() == 0x000c005b)) {
            android.util.Log.i("SalaRemote", "Tecla TCL: keyCode=" + event.getKeyCode()
                    + " scanCode=" + event.getScanCode());
        }
        // Route the dedicated gear key to TCL's picture settings panel on a short
        // press. The key is not registered as a global key by this TV's overlay.
        if (isFactorySettingsKey(event)) return handleSettingsKey(event);
        if (quickPanel != null && event.getAction() == KeyEvent.ACTION_DOWN
                && (event.getKeyCode() == KeyEvent.KEYCODE_BACK
                || event.getKeyCode() == KeyEvent.KEYCODE_ESCAPE)) {
            dismissQuickPanel();
            return true;
        }
        if (event.getAction() != KeyEvent.ACTION_DOWN || event.getRepeatCount() > 0) return false;
        // TCL remotes can expose the same branded key as either a private Android
        // keycode or the Linux key-layout code. Try both so the factory key table
        // keeps working across the receiver and Bluetooth variants.
        int scanCode = event.getScanCode();
        int keyCode = event.getKeyCode();
        if (scanCode == 456 || keyCode == 456) {
            openQuickSettings();
            return true;
        }
        String[] packages = packagesFor(scanCode);
        if (packages != null) {
            for (String pkg : packages) if (launch(pkg)) return true;
        }
        if (keyCode != scanCode) {
            packages = packagesFor(keyCode);
            if (packages != null) {
                for (String pkg : packages) if (launch(pkg)) return true;
            }
        }
        return false;
    }

    private boolean isFactorySettingsKey(KeyEvent event) {
        return event.getKeyCode() == KeyEvent.KEYCODE_NOTIFICATION
                || event.getScanCode() == 561
                // Alternate reports seen on TCL remotes for the same gear key.
                || event.getKeyCode() == 570
                || event.getScanCode() == 570
                || event.getScanCode() == 0x000c0055;
    }

    private boolean handleSettingsKey(KeyEvent event) {
        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            if (event.getRepeatCount() == 0) {
                settingsKeyHeld = true;
                fullSettingsOpened = false;
                handler.removeCallbacks(openFullSettingsAfterHold);
                handler.postDelayed(openFullSettingsAfterHold, ViewConfiguration.getLongPressTimeout());
            }
            return true;
        }
        if (event.getAction() == KeyEvent.ACTION_UP) {
            handler.removeCallbacks(openFullSettingsAfterHold);
            boolean wasHeld = settingsKeyHeld;
            settingsKeyHeld = false;
            if (wasHeld && !fullSettingsOpened) openPictureSettings();
            fullSettingsOpened = false;
            return true;
        }
        return true;
    }

    private void openPictureSettings() {
        // On this TCL firmware, DISPLAY_SETTINGS is handled by PictureActivity,
        // which opens the left-side image settings window over the active app.
        Intent panel = new Intent(Settings.ACTION_DISPLAY_SETTINGS)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            startActivity(panel);
            android.util.Log.i("SalaRemote", "Abrindo painel lateral de imagem TCL pela tecla Conf");
        } catch (Exception e) {
            android.util.Log.w("SalaRemote", "Não foi possível abrir as configurações de imagem TCL", e);
        }
    }

    private void openFullSettings() {
        Intent settings = new Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            startActivity(settings);
            android.util.Log.i("SalaRemote", "Abrindo configurações completas pela pressão longa da tecla Conf");
        } catch (Exception e) {
            android.util.Log.w("SalaRemote", "Não foi possível abrir as configurações completas da TV", e);
        }
    }



    private void openQuickSettings() {
        if (quickPanel != null) {
            dismissQuickPanel();
            return;
        }
        if (windowManager == null) windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        if (windowManager == null) {
            open(new Intent(Settings.ACTION_SETTINGS));
            return;
        }

        int panelColor = 0xF2182134;
        FrameLayout overlay = new FrameLayout(this);
        overlay.setBackgroundColor(0x55000000);
        overlay.setFocusableInTouchMode(true);
        overlay.setOnClickListener(v -> dismissQuickPanel());

        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(24), dp(26), dp(24), dp(22));
        GradientDrawable panelBackground = new GradientDrawable();
        panelBackground.setColor(panelColor);
        panelBackground.setCornerRadius(dp(24));
        panelBackground.setStroke(dp(1), 0x55FFFFFF);
        panel.setBackground(panelBackground);
        panel.setElevation(dp(16));
        panel.setOnClickListener(v -> { });

        TextView title = new TextView(this);
        title.setText("Configurações rápidas");
        title.setTextColor(Color.WHITE);
        title.setTextSize(22);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        title.setPadding(dp(10), dp(8), dp(10), dp(18));
        panel.addView(title, new LinearLayout.LayoutParams(-1, -2));

        addQuickOption(panel, "Wi-Fi", Settings.ACTION_WIFI_SETTINGS);
        addQuickOption(panel, "Imagem e tela", Settings.ACTION_DISPLAY_SETTINGS);
        addQuickOption(panel, "Som", Settings.ACTION_SOUND_SETTINGS);
        addQuickOption(panel, "Bluetooth e controles", Settings.ACTION_BLUETOOTH_SETTINGS);
        addQuickOption(panel, "Configurações completas", Settings.ACTION_SETTINGS);

        FrameLayout.LayoutParams panelParams = new FrameLayout.LayoutParams(dp(430), -1, Gravity.START);
        panelParams.setMargins(dp(18), dp(18), 0, dp(18));
        overlay.addView(panel, panelParams);
        overlay.setFocusable(true);

        WindowManager.LayoutParams windowParams = new WindowManager.LayoutParams(
                -1, -1,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                PixelFormat.TRANSLUCENT);
        windowParams.gravity = Gravity.START | Gravity.TOP;
        try {
            windowManager.addView(overlay, windowParams);
            quickPanel = overlay;
            android.util.Log.i("SalaRemote", "Painel rápido lateral aberto");
            overlay.requestFocus();
            panel.getChildAt(1).requestFocus();
        } catch (Exception e) {
            android.util.Log.w("SalaRemote", "Não foi possível mostrar o painel rápido", e);
            quickPanel = null;
            open(new Intent(Settings.ACTION_SETTINGS));
        }
    }

    private void addQuickOption(LinearLayout panel, String label, String action) {
        TextView option = new TextView(this);
        option.setText(label);
        option.setTextColor(Color.WHITE);
        option.setTextSize(17);
        option.setTypeface(null, android.graphics.Typeface.BOLD);
        option.setGravity(Gravity.CENTER_VERTICAL);
        option.setPadding(dp(16), 0, dp(16), 0);
        option.setFocusable(true);
        option.setClickable(true);
        option.setBackground(quickOptionBackground(false));
        option.setOnFocusChangeListener((view, focused) -> option.setBackground(quickOptionBackground(focused)));
        option.setOnClickListener(view -> {
            dismissQuickPanel();
            open(new Intent(action));
        });
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, dp(58));
        params.bottomMargin = dp(7);
        panel.addView(option, params);
    }

    private GradientDrawable quickOptionBackground(boolean focused) {
        GradientDrawable background = new GradientDrawable();
        background.setColor(focused ? Color.WHITE : 0x332D426B);
        background.setCornerRadius(dp(14));
        if (!focused) background.setStroke(dp(1), 0x44FFFFFF);
        return background;
    }

    private void dismissQuickPanel() {
        if (quickPanel == null) return;
        try { if (windowManager != null) windowManager.removeView(quickPanel); }
        catch (Exception ignored) { }
        quickPanel = null;
    }

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private String[] packagesFor(int code) {
        switch (code) {
            case 472: return new String[]{"com.netflix.ninja"};
            case 473: return new String[]{"com.amazon.amazonvideo.livingroom"};
            case 569: return new String[]{"com.google.android.youtube.tv"};
            case 586: return new String[]{"com.globo.globotv"};
            case 762: return new String[]{"com.disney.disneyplus"};
            case 759: return new String[]{"com.iqiyi.i18n.tv"};
            case 652: return new String[]{"com.apple.atve.androidtv.appletv"};
            case 649: return new String[]{"com.pluto.tv"};
            case 747: return findPackage("wetv", "we tv");
            case 751: return findPackage("okko");
            case 755: return findPackage("fpt play", "fptplayer");
            case 654: return findPackage("stan");
            case 743: return findPackage("vidio");
            case 646: return findPackage("wink");
            case 640: return findPackage("vix");
            case 641: return findPackage("sonyliv", "sony liv");
            case 642: return findPackage("vtvgo", "vtv go");
            case 753: return findPackage("tot");
            case 746: return findPackage("ivi");
            case 651: return findPackage("browsehere", "browser");
            case 655: return findPackage("galaview", "gala view");
            case 763: return findPackage("freeview play", "freeview");
            default: return null;
        }
    }

    private String[] findPackage(String... labels) {
        PackageManager pm = getPackageManager();
        for (android.content.pm.ResolveInfo ri : pm.queryIntentActivities(
                new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER), 0)) {
            String label = String.valueOf(ri.loadLabel(pm)).toLowerCase(java.util.Locale.ROOT);
            for (String target : labels) if (label.contains(target)) return new String[]{ri.activityInfo.packageName};
        }
        return new String[0];
    }

    private boolean launch(String pkg) {
        PackageManager pm = getPackageManager();
        Intent intent = pm.getLeanbackLaunchIntentForPackage(pkg);
        if (intent == null) intent = pm.getLaunchIntentForPackage(pkg);
        if (intent != null) History.claimPackage(this, pkg);
        return intent != null && open(intent);
    }

    private boolean open(Intent intent) {
        try {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
            startActivity(intent);
            return true;
        } catch (Exception e) {
            android.util.Log.w("SalaRemote", "Não foi possível abrir destino do controle", e);
            return false;
        }
    }
}
