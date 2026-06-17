package org.booklore.service.user;

import lombok.RequiredArgsConstructor;
import org.booklore.config.security.service.AuthenticationService;
import org.booklore.exception.ApiError;
import org.booklore.mapper.custom.BookLoreUserTransformer;
import org.booklore.model.dto.BookLoreUser;
import org.booklore.model.dto.request.ChangePasswordRequest;
import org.booklore.model.dto.request.ChangeUserPasswordRequest;
import org.booklore.model.dto.request.UpdateUserSettingRequest;
import org.booklore.model.dto.request.UserProfileUpdateRequest;
import org.booklore.model.dto.request.UserUpdateRequest;
import org.booklore.model.dto.settings.UserSettingKey;
import org.booklore.model.entity.BookLoreUserEntity;
import org.booklore.model.entity.LibraryEntity;
import org.booklore.model.entity.UserSettingEntity;
import org.booklore.model.enums.AuditAction;
import org.booklore.model.enums.UserPermission;
import org.booklore.repository.LibraryRepository;
import org.booklore.repository.UserRepository;
import org.booklore.service.audit.AuditService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class UserService {
    private static final Set<String> SUPPORTED_LOCALES = Set.of(
            "en", "es", "it", "de", "fr", "nl", "pl", "pt", "ru", "hr",
            "sv", "zh", "ja", "hu", "sl", "sk", "uk", "id", "da"
    );
    private static final Set<String> SUPPORTED_THEMES = Set.of(
            "grimmory", "cobalt", "ember", "crimson", "rose", "forest",
            "meadow", "teal", "lagoon", "violet", "fuchsia", "slate", "custom"
    );
    private static final Set<String> SUPPORTED_THEME_ACCENTS = Set.of(
            "red", "orange", "amber", "yellow", "lime", "green", "emerald", "teal",
            "cyan", "sky", "blue", "indigo", "violet", "purple", "fuchsia", "pink", "rose"
    );

    private final UserRepository userRepository;
    private final LibraryRepository libraryRepository;
    private final AuthenticationService authenticationService;
    private final PasswordEncoder passwordEncoder;
    private final ObjectMapper objectMapper;
    private final BookLoreUserTransformer bookLoreUserTransformer;
    private final AuditService auditService;

    @Transactional(readOnly = true)
    public List<BookLoreUser> getBookLoreUsers() {
        return userRepository.findAllWithDetails()
                .stream()
                .map(bookLoreUserTransformer::toDTO)
                .toList();
    }

    @Transactional
    public BookLoreUser updateUser(Long id, UserUpdateRequest updateRequest) {
        BookLoreUserEntity user = userRepository.findByIdWithDetails(id).orElseThrow(() -> ApiError.USER_NOT_FOUND.createException(id));
        user.setName(updateRequest.getName());
        user.setEmail(updateRequest.getEmail());

        boolean isTargetAdmin = updateRequest.getPermissions() == null ?
                user.getPermissions().isPermissionAdmin() :
                updateRequest.getPermissions().isAdmin();

        if (updateRequest.getPermissions() != null && getMyself().getPermissions().isAdmin()) {
            UserPermission.copyFromRequestToEntity(updateRequest.getPermissions(), user.getPermissions());
            auditService.log(AuditAction.PERMISSIONS_CHANGED, "User", id, "Changed permissions for user: " + user.getUsername());
        }

        if (updateRequest.getAssignedLibraries() == null) {
            throw ApiError.GENERIC_BAD_REQUEST.createException("Assigned Libraries is missing from request.");
        }

        Set<LibraryEntity> updatedLibraries = new HashSet<>();

        if (!updateRequest.getAssignedLibraries().isEmpty()) {
            updatedLibraries.addAll(libraryRepository.findAllById(updateRequest.getAssignedLibraries()));
        }

        if(!isTargetAdmin && updatedLibraries.isEmpty()){
            throw ApiError.GENERIC_BAD_REQUEST.createException("At least one library must be assigned.");
        }

        user.setLibraries(updatedLibraries);                  
        
        userRepository.save(user);
        auditService.log(AuditAction.USER_UPDATED, "User", id, "Updated user: " + user.getUsername());
        return bookLoreUserTransformer.toDTO(user);
    }

    @Transactional
    public BookLoreUser updateUserProfile(Long id, UserProfileUpdateRequest updateRequest) {
        BookLoreUserEntity user = userRepository.findByIdWithDetails(id).orElseThrow(() -> ApiError.USER_NOT_FOUND.createException(id));
        BookLoreUser currentUser = getMyself();
        boolean isAdmin = currentUser.getPermissions().isAdmin();

        if (!isAdmin && !currentUser.getId().equals(id)) {
            throw ApiError.GENERIC_UNAUTHORIZED.createException("You do not have permission to update this User");
        }

        if (updateRequest.getName() != null) {
            user.setName(updateRequest.getName());
        }

        if (updateRequest.getEmail() != null) {
            user.setEmail(updateRequest.getEmail());
        }

        if (updateRequest.getLocale() != null) {
            validateLocale(updateRequest.getLocale());
            user.setLocale(updateRequest.getLocale());
        }

        applyThemePreferences(user, updateRequest.getTheme(), updateRequest.getThemeAccent());
        if (updateRequest.getThemeSyncEnabled() != null) {
            user.setThemeSyncEnabled(updateRequest.getThemeSyncEnabled());
        }

        userRepository.save(user);
        auditService.log(AuditAction.USER_UPDATED, "User", id, "Updated user profile: " + user.getUsername());
        return bookLoreUserTransformer.toDTO(user);
    }

    @Transactional
    public void deleteUser(Long id) {
        BookLoreUserEntity userToDelete = userRepository.findById(id).orElseThrow(() -> ApiError.USER_NOT_FOUND.createException(id));
        BookLoreUser currentUser = authenticationService.getAuthenticatedUser();
        boolean isAdmin = currentUser.getPermissions().isAdmin();
        if (!isAdmin) {
            throw ApiError.GENERIC_UNAUTHORIZED.createException("You do not have permission to delete this User");
        }
        if (currentUser.getId().equals(userToDelete.getId())) {
            throw ApiError.SELF_DELETION_NOT_ALLOWED.createException();
        }
        userRepository.delete(userToDelete);
        auditService.log(AuditAction.USER_DELETED, "User", id, "Deleted user: " + userToDelete.getUsername());
    }

    @Transactional(readOnly = true)
    public BookLoreUser getBookLoreUser(Long id) {
        BookLoreUserEntity user = userRepository.findByIdWithDetails(id).orElseThrow(() -> ApiError.USER_NOT_FOUND.createException(id));
        return bookLoreUserTransformer.toDTO(user);
    }

    public BookLoreUser getMyself() {
        return authenticationService.getAuthenticatedUser();
    }

    @Transactional
    public void changePassword(ChangePasswordRequest changePasswordRequest) {
        BookLoreUser bookLoreUser = authenticationService.getAuthenticatedUser();

        BookLoreUserEntity bookLoreUserEntity = userRepository.findByIdWithPermissions(bookLoreUser.getId())
                .orElseThrow(() -> ApiError.USER_NOT_FOUND.createException(bookLoreUser.getId()));

        if (bookLoreUserEntity.getPermissions().isPermissionDemoUser()) {
            throw ApiError.DEMO_USER_PASSWORD_CHANGE_NOT_ALLOWED.createException();
        }

        if (!passwordEncoder.matches(changePasswordRequest.getCurrentPassword(), bookLoreUserEntity.getPasswordHash())) {
            throw ApiError.PASSWORD_INCORRECT.createException();
        }

        if (passwordEncoder.matches(changePasswordRequest.getNewPassword(), bookLoreUserEntity.getPasswordHash())) {
            throw ApiError.PASSWORD_SAME_AS_CURRENT.createException();
        }

        if (!meetsMinimumPasswordRequirements(changePasswordRequest.getNewPassword())) {
            throw ApiError.PASSWORD_TOO_SHORT.createException();
        }

        bookLoreUserEntity.setDefaultPassword(false);
        bookLoreUserEntity.setPasswordHash(passwordEncoder.encode(changePasswordRequest.getNewPassword()));
        userRepository.save(bookLoreUserEntity);
        auditService.log(AuditAction.PASSWORD_CHANGED, "User", bookLoreUser.getId(), "Password changed by user: " + bookLoreUser.getUsername());
    }

    @Transactional
    public void changeUserPassword(ChangeUserPasswordRequest request) {
        BookLoreUserEntity userEntity = userRepository.findByIdWithPermissions(request.getUserId()).orElseThrow(() -> ApiError.USER_NOT_FOUND.createException(request.getUserId()));
        if (!meetsMinimumPasswordRequirements(request.getNewPassword())) {
            throw ApiError.PASSWORD_TOO_SHORT.createException();
        }
        userEntity.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(userEntity);
        auditService.log(AuditAction.PASSWORD_CHANGED, "User", request.getUserId(), "Password changed for user: " + userEntity.getUsername());
    }

    @Transactional
    public void updateUserSetting(Long userId, UpdateUserSettingRequest request) {
        BookLoreUserEntity user = userRepository.findByIdWithSettings(userId).orElseThrow(() -> ApiError.USER_NOT_FOUND.createException(userId));

        String key = request.getKey();
        Object value = request.getValue();

        if (key == null || key.isBlank()) {
            throw ApiError.INVALID_INPUT.createException("Setting key cannot be null or blank.");
        }

        UserSettingKey settingKey;
        try {
            settingKey = UserSettingKey.fromDbKey(key);
        } catch (IllegalArgumentException e) {
            throw ApiError.INVALID_INPUT.createException("Unknown setting key: " + key);
        }

        UserSettingEntity setting = user.getSettings().stream()
                .filter(s -> s.getSettingKey().equals(key))
                .findFirst()
                .orElseGet(() -> {
                    UserSettingEntity newSetting = new UserSettingEntity();
                    newSetting.setUser(user);
                    newSetting.setSettingKey(key);
                    user.getSettings().add(newSetting);
                    return newSetting;
                });

        try {
            String serializedValue;
            if (settingKey.isJson()) {
                serializedValue = objectMapper.writeValueAsString(value);
            } else {
                serializedValue = value.toString();
            }
            setting.setSettingValue(serializedValue);
        } catch (Exception e) {
            throw ApiError.INVALID_INPUT.createException("Could not serialize setting value.");
        }

        userRepository.save(user);
    }

    private boolean meetsMinimumPasswordRequirements(String password) {
        return password != null && password.length() >= 8;
    }

    private void validateLocale(String locale) {
        if (!SUPPORTED_LOCALES.contains(locale)) {
            throw ApiError.INVALID_INPUT.createException("Unsupported locale: " + locale);
        }
    }

    private void validateTheme(String theme) {
        if (!SUPPORTED_THEMES.contains(theme)) {
            throw ApiError.INVALID_INPUT.createException("Unsupported theme: " + theme);
        }
    }

    private void applyThemePreferences(BookLoreUserEntity user, String theme, String themeAccent) {
        String nextTheme = theme != null ? theme : user.getTheme();
        validateTheme(nextTheme);

        if (!"custom".equals(nextTheme)) {
            if (themeAccent != null) {
                throw ApiError.INVALID_INPUT.createException("Theme accent can only be set when theme is custom.");
            }
            user.setTheme(nextTheme);
            user.setThemeAccent(null);
            return;
        }

        String nextThemeAccent = themeAccent != null ? themeAccent : user.getThemeAccent();
        if (nextThemeAccent == null) {
            throw ApiError.INVALID_INPUT.createException("Theme accent is required when theme is custom.");
        }

        validateThemeAccent(nextThemeAccent);
        user.setTheme(nextTheme);
        user.setThemeAccent(nextThemeAccent);
    }

    private void validateThemeAccent(String themeAccent) {
        if (!SUPPORTED_THEME_ACCENTS.contains(themeAccent)) {
            throw ApiError.INVALID_INPUT.createException("Unsupported theme accent: " + themeAccent);
        }
    }
}
