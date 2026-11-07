package org.booklore.service.metadata;

import lombok.RequiredArgsConstructor;
import org.booklore.exception.ApiError;
import org.booklore.model.dto.BookMetadata;
import org.booklore.model.dto.MetadataProviderDto;
import org.booklore.model.enums.MetadataProvider;
import org.booklore.service.metadata.parser.BookParser;
import org.booklore.service.metadata.parser.DetailedMetadataProvider;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class MetadataProviderService {
    private final Map<MetadataProvider, BookParser> parserMap;

    public List<MetadataProviderDto> getProviders() {
        return parserMap
                .entrySet()
                .stream()
                .map(e -> new MetadataProviderDto(e.getKey(), e.getValue().isEnabled()))
                .toList();
    }

    public BookMetadata getDetailedMetadata(MetadataProvider provider, String providerItemId) {
        BookParser parser = getParser(provider);

        if (!parser.isEnabled()) {
            return null;
        }

        if (parser instanceof DetailedMetadataProvider detailedProvider) {
            return detailedProvider.fetchDetailedMetadata(providerItemId);
        }
        return null;
    }

    private BookParser getParser(MetadataProvider provider) {
        BookParser parser = parserMap.get(provider);
        if (parser == null) {
            throw ApiError.METADATA_SOURCE_NOT_IMPLEMENT_OR_DOES_NOT_EXIST.createException();
        }
        return parser;
    }
}
