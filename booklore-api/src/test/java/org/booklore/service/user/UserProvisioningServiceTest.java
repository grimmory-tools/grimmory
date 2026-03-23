package org.booklore.service.user;

import org.booklore.config.AppProperties;
import org.booklore.model.dto.settings.OidcAutoProvisionDetails;
import org.booklore.model.entity.BookLoreUserEntity;
import org.booklore.model.entity.LibraryEntity;
import org.booklore.model.entity.UserPermissionsEntity;
import org.booklore.model.enums.AuditAction;
import org.booklore.model.enums.ProvisioningMethod;
import org.booklore.repository.LibraryRepository;
import org.booklore.repository.UserRepository;
import org.booklore.service.appsettings.AppSettingService;
import org.booklore.service.audit.AuditService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserProvisioningServiceTest {

    @Mock
    private AppProperties appProperties;

    @Mock
    private UserRepository userRepository;

    @Mock
    private LibraryRepository libraryRepository;

    @Mock
    private UserDefaultsService userDefaultsService;

    @Mock
    private AppSettingService appSettingService;

    @Mock
    private AuditService auditService;

    @InjectMocks
    private UserProvisioningService userProvisioningService;

    @Captor
    private ArgumentCaptor<BookLoreUserEntity> userCaptor;

    private OidcAutoProvisionDetails provisionDetails;

    @BeforeEach
    void setUp() {
        when(userRepository.save(any())).thenAnswer(invocation -> {
            var user = invocation.getArgument(0, BookLoreUserEntity.class);
            user.setId(1L);
            return user;
        });

        provisionDetails = new OidcAutoProvisionDetails();
    }

    @Test
    void provisionOidcUser_setsBasicFieldsCorrectly() {
        BookLoreUserEntity result = userProvisioningService.provisionOidcUser(
                "jdoe", "jdoe@example.com", "John Doe",
                "sub-123", "https://issuer.example.com", "https://avatar.example.com/jdoe.png",
                provisionDetails);

        verify(userRepository).save(userCaptor.capture());
        BookLoreUserEntity saved = userCaptor.getValue();

        assertThat(saved.getUsername()).isEqualTo("jdoe");
        assertThat(saved.getEmail()).isEqualTo("jdoe@example.com");
        assertThat(saved.getName()).isEqualTo("John Doe");
        assertThat(saved.getOidcSubject()).isEqualTo("sub-123");
        assertThat(saved.getOidcIssuer()).isEqualTo("https://issuer.example.com");
        assertThat(saved.getAvatarUrl()).isEqualTo("https://avatar.example.com/jdoe.png");
    }

    @Test
    void provisionOidcUser_setsProvisioningMethodToOidc() {
        userProvisioningService.provisionOidcUser(
                "jdoe", "jdoe@example.com", "John Doe",
                "sub-123", "https://issuer.example.com", null,
                provisionDetails);

        verify(userRepository).save(userCaptor.capture());
        assertThat(userCaptor.getValue().getProvisioningMethod()).isEqualTo(ProvisioningMethod.OIDC);
    }

    @Test
    void provisionOidcUser_setsPasswordHashStartingWithOidcUserPrefix() {
        userProvisioningService.provisionOidcUser(
                "jdoe", "jdoe@example.com", "John Doe",
                "sub-123", "https://issuer.example.com", null,
                provisionDetails);

        verify(userRepository).save(userCaptor.capture());
        assertThat(userCaptor.getValue().getPasswordHash()).startsWith("OIDC_USER_");
    }

    @Test
    void provisionOidcUser_setsDefaultPasswordToFalse() {
        userProvisioningService.provisionOidcUser(
                "jdoe", "jdoe@example.com", "John Doe",
                "sub-123", "https://issuer.example.com", null,
                provisionDetails);

        verify(userRepository).save(userCaptor.capture());
        assertThat(userCaptor.getValue().isDefaultPassword()).isFalse();
    }

    @Test
    void provisionOidcUser_appliesPermissionsFromDefaultPermissionsList() {
        provisionDetails.setDefaultPermissions(List.of(
                "permissionUpload", "permissionDownload", "permissionEditMetadata"));

        userProvisioningService.provisionOidcUser(
                "jdoe", "jdoe@example.com", "John Doe",
                "sub-123", "https://issuer.example.com", null,
                provisionDetails);

        verify(userRepository).save(userCaptor.capture());
        UserPermissionsEntity perms = userCaptor.getValue().getPermissions();

        assertThat(perms.isPermissionUpload()).isTrue();
        assertThat(perms.isPermissionDownload()).isTrue();
        assertThat(perms.isPermissionEditMetadata()).isTrue();
    }

    @Test
    void provisionOidcUser_doesNotSetUnspecifiedPermissions() {
        provisionDetails.setDefaultPermissions(List.of("permissionUpload"));

        userProvisioningService.provisionOidcUser(
                "jdoe", "jdoe@example.com", "John Doe",
                "sub-123", "https://issuer.example.com", null,
                provisionDetails);

        verify(userRepository).save(userCaptor.capture());
        UserPermissionsEntity perms = userCaptor.getValue().getPermissions();

        assertThat(perms.isPermissionUpload()).isTrue();
        assertThat(perms.isPermissionDownload()).isFalse();
        assertThat(perms.isPermissionEditMetadata()).isFalse();
        assertThat(perms.isPermissionManageLibrary()).isFalse();
        assertThat(perms.isPermissionEmailBook()).isFalse();
        assertThat(perms.isPermissionDeleteBook()).isFalse();
        assertThat(perms.isPermissionAccessOpds()).isFalse();
        assertThat(perms.isPermissionSyncKoreader()).isFalse();
        assertThat(perms.isPermissionSyncKobo()).isFalse();
        assertThat(perms.isPermissionManageMetadataConfig()).isFalse();
        assertThat(perms.isPermissionAccessBookdrop()).isFalse();
        assertThat(perms.isPermissionAdmin()).isFalse();
    }

    @Test
    void provisionOidcUser_assignsLibrariesFromDefaultLibraryIds() {
        provisionDetails.setDefaultLibraryIds(List.of(10L, 20L));

        LibraryEntity lib1 = new LibraryEntity();
        lib1.setId(10L);
        LibraryEntity lib2 = new LibraryEntity();
        lib2.setId(20L);
        when(libraryRepository.findAllById(List.of(10L, 20L))).thenReturn(List.of(lib1, lib2));

        userProvisioningService.provisionOidcUser(
                "jdoe", "jdoe@example.com", "John Doe",
                "sub-123", "https://issuer.example.com", null,
                provisionDetails);

        verify(userRepository).save(userCaptor.capture());
        assertThat(userCaptor.getValue().getLibraries())
                .hasSize(2)
                .extracting(LibraryEntity::getId)
                .containsExactlyInAnyOrder(10L, 20L);
    }

    @Test
    void provisionOidcUser_handlesNullDefaultPermissions() {
        provisionDetails.setDefaultPermissions(null);

        userProvisioningService.provisionOidcUser(
                "jdoe", "jdoe@example.com", "John Doe",
                "sub-123", "https://issuer.example.com", null,
                provisionDetails);

        verify(userRepository).save(userCaptor.capture());
        UserPermissionsEntity perms = userCaptor.getValue().getPermissions();

        assertThat(perms).isNotNull();
        assertThat(perms.isPermissionUpload()).isFalse();
        assertThat(perms.isPermissionDownload()).isFalse();
    }

    @Test
    void provisionOidcUser_handlesNullDefaultLibraryIds() {
        provisionDetails.setDefaultLibraryIds(null);

        userProvisioningService.provisionOidcUser(
                "jdoe", "jdoe@example.com", "John Doe",
                "sub-123", "https://issuer.example.com", null,
                provisionDetails);

        verify(userRepository).save(userCaptor.capture());
        assertThat(userCaptor.getValue().getLibraries()).isNullOrEmpty();
        verifyNoInteractions(libraryRepository);
    }

    @Test
    void provisionOidcUser_handlesEmptyDefaultLibraryIds() {
        provisionDetails.setDefaultLibraryIds(List.of());

        userProvisioningService.provisionOidcUser(
                "jdoe", "jdoe@example.com", "John Doe",
                "sub-123", "https://issuer.example.com", null,
                provisionDetails);

        verify(userRepository).save(userCaptor.capture());
        assertThat(userCaptor.getValue().getLibraries()).isNullOrEmpty();
        verifyNoInteractions(libraryRepository);
    }

    @Test
    void provisionOidcUser_callsAddDefaultShelvesAndSettings() {
        userProvisioningService.provisionOidcUser(
                "jdoe", "jdoe@example.com", "John Doe",
                "sub-123", "https://issuer.example.com", null,
                provisionDetails);

        verify(userRepository).save(userCaptor.capture());
        BookLoreUserEntity saved = userCaptor.getValue();

        verify(userDefaultsService).addDefaultShelves(saved);
        verify(userDefaultsService).addDefaultSettings(saved);
    }

    @Test
    void provisionOidcUser_auditsUserCreated() {
        userProvisioningService.provisionOidcUser(
                "jdoe", "jdoe@example.com", "John Doe",
                "sub-123", "https://issuer.example.com", null,
                provisionDetails);

        verify(auditService).log(
                eq(AuditAction.USER_CREATED),
                eq("User"),
                eq(1L),
                eq("Created user: jdoe"));
    }
}
