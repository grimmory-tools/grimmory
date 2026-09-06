package org.booklore.service.appsettings;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;

import org.booklore.config.AppProperties;
import org.booklore.config.security.service.AuthenticationService;
import org.booklore.model.dto.BookLoreUser;
import org.booklore.model.dto.settings.AppSettingKey;
import org.booklore.model.dto.settings.AppSettings;
import org.booklore.model.dto.settings.KomgaSettings;
import org.booklore.model.entity.AppSettingEntity;
import org.booklore.model.enums.AuditAction;
import org.booklore.repository.AppSettingsRepository;
import org.booklore.service.audit.AuditService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import tools.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AppSettingServiceTest {

    @Mock
    private AppProperties appProperties;
    @Mock
    private AuthenticationService authenticationService;
    @Mock
    private AuditService auditService;
    @Mock
    private AppSettingsRepository appSettingsRepository;

    private SettingPersistenceHelper settingPersistenceHelper;

    private AppSettingService appSettingService;

    @BeforeEach
    void setUp() {
        settingPersistenceHelper = new SettingPersistenceHelper(appSettingsRepository, new ObjectMapper());
        appSettingService = new AppSettingService(appProperties, settingPersistenceHelper, authenticationService, auditService, new ObjectMapper());

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

    @Test
    void getAppSettings_buildsKomgaSettingsDefault_whenNoRowsExist() throws Exception{
        when(appSettingsRepository.findAll()).thenReturn(List.of());
        when(appSettingsRepository.findByName(anyString())).thenReturn(null);
        when(appProperties.getRemoteAuth()).thenReturn(new AppProperties.RemoteAuth());

        AppSettings result = appSettingService.getAppSettings();

        assertThat(result.getKomgaSettings()).isNotNull();
        assertThat(result.getKomgaSettings().getRememberMeKey()).isNotBlank();
        assertThat(result.getKomgaSettings().getRememberMeDurationInSeconds()).isEqualTo(2592000);

        ArgumentCaptor<AppSettingEntity> captor = ArgumentCaptor.forClass(AppSettingEntity.class);
        verify(appSettingsRepository, atLeastOnce()).save(captor.capture());
        assertThat(captor.getAllValues())
                .anyMatch(e -> "komga_settings".equals(e.getName()));
    }

    @Test
    void getAppSettings_readsPersistedKomgaSettings() throws Exception {
        AppSettingEntity komgaRow = new AppSettingEntity();
        komgaRow.setName("komga_settings");
        komgaRow.setVal("{\"rememberMeKey\":\"persistedKey\",\"rememberMeDurationInSeconds\":86400}");

        when(appSettingsRepository.findAll()).thenReturn(List.of(komgaRow));
        when(appSettingsRepository.findByName(anyString())).thenReturn(null);
        when(appProperties.getRemoteAuth()).thenReturn(new AppProperties.RemoteAuth());

        AppSettings result = appSettingService.getAppSettings();

        assertThat(result.getKomgaSettings()).isNotNull();
        assertThat(result.getKomgaSettings().getRememberMeKey()).isEqualTo("persistedKey");
        assertThat(result.getKomgaSettings().getRememberMeDurationInSeconds()).isEqualTo(86400);
    }

    @Test
    void defaultKomgaSettings_generatesUniqueKeys() {
        KomgaSettings first = settingPersistenceHelper.getDefaultKomgaSettings();
        KomgaSettings second = settingPersistenceHelper.getDefaultKomgaSettings();

        assertThat(first.getRememberMeKey()).isNotBlank();
        assertThat(second.getRememberMeKey()).isNotBlank();
        assertThat(first.getRememberMeKey()).isNotEqualTo(second.getRememberMeKey());
        assertThat(first.getRememberMeDurationInSeconds()).isEqualTo(2592000);
    }

    @Test
    void updateSetting_rejectsNullKomgaSettings() {
        assertThatThrownBy(() -> appSettingService.updateSetting(
                AppSettingKey.KOMGA_SETTINGS,
                null
        ))
        .isInstanceOf(Exception.class)
        .hasMessageContaining("Komga settings cannot be null");

        verify(appSettingsRepository, never()).save(any());
    }

    @Test
    void updateSetting_rejectsKomgaSettingsWithNullKey() {
        KomgaSettings settings = new KomgaSettings();
        settings.setRememberMeKey(null);
        settings.setRememberMeDurationInSeconds(86400);

        assertThatThrownBy(() -> appSettingService.updateSetting(
                AppSettingKey.KOMGA_SETTINGS,
                settings
        ))
        .isInstanceOf(Exception.class)
        .hasMessageContaining("rememberMeKey");

        verify(appSettingsRepository, never()).save(any());
    }

    @Test
    void updateSetting_rejectsKomgaSettingsWithBlankKey() {
        KomgaSettings settings = new KomgaSettings();
        settings.setRememberMeKey("   ");
        settings.setRememberMeDurationInSeconds(86400);

        assertThatThrownBy(() -> appSettingService.updateSetting(
                AppSettingKey.KOMGA_SETTINGS,
                settings
        ))
        .isInstanceOf(Exception.class)
        .hasMessageContaining("rememberMeKey");

        verify(appSettingsRepository, never()).save(any());
    }

    @Test
    void updateSetting_rejectsKomgaSettingsWithNullDuration() {
        KomgaSettings settings = new KomgaSettings();
        settings.setRememberMeKey("validKey123");
        settings.setRememberMeDurationInSeconds(null);

        assertThatThrownBy(() -> appSettingService.updateSetting(
                AppSettingKey.KOMGA_SETTINGS,
                settings
        ))
        .isInstanceOf(Exception.class)
        .hasMessageContaining("rememberMeDurationInSeconds");

        verify(appSettingsRepository, never()).save(any());
    }

    @Test
    void updateSetting_rejectsKomgaSettingsWithZeroDuration() {
        KomgaSettings settings = new KomgaSettings();
        settings.setRememberMeKey("validKey123");
        settings.setRememberMeDurationInSeconds(0);

        assertThatThrownBy(() -> appSettingService.updateSetting(
                AppSettingKey.KOMGA_SETTINGS,
                settings
        ))
        .isInstanceOf(Exception.class)
        .hasMessageContaining("positive");

        verify(appSettingsRepository, never()).save(any());
    }

    @Test
    void updateSetting_rejectsKomgaSettingsWithNegativeDuration() {
        KomgaSettings settings = new KomgaSettings();
        settings.setRememberMeKey("validKey123");
        settings.setRememberMeDurationInSeconds(-1);

        assertThatThrownBy(() -> appSettingService.updateSetting(
                AppSettingKey.KOMGA_SETTINGS,
                settings
        ))
        .isInstanceOf(Exception.class)
        .hasMessageContaining("positive");

        verify(appSettingsRepository, never()).save(any());
    }

    @Test
    void updateSetting_acceptsValidKomgaSettings() throws Exception {
        KomgaSettings settings = new KomgaSettings();
        settings.setRememberMeKey("validKey123");
        settings.setRememberMeDurationInSeconds(86400);

        appSettingService.updateSetting(AppSettingKey.KOMGA_SETTINGS, settings);

        ArgumentCaptor<AppSettingEntity> settingCaptor = ArgumentCaptor.forClass(AppSettingEntity.class);
        verify(appSettingsRepository).save(settingCaptor.capture());

        AppSettingEntity savedSetting = settingCaptor.getValue();
        assertThat(savedSetting.getName()).isEqualTo(AppSettingKey.KOMGA_SETTINGS.toString());
        assertThat(savedSetting.getVal()).contains("validKey123");
        assertThat(savedSetting.getVal()).contains("86400");
    }

    @Test
    void updateSetting_acceptsKomgaSettingsAsMap() throws Exception {
        Map<String, Object> settings = Map.of(
                "rememberMeKey", "validKey123",
                "rememberMeDurationInSeconds", 86400
        );

        appSettingService.updateSetting(
                AppSettingKey.KOMGA_SETTINGS,
                settings
        );

        ArgumentCaptor<AppSettingEntity> captor =
                ArgumentCaptor.forClass(AppSettingEntity.class);

        verify(appSettingsRepository).save(captor.capture());

        assertThat(captor.getValue().getName())
                .isEqualTo(AppSettingKey.KOMGA_SETTINGS.toString());
        assertThat(captor.getValue().getVal()).contains("validKey123");
        assertThat(captor.getValue().getVal()).contains("86400");
    }

	@Test
	void updateSetting_rejectsIncompleteKomgaSettingsMap() {
		Map<String, Object> settings = Map.of(
				"rememberMeKey", "validKey123"
		);

		assertThatThrownBy(() -> appSettingService.updateSetting(
				AppSettingKey.KOMGA_SETTINGS,
				settings
		))
				.hasMessageContaining("rememberMeDurationInSeconds");

		verify(appSettingsRepository, never()).save(any());
	}
}
