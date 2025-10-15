package com.adityachandel.booklore.service.email;

import com.adityachandel.booklore.exception.ApiError;
import com.adityachandel.booklore.mapper.EmailRecipientMapper;
import com.adityachandel.booklore.model.dto.EmailRecipient;
import com.adityachandel.booklore.model.dto.request.CreateEmailRecipientRequest;
import com.adityachandel.booklore.model.entity.EmailRecipientEntity;
import com.adityachandel.booklore.repository.EmailRecipientRepository;
import jakarta.transaction.Transactional;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

@Deprecated
@Slf4j
@Service
@AllArgsConstructor
public class EmailRecipientService {

    private final EmailRecipientRepository emailRecipientRepository;
    private final EmailRecipientMapper emailRecipientMapper;

    public EmailRecipient getEmailRecipient(Long id) {
        EmailRecipientEntity emailRecipient = emailRecipientRepository.findById(id).orElseThrow(() -> ApiError.EMAIL_RECIPIENT_NOT_FOUND.createException(id));
        return emailRecipientMapper.toDTO(emailRecipient);
    }

    @Transactional
    public EmailRecipient createEmailRecipient(CreateEmailRecipientRequest request) {
        boolean isFirstRecipient = emailRecipientRepository.count() == 0;
        if (request.isDefaultRecipient() || isFirstRecipient) {
            emailRecipientRepository.updateAllRecipientsToNonDefault();
        }
        EmailRecipientEntity emailRecipientEntity = emailRecipientMapper.toEntity(request);
        emailRecipientEntity.setDefaultRecipient(request.isDefaultRecipient() || isFirstRecipient);
        EmailRecipientEntity savedEntity = emailRecipientRepository.save(emailRecipientEntity);
        return emailRecipientMapper.toDTO(savedEntity);
    }

    @Transactional
    public EmailRecipient updateEmailRecipient(Long id, CreateEmailRecipientRequest request) {
        EmailRecipientEntity existingRecipient = emailRecipientRepository.findById(id).orElseThrow(() -> ApiError.EMAIL_RECIPIENT_NOT_FOUND.createException(id));
        if (request.isDefaultRecipient()) {
            emailRecipientRepository.updateAllRecipientsToNonDefault();
        }
        emailRecipientMapper.updateEntityFromRequest(request, existingRecipient);
        EmailRecipientEntity updatedEntity = emailRecipientRepository.save(existingRecipient);
        return emailRecipientMapper.toDTO(updatedEntity);
    }

    @Transactional
    public void setDefaultRecipient(Long id) {
        EmailRecipientEntity emailRecipient = emailRecipientRepository.findById(id).orElseThrow(() -> ApiError.EMAIL_RECIPIENT_NOT_FOUND.createException(id));
        emailRecipientRepository.updateAllRecipientsToNonDefault();
        emailRecipient.setDefaultRecipient(true);
        emailRecipientRepository.save(emailRecipient);
    }

    @Transactional
    public void deleteEmailRecipient(Long id) {
        EmailRecipientEntity emailRecipientToDelete = emailRecipientRepository.findById(id).orElseThrow(() -> ApiError.EMAIL_RECIPIENT_NOT_FOUND.createException(id));
        boolean isDefaultRecipient = emailRecipientToDelete.isDefaultRecipient();
        if (isDefaultRecipient) {
            List<EmailRecipientEntity> allRecipients = emailRecipientRepository.findAll();
            if (allRecipients.size() > 1) {
                allRecipients.remove(emailRecipientToDelete);
                int randomIndex = ThreadLocalRandom.current().nextInt(allRecipients.size());
                EmailRecipientEntity newDefaultRecipient = allRecipients.get(randomIndex);
                newDefaultRecipient.setDefaultRecipient(true);
                emailRecipientRepository.save(newDefaultRecipient);
            }
        }
        emailRecipientRepository.deleteById(id);
    }

    public List<EmailRecipient> getEmailRecipients() {
        return emailRecipientRepository.findAll().stream()
                .map(emailRecipientMapper::toDTO)
                .toList();
    }
}