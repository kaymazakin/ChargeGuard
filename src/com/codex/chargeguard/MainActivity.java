package com.codex.chargeguard;

import android.Manifest;
import android.app.Activity;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

public class MainActivity extends Activity {
    static final String PREFS = "chargeguard_prefs";
    static final String KEY_THRESHOLD = "alarm_threshold";
    static final String KEY_SOUND = "alarm_sound";
    static final String KEY_VIBRATION = "alarm_vibration";
    static final String KEY_FULL_ALERT = "full_alert";
    static final String KEY_NIGHT_MODE = "night_mode";
    static final String KEY_MONITORING = "monitoring_enabled";
    static final String KEY_DAY = "stats_day";
    static final String KEY_MAX_TEMP = "max_temp";
    static final String KEY_TEMP_SUM = "temp_sum";
    static final String KEY_TEMP_SAMPLES = "temp_samples";
    static final String KEY_OVER40_EVENTS = "over40_events";
    static final String KEY_OVER40_SECONDS = "over40_seconds";
    static final String KEY_CHARGE_SECONDS = "charge_seconds";
    static final String KEY_LONGEST_CHARGE = "longest_charge_seconds";
    static final String KEY_LAST_SAMPLE_MS = "last_sample_ms";
    static final String KEY_WAS_ABOVE40 = "was_above40";
    static final String KEY_WAS_CHARGING = "was_charging";
    static final String KEY_CHARGE_START_MS = "charge_start_ms";
    static final String KEY_CHARGE_START_LEVEL = "charge_start_level";
    static final String KEY_LAST_HEAT_ALERT_MS = "last_heat_alert_ms";
    static final String KEY_LAST_HEALTH_ALERT_MS = "last_health_alert_ms";
    static final String KEY_LAST_FULL_ALERT_MS = "last_full_alert_ms";
    static final String KEY_LAST_LONG_ALERT_MS = "last_long_alert_ms";
    static final String KEY_HOUR_MAX_PREFIX = "hour_max_";
    static final int DEFAULT_THRESHOLD = 40;

