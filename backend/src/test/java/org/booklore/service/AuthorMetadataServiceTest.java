package org.booklore.service;

import org.booklore.config.security.service.AuthenticationService;
import org.booklore.exception.APIException;
import org.booklore.model.dto.AuthorDetails;
import org.booklore.model.dto.AuthorSearchResult;
import org.booklore.model.dto.BookLoreUser;
import org.booklore.model.dto.request.AuthorMatchRequest;
import org.booklore.model.entity.AuthorEntity;
import org.booklore.model.enums.AuditAction;
import org.booklore.model.enums.AuthorMetadataSource;
import org.booklore.repository.AuthorRepository;
import org.booklore.service.audit.AuditService;
import org.booklore.service.metadata.DuckDuckGoCoverService;
import org.booklore.service.metadata.parser.AuthorParser;
import org.booklore.util.FileService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

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
    @Mock private AuditService auditService;
    @Mock private FileService fileService;
    @Mock private DuckDuckGoCoverService duckDuckGoCoverService;
    @Mock private AuthenticationService authenticationService;

    private AuthorMetadataService service;

    @BeforeEach
    void setUp() {
        Map<AuthorMetadataSource, AuthorParser> authorParserMap = Map.of(
                AuthorMetadataSource.AUDNEXUS, authorParser
        );
        service = new AuthorMetadataService(authorRepository, authorParserMap, auditService, fileService, duckDuckGoCoverService, authenticationService);

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
        author.setName("Stephen King");

        when(authorRepository.findById(1L)).thenReturn(Optional.of(author));

        AuthorSearchResult result = AuthorSearchResult.builder()
                .source(AuthorMetadataSource.AUDNEXUS).asin("B000APZGGS")
                .description("Master of horror").imageUrl("https://example.com/king.jpg")
                .name("Stephen King")
                .build();

        when(authorParser.getAuthorByAsin("B000APZGGS", "us")).thenReturn(result);
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
        assertThat(author.getAsin()).isEqualTo("B000APZGGS");

        verify(authorRepository).save(author);
        verify(fileService).createAuthorThumbnailFromUrl(eq(1L), eq("https://example.com/king.jpg"));
        verify(auditService).log(eq(AuditAction.AUTHOR_METADATA_UPDATED), eq("Author"), eq(1L), anyString());
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
        when(authorParser.getAuthorByAsin("INVALID", "us")).thenReturn(null);

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

        when(authorParser.getAuthorByAsin("B000APZGGS", "us")).thenReturn(result);
        when(authorRepository.save(any(AuthorEntity.class))).thenAnswer(i -> i.getArgument(0));

        AuthorMatchRequest request = new AuthorMatchRequest();
        request.setAsin("B000APZGGS");
        request.setSource(AuthorMetadataSource.AUDNEXUS);
        request.setRegion("us");

        service.matchAuthor(1L, request);

        verify(fileService, never()).createAuthorThumbnailFromUrl(anyLong(), anyString());
    }
}
