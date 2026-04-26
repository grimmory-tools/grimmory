package org.grimmory.service.library;

import org.grimmory.config.security.service.AuthenticationService;
import org.grimmory.mapper.LibraryMapper;
import org.grimmory.service.audit.AuditService;
import org.grimmory.model.dto.BookLoreUser;
import org.grimmory.model.dto.Library;
import org.grimmory.model.dto.LibraryPath;
import org.grimmory.model.dto.request.CreateLibraryRequest;
import org.grimmory.model.entity.LibraryEntity;
import org.grimmory.model.entity.LibraryPathEntity;
import org.grimmory.model.enums.IconType;
import org.grimmory.repository.LibraryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.grimmory.mapper.BookMapper;
import org.grimmory.repository.BookRepository;
import org.grimmory.repository.LibraryPathRepository;
import org.grimmory.repository.UserRepository;
import org.grimmory.model.entity.BookLoreUserEntity;
import org.grimmory.service.NotificationService;
import org.grimmory.service.monitoring.LibraryWatchService;
import org.grimmory.util.FileService;
import org.springframework.context.ApplicationEventPublisher;

import java.util.Collections;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executor;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LibraryServiceIconTest {

    @Mock
    private LibraryRepository libraryRepository;
    @Mock
    private LibraryPathRepository libraryPathRepository;
    @Mock
    private BookRepository bookRepository;
    @Mock
    private LibraryProcessingService libraryProcessingService;
    @Mock
    private BookMapper bookMapper;
    @Mock
    private LibraryMapper libraryMapper;
    @Mock
    private NotificationService notificationService;
    @Mock
    private FileService fileService;
    @Mock
    private LibraryWatchService libraryWatchService;
    @Mock
    private AuthenticationService authenticationService;
    @Mock
    private UserRepository userRepository;
    @Mock
    private AuditService auditService;
    @Mock
    private ApplicationEventPublisher eventPublisher;
    @Mock
    private Executor taskExecutor;

    @InjectMocks
    private LibraryService libraryService;

    private BookLoreUser user;
    private BookLoreUserEntity userEntity;

    @BeforeEach
    void setUp() {
        user = BookLoreUser.builder().id(1L).isDefaultPassword(false).build();
        userEntity = BookLoreUserEntity.builder().id(1L).username("testuser").build();
    }

    @Test
    void updateLibrary_withNullIcon_shouldClearIconValues() {
        LibraryEntity existing = LibraryEntity.builder()
                .id(1L)
                .name("My Library")
                .icon("book")
                .iconType(IconType.PRIME_NG)
                .libraryPaths(new ArrayList<>())
                .watch(false)
                .build();

        CreateLibraryRequest request = CreateLibraryRequest.builder()
                .name("Updated Library")
                .icon(null)
                .iconType(null)
                .paths(Collections.emptyList())
                .watch(false)
                .build();

        when(libraryRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(libraryRepository.save(any(LibraryEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(libraryMapper.toLibrary(any(LibraryEntity.class))).thenReturn(Library.builder().name("Updated Library").build());

        libraryService.updateLibrary(request, 1L);

        ArgumentCaptor<LibraryEntity> captor = ArgumentCaptor.forClass(LibraryEntity.class);
        verify(libraryRepository).save(captor.capture());

        LibraryEntity saved = captor.getValue();
        assertNull(saved.getIcon());
        assertNull(saved.getIconType());
        assertEquals("Updated Library", saved.getName());
    }

    @Test
    void updateLibrary_withIcon_shouldPreserveIconValues() {
        LibraryEntity existing = LibraryEntity.builder()
                .id(1L)
                .name("My Library")
                .icon("book")
                .iconType(IconType.PRIME_NG)
                .libraryPaths(new ArrayList<>())
                .watch(false)
                .build();

        CreateLibraryRequest request = CreateLibraryRequest.builder()
                .name("Updated Library")
                .icon("folder")
                .iconType(IconType.CUSTOM_SVG)
                .paths(Collections.emptyList())
                .watch(false)
                .build();

        when(libraryRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(libraryRepository.save(any(LibraryEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(libraryMapper.toLibrary(any(LibraryEntity.class))).thenReturn(
                Library.builder().name("Updated Library").icon("folder").iconType(IconType.CUSTOM_SVG).build());

        libraryService.updateLibrary(request, 1L);

        ArgumentCaptor<LibraryEntity> captor = ArgumentCaptor.forClass(LibraryEntity.class);
        verify(libraryRepository).save(captor.capture());

        LibraryEntity saved = captor.getValue();
        assertEquals("folder", saved.getIcon());
        assertEquals(IconType.CUSTOM_SVG, saved.getIconType());
    }

    @Test
    void updateLibrary_fromIconToNull_shouldAllowRemovingIcon() {
        LibraryEntity existing = LibraryEntity.builder()
                .id(1L)
                .name("Library With Icon")
                .icon("star")
                .iconType(IconType.PRIME_NG)
                .libraryPaths(new ArrayList<>())
                .watch(false)
                .build();

        CreateLibraryRequest request = CreateLibraryRequest.builder()
                .name("Library With Icon")
                .icon(null)
                .iconType(null)
                .paths(Collections.emptyList())
                .watch(false)
                .build();

        when(libraryRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(libraryRepository.save(any(LibraryEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(libraryMapper.toLibrary(any(LibraryEntity.class))).thenReturn(Library.builder().name("Library With Icon").build());

        libraryService.updateLibrary(request, 1L);

        ArgumentCaptor<LibraryEntity> captor = ArgumentCaptor.forClass(LibraryEntity.class);
        verify(libraryRepository).save(captor.capture());

        LibraryEntity saved = captor.getValue();
        assertNull(saved.getIcon());
        assertNull(saved.getIconType());
    }

    @Test
    void createLibrary_withNullIcon_shouldPersistNullIconValues() {
        CreateLibraryRequest request = CreateLibraryRequest.builder()
                .name("No Icon Library")
                .icon(null)
                .iconType(null)
                .paths(Collections.emptyList())
                .watch(false)
                .build();

        when(authenticationService.getAuthenticatedUser()).thenReturn(user);
        when(userRepository.findById(1L)).thenReturn(Optional.of(userEntity));
        when(libraryRepository.save(any(LibraryEntity.class))).thenAnswer(invocation -> {
            LibraryEntity entity = invocation.getArgument(0);
            entity.setId(1L);
            return entity;
        });
        when(libraryMapper.toLibrary(any(LibraryEntity.class))).thenReturn(Library.builder().name("No Icon Library").build());

        libraryService.createLibrary(request);

        ArgumentCaptor<LibraryEntity> captor = ArgumentCaptor.forClass(LibraryEntity.class);
        verify(libraryRepository).save(captor.capture());

        LibraryEntity saved = captor.getValue();
        assertNull(saved.getIcon());
        assertNull(saved.getIconType());
        assertEquals("No Icon Library", saved.getName());
    }

    @Test
    void createLibrary_withIcon_shouldPersistIconValues() {
        CreateLibraryRequest request = CreateLibraryRequest.builder()
                .name("Icon Library")
                .icon("book")
                .iconType(IconType.PRIME_NG)
                .paths(Collections.emptyList())
                .watch(false)
                .build();

        when(authenticationService.getAuthenticatedUser()).thenReturn(user);
        when(userRepository.findById(1L)).thenReturn(Optional.of(userEntity));
        when(libraryRepository.save(any(LibraryEntity.class))).thenAnswer(invocation -> {
            LibraryEntity entity = invocation.getArgument(0);
            entity.setId(1L);
            return entity;
        });
        when(libraryMapper.toLibrary(any(LibraryEntity.class))).thenReturn(
                Library.builder().name("Icon Library").icon("book").iconType(IconType.PRIME_NG).build());

        libraryService.createLibrary(request);

        ArgumentCaptor<LibraryEntity> captor = ArgumentCaptor.forClass(LibraryEntity.class);
        verify(libraryRepository).save(captor.capture());

        LibraryEntity saved = captor.getValue();
        assertEquals("book", saved.getIcon());
        assertEquals(IconType.PRIME_NG, saved.getIconType());
    }
}
