package org.booklore.service.user;

import lombok.RequiredArgsConstructor;
import org.booklore.config.security.service.AuthenticationService;
import org.booklore.exception.ApiError;
import org.booklore.mapper.custom.BookLoreUserTransformer;
import org.booklore.model.dto.BookLoreUser;
import org.booklore.model.dto.request.ChangePasswordRequest;
import org.booklore.model.dto.request.ChangeUserPasswordRequest;
import org.booklore.model.dto.request.UpdateUserSettingRequest;
import org.booklore.model.dto.request.UserUpdateRequest;
import org.booklore.model.dto.settings.UserSettingKey;
import org.booklore.model.entity.BookLoreUserEntity;
import org.booklore.model.entity.LibraryEntity;
import org.booklore.model.entity.UserSettingEntity;
import org.booklore.model.enums.UserPermission;
import org.booklore.repository.LibraryRepository;
import org.booklore.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.stream.Collectors;
import org.booklore.model.enums.AuditAction;
import org.booklore.service.audit.AuditService;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final LibraryRepository libraryRepository;
    private final AuthenticationService authenticationService;
    private final PasswordEncoder passwordEncoder;
    private final ObjectMapper objectMapper;
    private final BookLoreUserTransformer bookLoreUserTransformer;
    private final AuditService auditService;

    public List<BookLoreUser> getBookLoreUsers() {
        return userRepository.findAll()
                .stream()
                .map(bookLoreUserTransformer::toDTO)
                .collect(Collectors.toList());
    }

    public BookLoreUser updateUser(Long id, UserUpdateRequest updateRequest) {
        BookLoreUserEntity user = userRepository.findById(id).orElseThrow(() -> ApiError.USER_NOT_FOUND.createException(id));
        user.setName(updateRequest.getName());
        user.setEmail(updateRequest.getEmail());

        if (updateRequest.getPermissions() != null && getMyself().getPermissions().isAdmin()) {
            UserPermission.copyFromRequestToEntity(updateRequest.getPermissions(), user.getPermissions());
            auditService.log(AuditAction.PERMISSIONS_CHANGED, "User", id, "Changed permissions for user: " + user.getUsername());
        }

        if (updateRequest.getAssignedLibraries() != null && getMyself().getPermissions().isAdmin()) {
            List<Long> libraryIds = updateRequest.getAssignedLibraries();
            List<LibraryEntity> updatedLibraries = libraryRepository.findAllById(libraryIds);
            user.setLibraries(updatedLibraries);
        }

        userRepository.save(user);
        auditService.log(AuditAction.USER_UPDATED, "User", id, "Updated user: " + user.getUsername());
        return bookLoreUserTransformer.toDTO(user);
    }

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

    public BookLoreUser getBookLoreUser(Long id) {
        BookLoreUserEntity user = userRepository.findById(id).orElseThrow(() -> ApiError.USER_NOT_FOUND.createException(id));
        return bookLoreUserTransformer.toDTO(user);
    }

    public BookLoreUser getMyself() {
        return authenticationService.getAuthenticatedUser();
    }

    public void changePassword(ChangePasswordRequest changePasswordRequest) {
        BookLoreUser bookLoreUser = authenticationService.getAuthenticatedUser();

        BookLoreUserEntity bookLoreUserEntity = userRepository.findById(bookLoreUser.getId())
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

    public void changeUserPassword(ChangeUserPasswordRequest request) {
        BookLoreUserEntity userEntity = userRepository.findById(request.getUserId()).orElseThrow(() -> ApiError.USER_NOT_FOUND.createException(request.getUserId()));
        if (!meetsMinimumPasswordRequirements(request.getNewPassword())) {
            throw ApiError.PASSWORD_TOO_SHORT.createException();
        }
        userEntity.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(userEntity);
        auditService.log(AuditAction.PASSWORD_CHANGED, "User", request.getUserId(), "Password changed for user: " + userEntity.getUsername());
    }

    public void updateUserSetting(Long userId, UpdateUserSettingRequest request) {
        BookLoreUserEntity user = userRepository.findById(userId).orElseThrow(() -> ApiError.USER_NOT_FOUND.createException(userId));

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
        return password != null && password.length() >= 6;
    }
}
