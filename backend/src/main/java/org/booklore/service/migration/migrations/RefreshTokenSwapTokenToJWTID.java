package org.booklore.service.migration.migrations;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.booklore.config.security.JwtUtils;
import org.booklore.model.dto.settings.AppSettingKey;
import org.booklore.model.entity.AppSettingEntity;
import org.booklore.model.entity.RefreshTokenEntity;
import org.booklore.repository.RefreshTokenRepository;
import org.booklore.service.migration.Migration;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.node.ObjectNode;

import java.time.Instant;
import java.util.Set;

@Slf4j
@Component
@RequiredArgsConstructor
public class RefreshTokenSwapTokenToJWTID implements Migration {
    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtUtils jwtUtils;

    @Override
    public String getKey() {
        return "refreshTokenSwapTokenToJWTID";
    }

    @Override
    public String getDescription() {
        return "Migrates Refresh Tokens to only storing the JWT ID rather than the full token.";
    }

    public String getJWTID(String token) {
        try {
            return jwtUtils.unsafeExtractJWTID(token);
        } catch (Exception e) {
            return null;
        }
    }

    public void migrate(RefreshTokenEntity refreshTokenEntity) {
        String jwtID = getJWTID(refreshTokenEntity.getToken());

        if (jwtID == null || jwtID.isBlank()) {
            // Skip non-token.
            return;
        }

        try {

            refreshTokenEntity.setToken(jwtID);
            refreshTokenRepository.save(refreshTokenEntity);
        } catch (Exception e) {
            // If there were any issues revoke this token.
            log.warn("Error getting JWT ID from token", e);
            refreshTokenEntity.setRevocationDate(Instant.now());
            refreshTokenEntity.setRevoked(true);
            refreshTokenRepository.save(refreshTokenEntity);
        }
    }

    @Override
    public void execute() {
        log.info("Executing migration: {}", getKey());

        Pageable pageable = PageRequest.of(0, 10, Sort.by("id"));
        Page<RefreshTokenEntity> refreshTokenEntities;
        do {
            refreshTokenEntities = refreshTokenRepository.findAll(pageable);

            for (var refreshTokenEntity : refreshTokenEntities) {
                migrate(refreshTokenEntity);
            }

            pageable = refreshTokenEntities.nextPageable();
        } while (refreshTokenEntities.hasNext());

        log.info("Completed migration: {}", getKey());
    }
}

