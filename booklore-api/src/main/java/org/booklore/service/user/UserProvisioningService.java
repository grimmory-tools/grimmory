package org.booklore.service.user;

import org.booklore.config.AppProperties;
import org.booklore.exception.ApiError;
import org.booklore.model.dto.UserCreateRequest;
import org.booklore.model.dto.request.InitialUserRequest;
import org.booklore.model.dto.settings.OidcAutoProvisionDetails;
import org.booklore.model.entity.*;
import org.booklore.model.enums.*;
import org.booklore.repository.LibraryRepository;
import org.booklore.repository.UserRepository;
import org.booklore.service.appsettings.AppSettingService;
import jakarta.transaction.Transactional;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.RandomStringUtils;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Pattern;
import org.booklore.model.enums.AuditAction;
import org.booklore.service.audit.AuditService;

@Slf4j
@Service
@AllArgsConstructor
public class UserProvisioningService {

    private final AppProperties appProperties;
    private final UserRepository userRepository;
    private final LibraryRepository libraryRepository;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    private final UserDefaultsService userDefaultsService;
    private final AppSettingService appSettingService;
    private final AuditService auditService;

    public boolean isInitialUserAlreadyProvisioned() {
        return userRepository.count() > 0;
    }

    @Transactional
    public void provisionInitialUser(InitialUserRequest request) {
        BookLoreUserEntity user = new BookLoreUserEntity();
        user.setUsername(request.getUsername());
        user.setEmail(request.getEmail());
        user.setName(request.getName());
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        user.setDefaultPassword(false);
        user.setProvisioningMethod(ProvisioningMethod.LOCAL);

        UserPermissionsEntity perms = new UserPermissionsEntity();
        perms.setPermissionAdmin(true);
        perms.setPermissionUpload(true);
        perms.setPermissionDownload(true);
        perms.setPermissionEditMetadata(true);
        perms.setPermissionManageLibrary(true);
        perms.setPermissionEmailBook(true);
        perms.setPermissionDeleteBook(true);
        perms.setPermissionAccessOpds(true);
        perms.setPermissionSyncKoreader(true);
        perms.setPermissionSyncKobo(true);
        perms.setPermissionManageMetadataConfig(true);
        perms.setPermissionAccessBookdrop(true);
        perms.setPermissionAccessLibraryStats(true);
        perms.setPermissionAccessUserStats(true);
        perms.setPermissionAccessTaskManager(true);
        perms.setPermissionManageGlobalPreferences(true);
        perms.setPermissionManageIcons(true);
        perms.setPermissionManageFonts(true);
        perms.setPermissionBulkAutoFetchMetadata(true);
        perms.setPermissionBulkCustomFetchMetadata(true);
        perms.setPermissionBulkEditMetadata(true);
        perms.setPermissionBulkRegenerateCover(true);
        perms.setPermissionMoveOrganizeFiles(true);
        perms.setPermissionBulkLockUnlockMetadata(true);
        perms.setPermissionBulkResetBookloreReadProgress(true);
        perms.setPermissionBulkResetKoReaderReadProgress(true);
        perms.setPermissionBulkResetBookReadStatus(true);

        user.setPermissions(perms);
        createUser(user);
    }

    @Transactional
    public void provisionInternalUser(UserCreateRequest request) {
        Optional<BookLoreUserEntity> existingUser = userRepository.findByUsername(request.getUsername());
        if (existingUser.isPresent()) {
            throw ApiError.USERNAME_ALREADY_TAKEN.createException(request.getUsername());
        }

        BookLoreUserEntity user = new BookLoreUserEntity();
        user.setUsername(request.getUsername());
        user.setDefaultPassword(true);
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        user.setName(request.getName());
        user.setEmail(request.getEmail());
        user.setProvisioningMethod(ProvisioningMethod.LOCAL);

        UserPermissionsEntity permissions = new UserPermissionsEntity();
        permissions.setUser(user);
        permissions.setPermissionUpload(request.isPermissionUpload());
        permissions.setPermissionDownload(request.isPermissionDownload());
        permissions.setPermissionEditMetadata(request.isPermissionEditMetadata());
        permissions.setPermissionManageLibrary(request.isPermissionManageLibrary());
        permissions.setPermissionEmailBook(request.isPermissionEmailBook());
        permissions.setPermissionDeleteBook(request.isPermissionDeleteBook());
        permissions.setPermissionAccessOpds(request.isPermissionAccessOpds());
        permissions.setPermissionSyncKoreader(request.isPermissionSyncKoreader());
        permissions.setPermissionSyncKobo(request.isPermissionSyncKobo());
        permissions.setPermissionAdmin(request.isPermissionAdmin());
        permissions.setPermissionManageMetadataConfig(request.isPermissionManageMetadataConfig());
        permissions.setPermissionAccessBookdrop(request.isPermissionAccessBookdrop());
        permissions.setPermissionAccessLibraryStats(request.isPermissionAccessLibraryStats());
        permissions.setPermissionAccessUserStats(request.isPermissionAccessUserStats());
        permissions.setPermissionAccessTaskManager(request.isPermissionAccessTaskManager());
        permissions.setPermissionManageGlobalPreferences(request.isPermissionManageGlobalPreferences());
        permissions.setPermissionManageIcons(request.isPermissionManageIcons());
        permissions.setPermissionManageFonts(request.isPermissionManageFonts());
        permissions.setPermissionBulkAutoFetchMetadata(request.isPermissionBulkAutoFetchMetadata());
        permissions.setPermissionBulkCustomFetchMetadata(request.isPermissionBulkCustomFetchMetadata());
        permissions.setPermissionBulkEditMetadata(request.isPermissionBulkEditMetadata());
        permissions.setPermissionBulkRegenerateCover(request.isPermissionBulkRegenerateCover());
        permissions.setPermissionMoveOrganizeFiles(request.isPermissionMoveOrganizeFiles());
        permissions.setPermissionBulkLockUnlockMetadata(request.isPermissionBulkLockUnlockMetadata());
        permissions.setPermissionBulkResetBookloreReadProgress(request.isPermissionBulkResetBookloreReadProgress());
        permissions.setPermissionBulkResetKoReaderReadProgress(request.isPermissionBulkResetKoReaderReadProgress());
        permissions.setPermissionBulkResetBookReadStatus(request.isPermissionBulkResetBookReadStatus());
        user.setPermissions(permissions);

        if (request.getSelectedLibraries() != null && !request.getSelectedLibraries().isEmpty()) {
            List<LibraryEntity> libraries = libraryRepository.findAllById(request.getSelectedLibraries());
            user.setLibraries(new ArrayList<>(libraries));
        }

        createUser(user);
    }

