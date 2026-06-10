package org.booklore.browse;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * The decoded contents of an opaque pagination cursor, encoded as compact base64url JSON by
 * CursorCodec. Offset-with-state rather than keyset: it carries the absolute offset and limit,
 * the sort string, a fingerprint of the facet/query parameters, and — for random sorts only —
 * the chosen permutation of random columns and their directions, so pages stay stable across
 * requests.
 *
 * @param version           cursor schema version; only the current version decodes
 * @param offset            zero-based row offset of the page
 * @param limit             page size
 * @param sort              the sort string this cursor was created with
 * @param paramsHash        fingerprint of the facet/query parameters
 * @param randomPermutation for random sorts, the ordered random-column indices; else null
 * @param randomDirections  for random sorts, descending flags paired with randomPermutation; else null
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record CursorState(
        @JsonProperty("v") int version,
        @JsonProperty("o") long offset,
        @JsonProperty("l") int limit,
        @JsonProperty("s") String sort,
        @JsonProperty("f") String paramsHash,
        @JsonProperty("r") List<Integer> randomPermutation,
        @JsonProperty("rd") List<Boolean> randomDirections
) {

    public CursorState withVersion(int newVersion) {
        return new CursorState(newVersion, offset, limit, sort, paramsHash, randomPermutation, randomDirections);
    }

    public CursorState withOffset(long newOffset) {
        return new CursorState(version, newOffset, limit, sort, paramsHash, randomPermutation, randomDirections);
    }
}
