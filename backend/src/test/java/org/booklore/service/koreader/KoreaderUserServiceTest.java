package org.booklore.service.koreader;

import org.booklore.config.security.service.AuthenticationService;
import org.booklore.exception.APIException;
import org.booklore.mapper.KoreaderUserMapper;
import org.booklore.model.dto.BookLoreUser;
import org.booklore.model.dto.KoreaderUser;
import org.booklore.model.dto.settings.AppSettings;
import org.booklore.model.dto.settings.KoreaderSyncSettings;
import org.booklore.model.entity.BookLoreUserEntity;
import org.booklore.model.entity.KoreaderUserEntity;
import org.booklore.repository.KoreaderUserRepository;
import org.booklore.repository.UserRepository;
import org.booklore.service.appsettings.AppSettingService;
import org.booklore.util.Md5Util;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class KoreaderUserServiceTest {

    @Mock private AuthenticationService authService;
    @Mock private UserRepository userRepository;
    @Mock private KoreaderUserRepository koreaderUserRepository;
    @Mock private KoreaderUserMapper koreaderUserMapper;
    @Mock private AppSettingService appSettingService;
    @Mock private BookLoreUser actor;

    @InjectMocks private KoreaderUserService service;

    private void externalMode(boolean enabled) {
        externalMode(enabled, false);
    }

    private void externalMode(boolean enabled, boolean usersCanEditLogin) {
        AppSettings settings = AppSettings.builder()
                .koreaderSyncSettings(KoreaderSyncSettings.builder()
                        .externalServerEnabled(enabled)
                        .externalServerUrl("https://sync.example")
                        .usersCanEditLogin(usersCanEditLogin)
                        .build())
                .build();
        when(appSettingService.getAppSettings()).thenReturn(settings);
    }

    @BeforeEach
    void setUp() {
        lenient().when(authService.getAuthenticatedUser()).thenReturn(actor);
        lenient().when(actor.getId()).thenReturn(7L);
    }

    @Test
    void readersCannotChangeTheirLoginInExternalModeByDefault() {
        externalMode(true);
        when(actor.getPermissions()).thenReturn(new BookLoreUser.UserPermissions());

        assertThrows(AccessDeniedException.class, () -> service.upsertUser("reader", "newpass1"));
        verify(koreaderUserRepository, never()).save(any());
    }

    @Test
    void readersCanChangeTheirLoginInExternalModeWhenAllowed() {
        externalMode(true, true);
        when(userRepository.findById(7L)).thenReturn(Optional.of(new BookLoreUserEntity()));
        when(koreaderUserRepository.findByBookLoreUserId(7L)).thenReturn(Optional.empty());
        when(koreaderUserRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(koreaderUserMapper.toDto(any())).thenReturn(mock(KoreaderUser.class));

        service.upsertUser("reader", "newpass1");

        ArgumentCaptor<KoreaderUserEntity> saved = ArgumentCaptor.forClass(KoreaderUserEntity.class);
        verify(koreaderUserRepository).save(saved.capture());
        assertEquals("reader", saved.getValue().getUsername());
        assertEquals(Md5Util.md5Hex("newpass1"), saved.getValue().getPasswordMD5());
    }

    @Test
    void generatedCodeIsTwelveLowerCaseAlphanumericsWithBoth() {
        for (int i = 0; i < 200; i++) {
            String code = KoreaderUserService.generateSyncCode();
            assertTrue(code.matches("[a-z0-9]{12}"), code);
            assertTrue(code.chars().anyMatch(Character::isDigit), code);
            assertTrue(code.chars().anyMatch(Character::isLetter), code);
        }
    }

    @Test
    void getUserInitialisesAManagedLoginInExternalMode() {
        externalMode(true);
        when(actor.getEmail()).thenReturn("reader@example.com");
        when(actor.getUsername()).thenReturn("reader@example.com");
        when(koreaderUserRepository.findByBookLoreUserId(7L)).thenReturn(Optional.empty());
        when(koreaderUserRepository.findByUsername("reader@example.com")).thenReturn(Optional.empty());
        when(userRepository.findById(7L)).thenReturn(Optional.of(new BookLoreUserEntity()));
        when(koreaderUserRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(koreaderUserMapper.toDto(any())).thenReturn(mock(KoreaderUser.class));

        service.getUser();

        ArgumentCaptor<KoreaderUserEntity> saved = ArgumentCaptor.forClass(KoreaderUserEntity.class);
        verify(koreaderUserRepository).save(saved.capture());
        assertEquals("reader@example.com", saved.getValue().getUsername());
        assertTrue(saved.getValue().getPassword().matches("[a-z0-9]{12}"));
        assertEquals(Md5Util.md5Hex(saved.getValue().getPassword()), saved.getValue().getPasswordMD5());
        assertTrue(saved.getValue().isSyncEnabled());
    }

    @Test
    void getUserStillReports404WhenExternalModeIsOff() {
        externalMode(false);
        when(koreaderUserRepository.findByBookLoreUserId(7L)).thenReturn(Optional.empty());

        assertThrows(APIException.class, () -> service.getUser());
        verify(koreaderUserRepository, never()).save(any());
    }

    @Test
    void getUserReturnsTheExistingLoginWithoutTouchingIt() {
        KoreaderUserEntity existing = new KoreaderUserEntity();
        existing.setUsername("reader@example.com");
        existing.setPassword("abc123abc123");
        when(koreaderUserRepository.findByBookLoreUserId(7L)).thenReturn(Optional.of(existing));
        when(koreaderUserMapper.toDto(existing)).thenReturn(mock(KoreaderUser.class));

        service.getUser();

        verify(koreaderUserRepository, never()).save(any());
        verify(appSettingService, never()).getAppSettings();
    }

    @Test
    void rotatePasswordReplacesOnlyThePassword() {
        KoreaderUserEntity existing = new KoreaderUserEntity();
        existing.setUsername("reader@example.com");
        existing.setPassword("abc123abc123");
        existing.setPasswordMD5(Md5Util.md5Hex("abc123abc123"));
        when(koreaderUserRepository.findByBookLoreUserId(7L)).thenReturn(Optional.of(existing));
        when(koreaderUserRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(koreaderUserMapper.toDto(any())).thenReturn(mock(KoreaderUser.class));

        service.rotatePassword();

        assertEquals("reader@example.com", existing.getUsername());
        assertNotEquals("abc123abc123", existing.getPassword());
        assertTrue(existing.getPassword().matches("[a-z0-9]{12}"));
        assertEquals(Md5Util.md5Hex(existing.getPassword()), existing.getPasswordMD5());
    }

    @Test
    void rotatePasswordInitialisesFirstInExternalModeWhenMissing() {
        externalMode(true);
        when(actor.getEmail()).thenReturn("reader@example.com");
        when(actor.getUsername()).thenReturn("reader@example.com");
        when(koreaderUserRepository.findByBookLoreUserId(7L)).thenReturn(Optional.empty());
        when(koreaderUserRepository.findByUsername("reader@example.com")).thenReturn(Optional.empty());
        when(userRepository.findById(7L)).thenReturn(Optional.of(new BookLoreUserEntity()));
        when(koreaderUserRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(koreaderUserMapper.toDto(any())).thenReturn(mock(KoreaderUser.class));

        service.rotatePassword();

        verify(koreaderUserRepository, times(2)).save(any());
    }

    @Test
    void takenUsernameGetsTheUserIdSuffix() {
        externalMode(true);
        when(actor.getEmail()).thenReturn("reader@example.com");
        when(actor.getUsername()).thenReturn("reader@example.com");
        when(koreaderUserRepository.findByBookLoreUserId(7L)).thenReturn(Optional.empty());
        when(koreaderUserRepository.findByUsername("reader@example.com")).thenReturn(Optional.of(new KoreaderUserEntity()));
        when(userRepository.findById(7L)).thenReturn(Optional.of(new BookLoreUserEntity()));
        when(koreaderUserRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(koreaderUserMapper.toDto(any())).thenReturn(mock(KoreaderUser.class));

        service.getUser();

        ArgumentCaptor<KoreaderUserEntity> saved = ArgumentCaptor.forClass(KoreaderUserEntity.class);
        verify(koreaderUserRepository).save(saved.capture());
        assertEquals("reader@example.com-7", saved.getValue().getUsername());
    }

    @Test
    void aTakenSuffixedUsernameGetsANumberToo() {
        externalMode(true);
        when(actor.getEmail()).thenReturn("reader@example.com");
        when(actor.getUsername()).thenReturn("reader@example.com");
        when(koreaderUserRepository.findByBookLoreUserId(7L)).thenReturn(Optional.empty());
        when(koreaderUserRepository.findByUsername("reader@example.com")).thenReturn(Optional.of(new KoreaderUserEntity()));
        when(koreaderUserRepository.findByUsername("reader@example.com-7")).thenReturn(Optional.of(new KoreaderUserEntity()));
        when(userRepository.findById(7L)).thenReturn(Optional.of(new BookLoreUserEntity()));
        when(koreaderUserRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(koreaderUserMapper.toDto(any())).thenReturn(mock(KoreaderUser.class));

        service.getUser();

        ArgumentCaptor<KoreaderUserEntity> saved = ArgumentCaptor.forClass(KoreaderUserEntity.class);
        verify(koreaderUserRepository).save(saved.capture());
        assertEquals("reader@example.com-7-2", saved.getValue().getUsername());
    }
}
