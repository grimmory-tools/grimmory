import {
  BrowseFacetGroup,
  BrowseFacetIndex,
  BrowseFacetValue,
  BrowseLink,
  BrowsePage,
  BrowsePageMetadata,
} from './browse.models';

interface RawLink {
  rel: string | string[];
  href: string;
  type: string;
}

interface RawBrowsePage<T> {
  content: T[];
  page: BrowsePageMetadata;
  links: RawLink[];
}

interface RawFacetLink extends RawLink {
  title: string;
  value: string;
  properties?: {numberOfItems?: number};
}

interface RawFacetGroup {
  metadata: {rel: string; key: string; min?: number; max?: number};
  links: RawFacetLink[];
}

interface RawFacetResponse {
  links: RawLink[];
  facets: RawFacetGroup[];
}

export function mapBrowsePage<T>(response: RawBrowsePage<T>): BrowsePage<T> {
  return {
    content: response.content,
    page: response.page,
    links: response.links.map(mapBrowseLink),
  };
}

export function mapBrowseFacetIndex(response: RawFacetResponse): BrowseFacetIndex {
  const facetKeys: string[] = [];
  const sortTokens: string[] = [];
  for (const group of response.facets) {
    if (group.metadata.rel === 'facet') {
      facetKeys.push(group.metadata.key);
    } else if (group.metadata.rel === 'sort') {
      sortTokens.push(...group.links.map(link => link.value));
    }
  }
  return {facetKeys, sortTokens};
}

export function mapBrowseFacetPage(response: RawFacetResponse): BrowseFacetGroup {
  const group = response.facets[0];
  return {
    key: group.metadata.key,
    values: group.links.map(mapBrowseFacetValue),
    min: group.metadata.min,
    max: group.metadata.max,
    complete: !response.links.some(link => normalizeRel(link.rel).includes('next')),
  };
}

function mapBrowseLink(raw: RawLink): BrowseLink {
  return {
    rel: normalizeRel(raw.rel),
    href: raw.href,
    type: raw.type,
  };
}

function mapBrowseFacetValue(raw: RawFacetLink): BrowseFacetValue {
  return {
    value: raw.value,
    title: raw.title,
    count: raw.properties?.numberOfItems ?? 0,
  };
}

function normalizeRel(rel: string | string[]): string[] {
  return Array.isArray(rel) ? rel : [rel];
}
