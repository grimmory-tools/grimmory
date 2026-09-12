import {
  BrowseFacetGroup,
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
  metadata: {rel: string; key: string; title: string};
  links: RawFacetLink[];
}

interface RawFacetResponse {
  facets: RawFacetGroup[];
}

export function mapBrowsePage<T>(response: RawBrowsePage<T>): BrowsePage<T> {
  return {
    content: response.content,
    page: response.page,
    links: response.links.map(mapBrowseLink),
  };
}

export function mapBrowseFacetGroups(response: RawFacetResponse): BrowseFacetGroup[] {
  return response.facets.map(group => ({
    rel: group.metadata.rel,
    key: group.metadata.key,
    title: group.metadata.title,
    values: group.links.map(mapBrowseFacetValue),
  }));
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
    selected: normalizeRel(raw.rel).includes('self'),
  };
}

function normalizeRel(rel: string | string[]): string[] {
  return Array.isArray(rel) ? rel : [rel];
}
