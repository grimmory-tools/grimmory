package org.booklore.service.audit;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.booklore.model.dto.BookLoreUser;
import org.booklore.model.dto.response.AuditLogDto;
import org.booklore.model.entity.AuditLogEntity;
import org.booklore.model.enums.AuditAction;
import org.booklore.repository.AuditLogRepository;
import org.booklore.util.RequestUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuditService {

    private final AuditLogRepository auditLogRepository;

    public void log(AuditAction action, String description) {
        log(action, null, null, description);
    }

    public void log(AuditAction action, String entityType, Long entityId, String description) {
        try {
            Long userId = null;
            String username = "system";

            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication != null && authentication.getPrincipal() instanceof BookLoreUser user) {
                userId = user.getId();
                username = user.getUsername();
            }

            String ipAddress = null;
            try {
                ipAddress = RequestUtils.getCurrentRequest().getRemoteAddr();
            } catch (Exception ignored) {
                // Non-HTTP context (scheduled tasks, etc.)
            }

            AuditLogEntity entity = AuditLogEntity.builder()
                    .userId(userId)
                    .username(username)
                    .action(action)
                    .entityType(entityType)
                    .entityId(entityId)
                    .description(description)
                    .ipAddress(ipAddress)
                    .build();

            auditLogRepository.save(entity);
        } catch (Exception e) {
            log.warn("Failed to write audit log: action={}, description={}", action, description, e);
        }
    }

    public Page<AuditLogDto> getAuditLogs(Pageable pageable) {
        return auditLogRepository.findAllByOrderByCreatedAtDesc(pageable)
                .map(this::toDto);
    }

    public Page<AuditLogDto> getAuditLogs(AuditAction action, Long userId, String username, LocalDateTime from, LocalDateTime to, Pageable pageable) {
        return auditLogRepository.findFiltered(action, userId, username, from, to, pageable)
                .map(this::toDto);
    }

    public List<String> getDistinctUsernames() {
        return auditLogRepository.findDistinctUsernames();
    }

    private AuditLogDto toDto(AuditLogEntity entity) {
        return AuditLogDto.builder()
                .id(entity.getId())
                .userId(entity.getUserId())
                .username(entity.getUsername())
                .action(entity.getAction())
                .entityType(entity.getEntityType())
                .entityId(entity.getEntityId())
                .description(entity.getDescription())
                .ipAddress(entity.getIpAddress())
                .createdAt(entity.getCreatedAt())
                .build();
    }
}
