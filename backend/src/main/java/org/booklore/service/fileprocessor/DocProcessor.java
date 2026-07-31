package org.booklore.service.fileprocessor;

import org.booklore.mapper.BookMapper;
import org.booklore.model.CoverExtraction;
import org.booklore.model.document.DocumentContent;
import org.booklore.model.document.DocumentParseResult;
import org.booklore.model.dto.settings.LibraryFile;
import org.booklore.model.entity.BookEntity;
import org.booklore.model.entity.BookFileEntity;
import org.booklore.model.entity.BookMetadataEntity;
import org.booklore.model.enums.BookFileType;
import org.booklore.repository.BookAdditionalFileRepository;
import org.booklore.repository.BookRepository;
import org.booklore.service.book.BookCreatorService;
import org.booklore.service.document.DocumentContentExtractor;
import org.booklore.service.metadata.MetadataMatchService;
import org.booklore.service.metadata.sidecar.SidecarMetadataWriter;
import org.booklore.util.BookUtils;
import org.booklore.util.FileService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.io.File;
import java.util.List;
import java.util.Set;

import static org.booklore.util.FileService.truncate;

/**
 * Word documents. This stage only gets them into the catalog - before it, a {@code .doc} in a
 * watched folder did not fail to open, it did not exist at all, because {@code BookFileExtension}
 * did not recognise the extension and the scan skipped the file without a word.
 */
@Slf4j
@Service
public class DocProcessor extends AbstractFileProcessor implements BookFileProcessor {

    private final DocumentContentExtractor documentContentExtractor;

    public DocProcessor(BookRepository bookRepository,
                        BookAdditionalFileRepository bookAdditionalFileRepository,
                        BookCreatorService bookCreatorService,
                        BookMapper bookMapper,
                        FileService fileService,
                        MetadataMatchService metadataMatchService,
                        SidecarMetadataWriter sidecarMetadataWriter,
                        DocumentContentExtractor documentContentExtractor) {
        super(bookRepository, bookAdditionalFileRepository, bookCreatorService, bookMapper, fileService,
                metadataMatchService, sidecarMetadataWriter);
        this.documentContentExtractor = documentContentExtractor;
    }

    @Override
    public BookEntity processNewFile(LibraryFile libraryFile) {
        BookEntity bookEntity = bookCreatorService.createShellBook(libraryFile, BookFileType.DOC);
        setBookMetadata(bookEntity);
        return bookEntity;
    }

    /**
     * One bounded parse supplies both identifying properties and the body model used by search and
     * the reader. Failures leave the filename baseline in place and are persisted on this file,
     * while the shell book still reaches the catalog.
     */
    private void setBookMetadata(BookEntity bookEntity) {
        File file = bookEntity.getFullFilePath().toFile();
        DocumentParseResult result = documentContentExtractor.parse(file);
        bookEntity.getPrimaryBookFile().setDocumentParseStatus(result.status());
        BookMetadataEntity metadata = bookEntity.getMetadata();

        DocumentContent content = result.content();
        String title = content == null ? null : truncate(content.title(), 1000);
        metadata.setTitle(StringUtils.isBlank(title) ? filenameTitle(bookEntity) : title);

        String documentBody = "";
        if (content != null) {
            metadata.setPublishedDate(content.createdDate());
            if (StringUtils.isNotBlank(content.author())) {
                bookCreatorService.addAuthorsToBook(Set.of(content.author()), bookEntity);
            }
            if (result.isReadable()) {
                documentBody = BookUtils.collectDocumentBodySearchText(
                        content.blocks().stream().map(block -> block.text()));
            }
        }
        metadata.replaceDocumentBodySearchText(documentBody);
    }

    private String filenameTitle(BookEntity bookEntity) {
        String fileName = bookEntity.getPrimaryBookFile().getFileName();
        int dot = fileName.lastIndexOf('.');
        return truncate(dot > 0 ? fileName.substring(0, dot) : fileName, 1000);
    }

    @Override
    public boolean generateCover(BookEntity bookEntity) {
        return false;
    }

    @Override
    public CoverExtraction extractCover(BookEntity bookEntity, BookFileEntity bookFile) {
        return CoverExtraction.noCoverFound();
    }

    @Override
    public List<BookFileType> getSupportedTypes() {
        return List.of(BookFileType.DOC);
    }
}
