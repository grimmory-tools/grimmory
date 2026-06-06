package org.booklore.service.oidc;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.booklore.exception.ApiError;
import org.booklore.mapper.OidcGroupMappingMapper;
import org.booklore.model.dto.OidcGroupMapping;
import org.booklore.model.entity.BookLoreUserEntity;
import org.booklore.model.entity.LibraryEntity;
import org.booklore.model.entity.OidcGroupMappingEntity;
import org.booklore.model.entity.UserPermissionsEntity;
import org.booklore.model.enums.AuditAction;
import org.booklore.repository.LibraryRepository;
import org.booklore.repository.OidcGroupMappingRepository;
import org.booklore.repository.UserRepository;
import org.booklore.service.appsettings.AppSettingService;
import org.booklore.service.audit.AuditService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Slf4j
@Service
@AllArgsConstructor
public class OidcGroupMappingService {

    private final OidcGroupMappingRepository repository;
    private final OidcGroupMappingMapper mapper;
    private final AuditService auditService;
    private final AppSettingService appSettingService;
    private final LibraryRepository libraryRepository;
    private final UserRepository userRepository;

    public List<OidcGroupMapping> getAll() {
        return mapper.toDtoList(repository.findAll());
    }

    public OidcGroupMapping create(OidcGroupMapping dto) {
        OidcGroupMappingEntity entity = mapper.toEntity(dto);
        entity.setId(null);
        OidcGroupMappingEntity saved = repository.save(entity);
        auditService.log(AuditAction.OIDC_GROUP_MAPPING_CREATED,
                "Created OIDC group mapping: " + saved.getOidcGroupClaim());
        return mapper.toDto(saved);
    }

    public OidcGroupMapping update(Long id, OidcGroupMapping dto) {
        OidcGroupMappingEntity existing = repository.findById(id)
                .orElseThrow(() -> ApiError.GENERIC_NOT_FOUND.createException("OIDC group mapping not found"));
        existing.setOidcGroupClaim(dto.oidcGroupClaim());
        existing.setAdmin(dto.isAdmin());
        existing.setPermissions(mapper.stringListToJson(dto.permissions()));
        existing.setLibraryIds(mapper.longListToJson(dto.libraryIds()));
        existing.setDescription(dto.description());
        OidcGroupMappingEntity saved = repository.save(existing);
        auditService.log(AuditAction.OIDC_GROUP_MAPPING_UPDATED,
                "Updated OIDC group mapping: " + saved.getOidcGroupClaim());
        return mapper.toDto(saved);
    }

    public void delete(Long id) {
        OidcGroupMappingEntity existing = repository.findById(id)
                .orElseThrow(() -> ApiError.GENERIC_NOT_FOUND.createException("OIDC group mapping not found"));
        repository.delete(existing);
        auditService.log(AuditAction.OIDC_GROUP_MAPPING_DELETED,
                "Deleted OIDC group mapping: " + existing.getOidcGroupClaim());
    }

    @Transactional
    public void syncUserGroups(BookLoreUserEntity user, List<String> groups) {
        if (groups == null || groups.isEmpty()) return;

        String syncMode = appSettingService.getAppSettings().getOidcGroupSyncMode();
        if (syncMode == null || "DISABLED".equals(syncMode)) return;

        List<OidcGroupMappingEntity> matchingMappings = repository.findByOidcGroupClaimIn(groups);
        if (matchingMappings.isEmpty()) return;

        boolean mergedAdmin = false;
        Set<String> mergedPermissions = new HashSet<>();
        Set<Long> mergedLibraryIds = new HashSet<>();

        for (OidcGroupMappingEntity mapping : matchingMappings) {
            if (mapping.isAdmin()) mergedAdmin = true;
            mergedPermissions.addAll(mapper.jsonToStringList(mapping.getPermissions()));
            mergedLibraryIds.addAll(mapper.jsonToLongList(mapping.getLibraryIds()));
        }

        UserPermissionsEntity perms = user.getPermissions();
        if (perms == null) {
            perms = new UserPermissionsEntity();
            perms.setUser(user);
            user.setPermissions(perms);
        } else if (perms.getUser() == null) {
            perms.setUser(user);
        }

        switch (syncMode) {
            case "ON_LOGIN" -> {
                applyPermissions(perms, mergedPermissions, mergedAdmin, false);
                List<LibraryEntity> libraries = libraryRepository.findAllById(mergedLibraryIds);
                user.setLibraries(new HashSet<>(libraries));
            }
            case "ON_LOGIN_ADDITIVE" -> {
                applyPermissions(perms, mergedPermissions, mergedAdmin, true);
                Set<Long> existingLibIds = new HashSet<>();
                if (user.getLibraries() != null) {
                    user.getLibraries().forEach(lib -> existingLibIds.add(lib.getId()));
                }
                Set<Long> allLibIds = new HashSet<>(existingLibIds);
                allLibIds.addAll(mergedLibraryIds);
                if (!allLibIds.equals(existingLibIds)) {
                    List<LibraryEntity> libraries = libraryRepository.findAllById(allLibIds);
                    user.setLibraries(new HashSet<>(libraries));
                }
            }
            default -> {
                return;
            }
        }

        userRepository.save(user);
        log.info("Synced OIDC group permissions for user '{}' (mode: {})", user.getUsername(), syncMode);
    }

