package org.booklore.crons;

import org.booklore.config.AppProperties;
import org.booklore.model.dto.BookloreTelemetry;
import org.booklore.model.dto.InstallationPing;
import org.booklore.model.dto.settings.AppSettings;
import org.booklore.service.TelemetryService;
import org.booklore.service.appsettings.AppSettingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

class CronServiceTest {

    private AppProperties appProperties;
    private TelemetryService telemetryService;
    private RestClient restClient;
    private AppSettingService appSettingService;
    private CronService cronService;

    @BeforeEach
    void setUp() {
        appProperties = mock(AppProperties.class, RETURNS_DEEP_STUBS);
        telemetryService = mock(TelemetryService.class);
        restClient = mock(RestClient.class, RETURNS_DEEP_STUBS);
        appSettingService = mock(AppSettingService.class);
        cronService = new CronService(appProperties, telemetryService, restClient, appSettingService);

        AppSettings defaultSettings = mock(AppSettings.class);
        when(appSettingService.getAppSettings()).thenReturn(defaultSettings);
        when(defaultSettings.isTelemetryEnabled()).thenReturn(true);

        InstallationPing defaultPing = InstallationPing.builder().appVersion("1.0.0").build();
        when(telemetryService.getInstallationPing()).thenReturn(defaultPing);
    }

    @Test
    void sendTelemetryData_telemetryDisabled_doesNotSend() {
        AppSettings settings = mock(AppSettings.class);
        when(appSettingService.getAppSettings()).thenReturn(settings);
        when(settings.isTelemetryEnabled()).thenReturn(false);
        cronService.sendTelemetryData();
        verifyNoInteractions(telemetryService);
        verify(appSettingService, never()).saveSetting(anyString(), anyString());
    }

    @Test
    void sendTelemetryData_telemetryEnabled_postSuccess_savesSetting() {
        AppSettings settings = mock(AppSettings.class);
        when(appSettingService.getAppSettings()).thenReturn(settings);
        when(settings.isTelemetryEnabled()).thenReturn(true);
        when(appProperties.getTelemetry().getBaseUrl()).thenReturn("http://telemetry");
        BookloreTelemetry telemetry = mock(BookloreTelemetry.class);
        when(telemetryService.collectTelemetry()).thenReturn(telemetry);
        RestClient.RequestBodyUriSpec post = mock(RestClient.RequestBodyUriSpec.class, RETURNS_DEEP_STUBS);
        when(restClient.post()).thenReturn(post);
        when(post.uri(anyString())).thenReturn(post);
        when(post.body(any())).thenReturn(post);
        when(post.retrieve().body(String.class)).thenReturn("ok");
        cronService.sendTelemetryData();
        verify(appSettingService).saveSetting(eq("last_telemetry_sent"), anyString());
    }

    @Test
    void sendTelemetryData_telemetryEnabled_postFails_doesNotSaveSetting() {
        AppSettings settings = mock(AppSettings.class);
        when(appSettingService.getAppSettings()).thenReturn(settings);
        when(settings.isTelemetryEnabled()).thenReturn(true);
        when(appProperties.getTelemetry().getBaseUrl()).thenReturn("http://telemetry");
        BookloreTelemetry telemetry = mock(BookloreTelemetry.class);
        when(telemetryService.collectTelemetry()).thenReturn(telemetry);

        CronService spy = spy(cronService);
        doReturn(false).when(spy).postData(anyString(), any());

        spy.sendTelemetryData();
        verify(appSettingService, never()).saveSetting(eq("last_telemetry_sent"), anyString());
    }

    @Test
    void sendPing_telemetryDisabled_doesNotSend() {
        AppSettings settings = mock(AppSettings.class);
        when(appSettingService.getAppSettings()).thenReturn(settings);
        when(settings.isTelemetryEnabled()).thenReturn(false);
        cronService.sendPing();
        verifyNoInteractions(restClient);
    }