    private static final int COLOR_BG = Color.rgb(244, 247, 248);
    private static final int COLOR_INK = Color.rgb(23, 32, 38);
    private static final int COLOR_MUTED = Color.rgb(101, 116, 126);
    private static final int COLOR_TEAL = Color.rgb(0, 168, 150);
    private static final int COLOR_CORAL = Color.rgb(255, 107, 74);
    private static final int COLOR_AMBER = Color.rgb(246, 183, 60);
    private static final int COLOR_BLUE = Color.rgb(47, 128, 237);
    private static final int COLOR_CARD = Color.WHITE;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private SharedPreferences prefs;
    private HeatGaugeView gaugeView;
    private ReportGraphView reportGraphView;
    private LinearLayout livePanel;
    private LinearLayout reportPanel;
    private LinearLayout settingsPanel;
    private Button tabLive;
    private Button tabReport;
    private Button tabSettings;
    private Button monitorButton;
    private TextView thresholdLabel;
    private TextView batteryValue;
    private TextView tempValue;
    private TextView statusValue;
    private TextView durationValue;
    private TextView etaValue;
    private TextView riskValue;
    private TextView healthValue;
    private TextView healthScoreValue;
    private TextView voltageValue;
    private TextView currentValue;
    private TextView technologyValue;
    private TextView adviceValue;
    private TextView reportMaxTemp;
    private TextView reportAvgTemp;
    private TextView reportOver40;
    private TextView reportChargeTime;
    private TextView reportScore;
    private TextView reportLongest;
    private TextView reportHealthScore;
    private TextView reportHealthState;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        seedDefaults();
        configureWindow();
        requestNotificationPermission();
        setContentView(buildContent());
        selectTab(0);
        handler.post(new Runnable() {
            @Override
            public void run() {
                updateDashboard();
                handler.postDelayed(this, 2000);
            }
        });
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }

    private void seedDefaults() {
        if (!prefs.contains(KEY_THRESHOLD)) {
            prefs.edit()
                    .putInt(KEY_THRESHOLD, DEFAULT_THRESHOLD)
                    .putBoolean(KEY_SOUND, true)
                    .putBoolean(KEY_VIBRATION, true)
                    .putBoolean(KEY_FULL_ALERT, true)
                    .putBoolean(KEY_NIGHT_MODE, true)
                    .apply();
        }
        ensureToday(prefs);
    }

    private void configureWindow() {
        Window window = getWindow();
        if (Build.VERSION.SDK_INT >= 21) {
            window.setStatusBarColor(COLOR_BG);
            window.setNavigationBarColor(COLOR_INK);
        }
        if (Build.VERSION.SDK_INT >= 23) {
            window.getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        }
    }

    private View buildContent() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(COLOR_BG);

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(18), dp(18), dp(18), dp(24));
        scroll.addView(content, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        content.addView(header, matchWrap());

        LinearLayout titleBlock = new LinearLayout(this);
        titleBlock.setOrientation(LinearLayout.VERTICAL);
        header.addView(titleBlock, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        titleBlock.addView(text("Şarj Koruyucu", 28, COLOR_INK, Typeface.BOLD));
        titleBlock.addView(text("ChargeGuard", 14, COLOR_MUTED, Typeface.NORMAL));

        TextView statusPill = text("Aktif", 13, Color.WHITE, Typeface.BOLD);
        statusPill.setGravity(Gravity.CENTER);
        statusPill.setPadding(dp(14), dp(8), dp(14), dp(8));
        statusPill.setBackground(round(COLOR_TEAL, 22, 0, 0));
        header.addView(statusPill);

        LinearLayout tabs = new LinearLayout(this);
        tabs.setOrientation(LinearLayout.HORIZONTAL);
        tabs.setGravity(Gravity.CENTER);
        tabs.setPadding(0, dp(18), 0, dp(12));
        content.addView(tabs, matchWrap());
        tabLive = tabButton("Anlık");
        tabReport = tabButton("Rapor");
        tabSettings = tabButton("Ayarlar");
        tabs.addView(tabLive, tabParams());
        tabs.addView(tabReport, tabParams());
        tabs.addView(tabSettings, tabParams());

        tabLive.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                selectTab(0);
            }
        });
        tabReport.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                selectTab(1);
            }
        });
        tabSettings.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                selectTab(2);
            }
        });

        livePanel = new LinearLayout(this);
        livePanel.setOrientation(LinearLayout.VERTICAL);
        content.addView(livePanel, matchWrap());
        buildLivePanel(livePanel);

        reportPanel = new LinearLayout(this);
        reportPanel.setOrientation(LinearLayout.VERTICAL);
        content.addView(reportPanel, matchWrap());
        buildReportPanel(reportPanel);

        settingsPanel = new LinearLayout(this);
        settingsPanel.setOrientation(LinearLayout.VERTICAL);
        content.addView(settingsPanel, matchWrap());
        buildSettingsPanel(settingsPanel);

        return scroll;
    }

    private void buildLivePanel(LinearLayout parent) {
        gaugeView = new HeatGaugeView(this);
        parent.addView(gaugeView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(230)));

        LinearLayout rowOne = row();
        parent.addView(rowOne, matchWrap());
        batteryValue = valueText("--");
        tempValue = valueText("--");
        rowOne.addView(metric("Pil", batteryValue, COLOR_BLUE), metricParams(true));
        rowOne.addView(metric("Sıcaklık", tempValue, COLOR_CORAL), metricParams(false));

        LinearLayout rowTwo = row();
        parent.addView(rowTwo, matchWrap());
        statusValue = valueText("--");
        durationValue = valueText("--");
        rowTwo.addView(metric("Durum", statusValue, COLOR_TEAL), metricParams(true));
        rowTwo.addView(metric("Şarj Süresi", durationValue, COLOR_AMBER), metricParams(false));

        LinearLayout rowThree = row();
        parent.addView(rowThree, matchWrap());
        etaValue = valueText("--");
        riskValue = valueText("--");
        rowThree.addView(metric("Dolma Tahmini", etaValue, COLOR_BLUE), metricParams(true));
        rowThree.addView(metric("Risk", riskValue, COLOR_CORAL), metricParams(false));

        LinearLayout healthRow = row();
        parent.addView(healthRow, matchWrap());
        healthValue = valueText("--");
        healthScoreValue = valueText("--");
        healthRow.addView(metric("Pil Sağlığı", healthValue, COLOR_TEAL), metricParams(true));
        healthRow.addView(metric("Tahmini Sağlık", healthScoreValue, COLOR_BLUE), metricParams(false));

        LinearLayout detailRow = row();
        parent.addView(detailRow, matchWrap());
        voltageValue = valueText("--");
        currentValue = valueText("--");
        detailRow.addView(metric("Voltaj", voltageValue, COLOR_AMBER), metricParams(true));
        detailRow.addView(metric("Akım", currentValue, COLOR_CORAL), metricParams(false));

        LinearLayout technologyRow = row();
        parent.addView(technologyRow, matchWrap());
        technologyValue = valueText("--");
        adviceValue = valueText("--");
        adviceValue.setTextSize(14);
        technologyRow.addView(metric("Pil Tipi", technologyValue, COLOR_BLUE), metricParams(true));
        technologyRow.addView(metric("Öneri", adviceValue, COLOR_TEAL), metricParams(false));

        LinearLayout thresholdCard = card();
        thresholdCard.setOrientation(LinearLayout.VERTICAL);
        thresholdCard.setPadding(dp(16), dp(14), dp(16), dp(12));
        LinearLayout.LayoutParams thresholdParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        thresholdParams.setMargins(0, dp(10), 0, dp(12));
        parent.addView(thresholdCard, thresholdParams);

        thresholdLabel = text("", 16, COLOR_INK, Typeface.BOLD);
        thresholdCard.addView(thresholdLabel);
        SeekBar thresholdSeek = new SeekBar(this);
        thresholdSeek.setMax(7);
        thresholdSeek.setProgress(prefs.getInt(KEY_THRESHOLD, DEFAULT_THRESHOLD) - 38);
        thresholdCard.addView(thresholdSeek, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        thresholdSeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                int value = 38 + progress;
                prefs.edit().putInt(KEY_THRESHOLD, value).apply();
                thresholdLabel.setText("Alarm Sınırı: " + value + "°C");
                updateDashboard();
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });
        thresholdLabel.setText("Alarm Sınırı: " + prefs.getInt(KEY_THRESHOLD, DEFAULT_THRESHOLD) + "°C");

        monitorButton = new Button(this);
        monitorButton.setAllCaps(false);
        monitorButton.setTextColor(Color.WHITE);
        monitorButton.setTextSize(16);
        monitorButton.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        monitorButton.setPadding(0, dp(12), 0, dp(12));
        monitorButton.setBackground(round(COLOR_INK, 8, 0, 0));
        parent.addView(monitorButton, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(54)));
        monitorButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                toggleMonitoring();
            }
        });
    }

    private void buildReportPanel(LinearLayout parent) {
        reportGraphView = new ReportGraphView(this);
        LinearLayout.LayoutParams graphParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(170));
        graphParams.setMargins(0, dp(4), 0, dp(14));
        parent.addView(reportGraphView, graphParams);

        LinearLayout rowOne = row();
        parent.addView(rowOne, matchWrap());
        reportMaxTemp = valueText("--");
        reportAvgTemp = valueText("--");
        rowOne.addView(metric("Maksimum", reportMaxTemp, COLOR_CORAL), metricParams(true));
        rowOne.addView(metric("Ortalama", reportAvgTemp, COLOR_TEAL), metricParams(false));

        LinearLayout rowTwo = row();
        parent.addView(rowTwo, matchWrap());
        reportOver40 = valueText("--");
        reportChargeTime = valueText("--");
        rowTwo.addView(metric("40°C Üstü", reportOver40, COLOR_AMBER), metricParams(true));
        rowTwo.addView(metric("Toplam Şarj", reportChargeTime, COLOR_BLUE), metricParams(false));

        LinearLayout rowThree = row();
        parent.addView(rowThree, matchWrap());
        reportScore = valueText("--");
        reportLongest = valueText("--");
        rowThree.addView(metric("Alışkanlık Skoru", reportScore, COLOR_TEAL), metricParams(true));
        rowThree.addView(metric("En Uzun Şarj", reportLongest, COLOR_CORAL), metricParams(false));

        LinearLayout rowFour = row();
        parent.addView(rowFour, matchWrap());
        reportHealthScore = valueText("--");
        reportHealthState = valueText("--");
        rowFour.addView(metric("Tahmini Sağlık", reportHealthScore, COLOR_BLUE), metricParams(true));
        rowFour.addView(metric("Sistem Durumu", reportHealthState, COLOR_TEAL), metricParams(false));
    }

    private void buildSettingsPanel(LinearLayout parent) {
        parent.addView(settingSwitch("Alarm sesi", KEY_SOUND, true));
        parent.addView(settingSwitch("Titreşim", KEY_VIBRATION, true));
        parent.addView(settingSwitch("%100 uyarısı", KEY_FULL_ALERT, true));
        parent.addView(settingSwitch("Gece koruması", KEY_NIGHT_MODE, true));

        LinearLayout proCard = card();
        proCard.setOrientation(LinearLayout.VERTICAL);
        proCard.setPadding(dp(16), dp(14), dp(16), dp(16));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, dp(12), 0, 0);
        parent.addView(proCard, params);
        proCard.addView(text("Pro Paket", 18, COLOR_INK, Typeface.BOLD));
        TextView price = text("Tek seferlik satın alma: 29,99 TL", 14, COLOR_MUTED, Typeface.NORMAL);
        price.setPadding(0, dp(5), 0, dp(10));
        proCard.addView(price);
        TextView features = text("Özel eşik, gece koruması, geçmiş grafikler, farklı alarm profilleri.", 14, COLOR_INK, Typeface.NORMAL);
        features.setLineSpacing(2, 1.0f);
        proCard.addView(features);

        Button billingButton = new Button(this);
        billingButton.setAllCaps(false);
        billingButton.setText("Google Play Billing bağla");
        billingButton.setTextColor(Color.WHITE);
        billingButton.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        billingButton.setBackground(round(COLOR_TEAL, 8, 0, 0));
        LinearLayout.LayoutParams buttonParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(48));
        buttonParams.setMargins(0, dp(14), 0, 0);
        proCard.addView(billingButton, buttonParams);
        billingButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Toast.makeText(MainActivity.this, "Satın alma entegrasyonu için Google Play Billing eklenir.", Toast.LENGTH_LONG).show();
            }
        });
    }

    private void updateDashboard() {
        BatterySnapshot snapshot = readBattery(this);
        recordSnapshot(this, snapshot, System.currentTimeMillis());

        int threshold = prefs.getInt(KEY_THRESHOLD, DEFAULT_THRESHOLD);
        gaugeView.setData(snapshot.temperatureC, threshold, snapshot.isCharging);
        batteryValue.setText(snapshot.level + "%");
        tempValue.setText(formatTemp(snapshot.temperatureC));
        statusValue.setText(snapshot.statusText());
        durationValue.setText(currentChargeDuration());
        etaValue.setText(estimateFullTime(snapshot));
        riskValue.setText(riskText(snapshot.temperatureC, threshold));
        riskValue.setTextColor(riskColor(snapshot.temperatureC, threshold));

        DailyStats stats = readStats(prefs);
        int healthScore = computeHealthScore(snapshot, stats);
        healthValue.setText(snapshot.healthText());
        healthValue.setTextColor(healthColor(snapshot.health));
        healthScoreValue.setText(healthScore + "/100");
        healthScoreValue.setTextColor(scoreColor(healthScore));
        voltageValue.setText(formatVoltage(snapshot.voltageMv));
        currentValue.setText(formatCurrent(snapshot.currentUa));
        technologyValue.setText(snapshot.technologyText());
        adviceValue.setText(healthAdvice(snapshot, stats, threshold));

        boolean monitoring = prefs.getBoolean(KEY_MONITORING, false);
        monitorButton.setText(monitoring ? "Şarj Takibini Durdur" : "Şarj Takibini Başlat");
        monitorButton.setBackground(round(monitoring ? COLOR_CORAL : COLOR_INK, 8, 0, 0));

        reportMaxTemp.setText(stats.samples == 0 ? "--" : formatTemp(stats.maxTemp));
        reportAvgTemp.setText(stats.samples == 0 ? "--" : formatTemp(stats.averageTemp()));
        reportOver40.setText(formatDuration(stats.over40Seconds));
        reportChargeTime.setText(formatDuration(stats.chargeSeconds));
        reportScore.setText(computeScore(stats) + "/100");
        reportLongest.setText(formatDuration(stats.longestChargeSeconds));
        reportHealthScore.setText(healthScore + "/100");
        reportHealthScore.setTextColor(scoreColor(healthScore));
        reportHealthState.setText(snapshot.healthText());
        reportHealthState.setTextColor(healthColor(snapshot.health));
        reportGraphView.setPrefs(prefs);
    }

    private void toggleMonitoring() {
        boolean monitoring = prefs.getBoolean(KEY_MONITORING, false);
        Intent intent = new Intent(this, BatteryMonitorService.class);
        if (monitoring) {
            stopService(intent);
            prefs.edit().putBoolean(KEY_MONITORING, false).apply();
            Toast.makeText(this, "Takip durduruldu.", Toast.LENGTH_SHORT).show();
        } else {
            requestNotificationPermission();
            if (Build.VERSION.SDK_INT >= 26) {
                startForegroundService(intent);
            } else {
                startService(intent);
            }
            prefs.edit().putBoolean(KEY_MONITORING, true).apply();
            Toast.makeText(this, "Şarj takibi başladı.", Toast.LENGTH_SHORT).show();
        }
        updateDashboard();
    }

    private void requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 44);
        }
    }

    private void selectTab(int tab) {
        livePanel.setVisibility(tab == 0 ? View.VISIBLE : View.GONE);
        reportPanel.setVisibility(tab == 1 ? View.VISIBLE : View.GONE);
        settingsPanel.setVisibility(tab == 2 ? View.VISIBLE : View.GONE);
        styleTab(tabLive, tab == 0);
        styleTab(tabReport, tab == 1);
        styleTab(tabSettings, tab == 2);
    }

    private LinearLayout settingSwitch(String label, final String key, boolean defaultValue) {
        LinearLayout row = card();
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(16), dp(12), dp(12), dp(12));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, 0, 0, dp(10));
        row.setLayoutParams(params);

        TextView text = text(label, 16, COLOR_INK, Typeface.BOLD);
        row.addView(text, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        Switch sw = new Switch(this);
        sw.setChecked(prefs.getBoolean(key, defaultValue));
        sw.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                prefs.edit().putBoolean(key, isChecked).apply();
            }
        });
        row.addView(sw);
        return row;
    }

    private LinearLayout metric(String label, TextView value, int accent) {
        LinearLayout box = card();
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(14), dp(12), dp(14), dp(12));
        TextView labelView = text(label, 12, COLOR_MUTED, Typeface.BOLD);
        labelView.setSingleLine(true);
        labelView.setEllipsize(TextUtils.TruncateAt.END);
        box.addView(labelView);
        value.setTextColor(COLOR_INK);
        value.setPadding(0, dp(5), 0, 0);
        box.addView(value);
        View stripe = new View(this);
        stripe.setBackgroundColor(accent);
        LinearLayout.LayoutParams stripeParams = new LinearLayout.LayoutParams(dp(36), dp(3));
        stripeParams.setMargins(0, dp(10), 0, 0);
        box.addView(stripe, stripeParams);
        return box;
    }

    private TextView valueText(String value) {
        TextView view = text(value, 20, COLOR_INK, Typeface.BOLD);
        view.setSingleLine(false);
        return view;
    }

    private LinearLayout row() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        return row;
    }

    private LinearLayout card() {
        LinearLayout card = new LinearLayout(this);
        card.setBackground(round(COLOR_CARD, 8, Color.rgb(223, 230, 234), 1));
        return card;
    }

    private Button tabButton(String label) {
        Button button = new Button(this);
        button.setAllCaps(false);
        button.setText(label);
        button.setTextSize(14);
        button.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        button.setPadding(0, dp(8), 0, dp(8));
        return button;
    }

    private void styleTab(Button button, boolean selected) {
        button.setTextColor(selected ? Color.WHITE : COLOR_MUTED);
        button.setBackground(round(selected ? COLOR_INK : Color.TRANSPARENT, 8,
                selected ? 0 : Color.rgb(207, 217, 222), selected ? 0 : 1));
    }

    private LinearLayout.LayoutParams tabParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(46), 1f);
        params.setMargins(dp(3), 0, dp(3), 0);
        return params;
    }

    private LinearLayout.LayoutParams metricParams(boolean left) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        params.setMargins(left ? 0 : dp(5), dp(5), left ? dp(5) : 0, dp(5));
        return params;
    }

    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private TextView text(String text, int sp, int color, int style) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextSize(sp);
        view.setTextColor(color);
        view.setTypeface(Typeface.DEFAULT, style);
        view.setIncludeFontPadding(true);
        return view;
    }

    private android.graphics.drawable.GradientDrawable round(int color, int radiusDp, int strokeColor, int strokeDp) {
        android.graphics.drawable.GradientDrawable drawable = new android.graphics.drawable.GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(radiusDp));
        if (strokeDp > 0) {
            drawable.setStroke(dp(strokeDp), strokeColor);
        }
        return drawable;
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private String currentChargeDuration() {
        long start = prefs.getLong(KEY_CHARGE_START_MS, 0);
        if (!prefs.getBoolean(KEY_WAS_CHARGING, false) || start == 0) {
            return "Şarjda değil";
        }
        return formatDuration((System.currentTimeMillis() - start) / 1000);
    }

    private String estimateFullTime(BatterySnapshot snapshot) {
        if (!snapshot.isCharging) {
            return "Şarjda değil";
        }
        if (snapshot.level >= 100) {
            return "Dolu";
        }
        long start = prefs.getLong(KEY_CHARGE_START_MS, 0);
        int startLevel = prefs.getInt(KEY_CHARGE_START_LEVEL, snapshot.level);
        long elapsed = System.currentTimeMillis() - start;
        int gained = snapshot.level - startLevel;
        if (start == 0 || gained <= 0 || elapsed < 180000) {
            return "Hesaplanıyor";
        }
        long secondsPerPercent = (elapsed / 1000) / Math.max(1, gained);
        return formatDuration(secondsPerPercent * (100 - snapshot.level));
    }

    static BatterySnapshot readBattery(Context context) {
        Intent intent = context.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        if (intent == null) {
            return new BatterySnapshot(0, 0f, false, false, 0,
                    BatteryManager.BATTERY_HEALTH_UNKNOWN, 0, "", Long.MIN_VALUE);
        }
        int level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, 0);
        int scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100);
        int percent = scale <= 0 ? level : Math.round(level * 100f / scale);
        int tempRaw = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0);
        float tempC = tempRaw / 10f;
        int status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, BatteryManager.BATTERY_STATUS_UNKNOWN);
        boolean charging = status == BatteryManager.BATTERY_STATUS_CHARGING
                || status == BatteryManager.BATTERY_STATUS_FULL;
        boolean full = status == BatteryManager.BATTERY_STATUS_FULL || percent >= 100;
        int plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0);
        int health = intent.getIntExtra(BatteryManager.EXTRA_HEALTH, BatteryManager.BATTERY_HEALTH_UNKNOWN);
        int voltageMv = intent.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0);
        String technology = intent.getStringExtra(BatteryManager.EXTRA_TECHNOLOGY);
        long currentUa = Long.MIN_VALUE;
        if (Build.VERSION.SDK_INT >= 21) {
            BatteryManager manager = (BatteryManager) context.getSystemService(Context.BATTERY_SERVICE);
            if (manager != null) {
                currentUa = manager.getLongProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW);
            }
        }
        return new BatterySnapshot(percent, tempC, charging, full, plugged, health, voltageMv, technology, currentUa);
    }

    static void recordSnapshot(Context context, BatterySnapshot snapshot, long now) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, MODE_PRIVATE);
        ensureToday(prefs);
        SharedPreferences.Editor edit = prefs.edit();
        long last = prefs.getLong(KEY_LAST_SAMPLE_MS, 0);
        long delta = 0;
        if (last > 0 && now > last) {
            delta = Math.min(60, (now - last) / 1000);
        }

        int samples = prefs.getInt(KEY_TEMP_SAMPLES, 0) + 1;
        float maxTemp = prefs.getFloat(KEY_MAX_TEMP, 0f);
        if (samples == 1 || snapshot.temperatureC > maxTemp) {
            maxTemp = snapshot.temperatureC;
        }
        edit.putInt(KEY_TEMP_SAMPLES, samples);
        edit.putFloat(KEY_TEMP_SUM, prefs.getFloat(KEY_TEMP_SUM, 0f) + snapshot.temperatureC);
        edit.putFloat(KEY_MAX_TEMP, maxTemp);

        boolean above40 = snapshot.temperatureC >= 40f;
        boolean wasAbove40 = prefs.getBoolean(KEY_WAS_ABOVE40, false);
        if (above40 && !wasAbove40) {
            edit.putInt(KEY_OVER40_EVENTS, prefs.getInt(KEY_OVER40_EVENTS, 0) + 1);
        }
        if (above40 && delta > 0) {
            edit.putLong(KEY_OVER40_SECONDS, prefs.getLong(KEY_OVER40_SECONDS, 0) + delta);
        }
        edit.putBoolean(KEY_WAS_ABOVE40, above40);

        boolean wasCharging = prefs.getBoolean(KEY_WAS_CHARGING, false);
        long chargeStart = prefs.getLong(KEY_CHARGE_START_MS, 0);
        if (snapshot.isCharging) {
            if (!wasCharging || chargeStart == 0) {
                chargeStart = now;
                edit.putLong(KEY_CHARGE_START_MS, chargeStart);
                edit.putInt(KEY_CHARGE_START_LEVEL, snapshot.level);
            }
            if (delta > 0) {
                edit.putLong(KEY_CHARGE_SECONDS, prefs.getLong(KEY_CHARGE_SECONDS, 0) + delta);
            }
            long currentSession = Math.max(0, (now - chargeStart) / 1000);
            edit.putLong(KEY_LONGEST_CHARGE, Math.max(prefs.getLong(KEY_LONGEST_CHARGE, 0), currentSession));
        }
        edit.putBoolean(KEY_WAS_CHARGING, snapshot.isCharging);
        edit.putLong(KEY_LAST_SAMPLE_MS, now);

        int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        String key = KEY_HOUR_MAX_PREFIX + hour;
        if (snapshot.temperatureC > prefs.getFloat(key, 0f)) {
            edit.putFloat(key, snapshot.temperatureC);
        }
        edit.apply();
    }

    static void ensureToday(SharedPreferences prefs) {
        String today = new SimpleDateFormat("yyyyMMdd", Locale.US).format(new Date());
        if (today.equals(prefs.getString(KEY_DAY, ""))) {
            return;
        }
        SharedPreferences.Editor edit = prefs.edit();
        edit.putString(KEY_DAY, today);
        edit.putFloat(KEY_MAX_TEMP, 0f);
        edit.putFloat(KEY_TEMP_SUM, 0f);
        edit.putInt(KEY_TEMP_SAMPLES, 0);
        edit.putInt(KEY_OVER40_EVENTS, 0);
        edit.putLong(KEY_OVER40_SECONDS, 0);
        edit.putLong(KEY_CHARGE_SECONDS, 0);
        edit.putLong(KEY_LONGEST_CHARGE, 0);
        edit.putLong(KEY_LAST_SAMPLE_MS, 0);
        edit.putBoolean(KEY_WAS_ABOVE40, false);
        edit.putBoolean(KEY_WAS_CHARGING, false);
        edit.putLong(KEY_CHARGE_START_MS, 0);
        edit.putInt(KEY_CHARGE_START_LEVEL, 0);
        edit.putLong(KEY_LAST_HEAT_ALERT_MS, 0);
        edit.putLong(KEY_LAST_HEALTH_ALERT_MS, 0);
        edit.putLong(KEY_LAST_FULL_ALERT_MS, 0);
        edit.putLong(KEY_LAST_LONG_ALERT_MS, 0);
        for (int i = 0; i < 24; i++) {
            edit.putFloat(KEY_HOUR_MAX_PREFIX + i, 0f);
        }
        edit.apply();
    }

    static DailyStats readStats(SharedPreferences prefs) {
        ensureToday(prefs);
        return new DailyStats(
                prefs.getFloat(KEY_MAX_TEMP, 0f),
                prefs.getFloat(KEY_TEMP_SUM, 0f),
                prefs.getInt(KEY_TEMP_SAMPLES, 0),
                prefs.getInt(KEY_OVER40_EVENTS, 0),
                prefs.getLong(KEY_OVER40_SECONDS, 0),
                prefs.getLong(KEY_CHARGE_SECONDS, 0),
                prefs.getLong(KEY_LONGEST_CHARGE, 0));
    }

    static int computeScore(DailyStats stats) {
        int penalty = 0;
        penalty += Math.min(28, (int) (stats.over40Seconds / 60) * 2);
        penalty += Math.min(18, stats.over40Events * 4);
        if (stats.maxTemp >= 43f) {
            penalty += 18;
        } else if (stats.maxTemp >= 40f) {
            penalty += 8;
        }
        long extraLong = Math.max(0, stats.longestChargeSeconds - 7200);
        penalty += Math.min(18, (int) (extraLong / 600));
        return Math.max(45, Math.min(100, 100 - penalty));
    }

    static int computeHealthScore(BatterySnapshot snapshot, DailyStats stats) {
        int score;
        switch (snapshot.health) {
            case BatteryManager.BATTERY_HEALTH_GOOD:
                score = 96;
                break;
            case BatteryManager.BATTERY_HEALTH_OVERHEAT:
                score = 58;
                break;
            case BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE:
                score = 55;
                break;
            case BatteryManager.BATTERY_HEALTH_DEAD:
                score = 35;
                break;
            case BatteryManager.BATTERY_HEALTH_COLD:
                score = 68;
                break;
            case BatteryManager.BATTERY_HEALTH_UNSPECIFIED_FAILURE:
                score = 50;
                break;
            default:
                score = 82;
                break;
        }

        if (stats.maxTemp >= 43f) {
            score -= 14;
        } else if (stats.maxTemp >= 40f) {
            score -= 7;
        }
        score -= Math.min(12, (int) (stats.over40Seconds / 300) * 2);
        score -= Math.min(8, Math.max(0, (int) ((stats.longestChargeSeconds - 7200) / 900)));
        if (snapshot.level <= 5 || (snapshot.isCharging && snapshot.level >= 100)) {
            score -= 3;
        }
        return Math.max(25, Math.min(100, score));
    }

    static int scoreColor(int score) {
        if (score >= 85) {
            return COLOR_TEAL;
        }
        if (score >= 70) {
            return COLOR_AMBER;
        }
        return COLOR_CORAL;
    }

    static int healthColor(int health) {
        switch (health) {
            case BatteryManager.BATTERY_HEALTH_GOOD:
                return COLOR_TEAL;
            case BatteryManager.BATTERY_HEALTH_UNKNOWN:
                return COLOR_AMBER;
            default:
                return COLOR_CORAL;
        }
    }

    static String healthText(int health) {
        switch (health) {
            case BatteryManager.BATTERY_HEALTH_GOOD:
                return "İyi";
            case BatteryManager.BATTERY_HEALTH_OVERHEAT:
                return "Aşırı sıcak";
            case BatteryManager.BATTERY_HEALTH_DEAD:
                return "Zayıf";
            case BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE:
                return "Yüksek voltaj";
            case BatteryManager.BATTERY_HEALTH_UNSPECIFIED_FAILURE:
                return "Hata";
            case BatteryManager.BATTERY_HEALTH_COLD:
                return "Soğuk";
            default:
                return "Bilinmiyor";
        }
    }

    static String healthAdvice(BatterySnapshot snapshot, DailyStats stats, int threshold) {
        if (snapshot.health == BatteryManager.BATTERY_HEALTH_OVERHEAT || snapshot.temperatureC >= 43f) {
            return "Şarjı çıkar";
        }
        if (snapshot.health == BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE) {
            return "Adaptörü değiştir";
        }
        if (snapshot.temperatureC >= threshold) {
            return "Serinlet";
        }
        if (stats.longestChargeSeconds >= 7200 && snapshot.isCharging) {
            return "Uzun şarj";
        }
        if (snapshot.level <= 15) {
            return "20%'ye yaklaş";
        }
        if (snapshot.level >= 90 && snapshot.isCharging) {
            return "Doluya yakın";
        }
        return "Normal";
    }

    static String riskText(float temp, int threshold) {
        if (temp >= 43f || temp >= threshold + 2) {
            return "Tehlikeli";
        }
        if (temp >= threshold) {
            return "Yüksek";
        }
        if (temp >= threshold - 2) {
            return "Orta";
        }
        return "Düşük";
    }

    static int riskColor(float temp, int threshold) {
        if (temp >= 43f || temp >= threshold + 2) {
            return COLOR_CORAL;
        }
        if (temp >= threshold - 2) {
            return COLOR_AMBER;
        }
        return COLOR_TEAL;
    }

    static String formatTemp(float temp) {
        return String.format(Locale.US, "%.1f°C", temp);
    }

    static String formatDuration(long totalSeconds) {
        if (totalSeconds <= 0) {
            return "0 dk";
        }
        long hours = totalSeconds / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        if (hours > 0 && minutes > 0) {
            return hours + " sa " + minutes + " dk";
        }
        if (hours > 0) {
            return hours + " sa";
        }
        return Math.max(1, minutes) + " dk";
    }

    static String formatVoltage(int millivolts) {
        if (millivolts <= 0) {
            return "--";
        }
        return String.format(Locale.US, "%.2f V", millivolts / 1000f);
    }

    static String formatCurrent(long microamps) {
        if (microamps == Long.MIN_VALUE || microamps == 0) {
            return "--";
        }
        long milliamps = Math.abs(microamps) / 1000;
        return milliamps + " mA";
    }

    static class BatterySnapshot {
        final int level;
        final float temperatureC;
        final boolean isCharging;
        final boolean isFull;
        final int plugged;
        final int health;
        final int voltageMv;
        final String technology;
        final long currentUa;

        BatterySnapshot(int level, float temperatureC, boolean isCharging, boolean isFull,
                        int plugged, int health, int voltageMv, String technology, long currentUa) {
            this.level = Math.max(0, Math.min(100, level));
            this.temperatureC = temperatureC;
            this.isCharging = isCharging;
            this.isFull = isFull;
            this.plugged = plugged;
            this.health = health;
            this.voltageMv = voltageMv;
            this.technology = technology == null || technology.length() == 0 ? "Bilinmiyor" : technology;
            this.currentUa = currentUa;
        }

        String healthText() {
            return MainActivity.healthText(health);
        }

        String technologyText() {
            return technology;
        }

        String statusText() {
            if (isFull) {
                return "Dolu";
            }
            if (!isCharging) {
                return "Şarjda değil";
            }
            String speed = "";
            if (currentUa != Long.MIN_VALUE && Math.abs(currentUa) >= 1500000L) {
                speed = "Hızlı ";
            }
            String source = "";
            if ((plugged & BatteryManager.BATTERY_PLUGGED_AC) != 0) {
                source = "AC";
            } else if ((plugged & BatteryManager.BATTERY_PLUGGED_USB) != 0) {
                source = "USB";
            } else if (Build.VERSION.SDK_INT >= 17
                    && (plugged & BatteryManager.BATTERY_PLUGGED_WIRELESS) != 0) {
                source = "Kablosuz";
            }
            return speed + "Şarj oluyor" + (source.length() > 0 ? " (" + source + ")" : "");
        }
    }

    static class DailyStats {
        final float maxTemp;
        final float tempSum;
        final int samples;
        final int over40Events;
        final long over40Seconds;
        final long chargeSeconds;
        final long longestChargeSeconds;

        DailyStats(float maxTemp, float tempSum, int samples, int over40Events,
                   long over40Seconds, long chargeSeconds, long longestChargeSeconds) {
            this.maxTemp = maxTemp;
            this.tempSum = tempSum;
            this.samples = samples;
            this.over40Events = over40Events;
            this.over40Seconds = over40Seconds;
            this.chargeSeconds = chargeSeconds;
            this.longestChargeSeconds = longestChargeSeconds;
        }

        float averageTemp() {
            return samples <= 0 ? 0f : tempSum / samples;
        }
    }

    static class HeatGaugeView extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF arc = new RectF();
        private float temp = 0f;
        private int threshold = DEFAULT_THRESHOLD;
        private boolean charging = false;

        HeatGaugeView(Context context) {
            super(context);
            setLayerType(View.LAYER_TYPE_SOFTWARE, null);
        }

        void setData(float temp, int threshold, boolean charging) {
            this.temp = temp;
            this.threshold = threshold;
            this.charging = charging;
            invalidate();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            int w = getWidth();
            int h = getHeight();
            float density = getResources().getDisplayMetrics().density;
            float radius = 18 * density;

            paint.setColor(COLOR_INK);
            paint.setShadowLayer(14 * density, 0, 6 * density, Color.argb(35, 0, 0, 0));
            RectF background = new RectF(0, 8 * density, w, h - 10 * density);
            canvas.drawRoundRect(background, radius, radius, paint);
            paint.clearShadowLayer();

            float cx = w / 2f;
            float cy = h * 0.57f;
            float size = Math.min(w * 0.76f, h * 1.05f);
            arc.set(cx - size / 2f, cy - size / 2f, cx + size / 2f, cy + size / 2f);

            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setStrokeWidth(16 * density);
            paint.setColor(Color.argb(55, 255, 255, 255));
            canvas.drawArc(arc, 155, 230, false, paint);

            float clamped = Math.max(20f, Math.min(46f, temp));
            float sweep = (clamped - 20f) / 26f * 230f;
            paint.setColor(riskColor(temp, threshold));
            canvas.drawArc(arc, 155, sweep, false, paint);
            paint.setStyle(Paint.Style.FILL);

            paint.setTextAlign(Paint.Align.CENTER);
            paint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
            paint.setColor(Color.WHITE);
            paint.setTextSize(16 * density);
            canvas.drawText("Pil Sıcaklığı", cx, 45 * density, paint);
            paint.setTextSize(50 * density);
            canvas.drawText(formatTemp(temp), cx, h * 0.52f, paint);

            paint.setTypeface(Typeface.DEFAULT);
            paint.setTextSize(14 * density);
            paint.setColor(Color.argb(220, 255, 255, 255));
            canvas.drawText("Risk: " + riskText(temp, threshold), cx, h * 0.66f, paint);
            canvas.drawText(charging ? "Şarj takibi hazır" : "Şarjda değil", cx, h * 0.78f, paint);
        }
    }

    static class ReportGraphView extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private SharedPreferences prefs;

        ReportGraphView(Context context) {
            super(context);
            prefs = context.getSharedPreferences(PREFS, MODE_PRIVATE);
        }

        void setPrefs(SharedPreferences prefs) {
            this.prefs = prefs;
            invalidate();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float density = getResources().getDisplayMetrics().density;
            float radius = 8 * density;
            RectF card = new RectF(0, 0, getWidth(), getHeight());
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(COLOR_CARD);
            canvas.drawRoundRect(card, radius, radius, paint);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(density);
            paint.setColor(Color.rgb(223, 230, 234));
            canvas.drawRoundRect(card, radius, radius, paint);
            paint.setStyle(Paint.Style.FILL);

            paint.setTextAlign(Paint.Align.LEFT);
            paint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
            paint.setTextSize(15 * density);
            paint.setColor(COLOR_INK);
            canvas.drawText("Saatlik Sıcaklık", 16 * density, 28 * density, paint);

            float left = 16 * density;
            float right = getWidth() - 16 * density;
            float top = 48 * density;
            float bottom = getHeight() - 26 * density;
            float gap = 3 * density;
            float barWidth = Math.max(3 * density, (right - left - gap * 23) / 24f);
            for (int i = 0; i < 24; i++) {
                float value = prefs.getFloat(KEY_HOUR_MAX_PREFIX + i, 0f);
                float normalized = value <= 0 ? 0f : Math.max(0.08f, Math.min(1f, (value - 24f) / 20f));
                float barHeight = normalized * (bottom - top);
                int color = value >= 40f ? COLOR_CORAL : (value >= 38f ? COLOR_AMBER : COLOR_TEAL);
                paint.setColor(value <= 0 ? Color.rgb(230, 236, 239) : color);
                float x = left + i * (barWidth + gap);
                canvas.drawRoundRect(new RectF(x, bottom - barHeight, x + barWidth, bottom),
                        3 * density, 3 * density, paint);
            }

            paint.setTypeface(Typeface.DEFAULT);
            paint.setTextSize(11 * density);
            paint.setColor(COLOR_MUTED);
            canvas.drawText("00", left, getHeight() - 8 * density, paint);
            paint.setTextAlign(Paint.Align.CENTER);
            canvas.drawText("12", (left + right) / 2f, getHeight() - 8 * density, paint);
            paint.setTextAlign(Paint.Align.RIGHT);
            canvas.drawText("23", right, getHeight() - 8 * density, paint);
        }
    }
}

