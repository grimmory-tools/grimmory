package org.booklore.service;

import org.booklore.config.security.service.AuthenticationService;
import org.booklore.model.dto.BookLoreUser;
import org.booklore.service.audit.AuditService;
import org.booklore.model.dto.MagicShelf;
import org.booklore.model.entity.MagicShelfEntity;
import org.booklore.model.enums.IconType;
import org.booklore.repository.MagicShelfRepository;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MagicShelfServiceTest {

    @Mock
    private MagicShelfRepository magicShelfRepository;
    @Mock
    private AuthenticationService authenticationService;
    @Mock
    private AuditService auditService;

    @InjectMocks
    private MagicShelfService magicShelfService;

    private BookLoreUser user;

    @BeforeEach
    void setUp() {
        BookLoreUser.UserPermissions permissions = new BookLoreUser.UserPermissions();
        permissions.setAdmin(true);
        user = BookLoreUser.builder().id(1L).isDefaultPassword(false).permissions(permissions).build();
    }

    @Test
    void createShelf_withNullIcon_shouldPersistNullIconValues() {
        MagicShelf dto = new MagicShelf();
        dto.setName("Unread Books");
        dto.setIcon(null);
        dto.setIconType(null);
        dto.setFilterJson("{\"status\": \"unread\"}");
        dto.setIsPublic(false);

        when(authenticationService.getAuthenticatedUser()).thenReturn(user);
        when(magicShelfRepository.existsByUserIdAndName(1L, "Unread Books")).thenReturn(false);
        when(magicShelfRepository.save(any(MagicShelfEntity.class))).thenAnswer(invocation -> {
            MagicShelfEntity entity = invocation.getArgument(0);
            entity.setId(1L);
            return entity;
        });

        MagicShelf result = magicShelfService.createOrUpdateShelf(dto);

        ArgumentCaptor<MagicShelfEntity> captor = ArgumentCaptor.forClass(MagicShelfEntity.class);
        verify(magicShelfRepository).save(captor.capture());

        MagicShelfEntity saved = captor.getValue();
        assertNull(saved.getIcon());
        assertNull(saved.getIconType());
        assertEquals("Unread Books", saved.getName());
    }

    @Test
    void createShelf_withIcon_shouldPersistIconValues() {
        MagicShelf dto = new MagicShelf();
        dto.setName("Favorites");
        dto.setIcon("star");
        dto.setIconType(IconType.PRIME_NG);
        dto.setFilterJson("{\"rating\": 5}");
        dto.setIsPublic(false);

        when(authenticationService.getAuthenticatedUser()).thenReturn(user);
        when(magicShelfRepository.existsByUserIdAndName(1L, "Favorites")).thenReturn(false);
        when(magicShelfRepository.save(any(MagicShelfEntity.class))).thenAnswer(invocation -> {
            MagicShelfEntity entity = invocation.getArgument(0);
            entity.setId(1L);
            return entity;
        });

        MagicShelf result = magicShelfService.createOrUpdateShelf(dto);

        assertNotNull(result);
        assertEquals("star", result.getIcon());
        assertEquals(IconType.PRIME_NG, result.getIconType());
    }

    @Test
    void updateShelf_withNullIcon_shouldClearIconValues() {
        MagicShelfEntity existing = MagicShelfEntity.builder()
                .id(1L)
                .userId(1L)
                .name("Old Shelf")
                .icon("star")
                .iconType(IconType.PRIME_NG)
                .filterJson("{\"status\": \"reading\"}")
                .build();

        MagicShelf dto = new MagicShelf();
        dto.setId(1L);
        dto.setName("Updated Shelf");
        dto.setIcon(null);
        dto.setIconType(null);
        dto.setFilterJson("{\"status\": \"updated\"}");
        dto.setIsPublic(false);

        when(authenticationService.getAuthenticatedUser()).thenReturn(user);
        when(magicShelfRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(magicShelfRepository.save(any(MagicShelfEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        magicShelfService.createOrUpdateShelf(dto);

        ArgumentCaptor<MagicShelfEntity> captor = ArgumentCaptor.forClass(MagicShelfEntity.class);
        verify(magicShelfRepository).save(captor.capture());

        MagicShelfEntity saved = captor.getValue();
        assertNull(saved.getIcon());
        assertNull(saved.getIconType());
        assertEquals("Updated Shelf", saved.getName());
    }

    @Test
    void updateShelf_withIcon_shouldPreserveIconValues() {
        MagicShelfEntity existing = MagicShelfEntity.builder()
                .id(1L)
                .userId(1L)
                .name("Old Shelf")
                .icon("star")
                .iconType(IconType.PRIME_NG)
                .filterJson("{\"status\": \"reading\"}")
                .build();

        MagicShelf dto = new MagicShelf();
        dto.setId(1L);
        dto.setName("Updated Shelf");
        dto.setIcon("bookmark");
        dto.setIconType(IconType.CUSTOM_SVG);
        dto.setFilterJson("{\"status\": \"updated\"}");
        dto.setIsPublic(false);

        when(authenticationService.getAuthenticatedUser()).thenReturn(user);
        when(magicShelfRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(magicShelfRepository.save(any(MagicShelfEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        magicShelfService.createOrUpdateShelf(dto);

        ArgumentCaptor<MagicShelfEntity> captor = ArgumentCaptor.forClass(MagicShelfEntity.class);
        verify(magicShelfRepository).save(captor.capture());

        MagicShelfEntity saved = captor.getValue();
        assertEquals("bookmark", saved.getIcon());
        assertEquals(IconType.CUSTOM_SVG, saved.getIconType());
    }

    @Test
    void updateShelf_fromIconToNull_shouldAllowRemovingIcon() {
        MagicShelfEntity existing = MagicShelfEntity.builder()
                .id(1L)
                .userId(1L)
                .name("Shelf With Icon")
                .icon("heart")
                .iconType(IconType.PRIME_NG)
                .filterJson("{}")
                .build();

        MagicShelf dto = new MagicShelf();
        dto.setId(1L);
        dto.setName("Shelf With Icon");
        dto.setIcon(null);
        dto.setIconType(null);
        dto.setFilterJson("{}");
        dto.setIsPublic(false);

        when(authenticationService.getAuthenticatedUser()).thenReturn(user);
        when(magicShelfRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(magicShelfRepository.save(any(MagicShelfEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        MagicShelf result = magicShelfService.createOrUpdateShelf(dto);

        assertNull(result.getIcon());
        assertNull(result.getIconType());
    }
}
