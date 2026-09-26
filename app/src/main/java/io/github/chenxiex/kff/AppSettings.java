package io.github.chenxiex.kff;

import android.content.Context;
import android.content.SharedPreferences;

final class AppSettings {
    static final String KEY_BUTTON_SIZE = "buttonSizeDp";
    static final String KEY_OPACITY = "opacity";
    static final String KEY_SPACING = "spacingDp";
    static final String KEY_X_FRACTION = "xFraction";
    static final String KEY_Y_FRACTION = "yFraction";
    static final String KEY_SWAP_BUTTONS = "swapButtons";
    static final String KEY_BORDERLESS = "borderless";
    static final String KEY_KINDLE_ONLY = "showOnlyInKindle";

    static final int DEFAULT_BUTTON_SIZE_DP = 56;
    static final int DEFAULT_OPACITY_PERCENT = 70;
    static final int DEFAULT_SPACING_DP = 4;
    static final float DEFAULT_X_FRACTION = 1.0f;
    static final float DEFAULT_Y_FRACTION = 0.60f;

    private final SharedPreferences preferences;

    AppSettings(Context context) {
        preferences = context.getSharedPreferences("page_buttons", Context.MODE_PRIVATE);
    }

    SharedPreferences preferences() {
        return preferences;
    }

    int buttonSizeDp() {
        return clamp(preferences.getInt(KEY_BUTTON_SIZE, DEFAULT_BUTTON_SIZE_DP), 36, 96);
    }

    void setButtonSizeDp(int value) {
        preferences.edit().putInt(KEY_BUTTON_SIZE, clamp(value, 36, 96)).apply();
    }

    int opacityPercent() {
        return clamp(preferences.getInt(KEY_OPACITY, DEFAULT_OPACITY_PERCENT), 20, 100);
    }

    void setOpacityPercent(int value) {
        preferences.edit().putInt(KEY_OPACITY, clamp(value, 20, 100)).apply();
    }

    int spacingDp() {
        return clamp(preferences.getInt(KEY_SPACING, DEFAULT_SPACING_DP), 0, 32);
    }

    void setSpacingDp(int value) {
        preferences.edit().putInt(KEY_SPACING, clamp(value, 0, 32)).apply();
    }

    boolean swapButtons() {
        return preferences.getBoolean(KEY_SWAP_BUTTONS, false);
    }

    void setSwapButtons(boolean value) {
        preferences.edit().putBoolean(KEY_SWAP_BUTTONS, value).apply();
    }

    boolean borderless() {
        return preferences.getBoolean(KEY_BORDERLESS, false);
    }

    void setBorderless(boolean value) {
        preferences.edit().putBoolean(KEY_BORDERLESS, value).apply();
    }

    boolean showOnlyInKindle() {
        return preferences.getBoolean(KEY_KINDLE_ONLY, true);
    }

    void setShowOnlyInKindle(boolean value) {
        preferences.edit().putBoolean(KEY_KINDLE_ONLY, value).apply();
    }

    float xFraction() {
        return clamp(preferences.getFloat(KEY_X_FRACTION, DEFAULT_X_FRACTION));
    }

    float yFraction() {
        return clamp(preferences.getFloat(KEY_Y_FRACTION, DEFAULT_Y_FRACTION));
    }

    void savePosition(float xFraction, float yFraction) {
        preferences.edit()
                .putFloat(KEY_X_FRACTION, clamp(xFraction))
                .putFloat(KEY_Y_FRACTION, clamp(yFraction))
                .commit();
    }

    void resetPosition() {
        preferences.edit()
                .putFloat(KEY_X_FRACTION, DEFAULT_X_FRACTION)
                .putFloat(KEY_Y_FRACTION, DEFAULT_Y_FRACTION)
                .apply();
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static float clamp(float value) {
        return Math.max(0.0f, Math.min(1.0f, value));
    }
}
