package org.booklore.service.email;

import org.booklore.config.security.service.AuthenticationService;
import org.booklore.exception.ApiError;
import org.booklore.mapper.EmailRecipientV2Mapper;
import org.booklore.model.dto.BookLoreUser;
import org.booklore.model.dto.EmailRecipientV2;
import org.booklore.model.dto.request.CreateEmailRecipientRequest;
import org.booklore.model.entity.EmailRecipientV2Entity;
import org.booklore.repository.EmailRecipientV2Repository;
import org.springframework.transaction.annotation.Transactional;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

@Slf4j
@Service
@AllArgsConstructor
public class EmailRecipientV2Service {

    private final EmailRecipientV2Repository repository;
    private final EmailRecipientV2Mapper mapper;
    private final AuthenticationService authService;


    public List<EmailRecipientV2> getEmailRecipients() {
        BookLoreUser user = authService.getAuthenticatedUser();
        return repository.findAllByUserId(user.getId()).stream()
                .map(mapper::toDTO)
                .toList();
    }

    public EmailRecipientV2 getEmailRecipient(Long id) {
        BookLoreUser user = authService.getAuthenticatedUser();
        EmailRecipientV2Entity emailRecipient = repository.findByIdAndUserId(id, user.getId()).orElseThrow(() -> ApiError.EMAIL_RECIPIENT_NOT_FOUND.createException(id));
        return mapper.toDTO(emailRecipient);
    }

    @Transactional
    public EmailRecipientV2 createEmailRecipient(CreateEmailRecipientRequest request) {
        BookLoreUser user = authService.getAuthenticatedUser();
        boolean isFirstRecipient = repository.count() == 0;
        if (request.isDefaultRecipient() || isFirstRecipient) {
            repository.updateAllRecipientsToNonDefault(user.getId());
        }
        EmailRecipientV2Entity entity = mapper.toEntity(request);
        entity.setDefaultRecipient(request.isDefaultRecipient() || isFirstRecipient);
        entity.setUserId(user.getId());
        EmailRecipientV2Entity savedEntity = repository.save(entity);
        return mapper.toDTO(savedEntity);
    }

    @Transactional
    public EmailRecipientV2 updateEmailRecipient(Long id, CreateEmailRecipientRequest request) {
        BookLoreUser user = authService.getAuthenticatedUser();
        EmailRecipientV2Entity existingRecipient = repository.findByIdAndUserId(id, user.getId()).orElseThrow(() -> ApiError.EMAIL_RECIPIENT_NOT_FOUND.createException(id));
        if (request.isDefaultRecipient()) {
            repository.updateAllRecipientsToNonDefault(user.getId());
        }
        mapper.updateEntityFromRequest(request, existingRecipient);
        EmailRecipientV2Entity updatedEntity = repository.save(existingRecipient);
        return mapper.toDTO(updatedEntity);
    }

    @Transactional
    public void setDefaultRecipient(Long id) {
        BookLoreUser user = authService.getAuthenticatedUser();
        EmailRecipientV2Entity emailRecipient = repository.findByIdAndUserId(id, user.getId()).orElseThrow(() -> ApiError.EMAIL_RECIPIENT_NOT_FOUND.createException(id));
        repository.updateAllRecipientsToNonDefault(user.getId());
        emailRecipient.setDefaultRecipient(true);
        repository.save(emailRecipient);
    }

    @Transactional
    public void deleteEmailRecipient(Long id) {
        BookLoreUser user = authService.getAuthenticatedUser();
        EmailRecipientV2Entity emailRecipientToDelete = repository.findByIdAndUserId(id, user.getId()).orElseThrow(() -> ApiError.EMAIL_RECIPIENT_NOT_FOUND.createException(id));
        boolean isDefaultRecipient = emailRecipientToDelete.isDefaultRecipient();
        if (isDefaultRecipient) {
            List<EmailRecipientV2Entity> allRecipients = repository.findAll();
            if (allRecipients.size() > 1) {
                allRecipients.remove(emailRecipientToDelete);
                int randomIndex = ThreadLocalRandom.current().nextInt(allRecipients.size());
                EmailRecipientV2Entity newDefaultRecipient = allRecipients.get(randomIndex);
                newDefaultRecipient.setDefaultRecipient(true);
                repository.save(newDefaultRecipient);
            }
        }
        repository.deleteById(id);
    }
}