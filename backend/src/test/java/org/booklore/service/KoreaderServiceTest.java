package org.booklore.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import org.booklore.config.security.userdetails.KoreaderUserDetails;
import org.booklore.exception.APIException;
import org.booklore.model.dto.progress.KoreaderProgress;
import org.booklore.model.entity.BookEntity;
import org.booklore.model.entity.BookLoreUserEntity;
import org.booklore.model.entity.KoreaderUserEntity;
import org.booklore.model.entity.UserBookProgressEntity;
import org.booklore.model.enums.ReadStatus;
import org.booklore.repository.BookRepository;
import org.booklore.repository.UserBookProgressRepository;
import org.booklore.repository.UserRepository;
import org.booklore.repository.KoreaderUserRepository;
import org.booklore.service.hardcover.HardcoverSyncService;
import org.booklore.service.koreader.KoreaderService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.*;

import java.lang.reflect.Method;
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
    @Mock
    HardcoverSyncService hardcoverSyncService;

    @InjectMocks
    KoreaderService service;

    private KoreaderUserDetails details;

    @BeforeEach
    void setUpAuth() {
        details = mock(KoreaderUserDetails.class);
        when(details.getUsername()).thenReturn("u");
        when(details.getPassword()).thenReturn("md5pwd");
        when(details.getBookLoreUserId()).thenReturn(42L);
        Authentication auth = mock(Authentication.class);
        SecurityContext context = new SecurityContextImpl();
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
        var userEntity = new KoreaderUserEntity();
        userEntity.setPasswordMD5("MD5PWD");
        when(koreaderUserRepo.findByUsername("u"))
                .thenReturn(Optional.of(userEntity));
        when(details.getPassword()).thenReturn("MD5PWD");

        ResponseEntity<Map<String, String>> resp = service.authorizeUser();
        assertEquals(200, resp.getStatusCode().value());
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
        var userEntity = new KoreaderUserEntity();
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
    void getProgress_includesTimestamp() {
        when(details.isSyncEnabled()).thenReturn(true);
        var book = new BookEntity();
        book.setId(100L);
        when(bookRepo.findByCurrentHash("hash123")).thenReturn(Optional.of(book));

        var prog = new UserBookProgressEntity();
        prog.setKoreaderProgress("progress/path");
        prog.setKoreaderProgressPercent(0.75F);
        Instant syncTime = Instant.ofEpochSecond(1762209924L);
        prog.setKoreaderLastSyncTime(syncTime);
        when(progressRepo.findByUserIdAndBookId(42L, 100L))
                .thenReturn(Optional.of(prog));

        KoreaderProgress out = service.getProgress("hash123");
        assertEquals("hash123", out.getDocument());
        assertEquals("progress/path", out.getProgress());
        assertEquals(0.75F, out.getPercentage());
        assertEquals(1762209924L, out.getTimestamp());
    }

    @Test
    void getProgress_nullTimestamp() {
        when(details.isSyncEnabled()).thenReturn(true);
        var book = new BookEntity();
        book.setId(101L);
        when(bookRepo.findByCurrentHash("hash456")).thenReturn(Optional.of(book));

        var prog = new UserBookProgressEntity();
        prog.setKoreaderProgress("progress/path2");
        prog.setKoreaderProgressPercent(0.25F);
        prog.setKoreaderLastSyncTime(null);
        when(progressRepo.findByUserIdAndBookId(42L, 101L))
                .thenReturn(Optional.of(prog));

        KoreaderProgress out = service.getProgress("hash456");
        assertEquals("hash456", out.getDocument());
        assertEquals("progress/path2", out.getProgress());
        assertEquals(0.25F, out.getPercentage());
        assertNull(out.getTimestamp());
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
    void saveProgress_updatesExistingNoProgressChange_noHardcoverUpdate() {
        when(details.isSyncEnabled()).thenReturn(true);
        var book = new BookEntity();
        book.setId(8L);
        when(bookRepo.findByCurrentHash("h")).thenReturn(Optional.of(book));
        var user = new BookLoreUserEntity();
        user.setId(42L);
        when(userRepo.findById(42L)).thenReturn(Optional.of(user));
        var existing = new UserBookProgressEntity();
        existing.setKoreaderProgressPercent(0.4F);
        existing.setReadStatus(ReadStatus.READING);
        when(progressRepo.findByUserIdAndBookId(42L, 8L))
                .thenReturn(Optional.of(existing));

        var dto = KoreaderProgress.builder()
                .document("h").progress("y").percentage(0.4F).device("d").device_id("id").build();
        service.saveProgress("h", dto);

        verify(progressRepo).save(existing);
        assertEquals("y", existing.getKoreaderProgress());
        assertEquals(0.4F, existing.getKoreaderProgressPercent());
        verify(hardcoverSyncService, never()).syncProgressToHardcover(any(), any(), any());
    }

    @Test
    void saveProgress_syncDisabled() {
        when(details.isSyncEnabled()).thenReturn(false);
        var dto = KoreaderProgress.builder().document("h").build();
        assertThrows(APIException.class, () -> service.saveProgress("h", dto));
    }

    @Test
    void normalizeProgressPercent_handlesNullAndRanges() throws Exception {
        Method method = KoreaderService.class.getDeclaredMethod("normalizeProgressPercent", Float.class);
        method.setAccessible(true);

        assertNull(method.invoke(service, new Object[]{null}));
        assertEquals(50.0f, (Float) method.invoke(service, 0.5f));
        assertEquals(100.0f, (Float) method.invoke(service, 1.0f));
        assertEquals(42.0f, (Float) method.invoke(service, 42.0f));
    }
}
