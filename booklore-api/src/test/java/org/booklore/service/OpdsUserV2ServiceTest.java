package org.booklore.service;

import org.booklore.config.security.service.AuthenticationService;
import org.booklore.mapper.OpdsUserV2Mapper;
import org.booklore.service.audit.AuditService;
import org.booklore.model.dto.BookLoreUser;
import org.booklore.model.dto.OpdsUserV2;
import org.booklore.model.dto.request.OpdsUserV2CreateRequest;
import org.booklore.model.entity.BookLoreUserEntity;
import org.booklore.model.entity.OpdsUserV2Entity;
import org.booklore.repository.OpdsUserV2Repository;
import org.booklore.repository.UserRepository;
import org.booklore.service.opds.OpdsUserV2Service;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OpdsUserV2ServiceTest {

    @Mock
    private OpdsUserV2Repository opdsUserV2Repository;
    @Mock
    private AuthenticationService authenticationService;
    @Mock
    private UserRepository userRepository;
    @Mock
    private OpdsUserV2Mapper mapper;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private AuditService auditService;

    @InjectMocks
    private OpdsUserV2Service service;

    @Captor
    private ArgumentCaptor<OpdsUserV2Entity> entityCaptor;

    @Test
    void getOpdsUsers_returnsMappedDtos() {
        BookLoreUser authUser = mock(BookLoreUser.class);
        when(authUser.getId()).thenReturn(1L);
        when(authenticationService.getAuthenticatedUser()).thenReturn(authUser);

        OpdsUserV2Entity entity = mock(OpdsUserV2Entity.class);
        List<OpdsUserV2Entity> entities = List.of(entity);
        when(opdsUserV2Repository.findByUserId(1L)).thenReturn(entities);

        OpdsUserV2 dto = mock(OpdsUserV2.class);
        List<OpdsUserV2> dtos = List.of(dto);
        when(mapper.toDto(entities)).thenReturn(dtos);

        List<OpdsUserV2> result = service.getOpdsUsers();

        assertSame(dtos, result);
        verify(opdsUserV2Repository).findByUserId(1L);
        verify(mapper).toDto(entities);
    }

    @Test
    void createOpdsUser_success_savesWithEncodedPasswordAndReturnsDto() {
        BookLoreUser authUser = mock(BookLoreUser.class);
        when(authUser.getId()).thenReturn(1L);
        when(authenticationService.getAuthenticatedUser()).thenReturn(authUser);

        BookLoreUserEntity userEntity = mock(BookLoreUserEntity.class);
        when(userRepository.findById(1L)).thenReturn(Optional.of(userEntity));

        OpdsUserV2CreateRequest request = mock(OpdsUserV2CreateRequest.class);
        when(request.getUsername()).thenReturn("alice");
        when(request.getPassword()).thenReturn("plaintext");

        when(passwordEncoder.encode("plaintext")).thenReturn("encoded-pass");

        OpdsUserV2Entity savedEntity = mock(OpdsUserV2Entity.class);
        OpdsUserV2 dto = mock(OpdsUserV2.class);
        when(opdsUserV2Repository.save(any())).thenReturn(savedEntity);
        when(mapper.toDto(savedEntity)).thenReturn(dto);

        OpdsUserV2 result = service.createOpdsUser(request);

        assertSame(dto, result);
        verify(passwordEncoder).encode("plaintext");
        verify(opdsUserV2Repository).save(entityCaptor.capture());
        OpdsUserV2Entity captured = entityCaptor.getValue();
        // we cannot assume concrete builder internals here; assert via getters that exist on entity
        assertEquals("alice", captured.getUsername());
        assertEquals("encoded-pass", captured.getPasswordHash());
        assertSame(userEntity, captured.getUser());
        verify(mapper).toDto(savedEntity);
    }

    @Test
    void createOpdsUser_userNotFound_throwsUsernameNotFoundException() {
        BookLoreUser authUser = mock(BookLoreUser.class);
        when(authUser.getId()).thenReturn(2L);
        when(authenticationService.getAuthenticatedUser()).thenReturn(authUser);
        when(userRepository.findById(2L)).thenReturn(Optional.empty());

        OpdsUserV2CreateRequest request = mock(OpdsUserV2CreateRequest.class);

        assertThrows(UsernameNotFoundException.class, () -> service.createOpdsUser(request));
        verify(opdsUserV2Repository, never()).save(any());
    }

    @Test
    void deleteOpdsUser_deletesWhenOwner() {
        BookLoreUser authUser = mock(BookLoreUser.class);
        when(authUser.getId()).thenReturn(10L);
        when(authenticationService.getAuthenticatedUser()).thenReturn(authUser);

        BookLoreUserEntity ownerEntity = mock(BookLoreUserEntity.class);
        when(ownerEntity.getId()).thenReturn(10L);

        OpdsUserV2Entity target = mock(OpdsUserV2Entity.class);
        when(target.getUser()).thenReturn(ownerEntity);

        when(opdsUserV2Repository.findById(100L)).thenReturn(Optional.of(target));

        service.deleteOpdsUser(100L);

        verify(opdsUserV2Repository).delete(target);
    }

    @Test
    void deleteOpdsUser_throwsAccessDeniedWhenNotOwner() {
        BookLoreUser authUser = mock(BookLoreUser.class);
        when(authUser.getId()).thenReturn(11L);
        when(authenticationService.getAuthenticatedUser()).thenReturn(authUser);

        BookLoreUserEntity ownerEntity = mock(BookLoreUserEntity.class);
        when(ownerEntity.getId()).thenReturn(9L);

        OpdsUserV2Entity target = mock(OpdsUserV2Entity.class);
        when(target.getUser()).thenReturn(ownerEntity);

        when(opdsUserV2Repository.findById(200L)).thenReturn(Optional.of(target));

        assertThrows(AccessDeniedException.class, () -> service.deleteOpdsUser(200L));
        verify(opdsUserV2Repository, never()).delete(any());
    }

    @Test
    void getOpdsUsers_returnsEmptyListWhenNoUsers() {
        BookLoreUser authUser = mock(BookLoreUser.class);
        when(authUser.getId()).thenReturn(5L);
        when(authenticationService.getAuthenticatedUser()).thenReturn(authUser);

        List<OpdsUserV2Entity> emptyEntities = List.of();
        when(opdsUserV2Repository.findByUserId(5L)).thenReturn(emptyEntities);

        List<OpdsUserV2> emptyDtos = List.of();
        when(mapper.toDto(emptyEntities)).thenReturn(emptyDtos);

        List<OpdsUserV2> result = service.getOpdsUsers();

        assertNotNull(result);
        assertTrue(result.isEmpty());
        verify(opdsUserV2Repository).findByUserId(5L);
        verify(mapper).toDto(emptyEntities);
    }

    @Test
    void deleteOpdsUser_userNotFound_throwsRuntimeException() {
        BookLoreUser authUser = mock(BookLoreUser.class);
        when(authenticationService.getAuthenticatedUser()).thenReturn(authUser);

        when(opdsUserV2Repository.findById(300L)).thenReturn(Optional.empty());

        RuntimeException ex = assertThrows(RuntimeException.class, () -> service.deleteOpdsUser(300L));
        assertTrue(ex.getMessage().contains("User not found with ID: 300"));
        verify(opdsUserV2Repository, never()).delete(any());
    }

    @Test
    void createOpdsUser_passwordEncoderThrows_propagatesException() {
        BookLoreUser authUser = mock(BookLoreUser.class);
        when(authUser.getId()).thenReturn(6L);
        when(authenticationService.getAuthenticatedUser()).thenReturn(authUser);

        BookLoreUserEntity userEntity = mock(BookLoreUserEntity.class);
        when(userRepository.findById(6L)).thenReturn(Optional.of(userEntity));

        OpdsUserV2CreateRequest request = mock(OpdsUserV2CreateRequest.class);
        when(request.getUsername()).thenReturn("bob");
        when(request.getPassword()).thenReturn("plaintext");

        when(passwordEncoder.encode("plaintext")).thenThrow(new IllegalArgumentException("encoding failed"));

        assertThrows(IllegalArgumentException.class, () -> service.createOpdsUser(request));
        verify(opdsUserV2Repository, never()).save(any());
    }

    @Test
    void createOpdsUser_usernameNull_savedWithNullUsername() {
        BookLoreUser authUser = mock(BookLoreUser.class);
        when(authUser.getId()).thenReturn(7L);
        when(authenticationService.getAuthenticatedUser()).thenReturn(authUser);

        BookLoreUserEntity userEntity = mock(BookLoreUserEntity.class);
        when(userRepository.findById(7L)).thenReturn(Optional.of(userEntity));

        OpdsUserV2CreateRequest request = mock(OpdsUserV2CreateRequest.class);
        when(request.getUsername()).thenReturn(null);
        when(request.getPassword()).thenReturn("secret");
        when(passwordEncoder.encode("secret")).thenReturn("encoded-secret");

        OpdsUserV2Entity savedEntity = mock(OpdsUserV2Entity.class);
        OpdsUserV2 dto = mock(OpdsUserV2.class);
        when(opdsUserV2Repository.save(any())).thenReturn(savedEntity);
        when(mapper.toDto(savedEntity)).thenReturn(dto);

        OpdsUserV2 result = service.createOpdsUser(request);

        assertSame(dto, result);
        verify(opdsUserV2Repository).save(entityCaptor.capture());
        OpdsUserV2Entity captured = entityCaptor.getValue();
        assertNull(captured.getUsername());
        assertEquals("encoded-secret", captured.getPasswordHash());
        assertSame(userEntity, captured.getUser());
    }
}
