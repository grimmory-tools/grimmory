package org.booklore.service.upload.lcp;

import lombok.RequiredArgsConstructor;
import org.booklore.config.security.service.AuthenticationService;
import org.booklore.exception.ApiError;
import org.booklore.model.dto.settings.UserSettingKey;
import org.booklore.model.entity.UserSettingEntity;
import org.booklore.repository.UserRepository;
import org.booklore.repository.UserSettingRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.Base64;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class LcpSettingsService {
    private final AuthenticationService authenticationService;
    private final UserSettingRepository settingRepository;
    private final UserRepository userRepository;
    private static final String KEY = UserSettingKey.LCP_DEFAULT_USER_KEY.getDbKey();

    private Long currentUserId() {
        var user = authenticationService.getAuthenticatedUser();
        if (user == null || user.getId() == null || user.getId() < 0) {
            throw ApiError.GENERIC_UNAUTHORIZED.createException("Authentication is required.");
        }
        return user.getId();
    }

    public boolean hasDefaultPassphrase() {
        return settingRepository.findByUserIdAndSettingKey(currentUserId(), KEY).isPresent();
    }

    /** This value is an unlock secret; it must never be included in user DTOs. */
    public Optional<byte[]> getDefaultUserKey() {
        return settingRepository.findByUserIdAndSettingKey(currentUserId(), KEY)
                .map(setting -> Base64.getDecoder().decode(setting.getSettingValue()));
    }

    @Transactional
    public void save(String passphrase) {
        byte[] derived = LcpEpubConverter.deriveUserKey(passphrase);
        try {
            Long userId = currentUserId();
            UserSettingEntity setting = settingRepository.findByUserIdAndSettingKey(userId, KEY)
                    .orElseGet(() -> UserSettingEntity.builder().user(userRepository.getReferenceById(userId)).settingKey(KEY).build());
            setting.setSettingValue(Base64.getEncoder().encodeToString(derived));
            settingRepository.save(setting);
        } finally { Arrays.fill(derived, (byte) 0); }
    }

    @Transactional
    public void clear() {
        settingRepository.deleteByUserIdAndSettingKey(currentUserId(), KEY);
    }
}
