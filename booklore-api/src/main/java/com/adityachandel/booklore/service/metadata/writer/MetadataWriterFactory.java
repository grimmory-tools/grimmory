package com.adityachandel.booklore.service.metadata.writer;

import com.adityachandel.booklore.model.enums.BookFileType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class MetadataWriterFactory {

    private final Map<BookFileType, MetadataWriter> writers;

    public MetadataWriterFactory(List<MetadataWriter> writerList) {
        writers = writerList.stream()
                .collect(Collectors.toMap(MetadataWriter::getSupportedBookType, Function.identity()));
    }

    public Optional<MetadataWriter> getWriter(BookFileType bookFileType) {
        return Optional.ofNullable(writers.get(bookFileType));
    }
}