    @Test
    void sendPing_telemetryEnabled_postSuccess_savesSettings() {
        AppSettings settings = mock(AppSettings.class);
        when(appSettingService.getAppSettings()).thenReturn(settings);
        when(settings.isTelemetryEnabled()).thenReturn(true);
        when(appProperties.getTelemetry().getBaseUrl()).thenReturn("http://telemetry");
        InstallationPing ping = InstallationPing.builder().appVersion("1.0.0").build();
        when(telemetryService.getInstallationPing()).thenReturn(ping);
        RestClient.RequestBodyUriSpec post = mock(RestClient.RequestBodyUriSpec.class, RETURNS_DEEP_STUBS);
        when(restClient.post()).thenReturn(post);
        when(post.uri(anyString())).thenReturn(post);
        when(post.body(any())).thenReturn(post);
        when(post.retrieve().body(String.class)).thenReturn("ok");
        cronService.sendPing();
        verify(appSettingService).saveSetting(eq("last_ping_sent"), anyString());
        verify(appSettingService).saveSetting(eq("last_ping_app_version"), eq("1.0.0"));
    }

    @Test
    void sendPing_telemetryEnabled_postFails_doesNotSaveSettings() {
        AppSettings settings = mock(AppSettings.class);
        when(appSettingService.getAppSettings()).thenReturn(settings);
        when(settings.isTelemetryEnabled()).thenReturn(true);
        when(appProperties.getTelemetry().getBaseUrl()).thenReturn("http://telemetry");
        InstallationPing ping = InstallationPing.builder().appVersion("1.0.0").build();
        when(telemetryService.getInstallationPing()).thenReturn(ping);

        CronService spy = spy(cronService);
        doReturn(false).when(spy).postData(anyString(), any());

        spy.sendPing();
        verify(appSettingService, never()).saveSetting(eq("last_ping_sent"), anyString());
        verify(appSettingService, never()).saveSetting(eq("last_ping_app_version"), anyString());
    }

    @Test
    void shouldRunTask_nullOrEmpty_returnsFalse() {
        assertFalse(cronServiceShouldRunTask(null));
        assertFalse(cronServiceShouldRunTask(""));
    }

    @Test
    void shouldRunTask_invalidTimestamp_returnsFalse() {
        assertFalse(cronServiceShouldRunTask("not-a-timestamp"));
    }

    @Test
    void shouldRunTask_recentTimestamp_returnsFalse() {
        String now = Instant.now().toString();
        assertFalse(cronServiceShouldRunTask(now));
    }

    @Test
    void shouldRunTask_oldTimestamp_returnsTrue() {
        String old = Instant.now().minusSeconds(60 * 60 * 25).toString();
        assertTrue(cronServiceShouldRunTask(old));
    }

    boolean cronServiceShouldRunTask(String lastRunStr) {
        return invokeShouldRunTask(cronService, lastRunStr);
    }