    private void applyPermissions(UserPermissionsEntity perms, Set<String> permissions, boolean isAdmin, boolean additive) {
        if (!additive) {
            perms.setPermissionAdmin(isAdmin);
            perms.setPermissionUpload(permissions.contains("permissionUpload"));
            perms.setPermissionDownload(permissions.contains("permissionDownload"));
            perms.setPermissionEditMetadata(permissions.contains("permissionEditMetadata"));
            perms.setPermissionManageLibrary(permissions.contains("permissionManageLibrary"));
            perms.setPermissionEmailBook(permissions.contains("permissionEmailBook"));
            perms.setPermissionDeleteBook(permissions.contains("permissionDeleteBook"));
            perms.setPermissionAccessOpds(permissions.contains("permissionAccessOpds"));
            perms.setPermissionSyncKoreader(permissions.contains("permissionSyncKoreader"));
            perms.setPermissionSyncKobo(permissions.contains("permissionSyncKobo"));
            perms.setPermissionManageMetadataConfig(permissions.contains("permissionManageMetadataConfig"));
            perms.setPermissionAccessBookdrop(permissions.contains("permissionAccessBookdrop"));
            perms.setPermissionAccessLibraryStats(permissions.contains("permissionAccessLibraryStats"));
            perms.setPermissionAccessUserStats(permissions.contains("permissionAccessUserStats"));
            perms.setPermissionAccessTaskManager(permissions.contains("permissionAccessTaskManager"));
            perms.setPermissionManageGlobalPreferences(permissions.contains("permissionManageGlobalPreferences"));
            perms.setPermissionManageIcons(permissions.contains("permissionManageIcons"));
            perms.setPermissionManageFonts(permissions.contains("permissionManageFonts"));
            perms.setPermissionBulkAutoFetchMetadata(permissions.contains("permissionBulkAutoFetchMetadata"));
            perms.setPermissionBulkCustomFetchMetadata(permissions.contains("permissionBulkCustomFetchMetadata"));
            perms.setPermissionBulkEditMetadata(permissions.contains("permissionBulkEditMetadata"));
            perms.setPermissionBulkRegenerateCover(permissions.contains("permissionBulkRegenerateCover"));
            perms.setPermissionMoveOrganizeFiles(permissions.contains("permissionMoveOrganizeFiles"));
            perms.setPermissionBulkLockUnlockMetadata(permissions.contains("permissionBulkLockUnlockMetadata"));
            perms.setPermissionBulkResetBookloreReadProgress(permissions.contains("permissionBulkResetBookloreReadProgress"));
            perms.setPermissionBulkResetKoReaderReadProgress(permissions.contains("permissionBulkResetKoReaderReadProgress"));
            perms.setPermissionBulkResetBookReadStatus(permissions.contains("permissionBulkResetBookReadStatus"));
        } else {
            if (isAdmin) perms.setPermissionAdmin(true);
            if (permissions.contains("permissionUpload")) perms.setPermissionUpload(true);
            if (permissions.contains("permissionDownload")) perms.setPermissionDownload(true);
            if (permissions.contains("permissionEditMetadata")) perms.setPermissionEditMetadata(true);
            if (permissions.contains("permissionManageLibrary")) perms.setPermissionManageLibrary(true);
            if (permissions.contains("permissionEmailBook")) perms.setPermissionEmailBook(true);
            if (permissions.contains("permissionDeleteBook")) perms.setPermissionDeleteBook(true);
            if (permissions.contains("permissionAccessOpds")) perms.setPermissionAccessOpds(true);
            if (permissions.contains("permissionSyncKoreader")) perms.setPermissionSyncKoreader(true);
            if (permissions.contains("permissionSyncKobo")) perms.setPermissionSyncKobo(true);
            if (permissions.contains("permissionManageMetadataConfig")) perms.setPermissionManageMetadataConfig(true);
            if (permissions.contains("permissionAccessBookdrop")) perms.setPermissionAccessBookdrop(true);
            if (permissions.contains("permissionAccessLibraryStats")) perms.setPermissionAccessLibraryStats(true);
            if (permissions.contains("permissionAccessUserStats")) perms.setPermissionAccessUserStats(true);
            if (permissions.contains("permissionAccessTaskManager")) perms.setPermissionAccessTaskManager(true);
            if (permissions.contains("permissionManageGlobalPreferences")) perms.setPermissionManageGlobalPreferences(true);
            if (permissions.contains("permissionManageIcons")) perms.setPermissionManageIcons(true);
            if (permissions.contains("permissionManageFonts")) perms.setPermissionManageFonts(true);
            if (permissions.contains("permissionBulkAutoFetchMetadata")) perms.setPermissionBulkAutoFetchMetadata(true);
            if (permissions.contains("permissionBulkCustomFetchMetadata")) perms.setPermissionBulkCustomFetchMetadata(true);
            if (permissions.contains("permissionBulkEditMetadata")) perms.setPermissionBulkEditMetadata(true);
            if (permissions.contains("permissionBulkRegenerateCover")) perms.setPermissionBulkRegenerateCover(true);
            if (permissions.contains("permissionMoveOrganizeFiles")) perms.setPermissionMoveOrganizeFiles(true);
            if (permissions.contains("permissionBulkLockUnlockMetadata")) perms.setPermissionBulkLockUnlockMetadata(true);
            if (permissions.contains("permissionBulkResetBookloreReadProgress")) perms.setPermissionBulkResetBookloreReadProgress(true);
            if (permissions.contains("permissionBulkResetKoReaderReadProgress")) perms.setPermissionBulkResetKoReaderReadProgress(true);
            if (permissions.contains("permissionBulkResetBookReadStatus")) perms.setPermissionBulkResetBookReadStatus(true);
        }
    }
}
