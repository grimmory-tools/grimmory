package org.booklore.service.user;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.booklore.mapper.custom.BookLoreUserTransformer;
import org.booklore.model.dto.BookLoreUser;
import org.booklore.model.entity.BookLoreUserEntity;
import org.booklore.repository.UserRepository;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserCacheService {

    private final UserRepository userRepository;
    private final BookLoreUserTransformer bookLoreUserTransformer;
    private final CacheManager cacheManager;

    @Transactional(readOnly = true)
    @Cacheable(value = "userDetails", key = "#userId")
    public BookLoreUser getUserDetails(Long userId) {
        log.debug("Cache miss for userDetails of user ID: {}", userId);
        BookLoreUserEntity entity = userRepository.findByIdWithDetails(userId)
                .orElse(null);
        if (entity == null) {
            return null;
        }
        return bookLoreUserTransformer.toDTO(entity);
    }

    public void evictUserCache(Long userId) {
        if (userId == null) {
            return;
        }
        var cache = cacheManager.getCache("userDetails");
        if (cache != null) {
            log.debug("Evicting userDetails cache for user ID: {}", userId);
            cache.evict(userId);
        }
    }
}
