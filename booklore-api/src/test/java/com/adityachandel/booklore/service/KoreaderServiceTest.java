package com.adityachandel.booklore.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.isNull;

import com.adityachandel.booklore.config.security.userdetails.KoreaderUserDetails;
import com.adityachandel.booklore.exception.APIException;
import com.adityachandel.booklore.model.dto.progress.KoreaderProgress;
import com.adityachandel.booklore.model.entity.BookEntity;
import com.adityachandel.booklore.model.entity.BookLoreUserEntity;
import com.adityachandel.booklore.model.entity.UserBookProgressEntity;
import com.adityachandel.booklore.repository.BookRepository;
import com.adityachandel.booklore.repository.UserBookProgressRepository;
import com.adityachandel.booklore.repository.UserRepository;
import com.adityachandel.booklore.repository.KoreaderUserRepository;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.*;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class KoreaderServiceTest {

    @Mock
    UserBookProgressRepository progressRepo;
    @Mock
    BookRepository bookRepo;
    @Mock
    UserRepository userRepo;
    @Mock
    KoreaderUserRepository koreaderUserRepo;

    @InjectMocks
    KoreaderService service;

    private KoreaderUserDetails details;
    private Authentication auth;
    private SecurityContext context;

    @BeforeEach
    void setUpAuth() {
        details = mock(KoreaderUserDetails.class);
        when(details.getUsername()).thenReturn("u");
        when(details.getPassword()).thenReturn("md5pwd");
        when(details.getBookLoreUserId()).thenReturn(42L);
        auth = mock(Authentication.class);
        context = new SecurityContextImpl();
        when(auth.getPrincipal()).thenReturn(details);
        context.setAuthentication(auth);
        SecurityContextHolder.setContext(context);
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }


    @Test
    void authorizeUser_success() {
        var userEntity = new com.adityachandel.booklore.model.entity.KoreaderUserEntity();
        userEntity.setPasswordMD5("MD5PWD");
        when(koreaderUserRepo.findByUsername("u"))
                .thenReturn(Optional.of(userEntity));
        when(details.getPassword()).thenReturn("MD5PWD");

        ResponseEntity<Map<String, String>> resp = service.authorizeUser();
        assertEquals(200, resp.getStatusCodeValue());
        assertEquals("u", resp.getBody().get("username"));
    }

    @Test
    void authorizeUser_notFound() {
        when(koreaderUserRepo.findByUsername("u")).thenReturn(Optional.empty());
        APIException ex = assertThrows(APIException.class, () -> service.authorizeUser());
        assertTrue(ex.getStatus().is4xxClientError());
    }

    @Test
    void authorizeUser_badPassword() {
        var userEntity = new com.adityachandel.booklore.model.entity.KoreaderUserEntity();
        userEntity.setPasswordMD5("OTHER");
        when(koreaderUserRepo.findByUsername("u"))
                .thenReturn(Optional.of(userEntity));
        assertThrows(APIException.class, () -> service.authorizeUser());
    }

    @Test
    void getProgress_success() {
        when(details.isSyncEnabled()).thenReturn(true);
        var book = new BookEntity();
        book.setId(99L);
        when(bookRepo.findByCurrentHash("h")).thenReturn(Optional.of(book));
        var prog = new UserBookProgressEntity();
        prog.setKoreaderProgress("p");
        prog.setKoreaderProgressPercent(0.5F);
        when(progressRepo.findByUserIdAndBookId(42L, 99L))
                .thenReturn(Optional.of(prog));

        KoreaderProgress out = service.getProgress("h");
        assertEquals("h", out.getDocument());
        assertEquals("p", out.getProgress());
        assertEquals(0.5F, out.getPercentage());
    }

    @Test
    void getProgress_bookNotFound() {
        when(details.isSyncEnabled()).thenReturn(true);
        when(bookRepo.findByCurrentHash("h")).thenReturn(Optional.empty());
        assertThrows(APIException.class, () -> service.getProgress("h"));
    }

    @Test
    void getProgress_noProgress() {
        when(details.isSyncEnabled()).thenReturn(true);
        when(bookRepo.findByCurrentHash("h"))
                .thenReturn(Optional.of(new BookEntity()));
        when(progressRepo.findByUserIdAndBookId(anyLong(), isNull()))
                .thenReturn(Optional.empty());
        assertThrows(APIException.class, () -> service.getProgress("h"));
    }

    @Test
    void getProgress_syncDisabled() {
        when(details.isSyncEnabled()).thenReturn(false);
        assertThrows(APIException.class, () -> service.getProgress("h"));
    }

    @Test
    void saveProgress_createsNew() {
        when(details.isSyncEnabled()).thenReturn(true);
        var book = new BookEntity();
        book.setId(7L);
        when(bookRepo.findByCurrentHash("h")).thenReturn(Optional.of(book));
        var user = new BookLoreUserEntity();
        user.setId(42L);
        when(userRepo.findById(42L)).thenReturn(Optional.of(user));
        when(progressRepo.findByUserIdAndBookId(42L, 7L))
                .thenReturn(Optional.empty());

        var dto = KoreaderProgress.builder()
                .document("h").progress("x").percentage(0.6F).device("d").device_id("id").build();
        service.saveProgress("h", dto);

        ArgumentCaptor<UserBookProgressEntity> cap = ArgumentCaptor.forClass(UserBookProgressEntity.class);
        verify(progressRepo).save(cap.capture());
        var saved = cap.getValue();
        assertEquals("x", saved.getKoreaderProgress());
        assertEquals(0.6F, saved.getKoreaderProgressPercent());
        assertEquals("d", saved.getKoreaderDevice());
        assertEquals("id", saved.getKoreaderDeviceId());
        assertEquals(Instant.class, saved.getKoreaderLastSyncTime().getClass());
    }

    @Test
    void saveProgress_updatesExisting() {
        when(details.isSyncEnabled()).thenReturn(true);
        var book = new BookEntity();
        book.setId(8L);
        when(bookRepo.findByCurrentHash("h")).thenReturn(Optional.of(book));
        var user = new BookLoreUserEntity();
        user.setId(42L);
        when(userRepo.findById(42L)).thenReturn(Optional.of(user));
        var existing = new UserBookProgressEntity();
        when(progressRepo.findByUserIdAndBookId(42L, 8L))
                .thenReturn(Optional.of(existing));

        var dto = KoreaderProgress.builder()
                .document("h").progress("y").percentage(0.4F).device("d").device_id("id").build();
        service.saveProgress("h", dto);

        verify(progressRepo).save(existing);
        assertEquals("y", existing.getKoreaderProgress());
        assertEquals(0.4F, existing.getKoreaderProgressPercent());
    }

    @Test
    void saveProgress_syncDisabled() {
        when(details.isSyncEnabled()).thenReturn(false);
        var dto = KoreaderProgress.builder().document("h").build();
        assertThrows(APIException.class, () -> service.saveProgress("h", dto));
    }
}
