package org.booklore.service.upload.lcp;

import org.booklore.config.security.service.AuthenticationService;
import org.booklore.exception.APIException;
import org.booklore.model.dto.BookLoreUser;
import org.booklore.model.entity.BookLoreUserEntity;
import org.booklore.model.entity.UserSettingEntity;
import org.booklore.repository.UserRepository;
import org.booklore.repository.UserSettingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Base64;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LcpSettingsServiceTest {
    @Mock AuthenticationService authenticationService;
    @Mock UserSettingRepository settingRepository;
    @Mock UserRepository userRepository;
    @InjectMocks LcpSettingsService service;
    private static final String KEY = "lcpDefaultUserKey";

    @BeforeEach
    void authenticate() {
        BookLoreUser user = new BookLoreUser();
        user.setId(7L);
        when(authenticationService.getAuthenticatedUser()).thenReturn(user);
    }

    @Test
    void savesDerivedKeyForAuthenticatedUserWithoutSavingPassphrase() {
        when(settingRepository.findByUserIdAndSettingKey(7L, KEY)).thenReturn(Optional.empty());
        BookLoreUserEntity user = new BookLoreUserEntity();
        user.setId(7L);
        when(userRepository.getReferenceById(7L)).thenReturn(user);
        service.save("library-card");
        ArgumentCaptor<UserSettingEntity> saved = ArgumentCaptor.forClass(UserSettingEntity.class);
        verify(settingRepository).save(saved.capture());
        assertThat(saved.getValue().getUser().getId()).isEqualTo(7L);
        assertThat(saved.getValue().getSettingValue()).isEqualTo(Base64.getEncoder().encodeToString(LcpEpubConverter.deriveUserKey("library-card")))
                .doesNotContain("library-card");
    }

    @Test
    void retrievesAndRemovesOnlyCurrentUsersDefault() {
        var setting = UserSettingEntity.builder().settingValue(Base64.getEncoder().encodeToString(new byte[32])).build();
        when(settingRepository.findByUserIdAndSettingKey(7L, KEY)).thenReturn(Optional.of(setting));
        assertThat(service.hasDefaultPassphrase()).isTrue();
        assertThat(service.getDefaultUserKey()).contains(new byte[32]);
        service.clear();
        verify(settingRepository).deleteByUserIdAndSettingKey(7L, KEY);
        BookLoreUser other = new BookLoreUser();
        other.setId(8L);
        when(authenticationService.getAuthenticatedUser()).thenReturn(other);
        when(settingRepository.findByUserIdAndSettingKey(8L, KEY)).thenReturn(Optional.empty());
        assertThat(service.hasDefaultPassphrase()).isFalse();
        assertThat(service.getDefaultUserKey()).isEmpty();
    }

    @Test
    void rejectsAnonymousAccess() {
        when(authenticationService.getAuthenticatedUser()).thenReturn(null);
        assertThatThrownBy(service::getDefaultUserKey).isInstanceOf(APIException.class);
    }
}
