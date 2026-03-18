package org.booklore.crons;

import jakarta.annotation.PostConstruct;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.booklore.config.AppProperties;
import org.booklore.model.dto.BookloreTelemetry;
import org.booklore.model.dto.InstallationPing;
import org.booklore.model.dto.settings.AppSettings;
import org.booklore.service.TelemetryService;
import org.booklore.service.appsettings.AppSettingService;
import org.springframework.boot.sql.init.dependency.DependsOnDatabaseInitialization;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.TimeUnit;

@Service
@AllArgsConstructor
@DependsOnDatabaseInitialization
@Slf4j
public class CronService {

    private static final String LAST_TELEMETRY_KEY = "last_telemetry_sent";
    private static final String LAST_PING_KEY = "last_ping_sent";
    private static final String LAST_PING_APP_VERSION_KEY = "last_ping_app_version";
    private static final long INTERVAL_HOURS = 24;

    private final AppProperties appProperties;
    private final TelemetryService telemetryService;
    private final RestClient restClient;
    private final AppSettingService appSettingService;

    @PostConstruct
    public void initScheduledTasks() {
        checkAndRunTelemetry();
        checkAndRunPing();
    }

    @Scheduled(fixedDelay = 24, timeUnit = TimeUnit.HOURS, initialDelay = 24)
    public void sendTelemetryData() {
        if (isTelemetryEnabled()) {
            String url = appProperties.getTelemetry().getBaseUrl() + "/api/v1/ingest";
            BookloreTelemetry telemetry = telemetryService.collectTelemetry();
            if (postData(url, telemetry)) {
                appSettingService.saveSetting(LAST_TELEMETRY_KEY, Instant.now().toString());
            }
        }
    }

    @Scheduled(fixedDelay = 24, timeUnit = TimeUnit.HOURS, initialDelay = 12)
    public void sendPing() {
        if (isTelemetryEnabled()) {
            String url = appProperties.getTelemetry().getBaseUrl() + "/api/v1/heartbeat";
            InstallationPing ping = telemetryService.getInstallationPing();
            if (ping != null && postData(url, ping)) {
                appSettingService.saveSetting(LAST_PING_KEY, Instant.now().toString());
                appSettingService.saveSetting(LAST_PING_APP_VERSION_KEY, ping.getAppVersion());
            }
        }
    }

    protected boolean postData(String url, Object body) {
        try {
            restClient.post()
                    .uri(url)
                    .body(body)
                    .retrieve()
                    .body(String.class);
            return true;
        } catch (Exception ex) {
            log.debug("POST request to URL: {}, Message: {}", url, ex.getMessage());
            return false;
        }
    }

    private boolean isTelemetryEnabled() {
        AppSettings settings = appSettingService.getAppSettings();
        return settings != null && settings.isTelemetryEnabled();
    }

    private void checkAndRunTelemetry() {
        AppSettings settings = appSettingService.getAppSettings();
        if (settings == null || !settings.isTelemetryEnabled()) {
            return;
        }
        String lastRunStr = appSettingService.getSettingValue(LAST_TELEMETRY_KEY);
        if (shouldRunTask(lastRunStr)) {
            log.info("Running stats on startup (last run: {})", lastRunStr);
            sendTelemetryData();
        }
    }

    private void checkAndRunPing() {
        String lastRunStr = appSettingService.getSettingValue(LAST_PING_KEY);
        if (hasAppVersionChanged()) {
            log.info("App version changed, sending immediate ping");
            sendPing();
            return;
        }
        if (shouldRunTask(lastRunStr)) {
            log.info("Running ping on startup (last run: {})", lastRunStr);
            sendPing();
        }
    }

    /**
     * Determines if a task should run immediately on startup.
     * Returns false for new installations (no last run recorded) to follow normal schedule.
     * Returns true if more than INTERVAL_HOURS have passed since the last run,
     * preventing data gaps when the server restarts close to scheduled execution time.
     * <p>
     * Example: Telemetry normally runs at 2:00 AM daily. If the server restarts at 1:55 AM,
     * the scheduled task would reset and not run until 2:00 AM the next day (48 hours later).
     * This method checks if 24+ hours have passed since the last run and executes immediately
     * on startup if needed, ensuring data is sent at 1:55 AM instead of waiting another 24 hours.
     */
    private boolean shouldRunTask(String lastRunStr) {
        if (lastRunStr == null || lastRunStr.isEmpty()) {
            return false;
        }
        try {
            Instant lastRun = Instant.parse(lastRunStr);
            Instant threshold = Instant.now().minus(INTERVAL_HOURS, ChronoUnit.HOURS);
            return lastRun.isBefore(threshold);
        } catch (Exception e) {
            log.warn("Failed to parse last run timestamp: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Checks if the app version has changed since the last ping.
     * Returns true if this is an established installation with a version change.
     */
    private boolean hasAppVersionChanged() {
        String lastPingVersion = appSettingService.getSettingValue(LAST_PING_APP_VERSION_KEY);
        InstallationPing ping = telemetryService.getInstallationPing();
        String currentVersion = ping != null ? ping.getAppVersion() : null;
        if (lastPingVersion == null || lastPingVersion.isEmpty() || currentVersion == null) {
            return false;
        }
        return !lastPingVersion.equals(currentVersion);
    }
}
