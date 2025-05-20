package com.adityachandel.booklore.service;

import com.adityachandel.booklore.exception.ApiError;
import com.adityachandel.booklore.model.dto.request.SendBookByEmailRequest;
import com.adityachandel.booklore.model.entity.BookEntity;
import com.adityachandel.booklore.model.entity.EmailProviderEntity;
import com.adityachandel.booklore.model.entity.EmailRecipientEntity;
import com.adityachandel.booklore.model.websocket.Topic;
import com.adityachandel.booklore.repository.BookRepository;
import com.adityachandel.booklore.repository.EmailProviderRepository;
import com.adityachandel.booklore.repository.EmailRecipientRepository;
import com.adityachandel.booklore.util.FileUtils;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.io.File;
import java.util.Properties;

import static com.adityachandel.booklore.model.websocket.LogNotification.createLogNotification;

@Slf4j
@Service
@AllArgsConstructor
public class EmailService {

    private final EmailProviderRepository emailProviderRepository;
    private final BookRepository bookRepository;
    private final EmailRecipientRepository emailRecipientRepository;
    private final NotificationService notificationService;

    public void emailBookQuick(Long bookId) {
        BookEntity book = bookRepository.findById(bookId).orElseThrow(() -> ApiError.BOOK_NOT_FOUND.createException(bookId));
        EmailProviderEntity defaultEmailProvider = emailProviderRepository.findDefaultEmailProvider().orElseThrow(ApiError.DEFAULT_EMAIL_PROVIDER_NOT_FOUND::createException);
        EmailRecipientEntity defaultEmailRecipient = emailRecipientRepository.findDefaultEmailRecipient().orElseThrow(ApiError.DEFAULT_EMAIL_RECIPIENT_NOT_FOUND::createException);

        sendEmailInVirtualThread(defaultEmailProvider, defaultEmailRecipient.getEmail(), book);
    }

    public void emailBook(SendBookByEmailRequest request) {
        EmailProviderEntity emailProvider = emailProviderRepository.findById(request.getProviderId()).orElseThrow(() -> ApiError.EMAIL_PROVIDER_NOT_FOUND.createException(request.getProviderId()));
        BookEntity book = bookRepository.findById(request.getBookId()).orElseThrow(() -> ApiError.BOOK_NOT_FOUND.createException(request.getBookId()));
        EmailRecipientEntity emailRecipient = emailRecipientRepository.findById(request.getRecipientId()).orElseThrow(() -> ApiError.EMAIL_RECIPIENT_NOT_FOUND.createException(request.getRecipientId()));

        sendEmailInVirtualThread(emailProvider, emailRecipient.getEmail(), book);
    }

    private void sendEmailInVirtualThread(EmailProviderEntity emailProvider, String recipientEmail, BookEntity book) {
        String bookTitle = book.getMetadata().getTitle();
        String logMessage = "Email dispatch initiated for book: " + bookTitle + " to " + recipientEmail;
        notificationService.sendMessage(Topic.LOG, createLogNotification(logMessage));
        log.info(logMessage);

        Thread.startVirtualThread(() -> {
            try {
                sendEmail(emailProvider, recipientEmail, book);
                String successMessage = "The book: " + bookTitle + " has been successfully sent to " + recipientEmail;
                notificationService.sendMessage(Topic.LOG, createLogNotification(successMessage));
                log.info(successMessage);
            } catch (Exception e) {
                String errorMessage = "An error occurred while sending the book: " + bookTitle + " to " + recipientEmail + ". Error: " + e.getMessage();
                notificationService.sendMessage(Topic.LOG, createLogNotification(errorMessage));
                log.error(errorMessage, e);
            }
        });
    }

    private void sendEmail(EmailProviderEntity emailProvider, String recipientEmail, BookEntity book) throws MessagingException {
        JavaMailSenderImpl dynamicMailSender = setupMailSender(emailProvider);
        MimeMessage message = dynamicMailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true);
        helper.setFrom(StringUtils.firstNonEmpty(emailProvider.getFromAddress(), emailProvider.getUsername()));
        helper.setTo(recipientEmail);
        helper.setSubject("Your Book from Booklore: " + book.getMetadata().getTitle());
        helper.setText(generateEmailBody(book.getMetadata().getTitle()));
        File bookFile = new File(FileUtils.getBookFullPath(book));
        helper.addAttachment(bookFile.getName(), bookFile);
        dynamicMailSender.send(message);
        log.info("Book sent successfully to {}", recipientEmail);
    }

    private JavaMailSenderImpl setupMailSender(EmailProviderEntity emailProvider) {
        JavaMailSenderImpl dynamicMailSender = new JavaMailSenderImpl();
        dynamicMailSender.setHost(emailProvider.getHost());
        dynamicMailSender.setPort(emailProvider.getPort());
        dynamicMailSender.setUsername(emailProvider.getUsername());
        dynamicMailSender.setPassword(emailProvider.getPassword());

        Properties mailProps = dynamicMailSender.getJavaMailProperties();
        mailProps.put("mail.smtp.auth", emailProvider.isAuth());
        mailProps.put("mail.smtp.starttls.enable", emailProvider.isStartTls());
        mailProps.put("mail.smtp.connectiontimeout", 15000);  // 15 seconds connection timeout
        mailProps.put("mail.smtp.timeout", 15000);            // 15 seconds socket timeout

        return dynamicMailSender;
    }

    private String generateEmailBody(String bookTitle) {
        return String.format("""
                Hello,
                
                You have received a book from Booklore. Please find the attached file titled '%s' for your reading pleasure.
                
                Thank you for using Booklore! We hope you enjoy your book.
                """, bookTitle);
    }
}