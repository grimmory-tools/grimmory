package org.booklore.service.oidc;

import org.booklore.exception.APIException;
import org.booklore.mapper.OidcGroupMappingMapper;
import org.booklore.model.dto.OidcGroupMapping;
import org.booklore.model.dto.settings.AppSettings;
import org.booklore.model.entity.BookLoreUserEntity;
import org.booklore.model.entity.LibraryEntity;
import org.booklore.model.entity.OidcGroupMappingEntity;
import org.booklore.model.entity.UserPermissionsEntity;
import org.booklore.repository.LibraryRepository;
import org.booklore.repository.OidcGroupMappingRepository;
import org.booklore.repository.UserRepository;
import org.booklore.service.appsettings.AppSettingService;
import org.booklore.service.audit.AuditService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OidcGroupMappingServiceTest {

    @Mock
    private OidcGroupMappingRepository repository;

    @Mock
    private OidcGroupMappingMapper mapper;

    @Mock
    private AuditService auditService;

    @Mock
    private AppSettingService appSettingService;

    @Mock
    private LibraryRepository libraryRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private OidcGroupMappingService service;

    @Test
    void getAll_returnsMappedList() {
        var entities = List.of(new OidcGroupMappingEntity());
        var dtos = List.of(new OidcGroupMapping(1L, "group1", false, List.of(), List.of(), "desc"));
        when(repository.findAll()).thenReturn(entities);
        when(mapper.toDtoList(entities)).thenReturn(dtos);

        var result = service.getAll();

        assertThat(result).isEqualTo(dtos);
        verify(repository).findAll();
        verify(mapper).toDtoList(entities);
    }

    @Test
    void create_savesEntityWithNullIdAndAudits() {
        var dto = new OidcGroupMapping(99L, "admins", true, List.of("permissionUpload"), List.of(1L), "Admin group");
        var entity = new OidcGroupMappingEntity();
        entity.setId(99L);
        entity.setOidcGroupClaim("admins");
        var savedEntity = new OidcGroupMappingEntity();
        savedEntity.setId(1L);
        savedEntity.setOidcGroupClaim("admins");
        var savedDto = new OidcGroupMapping(1L, "admins", true, List.of("permissionUpload"), List.of(1L), "Admin group");

        when(mapper.toEntity(dto)).thenReturn(entity);
        when(repository.save(entity)).thenReturn(savedEntity);
        when(mapper.toDto(savedEntity)).thenReturn(savedDto);

        var result = service.create(dto);

        assertThat(result).isEqualTo(savedDto);
        assertThat(entity.getId()).isNull();
        verify(repository).save(entity);
        verify(auditService).log(any(), anyString());
    }

    @Test
    void update_existingMapping_updatesFieldsAndAudits() {
        var existing = new OidcGroupMappingEntity();
        existing.setId(1L);
        existing.setOidcGroupClaim("old-group");
        var dto = new OidcGroupMapping(1L, "new-group", true, List.of("permissionUpload"), List.of(2L), "Updated");
        var entity = OidcGroupMappingEntity.builder().admin(true).oidcGroupClaim("new-group").description("Updated").build();

        var savedEntity = new OidcGroupMappingEntity();
        savedEntity.setId(1L);
        savedEntity.setOidcGroupClaim("new-group");
        var savedDto = new OidcGroupMapping(1L, "new-group", true, List.of("permissionUpload"), List.of(2L), "Updated");

        when(repository.findById(1L)).thenReturn(Optional.of(existing));
        when(repository.save(existing)).thenReturn(savedEntity);
        when(mapper.toDto(savedEntity)).thenReturn(savedDto);
        when(mapper.toEntity(dto)).thenReturn(entity);

        var result = service.update(1L, dto);

        assertThat(result).isEqualTo(savedDto);
        assertThat(existing.getOidcGroupClaim()).isEqualTo("new-group");
        assertThat(existing.isAdmin()).isTrue();
        assertThat(existing.getDescription()).isEqualTo("Updated");
        verify(auditService).log(any(), anyString());
    }

    @Test
    void update_nonExisting_throwsGenericNotFound() {
        when(repository.findById(999L)).thenReturn(Optional.empty());
        var dto = new OidcGroupMapping(999L, "group", false, List.of(), List.of(), null);

        assertThatThrownBy(() -> service.update(999L, dto))
                .isInstanceOf(APIException.class);

        verify(repository, never()).save(any());
    }

    @Test
    void delete_existingMapping_deletesAndAudits() {
        var existing = new OidcGroupMappingEntity();
        existing.setId(1L);
        existing.setOidcGroupClaim("group1");
        when(repository.findById(1L)).thenReturn(Optional.of(existing));

        service.delete(1L);

        verify(repository).delete(existing);
        verify(auditService).log(any(), anyString());
    }

    @Test
    void delete_nonExisting_throwsGenericNotFound() {
        when(repository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.delete(999L))
                .isInstanceOf(APIException.class);

        verify(repository, never()).delete(any());
    }

    @Test
    void syncUserGroups_nullGroups_doesNothing() {
        var user = mock(BookLoreUserEntity.class);

        service.syncUserGroups(user, null);

        verifyNoInteractions(appSettingService, repository, userRepository);
    }

    @Test
    void syncUserGroups_emptyGroups_doesNothing() {
        var user = mock(BookLoreUserEntity.class);

        service.syncUserGroups(user, Collections.emptyList());

        verifyNoInteractions(appSettingService, repository, userRepository);
    }

    @Test
    void syncUserGroups_disabledMode_doesNothing() {
        var user = mock(BookLoreUserEntity.class);
        var settings = new AppSettings();
        settings.setOidcGroupSyncMode("DISABLED");
        when(appSettingService.getAppSettings()).thenReturn(settings);

        service.syncUserGroups(user, List.of("group1"));

        verifyNoInteractions(repository, userRepository);
    }

    @Test
    void syncUserGroups_nullMode_doesNothing() {
        var user = mock(BookLoreUserEntity.class);
        var settings = new AppSettings();
        settings.setOidcGroupSyncMode(null);
        when(appSettingService.getAppSettings()).thenReturn(settings);

        service.syncUserGroups(user, List.of("group1"));

        verifyNoInteractions(repository, userRepository);
    }

    @Test
    void syncUserGroups_noMatchingMappings_doesNothing() {
        var user = mock(BookLoreUserEntity.class);
        var settings = new AppSettings();
        settings.setOidcGroupSyncMode("ON_LOGIN");
        when(appSettingService.getAppSettings()).thenReturn(settings);
        when(repository.findByOidcGroupClaimIn(List.of("group1"))).thenReturn(Collections.emptyList());

        service.syncUserGroups(user, List.of("group1"));

        verifyNoInteractions(userRepository);
    }

    @Test
    void syncUserGroups_onLogin_replacesPermissions() {
        var perms = new UserPermissionsEntity();
        perms.setPermissionUpload(true);
        perms.setPermissionDownload(true);
        var user = createMockedUser(perms);

        var mapping = createMapping(false, "[\"permissionUpload\",\"permissionEditMetadata\"]", "[1]");
        var dto = createMappingDto(false, List.of("permissionUpload", "permissionEditMetadata"), List.of(1L));
        setupSyncMocks("ON_LOGIN", List.of("group1"), Map.of(dto, mapping));

        var lib1 = new LibraryEntity();
        lib1.setId(1L);
        when(libraryRepository.findAllById(any())).thenReturn(List.of(lib1));

        service.syncUserGroups(user, List.of("group1"));

        assertThat(perms.isPermissionUpload()).isTrue();
        assertThat(perms.isPermissionDownload()).isFalse();
        assertThat(perms.isPermissionEditMetadata()).isTrue();
        assertThat(perms.isPermissionAdmin()).isFalse();
        verify(userRepository).save(user);
    }

    @Test
    void syncUserGroups_onLogin_replacesLibraries() {
        var perms = new UserPermissionsEntity();
        var librariesHolder = new AtomicReference<Set<LibraryEntity>>();
        var existingLib = new LibraryEntity();
        existingLib.setId(10L);
        librariesHolder.set(new HashSet<>(List.of(existingLib)));
        var user = createMockedUserWithLibraries(perms, librariesHolder);

        var mapping = createMapping(false, "[]", "[1,2]");
        setupSyncMocks("ON_LOGIN", List.of("group1"), List.of(mapping));

        var lib1 = new LibraryEntity();
        lib1.setId(1L);
        var lib2 = new LibraryEntity();
        lib2.setId(2L);
        when(libraryRepository.findAllById(any())).thenReturn(List.of(lib1, lib2));

        service.syncUserGroups(user, List.of("group1"));

        assertThat(librariesHolder.get()).containsExactlyInAnyOrder(lib1, lib2);
        verify(userRepository).save(user);
    }

    @Test
    void syncUserGroups_onLoginAdditive_onlyAddsPermissions() {
        var perms = new UserPermissionsEntity();
        perms.setPermissionDownload(true);
        var user = createMockedUser(perms);

        var mapping = createMapping(false, "[\"permissionUpload\"]", "[1]");
        var dto = createMappingDto(false, List.of("permissionUpload"), List.of(1L));

        setupSyncMocks("ON_LOGIN_ADDITIVE", List.of("group1"), Map.of(dto, mapping));

        var lib1 = new LibraryEntity();
        lib1.setId(1L);
        when(libraryRepository.findAllById(any())).thenReturn(List.of(lib1));

        service.syncUserGroups(user, List.of("group1"));

        assertThat(perms.isPermissionUpload()).isTrue();
        assertThat(perms.isPermissionDownload()).isTrue();
        verify(userRepository).save(user);
    }

    @Test
    void syncUserGroups_onLoginAdditive_addsLibrariesToExisting() {
        var perms = new UserPermissionsEntity();
        var librariesHolder = new AtomicReference<Set<LibraryEntity>>();
        var existingLib = new LibraryEntity();
        existingLib.setId(10L);
        librariesHolder.set(new HashSet<>(List.of(existingLib)));
        var user = createMockedUserWithLibraries(perms, librariesHolder);

        var mapping = createMapping(false, "[]", "[1]");
        var dto = createMappingDto(false, List.of(), List.of(1L));

        setupSyncMocks("ON_LOGIN_ADDITIVE", List.of("group1"), Map.of(dto, mapping));

        var lib1 = new LibraryEntity();
        lib1.setId(1L);
        var lib10 = new LibraryEntity();
        lib10.setId(10L);
        when(libraryRepository.findAllById(any())).thenReturn(List.of(lib1, lib10));

        service.syncUserGroups(user, List.of("group1"));

        assertThat(librariesHolder.get()).hasSize(2);
        verify(userRepository).save(user);
    }

    @Test
    void syncUserGroups_mergesAdminFlagFromMultipleMappings() {
        var perms = new UserPermissionsEntity();
        var user = createMockedUser(perms);

        var mapping1 = createMapping(false, "[\"permissionUpload\"]", "[]");
        var dto1 = createMappingDto(false, List.of("permissionUpload"), List.of());
        var mapping2 = createMapping(true, "[\"permissionDownload\"]", "[]");
        var dto2 = createMappingDto(true, List.of("permissionDownload"), List.of());

        setupSyncMocks("ON_LOGIN", List.of("group1", "group2"), Map.of(dto1, mapping1, dto2, mapping2));

        service.syncUserGroups(user, List.of("group1", "group2"));

        assertThat(perms.isPermissionAdmin()).isTrue();
        assertThat(perms.isPermissionUpload()).isTrue();
        assertThat(perms.isPermissionDownload()).isTrue();
        verify(userRepository).save(user);
    }

    @Test
    void syncUserGroups_createsPermissionsEntityIfNull() {
        var permissionsHolder = new AtomicReference<UserPermissionsEntity>();
        var user = mock(BookLoreUserEntity.class);
        when(user.getUsername()).thenReturn("testuser");
        when(user.getPermissions()).thenReturn(null).thenAnswer(_ -> permissionsHolder.get());
        doAnswer(invocation -> {
            permissionsHolder.set(invocation.getArgument(0));
            return null;
        }).when(user).setPermissions(any());
        doAnswer(invocation -> {
            permissionsHolder.get().setUser(user);
            return null;
        }).when(user).setLibraries(any());
        // Need getLibraries for additive mode but this is ON_LOGIN
        lenient().when(user.getLibraries()).thenReturn(null);

        var mapping = createMapping(false, "[\"permissionUpload\"]", "[1]");
        var dto = createMappingDto(false, List.of("permissionUpload"), List.of(1L));
        setupSyncMocks("ON_LOGIN", List.of("group1"), Map.of(dto, mapping));

        var lib1 = new LibraryEntity();
        lib1.setId(1L);
        when(libraryRepository.findAllById(any())).thenReturn(List.of(lib1));

        service.syncUserGroups(user, List.of("group1"));

        assertThat(permissionsHolder.get()).isNotNull();
        assertThat(permissionsHolder.get().getUser()).isEqualTo(user);
        assertThat(permissionsHolder.get().isPermissionUpload()).isTrue();
        verify(userRepository).save(user);
    }

    @Test
    void syncUserGroups_unknownMode_doesNothing() {
        var user = mock(BookLoreUserEntity.class);
        var perms = new UserPermissionsEntity();
        lenient().when(user.getPermissions()).thenReturn(perms);

        var mapping = createMapping(false, "[\"permissionUpload\"]", "[1]");
        var dto = new OidcGroupMapping(1L, "", false, List.of("permissionUpload"), List.of(1L), "");

        setupSyncMocks("UNKNOWN_MODE", List.of("group1"), List.of(mapping));

        when(mapper.toDto(mapping)).thenReturn(dto);

        service.syncUserGroups(user, List.of("group1"));

        verifyNoInteractions(userRepository);
    }

    private BookLoreUserEntity createMockedUser(UserPermissionsEntity perms) {
        var user = mock(BookLoreUserEntity.class);
        lenient().when(user.getUsername()).thenReturn("testuser");
        when(user.getPermissions()).thenReturn(perms);
        lenient().when(user.getLibraries()).thenReturn(null);
        return user;
    }

    private BookLoreUserEntity createMockedUserWithLibraries(UserPermissionsEntity perms,
                                                              AtomicReference<Set<LibraryEntity>> librariesHolder) {
        var user = mock(BookLoreUserEntity.class);
        lenient().when(user.getUsername()).thenReturn("testuser");
        when(user.getPermissions()).thenReturn(perms);
        lenient().when(user.getLibraries()).thenAnswer(_ -> librariesHolder.get());
        doAnswer(invocation -> {
            librariesHolder.set(invocation.getArgument(0));
            return null;
        }).when(user).setLibraries(any());
        return user;
    }

    private OidcGroupMappingEntity createMapping(boolean admin, String permissionsJson, String libraryIdsJson) {
        return OidcGroupMappingEntity.builder()
                .id(1L)
                .admin(admin)
                .permissions(permissionsJson)
                .libraryIds(libraryIdsJson)
                .oidcGroupClaim("group")
                .description("")
                .build();
    }

    private OidcGroupMapping createMappingDto(boolean admin, List<String> permissions, List<Long> libraryIds) {
        return new OidcGroupMapping(
            1L,
            "",
            admin,
            permissions,
            libraryIds,
            ""
        );
    }

    private void setupSyncMocks(String syncMode, List<String> groups, List<OidcGroupMappingEntity> mappings) {
        var settings = new AppSettings();
        settings.setOidcGroupSyncMode(syncMode);
        when(appSettingService.getAppSettings()).thenReturn(settings);
        when(repository.findByOidcGroupClaimIn(groups)).thenReturn(mappings);
    }

    private void setupMockMapper(Map<OidcGroupMapping, OidcGroupMappingEntity> mappings) {
        for (var entry : mappings.entrySet()) {
            when(mapper.toDto(entry.getValue())).thenReturn(entry.getKey());
            when(mapper.toEntity(entry.getKey())).thenReturn(entry.getValue());
        }
    }

    private void setupSyncMocks(String syncMode, List<String> groups, Map<OidcGroupMapping, OidcGroupMappingEntity> mappings) {
        setupSyncMocks(syncMode, groups, mappings.values().stream().toList());
        setupMockMapper(mappings);
    }
}
