package org.booklore.model.dto.response;

import java.util.Map;

/**
 * Lightweight aggregate counts used by the sidebar menu to render book counts
 * per library / shelf / magic shelf without materialising the full book list.
 *
 * <p>Counts intentionally do not apply per-user content restrictions (category /
 * tag / mood / age-rating). The sidebar is a navigational aid, not an authoritative
 * view into filtered content, so minor discrepancies with the restricted listing
 * are acceptable in exchange for O(1) aggregate queries.
 */
public record MenuCountsResponse(
        Map<Long, Long> libraryCounts,
        Map<Long, Long> shelfCounts,
        Map<Long, Long> magicShelfCounts,
        long totalBookCount,
        long unshelvedBookCount
) {}
