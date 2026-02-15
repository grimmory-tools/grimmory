package org.booklore.service.email;

import org.booklore.config.security.service.AuthenticationService;
import org.booklore.exception.ApiError;
import org.booklore.model.dto.BookLoreUser;
import org.booklore.model.dto.request.SendBookByEmailRequest;
import org.booklore.model.entity.BookEntity;
import org.booklore.model.entity.BookFileEntity;
import org.booklore.model.entity.EmailProviderV2Entity;
import org.booklore.model.entity.EmailRecipientV2Entity;
import org.booklore.model.entity.UserEmailProviderPreferenceEntity;
import org.booklore.model.websocket.LogNotification;
import org.booklore.model.websocket.Topic;
import org.booklore.repository.BookRepository;
import org.booklore.repository.EmailProviderV2Repository;
import org.booklore.repository.EmailRecipientV2Repository;
import org.booklore.repository.UserEmailProviderPreferenceRepository;
import org.booklore.service.NotificationService;
import org.booklore.util.FileUtils;
import org.booklore.util.SecurityContextVirtualThread;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.booklore.model.enums.AuditAction;
import org.booklore.service.audit.AuditService;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.io.File;
import java.util.Properties;

@Slf4j
@Service
@AllArgsConstructor
public class SendEmailV2Service {

    private final EmailProviderV2Repository emailProviderRepository;
    private final UserEmailProviderPreferenceRepository preferenceRepository;
    private final BookRepository bookRepository;
    private final EmailRecipientV2Repository emailRecipientRepository;
    private final NotificationService notificationService;
    private final AuthenticationService authenticationService;
    private final AuditService auditService;

    public void emailBookQuick(Long bookId) {
        BookLoreUser user = authenticationService.getAuthenticatedUser();
        BookEntity book = bookRepository.findByIdWithBookFiles(bookId).orElseThrow(() -> ApiError.BOOK_NOT_FOUND.createException(bookId));
        EmailProviderV2Entity defaultEmailProvider = getDefaultEmailProvider();
        EmailRecipientV2Entity defaultEmailRecipient = emailRecipientRepository.findDefaultEmailRecipientByUserId(user.getId()).orElseThrow(ApiError.DEFAULT_EMAIL_RECIPIENT_NOT_FOUND::createException);
        BookFileEntity bookFile = book.getPrimaryBookFile();
        sendEmailInVirtualThread(defaultEmailProvider, defaultEmailRecipient.getEmail(), book, bookFile);
    }

    public void emailBook(SendBookByEmailRequest request) {
        BookLoreUser user = authenticationService.getAuthenticatedUser();
        EmailProviderV2Entity emailProvider = emailProviderRepository.findByIdAndUserId(request.getProviderId(), user.getId())
                .orElseGet(() ->
                        emailProviderRepository.findSharedProviderById(request.getProviderId())
                                .orElseThrow(() -> ApiError.EMAIL_PROVIDER_NOT_FOUND.createException(request.getProviderId()))
                );
        BookEntity book = bookRepository.findByIdWithBookFiles(request.getBookId()).orElseThrow(() -> ApiError.BOOK_NOT_FOUND.createException(request.getBookId()));
        EmailRecipientV2Entity emailRecipient = emailRecipientRepository.findByIdAndUserId(request.getRecipientId(), user.getId()).orElseThrow(() -> ApiError.EMAIL_RECIPIENT_NOT_FOUND.createException(request.getRecipientId()));
        BookFileEntity bookFile = resolveBookFile(book, request.getBookFileId());
        sendEmailInVirtualThread(emailProvider, emailRecipient.getEmail(), book, bookFile);
    }

    private void sendEmailInVirtualThread(EmailProviderV2Entity emailProvider, String recipientEmail, BookEntity book, BookFileEntity bookFile) {
        String bookTitle = book.getMetadata().getTitle();
        String logMessage = "Email dispatch initiated for book: " + bookTitle + " to " + recipientEmail;
        notificationService.sendMessage(Topic.LOG, LogNotification.info(logMessage));
        log.info(logMessage);
        SecurityContextVirtualThread.runWithSecurityContext(() -> {
            try {
                sendEmail(emailProvider, recipientEmail, book, bookFile);
                auditService.log(AuditAction.BOOK_SENT, "Book", book.getId(), "Sent book: " + bookTitle + " to " + recipientEmail);
                String successMessage = "The book: " + bookTitle + " has been successfully sent to " + recipientEmail;
                notificationService.sendMessage(Topic.LOG, LogNotification.info(successMessage));
                log.info(successMessage);
            } catch (Exception e) {
                String errorMessage = "An error occurred while sending the book: " + bookTitle + " to " + recipientEmail + ". Error: " + e.getMessage();
                notificationService.sendMessage(Topic.LOG, LogNotification.error(errorMessage));
                log.error(errorMessage, e);
            }
        });
    }

