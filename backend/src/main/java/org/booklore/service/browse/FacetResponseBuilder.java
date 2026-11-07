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
import java.util.stream.Stream;

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

    FacetGroup numberGroup(String key, String title, List<FacetCount> counts, Number min, Number max) {
        FacetGroup group = group(key, title, counts);
        if (min == null) {
            return group;
        }
        String href = pageLink("facet=" + BrowseParams.encode(key + ":") + "{min}..{max}");
        FacetLink range = new FacetLink(List.of("facet"), href, Link.JSON_TYPE, true, title, null, new Properties(null, min, max));
        return new FacetGroup(group.metadata(), Stream.concat(Stream.of(range), group.links().stream()).toList());
    }

    List<Link> selfLinks() {
        return List.of(Link.json(List.of("self"), href(facetPath)));
    }

    List<Link> pagedLinks(String search, int page, int size, boolean hasNext) {
        String query = search == null ? preserved : join(preserved, "search=" + BrowseParams.encode(search));
        List<Link> links = new ArrayList<>();
        links.add(Link.json(List.of("self"), facetPath + "?" + join(query, "page=" + page + "&size=" + size)));
        if (hasNext) {
            links.add(Link.json(List.of("next"), facetPath + "?" + join(query, "page=" + (page + 1) + "&size=" + size)));
        }
        return links;
    }

    private String pageLink(String param) {
        return pagePath + "?" + join(preserved, param);
    }

    private String href(String path) {
        return preserved.isBlank() ? path : path + "?" + preserved;
    }

    private static String join(String query, String params) {
        return query.isBlank() ? params : query + "&" + params;
    }
}
