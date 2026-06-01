package org.booklore.service.user;

import org.booklore.config.security.service.AuthenticationService;
import org.booklore.exception.APIException;
import org.booklore.mapper.custom.BookLoreUserTransformer;
import org.booklore.model.dto.BookLoreUser;
import org.booklore.model.dto.request.UserProfileUpdateRequest;
import org.booklore.model.entity.BookLoreUserEntity;
import org.booklore.model.entity.UserPermissionsEntity;
import org.booklore.repository.LibraryRepository;
import org.booklore.repository.UserRepository;
import org.booklore.service.audit.AuditService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import tools.jackson.databind.ObjectMapper;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private LibraryRepository libraryRepository;

    @Mock
    private AuthenticationService authenticationService;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private BookLoreUserTransformer bookLoreUserTransformer;

    @Mock
    private AuditService auditService;

    @InjectMocks
    private UserService userService;

    @Test
    void updateUserProfile_allowsSelfToUpdateLocaleAndTheme() {
        BookLoreUserEntity user = userEntity(1L);
        UserProfileUpdateRequest request = new UserProfileUpdateRequest();
        request.setLocale("de");
        request.setTheme("custom");
        request.setThemeAccent("teal");

        when(userRepository.findByIdWithDetails(1L)).thenReturn(Optional.of(user));
        when(authenticationService.getAuthenticatedUser()).thenReturn(currentUser(1L, false));
        when(userRepository.save(user)).thenReturn(user);
        when(bookLoreUserTransformer.toDTO(user)).thenAnswer(invocation -> currentUser(1L, false));

        userService.updateUserProfile(1L, request);

        assertThat(user.getLocale()).isEqualTo("de");
        assertThat(user.getTheme()).isEqualTo("custom");
        assertThat(user.getThemeAccent()).isEqualTo("teal");
        verify(userRepository).save(user);
    }

    @Test
    void updateUserProfile_clearsThemeAccentWhenThemeIsNotCustom() {
        BookLoreUserEntity user = userEntity(1L);
        user.setTheme("custom");
        user.setThemeAccent("teal");
        UserProfileUpdateRequest request = new UserProfileUpdateRequest();
        request.setTheme("cobalt");

        when(userRepository.findByIdWithDetails(1L)).thenReturn(Optional.of(user));
        when(authenticationService.getAuthenticatedUser()).thenReturn(currentUser(1L, false));
        when(userRepository.save(user)).thenReturn(user);
        when(bookLoreUserTransformer.toDTO(user)).thenAnswer(invocation -> currentUser(1L, false));

        userService.updateUserProfile(1L, request);

        assertThat(user.getTheme()).isEqualTo("cobalt");
        assertThat(user.getThemeAccent()).isNull();
        verify(userRepository).save(user);
    }

    @Test
    void updateUserProfile_rejectsUnsupportedLocale() {
        BookLoreUserEntity user = userEntity(1L);
        UserProfileUpdateRequest request = new UserProfileUpdateRequest();
        request.setLocale("zz");

        when(userRepository.findByIdWithDetails(1L)).thenReturn(Optional.of(user));
        when(authenticationService.getAuthenticatedUser()).thenReturn(currentUser(1L, false));

        assertThatThrownBy(() -> userService.updateUserProfile(1L, request))
                .isInstanceOf(APIException.class)
                .hasMessageContaining("Unsupported locale");

        verify(userRepository, never()).save(any());
    }

    @Test
    void updateUserProfile_rejectsUnsupportedTheme() {
        BookLoreUserEntity user = userEntity(1L);
        UserProfileUpdateRequest request = new UserProfileUpdateRequest();
        request.setTheme("neon");

        when(userRepository.findByIdWithDetails(1L)).thenReturn(Optional.of(user));
        when(authenticationService.getAuthenticatedUser()).thenReturn(currentUser(1L, false));

        assertThatThrownBy(() -> userService.updateUserProfile(1L, request))
                .isInstanceOf(APIException.class)
                .hasMessageContaining("Unsupported theme");

        verify(userRepository, never()).save(any());
    }

    @Test
    void updateUserProfile_rejectsUnsupportedThemeAccent() {
        BookLoreUserEntity user = userEntity(1L);
        user.setTheme("custom");
        user.setThemeAccent("teal");
        UserProfileUpdateRequest request = new UserProfileUpdateRequest();
        request.setThemeAccent("chartreuse");

        when(userRepository.findByIdWithDetails(1L)).thenReturn(Optional.of(user));
        when(authenticationService.getAuthenticatedUser()).thenReturn(currentUser(1L, false));

        assertThatThrownBy(() -> userService.updateUserProfile(1L, request))
                .isInstanceOf(APIException.class)
                .hasMessageContaining("Unsupported theme accent");

        verify(userRepository, never()).save(any());
    }

    @Test
    void updateUserProfile_rejectsThemeAccentWhenThemeIsNotCustom() {
        BookLoreUserEntity user = userEntity(1L);
        UserProfileUpdateRequest request = new UserProfileUpdateRequest();
        request.setThemeAccent("teal");

        when(userRepository.findByIdWithDetails(1L)).thenReturn(Optional.of(user));
        when(authenticationService.getAuthenticatedUser()).thenReturn(currentUser(1L, false));

        assertThatThrownBy(() -> userService.updateUserProfile(1L, request))
                .isInstanceOf(APIException.class)
                .hasMessageContaining("Theme accent can only be set");

        verify(userRepository, never()).save(any());
    }

    @Test
    void updateUserProfile_rejectsCustomThemeWithoutThemeAccent() {
        BookLoreUserEntity user = userEntity(1L);
        UserProfileUpdateRequest request = new UserProfileUpdateRequest();
        request.setTheme("custom");

        when(userRepository.findByIdWithDetails(1L)).thenReturn(Optional.of(user));
        when(authenticationService.getAuthenticatedUser()).thenReturn(currentUser(1L, false));

        assertThatThrownBy(() -> userService.updateUserProfile(1L, request))
                .isInstanceOf(APIException.class)
                .hasMessageContaining("Theme accent is required");

        verify(userRepository, never()).save(any());
    }

    @Test
    void updateUserProfile_rejectsNonAdminUpdatingAnotherUser() {
        BookLoreUserEntity user = userEntity(1L);
        UserProfileUpdateRequest request = new UserProfileUpdateRequest();
        request.setLocale("de");

        when(userRepository.findByIdWithDetails(1L)).thenReturn(Optional.of(user));
        when(authenticationService.getAuthenticatedUser()).thenReturn(currentUser(2L, false));

        assertThatThrownBy(() -> userService.updateUserProfile(1L, request))
                .isInstanceOf(APIException.class)
                .hasMessageContaining("permission to update this User");

        verify(userRepository, never()).save(any());
    }

    private BookLoreUserEntity userEntity(Long id) {
        BookLoreUserEntity user = new BookLoreUserEntity();
        user.setId(id);
        user.setUsername("admin");
        user.setName("Admin");
        user.setLocale("en");
        user.setTheme("grimmory");
        user.setPermissions(new UserPermissionsEntity());
        return user;
    }

    private BookLoreUser currentUser(Long id, boolean admin) {
        BookLoreUser.UserPermissions permissions = new BookLoreUser.UserPermissions();
        permissions.setAdmin(admin);
        return BookLoreUser.builder()
                .id(id)
                .username("admin")
                .name("Admin")
                .locale("en")
                .theme("grimmory")
                .permissions(permissions)
                .build();
    }
}