    private void sendEmail(EmailProviderV2Entity emailProvider, String recipientEmail, BookEntity book, BookFileEntity bookFileEntity) throws MessagingException {
        JavaMailSenderImpl dynamicMailSender = setupMailSender(emailProvider);
        MimeMessage message = dynamicMailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true);
        helper.setFrom(StringUtils.firstNonEmpty(emailProvider.getFromAddress(), emailProvider.getUsername()));
        helper.setTo(recipientEmail);
        helper.setSubject("Your Book from Booklore: " + book.getMetadata().getTitle());
        helper.setText(generateEmailBody(book.getMetadata().getTitle()));
        File bookFile = new File(FileUtils.getBookFullPath(book, bookFileEntity));
        helper.addAttachment(bookFile.getName(), bookFile);
        dynamicMailSender.send(message);
        log.info("Book sent successfully to {}", recipientEmail);
    }

    private BookFileEntity resolveBookFile(BookEntity book, Long bookFileId) {
        if (bookFileId == null) {
            return book.getPrimaryBookFile();
        }
        return book.getBookFiles().stream()
                .filter(bf -> bf.getId().equals(bookFileId))
                .findFirst()
                .orElseThrow(() -> ApiError.FILE_NOT_FOUND.createException(bookFileId));
    }

    private JavaMailSenderImpl setupMailSender(EmailProviderV2Entity emailProvider) {
        JavaMailSenderImpl dynamicMailSender = new JavaMailSenderImpl();
        dynamicMailSender.setHost(emailProvider.getHost());
        dynamicMailSender.setPort(emailProvider.getPort());
        dynamicMailSender.setUsername(emailProvider.getUsername());
        dynamicMailSender.setPassword(emailProvider.getPassword());

        Properties mailProps = dynamicMailSender.getJavaMailProperties();
        mailProps.put("mail.smtp.auth", emailProvider.isAuth());

        ConnectionType connectionType = determineConnectionType(emailProvider);
        configureConnectionType(mailProps, connectionType, emailProvider);
        configureTimeouts(mailProps);

        String debugMode = System.getProperty("mail.debug", "false");
        mailProps.put("mail.debug", debugMode);

        log.info("Email configuration: Host={}, Port={}, Type={}, Timeouts=60s", emailProvider.getHost(), emailProvider.getPort(), connectionType);

        return dynamicMailSender;
    }

    private ConnectionType determineConnectionType(EmailProviderV2Entity emailProvider) {
        if (emailProvider.getPort() == 465) {
            return ConnectionType.SSL;
        } else if (emailProvider.getPort() == 587 && emailProvider.isStartTls()) {
            return ConnectionType.STARTTLS;
        } else if (emailProvider.isStartTls()) {
            return ConnectionType.STARTTLS;
        } else {
            return ConnectionType.PLAIN;
        }
    }

    private void configureConnectionType(Properties mailProps, ConnectionType connectionType, EmailProviderV2Entity emailProvider) {
        switch (connectionType) {
            case SSL -> {
                mailProps.put("mail.transport.protocol", "smtps");
                mailProps.put("mail.smtp.ssl.enable", "true");
                mailProps.put("mail.smtp.ssl.trust", emailProvider.getHost());
                mailProps.put("mail.smtp.starttls.enable", "false");
                mailProps.put("mail.smtp.ssl.protocols", "TLSv1.2,TLSv1.3");
                mailProps.put("mail.smtp.ssl.checkserveridentity", "false");
                mailProps.put("mail.smtp.ssl.socketFactory.class", "javax.net.ssl.SSLSocketFactory");
                mailProps.put("mail.smtp.ssl.socketFactory.fallback", "false");
            }
            case STARTTLS -> {
                mailProps.put("mail.transport.protocol", "smtp");
                mailProps.put("mail.smtp.starttls.enable", "true");
                mailProps.put("mail.smtp.starttls.required", "true");
                mailProps.put("mail.smtp.ssl.enable", "false");
            }
            case PLAIN -> {
                mailProps.put("mail.transport.protocol", "smtp");
                mailProps.put("mail.smtp.starttls.enable", "false");
                mailProps.put("mail.smtp.ssl.enable", "false");
            }
        }
    }

    private void configureTimeouts(Properties mailProps) {
        String connectionTimeout = System.getProperty("mail.smtp.connectiontimeout", "60000");
        String socketTimeout = System.getProperty("mail.smtp.timeout", "60000");
        String writeTimeout = System.getProperty("mail.smtp.writetimeout", "60000");

        mailProps.put("mail.smtp.connectiontimeout", connectionTimeout);
        mailProps.put("mail.smtp.timeout", socketTimeout);
        mailProps.put("mail.smtp.writetimeout", writeTimeout);
    }

    private String generateEmailBody(String bookTitle) {
        return String.format("""
                Hello,
                
                You have received a book from Booklore. Please find the attached file titled '%s' for your reading pleasure.
                
                Thank you for using Booklore! Hope you enjoy your book.
                """, bookTitle);
    }

    private EmailProviderV2Entity getDefaultEmailProvider() {
        BookLoreUser user = authenticationService.getAuthenticatedUser();

        Long defaultProviderId = preferenceRepository.findByUserId(user.getId())
                .map(UserEmailProviderPreferenceEntity::getDefaultProviderId)
                .orElseThrow(ApiError.DEFAULT_EMAIL_PROVIDER_NOT_FOUND::createException);

        return emailProviderRepository.findAccessibleProvider(defaultProviderId, user.getId())
                .orElseThrow(ApiError.DEFAULT_EMAIL_PROVIDER_NOT_FOUND::createException);
    }

    private enum ConnectionType {
        SSL,
        STARTTLS,
        PLAIN
    }
}