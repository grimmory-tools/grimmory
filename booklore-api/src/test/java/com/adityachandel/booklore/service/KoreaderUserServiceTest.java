package com.adityachandel.booklore.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.adityachandel.booklore.config.security.service.AuthenticationService;
import com.adityachandel.booklore.model.dto.BookLoreUser;
import com.adityachandel.booklore.model.dto.KoreaderUser;
import com.adityachandel.booklore.model.entity.BookLoreUserEntity;
import com.adityachandel.booklore.model.entity.KoreaderUserEntity;
import com.adityachandel.booklore.repository.KoreaderUserRepository;
import com.adityachandel.booklore.repository.UserRepository;
import com.adityachandel.booklore.util.Md5Util;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Optional;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class KoreaderUserServiceTest {

    @Mock AuthenticationService authService;
    @Mock UserRepository userRepository;
    @Mock KoreaderUserRepository koreaderUserRepository;
    @Mock com.adityachandel.booklore.mapper.KoreaderUserMapper koreaderUserMapper;
    @InjectMocks KoreaderUserService service;

    private BookLoreUser ownerDto;
    private BookLoreUserEntity ownerEntity;
    private KoreaderUserEntity entity;
    private KoreaderUser dto;

    @BeforeEach
    void init() {
        ownerDto = mock(BookLoreUser.class);
        when(ownerDto.getId()).thenReturn(123L);
        when(ownerDto.getUsername()).thenReturn("ownerName");
        when(authService.getAuthenticatedUser()).thenReturn(ownerDto);

        ownerEntity = new BookLoreUserEntity();
        ownerEntity.setId(123L);
        ownerEntity.setUsername("ownerName");

        entity = new KoreaderUserEntity();
        entity.setId(10L);
        entity.setBookLoreUser(ownerEntity);
        entity.setUsername("kvUser");

        dto = new KoreaderUser(10L, "kvUser", null, null, false);
        when(koreaderUserMapper.toDto(any(KoreaderUserEntity.class))).thenReturn(dto);
    }

    @Test
    void upsertUser_createsNew_whenAbsent() {
        when(userRepository.findById(123L)).thenReturn(Optional.of(ownerEntity));
        when(koreaderUserRepository.findByBookLoreUserId(123L)).thenReturn(Optional.empty());
        when(koreaderUserRepository.save(any(KoreaderUserEntity.class))).thenAnswer(invocation -> {
            KoreaderUserEntity arg = invocation.getArgument(0);
            arg.setId(42L);
            return arg;
        });

        when(koreaderUserMapper.toDto(any(KoreaderUserEntity.class))).thenAnswer(invocation -> {
            KoreaderUserEntity u = invocation.getArgument(0);
            return new KoreaderUser(u.getId(), u.getUsername(), u.getPassword(), u.getPasswordMD5(), u.isSyncEnabled());
        });

        KoreaderUser result = service.upsertUser("userA", "passA");

        assertEquals(42L, result.getId());
        assertEquals("userA", result.getUsername());
        verify(koreaderUserRepository).save(argThat(u ->
            u.getBookLoreUser() == ownerEntity &&
            u.getUsername().equals("userA") &&
            u.getPasswordMD5().equals(Md5Util.md5Hex("passA"))
        ));
    }

    @Test
    void upsertUser_updatesExisting_whenPresent() {
        when(userRepository.findById(123L)).thenReturn(Optional.of(ownerEntity));
        when(koreaderUserRepository.findByBookLoreUserId(123L)).thenReturn(Optional.of(entity));
        when(koreaderUserRepository.save(entity)).thenReturn(entity);

        KoreaderUser result = service.upsertUser("newName", "newPass");

        assertEquals(dto, result);
        verify(koreaderUserRepository).save(entity);
        assertEquals("newName", entity.getUsername());
        assertEquals(Md5Util.md5Hex("newPass"), entity.getPasswordMD5());
    }

    @Test
    void upsertUser_throws_whenOwnerMissing() {
        when(userRepository.findById(123L)).thenReturn(Optional.empty());
        assertThrows(com.adityachandel.booklore.exception.APIException.class,
                     () -> service.upsertUser("x", "y"));
    }

    @Test
    void getUser_returnsDto_whenFound() {
        when(koreaderUserRepository.findByBookLoreUserId(123L)).thenReturn(Optional.of(entity));
        KoreaderUser result = service.getUser();
        assertEquals(dto, result);
    }

    @Test
    void getUser_throws_whenNotFound() {
        when(koreaderUserRepository.findByBookLoreUserId(123L)).thenReturn(Optional.empty());
        assertThrows(com.adityachandel.booklore.exception.APIException.class, () -> service.getUser());
    }

    @Test
    void toggleSync_setsFlag_andSaves() {
        when(koreaderUserRepository.findByBookLoreUserId(123L)).thenReturn(Optional.of(entity));
        service.toggleSync(true);
        assertTrue(entity.isSyncEnabled());
        verify(koreaderUserRepository).save(entity);
    }

    @Test
    void toggleSync_throws_whenEntityMissing() {
        when(koreaderUserRepository.findByBookLoreUserId(123L)).thenReturn(Optional.empty());
        assertThrows(com.adityachandel.booklore.exception.APIException.class, () -> service.toggleSync(false));
    }
}
