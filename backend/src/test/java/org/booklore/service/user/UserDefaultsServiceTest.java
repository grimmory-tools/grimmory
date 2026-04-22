package org.booklore.service.user;

import tools.jackson.databind.ObjectMapper;
import org.booklore.model.entity.BookLoreUserEntity;
import org.booklore.model.entity.ShelfEntity;
import org.booklore.model.enums.IconType;
import org.booklore.repository.ShelfRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserDefaultsServiceTest {

    @Mock
    private ShelfRepository shelfRepository;
    @Mock
    private ObjectMapper objectMapper;
    @Mock
    private DefaultUserSettingsProvider defaultSettingsProvider;

    @InjectMocks
    private UserDefaultsService userDefaultsService;

    @Test
    void addDefaultShelves_shouldCreateFavoritesShelfWithIcon() {
        BookLoreUserEntity user = BookLoreUserEntity.builder()
                .id(1L)
                .username("testuser")
                .build();

        when(shelfRepository.save(any(ShelfEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        userDefaultsService.addDefaultShelves(user);

        ArgumentCaptor<ShelfEntity> captor = ArgumentCaptor.forClass(ShelfEntity.class);
        verify(shelfRepository).save(captor.capture());

        ShelfEntity saved = captor.getValue();
        assertEquals("Favorites", saved.getName());
        assertEquals("heart", saved.getIcon());
        assertEquals(IconType.PRIME_NG, saved.getIconType());
        assertEquals(user, saved.getUser());
    }

    @Test
    void addDefaultShelves_shouldSetExplicitIconType() {
        BookLoreUserEntity user = BookLoreUserEntity.builder()
                .id(2L)
                .username("anotheruser")
                .build();

        when(shelfRepository.save(any(ShelfEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        userDefaultsService.addDefaultShelves(user);

        ArgumentCaptor<ShelfEntity> captor = ArgumentCaptor.forClass(ShelfEntity.class);
        verify(shelfRepository).save(captor.capture());

        ShelfEntity saved = captor.getValue();
        assertNotNull(saved.getIconType(), "Default shelf must have an explicit iconType since entity no longer has a default");
        assertNotNull(saved.getIcon(), "Default shelf must have an explicit icon since entity no longer has a default");
    }
}
