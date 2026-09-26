package io.github.chenxiex.kff;

import android.accessibilityservice.AccessibilityService;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.hardware.display.DisplayManager;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.GradientDrawable;
import android.util.Log;
import android.util.TypedValue;
import android.view.Display;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.view.WindowMetrics;
import android.view.accessibility.AccessibilityEvent;
import android.widget.LinearLayout;
import android.widget.TextView;

public final class PageButtonAccessibilityService extends AccessibilityService {
    private static final String TAG = "PageButtons";
    private static final String KINDLE_PACKAGE = "com.amazon.kindle";

    private AppSettings settings;
    private Context windowContext;
    private WindowManager windowManager;
    private LinearLayout overlayView;
    private TextView previousButton;
    private TextView nextButton;
    private WindowManager.LayoutParams overlayParams;
    private boolean overlayAttached;
    private boolean kindleForeground;
    private int touchSlop;

    private final SharedPreferences.OnSharedPreferenceChangeListener settingsListener =
            (preferences, key) -> {
                if (AppSettings.KEY_BUTTON_SIZE.equals(key)
                        || AppSettings.KEY_SPACING.equals(key)
                        || AppSettings.KEY_OPACITY.equals(key)) {
                    updateOverlayAppearance();
                } else if (AppSettings.KEY_X_FRACTION.equals(key)
                        || AppSettings.KEY_Y_FRACTION.equals(key)) {
                    positionFromPreferences();
                } else if (AppSettings.KEY_KINDLE_ONLY.equals(key)) {
                    updateVisibility();
                }
            };

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        if (overlayView != null) {
            return;
        }
        settings = new AppSettings(this);
        DisplayManager displayManager = getSystemService(DisplayManager.class);
        Display display = displayManager == null ? null
                : displayManager.getDisplay(Display.DEFAULT_DISPLAY);
        if (display == null) {
            logFailure("Default display unavailable", null);
            return;
        }
        try {
            windowContext = createWindowContext(display,
                    WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY, null);
            windowManager = windowContext.getSystemService(WindowManager.class);
            if (windowManager == null) {
                logFailure("WindowManager unavailable", null);
                return;
            }
            touchSlop = ViewConfiguration.get(windowContext).getScaledTouchSlop();
            createOverlay();
            updateVisibility();
            settings.preferences().registerOnSharedPreferenceChangeListener(settingsListener);
        } catch (RuntimeException error) {
            logFailure("Cannot initialize accessibility overlay", error);
            if (overlayAttached) {
                try {
                    hideOverlay();
                } catch (RuntimeException cleanupError) {
                    logFailure("Cannot remove failed overlay", cleanupError);
                }
            }
            overlayAttached = false;
            overlayView = null;
            overlayParams = null;
        }
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null || event.getEventType() != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            return;
        }
        CharSequence eventPackage = event.getPackageName();
        if (eventPackage == null) {
            return;
        }
        String packageName = eventPackage.toString();
        if (getPackageName().equals(packageName)) {
            CharSequence className = event.getClassName();
            if (className == null
                    || !MainActivity.class.getName().contentEquals(className)) {
                // The service's overlay can produce its own window event. The settings Activity
                // is the only application window here and must still hide the overlay.
                return;
            }
        }
        boolean isKindle = KINDLE_PACKAGE.equals(packageName);
        if (kindleForeground != isKindle) {
            kindleForeground = isKindle;
            updateVisibility();
        }
    }

    @Override
    public void onInterrupt() {
        // No ongoing action needs to be cancelled.
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        positionFromPreferences();
        if (overlayAttached) {
            overlayView.post(this::positionFromPreferences);
        }
    }

    @Override
    public void onDestroy() {
        if (settings != null) {
            settings.preferences().unregisterOnSharedPreferenceChangeListener(settingsListener);
        }
        hideOverlay();
        super.onDestroy();
    }

    private void createOverlay() {
        overlayView = new LinearLayout(windowContext);
        overlayView.setOrientation(LinearLayout.VERTICAL);
        previousButton = createButton("‹", R.string.previous_page, false);
        nextButton = createButton("›", R.string.next_page, true);
        overlayView.addView(previousButton);
        overlayView.addView(nextButton);

        overlayParams = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT);
        overlayParams.gravity = Gravity.TOP | Gravity.LEFT;
        overlayParams.setFitInsetsTypes(0);
        overlayParams.setTitle("Kindle Page Buttons");
        updateOverlayAppearance();
    }

    private TextView createButton(String text, int descriptionId, boolean next) {
        TextView button = new TextView(windowContext);
        button.setText(text);
        button.setTextColor(0xff000000);
        button.setGravity(Gravity.CENTER);
        button.setContentDescription(getString(descriptionId));
        button.setOnTouchListener(new ButtonTouchListener(next));
        return button;
    }

    private void updateOverlayAppearance() {
        if (overlayView == null || settings == null) {
            return;
        }
        int size = dp(settings.buttonSizeDp());
        int spacing = dp(settings.spacingDp());
        previousButton.setTextSize(TypedValue.COMPLEX_UNIT_PX, size * 0.58f);
        nextButton.setTextSize(TypedValue.COMPLEX_UNIT_PX, size * 0.58f);
        previousButton.setLayoutParams(new LinearLayout.LayoutParams(size, size));
        LinearLayout.LayoutParams nextLayout = new LinearLayout.LayoutParams(size, size);
        nextLayout.topMargin = spacing;
        nextButton.setLayoutParams(nextLayout);

        GradientDrawable background = new GradientDrawable();
        background.setColor((Math.round(settings.opacityPercent() * 255f / 100f) << 24)
                | 0x00ffffff);
        background.setStroke(dp(1), 0xff000000);
        previousButton.setBackground(background);
        nextButton.setBackground(background.getConstantState().newDrawable().mutate());

        overlayParams.width = size;
        overlayParams.height = size * 2 + spacing;
        positionFromPreferences();
    }

    private void updateVisibility() {
        if (overlayView == null || settings == null) {
            return;
        }
        if (!settings.showOnlyInKindle() || kindleForeground) {
            showOverlay();
        } else {
            hideOverlay();
        }
    }

    private void showOverlay() {
        if (overlayAttached) {
            return;
        }
        positionFromPreferences();
        try {
            windowManager.addView(overlayView, overlayParams);
            overlayAttached = true;
        } catch (WindowManager.BadTokenException | IllegalArgumentException error) {
            logFailure("Cannot show overlay", error);
        }
    }

    private void hideOverlay() {
        if (!overlayAttached) {
            return;
        }
        windowManager.removeView(overlayView);
        overlayAttached = false;
    }

    private void positionFromPreferences() {
        if (overlayParams == null || settings == null || windowManager == null) {
            return;
        }
        Rect safeArea = safeArea();
        int maxX = Math.max(safeArea.left, safeArea.right - overlayParams.width);
        int maxY = Math.max(safeArea.top, safeArea.bottom - overlayParams.height);
        overlayParams.x = safeArea.left
                + Math.round((maxX - safeArea.left) * settings.xFraction());
        overlayParams.y = safeArea.top
                + Math.round((maxY - safeArea.top) * settings.yFraction());
        updateWindowLayout();
    }

    private Rect safeArea() {
        WindowMetrics metrics = windowManager.getCurrentWindowMetrics();
        Rect bounds = new Rect(metrics.getBounds());
        android.graphics.Insets insets = metrics.getWindowInsets().getInsetsIgnoringVisibility(
                WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
        bounds.left += insets.left;
        bounds.top += insets.top;
        bounds.right -= insets.right;
        bounds.bottom -= insets.bottom;
        return bounds;
    }

    private void moveOverlay(int x, int y) {
        Rect safeArea = safeArea();
        int maxX = Math.max(safeArea.left, safeArea.right - overlayParams.width);
        int maxY = Math.max(safeArea.top, safeArea.bottom - overlayParams.height);
        overlayParams.x = Math.max(safeArea.left, Math.min(maxX, x));
        overlayParams.y = Math.max(safeArea.top, Math.min(maxY, y));
        updateWindowLayout();
    }

    private void updateWindowLayout() {
        if (overlayAttached) {
            windowManager.updateViewLayout(overlayView, overlayParams);
        }
    }

    private void savePosition() {
        Rect safeArea = safeArea();
        int xRange = Math.max(0, safeArea.width() - overlayParams.width);
        int yRange = Math.max(0, safeArea.height() - overlayParams.height);
        float xFraction = xRange == 0 ? 0f : (overlayParams.x - safeArea.left) / (float) xRange;
        float yFraction = yRange == 0 ? 0f : (overlayParams.y - safeArea.top) / (float) yRange;
        settings.savePosition(xFraction, yFraction);
    }

    private void turnPage(boolean next) {
        boolean moveRight = next != settings.swapButtons();
        int action = moveRight ? GLOBAL_ACTION_DPAD_RIGHT : GLOBAL_ACTION_DPAD_LEFT;
        if (!performGlobalAction(action)) {
            logFailure("D-pad global action failed", null);
        }
    }

    private void logFailure(String message, Throwable error) {
        if ((getApplicationInfo().flags & android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0) {
            Log.w(TAG, message, error);
        }
    }

    private int dp(float value) {
        return Math.round(value * windowContext.getResources().getDisplayMetrics().density);
    }

    private final class ButtonTouchListener implements View.OnTouchListener {
        private final boolean next;
        private float downRawX;
        private float downRawY;
        private int startX;
        private int startY;
        private long downTime;
        private boolean dragging;
        private boolean multiTouch;

        ButtonTouchListener(boolean next) {
            this.next = next;
        }

        @Override
        public boolean onTouch(View view, MotionEvent event) {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    downRawX = event.getRawX();
                    downRawY = event.getRawY();
                    startX = overlayParams.x;
                    startY = overlayParams.y;
                    downTime = event.getEventTime();
                    dragging = false;
                    multiTouch = false;
                    return true;
                case MotionEvent.ACTION_POINTER_DOWN:
                    multiTouch = true;
                    return true;
                case MotionEvent.ACTION_MOVE:
                    if (multiTouch || event.getPointerCount() != 1) {
                        multiTouch = true;
                        return true;
                    }
                    float dx = event.getRawX() - downRawX;
                    float dy = event.getRawY() - downRawY;
                    if (!dragging && dx * dx + dy * dy > touchSlop * touchSlop) {
                        dragging = true;
                    }
                    if (dragging) {
                        moveOverlay(startX + Math.round(dx), startY + Math.round(dy));
                    }
                    return true;
                case MotionEvent.ACTION_UP:
                    if (dragging) {
                        savePosition();
                    } else if (!multiTouch
                            && event.getEventTime() - downTime
                            < ViewConfiguration.getLongPressTimeout()) {
                        turnPage(next);
                    }
                    return true;
                case MotionEvent.ACTION_CANCEL:
                    if (dragging) {
                        savePosition();
                    }
                    return true;
                default:
                    return true;
            }
        }
    }
}
