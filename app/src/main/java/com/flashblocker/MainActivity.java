package com.flashblocker;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.MenuItem;
import android.view.View;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;

import java.util.concurrent.TimeUnit;

/**
 * Main screen of the app.
 * Shows the AccessibilityService status, statistics and block history.
 *
 * The app uses only the AccessibilityService to close the Flash SMS (Class 0)
 * popup. It does NOT need to be the default SMS app and does NOT read/send/store SMS.
 */
public class MainActivity extends Activity {
    private static final int MENU_ENABLE = 1;
    private static final int MENU_LEARNING = 2;
    private static final int MENU_ABOUT = 3;
    private static final int MENU_PAUSE = 4;
    private static final int REQ_NOTIFICATION_PERMISSION = 1;

    private TextView statusAccessibility;
    private TextView statNumber;
    private TextView statsLastBlocked;
    private TextView logView;
    private BlockStats stats;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        stats = new BlockStats(getSharedPreferences(BlockStats.PREFS_NAME, MODE_PRIVATE));

        statusAccessibility = findViewById(R.id.status_accessibility);
        statNumber = findViewById(R.id.stat_number);
        statsLastBlocked = findViewById(R.id.stats_last_blocked);
        logView = findViewById(R.id.log_view);

        // ⋮ overflow menu in the header: app actions.
        findViewById(R.id.btn_overflow).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showOverflowMenu(v);
            }
        });

        // Tapping the status card goes straight to the accessibility settings.
        statusAccessibility.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openAccessibilitySettings();
            }
        });

        // "Clear" in the history header.
        findViewById(R.id.btn_clear_log).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                stats.clear();
                refreshStats();
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshStats();
    }

    private void showOverflowMenu(View anchor) {
        PopupMenu popup = new PopupMenu(this, anchor);
        if (isAccessibilityEnabled()) {
            // Real disable via disableSelf() — needed because bank apps refuse
            // to run while any unknown accessibility service is enabled.
            popup.getMenu().add(0, MENU_PAUSE, 0, "Pausar por 15 min (apps de banco)");
        } else {
            popup.getMenu().add(0, MENU_ENABLE, 0, "Ativar acessibilidade");
        }
        MenuItem learn = popup.getMenu().add(0, MENU_LEARNING, 1, "Modo aprendizado");
        learn.setCheckable(true);
        learn.setChecked(stats.isLearningMode());
        popup.getMenu().add(0, MENU_ABOUT, 2, "Sobre o app");
        popup.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(MenuItem item) {
                switch (item.getItemId()) {
                    case MENU_ENABLE:
                        openAccessibilitySettings();
                        return true;
                    case MENU_PAUSE:
                        pauseBlocking();
                        return true;
                    case MENU_LEARNING:
                        toggleLearningMode();
                        return true;
                    case MENU_ABOUT:
                        showAbout();
                        return true;
                    default:
                        return false;
                }
            }
        });
        popup.show();
    }

    private void pauseBlocking() {
        requestNotificationPermissionIfNeeded();
        boolean ok = FlashSmsAccessibilityService.pause();
        Toast.makeText(this, ok
            ? "Bloqueio pausado por 15 min — o serviço de acessibilidade foi "
              + "desativado. Ao acabar o tempo você recebe uma notificação "
              + "para reativar (Android não permite reativar sozinho)."
            : "O serviço não está conectado — nada para pausar.",
            Toast.LENGTH_LONG).show();
        refreshStats();
    }

    /**
     * Android 13+ requires runtime consent to post notifications. Without it
     * the pause still works, it just won't be able to remind the user when
     * the 15 min are up.
     */
    private void requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return;
        if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED) return;
        requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},
            REQ_NOTIFICATION_PERMISSION);
    }

    private void toggleLearningMode() {
        boolean on = !stats.isLearningMode();
        stats.setLearningMode(on);
        Toast.makeText(this, on
            ? "Modo aprendizado LIGADO — o app NÃO bloqueia; só registra as janelas "
              + "no histórico. Desligue depois de capturar."
            : "Modo aprendizado desligado — bloqueio normal.",
            Toast.LENGTH_LONG).show();
        refreshStats();
    }

    private void showAbout() {
        new AlertDialog.Builder(this)
            .setIcon(R.drawable.ic_header_logo)
            .setTitle("Flash SMS Blocker")
            .setMessage("Fecha automaticamente os pop-ups de SMS Flash (Class 0) — "
                + "aquelas mensagens que aparecem na tela inteira.\n\n"
                + "Usa apenas o servico de Acessibilidade. Nao le, nao envia e nao "
                + "armazena nenhum SMS.")
            .setPositiveButton("OK", null)
            .show();
    }

    private void openAccessibilitySettings() {
        startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
    }

    private void refreshStats() {
        if (stats.isLearningMode()) {
            statusAccessibility.setText("⚙  Modo aprendizado ativo — NÃO está bloqueando");
            statusAccessibility.setTextColor(getColor(R.color.warning));
            statusAccessibility.setBackgroundResource(R.drawable.status_warning_bg);
        } else if (stats.isPaused() && !isAccessibilityEnabled()) {
            statusAccessibility.setText(pauseStatusText());
            statusAccessibility.setTextColor(getColor(R.color.warning));
            statusAccessibility.setBackgroundResource(R.drawable.status_warning_bg);
        } else if (isAccessibilityEnabled()) {
            statusAccessibility.setText("✓  Proteção ativa");
            statusAccessibility.setTextColor(getColor(R.color.success));
            statusAccessibility.setBackgroundResource(R.drawable.status_active_bg);
        } else {
            statusAccessibility.setText("✕  Proteção inativa — toque aqui para ativar acessibilidade");
            statusAccessibility.setTextColor(getColor(R.color.danger));
            statusAccessibility.setBackgroundResource(R.drawable.status_inactive_bg);
        }

        statNumber.setText(String.valueOf(stats.getBlockedCount()));
        statsLastBlocked.setText("Último bloqueio: " + stats.getLastBlockedTime());
        logView.setText(stats.getLog());
    }

    /** Status text while a timed pause is active (or has just run out). */
    private String pauseStatusText() {
        long remainingMs = stats.getPauseUntil() - System.currentTimeMillis();
        if (remainingMs <= 0) {
            return "⏸  Pausa encerrada — toque aqui para reativar";
        }
        long minutes = TimeUnit.MILLISECONDS.toMinutes(remainingMs) + 1;
        return "⏸  Bloqueio pausado — reativa em ~" + minutes
            + " min (ou toque aqui antes)";
    }

    /** Checks whether this app's AccessibilityService is active. */
    private boolean isAccessibilityEnabled() {
        String serviceName = FlashSmsAccessibilityService.class.getName();
        String flat = Settings.Secure.getString(
            getContentResolver(),
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        if (flat == null || flat.isEmpty()) return false;
        return flat.contains(getPackageName() + "/" + serviceName);
    }
}
