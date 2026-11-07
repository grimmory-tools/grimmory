export interface BrowseLink {
  rel: string[];
  href: string;
  type: string;
}

export interface BrowsePageMetadata {
  number: number;
  size: number;
  totalElements: number;
  totalPages: number;
  cursor: string;
}

export interface BrowsePage<T> {
  content: T[];
  page: BrowsePageMetadata;
  links: BrowseLink[];
}

export interface BrowseFacetValue {
  value: string;
  title: string;
  count: number;
}

export interface BrowseFacetGroup {
  key: string;
  values: BrowseFacetValue[];
  min?: number;
  max?: number;
  complete?: boolean;
}

export interface BrowseFacetIndex {
  facetKeys: string[];
  sortTokens: string[];
}

export type BrowseSortDirection = 'asc' | 'desc';

export interface BrowseSortTerm<Key extends string = string> {
  key: Key;
  direction: BrowseSortDirection;
}

export function findBrowsePageLink(
  page: BrowsePage<unknown>,
  rel: string,
): BrowseLink | undefined {
  return page.links.find(link => link.rel.includes(rel));
}

export function flattenBrowsePages<T extends {id: number}>(
  data: {pages: BrowsePage<T>[]} | undefined,
): T[] {
  const items = data?.pages.flatMap(page => page.content) ?? [];
  const seen = new Set<number>();
  return items.filter(item => {
    if (seen.has(item.id)) {
      return false;
    }
    seen.add(item.id);
    return true;
  });
}