    @Transactional
    public BookLoreUserEntity provisionOidcUser(String username, String email, String name, OidcAutoProvisionDetails oidcAutoProvisionDetails) {
        BookLoreUserEntity user = new BookLoreUserEntity();
        user.setUsername(username);
        user.setEmail(email);
        user.setName(name);
        user.setDefaultPassword(false);
        user.setPasswordHash("OIDC_USER_" + UUID.randomUUID());
        user.setProvisioningMethod(ProvisioningMethod.OIDC);

        UserPermissionsEntity perms = new UserPermissionsEntity();
        List<String> defaultPermissions = oidcAutoProvisionDetails.getDefaultPermissions();
        if (defaultPermissions != null) {
            perms.setPermissionUpload(defaultPermissions.contains("permissionUpload"));
            perms.setPermissionDownload(defaultPermissions.contains("permissionDownload"));
            perms.setPermissionEditMetadata(defaultPermissions.contains("permissionEditMetadata"));
            perms.setPermissionManageLibrary(defaultPermissions.contains("permissionManageLibrary"));
            perms.setPermissionEmailBook(defaultPermissions.contains("permissionEmailBook"));
            perms.setPermissionDeleteBook(defaultPermissions.contains("permissionDeleteBook"));
            perms.setPermissionAccessOpds(defaultPermissions.contains("permissionAccessOpds"));
            perms.setPermissionSyncKoreader(defaultPermissions.contains("permissionSyncKoreader"));
            perms.setPermissionSyncKobo(defaultPermissions.contains("permissionSyncKobo"));
            perms.setPermissionManageMetadataConfig(defaultPermissions.contains("permissionManageMetadataConfig"));
            perms.setPermissionAccessBookdrop(defaultPermissions.contains("permissionAccessBookdrop"));
            perms.setPermissionAccessLibraryStats(defaultPermissions.contains("permissionAccessLibraryStats"));
            perms.setPermissionAccessUserStats(defaultPermissions.contains("permissionAccessUserStats"));
            perms.setPermissionAccessTaskManager(defaultPermissions.contains("permissionAccessTaskManager"));
            perms.setPermissionManageGlobalPreferences(defaultPermissions.contains("permissionManageGlobalPreferences"));
            perms.setPermissionManageIcons(defaultPermissions.contains("permissionManageIcons"));
            perms.setPermissionManageFonts(defaultPermissions.contains("permissionManageFonts"));
        }
        user.setPermissions(perms);

        List<Long> defaultLibraryIds = oidcAutoProvisionDetails.getDefaultLibraryIds();
        if (defaultLibraryIds != null && !defaultLibraryIds.isEmpty()) {
            List<LibraryEntity> libraries = libraryRepository.findAllById(defaultLibraryIds);
            user.setLibraries(new ArrayList<>(libraries));
        }

        return createUser(user);
    }

