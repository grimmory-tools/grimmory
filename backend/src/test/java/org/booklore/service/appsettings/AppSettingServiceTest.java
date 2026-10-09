package org.booklore.service.appsettings;

import org.booklore.config.AppProperties;
import org.booklore.config.security.service.AuthenticationService;
import org.booklore.exception.APIException;
import org.booklore.model.dto.BookLoreUser;
import org.booklore.model.dto.settings.AppSettingKey;
import org.booklore.model.dto.settings.PublicAppSetting;
import org.booklore.model.entity.AppSettingEntity;
import org.booklore.model.enums.AuditAction;
import org.booklore.repository.AppSettingsRepository;
import org.booklore.model.dto.settings.KoreaderSyncSettings;
import org.booklore.service.audit.AuditService;
import org.booklore.service.koreader.KoreaderSyncSettingsChangedEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AppSettingServiceTest {

    @Mock
    private AppProperties appProperties;
    @Mock
    private AuthenticationService authenticationService;
    @Mock
    private AuditService auditService;
    @Mock
    private AppSettingsRepository appSettingsRepository;
    @Spy
    private ObjectMapper objectMapper = JsonMapper.shared();
    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private AppSettingService appSettingService;

    @Nested
    class PublicSettings {
        private PublicAppSetting publicSettingsWithKoreaderSync(String json) {
            AppSettingEntity stored = new AppSettingEntity();
            stored.setName(AppSettingKey.KOREADER_SYNC_SETTINGS.toString());
            stored.setVal(json);
            when(appSettingsRepository.findAll()).thenReturn(List.of(stored));
            when(appProperties.getRemoteAuth()).thenReturn(new AppProperties.RemoteAuth());
            return appSettingService.getPublicSettings();
        }

        @Test
        void publishesTheExternalServerAndLoginEditsWhileExternalSyncIsOn() {
            PublicAppSetting settings = publicSettingsWithKoreaderSync(
                    "{\"externalServerEnabled\":true,\"externalServerUrl\":\" https://sync.example \",\"usersCanEditLogin\":true}");

            assertThat(settings.getKoreaderSyncUrlOverride()).isEqualTo("https://sync.example");
            assertThat(settings.isKoreaderUsersCanEditLogin()).isTrue();
        }

        @Test
        void loginEditsAreOffWhileExternalSyncIsOff() {
            PublicAppSetting settings = publicSettingsWithKoreaderSync(
                    "{\"externalServerEnabled\":false,\"usersCanEditLogin\":true}");

            assertThat(settings.getKoreaderSyncUrlOverride()).isNull();
            assertThat(settings.isKoreaderUsersCanEditLogin()).isFalse();
        }
    }

    @Nested
    class UpdateSetting {
        @BeforeEach
        void setUp() {
            var permissions = new BookLoreUser.UserPermissions();
            permissions.setAdmin(true);
            BookLoreUser user = BookLoreUser.builder()
                    .id(1L)
                    .username("admin")
                    .permissions(permissions)
                    .build();

            when(authenticationService.getAuthenticatedUser()).thenReturn(user);
        }

        @Test
        void updateSetting_publishesKoreaderSyncSettingsChange() throws Exception {
            AppSettingEntity stored = new AppSettingEntity();
            stored.setName(AppSettingKey.KOREADER_SYNC_SETTINGS.toString());
            stored.setVal("{\"externalServerEnabled\":false,\"externalServerUrl\":\"\"}");
            when(appSettingsRepository.findByName(AppSettingKey.KOREADER_SYNC_SETTINGS.toString())).thenReturn(stored);

            appSettingService.updateSetting(AppSettingKey.KOREADER_SYNC_SETTINGS,
                    Map.of("externalServerEnabled", true, "externalServerUrl", "https://sync.example", "shelfName", " Devices "));

            ArgumentCaptor<KoreaderSyncSettingsChangedEvent> eventCaptor = ArgumentCaptor.forClass(KoreaderSyncSettingsChangedEvent.class);
            verify(eventPublisher).publishEvent(eventCaptor.capture());
            KoreaderSyncSettingsChangedEvent event = eventCaptor.getValue();
            assertThat(event.before().isExternalServerEnabled()).isFalse();
            assertThat(event.before().effectiveShelfName()).isEqualTo(KoreaderSyncSettings.DEFAULT_SHELF_NAME);
            assertThat(event.after().isExternalServerEnabled()).isTrue();
            assertThat(event.after().effectiveShelfName()).isEqualTo("Devices");
        }

        @Test
        void updateSetting_rejectsAnExternalKoreaderServerThatIsNotAnHttpUrl() {
            assertThatThrownBy(() -> appSettingService.updateSetting(AppSettingKey.KOREADER_SYNC_SETTINGS,
                    Map.of("externalServerEnabled", true, "externalServerUrl", "sync.example")))
                    .isInstanceOf(APIException.class);

            verify(appSettingsRepository, never()).save(any());
        }

        @Test
        void updateSetting_rejectsABlankUrlWhileExternalSyncIsOn() {
            assertThatThrownBy(() -> appSettingService.updateSetting(AppSettingKey.KOREADER_SYNC_SETTINGS,
                    Map.of("externalServerEnabled", true, "externalServerUrl", "  ")))
                    .isInstanceOf(APIException.class);

            verify(appSettingsRepository, never()).save(any());
        }

        @Test
        void updateSetting_acceptsABlankUrlWhileExternalSyncIsOff() throws Exception {
            appSettingService.updateSetting(AppSettingKey.KOREADER_SYNC_SETTINGS,
                    Map.of("externalServerEnabled", false, "externalServerUrl", ""));

            verify(appSettingsRepository).save(any());
        }

        @Test
        void updateSetting_leavesTheCacheAloneWhenShelfReconciliationFails() {
            AppSettingEntity stored = new AppSettingEntity();
            stored.setName(AppSettingKey.KOREADER_SYNC_SETTINGS.toString());
            stored.setVal("{\"externalServerEnabled\":false}");
            when(appSettingsRepository.findByName(AppSettingKey.KOREADER_SYNC_SETTINGS.toString())).thenReturn(stored);
            doThrow(new IllegalStateException("reconcile failed")).when(eventPublisher).publishEvent(any(KoreaderSyncSettingsChangedEvent.class));

            assertThatThrownBy(() -> appSettingService.updateSetting(AppSettingKey.KOREADER_SYNC_SETTINGS,
                    Map.of("externalServerEnabled", true, "externalServerUrl", "https://sync.example")))
                    .isInstanceOf(IllegalStateException.class);

            AppSettingEntity committed = new AppSettingEntity();
            committed.setName(AppSettingKey.KOREADER_SYNC_SETTINGS.toString());
            committed.setVal("{\"externalServerEnabled\":false}");
            when(appSettingsRepository.findAll()).thenReturn(List.of(committed));
            when(appProperties.getRemoteAuth()).thenReturn(new AppProperties.RemoteAuth());
            assertThat(appSettingService.getPublicSettings().getKoreaderSyncUrlOverride()).isNull();
        }

        @Test
        void updateSetting_doesNotPublishForOtherKeys() throws Exception {
            appSettingService.updateSetting(AppSettingKey.AUTO_BOOK_SEARCH, true);

            verify(eventPublisher, never()).publishEvent(any());
        }

        @Test
        void updateSetting_acceptsValidOidcRedirectUris() throws Exception {
            appSettingService.updateSetting(
                    AppSettingKey.OIDC_REDIRECT_URIS,
                    List.of("grimmory://oauth2-callback", "grimmory://auth/return")
            );

            ArgumentCaptor<AppSettingEntity> settingCaptor = ArgumentCaptor.forClass(AppSettingEntity.class);
            verify(appSettingsRepository).save(settingCaptor.capture());

            AppSettingEntity savedSetting = settingCaptor.getValue();
            assertThat(savedSetting.getName()).isEqualTo(AppSettingKey.OIDC_REDIRECT_URIS.toString());
            assertThat(savedSetting.getVal()).isEqualTo("[\"grimmory://oauth2-callback\",\"grimmory://auth/return\"]");
            verify(auditService).log(AuditAction.OIDC_CONFIG_CHANGED, "Updated setting: " + AppSettingKey.OIDC_REDIRECT_URIS);
        }

        @Test
        void updateSetting_acceptsWildcardOidcRedirectUri() throws Exception {
            appSettingService.updateSetting(
                    AppSettingKey.OIDC_REDIRECT_URIS,
                    List.of("*")
            );

            ArgumentCaptor<AppSettingEntity> settingCaptor = ArgumentCaptor.forClass(AppSettingEntity.class);
            verify(appSettingsRepository).save(settingCaptor.capture());

            AppSettingEntity savedSetting = settingCaptor.getValue();
            assertThat(savedSetting.getName()).isEqualTo(AppSettingKey.OIDC_REDIRECT_URIS.toString());
            assertThat(savedSetting.getVal()).isEqualTo("[\"*\"]");
            verify(auditService).log(AuditAction.OIDC_CONFIG_CHANGED, "Updated setting: " + AppSettingKey.OIDC_REDIRECT_URIS);
        }

        @Test
        void updateSetting_rejectsWildcardCombinedWithOtherUris() {
            assertThatThrownBy(() -> appSettingService.updateSetting(
                    AppSettingKey.OIDC_REDIRECT_URIS,
                    List.of("*", "grimmory://oauth2-callback")
            ))
                    .hasMessageContaining("Wildcard redirect URI must be the only value");

            verify(appSettingsRepository, never()).save(any());
        }

        @Test
        void updateSetting_rejectsBlankOidcMobileRedirectUri() {
            assertThatThrownBy(() -> appSettingService.updateSetting(
                    AppSettingKey.OIDC_REDIRECT_URIS,
                    List.of(" ")
            ))
                    .hasMessageContaining("Redirect URI cannot be blank");

            verify(appSettingsRepository, never()).save(any());
        }

        @Test
        void updateSetting_rejectsNonStringOidcMobileRedirectUriEntries() {
            assertThatThrownBy(() -> appSettingService.updateSetting(
                    AppSettingKey.OIDC_REDIRECT_URIS,
                    List.of(42)
            ))
                    .hasMessageContaining("OIDC redirect URIs must be an array of strings");

            verify(appSettingsRepository, never()).save(any());
        }

        @Test
        void updateSetting_rejectsDuplicateOidcMobileRedirectUris() {
            assertThatThrownBy(() -> appSettingService.updateSetting(
                    AppSettingKey.OIDC_REDIRECT_URIS,
                    List.of("grimmory://oauth2-callback", "grimmory://oauth2-callback")
            ))
                    .hasMessageContaining("Duplicate redirect URI");

            verify(appSettingsRepository, never()).save(any());
        }

        @Test
        void updateSetting_rejectsHttpRedirectUri() {
            assertThatThrownBy(() -> appSettingService.updateSetting(
                    AppSettingKey.OIDC_REDIRECT_URIS,
                    List.of("https://example.com/oauth2-callback")
            ))
                    .hasMessageContaining("Redirect URI must use a custom mobile scheme");

            verify(appSettingsRepository, never()).save(any());
        }

        @Test
        void updateSetting_rejectsRedirectUriWithFragment() {
            assertThatThrownBy(() -> appSettingService.updateSetting(
                    AppSettingKey.OIDC_REDIRECT_URIS,
                    List.of("grimmory://oauth2-callback#done")
            ))
                    .hasMessageContaining("Redirect URI must not contain a fragment");

            verify(appSettingsRepository, never()).save(any());
        }

        @Test
        void updateSetting_rejectsRedirectUriWithoutScheme() {
            assertThatThrownBy(() -> appSettingService.updateSetting(
                    AppSettingKey.OIDC_REDIRECT_URIS,
                    List.of("oauth2-callback")
            ))
                    .hasMessageContaining("Redirect URI must include a scheme");

            verify(appSettingsRepository, never()).save(any());
        }
    }

    @Test
    void getAppSettings_handlesNullSettingValues() {
        when(appProperties.getRemoteAuth()).thenReturn(new AppProperties.RemoteAuth());

        var setting = new AppSettingEntity();
        setting.setName("test");
        setting.setVal(null);
        when(appSettingsRepository.findAll()).thenReturn(List.of(setting));
        assertThat(appSettingService.getAppSettings()).isNotNull();
    }
}
