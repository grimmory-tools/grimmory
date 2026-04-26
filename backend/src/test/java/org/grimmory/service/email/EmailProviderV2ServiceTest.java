package org.booklore.service.email;

import org.booklore.config.security.service.AuthenticationService;
import org.booklore.mapper.EmailProviderV2Mapper;
import org.booklore.model.dto.BookLoreUser;
import org.booklore.model.dto.EmailProviderV2;
import org.booklore.model.dto.request.CreateEmailProviderRequest;
import org.booklore.model.entity.EmailProviderV2Entity;
import org.booklore.model.entity.UserEmailProviderPreferenceEntity;
import org.booklore.repository.EmailProviderV2Repository;
import org.booklore.repository.UserEmailProviderPreferenceRepository;
import org.booklore.service.audit.AuditService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EmailProviderV2ServiceTest {

    @Mock
    private EmailProviderV2Repository repository;

    @Mock
    private UserEmailProviderPreferenceRepository preferenceRepository;

    @Mock
    private EmailProviderV2Mapper mapper;

    @Mock
    private AuthenticationService authService;

    @Mock
    private AuditService auditService;

    @InjectMocks
    private EmailProviderV2Service emailProviderV2Service;

    private BookLoreUser adminUser;
    private BookLoreUser regularUser;
    private EmailProviderV2Entity savedEntity;
    private EmailProviderV2 providerDto;

    @BeforeEach
    void setUp() {
        BookLoreUser.UserPermissions adminPerms = new BookLoreUser.UserPermissions();
        adminPerms.setAdmin(true);
        adminUser = BookLoreUser.builder().id(1L).username("admin").permissions(adminPerms).build();

        BookLoreUser.UserPermissions regularPerms = new BookLoreUser.UserPermissions();
        regularPerms.setAdmin(false);
        regularUser = BookLoreUser.builder().id(2L).username("user").permissions(regularPerms).build();

        savedEntity = EmailProviderV2Entity.builder()
                .id(10L)
                .userId(1L)
                .host("smtp.test.com")
                .port(587)
                .build();

        providerDto = EmailProviderV2.builder()
                .id(10L)
                .userId(1L)
                .host("smtp.test.com")
                .port(587)
                .shared(false)
                .build();
    }

    @Test
    void createEmailProvider_nullShared_setsSharedFalse() {
        CreateEmailProviderRequest request = CreateEmailProviderRequest.builder()
                .name("Test")
                .host("smtp.test.com")
                .port(587)
                .shared(null)
                .build();

        when(authService.getAuthenticatedUser()).thenReturn(adminUser);
        when(mapper.toEntity(request)).thenReturn(EmailProviderV2Entity.builder().build());
        when(repository.save(any())).thenReturn(savedEntity);
        when(preferenceRepository.findByUserId(1L)).thenReturn(Optional.empty());
        when(mapper.toDTO(eq(savedEntity), any())).thenReturn(providerDto);

        emailProviderV2Service.createEmailProvider(request);

        verify(repository).save(argThat(entity -> !entity.isShared()));
    }

    @Test
    void createEmailProvider_sharedFalse_setsSharedFalse() {
        CreateEmailProviderRequest request = CreateEmailProviderRequest.builder()
                .name("Test")
                .host("smtp.test.com")
                .port(587)
                .shared(false)
                .build();

        when(authService.getAuthenticatedUser()).thenReturn(adminUser);
        when(mapper.toEntity(request)).thenReturn(EmailProviderV2Entity.builder().build());
        when(repository.save(any())).thenReturn(savedEntity);
        when(preferenceRepository.findByUserId(1L)).thenReturn(Optional.empty());
        when(mapper.toDTO(eq(savedEntity), any())).thenReturn(providerDto);

        emailProviderV2Service.createEmailProvider(request);

        verify(repository).save(argThat(entity -> !entity.isShared()));
    }

    @Test
    void createEmailProvider_sharedTrue_adminSetsSharedTrue() {
        CreateEmailProviderRequest request = CreateEmailProviderRequest.builder()
                .name("Test")
                .host("smtp.test.com")
                .port(587)
                .shared(true)
                .build();

        when(authService.getAuthenticatedUser()).thenReturn(adminUser);
        when(mapper.toEntity(request)).thenReturn(EmailProviderV2Entity.builder().build());
        when(repository.save(any())).thenReturn(savedEntity);
        when(preferenceRepository.findByUserId(1L)).thenReturn(Optional.empty());
        when(mapper.toDTO(eq(savedEntity), any())).thenReturn(providerDto);

        emailProviderV2Service.createEmailProvider(request);

        verify(repository).save(argThat(EmailProviderV2Entity::isShared));
    }

    @Test
    void createEmailProvider_sharedTrue_nonAdminSetsSharedFalse() {
        CreateEmailProviderRequest request = CreateEmailProviderRequest.builder()
                .name("Test")
                .host("smtp.test.com")
                .port(587)
                .shared(true)
                .build();

        when(authService.getAuthenticatedUser()).thenReturn(regularUser);
        when(mapper.toEntity(request)).thenReturn(EmailProviderV2Entity.builder().build());
        when(repository.save(any())).thenReturn(savedEntity);
        when(preferenceRepository.findByUserId(2L)).thenReturn(Optional.empty());
        when(mapper.toDTO(eq(savedEntity), any())).thenReturn(providerDto);

        emailProviderV2Service.createEmailProvider(request);

        verify(repository).save(argThat(entity -> !entity.isShared()));
    }

    @Test
    void updateEmailProvider_nullShared_adminSetsSharedFalse() {
        CreateEmailProviderRequest request = CreateEmailProviderRequest.builder()
                .name("Updated")
                .host("smtp.test.com")
                .port(587)
                .shared(null)
                .build();

        EmailProviderV2Entity existingEntity = EmailProviderV2Entity.builder()
                .id(10L)
                .userId(1L)
                .host("smtp.test.com")
                .port(587)
                .shared(true)
                .build();

        when(authService.getAuthenticatedUser()).thenReturn(adminUser);
        when(repository.findByIdAndUserId(10L, 1L)).thenReturn(Optional.of(existingEntity));
        when(repository.save(any())).thenReturn(existingEntity);
        when(preferenceRepository.findByUserId(1L)).thenReturn(Optional.empty());
        when(mapper.toDTO(eq(existingEntity), any())).thenReturn(providerDto);

        emailProviderV2Service.updateEmailProvider(10L, request);

        assertFalse(existingEntity.isShared());
    }

    @Test
    void updateEmailProvider_sharedTrue_adminSetsSharedTrue() {
        CreateEmailProviderRequest request = CreateEmailProviderRequest.builder()
                .name("Updated")
                .host("smtp.test.com")
                .port(587)
                .shared(true)
                .build();

        EmailProviderV2Entity existingEntity = EmailProviderV2Entity.builder()
                .id(10L)
                .userId(1L)
                .host("smtp.test.com")
                .port(587)
                .shared(false)
                .build();

        when(authService.getAuthenticatedUser()).thenReturn(adminUser);
        when(repository.findByIdAndUserId(10L, 1L)).thenReturn(Optional.of(existingEntity));
        when(repository.save(any())).thenReturn(existingEntity);
        when(preferenceRepository.findByUserId(1L)).thenReturn(Optional.empty());
        when(mapper.toDTO(eq(existingEntity), any())).thenReturn(providerDto);

        emailProviderV2Service.updateEmailProvider(10L, request);

        assertTrue(existingEntity.isShared());
    }

    @Test
    void updateEmailProvider_sharedTrue_nonAdminDoesNotChangeShared() {
        CreateEmailProviderRequest request = CreateEmailProviderRequest.builder()
                .name("Updated")
                .host("smtp.test.com")
                .port(587)
                .shared(true)
                .build();

        EmailProviderV2Entity existingEntity = EmailProviderV2Entity.builder()
                .id(10L)
                .userId(2L)
                .host("smtp.test.com")
                .port(587)
                .shared(false)
                .build();

        when(authService.getAuthenticatedUser()).thenReturn(regularUser);
        when(repository.findByIdAndUserId(10L, 2L)).thenReturn(Optional.of(existingEntity));
        when(repository.save(any())).thenReturn(existingEntity);
        when(preferenceRepository.findByUserId(2L)).thenReturn(Optional.empty());
        when(mapper.toDTO(eq(existingEntity), any())).thenReturn(providerDto);

        emailProviderV2Service.updateEmailProvider(10L, request);

        assertFalse(existingEntity.isShared());
    }
}
