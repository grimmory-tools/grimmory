package com.adityachandel.booklore.service;

import com.adityachandel.booklore.config.security.service.AuthenticationService;
import com.adityachandel.booklore.exception.ApiError;
import com.adityachandel.booklore.mapper.KoreaderUserMapper;
import com.adityachandel.booklore.model.dto.KoreaderUser;
import com.adityachandel.booklore.model.entity.BookLoreUserEntity;
import com.adityachandel.booklore.model.entity.KoreaderUserEntity;
import com.adityachandel.booklore.repository.KoreaderUserRepository;
import com.adityachandel.booklore.repository.UserRepository;
import com.adityachandel.booklore.util.Md5Util;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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

    public KoreaderUser getUser() {
        Long id = authService.getAuthenticatedUser().getId();
        KoreaderUserEntity user = koreaderUserRepository.findByBookLoreUserId(id)
                .orElseThrow(() -> ApiError.GENERIC_NOT_FOUND.createException("Koreader user not found for BookLore user ID: " + id));
        return koreaderUserMapper.toDto(user);
    }

    public void toggleSync(boolean enabled) {
        Long id = authService.getAuthenticatedUser().getId();
        KoreaderUserEntity user = koreaderUserRepository.findByBookLoreUserId(id)
                .orElseThrow(() -> ApiError.GENERIC_NOT_FOUND.createException("Koreader user not found for BookLore user ID: " + id));
        user.setSyncEnabled(enabled);
        koreaderUserRepository.save(user);
    }
}