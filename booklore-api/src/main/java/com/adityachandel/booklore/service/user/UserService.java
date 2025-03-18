package com.adityachandel.booklore.service.user;

import com.adityachandel.booklore.config.security.AuthenticationService;
import com.adityachandel.booklore.exception.ApiError;
import com.adityachandel.booklore.mapper.BookLoreUserMapper;
import com.adityachandel.booklore.model.dto.request.ChangePasswordRequest;
import com.adityachandel.booklore.model.dto.request.ChangeUserPasswordRequest;
import com.adityachandel.booklore.model.dto.settings.BookPreferences;
import com.adityachandel.booklore.model.dto.BookLoreUser;
import com.adityachandel.booklore.model.dto.request.UserUpdateRequest;
import com.adityachandel.booklore.model.entity.BookLoreUserEntity;
import com.adityachandel.booklore.model.entity.LibraryEntity;
import com.adityachandel.booklore.repository.LibraryRepository;
import com.adityachandel.booklore.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final BookLoreUserMapper bookLoreUserMapper;
    private final LibraryRepository libraryRepository;
    private final AuthenticationService authenticationService;
    private final PasswordEncoder passwordEncoder;

    public List<BookLoreUser> getBookLoreUsers() {
        return userRepository.findAll()
                .stream()
                .map(bookLoreUserMapper::toDto)
                .collect(Collectors.toList());
    }

    public BookLoreUser updateUser(Long id, UserUpdateRequest updateRequest) {
        BookLoreUserEntity user = userRepository.findById(id).orElseThrow(() -> ApiError.USER_NOT_FOUND.createException(id));

        user.setName(updateRequest.getName());
        user.setEmail(updateRequest.getEmail());
        user.getPermissions().setPermissionUpload(updateRequest.getPermissions().isCanUpload());
        user.getPermissions().setPermissionDownload(updateRequest.getPermissions().isCanDownload());
        user.getPermissions().setPermissionEditMetadata(updateRequest.getPermissions().isCanEditMetadata());

        List<Long> libraryIds = updateRequest.getAssignedLibraries();
        if (libraryIds != null) {
            List<LibraryEntity> updatedLibraries = libraryRepository.findAllById(libraryIds);
            user.setLibraries(updatedLibraries);
        }

        userRepository.save(user);
        return bookLoreUserMapper.toDto(user);
    }

    public void deleteUser(Long id) {
        BookLoreUserEntity user = userRepository.findById(id).orElseThrow(() -> ApiError.USER_NOT_FOUND.createException(id));

        if (user.getPermissions().isPermissionAdmin()) {
            throw ApiError.CANNOT_DELETE_ADMIN.createException();
        }

        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (principal instanceof UserDetails userDetails) {
            if (userDetails.getAuthorities().stream().noneMatch(auth -> auth.getAuthority().equals("ROLE_ADMIN"))) {
                throw ApiError.UNAUTHORIZED.createException();
            }
        }

        userRepository.delete(user);
    }

    public BookLoreUser getBookLoreUser(Long id) {
        BookLoreUserEntity user = userRepository.findById(id).orElseThrow(() -> ApiError.USER_NOT_FOUND.createException(id));
        return bookLoreUserMapper.toDto(user);
    }


    public void updateBookPreferences(long userId, BookPreferences bookPreferences) {
        BookLoreUserEntity user = userRepository.findById(userId).orElseThrow(() -> ApiError.USER_NOT_FOUND.createException(userId));
        user.setBookPreferences(bookPreferences);
        userRepository.save(user);
    }

    public BookLoreUser getMyself() {
        return authenticationService.getAuthenticatedUser();
    }

    public void changePassword(ChangePasswordRequest changePasswordRequest) {
        BookLoreUser bookLoreUser = authenticationService.getAuthenticatedUser();
        BookLoreUserEntity bookLoreUserEntity = userRepository.findById(bookLoreUser.getId())
                .orElseThrow(() -> ApiError.USER_NOT_FOUND.createException(bookLoreUser.getId()));

        if (!passwordEncoder.matches(changePasswordRequest.getCurrentPassword(), bookLoreUserEntity.getPasswordHash())) {
            throw ApiError.PASSWORD_INCORRECT.createException();
        }

        if (passwordEncoder.matches(changePasswordRequest.getNewPassword(), bookLoreUserEntity.getPasswordHash())) {
            throw ApiError.PASSWORD_SAME_AS_CURRENT.createException();
        }

        if (!isValidPassword(changePasswordRequest.getNewPassword())) {
            throw ApiError.PASSWORD_TOO_SHORT.createException();
        }

        bookLoreUserEntity.setDefaultPassword(false);
        bookLoreUserEntity.setPasswordHash(passwordEncoder.encode(changePasswordRequest.getNewPassword()));
        userRepository.save(bookLoreUserEntity);
    }

    public void changeUserPassword(ChangeUserPasswordRequest request) {
        BookLoreUserEntity userEntity = userRepository.findById(request.getUserId()).orElseThrow(() -> ApiError.USER_NOT_FOUND.createException(request.getUserId()));
        if (!isValidPassword(request.getNewPassword())) {
            throw ApiError.PASSWORD_TOO_SHORT.createException();
        }
        userEntity.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(userEntity);
    }

    private boolean isValidPassword(String password) {
        return password != null && password.length() >= 6;
    }
}