class BatteryMonitorServiceBase extends Service {
    private static final int MONITOR_NOTIFICATION_ID = 7101;
    private static final int ALERT_NOTIFICATION_ID = 7102;
    private static final String MONITOR_CHANNEL = "chargeguard_monitor";
    private static final String ACTION_STOP = "com.codex.chargeguard.STOP";
    private final Handler handler = new Handler(Looper.getMainLooper());
    private SharedPreferences prefs;
    private Runnable loop;

    @Override
    public void onCreate() {
        super.onCreate();
        prefs = getSharedPreferences(MainActivity.PREFS, MODE_PRIVATE);
        createMonitorChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_STOP.equals(intent.getAction())) {
            stopSelf();
            return START_NOT_STICKY;
        }
        prefs.edit().putBoolean(MainActivity.KEY_MONITORING, true).apply();
        MainActivity.BatterySnapshot snapshot = MainActivity.readBattery(this);
        startForeground(MONITOR_NOTIFICATION_ID, buildMonitorNotification(snapshot));
        startLoop();
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        prefs.edit().putBoolean(MainActivity.KEY_MONITORING, false).apply();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void startLoop() {
        if (loop != null) {
            return;
        }
        loop = new Runnable() {
            @Override
            public void run() {
                checkBattery();
                handler.postDelayed(this, 15000);
            }
        };
        loop.run();
    }

