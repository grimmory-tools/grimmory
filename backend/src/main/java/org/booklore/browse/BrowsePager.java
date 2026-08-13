package org.booklore.browse;

import org.booklore.exception.ApiError;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class BrowsePager {

    public static final int MAX_PAGE_SIZE = 100;

    private final CursorCodec codec;
    private final LinksBuilder linksBuilder;

    public BrowsePager(CursorCodec codec, LinksBuilder linksBuilder) {
        this.codec = codec;
        this.linksBuilder = linksBuilder;
    }

    public record Window(long offset, int limit, String sort, String paramsHash) {
    }

    public Window resolve(String sort, String cursor, Pageable pageable, String paramsHash) {
        long offset;
        int limit;
        String sortString;
        if (cursor != null) {
            CursorState state = codec.decode(cursor);
            codec.verifyParamsMatch(state, paramsHash);
            offset = state.offset();
            limit = state.limit();
            sortString = state.sort();
        } else {
            offset = pageable.getOffset();
            limit = pageable.getPageSize();
            sortString = sort;
        }
        if (limit <= 0) {
            throw ApiError.INVALID_INPUT.createException("Page size must be positive.");
        }
        return new Window(offset, Math.min(limit, MAX_PAGE_SIZE), sortString, paramsHash);
    }

    public <T> BrowsePage<T> assemble(String pagePath, String facetPath, String preserved,
                                      Window window, long total, List<T> content) {
        CursorState baseState = new CursorState(window.offset(), window.limit(), window.sort(), window.paramsHash());
        List<Link> links = linksBuilder.build(new LinksBuilder.Context(
                pagePath, facetPath, preserved, window.offset(), window.limit(), total, baseState));
        return BrowsePage.of(content, window.offset(), window.limit(), total, codec.encode(baseState), links);
    }
}
