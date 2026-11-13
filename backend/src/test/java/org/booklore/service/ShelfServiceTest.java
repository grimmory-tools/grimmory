package org.booklore.service;

import org.booklore.config.security.service.AuthenticationService;
import org.booklore.exception.APIException;
import org.booklore.mapper.ShelfMapper;
import org.booklore.service.audit.AuditService;
import org.booklore.model.dto.BookLoreUser;
import org.booklore.model.dto.Shelf;
import org.booklore.model.dto.request.ShelfCreateRequest;
import org.booklore.model.entity.BookLoreUserEntity;
import org.booklore.model.entity.ShelfEntity;
import org.booklore.model.enums.IconType;
import org.booklore.repository.BookRepository;
import org.booklore.repository.ShelfRepository;
import org.booklore.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

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
                .iconType(IconType.LUCIDE)
                .build();

        when(authenticationService.getAuthenticatedUser()).thenReturn(user);
        when(shelfRepository.existsByUserIdAndName(1L, "My Shelf")).thenReturn(false);
        when(userRepository.findById(1L)).thenReturn(Optional.of(userEntity));
        when(shelfRepository.save(any(ShelfEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(shelfMapper.toShelf(any(ShelfEntity.class))).thenReturn(
                Shelf.builder().name("My Shelf").icon("heart").iconType(IconType.LUCIDE).build());

        shelfService.createShelf(request);

        ArgumentCaptor<ShelfEntity> captor = ArgumentCaptor.forClass(ShelfEntity.class);
        verify(shelfRepository).save(captor.capture());

        ShelfEntity saved = captor.getValue();
        assertEquals("heart", saved.getIcon());
        assertEquals(IconType.LUCIDE, saved.getIconType());
    }

    @Test
    void updateShelf_withNullIcon_shouldClearIconValues() {
        ShelfEntity existingShelf = ShelfEntity.builder()
                .id(1L)
                .name("Old Shelf")
                .icon("star")
                .iconType(IconType.LUCIDE)
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
                .iconType(IconType.LUCIDE)
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

    @ParameterizedTest
    @ValueSource(strings = {"Nuno's Kobo", "kobo", "Kobo ", "My Shelf"})
    void updateShelf_renamingKoboShelf_shouldBeRejected(String newName) {
        ShelfEntity koboShelf = ShelfEntity.builder()
                .id(1L)
                .name("Kobo")
                .icon("tablet")
                .iconType(IconType.LUCIDE)
                .user(userEntity)
                .build();

        ShelfCreateRequest request = ShelfCreateRequest.builder()
                .name(newName)
                .icon("tablet")
                .iconType(IconType.LUCIDE)
                .build();

        when(shelfRepository.findById(1L)).thenReturn(Optional.of(koboShelf));

        APIException exception = assertThrows(APIException.class, () -> shelfService.updateShelf(1L, request));

        assertEquals(HttpStatus.FORBIDDEN, exception.getStatus());
        assertEquals("Kobo", koboShelf.getName());
        verify(shelfRepository, never()).save(any(ShelfEntity.class));
    }

    @Test
    void updateShelf_koboShelfWithSameName_shouldStillUpdateIcon() {
        ShelfEntity koboShelf = ShelfEntity.builder()
                .id(1L)
                .name("Kobo")
                .icon("tablet")
                .iconType(IconType.LUCIDE)
                .user(userEntity)
                .build();

        ShelfCreateRequest request = ShelfCreateRequest.builder()
                .name("Kobo")
                .icon("book")
                .iconType(IconType.LUCIDE)
                .build();

        when(shelfRepository.findById(1L)).thenReturn(Optional.of(koboShelf));
        when(shelfRepository.save(any(ShelfEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(shelfMapper.toShelf(any(ShelfEntity.class))).thenReturn(Shelf.builder().name("Kobo").icon("book").build());

        shelfService.updateShelf(1L, request);

        ArgumentCaptor<ShelfEntity> captor = ArgumentCaptor.forClass(ShelfEntity.class);
        verify(shelfRepository).save(captor.capture());

        ShelfEntity saved = captor.getValue();
        assertEquals("Kobo", saved.getName());
        assertEquals("book", saved.getIcon());
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