    /**
     * Create and persist a remote-provisioned user based on incoming headers.
     * This is the preferred (non-deprecated) entry point for remote provisioning.
     */
    @Transactional
    public BookLoreUserEntity provisionRemoteUserFromHeaders(String name, String username, String email, String groups) {
        boolean isAdmin = false;
        if (groups != null && appProperties.getRemoteAuth().getAdminGroup() != null) {
            String groupsContent = groups.trim();
            if (groupsContent.length() >= 2 && groupsContent.charAt(0) == '[' && groupsContent.charAt(groupsContent.length() - 1) == ']') {
                groupsContent = groupsContent.substring(1, groupsContent.length() - 1);
            }
            String delimiter = appProperties.getRemoteAuth().getGroupsDelimiter();
            Pattern groupsPattern = Pattern.compile(delimiter);
            List<String> groupsList = Arrays.asList(groupsPattern.split(groupsContent));
            isAdmin = groupsList.contains(appProperties.getRemoteAuth().getAdminGroup());
            log.debug("Remote-Auth: user {} will be admin: {}", username, isAdmin);
        }

        BookLoreUserEntity user = new BookLoreUserEntity();
        user.setUsername(username);
        user.setName(name != null ? name : username);
        user.setEmail(email);
        user.setDefaultPassword(false);
        user.setProvisioningMethod(ProvisioningMethod.REMOTE);
        user.setPasswordHash("RemoteUser_" + RandomStringUtils.secure().nextAlphanumeric(32));

        OidcAutoProvisionDetails oidcAutoProvisionDetails = appSettingService.getAppSettings().getOidcAutoProvisionDetails();

        UserPermissionsEntity permissions = new UserPermissionsEntity();
        permissions.setUser(user);

        if (oidcAutoProvisionDetails != null && oidcAutoProvisionDetails.getDefaultPermissions() != null) {
            List<String> defaultPermissions = oidcAutoProvisionDetails.getDefaultPermissions();
            permissions.setPermissionUpload(defaultPermissions.contains("permissionUpload"));
            permissions.setPermissionDownload(defaultPermissions.contains("permissionDownload"));
            permissions.setPermissionEditMetadata(defaultPermissions.contains("permissionEditMetadata"));
            permissions.setPermissionManageLibrary(defaultPermissions.contains("permissionManageLibrary"));
            permissions.setPermissionEmailBook(defaultPermissions.contains("permissionEmailBook"));
            permissions.setPermissionDeleteBook(defaultPermissions.contains("permissionDeleteBook"));
            permissions.setPermissionAccessOpds(defaultPermissions.contains("permissionAccessOpds"));
            permissions.setPermissionSyncKoreader(defaultPermissions.contains("permissionSyncKoreader"));
            permissions.setPermissionSyncKobo(defaultPermissions.contains("permissionSyncKobo"));
            permissions.setPermissionManageMetadataConfig(defaultPermissions.contains("permissionManageMetadataConfig"));
            permissions.setPermissionAccessBookdrop(defaultPermissions.contains("permissionAccessBookdrop"));
            permissions.setPermissionAccessLibraryStats(defaultPermissions.contains("permissionAccessLibraryStats"));
            permissions.setPermissionAccessUserStats(defaultPermissions.contains("permissionAccessUserStats"));
            permissions.setPermissionAccessTaskManager(defaultPermissions.contains("permissionAccessTaskManager"));
            permissions.setPermissionManageGlobalPreferences(defaultPermissions.contains("permissionManageGlobalPreferences"));
            permissions.setPermissionManageIcons(defaultPermissions.contains("permissionManageIcons"));
            permissions.setPermissionManageFonts(defaultPermissions.contains("permissionManageFonts"));
        } else {
            permissions.setPermissionUpload(false);
            permissions.setPermissionDownload(false);
            permissions.setPermissionEditMetadata(false);
            permissions.setPermissionManageLibrary(false);
            permissions.setPermissionEmailBook(false);
            permissions.setPermissionAccessOpds(false);
            permissions.setPermissionDeleteBook(false);
            permissions.setPermissionSyncKoreader(false);
            permissions.setPermissionSyncKobo(false);
            permissions.setPermissionManageMetadataConfig(false);
            permissions.setPermissionAccessBookdrop(false);
            permissions.setPermissionAccessLibraryStats(false);
            permissions.setPermissionAccessUserStats(false);
            permissions.setPermissionAccessTaskManager(false);
            permissions.setPermissionManageGlobalPreferences(false);
            permissions.setPermissionManageIcons(false);
            permissions.setPermissionManageFonts(false);
        }

        permissions.setPermissionAdmin(isAdmin);
        user.setPermissions(permissions);

        if (isAdmin) {
            List<LibraryEntity> libraries = libraryRepository.findAll();
            user.setLibraries(new ArrayList<>(libraries));
        } else if (oidcAutoProvisionDetails != null && oidcAutoProvisionDetails.getDefaultLibraryIds() != null && !oidcAutoProvisionDetails.getDefaultLibraryIds().isEmpty()) {
            List<LibraryEntity> libraries = libraryRepository.findAllById(oidcAutoProvisionDetails.getDefaultLibraryIds());
            user.setLibraries(new ArrayList<>(libraries));
        }

        return createUser(user);
    }

    protected BookLoreUserEntity createUser(BookLoreUserEntity user) {
        BookLoreUserEntity save = userRepository.save(user);
        userDefaultsService.addDefaultShelves(save);
        userDefaultsService.addDefaultSettings(save);
        auditService.log(AuditAction.USER_CREATED, "User", save.getId(), "Created user: " + save.getUsername());
        return save;
    }
}
