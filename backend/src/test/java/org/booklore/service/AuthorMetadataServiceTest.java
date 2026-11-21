package org.booklore.service;

import org.booklore.config.security.service.AuthenticationService;
import org.booklore.exception.APIException;
import org.booklore.model.dto.AuthorDetails;
import org.booklore.model.dto.AuthorSearchResult;
import org.booklore.model.dto.BookLoreUser;
import org.booklore.model.dto.request.AuthorMatchRequest;
import org.booklore.model.dto.settings.AppSettings;
import org.booklore.model.dto.settings.MetadataProviderSettings;
import org.booklore.model.entity.AuthorEntity;
import org.booklore.model.enums.AuditAction;
import org.booklore.model.enums.AuthorMetadataSource;
import org.booklore.repository.AuthorRepository;
import org.booklore.service.appsettings.AppSettingService;
import org.booklore.service.audit.AuditService;
import org.booklore.service.metadata.DuckDuckGoCoverService;
import org.booklore.service.metadata.parser.AuthorParser;
import org.booklore.util.FileService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.json.JsonMapper;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class AuthorMetadataServiceTest {

    @Mock private AuthorRepository authorRepository;
    @Mock private AuthorParser authorParser;
    @Mock private AuthorParser bnfAuthorParser;
    @Mock private AuditService auditService;
    @Mock private FileService fileService;
    @Mock private DuckDuckGoCoverService duckDuckGoCoverService;
    @Mock private AuthenticationService authenticationService;
    @Mock private AppSettingService appSettingService;

    private AuthorMetadataService service;

    @BeforeEach
    void setUp() {
        Map<AuthorMetadataSource, AuthorParser> authorParserMap = Map.of(
                AuthorMetadataSource.AUDNEXUS, authorParser,
                AuthorMetadataSource.BNF, bnfAuthorParser
        );

        service = new AuthorMetadataService(
                authorRepository,
                authorParserMap,
                auditService,
                fileService,
                duckDuckGoCoverService,
                authenticationService,
                appSettingService
        );
        lenient().when(appSettingService.getAppSettings()).thenReturn(appSettingsWithAmazon(true, "com"));
        lenient().when(fileService.getAuthorThumbnailFile(anyLong())).thenReturn("/nonexistent/path/thumbnail.jpg");

        BookLoreUser.UserPermissions adminPermissions = new BookLoreUser.UserPermissions();
        adminPermissions.setAdmin(true);
        BookLoreUser adminUser = BookLoreUser.builder().id(1L).permissions(adminPermissions).build();
        lenient().when(authenticationService.getAuthenticatedUser()).thenReturn(adminUser);
    }

    @Test
    void searchAuthorMetadata_returnsResults() {
        AuthorSearchResult r1 = AuthorSearchResult.builder()
                .source(AuthorMetadataSource.AUDNEXUS).asin("B000APZGGS")
                .name("Stephen King").description("American author")
                .imageUrl("https://example.com/king.jpg")
                .build();
        AuthorSearchResult r2 = AuthorSearchResult.builder()
                .source(AuthorMetadataSource.AUDNEXUS).asin("B000AP1234")
                .name("Stephen King Jr").description("Another author")
                .imageUrl("https://example.com/kingjr.jpg")
                .build();

        when(authorParser.searchAuthors("Stephen King", "us")).thenReturn(List.of(r1, r2));

        List<AuthorSearchResult> results = service.searchAuthorMetadata("Stephen King", "us");

        assertThat(results).hasSize(2);
        assertThat(results.get(0).getAsin()).isEqualTo("B000APZGGS");
        assertThat(results.get(0).getSource()).isEqualTo(AuthorMetadataSource.AUDNEXUS);
        assertThat(results.get(0).getName()).isEqualTo("Stephen King");
        assertThat(results.get(0).getDescription()).isEqualTo("American author");
        assertThat(results.get(0).getImageUrl()).isEqualTo("https://example.com/king.jpg");
        assertThat(results.get(1).getAsin()).isEqualTo("B000AP1234");
    }

    @Test
    void searchAuthorMetadataUsesBnfWhenAmazonProviderIsDisabled() {
        when(appSettingService.getAppSettings()).thenReturn(appSettingsWithAmazon(false, "com"));
        AuthorSearchResult result = AuthorSearchResult.builder()
                .source(AuthorMetadataSource.BNF)
                .providerId("http://data.bnf.fr/ark:/12148/cb123#about")
                .name("Stephen King")
                .isni("000000012146438X")
                .build();
        when(bnfAuthorParser.searchAuthors("Stephen King", "us")).thenReturn(List.of(result));

        assertThat(service.searchAuthorMetadata("Stephen King", null)).containsExactly(result);

        verifyNoInteractions(authorParser);
    }

    @Test
    void quickMatchAuthorUsesConfiguredAmazonDomainWhenNoRegionIsProvided() {
        AuthorEntity author = new AuthorEntity();
        author.setId(1L);
        author.setName("S. King");
        when(authorRepository.findById(1L)).thenReturn(Optional.of(author));
        when(authorParser.searchAuthors("S. King", "uk")).thenReturn(List.of(AuthorSearchResult.builder()
                .source(AuthorMetadataSource.AUDNEXUS)
                .asin("B000APZGGS")
                .name("Stephen King")
                .description("Author biography")
                .build()));
        when(authorRepository.save(any(AuthorEntity.class))).thenAnswer(i -> i.getArgument(0));
        when(appSettingService.getAppSettings()).thenReturn(appSettingsWithAmazon(true, "co.uk"));

        AuthorDetails details = service.quickMatchAuthor(1L, null);

        assertThat(details.getName()).isEqualTo("Stephen King");
        assertThat(details.getAsin()).isEqualTo("B000APZGGS");
        verify(authorParser).searchAuthors("S. King", "uk");
    }

    @Test
    void quickMatchAuthorLooksUpExistingAsinBeforeSearchingByName() {
        AuthorEntity author = new AuthorEntity();
        author.setId(1L);
        author.setName("S. King");
        author.setAsin("B000APZGGS");
        when(authorRepository.findById(1L)).thenReturn(Optional.of(author));
        when(authorParser.getAuthorById("B000APZGGS", "us")).thenReturn(AuthorSearchResult.builder()
                .source(AuthorMetadataSource.AUDNEXUS)
                .asin("B000APZGGS")
                .name("Stephen King")
                .description("Author biography")
                .build());
        when(authorRepository.save(any(AuthorEntity.class))).thenAnswer(i -> i.getArgument(0));

        AuthorDetails details = service.quickMatchAuthor(1L, null);

        assertThat(details.getName()).isEqualTo("Stephen King");
        assertThat(details.getAsin()).isEqualTo("B000APZGGS");
        assertThat(details.getDescription()).isEqualTo("Author biography");
        verify(authorParser).getAuthorById("B000APZGGS", "us");
        verify(authorParser, never()).searchAuthors(anyString(), anyString());
    }

    @Test
    void quickMatchAuthorFallsBackToNameSearchWhenExistingAsinIsNotFound() {
        AuthorEntity author = new AuthorEntity();
        author.setId(1L);
        author.setName("Stephen King");
        author.setAsin("B000APZGGS");
        when(authorRepository.findById(1L)).thenReturn(Optional.of(author));
        when(authorParser.getAuthorById("B000APZGGS", "us")).thenReturn(null);
        when(authorParser.searchAuthors("Stephen King", "us")).thenReturn(List.of(AuthorSearchResult.builder()
                .source(AuthorMetadataSource.AUDNEXUS)
                .asin("B000APZGGS")
                .name("Stephen King")
                .build()));
        when(authorRepository.save(any(AuthorEntity.class))).thenAnswer(i -> i.getArgument(0));

        AuthorDetails details = service.quickMatchAuthor(1L, null);

        assertThat(details.getAsin()).isEqualTo("B000APZGGS");
        verify(authorParser).searchAuthors("Stephen King", "us");
    }

    @Test
    void quickMatchAuthorRejectsMultipleCandidatesWithoutUpdatingTheAuthor() {
        AuthorEntity author = new AuthorEntity();
        author.setId(1L);
        author.setName("Stephen King");
        when(authorRepository.findById(1L)).thenReturn(Optional.of(author));
        when(authorParser.searchAuthors("Stephen King", "us")).thenReturn(List.of(
                AuthorSearchResult.builder().source(AuthorMetadataSource.AUDNEXUS).asin("B0001").name("Stephen King").build(),
                AuthorSearchResult.builder().source(AuthorMetadataSource.AUDNEXUS).asin("B0002").name("Stephen King Jr").build()
        ));

        assertThatThrownBy(() -> service.quickMatchAuthor(1L, null))
                .isInstanceOf(APIException.class)
                .hasMessageContaining("Multiple metadata matches")
                .hasMessageContaining("manual validation required");

        verify(authorRepository, never()).save(any(AuthorEntity.class));
        verifyNoInteractions(fileService);
    }

    @Test
    void searchAuthorMetadata_returnsEmptyListWhenNull() {
        when(authorParser.searchAuthors("Unknown", "us")).thenReturn(null);

        List<AuthorSearchResult> results = service.searchAuthorMetadata("Unknown", "us");

        assertThat(results).isEmpty();
    }

    @Test
    void searchAuthorMetadata_returnsEmptyListWhenEmpty() {
        when(authorParser.searchAuthors("Nobody", "us")).thenReturn(Collections.emptyList());

        List<AuthorSearchResult> results = service.searchAuthorMetadata("Nobody", "us");

        assertThat(results).isEmpty();
    }

    @Test
    void matchAuthor_updatesEntityAndReturnsDetails() {
        AuthorEntity author = new AuthorEntity();
        author.setId(1L);
        author.setName("S. King");

        when(authorRepository.findById(1L)).thenReturn(Optional.of(author));

        AuthorSearchResult result = AuthorSearchResult.builder()
                .source(AuthorMetadataSource.AUDNEXUS).asin("B000APZGGS")
                .description("Master of horror").imageUrl("https://example.com/king.jpg")
                .name("Stephen King")
                .build();

        when(authorParser.getAuthorById("B000APZGGS", "us")).thenReturn(result);
        when(authorRepository.save(any(AuthorEntity.class))).thenAnswer(i -> i.getArgument(0));

        AuthorMatchRequest request = new AuthorMatchRequest();
        request.setAsin("B000APZGGS");
        request.setSource(AuthorMetadataSource.AUDNEXUS);
        request.setRegion("us");

        AuthorDetails details = service.matchAuthor(1L, request);

        assertThat(details.getId()).isEqualTo(1L);
        assertThat(details.getName()).isEqualTo("Stephen King");
        assertThat(details.getDescription()).isEqualTo("Master of horror");
        assertThat(details.getAsin()).isEqualTo("B000APZGGS");

        assertThat(author.getDescription()).isEqualTo("Master of horror");
        assertThat(author.getName()).isEqualTo("Stephen King");
        assertThat(author.getAsin()).isEqualTo("B000APZGGS");

        verify(authorRepository).save(author);
        verify(fileService).createAuthorThumbnailFromUrl(eq(1L), eq("https://example.com/king.jpg"));
        verify(auditService).log(eq(AuditAction.AUTHOR_METADATA_UPDATED), eq("Author"), eq(1L), anyString());
    }

    @Test
    void matchAuthorReportsPhotoActuallySavedAfterMetadataUpdate(@TempDir Path tempDir) throws Exception {
        AuthorEntity author = new AuthorEntity();
        author.setId(1L);
        author.setName("Test Author");
        Path thumbnail = tempDir.resolve("thumbnail.jpg");
        when(fileService.getAuthorThumbnailFile(1L)).thenReturn(thumbnail.toString());
        when(authorRepository.findById(1L)).thenReturn(Optional.of(author));
        when(authorParser.getAuthorById("B000APZGGS", "us")).thenReturn(AuthorSearchResult.builder()
                .source(AuthorMetadataSource.AUDNEXUS)
                .asin("B000APZGGS")
                .name("Test Author")
                .imageUrl("https://example.com/photo.jpg")
                .build());
        doAnswer(invocation -> {
            Files.writeString(thumbnail, "saved");
            return null;
        }).when(fileService).createAuthorThumbnailFromUrl(1L, "https://example.com/photo.jpg");
        AuthorMatchRequest request = new AuthorMatchRequest();
        request.setSource(AuthorMetadataSource.AUDNEXUS);
        request.setAsin("B000APZGGS");

        AuthorDetails details = service.matchAuthor(1L, request);
        assertThat(details.isHasPhoto()).isTrue();
        assertThat(JsonMapper.builder().build().writeValueAsString(details)).contains("\"hasPhoto\":true");
        assertThat(Files.readString(thumbnail)).isEqualTo("saved");
    }

    @Test
    void deleteAuthorPhotoDeletesOnlyThePhotoAndThumbnail(@TempDir Path tempDir) throws Exception {
        AuthorEntity author = new AuthorEntity();
        author.setId(1L);
        author.setName("Test Author");
        Path photo = Files.writeString(tempDir.resolve("photo.jpg"), "photo");
        Path thumbnail = Files.writeString(tempDir.resolve("thumbnail.jpg"), "thumbnail");
        Path other = Files.writeString(tempDir.resolve("other.txt"), "preserve");
        when(authorRepository.findById(1L)).thenReturn(Optional.of(author));
        when(fileService.getAuthorPhotoFile(1L)).thenReturn(photo.toString());
        when(fileService.getAuthorThumbnailFile(1L)).thenReturn(thumbnail.toString());

        AuthorDetails result = service.deleteAuthorPhoto(1L);

        assertThat(Files.exists(photo)).isFalse();
        assertThat(Files.exists(thumbnail)).isFalse();
        assertThat(Files.readString(other)).isEqualTo("preserve");
        assertThat(result.isHasPhoto()).isFalse();
        verify(auditService).log(eq(AuditAction.AUTHOR_METADATA_UPDATED), eq("Author"), eq(1L), contains("Deleted photo"));
    }

    @Test
    void deleteAuthorPhotoRejectsLockedPhotos(@TempDir Path tempDir) throws Exception {
        AuthorEntity author = new AuthorEntity();
        author.setId(1L);
        author.setPhotoLocked(true);
        Path photo = Files.writeString(tempDir.resolve("photo.jpg"), "photo");
        when(authorRepository.findById(1L)).thenReturn(Optional.of(author));

        assertThatThrownBy(() -> service.deleteAuthorPhoto(1L))
                .isInstanceOf(APIException.class)
                .hasMessageContaining("locked");
        assertThat(Files.exists(photo)).isTrue();
        verifyNoInteractions(fileService);
    }

    @Test
    void deleteAuthorPhotoReportsFileErrors(@TempDir Path tempDir) throws Exception {
        AuthorEntity author = new AuthorEntity();
        author.setId(1L);
        Path nonEmptyDirectory = Files.createDirectory(tempDir.resolve("photo"));
        Files.writeString(nonEmptyDirectory.resolve("keep.txt"), "keep");
        when(authorRepository.findById(1L)).thenReturn(Optional.of(author));
        when(fileService.getAuthorPhotoFile(1L)).thenReturn(nonEmptyDirectory.toString());

        assertThatThrownBy(() -> service.deleteAuthorPhoto(1L))
                .isInstanceOf(APIException.class)
                .hasMessageContaining("Failed to delete author photo");
        verifyNoInteractions(auditService);
    }

    @Test
    void matchAuthorFromBnfUpdatesNameAndIsniWithoutClearingAmazonMetadata() {
        AuthorEntity author = new AuthorEntity();
        author.setId(1L);
        author.setName("S. King");
        author.setDescription("Existing description");
        author.setAsin("B000APZGGS");
        when(authorRepository.findById(1L)).thenReturn(Optional.of(author));
        when(bnfAuthorParser.getAuthorById("http://data.bnf.fr/ark:/12148/cb123#about", "us"))
                .thenReturn(AuthorSearchResult.builder()
                        .source(AuthorMetadataSource.BNF)
                        .providerId("http://data.bnf.fr/ark:/12148/cb123#about")
                        .name("Stephen King")
                        .isni("000000012146438X")
                        .build());
        when(authorRepository.save(any(AuthorEntity.class))).thenAnswer(i -> i.getArgument(0));

        AuthorMatchRequest request = new AuthorMatchRequest();
        request.setSource(AuthorMetadataSource.BNF);
        request.setProviderId("http://data.bnf.fr/ark:/12148/cb123#about");

        AuthorDetails details = service.matchAuthor(1L, request);

        assertThat(details.getName()).isEqualTo("Stephen King");
        assertThat(details.getIsni()).isEqualTo("000000012146438X");
        assertThat(details.getAsin()).isEqualTo("B000APZGGS");
        assertThat(details.getDescription()).isEqualTo("Existing description");
        verify(bnfAuthorParser).getAuthorById("http://data.bnf.fr/ark:/12148/cb123#about", "us");
    }

    @Test
    void matchAuthorDoesNotOverwriteLockedIsni() {
        AuthorEntity author = new AuthorEntity();
        author.setId(1L);
        author.setName("Ada Lovelace");
        author.setIsni("manual-isni");
        author.setIsniLocked(true);
        when(authorRepository.findById(1L)).thenReturn(Optional.of(author));
        when(bnfAuthorParser.getAuthorById("http://data.bnf.fr/ark:/12148/cb123#about", "us"))
                .thenReturn(AuthorSearchResult.builder()
                        .source(AuthorMetadataSource.BNF)
                        .providerId("http://data.bnf.fr/ark:/12148/cb123#about")
                        .name("Ada Lovelace")
                        .isni("000000012146438X")
                        .build());
        when(authorRepository.save(any(AuthorEntity.class))).thenAnswer(i -> i.getArgument(0));

        AuthorMatchRequest request = new AuthorMatchRequest();
        request.setSource(AuthorMetadataSource.BNF);
        request.setProviderId("http://data.bnf.fr/ark:/12148/cb123#about");

        AuthorDetails details = service.matchAuthor(1L, request);

        assertThat(details.getIsni()).isEqualTo("manual-isni");
        assertThat(details.isIsniLocked()).isTrue();
    }

    @Test
    void matchAuthor_preservesNameWhenNameIsLocked() {
        AuthorEntity author = new AuthorEntity();
        author.setId(1L);
        author.setName("Stephen King (custom)");
        author.setNameLocked(true);
        when(authorRepository.findById(1L)).thenReturn(Optional.of(author));
        when(authorParser.getAuthorById("B000APZGGS", "us")).thenReturn(AuthorSearchResult.builder()
                .source(AuthorMetadataSource.AUDNEXUS)
                .asin("B000APZGGS")
                .name("Stephen King")
                .build());
        when(authorRepository.save(any(AuthorEntity.class))).thenAnswer(i -> i.getArgument(0));

        AuthorMatchRequest request = new AuthorMatchRequest();
        request.setAsin("B000APZGGS");
        request.setSource(AuthorMetadataSource.AUDNEXUS);

        AuthorDetails details = service.matchAuthor(1L, request);

        assertThat(details.getName()).isEqualTo("Stephen King (custom)");
        assertThat(author.getName()).isEqualTo("Stephen King (custom)");
    }

    @Test
    void matchAuthor_throwsWhenAuthorNotFound() {
        when(authorRepository.findById(99L)).thenReturn(Optional.empty());

        AuthorMatchRequest request = new AuthorMatchRequest();
        request.setAsin("B000APZGGS");
        request.setSource(AuthorMetadataSource.AUDNEXUS);

        assertThatThrownBy(() -> service.matchAuthor(99L, request))
                .isInstanceOf(APIException.class)
                .hasMessageContaining("Author not found");
    }

    @Test
    void matchAuthor_throwsWhenProviderReturnsNull() {
        AuthorEntity author = new AuthorEntity();
        author.setId(1L);
        author.setName("Test Author");

        when(authorRepository.findById(1L)).thenReturn(Optional.of(author));
        when(authorParser.getAuthorById("INVALID", "us")).thenReturn(null);

        AuthorMatchRequest request = new AuthorMatchRequest();
        request.setAsin("INVALID");
        request.setSource(AuthorMetadataSource.AUDNEXUS);
        request.setRegion("us");

        assertThatThrownBy(() -> service.matchAuthor(1L, request))
                .isInstanceOf(APIException.class)
                .hasMessageContaining("Failed to fetch author metadata");
    }

    @Test
    void matchAuthor_throwsWhenSourceUnsupported() {
        AuthorEntity author = new AuthorEntity();
        author.setId(1L);
        author.setName("Test Author");

        when(authorRepository.findById(1L)).thenReturn(Optional.of(author));

        AuthorMatchRequest request = new AuthorMatchRequest();
        request.setAsin("12345");
        request.setSource(null);
        request.setRegion("us");

        assertThatThrownBy(() -> service.matchAuthor(1L, request))
                .isInstanceOf(APIException.class)
                .hasMessageContaining("Unsupported author metadata source");
    }

    @Test
    void getAuthorDetails_returnsDetails() {
        AuthorEntity author = new AuthorEntity();
        author.setId(5L);
        author.setName("Brandon Sanderson");
        author.setDescription("Fantasy author");
        author.setAsin("B001IGFHW6");

        when(authorRepository.findById(5L)).thenReturn(Optional.of(author));

        AuthorDetails details = service.getAuthorDetails(5L);

        assertThat(details.getId()).isEqualTo(5L);
        assertThat(details.getName()).isEqualTo("Brandon Sanderson");
        assertThat(details.getDescription()).isEqualTo("Fantasy author");
        assertThat(details.getAsin()).isEqualTo("B001IGFHW6");
    }

    @Test
    void getAuthorDetails_throwsWhenNotFound() {
        when(authorRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getAuthorDetails(99L))
                .isInstanceOf(APIException.class)
                .hasMessageContaining("Author not found");
    }

    @Test
    void deleteAuthorsWithoutBooks_deletesOnlyAuthorsReturnedByEmptyAssociationQuery() {
        AuthorEntity author = new AuthorEntity();
        author.setId(8L);
        author.setName("Orphan Author");
        when(authorRepository.findAuthorsWithoutBooks()).thenReturn(List.of(author));

        int deletedCount = service.deleteAuthorsWithoutBooks();

        assertThat(deletedCount).isEqualTo(1);
        verify(authorRepository).delete(author);
        verify(fileService).deleteAuthorImages(8L);
        verify(auditService).log(AuditAction.AUTHOR_DELETED, "Author", 8L, "Deleted author 'Orphan Author'");
    }

    @Test
    void deleteAuthorsWithoutBooks_returnsZeroWhenNoOrphansExist() {
        when(authorRepository.findAuthorsWithoutBooks()).thenReturn(Collections.emptyList());

        assertThat(service.deleteAuthorsWithoutBooks()).isZero();

        verify(authorRepository, never()).delete(any(AuthorEntity.class));
        verifyNoInteractions(fileService);
    }

    @Test
    void getAuthorPhoto_returnsNullWhenFileDoesNotExist() {
        when(fileService.getAuthorPhotoFile(1L)).thenReturn("/nonexistent/path/photo.jpg");

        assertThat(service.getAuthorPhoto(1L)).isNull();
    }

    @Test
    void getAuthorThumbnail_returnsNullWhenFileDoesNotExist() {
        when(fileService.getAuthorThumbnailFile(1L)).thenReturn("/nonexistent/path/thumbnail.jpg");

        assertThat(service.getAuthorThumbnail(1L)).isNull();
    }

    @Test
    void matchAuthor_skipsPhotoWhenPhotoLocked() {
        AuthorEntity author = new AuthorEntity();
        author.setId(1L);
        author.setName("Test Author");
        author.setPhotoLocked(true);

        when(authorRepository.findById(1L)).thenReturn(Optional.of(author));

        AuthorSearchResult result = AuthorSearchResult.builder()
                .source(AuthorMetadataSource.AUDNEXUS).asin("B000APZGGS")
                .description("Bio").imageUrl("https://example.com/photo.jpg")
                .name("Test Author")
                .build();

        when(authorParser.getAuthorById("B000APZGGS", "us")).thenReturn(result);
        when(authorRepository.save(any(AuthorEntity.class))).thenAnswer(i -> i.getArgument(0));

        AuthorMatchRequest request = new AuthorMatchRequest();
        request.setAsin("B000APZGGS");
        request.setSource(AuthorMetadataSource.AUDNEXUS);
        request.setRegion("us");

        AuthorDetails details = service.matchAuthor(1L, request);

        verify(fileService, never()).createAuthorThumbnailFromUrl(anyLong(), anyString());
        assertThat(details.isHasPhoto()).isFalse();
    }

    private AppSettings appSettingsWithAmazon(boolean enabled, String domain) {
        MetadataProviderSettings.Amazon amazon = new MetadataProviderSettings.Amazon();
        amazon.setEnabled(enabled);
        amazon.setDomain(domain);
        MetadataProviderSettings providers = new MetadataProviderSettings();
        providers.setAmazon(amazon);
        AppSettings settings = new AppSettings();
        settings.setMetadataProviderSettings(providers);
        return settings;
    }
}
