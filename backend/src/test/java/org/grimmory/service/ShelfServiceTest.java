package org.grimmory.service;

import org.grimmory.config.security.service.AuthenticationService;
import org.grimmory.mapper.ShelfMapper;
import org.grimmory.service.audit.AuditService;
import org.grimmory.model.dto.BookLoreUser;
import org.grimmory.model.dto.Shelf;
import org.grimmory.model.dto.request.ShelfCreateRequest;
import org.grimmory.model.entity.BookLoreUserEntity;
import org.grimmory.model.entity.ShelfEntity;
import org.grimmory.model.enums.IconType;
import org.grimmory.repository.BookRepository;
import org.grimmory.repository.ShelfRepository;
import org.grimmory.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ShelfServiceTest {

    @Mock
    private ShelfRepository shelfRepository;
    @Mock
    private BookRepository bookRepository;
    @Mock
    private ShelfMapper shelfMapper;
    @Mock
    private AuthenticationService authenticationService;
    @Mock
    private UserRepository userRepository;
    @Mock
    private AuditService auditService;

    @InjectMocks
    private ShelfService shelfService;

    private BookLoreUser user;
    private BookLoreUserEntity userEntity;

    @BeforeEach
    void setUp() {
        user = BookLoreUser.builder().id(1L).isDefaultPassword(false).build();
        userEntity = BookLoreUserEntity.builder().id(1L).username("testuser").build();
    }

    @Test
    void createShelf_withNullIcon_shouldPersistNullIconValues() {
        ShelfCreateRequest request = ShelfCreateRequest.builder()
                .name("My Shelf")
                .icon(null)
                .iconType(null)
                .build();

        when(authenticationService.getAuthenticatedUser()).thenReturn(user);
        when(shelfRepository.existsByUserIdAndName(1L, "My Shelf")).thenReturn(false);
        when(userRepository.findById(1L)).thenReturn(Optional.of(userEntity));
        when(shelfRepository.save(any(ShelfEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(shelfMapper.toShelf(any(ShelfEntity.class))).thenReturn(Shelf.builder().name("My Shelf").build());

        shelfService.createShelf(request);

        ArgumentCaptor<ShelfEntity> captor = ArgumentCaptor.forClass(ShelfEntity.class);
        verify(shelfRepository).save(captor.capture());

        ShelfEntity saved = captor.getValue();
        assertNull(saved.getIcon());
        assertNull(saved.getIconType());
        assertEquals("My Shelf", saved.getName());
    }

    @Test
    void createShelf_withIcon_shouldPersistIconValues() {
        ShelfCreateRequest request = ShelfCreateRequest.builder()
                .name("My Shelf")
                .icon("heart")
                .iconType(IconType.PRIME_NG)
                .build();

        when(authenticationService.getAuthenticatedUser()).thenReturn(user);
        when(shelfRepository.existsByUserIdAndName(1L, "My Shelf")).thenReturn(false);
        when(userRepository.findById(1L)).thenReturn(Optional.of(userEntity));
        when(shelfRepository.save(any(ShelfEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(shelfMapper.toShelf(any(ShelfEntity.class))).thenReturn(
                Shelf.builder().name("My Shelf").icon("heart").iconType(IconType.PRIME_NG).build());

        shelfService.createShelf(request);

        ArgumentCaptor<ShelfEntity> captor = ArgumentCaptor.forClass(ShelfEntity.class);
        verify(shelfRepository).save(captor.capture());

        ShelfEntity saved = captor.getValue();
        assertEquals("heart", saved.getIcon());
        assertEquals(IconType.PRIME_NG, saved.getIconType());
    }

    @Test
    void updateShelf_withNullIcon_shouldClearIconValues() {
        ShelfEntity existingShelf = ShelfEntity.builder()
                .id(1L)
                .name("Old Shelf")
                .icon("star")
                .iconType(IconType.PRIME_NG)
                .user(userEntity)
                .build();

        ShelfCreateRequest request = ShelfCreateRequest.builder()
                .name("Updated Shelf")
                .icon(null)
                .iconType(null)
                .build();

        when(shelfRepository.findById(1L)).thenReturn(Optional.of(existingShelf));
        when(shelfRepository.save(any(ShelfEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(shelfMapper.toShelf(any(ShelfEntity.class))).thenReturn(Shelf.builder().name("Updated Shelf").build());

        shelfService.updateShelf(1L, request);

        ArgumentCaptor<ShelfEntity> captor = ArgumentCaptor.forClass(ShelfEntity.class);
        verify(shelfRepository).save(captor.capture());

        ShelfEntity saved = captor.getValue();
        assertNull(saved.getIcon());
        assertNull(saved.getIconType());
        assertEquals("Updated Shelf", saved.getName());
    }

    @Test
    void updateShelf_withIcon_shouldPreserveIconValues() {
        ShelfEntity existingShelf = ShelfEntity.builder()
                .id(1L)
                .name("Old Shelf")
                .icon("star")
                .iconType(IconType.PRIME_NG)
                .user(userEntity)
                .build();

        ShelfCreateRequest request = ShelfCreateRequest.builder()
                .name("Updated Shelf")
                .icon("bookmark")
                .iconType(IconType.CUSTOM_SVG)
                .build();

        when(shelfRepository.findById(1L)).thenReturn(Optional.of(existingShelf));
        when(shelfRepository.save(any(ShelfEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(shelfMapper.toShelf(any(ShelfEntity.class))).thenReturn(
                Shelf.builder().name("Updated Shelf").icon("bookmark").iconType(IconType.CUSTOM_SVG).build());

        shelfService.updateShelf(1L, request);

        ArgumentCaptor<ShelfEntity> captor = ArgumentCaptor.forClass(ShelfEntity.class);
        verify(shelfRepository).save(captor.capture());

        ShelfEntity saved = captor.getValue();
        assertEquals("bookmark", saved.getIcon());
        assertEquals(IconType.CUSTOM_SVG, saved.getIconType());
    }

    @Test
    void createShelf_withCustomSvgIcon_shouldPersistCorrectIconType() {
        ShelfCreateRequest request = ShelfCreateRequest.builder()
                .name("SVG Shelf")
                .icon("<svg>...</svg>")
                .iconType(IconType.CUSTOM_SVG)
                .build();

        when(authenticationService.getAuthenticatedUser()).thenReturn(user);
        when(shelfRepository.existsByUserIdAndName(1L, "SVG Shelf")).thenReturn(false);
        when(userRepository.findById(1L)).thenReturn(Optional.of(userEntity));
        when(shelfRepository.save(any(ShelfEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(shelfMapper.toShelf(any(ShelfEntity.class))).thenReturn(
                Shelf.builder().name("SVG Shelf").icon("<svg>...</svg>").iconType(IconType.CUSTOM_SVG).build());

        shelfService.createShelf(request);

        ArgumentCaptor<ShelfEntity> captor = ArgumentCaptor.forClass(ShelfEntity.class);
        verify(shelfRepository).save(captor.capture());

        ShelfEntity saved = captor.getValue();
        assertEquals("<svg>...</svg>", saved.getIcon());
        assertEquals(IconType.CUSTOM_SVG, saved.getIconType());
    }
}
