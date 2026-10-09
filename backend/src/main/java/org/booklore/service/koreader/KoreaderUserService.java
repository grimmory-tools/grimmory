package org.booklore.service.koreader;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.booklore.config.security.service.AuthenticationService;
import org.booklore.exception.ApiError;
import org.booklore.mapper.KoreaderUserMapper;
import org.booklore.model.dto.BookLoreUser;
import org.booklore.model.dto.KoreaderUser;
import org.booklore.model.dto.settings.KoreaderSyncSettings;
import org.booklore.model.entity.BookLoreUserEntity;
import org.booklore.model.entity.KoreaderUserEntity;
import org.booklore.repository.KoreaderUserRepository;
import org.booklore.repository.UserRepository;
import org.booklore.service.appsettings.AppSettingService;
import org.booklore.util.Md5Util;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class KoreaderUserService {

    private final AuthenticationService authService;
    private final UserRepository userRepository;
    private final KoreaderUserRepository koreaderUserRepository;
    private final KoreaderUserMapper koreaderUserMapper;
    private final AppSettingService appSettingService;

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String SYNC_CODE_ALPHABET = "abcdefghijklmnopqrstuvwxyz0123456789";
    private static final int SYNC_CODE_LENGTH = 12;

    static String generateSyncCode() {
        while (true) {
            StringBuilder code = new StringBuilder(SYNC_CODE_LENGTH);
            for (int i = 0; i < SYNC_CODE_LENGTH; i++) {
                code.append(SYNC_CODE_ALPHABET.charAt(RANDOM.nextInt(SYNC_CODE_ALPHABET.length())));
            }
            String value = code.toString();
            if (value.chars().anyMatch(Character::isDigit) && value.chars().anyMatch(Character::isLetter)) {
                return value;
            }
        }
    }

    private boolean externalServerEnabled() {
        return appSettingService.getAppSettings().getKoreaderSyncSettings().isExternalServerEnabled();
    }

    private KoreaderUserEntity initialiseManagedLogin(BookLoreUser actor) {
        BookLoreUserEntity owner = userRepository.findById(actor.getId())
                .orElseThrow(() -> ApiError.USER_NOT_FOUND.createException(actor.getId()));
        String base = actor.getEmail() != null && !actor.getEmail().isBlank()
                ? actor.getEmail().trim() : actor.getUsername();
        String username = base;
        if (koreaderUserRepository.findByUsername(username).isPresent()) {
            username = base + "-" + actor.getId();
        }
        for (int n = 2; koreaderUserRepository.findByUsername(username).isPresent(); n++) {
            username = base + "-" + actor.getId() + "-" + n;
        }
        String code = generateSyncCode();
        KoreaderUserEntity user = new KoreaderUserEntity();
        user.setBookLoreUser(owner);
        user.setUsername(username);
        user.setPassword(code);
        user.setPasswordMD5(Md5Util.md5Hex(code));
        user.setSyncEnabled(true);
        KoreaderUserEntity saved = koreaderUserRepository.save(user);
        log.info("Initialised managed KOReader sync login [id={}, username='{}'] for BookLoreUser='{}'",
                saved.getId(), saved.getUsername(), actor.getUsername());
        return saved;
    }

    @Transactional
    public KoreaderUser rotatePassword() {
        BookLoreUser actor = authService.getAuthenticatedUser();
        KoreaderUserEntity user = koreaderUserRepository.findByBookLoreUserId(actor.getId())
                .orElseGet(() -> {
                    if (!externalServerEnabled()) {
                        throw ApiError.GENERIC_NOT_FOUND.createException("Koreader user not found for BookLore user ID: " + actor.getId());
                    }
                    return initialiseManagedLogin(actor);
                });
        String code = generateSyncCode();
        user.setPassword(code);
        user.setPasswordMD5(Md5Util.md5Hex(code));
        KoreaderUserEntity saved = koreaderUserRepository.save(user);
        log.info("Rotated KOReader sync password [id={}, username='{}'] for BookLoreUser='{}'",
                saved.getId(), saved.getUsername(), actor.getUsername());
        return koreaderUserMapper.toDto(saved);
    }

    @Transactional
    public KoreaderUser upsertUser(String username, String rawPassword) {
        BookLoreUser actor = authService.getAuthenticatedUser();
        KoreaderSyncSettings sync = appSettingService.getAppSettings().getKoreaderSyncSettings();
        if (sync.isExternalServerEnabled() && !sync.isUsersCanEditLogin() && !actor.getPermissions().isAdmin()) {
            throw new AccessDeniedException("KOReader sync credentials are managed by the administrator");
        }
        Long ownerId = actor.getId();
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

    @Transactional
    public KoreaderUser getUser() {
        BookLoreUser actor = authService.getAuthenticatedUser();
        Long id = actor.getId();
        KoreaderUserEntity user = koreaderUserRepository.findByBookLoreUserId(id)
                .orElseGet(() -> {
                    if (!externalServerEnabled()) {
                        throw ApiError.GENERIC_NOT_FOUND.createException("Koreader user not found for BookLore user ID: " + id);
                    }
                    return initialiseManagedLogin(actor);
                });
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
}