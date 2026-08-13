package org.booklore.service.browse;

import org.booklore.browse.Link;
import org.booklore.browse.SortParser;
import org.booklore.model.dto.browse.FacetGroupsResponse.FacetGroup;
import org.booklore.model.dto.browse.FacetGroupsResponse.FacetLink;
import org.booklore.model.dto.browse.FacetGroupsResponse.Metadata;
import org.booklore.model.dto.browse.FacetGroupsResponse.Properties;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

final class FacetResponseBuilder {

    record FacetCount(String value, long count) {
    }

    private final String pagePath;
    private final String facetPath;
    private final String preserved;
    private final List<String> selectedFacets;

    FacetResponseBuilder(String pagePath, String facetPath, String preserved, List<String> selectedFacets) {
        this.pagePath = pagePath;
        this.facetPath = facetPath;
        this.preserved = preserved;
        this.selectedFacets = selectedFacets;
    }

    FacetGroup sortGroup(Set<String> sortKeys) {
        List<FacetLink> links = new ArrayList<>();
        for (String key : sortKeys) {
            if (key.equals(SortParser.TIEBREAKER_KEY)) {
                continue;
            }
            links.add(new FacetLink(List.of("sort"), pageLink("sort=" + BrowseParams.encode(key)), Link.JSON_TYPE, key + " ascending", key, null));
            links.add(new FacetLink(List.of("sort"), pageLink("sort=-" + BrowseParams.encode(key)), Link.JSON_TYPE, key + " descending", "-" + key, null));
        }
        return new FacetGroup(new Metadata("sort", "sort", "Sort"), links);
    }

    FacetGroup group(String key, String title, List<FacetCount> counts) {
        List<FacetLink> links = counts.stream()
                .map(c -> {
                    boolean active = BrowseParams.hasFacet(selectedFacets, key, c.value());
                    List<String> rel = active ? List.of("self", "facet") : List.of("facet");
                    String href = active
                            ? href(pagePath)
                            : pageLink("facet=" + BrowseParams.encode(key + ":" + c.value()));
                    return new FacetLink(rel, href, Link.JSON_TYPE, c.value(), c.value(), new Properties(c.count()));
                })
                .toList();
        return new FacetGroup(new Metadata("facet", key, title), links);
    }

    List<Link> selfLinks() {
        return List.of(Link.json(List.of("self"), href(facetPath)));
    }

    private String pageLink(String param) {
        return preserved.isBlank() ? pagePath + "?" + param : pagePath + "?" + preserved + "&" + param;
    }

    private String href(String path) {
        return preserved.isBlank() ? path : path + "?" + preserved;
    }
}
