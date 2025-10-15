package com.adityachandel.booklore.service.email;

import com.adityachandel.booklore.config.security.service.AuthenticationService;
import com.adityachandel.booklore.exception.ApiError;
import com.adityachandel.booklore.mapper.EmailProviderV2Mapper;
import com.adityachandel.booklore.model.dto.BookLoreUser;
import com.adityachandel.booklore.model.dto.EmailProviderV2;
import com.adityachandel.booklore.model.dto.request.CreateEmailProviderRequest;
import com.adityachandel.booklore.model.entity.EmailProviderV2Entity;
import com.adityachandel.booklore.repository.EmailProviderV2Repository;
import jakarta.transaction.Transactional;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

@Slf4j
@Service
@AllArgsConstructor
public class EmailProviderV2Service {

    private final EmailProviderV2Repository repository;
    private final EmailProviderV2Mapper mapper;
    private final AuthenticationService authService;

    public List<EmailProviderV2> getEmailProviders() {
        BookLoreUser user = authService.getAuthenticatedUser();
        return repository.findAllByUserId(user.getId()).stream().map(mapper::toDTO).toList();
    }

    public EmailProviderV2 getEmailProvider(Long id) {
        BookLoreUser user = authService.getAuthenticatedUser();
        EmailProviderV2Entity entity = repository.findByIdAndUserId(id, user.getId()).orElseThrow(() -> ApiError.EMAIL_PROVIDER_NOT_FOUND.createException(id));
        return mapper.toDTO(entity);
    }

    public EmailProviderV2 createEmailProvider(CreateEmailProviderRequest request) {
        BookLoreUser user = authService.getAuthenticatedUser();
        boolean isFirstProvider = repository.count() == 0;
        EmailProviderV2Entity entity = mapper.toEntity(request);
        entity.setDefaultProvider(isFirstProvider);
        entity.setUserId(user.getId());
        EmailProviderV2Entity savedEntity = repository.save(entity);
        return mapper.toDTO(savedEntity);
    }

    public EmailProviderV2 updateEmailProvider(Long id, CreateEmailProviderRequest request) {
        BookLoreUser user = authService.getAuthenticatedUser();
        EmailProviderV2Entity existingProvider = repository.findByIdAndUserId(id, user.getId()).orElseThrow(() -> ApiError.EMAIL_PROVIDER_NOT_FOUND.createException(id));
        mapper.updateEntityFromRequest(request, existingProvider);
        EmailProviderV2Entity updatedEntity = repository.save(existingProvider);
        return mapper.toDTO(updatedEntity);
    }

    @Transactional
    public void setDefaultEmailProvider(Long id) {
        BookLoreUser user = authService.getAuthenticatedUser();
        EmailProviderV2Entity emailProvider = repository.findByIdAndUserId(id, user.getId()).orElseThrow(() -> ApiError.EMAIL_PROVIDER_NOT_FOUND.createException(id));
        repository.updateAllProvidersToNonDefault();
        emailProvider.setDefaultProvider(true);
        repository.save(emailProvider);
    }

    @Transactional
    public void deleteEmailProvider(Long id) {
        BookLoreUser user = authService.getAuthenticatedUser();
        EmailProviderV2Entity emailProviderToDelete = repository.findByIdAndUserId(id, user.getId()).orElseThrow(() -> ApiError.EMAIL_PROVIDER_NOT_FOUND.createException(id));
        boolean isDefaultProvider = emailProviderToDelete.isDefaultProvider();
        if (isDefaultProvider) {
            List<EmailProviderV2Entity> allProviders = repository.findAll();
            if (allProviders.size() > 1) {
                allProviders.remove(emailProviderToDelete);
                EmailProviderV2Entity newDefaultProvider = allProviders.get(ThreadLocalRandom.current().nextInt(allProviders.size()));
                newDefaultProvider.setDefaultProvider(true);
                repository.save(newDefaultProvider);
            }
        }
        repository.deleteById(id);
    }
}