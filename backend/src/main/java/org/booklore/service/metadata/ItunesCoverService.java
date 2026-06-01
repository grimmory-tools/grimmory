package org.booklore.service.metadata;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.booklore.model.dto.Book;
import org.booklore.model.dto.BookFile;
import org.booklore.model.dto.BookMetadata;
import org.booklore.model.dto.CoverImage;
import org.booklore.model.dto.request.CoverFetchRequest;
import org.booklore.model.dto.request.FetchMetadataRequest;
import org.booklore.model.enums.BookFileType;
import org.booklore.service.appsettings.AppSettingService;
import org.booklore.service.metadata.parser.ItunesParser;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Service
@AllArgsConstructor
public class ItunesCoverService implements BookCoverProvider {

    private final ItunesParser itunesParser;
    private final AppSettingService appSettingService;

    @Override
    public Flux<CoverImage> getCovers(CoverFetchRequest request) {
        return Flux.defer(() -> {
            try {
                var itunesSettings = appSettingService.getAppSettings().getMetadataProviderSettings().getItunes();
                if (itunesSettings == null || !itunesSettings.isEnabled()) {
                    return Flux.empty();
                }

                BookFileType fileType = "audiobook".equalsIgnoreCase(request.getCoverType())
                        ? BookFileType.AUDIOBOOK
                        : BookFileType.EPUB;

                Book dummyBook = Book.builder()
                        .primaryFile(BookFile.builder().bookType(fileType).build())
                        .build();

                FetchMetadataRequest metaRequest = FetchMetadataRequest.builder()
                        .title(request.getTitle())
                        .author(request.getAuthor())
                        .isbn(request.getIsbn())
                        .build();

                List<BookMetadata> results = itunesParser.fetchMetadata(dummyBook, metaRequest);

                List<CoverImage> covers = new ArrayList<>();
                AtomicInteger index = new AtomicInteger(1);

                for (BookMetadata metadata : results) {
                    if (metadata.getThumbnailUrl() != null && !metadata.getThumbnailUrl().isBlank()) {
                        String baseCover = metadata.getThumbnailUrl();
                        String highResCover = ItunesParser.resizeArtworkUrl(baseCover, 1000, 1000);

                        covers.add(new CoverImage(highResCover, 1000, 1000, index.getAndIncrement()));
                    }
                }

                return Flux.fromIterable(covers);
            } catch (Exception e) {
                log.error("Error fetching covers from iTunes: ", e);
                return Flux.empty();
            }
        }).subscribeOn(Schedulers.boundedElastic());
    }
}