    boolean invokeShouldRunTask(CronService cronService, String lastRunStr) {
        try {
            var m = CronService.class.getDeclaredMethod("shouldRunTask", String.class);
            m.setAccessible(true);
            return (boolean) m.invoke(cronService, lastRunStr);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void hasAppVersionChanged_noLastPing_returnsFalse() {
        when(appSettingService.getSettingValue("last_ping_app_version")).thenReturn(null);
        InstallationPing ping = InstallationPing.builder().appVersion("1.0.0").build();
        when(telemetryService.getInstallationPing()).thenReturn(ping);
        assertFalse(invokeHasAppVersionChanged(cronService));
    }

    @Test
    void hasAppVersionChanged_lastPingEmpty_returnsFalse() {
        when(appSettingService.getSettingValue("last_ping_app_version")).thenReturn("");
        InstallationPing ping = InstallationPing.builder().appVersion("1.0.0").build();
        when(telemetryService.getInstallationPing()).thenReturn(ping);
        assertFalse(invokeHasAppVersionChanged(cronService));
    }

    @Test
    void hasAppVersionChanged_sameVersion_returnsFalse() {
        when(appSettingService.getSettingValue("last_ping_app_version")).thenReturn("1.0.0");
        InstallationPing ping = InstallationPing.builder().appVersion("1.0.0").build();
        when(telemetryService.getInstallationPing()).thenReturn(ping);
        assertFalse(invokeHasAppVersionChanged(cronService));
    }

    @Test
    void hasAppVersionChanged_differentVersion_returnsTrue() {
        when(appSettingService.getSettingValue("last_ping_app_version")).thenReturn("1.0.0");
        InstallationPing ping = InstallationPing.builder().appVersion("2.0.0").build();
        when(telemetryService.getInstallationPing()).thenReturn(ping);
        assertTrue(invokeHasAppVersionChanged(cronService));
    }

    @Test
    void hasAppVersionChanged_nullPing_returnsFalse() {
        when(appSettingService.getSettingValue("last_ping_app_version")).thenReturn("1.0.0");
        when(telemetryService.getInstallationPing()).thenReturn(null);
        assertFalse(invokeHasAppVersionChanged(cronService));
    }

    boolean invokeHasAppVersionChanged(CronService cronService) {
        try {
            var m = CronService.class.getDeclaredMethod("hasAppVersionChanged");
            m.setAccessible(true);
            return (boolean) m.invoke(cronService);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void checkAndRunTelemetry_telemetryDisabled_doesNothing() {
        AppSettings settings = mock(AppSettings.class);
        when(appSettingService.getAppSettings()).thenReturn(settings);
        when(settings.isTelemetryEnabled()).thenReturn(false);
        cronService.initScheduledTasks();
        verify(appSettingService, never()).getSettingValue("last_telemetry_sent");
    }

    @Test
    void checkAndRunTelemetry_shouldRunTaskTrue_callsSendTelemetryData() {
        AppSettings settings = mock(AppSettings.class);
        when(appSettingService.getAppSettings()).thenReturn(settings);
        when(settings.isTelemetryEnabled()).thenReturn(true);
        when(appSettingService.getSettingValue("last_telemetry_sent")).thenReturn(Instant.now().minusSeconds(60 * 60 * 25).toString());
        CronService spy = spy(cronService);
        doNothing().when(spy).sendTelemetryData();
        // Ensure getInstallationPing returns non-null for ping checks
        InstallationPing ping = InstallationPing.builder().appVersion("1.0.0").build();
        when(telemetryService.getInstallationPing()).thenReturn(ping);
        spy.initScheduledTasks();
        verify(spy).sendTelemetryData();
    }

    @Test
    void checkAndRunPing_appVersionChanged_callsSendPing() {
        when(appSettingService.getSettingValue("last_ping_app_version")).thenReturn("1.0.0");
        InstallationPing ping = InstallationPing.builder().appVersion("2.0.0").build();
        when(telemetryService.getInstallationPing()).thenReturn(ping);
        CronService spy = spy(cronService);
        doNothing().when(spy).sendPing();
        spy.initScheduledTasks();
        verify(spy).sendPing();
    }

    @Test
    void checkAndRunPing_shouldRunTaskTrue_callsSendPing() {
        when(appSettingService.getSettingValue("last_ping_app_version")).thenReturn("1.0.0");
        InstallationPing ping = InstallationPing.builder().appVersion("1.0.0").build();
        when(telemetryService.getInstallationPing()).thenReturn(ping);
        when(appSettingService.getSettingValue("last_ping_sent")).thenReturn(Instant.now().minusSeconds(60 * 60 * 25).toString());
        CronService spy = spy(cronService);
        doNothing().when(spy).sendPing();
        spy.initScheduledTasks();
        verify(spy).sendPing();
    }

    @Test
    void checkAndRunPing_shouldRunTaskFalse_doesNotCallSendPing() {
        when(appSettingService.getSettingValue("last_ping_app_version")).thenReturn("1.0.0");
        InstallationPing ping = InstallationPing.builder().appVersion("1.0.0").build();
        when(telemetryService.getInstallationPing()).thenReturn(ping);
        when(appSettingService.getSettingValue("last_ping_sent")).thenReturn(Instant.now().toString());
        CronService spy = spy(cronService);
        doNothing().when(spy).sendPing();
        spy.initScheduledTasks();
        verify(spy, never()).sendPing();
    }

    @Test
    void sendTelemetryData_nullSettings_doesNotThrowOrSend() {
        when(appSettingService.getAppSettings()).thenReturn(null);
        cronService.sendTelemetryData();
        verifyNoInteractions(telemetryService);
        verify(appSettingService, never()).saveSetting(anyString(), anyString());
    }

    @Test
    void sendTelemetryData_telemetryEnabled_collectTelemetryReturnsNull_doesNotThrow() {
        AppSettings settings = mock(AppSettings.class);
        when(appSettingService.getAppSettings()).thenReturn(settings);
        when(settings.isTelemetryEnabled()).thenReturn(true);
        when(appProperties.getTelemetry().getBaseUrl()).thenReturn("http://telemetry");
        when(telemetryService.collectTelemetry()).thenReturn(null);

        CronService spy = spy(cronService);
        doReturn(false).when(spy).postData(anyString(), any());

        spy.sendTelemetryData();
        verify(appSettingService, never()).saveSetting(eq("last_telemetry_sent"), anyString());
    }

    @Test
    void sendPing_nullPing_doesNotSave() {
        when(telemetryService.getInstallationPing()).thenReturn(null);
        cronService.sendPing();
        verify(appSettingService, never()).saveSetting(anyString(), anyString());
    }

    @Test
    void shouldRunTask_farFutureTimestamp_returnsFalse() {
        String future = Instant.now().plusSeconds(60 * 60 * 25).toString();
        assertFalse(cronServiceShouldRunTask(future));
    }

    @Test
    void shouldRunTask_epoch_returnsTrue() {
        String epoch = Instant.EPOCH.toString();
        assertTrue(cronServiceShouldRunTask(epoch));
    }

    @Test
    void hasAppVersionChanged_lastPingNullCurrentNull_returnsFalse() {
        when(appSettingService.getSettingValue("last_ping_app_version")).thenReturn(null);
        when(telemetryService.getInstallationPing()).thenReturn(null);
        assertFalse(invokeHasAppVersionChanged(cronService));
    }

    @Test
    void hasAppVersionChanged_lastPingEmptyCurrentNull_returnsFalse() {
        when(appSettingService.getSettingValue("last_ping_app_version")).thenReturn("");
        when(telemetryService.getInstallationPing()).thenReturn(null);
        assertFalse(invokeHasAppVersionChanged(cronService));
    }

    @Test
    void hasAppVersionChanged_lastPingNonEmptyCurrentNull_returnsFalse() {
        when(appSettingService.getSettingValue("last_ping_app_version")).thenReturn("1.0.0");
        when(telemetryService.getInstallationPing()).thenReturn(null);
        assertFalse(invokeHasAppVersionChanged(cronService));
    }

    @Test
    void checkAndRunTelemetry_nullSettings_doesNothing() {
        when(appSettingService.getAppSettings()).thenReturn(null);
        cronService.initScheduledTasks();
        verify(appSettingService, never()).getSettingValue("last_telemetry_sent");
    }

    @Test
    void checkAndRunTelemetry_shouldRunTaskFalse_doesNotCallSendTelemetryData() {
        AppSettings settings = mock(AppSettings.class);
        when(appSettingService.getAppSettings()).thenReturn(settings);
        when(settings.isTelemetryEnabled()).thenReturn(true);
        when(appSettingService.getSettingValue("last_telemetry_sent")).thenReturn(Instant.now().toString());
        CronService spy = spy(cronService);
        doNothing().when(spy).sendTelemetryData();
        spy.initScheduledTasks();
        verify(spy, never()).sendTelemetryData();
    }

    @Test
    void checkAndRunPing_nullLastPingVersion_doesNotCallSendPing() {
        when(appSettingService.getSettingValue("last_ping_app_version")).thenReturn(null);
        InstallationPing ping = InstallationPing.builder().appVersion("1.0.0").build();
        when(telemetryService.getInstallationPing()).thenReturn(ping);
        CronService spy = spy(cronService);
        doNothing().when(spy).sendPing();
        spy.initScheduledTasks();
        verify(spy, never()).sendPing();
    }

    @Test
    void checkAndRunPing_nullPing_doesNotCallSendPing() {
        when(appSettingService.getSettingValue("last_ping_app_version")).thenReturn("1.0.0");
        when(telemetryService.getInstallationPing()).thenReturn(null);
        CronService spy = spy(cronService);
        doNothing().when(spy).sendPing();
        spy.initScheduledTasks();
        verify(spy, never()).sendPing();
    }

    @Test
    void checkAndRunPing_shouldRunTaskFalseAndNoVersionChange_doesNotCallSendPing() {
        when(appSettingService.getSettingValue("last_ping_app_version")).thenReturn("1.0.0");
        InstallationPing ping = InstallationPing.builder().appVersion("1.0.0").build();
        when(telemetryService.getInstallationPing()).thenReturn(ping);
        when(appSettingService.getSettingValue("last_ping_sent")).thenReturn(Instant.now().toString());
        CronService spy = spy(cronService);
        doNothing().when(spy).sendPing();
        spy.initScheduledTasks();
        verify(spy, never()).sendPing();
    }
}
