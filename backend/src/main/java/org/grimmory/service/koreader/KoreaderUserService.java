package org.grimmory.service.koreader;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.grimmory.config.security.service.AuthenticationService;
import org.grimmory.exception.ApiError;
import org.grimmory.mapper.KoreaderUserMapper;
import org.grimmory.model.dto.KoreaderUser;
import org.grimmory.model.entity.BookLoreUserEntity;
import org.grimmory.model.entity.KoreaderUserEntity;
import org.grimmory.repository.KoreaderUserRepository;
import org.grimmory.repository.UserRepository;
import org.grimmory.util.Md5Util;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class KoreaderUserService {

    private final AuthenticationService authService;
    private final UserRepository userRepository;
    private final KoreaderUserRepository koreaderUserRepository;
    private final KoreaderUserMapper koreaderUserMapper;

    @Transactional
    public KoreaderUser upsertUser(String username, String rawPassword) {
        Long ownerId = authService.getAuthenticatedUser().getId();
        BookLoreUserEntity owner = userRepository.findById(ownerId)
                .orElseThrow(() -> ApiError.USER_NOT_FOUND.createException(ownerId));

        String md5Password = Md5Util.md5Hex(rawPassword);
        Optional<KoreaderUserEntity> existing = koreaderUserRepository.findByBookLoreUserId(ownerId);
        boolean isUpdate = existing.isPresent();
        KoreaderUserEntity user = existing.orElseGet(() -> {
            KoreaderUserEntity u = new KoreaderUserEntity();
            u.setBookLoreUser(owner);
            return u;
        });

        user.setUsername(username);
        user.setPassword(rawPassword);
        user.setPasswordMD5(md5Password);
        KoreaderUserEntity saved = koreaderUserRepository.save(user);

        log.info("upsertUser: {} KoreaderUser [id={}, username='{}'] for BookLoreUser='{}'",
                isUpdate ? "Updated" : "Created",
                saved.getId(), saved.getUsername(),
                authService.getAuthenticatedUser().getUsername());

        return koreaderUserMapper.toDto(saved);
    }

    @Transactional(readOnly = true)
    public KoreaderUser getUser() {
        Long id = authService.getAuthenticatedUser().getId();
        KoreaderUserEntity user = koreaderUserRepository.findByBookLoreUserId(id)
                .orElseThrow(() -> ApiError.GENERIC_NOT_FOUND.createException("Koreader user not found for BookLore user ID: " + id));
        return koreaderUserMapper.toDto(user);
    }

    @Transactional
    public void toggleSync(boolean enabled) {
        Long id = authService.getAuthenticatedUser().getId();
        KoreaderUserEntity user = koreaderUserRepository.findByBookLoreUserId(id)
                .orElseThrow(() -> ApiError.GENERIC_NOT_FOUND.createException("Koreader user not found for BookLore user ID: " + id));
        user.setSyncEnabled(enabled);
        koreaderUserRepository.save(user);
    }

    @Transactional
    public void toggleSyncProgressWithBooklore(boolean enabled) {
        Long id = authService.getAuthenticatedUser().getId();
        KoreaderUserEntity user = koreaderUserRepository.findByBookLoreUserId(id)
                .orElseThrow(() -> ApiError.GENERIC_NOT_FOUND.createException("Koreader user not found for BookLore user ID: " + id));
        user.setSyncWithBookloreReader(enabled);
        koreaderUserRepository.save(user);
    }
}