package org.booklore.browse;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

// Decoded contents of a pagination cursor (compact base64url JSON via CursorCodec)
// Offset-with-state: absolute offset/limit, sort string, and a fingerprint of the
// facet/query params, so a cursor with conflicting params is rejected
@JsonInclude(JsonInclude.Include.NON_NULL)
public record CursorState(
        @JsonProperty("o") long offset,
        @JsonProperty("l") int limit,
        @JsonProperty("s") String sort,
        @JsonProperty("f") String paramsHash
) {

    public CursorState withOffset(long newOffset) {
        return new CursorState(newOffset, limit, sort, paramsHash);
    }
}
