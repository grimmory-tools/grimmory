package org.booklore.service.metadata.parser;

import org.booklore.model.dto.Book;
import org.booklore.model.dto.BookMetadata;
import org.booklore.model.dto.request.FetchMetadataRequest;
import reactor.core.publisher.Flux;

import java.util.List;

public interface BookParser {

    List<BookMetadata> fetchMetadata(Book book, FetchMetadataRequest fetchMetadataRequest);

    default Flux<BookMetadata> fetchMetadataStream(Book book, FetchMetadataRequest fetchMetadataRequest) {
        return Flux.fromIterable(fetchMetadata(book, fetchMetadataRequest));
    }

    BookMetadata fetchTopMetadata(Book book, FetchMetadataRequest fetchMetadataRequest);
}