    private void checkBattery() {
        MainActivity.BatterySnapshot snapshot = MainActivity.readBattery(this);
        long now = System.currentTimeMillis();
        MainActivity.recordSnapshot(this, snapshot, now);
        NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (manager != null) {
            manager.notify(MONITOR_NOTIFICATION_ID, buildMonitorNotification(snapshot));
        }

        int threshold = prefs.getInt(MainActivity.KEY_THRESHOLD, MainActivity.DEFAULT_THRESHOLD);
        if (snapshot.isCharging && snapshot.temperatureC >= threshold) {
            long last = prefs.getLong(MainActivity.KEY_LAST_HEAT_ALERT_MS, 0);
            if (now - last > 180000) {
                prefs.edit().putLong(MainActivity.KEY_LAST_HEAT_ALERT_MS, now).apply();
                sendAlert("Şarj ısınıyor", MainActivity.formatTemp(snapshot.temperatureC)
                        + " ölçüldü. Şarj kablosunu çıkarmayı veya telefonu serinletmeyi düşün.");
            }
        }

        if (snapshot.health != BatteryManager.BATTERY_HEALTH_GOOD
                && snapshot.health != BatteryManager.BATTERY_HEALTH_UNKNOWN) {
            long last = prefs.getLong(MainActivity.KEY_LAST_HEALTH_ALERT_MS, 0);
            if (now - last > 300000) {
                prefs.edit().putLong(MainActivity.KEY_LAST_HEALTH_ALERT_MS, now).apply();
                sendAlert("Pil sağlığı uyarısı", "Sistem durumu: "
                        + snapshot.healthText() + ". " + MainActivity.healthAdvice(snapshot,
                        MainActivity.readStats(prefs), threshold) + ".");
            }
        }

        if (snapshot.isCharging && snapshot.isFull
                && prefs.getBoolean(MainActivity.KEY_FULL_ALERT, true)) {
            long last = prefs.getLong(MainActivity.KEY_LAST_FULL_ALERT_MS, 0);
            if (now - last > 3600000) {
                prefs.edit().putLong(MainActivity.KEY_LAST_FULL_ALERT_MS, now).apply();
                sendAlert("Pil %100 oldu", "Telefon doldu. Uzun süre %100'de bırakmamak pil alışkanlığı skorunu korur.");
            }
        }

        if (snapshot.isCharging && prefs.getBoolean(MainActivity.KEY_NIGHT_MODE, true)) {
            long start = prefs.getLong(MainActivity.KEY_CHARGE_START_MS, 0);
            long sessionSeconds = start == 0 ? 0 : (now - start) / 1000;
            int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
            boolean night = hour >= 22 || hour <= 7;
            long last = prefs.getLong(MainActivity.KEY_LAST_LONG_ALERT_MS, 0);
            if (night && sessionSeconds >= 7200 && now - last > 3600000) {
                prefs.edit().putLong(MainActivity.KEY_LAST_LONG_ALERT_MS, now).apply();
                sendAlert("Gece şarjı uzadı", "Telefon " + MainActivity.formatDuration(sessionSeconds)
                        + " şarjda kaldı. İstersen şarjı çıkar.");
            }
        }
    }

