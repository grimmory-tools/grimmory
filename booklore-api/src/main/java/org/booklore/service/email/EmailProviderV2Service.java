package org.booklore.service.email;

import org.booklore.config.security.service.AuthenticationService;
import org.booklore.exception.ApiError;
import org.booklore.mapper.EmailProviderV2Mapper;
import org.booklore.model.dto.BookLoreUser;
import org.booklore.model.dto.EmailProviderV2;
import org.booklore.model.dto.request.CreateEmailProviderRequest;
import org.booklore.model.entity.EmailProviderV2Entity;
import org.booklore.model.entity.UserEmailProviderPreferenceEntity;
import org.booklore.repository.EmailProviderV2Repository;
import org.booklore.repository.UserEmailProviderPreferenceRepository;
import jakarta.transaction.Transactional;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.booklore.model.enums.AuditAction;
import org.booklore.service.audit.AuditService;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

@Slf4j
@Service
@AllArgsConstructor
public class EmailProviderV2Service {

    private final EmailProviderV2Repository repository;
    private final UserEmailProviderPreferenceRepository preferenceRepository;
    private final EmailProviderV2Mapper mapper;
    private final AuthenticationService authService;
    private final AuditService auditService;

    public List<EmailProviderV2> getEmailProviders() {
        BookLoreUser user = authService.getAuthenticatedUser();
        List<EmailProviderV2Entity> userProviders = repository.findAllByUserId(user.getId());
        if (!user.getPermissions().isAdmin()) {
            List<EmailProviderV2Entity> sharedProviders = repository.findAllBySharedTrueAndAdmin();
            userProviders.addAll(sharedProviders);
        }

        Long defaultProviderId = getDefaultProviderIdForUser(user.getId());
        return userProviders.stream()
                .map(entity -> mapper.toDTO(entity, defaultProviderId))
                .toList();
    }

    public EmailProviderV2 getEmailProvider(Long id) {
        BookLoreUser user = authService.getAuthenticatedUser();
        EmailProviderV2Entity entity = repository.findAccessibleProvider(id, user.getId())
                .orElseThrow(() -> ApiError.EMAIL_PROVIDER_NOT_FOUND.createException(id));

        Long defaultProviderId = getDefaultProviderIdForUser(user.getId());
        return mapper.toDTO(entity, defaultProviderId);
    }

    @Transactional
    public EmailProviderV2 createEmailProvider(CreateEmailProviderRequest request) {
        BookLoreUser user = authService.getAuthenticatedUser();
        EmailProviderV2Entity entity = mapper.toEntity(request);
        entity.setUserId(user.getId());
        entity.setShared(user.getPermissions().isAdmin() && request.isShared());
        EmailProviderV2Entity savedEntity = repository.save(entity);

        if (preferenceRepository.findByUserId(user.getId()).isEmpty()) {
            setDefaultProviderForUser(user.getId(), savedEntity.getId());
        }

        Long defaultProviderId = getDefaultProviderIdForUser(user.getId());
        auditService.log(AuditAction.EMAIL_PROVIDER_CREATED, "EmailProvider", savedEntity.getId(), "Created email provider: " + savedEntity.getHost() + ":" + savedEntity.getPort());
        return mapper.toDTO(savedEntity, defaultProviderId);
    }

    @Transactional
    public EmailProviderV2 updateEmailProvider(Long id, CreateEmailProviderRequest request) {
        BookLoreUser user = authService.getAuthenticatedUser();
        EmailProviderV2Entity existingProvider = repository.findByIdAndUserId(id, user.getId())
                .orElseThrow(() -> ApiError.EMAIL_PROVIDER_NOT_FOUND.createException(id));

        mapper.updateEntityFromRequest(request, existingProvider);
        if (user.getPermissions().isAdmin()) {
            existingProvider.setShared(request.isShared());
        }
        EmailProviderV2Entity updatedEntity = repository.save(existingProvider);
        auditService.log(AuditAction.EMAIL_PROVIDER_UPDATED, "EmailProvider", id, "Updated email provider: " + updatedEntity.getHost() + ":" + updatedEntity.getPort());

        Long defaultProviderId = getDefaultProviderIdForUser(user.getId());
        return mapper.toDTO(updatedEntity, defaultProviderId);
    }

    @Transactional
    public void setDefaultEmailProvider(Long id) {
        BookLoreUser user = authService.getAuthenticatedUser();
        // Verify user has access to this provider
        repository.findAccessibleProvider(id, user.getId())
                .orElseThrow(() -> ApiError.EMAIL_PROVIDER_NOT_FOUND.createException(id));

        setDefaultProviderForUser(user.getId(), id);
    }

    @Transactional
    public void deleteEmailProvider(Long id) {
        BookLoreUser user = authService.getAuthenticatedUser();
        repository.findByIdAndUserId(id, user.getId())
                .orElseThrow(() -> ApiError.EMAIL_PROVIDER_NOT_FOUND.createException(id));

        List<UserEmailProviderPreferenceEntity> preferencesUsingProvider =
                preferenceRepository.findAll().stream()
                        .filter(pref -> pref.getDefaultProviderId().equals(id))
                        .toList();

        for (UserEmailProviderPreferenceEntity preference : preferencesUsingProvider) {
            List<EmailProviderV2Entity> availableProviders = getAccessibleProvidersForUser(preference.getUserId());
            availableProviders.removeIf(p -> p.getId().equals(id));

            if (!availableProviders.isEmpty()) {
                EmailProviderV2Entity newDefault = availableProviders.get(ThreadLocalRandom.current().nextInt(availableProviders.size()));
                preference.setDefaultProviderId(newDefault.getId());
                preferenceRepository.save(preference);
            } else {
                preferenceRepository.delete(preference);
            }
        }

        repository.deleteById(id);
        auditService.log(AuditAction.EMAIL_PROVIDER_DELETED, "EmailProvider", id, "Deleted email provider");
    }

    private Long getDefaultProviderIdForUser(Long userId) {
        return preferenceRepository.findByUserId(userId)
                .map(UserEmailProviderPreferenceEntity::getDefaultProviderId)
                .orElse(null);
    }

    private void setDefaultProviderForUser(Long userId, Long providerId) {
        UserEmailProviderPreferenceEntity preference = preferenceRepository.findByUserId(userId)
                .orElse(UserEmailProviderPreferenceEntity.builder()
                        .userId(userId)
                        .build());
        preference.setDefaultProviderId(providerId);
        preferenceRepository.save(preference);
    }

    private List<EmailProviderV2Entity> getAccessibleProvidersForUser(Long userId) {
        List<EmailProviderV2Entity> providers = repository.findAllByUserId(userId);
        providers.addAll(repository.findAllBySharedTrueAndAdmin());
        return providers;
    }
}