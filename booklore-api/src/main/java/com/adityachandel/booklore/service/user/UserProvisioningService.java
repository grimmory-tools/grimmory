package com.adityachandel.booklore.service.user;

import com.adityachandel.booklore.config.AppProperties;
import com.adityachandel.booklore.exception.ApiError;
import com.adityachandel.booklore.model.dto.UserCreateRequest;
import com.adityachandel.booklore.model.dto.request.InitialUserRequest;
import com.adityachandel.booklore.model.dto.settings.OidcAutoProvisionDetails;
import com.adityachandel.booklore.model.entity.*;
import com.adityachandel.booklore.model.enums.*;
import com.adityachandel.booklore.repository.LibraryRepository;
import com.adityachandel.booklore.repository.UserRepository;
import com.adityachandel.booklore.service.appsettings.AppSettingService;
import jakarta.transaction.Transactional;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.RandomStringUtils;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.*;

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
        perms.setPermissionManipulateLibrary(true);
        perms.setPermissionEmailBook(true);
        perms.setPermissionDeleteBook(true);
        perms.setPermissionAccessOpds(true);
        perms.setPermissionSyncKoreader(true);
        perms.setPermissionSyncKobo(true);

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
        permissions.setPermissionManipulateLibrary(request.isPermissionManipulateLibrary());
        permissions.setPermissionEmailBook(request.isPermissionEmailBook());
        permissions.setPermissionDeleteBook(request.isPermissionDeleteBook());
        permissions.setPermissionAccessOpds(request.isPermissionAccessOpds());
        permissions.setPermissionSyncKoreader(request.isPermissionSyncKoreader());
        permissions.setPermissionSyncKobo(request.isPermissionSyncKobo());
        permissions.setPermissionAdmin(request.isPermissionAdmin());
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
            perms.setPermissionManipulateLibrary(defaultPermissions.contains("permissionManipulateLibrary"));
            perms.setPermissionEmailBook(defaultPermissions.contains("permissionEmailBook"));
            perms.setPermissionDeleteBook(defaultPermissions.contains("permissionDeleteBook"));
            perms.setPermissionAccessOpds(defaultPermissions.contains("permissionAccessOpds"));
            perms.setPermissionSyncKoreader(defaultPermissions.contains("permissionSyncKoreader"));
            perms.setPermissionSyncKobo(defaultPermissions.contains("permissionSyncKobo"));
        }
        user.setPermissions(perms);

        List<Long> defaultLibraryIds = oidcAutoProvisionDetails.getDefaultLibraryIds();
        if (defaultLibraryIds != null && !defaultLibraryIds.isEmpty()) {
            List<LibraryEntity> libraries = libraryRepository.findAllById(defaultLibraryIds);
            user.setLibraries(new ArrayList<>(libraries));
        }

        return createUser(user);
    }

    @Deprecated
    @Transactional
    public BookLoreUserEntity provisionRemoteUser(String name, String username, String email, String groups) {
        boolean isAdmin = false;
        if (groups != null && appProperties.getRemoteAuth().getAdminGroup() != null) {
            String groupsContent = groups.trim();
            if (groupsContent.startsWith("[") && groupsContent.endsWith("]")) {
                groupsContent = groupsContent.substring(1, groupsContent.length() - 1);
            }
            List<String> groupsList = Arrays.asList(groupsContent.split("\\s+"));
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
            permissions.setPermissionManipulateLibrary(defaultPermissions.contains("permissionManipulateLibrary"));
            permissions.setPermissionEmailBook(defaultPermissions.contains("permissionEmailBook"));
            permissions.setPermissionDeleteBook(defaultPermissions.contains("permissionDeleteBook"));
            permissions.setPermissionAccessOpds(defaultPermissions.contains("permissionAccessOpds"));
            permissions.setPermissionSyncKoreader(defaultPermissions.contains("permissionSyncKoreader"));
            permissions.setPermissionSyncKobo(defaultPermissions.contains("permissionSyncKobo"));
        } else {
            permissions.setPermissionUpload(false);
            permissions.setPermissionDownload(false);
            permissions.setPermissionEditMetadata(false);
            permissions.setPermissionManipulateLibrary(false);
            permissions.setPermissionEmailBook(false);
            permissions.setPermissionAccessOpds(false);
            permissions.setPermissionDeleteBook(false);
            permissions.setPermissionSyncKoreader(false);
            permissions.setPermissionSyncKobo(false);
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
        user = userRepository.save(user);
        userDefaultsService.addDefaultShelves(user);
        userDefaultsService.addDefaultSettings(user);
        return user;
    }
}