    private Notification buildMonitorNotification(MainActivity.BatterySnapshot snapshot) {
        Intent openIntent = new Intent(this, MainActivity.class);
        PendingIntent openPendingIntent = PendingIntent.getActivity(this, 0, openIntent, pendingFlags());

        Intent stopIntent = new Intent(this, BatteryMonitorService.class);
        stopIntent.setAction(ACTION_STOP);
        PendingIntent stopPendingIntent = PendingIntent.getService(this, 1, stopIntent, pendingFlags());

        String content = MainActivity.formatTemp(snapshot.temperatureC) + " • "
                + snapshot.level + "% • Sağlık: " + snapshot.healthText();

        Notification.Builder builder = notificationBuilder(MONITOR_CHANNEL)
                .setSmallIcon(android.R.drawable.ic_lock_idle_charging)
                .setContentTitle("Şarj takibi açık")
                .setContentText(content)
                .setContentIntent(openPendingIntent)
                .setOngoing(true)
                .setShowWhen(false)
                .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Durdur", stopPendingIntent);
        if (Build.VERSION.SDK_INT < 26) {
            builder.setPriority(Notification.PRIORITY_LOW);
        }
        return builder.build();
    }

    private void sendAlert(String title, String text) {
        boolean sound = prefs.getBoolean(MainActivity.KEY_SOUND, true);
        boolean vibration = prefs.getBoolean(MainActivity.KEY_VIBRATION, true);
        String channel = alertChannelId(sound, vibration);
        createAlertChannel(channel, sound, vibration);

        Intent openIntent = new Intent(this, MainActivity.class);
        PendingIntent openPendingIntent = PendingIntent.getActivity(this, 2, openIntent, pendingFlags());
        Notification.Builder builder = notificationBuilder(channel)
                .setSmallIcon(android.R.drawable.stat_sys_warning)
                .setContentTitle(title)
                .setContentText(text)
                .setStyle(new Notification.BigTextStyle().bigText(text))
                .setContentIntent(openPendingIntent)
                .setAutoCancel(true)
                .setCategory(Notification.CATEGORY_ALARM)
                .setShowWhen(true);
        if (Build.VERSION.SDK_INT < 26) {
            int defaults = 0;
            if (sound) {
                defaults |= Notification.DEFAULT_SOUND;
            }
            if (vibration) {
                defaults |= Notification.DEFAULT_VIBRATE;
            }
            builder.setDefaults(defaults);
            builder.setPriority(Notification.PRIORITY_HIGH);
        }

        NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (manager != null) {
            manager.notify(ALERT_NOTIFICATION_ID, builder.build());
        }
        if (vibration) {
            vibrate();
        }
    }

