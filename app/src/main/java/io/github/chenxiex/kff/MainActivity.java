package io.github.chenxiex.kff;

import android.accessibilityservice.AccessibilityServiceInfo;
import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityManager;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;

import java.util.List;

public final class MainActivity extends Activity {
    private AppSettings settings;
    private TextView serviceStatus;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        settings = new AppSettings(this);
        WindowManager windowManager = getSystemService(WindowManager.class);
        if (windowManager != null) {
            settings.migrateLegacyButtonSize(
                    AppSettings.safeArea(windowManager.getCurrentWindowMetrics()),
                    getResources().getDisplayMetrics().density);
        }

        ScrollView scrollView = new ScrollView(this);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        int padding = dp(20);
        content.setPadding(padding, padding, padding, padding);
        scrollView.addView(content);

        TextView title = label(getString(R.string.app_name), 24);
        content.addView(title);
        content.addView(label(getString(R.string.service_title), 18));
        serviceStatus = label("", 16);
        content.addView(serviceStatus);

        Button accessibilitySettings = plainButton(R.string.open_accessibility_settings);
        accessibilitySettings.setOnClickListener(view ->
                startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));
        content.addView(accessibilitySettings);

        addSeekBar(content, R.string.button_width, AppSettings.MIN_BUTTON_WIDTH_PERCENT,
                AppSettings.MAX_BUTTON_WIDTH_PERCENT, settings.buttonWidthPercent(), "%",
                settings::setButtonWidthPercent);
        addSeekBar(content, R.string.button_height, AppSettings.MIN_BUTTON_HEIGHT_PERCENT,
                AppSettings.MAX_BUTTON_HEIGHT_PERCENT, settings.buttonHeightPercent(), "%",
                settings::setButtonHeightPercent);
        addSeekBar(content, R.string.opacity, AppSettings.MIN_OPACITY_PERCENT,
                AppSettings.MAX_OPACITY_PERCENT, settings.opacityPercent(), "%",
                settings::setOpacityPercent);
        addSeekBar(content, R.string.button_spacing, 0, 32, settings.spacingDp(), "dp",
                settings::setSpacingDp);

        CheckBox borderless = new CheckBox(this);
        borderless.setBackground(null);
        borderless.setText(R.string.borderless_mode);
        borderless.setChecked(settings.borderless());
        borderless.setOnCheckedChangeListener((button, checked) -> settings.setBorderless(checked));
        content.addView(borderless);

        CheckBox swapButtons = new CheckBox(this);
        swapButtons.setBackground(null);
        swapButtons.setText(R.string.swap_buttons);
        swapButtons.setChecked(settings.swapButtons());
        swapButtons.setOnCheckedChangeListener((button, checked) -> settings.setSwapButtons(checked));
        content.addView(swapButtons);

        content.addView(label(getString(R.string.display_scope), 18));
        RadioGroup scope = new RadioGroup(this);
        RadioButton kindleOnly = new RadioButton(this);
        kindleOnly.setBackground(null);
        kindleOnly.setText(R.string.kindle_only);
        kindleOnly.setId(View.generateViewId());
        scope.addView(kindleOnly);
        RadioButton allApps = new RadioButton(this);
        allApps.setBackground(null);
        allApps.setText(R.string.all_apps);
        allApps.setId(View.generateViewId());
        scope.addView(allApps);
        scope.check(settings.showOnlyInKindle() ? kindleOnly.getId() : allApps.getId());
        scope.setOnCheckedChangeListener((group, checkedId) ->
                settings.setShowOnlyInKindle(checkedId == kindleOnly.getId()));
        content.addView(scope);

        Button resetPosition = plainButton(R.string.reset_position);
        resetPosition.setOnClickListener(view -> settings.resetPosition());
        content.addView(resetPosition);
        content.addView(label(getString(R.string.usage_hint), 15));

        setContentView(scrollView);
    }

    @Override
    protected void onResume() {
        super.onResume();
        serviceStatus.setText(isServiceEnabled()
                ? R.string.service_enabled : R.string.service_disabled);
    }

    private boolean isServiceEnabled() {
        AccessibilityManager manager = getSystemService(AccessibilityManager.class);
        if (manager == null) {
            return false;
        }
        ComponentName expected = new ComponentName(this, PageButtonAccessibilityService.class);
        List<AccessibilityServiceInfo> services = manager.getEnabledAccessibilityServiceList(
                AccessibilityServiceInfo.FEEDBACK_ALL_MASK);
        for (AccessibilityServiceInfo info : services) {
            if (expected.equals(ComponentName.unflattenFromString(info.getId()))) {
                return true;
            }
        }
        return false;
    }

    private void addSeekBar(LinearLayout parent, int labelId, int min, int max,
            int initial, String unit, ValueConsumer onChange) {
        TextView label = label("", 18);
        parent.addView(label);
        SeekBar seekBar = new SeekBar(this);
        seekBar.setMax(max - min);
        seekBar.setProgress(initial - min);
        label.setText(formatSetting(labelId, initial, unit));
        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                if (fromUser) {
                    int value = min + progress;
                    label.setText(formatSetting(labelId, value, unit));
                    onChange.accept(value);
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar bar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar bar) {
            }
        });
        parent.addView(seekBar);
    }

    private String formatSetting(int labelId, int value, String unit) {
        return getString(R.string.setting_value_format, getString(labelId), value, unit);
    }

    private TextView label(String text, int textSizeSp) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextSize(textSizeSp);
        view.setTextColor(0xff111111);
        view.setPadding(0, dp(10), 0, dp(4));
        return view;
    }

    private Button plainButton(int textId) {
        Button button = new Button(this);
        button.setText(textId);
        button.setAllCaps(false);
        button.setStateListAnimator(null);
        GradientDrawable background = new GradientDrawable();
        background.setColor(0xffffffff);
        background.setStroke(dp(1), 0xff111111);
        button.setBackground(background);
        button.setMinHeight(dp(48));
        return button;
    }

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private interface ValueConsumer {
        void accept(int value);
    }
}
