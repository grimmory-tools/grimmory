package org.booklore.browse;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * An OPDS-style link in a browse response. rel is a list so one link can carry several
 * relations (e.g. ["first", "previous"]).
 *
 * @param rel  the relation(s): self, first, previous, next, facet
 * @param href the target URL, carrying the cursor parameter for paging relations
 * @param type the media type; application/json for browse pages
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record Link(List<String> rel, String href, String type) {

    public static final String JSON_TYPE = "application/json";

    public static Link json(List<String> rel, String href) {
        return new Link(rel, href, JSON_TYPE);
    }
}