    private Notification.Builder notificationBuilder(String channelId) {
        if (Build.VERSION.SDK_INT >= 26) {
            return new Notification.Builder(this, channelId);
        }
        return new Notification.Builder(this);
    }

    private void createMonitorChannel() {
        if (Build.VERSION.SDK_INT < 26) {
            return;
        }
        NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (manager == null || manager.getNotificationChannel(MONITOR_CHANNEL) != null) {
            return;
        }
        NotificationChannel channel = new NotificationChannel(
                MONITOR_CHANNEL,
                "Şarj Takibi",
                NotificationManager.IMPORTANCE_LOW);
        channel.setDescription("Sıcaklık ve şarj durumunu izler.");
        manager.createNotificationChannel(channel);
    }

    private void createAlertChannel(String id, boolean sound, boolean vibration) {
        if (Build.VERSION.SDK_INT < 26) {
            return;
        }
        NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (manager == null || manager.getNotificationChannel(id) != null) {
            return;
        }
        NotificationChannel channel = new NotificationChannel(
                id,
                "Şarj Alarmı",
                NotificationManager.IMPORTANCE_HIGH);
        channel.enableVibration(vibration);
        if (!sound) {
            channel.setSound(null, null);
        }
        manager.createNotificationChannel(channel);
    }

    private String alertChannelId(boolean sound, boolean vibration) {
        return "chargeguard_alert_" + (sound ? "sound" : "silent") + "_" + (vibration ? "vibe" : "still");
    }

    private int pendingFlags() {
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= 23) {
            flags |= PendingIntent.FLAG_IMMUTABLE;
        }
        return flags;
    }

    private void vibrate() {
        Vibrator vibrator = (Vibrator) getSystemService(VIBRATOR_SERVICE);
        if (vibrator == null || !vibrator.hasVibrator()) {
            return;
        }
        if (Build.VERSION.SDK_INT >= 26) {
            vibrator.vibrate(VibrationEffect.createWaveform(new long[]{0, 350, 180, 350}, -1));
        } else {
            vibrator.vibrate(new long[]{0, 350, 180, 350}, -1);
        }
    }
}
